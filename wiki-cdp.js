#!/usr/bin/env node
/* ============================================================================
   wiki-cdp.js — 通过「你本机 Chrome 的调试端口」读写 Miraheze wiki
   ----------------------------------------------------------------------------
   为什么要有它：Miraheze 全站在 Cloudflare 托管挑战后面，本机 curl 一律 403；
   而浏览器能过挑战。所以这里用 CDP（Chrome DevTools Protocol）把请求交给浏览器去发：
     * 不需要把 cookie / 密码交给任何外部程序；
     * 不需要 Special:Import 权限；
     * 不需要安装任何插件；
     * Chrome 和 Edge 都行（都是 Chromium，协议一致）——换浏览器只改启动命令里的 exe。

   前置：浏览器以调试端口启动（推荐用现成脚本，它会自动判断要不要关掉正在运行的浏览器）
     .\open-wiki-browser.ps1                     # Edge + 你平时的配置（推荐：登录/挑战都在）
     .\open-wiki-browser.ps1 -TempProfile        # Edge + 独立配置（要在新窗口过一次验证并登录）
     .\open-wiki-browser.ps1 -Browser Chrome     # 想用 Chrome
     .\open-wiki-browser.ps1 -Port 9333          # 换端口（记得给本脚本设 CDP_PORT=9333）

   也可以用任意 Chromium 浏览器手动启动：
     msedge.exe --remote-debugging-port=9222 --remote-allow-origins=*        # 用你平时的配置
     msedge.exe --remote-debugging-port=9222 --remote-allow-origins=* --user-data-dir="%TEMP%\jafa-edge"
   （想用**已登录**的会话：先完全退出该浏览器，再**不加** --user-data-dir 启动）

   用法：
     node wiki-cdp.js check              # 连接自检 + 看当前登录用户
     node wiki-cdp.js pages [前缀]        # 列出 wiki 上所有页面（标题 / 字节 / 最后修改）
     node wiki-cdp.js dump [文件名]       # 把全部页面正文导出成 JSON（默认 wiki-dump.json）
     node wiki-cdp.js push [目录] [--dry] # 把本地 wiki\*.wiki 推上去（--dry 只预览）
     node wiki-cdp.js upload <映射.json>  # 上传本地图片（{"Wiki 文件名.png": "本地路径.png"}）
     node wiki-cdp.js eval <js文件>       # 在页面里执行任意 JS 并打印返回值
   ============================================================================ */
const fs = require("fs");
const path = require("path");

const PORT = process.env.CDP_PORT || 9222;
const ORIGIN = process.env.WIKI_ORIGIN || "https://jafa.miraheze.org";
const API = "/w/api.php";
const SUMMARY = "从本地 wikitext 批量导入/更新（JOE's Addons for ABMC）";

// ---------------------------------------------------------------- CDP 客户端
async function pickTarget() {
  const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
  const pages = list.filter(t => t.type === "page");
  if (!pages.length) throw new Error("没有可用的页面目标：请先启动 Chrome（见文件头说明）");
  // 优先选已经在目标站点上的标签页，否则用第一个
  return pages.find(p => p.url.startsWith(ORIGIN)) || pages[0];
}

class CDP {
  constructor(ws) { this.ws = ws; this.id = 0; this.pending = new Map(); }
  static async connect(wsUrl) {
    const sock = new WebSocket(wsUrl);
    await new Promise((res, rej) => {
      sock.addEventListener("open", res, { once: true });
      sock.addEventListener("error", () => rej(new Error("WebSocket 连接失败（Chrome 是否带 --remote-allow-origins=* 启动？）")), { once: true });
    });
    const cdp = new CDP(sock);
    sock.addEventListener("message", (e) => {
      const m = JSON.parse(e.data);
      if (m.id && cdp.pending.has(m.id)) { cdp.pending.get(m.id)(m); cdp.pending.delete(m.id); }
    });
    return cdp;
  }
  send(method, params = {}) {
    return new Promise((res) => { const i = ++this.id; this.pending.set(i, res); this.ws.send(JSON.stringify({ id: i, method, params })); });
  }
  /** 在页面里执行 JS，返回其（JSON 化的）结果 */
  async eval(expression, awaitPromise = true) {
    const r = await this.send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise });
    if (r.result?.exceptionDetails) {
      throw new Error("页面内 JS 异常: " + (r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text));
    }
    return r.result?.result?.value;
  }
  close() { try { this.ws.close(); } catch {} }
}

/** 等 Cloudflare 挑战通过：新开的浏览器配置第一次访问时，页面要跑几秒 JS 才会放行 API。 */
async function waitReady(cdp, tries = 20, delayMs = 1500) {
  for (let i = 0; i < tries; i++) {
    const status = await cdp.eval(`(async()=>{ try {
        const r = await fetch("${API}?action=query&meta=siteinfo&format=json", { credentials: "same-origin" });
        return r.status;
      } catch (e) { return "err:" + e.message; } })()`);
    if (status === 200) return true;
    if (i === 0) console.log(`… Cloudflare 挑战尚未通过（${status}），等待中…`);
    await new Promise(r => setTimeout(r, delayMs));
  }
  // 还没通过：看看浏览器是不是停在 Cloudflare 的验证页上，给出可操作提示
  const info = await cdp.eval(`JSON.stringify({ title: document.title, href: location.href })`);
  let parsed = {};
  try { parsed = JSON.parse(info); } catch {}
  const stuck = /请稍候|Just a moment|Checking/i.test(parsed.title || "");
  throw new Error(
    "等不到 Cloudflare 放行。" + (stuck
      ? `浏览器窗口现在停在验证页「${parsed.title}」——请在那个窗口里完成验证（必要时点一下复选框），或改用你平时的浏览器配置（已带 clearance，无需验证）。`
      : `当前页面：${parsed.title || parsed.href}。请在浏览器里确认能正常打开 wiki，并已登录。`));
}

async function ensureOnWiki(cdp) {
  const href = await cdp.eval("location.href");
  if (href && href.startsWith(ORIGIN)) { await waitReady(cdp); return href; }
  console.log(`… 当前标签页在 ${href}，导航到 ${ORIGIN}/wiki/Special:Version`);
  await cdp.send("Page.enable");
  await cdp.send("Page.navigate", { url: `${ORIGIN}/wiki/Special:Version` });
  for (let i = 0; i < 40; i++) {
    await new Promise(r => setTimeout(r, 500));
    const st = await cdp.eval("document.readyState");
    const now = await cdp.eval("location.href");
    if (st === "complete" && now && now.startsWith(ORIGIN)) { await waitReady(cdp); return now; }
  }
  throw new Error("导航超时");
}

// 页面内通用的小工具（每次注入都覆盖，避免旧的 helper 残留在页面里）
const HELPER = `
window.__jafa = {
  async api(params) {
    const body = new URLSearchParams({ format: "json", formatversion: "2", ...params });
    const res = await fetch("${API}", { method: "POST", body, credentials: "same-origin" });
    if (!res.ok) throw new Error("HTTP " + res.status);
    const data = await res.json();
    if (data.error) throw new Error(data.error.code + ": " + data.error.info);
    return data;
  },
  async token() {
    if (!this._t) {
      // 注意：有的 MediaWiki（含本 wiki 的 1.46）返回的键是 csrftoken 而不是 csrf，两个都兼容
      const tk = (await this.api({ action: "query", meta: "tokens", type: "csrf" })).query.tokens;
      this._t = tk.csrftoken || tk.csrf;
      if (!this._t) throw new Error("拿不到 CSRF token：" + JSON.stringify(tk));
    }
    return this._t;
  },
  resetToken() { this._t = null; }
}; "ok"`;

// ------------------------------------------------------------------ 子命令
async function cmdCheck(cdp) {
  const href = await ensureOnWiki(cdp);
  await cdp.eval(HELPER);
  const who = await cdp.eval(`(async()=>JSON.stringify((await window.__jafa.api({action:"query",meta:"userinfo"})).query.userinfo))()`);
  const info = JSON.parse(who);
  const site = await cdp.eval(`(async()=>JSON.stringify((await window.__jafa.api({action:"query",meta:"siteinfo",siprop:"general"})).query.general))()`);
  const g = JSON.parse(site);
  // 未登录时 userinfo 也会返回一个 name（内容是 IP），必须靠 anon / id===0 判断
  const anonymous = info.anon === true || info.id === 0 || /^\d+\.\d+\.\d+\.\d+$/.test(info.name || "");
  console.log("页面地址   :", href);
  console.log("站点       :", g.sitename, "| MediaWiki", g.generator);
  console.log("登录用户   :", anonymous ? "（未登录，只读模式）" : info.name);
  console.log("可编辑     :", anonymous ? "否 —— 编辑前需要在这个浏览器窗口里登录 wiki" : "是");
}

async function cmdPages(cdp, prefix) {
  await ensureOnWiki(cdp); await cdp.eval(HELPER);
  const json = await cdp.eval(`(async()=>{
    const out=[]; let cont=null;
    do {
      const p={action:"query",list:"allpages",aplimit:"500",apnamespace:"0"};
      if(cont) p.apcontinue=cont;
      const d=await window.__jafa.api(p);
      out.push(...d.query.allpages.map(x=>x.title));
      cont=d.continue?d.continue.apcontinue:null;
    } while(cont);
    return JSON.stringify(out);
  })()`);
  let titles = JSON.parse(json);
  if (prefix) titles = titles.filter(t => t.startsWith(prefix));
  // 分批取长度与时间
  const rows = [];
  for (let i = 0; i < titles.length; i += 50) {
    const batch = titles.slice(i, i + 50);
    const j = await cdp.eval(`(async()=>JSON.stringify((await window.__jafa.api({
      action:"query",prop:"revisions",rvprop:"timestamp|size",titles:${JSON.stringify(batch.join("|"))}
    })).query.pages))()`);
    for (const pg of JSON.parse(j)) {
      const rev = (pg.revisions || [])[0] || {};
      rows.push({ 标题: pg.title, 字节: rev.size ?? 0, 最后修改: rev.timestamp ?? "" });
    }
  }
  rows.sort((a, b) => b.字节 - a.字节);
  console.log(`共 ${rows.length} 个条目页：`);
  for (const r of rows) console.log(String(r.字节).padStart(7), r.最后修改.slice(0, 16), r.标题);
  return rows;
}

async function cmdDump(cdp, outfile = "wiki-dump.json") {
  await ensureOnWiki(cdp); await cdp.eval(HELPER);
  const titles = JSON.parse(await cdp.eval(`(async()=>{
    const out=[]; let cont=null;
    do { const p={action:"query",list:"allpages",aplimit:"500",apnamespace:"0"};
      if(cont) p.apcontinue=cont;
      const d=await window.__jafa.api(p); out.push(...d.query.allpages.map(x=>x.title));
      cont=d.continue?d.continue.apcontinue:null; } while(cont);
    return JSON.stringify(out);
  })()`));
  console.log(`共 ${titles.length} 个条目页，开始抓正文…`);
  const pages = {};
  for (let i = 0; i < titles.length; i += 20) {
    const batch = titles.slice(i, i + 20);
    const j = await cdp.eval(`(async()=>JSON.stringify((await window.__jafa.api({
      action:"query",prop:"revisions",rvprop:"content|timestamp",rvslots:"main",
      titles:${JSON.stringify(batch.join("|"))}
    })).query.pages))()`);
    for (const pg of JSON.parse(j)) {
      const rev = (pg.revisions || [])[0];
      pages[pg.title] = { 内容: rev?.slots?.main?.content ?? "", 最后修改: rev?.timestamp ?? "" };
    }
    process.stdout.write(`\r  已抓 ${Math.min(i + 20, titles.length)}/${titles.length}`);
  }
  const payload = { 导出时间: new Date().toISOString(), 站点: ORIGIN, 页面数: Object.keys(pages).length, 页面: pages };
  fs.writeFileSync(outfile, JSON.stringify(payload, null, 1), "utf8");
  console.log(`\n已写出 ${outfile}（${Object.keys(pages).length} 页，${(fs.statSync(outfile).size / 1024).toFixed(1)} KB）`);
}

async function cmdPush(cdp, dir = "wiki", dry = false) {
  if (!fs.existsSync(dir)) throw new Error(`找不到目录 ${dir}`);
  const files = fs.readdirSync(dir).filter(f => f.endsWith(".wiki")).sort();
  if (!files.length) throw new Error(`${dir} 里没有 .wiki 文件`);
  await ensureOnWiki(cdp); await cdp.eval(HELPER);
  const who = JSON.parse(await cdp.eval(`(async()=>JSON.stringify((await window.__jafa.api({action:"query",meta:"userinfo"})).query.userinfo))()`));
  const anon = who.anon === true || who.id === 0 || /^\d+\.\d+\.\d+\.\d+$/.test(who.name || "");
  if (anon) throw new Error("没有登录：请在这一个 Chrome 窗口里登录 wiki 后再推送（只读操作不需要登录，编辑需要）");
  console.log(`以 ${who.name} 的身份推送 ${files.length} 个页面${dry ? "（dry-run：不写入）" : ""}`);
  const results = [];
  for (const f of files) {
    const base = path.basename(f, ".wiki");
    const title = base.startsWith("分类-") ? "Category:" + base.slice(3) : base;
    const text = fs.readFileSync(path.join(dir, f), "utf8");
    if (dry) { console.log(`  [预览] ${title} — ${text.length} 字符`); results.push({ 标题: title, 结果: "预览" }); continue; }
    try {
      // 每页最多试两次：badtoken 时刷新 token 再试一次（登录前后 token 会变）
      const r = JSON.parse(await cdp.eval(`(async()=>{
        const edit = async () => {
          const t = await window.__jafa.token();
          const d = await window.__jafa.api({action:"edit",title:${JSON.stringify(title)},text:${JSON.stringify(text)},
            summary:${JSON.stringify(SUMMARY)},token:t,bot:1,watchlist:"nochange",recreate:1,assert:"user"});
          return {ok:true, result:d.edit.result, rev:d.edit.newrevid};
        };
        try { return JSON.stringify(await edit()); }
        catch(e) {
          if (/badtoken|notoken/i.test(e.message)) {
            window.__jafa.resetToken();
            try { return JSON.stringify(await edit()); }
            catch(e2) { return JSON.stringify({ok:false, result:e2.message}); }
          }
          return JSON.stringify({ok:false, result:e.message});
        }
      })()`));
      console.log(`  ${r.ok ? "✅" : "❌"} ${title} — ${r.result}${r.rev ? "（rev " + r.rev + "）" : ""}`);
      results.push({ 标题: title, 结果: r.result });
    } catch (e) {
      console.log(`  ❌ ${title} — ${e.message}`);
      results.push({ 标题: title, 结果: e.message });
    }
    await new Promise(r => setTimeout(r, 120));
  }
  const ok = results.filter(r => r.结果 === "Success" || r.结果 === "预览").length;
  console.log(`完成：${ok}/${results.length}`);
  return results;
}

// ------------------------------------------------------------------- 上传
/**
 * 上传本地图片。map 文件格式（JSON）两种都支持：
 *   简单版：{ "Wiki 上的文件名.png": "本地路径.png", ... }
 *   带说明：{ "Wiki 上的文件名.png": { "path": "本地路径.png", "desc": "游戏内截图" }, ... }
 * 走 action=upload（multipart/form-data），文件字节由 Node 读入、base64 传给页面再用 Blob 还原。
 */
async function cmdUpload(cdp, mapFile) {
  if (!mapFile || !fs.existsSync(mapFile)) throw new Error(`找不到映射文件 ${mapFile}`);
  const map = JSON.parse(fs.readFileSync(mapFile, "utf8"));
  const entries = Object.entries(map).map(([name, v]) =>
    typeof v === "string" ? { name, path: v, desc: null } : { name, path: v.path, desc: v.desc || null });
  await ensureOnWiki(cdp); await cdp.eval(HELPER);
  const who = JSON.parse(await cdp.eval(`(async()=>JSON.stringify((await window.__jafa.api({action:"query",meta:"userinfo"})).query.userinfo))()`));
  const anon = who.anon === true || who.id === 0 || /^\d+\.\d+\.\d+\.\d+$/.test(who.name || "");
  if (anon) throw new Error("没有登录：上传需要登录（且账号要有 upload 权限）");
  console.log(`以 ${who.name} 的身份上传 ${entries.length} 个文件`);

  const results = [];
  for (const { name: wikiName, path: localPath, desc } of entries) {
    if (!fs.existsSync(localPath)) { console.log(`  ⏭  跳过 ${wikiName}（本地不存在 ${localPath}）`); results.push({ 文件: wikiName, 结果: "本地缺文件" }); continue; }
    const b64 = fs.readFileSync(localPath).toString("base64");
    const text = desc
      ? `${desc}。\n\n来源：${desc.includes("截图") ? "游戏内实拍" : "JOE's Addons for ABMC 模组资源包"}。`
      : `取自 JOE's Addons for ABMC 模组资源包。\n\n来源：模组资源文件 \`${localPath.replace(/\\/g, "/")}\`。`;
    try {
      const r = JSON.parse(await cdp.eval(`(async()=>{
        const bin = Uint8Array.from(atob(${JSON.stringify(b64)}), c => c.charCodeAt(0));
        const fd = new FormData();
        fd.append("action","upload"); fd.append("format","json"); fd.append("formatversion","2");
        fd.append("filename", ${JSON.stringify(wikiName)});
        fd.append("comment", "上传模组资源包贴图（JOE's Addons for ABMC）");
        fd.append("text", ${JSON.stringify(text)});
        fd.append("ignorewarnings", "1");
        fd.append("token", await window.__jafa.token());
        fd.append("file", new Blob([bin], { type: "image/png" }), ${JSON.stringify(wikiName)});
        const res = await fetch("${API}", { method:"POST", body: fd, credentials:"same-origin" });
        const j = await res.json();
        if (j.error) return JSON.stringify({ ok:false, msg: j.error.code + ": " + j.error.info });
        const u = j.upload || {};
        const w = u.warnings ? Object.keys(u.warnings).join(",") : "";
        return JSON.stringify({ ok:true, result: u.result, url: u.imageinfo && u.imageinfo.url, warn: w });
      })()`));
      console.log(`  ${r.ok ? "✅" : "❌"} ${wikiName} — ${r.result || r.msg}${r.warn ? "（警告：" + r.warn + "）" : ""}`);
      results.push({ 文件: wikiName, 结果: r.result || r.msg });
    } catch (e) {
      console.log(`  ❌ ${wikiName} — ${e.message}`);
      results.push({ 文件: wikiName, 结果: e.message });
    }
    await new Promise(r => setTimeout(r, 200));
  }
  const ok = results.filter(r => /Success/i.test(r.结果 || "")).length;
  console.log(`完成：${ok}/${results.length}`);
  return results;
}

// ---------------------------------------------------------------------- main
(async () => {
  const [cmd, a1, a2] = process.argv.slice(2);
  if (!cmd) { console.log(fs.readFileSync(__filename, "utf8").split("=== */")[0].split("*/")[1]); return; }
  const target = await pickTarget();
  const cdp = await CDP.connect(target.webSocketDebuggerUrl);
  try {
    if (cmd === "check") await cmdCheck(cdp);
    else if (cmd === "pages") await cmdPages(cdp, a1);
    else if (cmd === "dump") await cmdDump(cdp, a1);
    else if (cmd === "push") await cmdPush(cdp, a1 || "wiki", process.argv.includes("--dry"));
    else if (cmd === "upload") await cmdUpload(cdp, a1);
    else if (cmd === "eval") {
      await ensureOnWiki(cdp); await cdp.eval(HELPER);
      const expr = fs.readFileSync(a1, "utf8");
      console.log(await cdp.eval(expr));
    } else console.log("未知命令：", cmd);
  } finally { cdp.close(); }
})().catch(e => { console.error("错误:", e.message); process.exit(1); });

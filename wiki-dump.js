/* ============================================================================
   JOE's Addons for ABMC — wiki 内容导出脚本（浏览器控制台版）
   ----------------------------------------------------------------------------
   用途：把你的 Miraheze wiki 上**现有页面**的标题与 wikitext 全部导出成一个 JSON 文件，
         这样即使我这边被 Cloudflare 挡着，也能拿到你 wiki 的现状来比对、改写。

   用法：
     1. 浏览器登录你的 wiki，随便打开一个页面（保证同源）；
     2. F12 → Console → 把本文件全部内容粘贴进去 → 回车；
     3. 等它跑完，浏览器会自动下载 wiki-dump.json；
     4. 把这个 JSON 发给我（或告诉我它有多少个页面、哪些是本模组相关的老页面）。

   参数：
     NAMESPACES = [0, 14]  0=条目，14=分类；想连模板/模块一起导就加 10、828
     API        = "/w/api.php"（Miraheze 标准路径；若你的 wiki 不同请改）
   ============================================================================ */
(async () => {
  const API = "/w/api.php";
  const NAMESPACES = [0, 14];
  const log = (...a) => console.log("%c[wiki-dump]", "color:#1971c2;font-weight:bold", ...a);

  const post = async (params) => {
    const body = new URLSearchParams({ format: "json", formatversion: "2", ...params });
    const res = await fetch(API, { method: "POST", body, credentials: "same-origin" });
    const data = await res.json();
    if (data.error) throw new Error(`${data.error.code}: ${data.error.info}`);
    return data;
  };

  const who = await post({ action: "query", meta: "userinfo" });
  const user = who.query?.userinfo?.name;
  if (!user) { console.error("没有登录：请先登录 wiki 再运行。"); return; }
  log(`当前登录用户：${user}`);

  // ---- 1) 列出所有页面标题 ------------------------------------------------
  const titles = [];
  for (const ns of NAMESPACES) {
    let cont = null;
    do {
      const params = {
        action: "query", list: "allpages", aplimit: "500",
        apnamespace: String(ns), apfilterredir: "all"
      };
      if (cont) params.apcontinue = cont;
      const d = await post(params);
      const list = d.query?.allpages ?? [];
      titles.push(...list.map(x => x.title));
      cont = d.continue?.apcontinue ?? null;
      log(`命名空间 ${ns}：已列出 ${titles.length} 个标题…`);
    } while (cont);
  }
  log(`共 ${titles.length} 个页面，开始拉取正文…`);

  // ---- 2) 分批取 wikitext -------------------------------------------------
  const out = {};
  const BATCH = 50;
  for (let i = 0; i < titles.length; i += BATCH) {
    const batch = titles.slice(i, i + BATCH);
    const d = await post({
      action: "query", prop: "revisions", rvprop: "content|timestamp",
      rvslots: "main", titles: batch.join("|")
    });
    for (const pg of d.query?.pages ?? []) {
      const rev = pg.revisions?.[0];
      out[pg.title] = {
        内容: rev?.slots?.main?.content ?? "",
        最后修改: rev?.timestamp ?? "",
        长度: (rev?.slots?.main?.content ?? "").length
      };
    }
    log(`已取 ${Math.min(i + BATCH, titles.length)}/${titles.length}`);
  }

  // ---- 3) 下载成 JSON -----------------------------------------------------
  const payload = {
    导出时间: new Date().toISOString(),
    站点: location.origin,
    用户: user,
    页面数: Object.keys(out).length,
    页面: out
  };
  const blob = new Blob([JSON.stringify(payload, null, 1)], { type: "application/json" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "wiki-dump.json";
  a.click();
  log(`完成：已导出 ${Object.keys(out).length} 个页面 → wiki-dump.json`);
  log("页面一览：", Object.entries(out)
      .sort((x, y) => y[1].长度 - x[1].长度)
      .map(([t, v]) => `${v.长度}字 ${t}`).join(" | "));
})();

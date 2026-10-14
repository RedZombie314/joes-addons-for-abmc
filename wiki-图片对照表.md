# 页面图片对照表（19 / 19 全部已上传 ✅）

> **现状**：本模组所有页面用到的 **19 个 `File:` 名全部已上传并验证生效**，
> `Special:WantedFiles` / `Category:含有受损文件链接的页面` 现在**已经是空的**。
>
> 上传工具：`wiki-cdp.js` 的 `upload` 命令（映射文件 `upload-map.json` / `upload-map-shots.json`）
> ```powershell
> node wiki-cdp.js upload upload-map.json          # 13 张模组贴图
> node wiki-cdp.js upload upload-map-shots.json    # 6 张游戏内截图
> ```

## 一、取自模组资源包（13 张）✅

| wiki 上的文件名 | 来源（仓库文件） | 用在哪些页面 |
|---|---|---|
| `Beeper.png` | `textures/entity/beeper/beeper.png` | 苦力蜂 |
| `Blaze staff.png` | `textures/item/blaze_staff.png` | 烈焰手杖 |
| `Crafting table hat.png` | `textures/item/crafting_hat.png` | 工作台帽子 |
| `Glistering melon knife.png` | `textures/item/glistering_melon_knife.png` | 闪烁西瓜刀 |
| `Iron hook.png` | `textures/item/iron_hook.png` | 铁抓钩 |
| `Lucky block.png` | `textures/block/lucky_block.png` | 幸运方块 |
| `Support lucky block.png` | 同上（两者共用同一张贴图） | 支援幸运方块 |
| `Lucky dimension block.png` | `textures/block/lucky_dimension_block.png` | 幸运维度、幸运维度方块 |
| `Lucky selector.png` | `textures/entity/lucky_selector/outer.png` | 幸运选择器 |
| `Staff bee nest.png` | `textures/item/staff_bee_nest.png` | 蜂巢权杖 |
| `Staff crafting table.png` | `textures/item/staff_crafting_table.png` | 工作台权杖 |
| `Wart on a stick.png` | `textures/item/wart_on_a_stick.png` | 地狱疣生成器 |
| `Wings.png` | `textures/entity/wings.png` | 翅膀 |

`File:` 页面已标注来源：「取自 JOE's Addons for ABMC 模组资源包」。

## 二、游戏内截图（6 张）✅

| wiki 上的文件名 | 尺寸 | 用在哪个页面 | 内容 |
|---|---|---|---|
| `BeeBoss.png` | 1554×1356 | 蜜蜂Boss | 举着蜂巢权杖的 BeeBoss |
| `Fire charge launcher.png` | 972×1204 | 烈焰弹发射器 | 手持该物品 |
| `Explosive arrow.png` | 1200×1321 | 爆炸之箭 | 手持该物品 |
| `Orb of luck.png` | 1102×654 | 幸运核心 | INITIAL 阶段的悬空发光球 |
| `Player shell.png` | 1192×1279 | 玩家空壳 | 附体/变形空壳 |
| `Orb of luck temple.png` | 1773×1522 | 幸运核心神庙 | 整座石英阶梯神庙 |

`File:` 页面已标注来源：「JAFA 模组游戏内截图」。

## 三、上传后的核对方式（已执行过）

1. `action=query&prop=imageinfo` 确认 19 个 `File:` 全部存在（含尺寸）；
2. `action=parse` 抓页面 HTML，确认渲染出真实 `<img src="//static.wikitide.net/jafawiki/thumb/…">`，而不是红链；
3. `action=purge&forcelinkupdate=1` 刷新链接表后，`Category:含有受损文件链接的页面` 已经是**空分类**。

## 四、以后再传图的流程

在映射 JSON 里加一行即可（两种写法都支持）：

```json
{
  "Wiki 上的文件名.png": "本地路径.png",
  "另一个名字.png": { "path": "本地路径.png", "desc": "游戏内截图" }
}
```

然后 `node wiki-cdp.js upload 你的映射.json`。文件名必须与页面里 `[[File:…]]` 的写法**完全一致**（含大小写、空格与扩展名）。

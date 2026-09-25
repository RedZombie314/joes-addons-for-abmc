# 结构检测调试机制 — 临时参考文档

> 目的：记录本 mod（Joe's Addons for ABMC，1.21.1 NeoForge，包 `cn.autoforged.joes_addons_for_abmc`）当前已实现的「结构检测」调试功能与相关工程上下文，供新会话快速接续。用户清空聊天记录后请以本文档为准。

## 当前版本
- `gradle.properties` → `mod_version=5.4.11`（每次改动触发构建前必须递增版本号，默认只加第三位；构建会自动部署到 `E:\.minecraft\versions\生存档（mod版3）\.minecraft\versions\ABMC Mod Testing\mods`，旧 jar 会被清理）。

## 一、结构检测（调试用，多结构注册表）
**位置**：`src/main/java/cn/autoforged/joes_addons_for_abmc/ModMain.java` → `onDebugStructureRightClick(PlayerInteractEvent.RightClickBlock)`
**注册**：`NeoForge.EVENT_BUS.addListener(ModMain::onDebugStructureRightClick)`（约 1168 行）

**行为**：
- 玩家**空手**（主手为空）右击**检测方块**（某结构模板的锚点格）时，若其周围构成该模板描述的结构，聊天框输出绿色「检测成功！」。**多个结构依次尝试，任一命中即成功**。
- 结构注册表（`DEBUG_STRUCTURES`，约 6992 行）：`List<DebugStructureTemplate>`，每个模板 = `cells[w][v][u]`（u=列、v=行、w=层；尺寸任意）+ `anchor{w,v,u}`（检测方块在模板内的位置）。**后续新增结构只需在 `DEBUG_STRUCTURES` 里追加一个 `new DebugStructureTemplate(...)`**。
- **单元格类型**（`cells` 元素）：具体方块（如 `Blocks.NOTE_BLOCK`）表示该格**必须**是这个方块；`CELL_SPECIAL`（哨兵，约 6975 行）表示该格必须属于「特殊」类。**改结构只需改这个数组 + 锚点**。
- 当前结构（`DEBUG_STRUCTURES` 现有 5 个，依次尝试）：
  1. **1×2×3**（两层、每层一排三格）：第一层 音符盒 末地烛 末地烛；第二层 干草块 特殊 特殊。锚点 = 第一层离音符盒较近的那根末地烛（`anchor={0,0,1}`）。
  2. **1×3×1**（三层、每层一格，垂直叠放）：第一层 云杉栅栏；第二层 音符盒；第三层 南瓜。锚点 = 第一层云杉栅栏（`anchor={0,0,0}`）。
  3. **1×2×3**（两层、每层一排三格）：第一层 音符盒 红砖墙 红砖墙；第二层 黄绿色羊毛 特殊 特殊。锚点 = 第一层红砖墙（`anchor={0,0,1}`）。
  4. **1×2×3**（两层、每层一排三格）：第一层 音符盒 下界砖墙 下界砖墙；第二层 橡木木板 特殊 特殊。锚点 = 第一层下界砖墙（`anchor={0,0,1}`）。
  5. **1×2×3**（两层、每层一排三格）：第一层 音符盒 红色下界砖墙 红色下界砖墙；第二层 白色羊毛 特殊 特殊。锚点 = 第一层红色下界砖墙（`anchor={0,0,1}`）。
- **容错判定（48 种朝向）**：默认先沿**从南到北**检测；失败则依次尝试**从东到西**、**从上到下**，以及这三个方向的**镜像方向**（反向）和**旋转方向**——共 `6 方向 × 4 旋转 × 2 镜像 = 48` 种朝向（正好覆盖立方体全部 48 种空间朝向，即 3!×2³；非立方尺寸会有冗余朝向，无害）。**任一朝向命中即成功并跳过剩余检测**。
- 代码流程：`onDebugStructureRightClick` → `debugAnchorMatches(state, tpl)`（锚点格预检）→ `checkDebugStructure(level, pos, tpl)`（枚举 6 主轴向：`{0,0,-1}`南→北默认、`{-1,0,0}`东→西、`{0,-1,0}`上→下及各自反向，套 4 旋转 × 2 镜像）→ `checkDebugStructureAt(...)`（按模板实际尺寸 + 锚点偏移铺格逐一比对）。
- 仅服务端处理（`event.getLevel().isClientSide()` 直接 return）。

**重要坑（同刻去重）**：NeoForge 的 `RightClickBlock` 对**一次右键会触发多次**（客户端+服务端双 fire / 双事件），会导致重复提示。处理方式是**同游戏刻去重**：
```java
String key = player.getUUID() + ":" + event.getLevel().dimension().location() + ":" + event.getPos();
long now = event.getLevel().getGameTime();
Long last = DEBUG_STRUCTURE_LAST.get(key);
if (last != null && last == now) return;
DEBUG_STRUCTURE_LAST.put(key, now);
```
去重表字段：`private static final Map<String,Long> DEBUG_STRUCTURE_LAST = new HashMap<>();`（声明在 `ENCHANTING_TABLE_LAST_DEC` 附近，约 6918 行）。

**特殊类方块（以后统称「特殊」）**（2026-09-21 新增，已部署）：
- 定义：**不具备碰撞箱的方块**集合。用户给出的基础清单：空气、水源、海草、草、蕨、高草、高蕨、各种花、各种大型花、蘑菇、下界菌、各种树苗、各种庄稼等；遗漏部分由实现补全（见下）。
- 代码位置（`ModMain.java`）：
  - 集合字段：`private static final Set<Block> SPECIAL_BLOCKS`（声明在 `DEBUG_STRUCTURE_LAST` 之后，约 6921 行）。
  - 判定函数：`private static boolean isSpecialBlock(BlockState state)`（声明在 `onDebugStructureRightClick` 之前，约 7135 行）。
- 覆盖范围（按注释分组）：
  - 空气与流体：空气/洞穴空气/虚空空气、水（含流动水）、熔岩、气泡柱。
  - 草/蕨/海草：草、蕨、高草、高蕨、海草、高海草。
  - 各种花（小型，含凋零玫瑰、火把花）与各种大型花（含瓶子草）。
  - 蘑菇/下界菌：红/棕蘑菇、绯红/诡异菌、绯红/诡异菌索、下界苗。
  - 各种树苗：6 种主世界树苗 + 樱花树苗、红树胎生苗、竹笋。
  - 各种庄稼：小麦/胡萝卜/马铃薯/甜菜/下界疣/火把花作物/瓶子草作物 + 南瓜/西瓜茎。**甜浆果丛、可可豆有碰撞箱，不纳入**。
  - 藤蔓类：藤蔓、洞穴藤蔓、垂泪藤、缠怨藤、海带、枯灌木、垂根、发光地衣、幽匿脉络。
  - 其余无碰撞箱方块：火把（含墙挂/灵魂/红石变体）、红石线、4 种铁轨、11 种按钮、13 种压力板、16 色地毯、绊线、拉杆、火、结构虚空。注意：MC 中「线」放置后即绊线方块 `TRIPWIRE`，无独立弦方块。
- 当前已**接入结构检测**：模板中用 `CELL_SPECIAL` 标记的格子用 `isSpecialBlock` 判定必须属于「特殊」类。

## 二、废弃传送门藏宝图（与本调试机制相关的结构定位参考）
**位置**：`src/main/java/cn/autoforged/joes_addons_for_abmc/item/RuinedPortalMapItem.java`（继承原版 `MapItem`）
**注册**：`ModItems` 里 id `ruined_portal_map`；创造栏已加入；模型 `models/item/ruined_portal_map.json`（默认空地图 + `overrides` 按物品属性 `joes_addons_for_abmc:located` 切换到 `ruined_portal_map_filled.json`）。

关键实现（供参考结构定位写法）：
- **结构 tag 必须放在 `data/minecraft/tags/worldgen/structure/ruined_portal.json`**（不是 `tags/structure/`，否则 `findNearestMapStructure` 永远返回 null）。
- 定位：`serverLevel.findNearestMapStructure(RUINED_PORTAL_TAG, playerPos, 10000, false)`，tag 用 `TagKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("ruined_portal"))`。
- **地图以结构位置为中心**（不是玩家）：`MapItem.create(serverLevel, portalPos.getX(), portalPos.getZ(), (byte)2, true, false)`，写 `DataComponents.MAP_ID`，再 `MapItemSavedData.addDecoration(MapDecorationTypes.TARGET_X, serverLevel, "ruined_portal", portalPos.getX(), portalPos.getZ(), 0.0, null)`。
- 客户端图标双态：`ClientEvents` 注册物品属性 `located`（有 MAP_ID=1 否则 0），模型 override 切换空地图/已填地图；手持/展示框渲染走 `instanceof MapItem` 的 `MapRenderer`（自动显示地形+红 X）。
- 战利品表：`data/minecraft/loot_table/chests/buried_treasure.json`（必掉 1 份）、`shipwreck_treasure.json`（rolls 0.3）。

## 三、同类工程约定（沿用）
- **RightClickBlock 双 fire 是常见坑**：凡是在 `RightClickBlock` 里做一次性动作（发消息/减计数/召实体），都建议做同刻或短窗口去重（参考 `ENCHANTING_TABLE_LAST_DEC`、`DEBUG_STRUCTURE_LAST` 的写法）。
- 构建：`.\gradlew.bat build --console=plain`（cwd 项目根）；编译错误先修到 `compileJava` 通过。
- 语言：中英 `lang` 文件都要补；新物品/实体记得加创造栏、模型 json。

## 四、下一步（若新会话接手）
- 多结构注册表（`DEBUG_STRUCTURES`）已接入检测输出。后续新增结构只需追加 `new DebugStructureTemplate(...)`（模板尺寸/方块/锚点均可配置）；用户后续可能补充更多结构或「特殊」类的遗漏方块。
- 用户尚未提出新需求，但若有新结构检测/藏宝图相关改动，请先读本文档对应的类与方法，再按上述约定实现。

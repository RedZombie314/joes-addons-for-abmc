# 结构检测调试机制 — 临时参考文档

> 目的：记录本 mod（Joe's Addons for ABMC，1.21.1 NeoForge，包 `cn.autoforged.joes_addons_for_abmc`）当前已实现的「结构检测」调试功能与相关工程上下文，供新会话快速接续。用户清空聊天记录后请以本文档为准。

## 当前版本
- `gradle.properties` → `mod_version=6.7.0`（每次改动触发构建前必须递增版本号，默认只加第三位；构建会自动部署到 `E:\.minecraft\versions\生存档（mod版3）\.minecraft\versions\ABMC Mod Testing\mods`，旧 jar 会被清理）。

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

## 三、幸运之庙（lucky_temple）自然生成（6.2.19 新增）
需求：只生成在**平原**生物群系、**海平面以上**的**地表**，结构最低一层方块**取代生成高度处的方块**（嵌入地表），
结构体积内的方块被整体替换（无掉落物），生成概率参考「平原村庄在平原」。

**与女巫小屋的区别**：这是本 mod 第一个**独立的新结构**（不拦截原版结构），走原版正规路线：

| 文件 | 作用 |
| --- | --- |
| `worldgen/ModStructures.java` | 注册 `StructureType`（`joes_addons_for_abmc:lucky_temple`）与 `StructurePieceType`；在 `ModMain` 构造函数里 `register(modEventBus)` |
| `worldgen/LuckyTempleStructure.java` | `Structure` 子类：算生成点、地形判定、决定「嵌入」高度 |
| `worldgen/LuckyTemplePiece.java` | `TemplateStructurePiece` 子类：直接用原版基类放模板（无需自己写放置/裁剪） |
| `data/joes_addons_for_abmc/structure/lucky_temple.nbt` | 建筑模板（7×5×7，由用户提供的结构文件） |
| `data/joes_addons_for_abmc/worldgen/structure/lucky_temple.json` | `type` + `biomes: minecraft:plains` + `step: surface_structures` + `terrain_adaptation: none` |
| `data/joes_addons_for_abmc/worldgen/structure_set/lucky_temples.json` | 生成概率（`random_spread`：spacing 34 / separation 8 / salt 20250921；与村庄同参数，salt 不同；另有 `exclusion_zone` 避开 `minecraft:villages` 8 区块） |

**关键实现点（结论已核对原版反编译源码）**：
- 生成高度用 `ChunkGenerator.getFirstOccupiedHeight(..., WORLD_SURFACE_WG, ...)`（= **最上面那块实心方块的 y**，
  原版沙漠神殿/村庄的「嵌入地表」就是这个语义），对底面 49 列逐列采样取**最低**值作为结构最小角的 y：
  最低那列的最底层正好盖住地表方块 → 嵌入；更高的列被模板里的**空气**替换掉 → 7×7 平台被削平。
- `minSurface > chunkGenerator.getSeaLevel()`（主世界 63）：只在水面之上的地表生成；水里的列地表 ≤ 海平面直接被否掉。
- 底面 49 列高差 > 3 视为地形过陡，不生成（`MAX_SURFACE_SPREAD`）。
- 结构放在**区块正中**：7×7（±3）必定落在同一个区块内，不会被区块边界切块。
- `TemplateStructurePiece` 用 `template.placeInWorld(...)` 逐格 `setBlock(pos, state, 2)`：模板里的空气同样写入
  → 结构体积内地形的凸起被替换掉；因为是 setBlock 而非破坏方块，**不会产生掉落物**。区块存盘后重新读入时按
  `Template` 字段重新加载模板，不依赖内存引用。
- 概率参数与 `data/minecraft/worldgen/structure_set/villages.json` 完全一致（spacing 34 / separation 8 / linear），
  所以「尝试生成」的密度和平原村庄在平原里的一致；建筑蓝图放哪一行由各结构自己的 `findGenerationPoint` 决定。
- 游戏内查看新结构（旧区块不会补生成）：新世界或未探索区域里用 `/locate structure joes_addons_for_abmc:lucky_temple`。

## 四、幸运宝珠神庙（orb_of_luck_temple）·幸运维度一次性生成（6.3.7 新增）
需求：把 `orb_of_luck_temple.nbt`（26×20×26 的阶梯石英神庙）生成到**幸运维度**；**尽量选高处的平缓地形（小山坡顶部）**；
**每个存档只生成一座**；**不在玩家初次进入幸运维度所在坐标周围 5 个区块内**生成。

**为什么不用 worldgen（structure_set）**：上面第 3、4 条数据包表达不了。所以做成
「玩家第一次进入幸运维度时」在主线程选址 + 放置的一次性逻辑：

| 位置 | 作用 |
| --- | --- |
| `worldgen/OrbOfLuckTemple.java` | 全部逻辑：记录初入坐标 → 选址 → 分帧加载区块 → 一次性放置；由 `ModMain.onServerTickPre` 每刻调用 |
| `data/joes_addons_for_abmc/structure/orb_of_luck_temple.nbt` | 建筑模板（用户提供） |
| `ModMain.SharedCounts` 新增字段 | `luckyDimEntryX/Z`（初入坐标）、`orbTemplePlaced`（已生成）、`orbTempleTarget`（已选址未放置，跨会话恢复用） |

**关键实现点**：
- **初入坐标**：服务端每刻扫一遍在线玩家，第一次发现有玩家身处 `joes_addons_for_abmc:lucky_dimension` 时，
  把其坐标写进 `SharedCounts`（存主世界维度数据 → 每存档一份，多人共享）。只在第一次记录。
- **选址**：以初入坐标为中心，环带 80~192 格（5~12 区块）用 **16 格网格**粗扫
  `ChunkGenerator.getBaseHeight(..., WORLD_SURFACE_WG, ...)`（噪声高度图，**不需要加载区块**），
  按高度从高到低取最多 16 个候选，用 **8 格步长**探一遍整座 26×26 底面：**高差 ≤ 5** 就采用
  （= 山顶平台而不是陡坡）；近处找不到平台就把半径放到 512 格再找一遍；再不行就取高差最小的候选兜底。
- **排除区**：候选结构覆盖的区块与「初入坐标所在区块」的切比雪夫区块距离必须 **> 5**（`insideExclusion`），
  否则跳过该候选——即整体落在初入坐标周围 5 区块之外。
- **分帧加载区块**：26×26 最多跨 3×3 个区块，每刻最多 `getChunk` 生成 2 个（`hasChunk` 判断，不需要指针，
  天然可跨刻/跨会话恢复），全部就绪后再放置，避免单刻同步生成太多区块卡顿。
- **底面高度**：区块就绪后用**真实高度图** `level.getHeight(MOTION_BLOCKING_NO_LEAVES, x, z) - 1` 取
  底面 26×26 里**最低**的地表方块 y 作为结构最小角 → 底面永远踩在地上、不悬空；高于该高度的地形会被模板里的
  **空气**替换掉（跟幸运之庙一个套路）。flag `2|16` 直接 setBlock，**没有掉落物**。
- **只生成一次**：放完把 `orbTemplePlaced = true` 写进存档，之后每刻直接 return。
- **神庙里的宝珠**：放置完模板后，从模板里**动态找**那处 2×2×2 雕纹石英块平台
  （`template.filterBlocks(..., Blocks.CHISELED_QUARTZ_BLOCK, false)`，不写死坐标），在它 `(minX+maxX+1)/2, (minZ+maxZ+1)/2`
  的中心、平台顶面往上 `ORB_HEIGHT_ABOVE_PLATFORM`(2) 格处生成一只 `orb_of_luck`（`setOrbState(INITIAL)`，
  核心碰撞箱底面对齐坐标 → 这个 y 就是它悬停的层）。**必须 `setPersistenceRequired()`**：
  否则玩家在 128 格外时会被 `checkDespawn` 当野怪刷掉，宝珠就没了。
- 幸运维度 `lucky_plains` 生物群系**没有任何 features**（见 `ModBiomes`），所以高度图就是纯噪声地形，
  选址很干净；该维度也不会有原版结构（生物群系不匹配）。
- 排查：日志里搜 `[orb-temple]`（会打印初入坐标、选址、最终原点与地形高差）。

## 五、同类工程约定（沿用）
- **RightClickBlock 双 fire 是常见坑**：凡是在 `RightClickBlock` 里做一次性动作（发消息/减计数/召实体），都建议做同刻或短窗口去重（参考 `ENCHANTING_TABLE_LAST_DEC`、`DEBUG_STRUCTURE_LAST` 的写法）。
- **女巫 Boss 小屋的出货概率有两处、共用同一个保底计数**，改概率必须一起改（6.7.0 起由 `ModMain.WITCH_BOSS_CHANCE_DENOMINATOR`=10、`ModMain.WITCH_BOSS_PITY_HUTS`=20 两个常量统一驱动）：
  1. `ModMain.genRollBossHut(...)`（worldgen worker 线程，由 `SwampHutPieceMixin` 调用）＝把原版沼泽小屋**换成** Boss 小屋；
  2. `ModMain.handleWitchHutBossSpawn(...)`（主线程，小屋里的女巫刷出时）＝没被换掉的小屋里把**女巫变异成 Boss**的兜底。
  两者共享 `SharedCounts.witchHutCount` / `GEN_WITCH_HUT_COUNT`（连续未出货的小屋数），到保底数下一座必出。
- **配置默认值只在代码里**：`ModConfig`（`ModConfigSpec`）在首次启动时生成 `config/joes_addons_for_abmc-common.toml`，NeoForge **不会**读取 mod jar 里的默认配置（只能从实例目录的 `defaultconfigs/` 复制）。要随下载附带"默认配置"，就是手工把一份 toml 放在 `defaultconfigs/`（或让玩家丢进 `config/`）——仓库里那份是 `uploads/joes_addons_for_abmc-common.toml`。
- 构建：`.\gradlew.bat build --console=plain`（cwd 项目根）；编译错误先修到 `compileJava` 通过。
- 语言：中英 `lang` 文件都要补；新物品/实体记得加创造栏、模型 json。

## 六、下一步（若新会话接手）
- 多结构注册表（`DEBUG_STRUCTURES`）已接入检测输出。后续新增结构只需追加 `new DebugStructureTemplate(...)`（模板尺寸/方块/锚点均可配置）；用户后续可能补充更多结构或「特殊」类的遗漏方块。
- 「幸运之庙」若要调概率只改 `worldgen/structure_set/lucky_temples.json`（spacing 调大 = 更稀有），要换生物群系只改 `worldgen/structure/lucky_temple.json` 的 `biomes`（也可换成 `#tag` 或数组，例如把 `minecraft:sunflower_plains` 一起加进来）。
- 「幸运宝珠神庙」的选址常量都在 `OrbOfLuckTemple` 顶部（搜索半径/网格步长/允许高差/排除区块数/每刻加载区块数）；要重测可以删掉存档里的 `jafa_shared_counts`（`data/jafa_shared_counts.dat`）或把 `orb_temple_placed` 改回 0。
- 用户尚未提出新需求，但若有新结构检测/藏宝图相关改动，请先读本文档对应的类与方法，再按上述约定实现。

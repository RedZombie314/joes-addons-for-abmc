# Debug Session: witch-hut-teleport-bug

- **Status**: [OPEN]
- **Issue**: 女巫回家/补货时传送到相近 XZ 坐标的地底，而非小屋生成点；`/jafa witchhut` 生成的女巫位置不对；需将 `/jafa witchhut` 小屋下移三格与自然生成对齐。
- **Log File**: `E:\.minecraft\versions\生存档（mod版3）\.minecraft\versions\ABMC Mod Testing\logs\latest.log`
- **日志前缀**: `[DBG-HUT]`

## Reproduction Steps
1. 让女巫Boss离家超过 50 格，或把某类药水打到低余量触发补货，观察是否传送到地底。
2. 执行 `/jafa witchhut`，观察小屋底部高度与女巫生成位置。

## Hypotheses & Verification
| ID | Hypothesis | Likelihood | Effort | Evidence |
|----|------------|------------|--------|----------|
| A | `resupplyWitchBoss` 直接用 home 原始坐标传送，未走 `findPortalExitNear` 安全落点搜索 | High | Low | Pending |
| B | 自然生成 home.y 记为 `oy+4`（结构内部），而女巫实际站立 `oy+7`，home 记录在错误高度 | High | Low | Pending |
| C | `/jafa witchhut` 结构 origin 用玩家脚下（未下移3格），女巫 spawn 用 `center+(0.5,1,0.5)` 而非 `HUT_BOSS_OFFSET` | High | Low | Pending |

## Log Evidence
[待收集]

## Verification Conclusion
[待填写]

# Debug: 玩家空壳/生物壳无法复原

- Session: shell-transmutation-revert
- Status: [OPEN]
- 症状: 玩家空壳与刷怪蛋生物壳在倒计时结束或变形解药下均无法复原为原生物；生物壳也不免疫变形药水。

## 复现步骤
1. 用带名字的命名牌 + 准变形药水酿出玩家空壳药水，砸向玩家/生物。
2. 用刷怪蛋 + 准变形药水酿出生物壳药水，砸向玩家/生物。
3. 等待倒计时结束 / 扔变形解药。
4. 观察：壳体未复原为原生物。

## 假设（Hypotheses）
- H1: 壳体从未被加入 LIVING_SHELLS 地图（performPlayerShellTransmutation / performMobShellTransmutation 未执行或未 put）。
- H2: 壳体被加入 LIVING_SHELLS，但 onEntityLeaveLevel 立即把它移除了（addFreshEntity 触发了 leave 事件）。
- H3: tickLivingShells 未逐刻递减（壳体找不到 / 倒计时未推进）。
- H4: 倒计时已到 0，但 revertLivingShell 复原失败（玩家未恢复 / 生物未生成）。
- H5: 变形解药 applyAntidoteSplash 未命中壳体（半径判断 / map 为空 / 解药未识别）。

## 插桩点
- onMobEffectAdded：记录效果施加、是否免疫。
- performTransmutation / performPlayerShellTransmutation / performMobShellTransmutation：记录创建、remainingTicks、LIVING_SHELLS 大小。
- tickLivingShells：记录每刻递减与归零回退。
- onEntityLeaveLevel：记录壳体离开。
- applyAntidoteSplash：记录解药命中。

## 证据（来自 latest2_10_2.log）

每个壳体在创建后、下一 tick 立即从地图移除：

```
mobShell CREATED uuid=de5e97c9-... mapSize=1
tickLivingShells: SHELL MISSING/GONE uuid=de5e97c9-... alive=false findNull=true mapSize=1
```

- 壳体确实被 addFreshEntity 且放入 LIVING_SHELLS（mapSize=1）。
- 但 `level.getEntity(uuid)` 立即返回 null（findNull=true），因此被 tick 从地图移除。
- 因此 H3 的“壳体找不到”成立；H2（onEntityLeaveLevel 移除）排除（无 leave 日志）。
- 从地图移除后，后续变形药水命中该壳体时 `LIVING_SHELLS.containsKey` 为 false，免疫失效（再次被变形）—— 对应需求③。

## 本轮新增诊断（2.10.2 重出）
- mobShell afterAdd: 记录 `alive / inLevel / sameLevel / getEntity`，判断 addFreshEntity 后实体是否真在 level 中。
- tickLivingShells crossDimFound: 跨维度查找该 uuid，判断实体是否在别的维度。

## 根因确认（2.10.2 实测日志）
```
mobShell afterAdd: alive=true inLevel=true sameLevel=true getEntity=Husk[..., l='ServerLevel[showcase_youtube]']
mobShell CREATED ... mapSize=1
tickLivingShells: SHELL MISSING/GONE ... findNull=true mapSize=1
tickLivingShells: crossDimFound=true type=minecraft:husk
```
- 壳体确实被 addFreshEntity 进主世界（afterAdd 时 getEntity 能查到）。
- 但下一 tick 主世界 `getEntity` 返回 null，而 getAllLevels 跨维度能查到（crossDimFound=true）。
- 结论：**壳体被本 mod 的传送门系统传送到了其他维度**（主世界→物理/幸运维度等），导致原按维度逐级 `tickLivingShells(level)` 用主世界查不到 → 立即从地图移除 → 倒计时失效、免疫失效。

## 修复（2.10.3）
- `tickLivingShells` 改为**全局跨维度**处理：接收 `MinecraftServer`，遍历所有维度查壳体，只递减一次，倒计时到期在壳体所在维度 `revertLivingShell`。
- 不再按维度逐级调用，避免壳体被传送后查不到而被误删。
- 由此 ① 倒计时复原、② 免疫 均恢复（壳体不再被误删出地图）。
- afterAdd 日志补充 `dim` 字段便于确认壳体所在维度。
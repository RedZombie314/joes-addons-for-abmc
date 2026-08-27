
Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/

Troubleshooting - staff models
==========

如果某把权杖在游戏内渲染成紫黑方块（缺失模型），最常见的原因是
**物品模型 JSON 中包含 Minecraft 不允许的旋转角度**。Minecraft 的物品/方块
模型只接受 `0 / ±22.5 / ±45` 四种旋转角度（见
`net/minecraft/client/renderer/block/model/BlockElement.java` 的
`getAngle` 校验），而 Blockbench 导出/原始文件里经常使用其它任意角度
（例如附魔台权杖早期版本的 `7.5`）。这样的模型在加载时会被直接丢弃，
权杖就会显示为紫黑方块。

修复方法：把模型里所有 `rotation.angle` 改成合法的角度（通常用 `0`），
并确认 `textures` 指向的贴图路径（`<modid>:item/<name>`）确实存在对应的
PNG 文件。纹理命名空间和文件位置不一致同样会导致紫黑贴图。

If a staff renders as purple/black squares (missing model) in-game, the most
common cause is an **illegal rotation angle in the item model JSON**.
Minecraft block/item models only accept rotation angles of `0 / ±22.5 / ±45`
(validated in `BlockModel$Deserializer#getAngle`), while Blockbench export files
frequently contain arbitrary angles (e.g. the enchantment-table staff used `7.5`).
Such a model fails to load and the staff falls back to the missing-model texture.
Fix: replace every `rotation.angle` with a valid angle (usually `0`) and make sure
the `textures` path (`<modid>:item/<name>`) resolves to an existing PNG file.


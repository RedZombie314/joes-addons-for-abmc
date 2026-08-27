package cn.autoforged.joes_addons_for_abmc.potion;

import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

public class TransmutationBrewingRecipe implements IBrewingRecipe {

    // 客户端在材质加载完成后注入的主色提取函数；-1 表示无法获取（非客户端环境）。
    // 之所以需要客户端：服务端的资源管理器不包含 textures 资源，只有客户端才有贴图。
    private static ToIntFunction<Item> clientColorProvider = item -> -1;

    public static void setClientColorProvider(ToIntFunction<Item> provider) {
        if (provider != null) {
            clientColorProvider = provider;
        }
    }

    private static ItemStack copyToItemType(ItemStack input, net.minecraft.world.item.Item targetItem,
            PotionContents contents, Holder<Potion> potion) {
        String itemType = input.getOrDefault(ModDataComponents.ITEM_TYPE.get(), null);
        int existingColor = contents.customColor().orElse(0x9370DB);
        ItemStack result = new ItemStack(targetItem);
        result.set(DataComponents.POTION_CONTENTS,
            new PotionContents(Optional.of(potion),
                Optional.of(existingColor), List.of()));
        if (itemType != null) {
            result.set(ModDataComponents.ITEM_TYPE.get(), itemType);
        }
        // 保留“变形为 XX”的自定义名称（喷溅/滞留转换时命名保持不变）
        Component customName = input.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            result.set(DataComponents.CUSTOM_NAME, customName);
        }
        return result;
    }

    private static boolean isPotionItem(ItemStack stack) {
        return stack.getItem() == Items.POTION
            || stack.getItem() == Items.SPLASH_POTION
            || stack.getItem() == Items.LINGERING_POTION;
    }

    @Override
    public boolean isInput(ItemStack input) {
        if (!isPotionItem(input)) return false;
        PotionContents contents = input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (contents.potion().isEmpty()) return false;
        Holder<Potion> potion = contents.potion().get();
        ResourceKey<Potion> key = potion.getKey();
        return key.equals(ModPotions.PRE_TRANSMUTATION.getKey())
            || key.equals(ModPotions.TRANSMUTATION.getKey())
            || key.equals(ModPotions.LONG_TRANSMUTATION.getKey());
    }

    @Override
    public boolean isIngredient(ItemStack ingredient) {
        if (ingredient.isEmpty()) return false;
        if (ingredient.getItem() == Items.REDSTONE) return true;
        // 命名牌（命名后）与刷怪蛋的最大堆叠数都是 1，需单独放行
        if (ingredient.getItem() == Items.NAME_TAG) return true;
        if (ingredient.getItem() instanceof net.minecraft.world.item.SpawnEggItem) return true;
        return ingredient.getMaxStackSize() > 1;
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (!isInput(input) || !isIngredient(ingredient)) return ItemStack.EMPTY;
        if (ingredient.isEmpty()) return ItemStack.EMPTY;

        PotionContents contents = input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (contents.potion().isEmpty()) return ItemStack.EMPTY;
        Holder<Potion> inputPotion = contents.potion().get();
        ResourceKey<Potion> inputKey = inputPotion.getKey();

        boolean isPreTransmutation = inputKey.equals(ModPotions.PRE_TRANSMUTATION.getKey());

        if (ingredient.getItem() == Items.REDSTONE) {
            if (isPreTransmutation) return ItemStack.EMPTY;
            String itemType = input.getOrDefault(ModDataComponents.ITEM_TYPE.get(), null);
            int existingColor = contents.customColor().orElse(0x9370DB);

            ItemStack result = new ItemStack(input.getItem());
            result.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.of(ModPotions.LONG_TRANSMUTATION),
                    Optional.of(existingColor), List.of()));
            if (itemType != null) {
                result.set(ModDataComponents.ITEM_TYPE.get(), itemType);
            }
            // 延时版保持“变形为 XX”命名不变
            Component customName = input.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                result.set(DataComponents.CUSTOM_NAME, customName);
            }
            return result;
        }

        if (ingredient.getItem() == Items.GUNPOWDER && input.getItem() == Items.POTION) {
            return copyToItemType(input, Items.SPLASH_POTION, contents, inputPotion);
        }

        if (ingredient.getItem() == Items.DRAGON_BREATH && input.getItem() == Items.SPLASH_POTION) {
            return copyToItemType(input, Items.LINGERING_POTION, contents, inputPotion);
        }

        if (!isPreTransmutation) return ItemStack.EMPTY;

        // 带有自定义名称的命名牌：酿出“玩家空壳变形药水”，将生物变成对应名字的玩家空壳。
        // 把名字以 Base64 编码进 ITEM_TYPE，避免名字中的特殊字符影响 ResourceLocation 解析。
        if (ingredient.getItem() == Items.NAME_TAG
            && ingredient.getOrDefault(DataComponents.CUSTOM_NAME, null) != null) {
            String shellName = ingredient.getHoverName().getString();
            String encoded = "player_shell:" + Base64.getEncoder()
                .encodeToString(shellName.getBytes(StandardCharsets.UTF_8));
            ItemStack result = new ItemStack(input.getItem());
            result.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.of(ModPotions.TRANSMUTATION),
                    Optional.of(0x50C878), List.of()));
            result.set(ModDataComponents.ITEM_TYPE.get(), encoded);
            // 命名：“变形为 玩家名”（例：变形为 Dream）
            result.set(DataComponents.CUSTOM_NAME,
                Component.literal("变形为 " + shellName));
            return result;
        }

        // 刷怪蛋：酿出“生物变形药水”，将生物变成刷怪蛋对应的生物（使用其 AI）。
        // 编码为 mob_shell:<实体类型ID>，供变形流程里生成对应生物。
        if (ingredient.getItem() instanceof net.minecraft.world.item.SpawnEggItem spawnEgg) {
            net.minecraft.world.entity.EntityType<?> eggType =
                spawnEgg.getType(ingredient);
            if (eggType != null) {
                String encoded = "mob_shell:"
                    + BuiltInRegistries.ENTITY_TYPE.getKey(eggType);
                ItemStack result = new ItemStack(input.getItem());
                result.set(DataComponents.POTION_CONTENTS,
                    new PotionContents(Optional.of(ModPotions.TRANSMUTATION),
                        Optional.of(0x8B4513), List.of()));
                result.set(ModDataComponents.ITEM_TYPE.get(), encoded);
                // 命名：“变形为 生物名”（例：变形为 尸壳），保留翻译组件按客户端语言解析
                result.set(DataComponents.CUSTOM_NAME,
                    Component.literal("变形为 ").append(eggType.getDescription()));
                return result;
            }
        }

        if (ingredient.getMaxStackSize() <= 1) return ItemStack.EMPTY;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(ingredient.getItem());

        // 黑名单：带方块实体（可储存物品）的方块不能作为变形目标（变身后是空白方块且会死亡），
        // 用这些方块酿造变形药水时，结果退化为随机变形药水“变形为§krandom”。
        if (BuiltInRegistries.BLOCK.containsKey(itemId)
            && BuiltInRegistries.BLOCK.get(itemId) instanceof net.minecraft.world.level.block.EntityBlock) {
            ItemStack random = new ItemStack(input.getItem());
            random.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.of(ModPotions.TRANSMUTATION),
                    Optional.of(0x9370DB), List.of()));
            random.set(DataComponents.CUSTOM_NAME, Component.literal("变形为§krandom"));
            return random;
        }

        String itemTypeStr = itemId.toString();

        int medianColor = calculateMedianColor(ingredient.getItem());

        ItemStack result = new ItemStack(input.getItem());
        result.set(DataComponents.POTION_CONTENTS,
            new PotionContents(Optional.of(ModPotions.TRANSMUTATION),
                Optional.of(medianColor), List.of()));
        result.set(ModDataComponents.ITEM_TYPE.get(), itemTypeStr);
        // 命名：“变形为 物品/方块名”（例：变形为 骨头 / 变形为 黑石），保留翻译组件按客户端语言解析
        result.set(DataComponents.CUSTOM_NAME,
            Component.literal("变形为 ").append(ingredient.getHoverName()));
        return result;
    }

    /**
     * 从加入的物品/方块材质中提取中位主色。
     * 优先尝试对应的 item 贴图，其次尝试方块贴图；把所有不透明像素的颜色值升序排序后取中位数。
     * 若无法读取材质则退回默认颜色。
     */
    private static int calculateMedianColor(Item item) {
        // 优先用客户端（材质在客户端资源管理器中）
        int clientColor = clientColorProvider.applyAsInt(item);
        if (clientColor != -1) return clientColor;

        // 非客户端环境（如纯专用服务器）：退回服务端资源管理器读取贴图
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ResourceManager resourceManager = server != null ? server.getResourceManager() : null;

        List<Integer> colors = new java.util.ArrayList<>();
        if (resourceManager != null) {
            ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(item);
            String ns = itemKey.getNamespace();
            String path = itemKey.getPath();

            java.util.List<ResourceLocation> candidateTextures = new java.util.ArrayList<>();
            candidateTextures.add(ResourceLocation.fromNamespaceAndPath(ns, "textures/item/" + path + ".png"));
            Block block = BuiltInRegistries.BLOCK.get(itemKey);
            if (block != null) {
                candidateTextures.add(ResourceLocation.fromNamespaceAndPath(ns, "textures/block/" + path + ".png"));
            }

            for (ResourceLocation texturePath : candidateTextures) {
                Optional<Resource> resource = resourceManager.getResource(texturePath);
                if (resource.isEmpty()) continue;
                try (InputStream in = resource.get().open()) {
                    BufferedImage image = ImageIO.read(in);
                    if (image == null) continue;
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            int argb = image.getRGB(x, y);
                            int alpha = (argb >>> 24) & 0xFF;
                            // 跳过全透明像素，避免与背景混色
                            if (alpha < 128) continue;
                            colors.add(argb & 0xFFFFFF);
                        }
                    }
                } catch (Exception ignored) {
                    // 忽略单张贴图解码失败
                }
                if (!colors.isEmpty()) break;
            }
        }

        if (colors.isEmpty()) return 0x9370DB;
        colors.sort(Integer::compareTo);
        return colors.get(colors.size() / 2);
    }
}

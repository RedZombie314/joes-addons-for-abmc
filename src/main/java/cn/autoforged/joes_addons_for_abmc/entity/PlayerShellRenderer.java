package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家空壳的渲染器：与玩家渲染同套机制（继承 LivingEntityRenderer），
 * 因此模型朝向、缩放、阴影、位置计算与真实玩家完全一致，不会再出现颠倒/幼体/浮空问题。
 *
 * 皮肤规则：
 * - SkinTexture 为空：根据实体 UUID 生成一个稳定的默认皮肤（Steve/Alex）。
 * - SkinTexture 为玩家名：在后台线程解析该玩家并下载其皮肤，注册为本机动态纹理，
 *   完成后稳定显示，不再在默认与正版皮肤之间来回闪烁。
 */
@OnlyIn(Dist.CLIENT)
public class PlayerShellRenderer extends LivingEntityRenderer<PlayerShellEntity, PlayerModel<PlayerShellEntity>> {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerShellRenderer");

    private final PlayerModel<PlayerShellEntity> slimModel;
    private final PlayerModel<PlayerShellEntity> wideModel;

    // 每帧使用的皮肤数据（在 render 中解析，供 getTextureLocation 复用）
    private PlayerSkinData currentSkin;

    // 按玩家名缓存皮肤加载状态，避免每帧重复发起网络请求
    private static final Map<String, LoadedSkin> SKIN_CACHE = new HashMap<>();

    private record PlayerSkinData(ResourceLocation texture, boolean slim) {
    }

    private static class LoadedSkin {
        final String name;
        volatile ResourceLocation texture; // 注册到本机的皮肤纹理
        volatile boolean slim;
        volatile boolean ready;
        volatile boolean loading;
        volatile long lastAttempt; // 上次尝试加载的时间戳，用于失败后的冷却重试

        LoadedSkin(String name) {
            this.name = name;
        }
    }

    public PlayerShellRenderer(EntityRendererProvider.Context context) {
        super(context,
            new PlayerModel<>(context.getModelSet().bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.slimModel = new PlayerModel<>(context.getModelSet().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.wideModel = new PlayerModel<>(context.getModelSet().bakeLayer(ModelLayers.PLAYER), false);
    }

    @Override
    public void render(PlayerShellEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        PlayerSkinData data = resolveSkinData(entity);
        this.currentSkin = data;
        // 根据皮肤手臂模型切换 细臂(slim)/粗臂(wide)
        this.model = data.slim() ? this.slimModel : this.wideModel;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerShellEntity entity) {
        PlayerSkinData data = this.currentSkin;
        if (data == null) {
            data = resolveSkinData(entity);
            this.currentSkin = data;
        }
        return data.texture();
    }

    /**
     * 解析实体当前应使用的皮肤数据（纹理 + 手臂模型）。
     */
    private PlayerSkinData resolveSkinData(PlayerShellEntity entity) {
        String skinName = entity.getSkinTexture();
        // 为空：用实体 UUID 产生一个稳定的默认皮肤
        if (skinName == null || skinName.isBlank()) {
            LOGGER.info("[PlayerShell] SkinTexture 为空或未同步，使用默认皮肤 (entityUuid={})",
                entity.getUUID());
            PlayerSkin s = DefaultPlayerSkin.get(entity.getUUID());
            return new PlayerSkinData(s.texture(), s.model() == PlayerSkin.Model.SLIM);
        }

        // 日志：确认客户端收到的 SkinTexture 值
        if (SKIN_CACHE.get(skinName) == null) {
            LOGGER.info("[PlayerShell] 客户端收到 SkinTexture='{}'", skinName);
        }

        UUID opaqueUuid = opaqueUuid(skinName);
        PlayerSkin fallback = DefaultPlayerSkin.get(opaqueUuid);
        LoadedSkin loaded = SKIN_CACHE.computeIfAbsent(skinName, LoadedSkin::new);

        // 尚未开始加载（或上次失败后已过冷却）：触发一次后台解析与下载，先用默认皮肤
        if (!loaded.loading && System.currentTimeMillis() - loaded.lastAttempt > 10000) {
            loaded.loading = true;
            loaded.lastAttempt = System.currentTimeMillis();
            Util.backgroundExecutor().execute(() -> loadSkin(loaded, skinName));
            return new PlayerSkinData(fallback.texture(), fallback.model() == PlayerSkin.Model.SLIM);
        }

        // 已下载完成：稳定使用本地动态纹理
        if (loaded.ready && loaded.texture != null) {
            return new PlayerSkinData(loaded.texture, loaded.slim);
        }

        // 解析中/失败：使用默认皮肤，避免闪烁（一旦 ready 即切换为真实皮肤）
        return new PlayerSkinData(fallback.texture(), fallback.model() == PlayerSkin.Model.SLIM);
    }

    /**
     * 在后台线程解析玩家名并下载皮肤，随后在主线程注册为本机动态纹理。
     * 皮肤来源自动回退：mc-heads.net -> minotar.net -> Mojang 官方 textures。
     * 任何环节失败都只是停留在默认皮肤，不会崩溃，冷却后可重试。
     */
    private static void loadSkin(LoadedSkin loaded, String skinName) {
        try {
            boolean slim = false;
            boolean slimKnown = false;
            String mojangUrl = null;

            // 1. 尽力从 Mojang 官方接口获取手臂模型（细臂/粗臂，权威来源）与官方皮肤 URL。
            //    若网络不可达，则回退到下一步，并改用 PNG 皮肤分析判断模型。
            try {
                String uuidStr = resolveUuid(skinName);
                if (uuidStr != null) {
                    UUID uuid = toUuid(uuidStr);
                    ProfileResult result = Minecraft.getInstance()
                        .getMinecraftSessionService().fetchProfile(uuid, false);
                    if (result != null && result.profile() != null) {
                        GameProfile profile = result.profile();
                        if (profile.getProperties().containsKey("textures")) {
                            JsonObject textures = parseTextures(profile);
                            if (textures != null && textures.has("SKIN")) {
                                JsonObject skin = textures.getAsJsonObject("SKIN");
                                mojangUrl = skin.has("url") ? skin.get("url").getAsString() : null;
                                slim = isSlimModel(skin);
                                slimKnown = true;
                            }
                        }
                    }
                } else {
                    LOGGER.info("[PlayerShell] Mojang name->uuid 解析失败: '{}'", skinName);
                }
            } catch (Exception e) {
                LOGGER.info("[PlayerShell] Mojang 解析异常: {}", e.getMessage());
            }

            // 2. 下载皮肤 PNG：优先可靠的第三方来源，最后才尝试 Mojang 官方
            byte[] png = null;
            try {
                String encoded = URLEncoder.encode(skinName, "UTF-8");
                png = download("https://mc-heads.net/skin/" + encoded);
                if (png == null) {
                    LOGGER.info("[PlayerShell] mc-heads 下载失败，尝试 minotar: '{}'", skinName);
                    png = download("https://minotar.net/skin/" + encoded);
                }
                if (png == null && mojangUrl != null) {
                    LOGGER.info("[PlayerShell] minotar 下载失败，尝试 Mojang 官方: '{}'", skinName);
                    png = download(mojangUrl);
                }
            } catch (Exception e) {
                // 忽略
            }
            if (png == null) {
                LOGGER.info("[PlayerShell] 所有皮肤来源均下载失败: '{}'", skinName);
                return;
            }

            // 3. 在主线程注册为本机动态纹理，随后标记就绪
            final byte[] skinPng = png;
            final boolean slimFinal = slim;
            final boolean slimKnownFinal = slimKnown;
            Minecraft.getInstance().execute(() -> {
                try {
                    NativeImage image = NativeImage.read(skinPng);
                    if (image.getWidth() == 0) {
                        image.close();
                        return;
                    }
                    // 若未能从 Mojang 获取模型，则通过皮肤贴图分析手臂模型
                    boolean actualSlim = slimKnownFinal ? slimFinal : detectSlim(image);

                    ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                        ModMain.MODID, "player_shell/" + sanitize(skinName));
                    // DynamicTexture 接管 image 的所有权，由它负责关闭，这里不再手动 close
                    DynamicTexture dynamicTexture = new DynamicTexture(image);
                    TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                    textureManager.register(loc, dynamicTexture);
                    dynamicTexture.upload();

                    loaded.texture = loc;
                    loaded.slim = actualSlim;
                    loaded.ready = true;
                    LOGGER.info("[PlayerShell] 皮肤注册成功: '{}' slim={}", skinName, actualSlim);
                } catch (Exception e) {
                    LOGGER.info("[PlayerShell] 纹理注册失败: '{}' {}", skinName, e.getMessage());
                }
            });
        } finally {
            // 无论成功与否都复位 loading，允许在冷却结束后重试
            loaded.loading = false;
        }
    }

    /**
     * 从档案的 textures 属性（base64 JSON）中解析出 textures 对象。
     */
    private static JsonObject parseTextures(GameProfile profile) {
        String value = profile.getProperties().get("textures").iterator().next().value();
        String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return root.has("textures") ? root.getAsJsonObject("textures") : null;
    }

    /**
     * 判断皮肤模型是否为细臂（slim）。
     */
    private static boolean isSlimModel(JsonObject skin) {
        if (skin.has("metadata")) {
            JsonObject meta = skin.getAsJsonObject("metadata");
            if (meta.has("model") && "slim".equals(meta.get("model").getAsString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 通过皮肤贴图分析手臂模型：细臂(Alex)在右臂区域为透明，粗臂(Steve)为不透明。
     * 现代 64x64 皮肤中，右臂前/后贴图之间的缝隙位于 {x:48..51, y:20..31}。
     */
    private static boolean detectSlim(NativeImage image) {
        int transparent = 0;
        int total = 0;
        for (int y = 20; y <= 31 && y < image.getHeight(); y++) {
            for (int x = 48; x <= 51 && x < image.getWidth(); x++) {
                int alpha = (image.getPixelRGBA(x, y) >>> 24) & 0xFF;
                if (alpha < 128) {
                    transparent++;
                }
                total++;
            }
        }
        return total > 0 && (transparent * 100 / total) > 50;
    }

    /**
     * 通过 Mojang 公开接口将玩家名转换为无连字符的 UUID 十六进制串。
     */
    private static String resolveUuid(String name) {
        try {
            String encoded = URLEncoder.encode(name, "UTF-8");
            HttpURLConnection conn = (HttpURLConnection)
                new URL("https://api.mojang.com/users/profiles/minecraft/" + encoded).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Joe's Addons for ABMC)");
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            JsonObject obj = JsonParser.parseString(body.toString()).getAsJsonObject();
            return obj.has("id") ? obj.get("id").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 下载 URL 的字节内容（最多 1 MB，超时自动放弃）。
     */
    private static byte[] download(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Joe's Addons for ABMC)");
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    if (out.size() > 1024 * 1024) {
                        return null;
                    }
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** 生成离线风格 UUID 作为本地缓存兜底 key。 */
    private static UUID opaqueUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
    }

    /** 把无连字符的 32 位十六进制字符串解析为 UUID。 */
    private static UUID toUuid(String s) {
        return UUID.fromString(s.replaceFirst(
            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }

    /** 清理文件名非法字符，并转为小写，保证动态纹理路径唯一且合法。 */
    private static String sanitize(String name) {
        String s = name.toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9_]", "_");
        return s.isEmpty() ? "shell" : s;
    }
}
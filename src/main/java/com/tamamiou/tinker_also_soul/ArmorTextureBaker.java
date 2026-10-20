package com.tamamiou.tinker_also_soul;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ArmorTextureBaker {

    private static final int MAX_CACHE_SIZE = 50;
    private static final Map<String, ResourceLocation> BAKED_CACHE = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (size() > MAX_CACHE_SIZE) {
                try {
                    Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                } catch (Exception e) {
                    // ignore
                }
                return true;
            }
            return false;
        }
    };

    public static ResourceLocation getOrBakeArmor(ItemStack stack, EquipmentSlot slot, RegistryAccess access) {
        try {
            String cacheKey = generateCacheKey(stack, slot);

            if (BAKED_CACHE.containsKey(cacheKey)) {
                return BAKED_CACHE.get(cacheKey);
            }

            List<TinkerArmorExtractor.ArmorLayerInfo> layers = TinkerArmorExtractor.getLayerPaths(stack, slot, access);

            if (layers.isEmpty()) {
                int layerNum = (slot == EquipmentSlot.LEGS) ? 2 : 1;
                return ResourceLocation.withDefaultNamespace("textures/models/armor/iron_layer_" + layerNum + ".png");
            }

            ResourceLocation finalTexture = bakePixels(layers, cacheKey);
            if (finalTexture != null) {
                BAKED_CACHE.put(cacheKey, finalTexture);
                return finalTexture;
            }
        } catch (Throwable t) {
            TinkerAlsoSoul.LOGGER.error("[Tinker Also Soul] Fatal error during armor baking, returning null.", t);
        }
        return null;
    }

    private static String generateCacheKey(ItemStack stack, EquipmentSlot slot) {
        String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        String visualDataStr = stack.getComponents().toString();

        String safeHash = Integer.toHexString(visualDataStr.hashCode());

        String safeItemName = itemName.replaceAll("[^a-z0-9_]", "_");
        String safeSlotName = slot.getName().replaceAll("[^a-z0-9_]", "_");

        return "tic_baked_" + safeItemName + "_" + safeSlotName + "_" + safeHash;
    }

    private static ResourceLocation bakePixels(List<TinkerArmorExtractor.ArmorLayerInfo> layers, String cacheKey) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();

        record LoadedLayer(NativeImage image, int tintColor) {}
        List<LoadedLayer> loadedLayers = new ArrayList<>();

        // 【关键修复】从 0 开始，取所有层的最大尺寸。
        // 原版盔甲纹理是 64x32，如果强行 64x64 会导致 UV 采样错位、下半部分透明。
        int maxWidth = 0;
        int maxHeight = 0;

        for (TinkerArmorExtractor.ArmorLayerInfo layer : layers) {
            try {
                Optional<Resource> resourceOpt = manager.getResource(layer.texturePath());
                if (resourceOpt.isEmpty()) continue;

                try (InputStream inputStream = resourceOpt.get().open()) {
                    NativeImage img = NativeImage.read(inputStream);
                    maxWidth = Math.max(maxWidth, img.getWidth());
                    maxHeight = Math.max(maxHeight, img.getHeight());
                    loadedLayers.add(new LoadedLayer(img, layer.colorTint()));
                }
            } catch (Exception e) {
                TinkerAlsoSoul.LOGGER.warn("[Tinker Also Soul] Unable to load texture: {}", layer.texturePath());
            }
        }

        if (loadedLayers.isEmpty() || maxWidth <= 0 || maxHeight <= 0) {
            return null;
        }

        if (maxWidth > 2048 || maxHeight > 2048) {
            TinkerAlsoSoul.LOGGER.error("[Tinker Also Soul] Texture too large ({}x{}), aborting bake.", maxWidth, maxHeight);
            for (LoadedLayer layer : loadedLayers) {
                layer.image().close();
            }
            return null;
        }

        NativeImage canvas = new NativeImage(maxWidth, maxHeight, true);

        try {
            for (LoadedLayer layer : loadedLayers) {
                NativeImage img = layer.image();
                int tintColor = layer.tintColor();

                int scaleX = maxWidth / img.getWidth();
                int scaleY = maxHeight / img.getHeight();
                if (scaleX < 1) scaleX = 1;
                if (scaleY < 1) scaleY = 1;

                for (int x = 0; x < img.getWidth(); x++) {
                    for (int y = 0; y < img.getHeight(); y++) {
                        int pixelColor = img.getPixelRGBA(x, y);
                        int alpha = (pixelColor >> 24) & 0xFF;

                        if (alpha > 0) {
                            // 统一乘 tint（-1 / 0xFFFFFFFF 乘上去也不会变，不用单独判断）
                            pixelColor = multiplyColor(pixelColor, tintColor);

                            for (int dx = 0; dx < scaleX; dx++) {
                                for (int dy = 0; dy < scaleY; dy++) {
                                    canvas.blendPixel((x * scaleX) + dx, (y * scaleY) + dy, pixelColor);
                                }
                            }
                        }
                    }
                }
                img.close();
            }

            DynamicTexture dynamicTexture = new DynamicTexture(canvas);
            // 【修复】补全 textures/models/armor/ 前缀和 .png，与 EpicFight/原版路径约定一致
            ResourceLocation generatedLocation = ResourceLocation.fromNamespaceAndPath(
                    "tinker_also_soul",
                    "textures/models/armor/" + cacheKey + ".png"
            );
            Minecraft.getInstance().getTextureManager().register(generatedLocation, dynamicTexture);

            return generatedLocation;
        } catch (Exception e) {
            TinkerAlsoSoul.LOGGER.error("[Tinker Also Soul] Error while stitching pixels!", e);
            canvas.close();
            return null;
        }
    }

    private static int multiplyColor(int basePixel, int tintColor) {
        // NativeImage 像素格式为 ABGR：0xAABBGGRR
        int a = (basePixel >> 24) & 0xFF;
        int b = (basePixel >> 16) & 0xFF;
        int g = (basePixel >> 8)  & 0xFF;
        int r = basePixel         & 0xFF;

        // tintColor 为 ARGB：0xAARRGGBB
        int tintA = (tintColor >> 24) & 0xFF;
        int tintR = (tintColor >> 16) & 0xFF;
        int tintG = (tintColor >> 8)  & 0xFF;
        int tintB = tintColor         & 0xFF;

        int newA = (a * tintA) / 255;
        int newR = (r * tintR) / 255;
        int newG = (g * tintG) / 255;
        int newB = (b * tintB) / 255;

        return (newA << 24) | (newB << 16) | (newG << 8) | newR;
    }

    public static void clearCache() {
        for (ResourceLocation loc : BAKED_CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(loc);
        }
        BAKED_CACHE.clear();
    }
}
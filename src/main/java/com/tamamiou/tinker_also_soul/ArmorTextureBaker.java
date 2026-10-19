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
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                return true;
            }
            return false;
        }
    };

    public static ResourceLocation getOrBakeArmor(ItemStack stack, EquipmentSlot slot, RegistryAccess access) {
        String cacheKey = generateCacheKey(stack, slot);

        if (BAKED_CACHE.containsKey(cacheKey)) {
            return BAKED_CACHE.get(cacheKey);
        }

        List<TinkerArmorExtractor.ArmorLayerInfo> layers = TinkerArmorExtractor.getLayerPaths(stack, slot, access);

        if (layers.isEmpty()) {
            int layerNum = (slot == EquipmentSlot.LEGS) ? 2 : 1;
            return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/models/armor/iron_layer_" + layerNum + ".png");
        }

        ResourceLocation finalTexture = bakePixels(layers, cacheKey);
        if (finalTexture != null) {
            BAKED_CACHE.put(cacheKey, finalTexture);
        }

        return finalTexture;
    }

    private static String generateCacheKey(ItemStack stack, EquipmentSlot slot) {
        String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

        StringBuilder visualData = new StringBuilder();
        visualData.append(stack.getComponents().toString());

        int visualHash = visualData.toString().hashCode();

        return "tic_baked_" + itemName + "_" + slot.getName() + "_" + visualHash;
    }

    private static ResourceLocation bakePixels(List<TinkerArmorExtractor.ArmorLayerInfo> layers, String cacheKey) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();

        record LoadedLayer(NativeImage image, int tintColor) {}
        List<LoadedLayer> loadedLayers = new ArrayList<>();

        int maxWidth = 64;
        int maxHeight = 64;

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

        if (loadedLayers.isEmpty()) {
            return null;
        }

        NativeImage canvas = new NativeImage(maxWidth, maxHeight, true);

        for (LoadedLayer layer : loadedLayers) {
            NativeImage img = layer.image();
            int tintColor = layer.tintColor();

            int scaleX = maxWidth / img.getWidth();
            int scaleY = maxHeight / img.getHeight();

            for (int x = 0; x < img.getWidth(); x++) {
                for (int y = 0; y < img.getHeight(); y++) {
                    int pixelColor = img.getPixelRGBA(x, y);
                    int alpha = (pixelColor >> 24) & 0xFF;

                    if (alpha > 0) {
                        if (tintColor != -1) {
                            pixelColor = multiplyColor(pixelColor, tintColor);
                        }

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
        ResourceLocation generatedLocation = ResourceLocation.fromNamespaceAndPath("tinker_also_soul", cacheKey);
        Minecraft.getInstance().getTextureManager().register(generatedLocation, dynamicTexture);

        return generatedLocation;
    }

    private static int multiplyColor(int basePixel, int tintColor) {
        int a = (basePixel >> 24) & 0xFF;
        int b = (basePixel >> 16) & 0xFF;
        int g = (basePixel >> 8)  & 0xFF;
        int r = basePixel         & 0xFF;

        int tintR = (tintColor >> 16) & 0xFF;
        int tintG = (tintColor >> 8)  & 0xFF;
        int tintB = tintColor         & 0xFF;

        int newR = (r * tintR) / 255;
        int newG = (g * tintG) / 255;
        int newB = (b * tintB) / 255;

        return (a << 24) | (newB << 16) | (newG << 8) | newR;
    }

    public static void clearCache() {
        for (ResourceLocation loc : BAKED_CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(loc);
        }
        BAKED_CACHE.clear();
    }
}
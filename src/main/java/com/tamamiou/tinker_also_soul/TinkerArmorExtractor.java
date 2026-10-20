package com.tamamiou.tinker_also_soul;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import slimeknights.tconstruct.library.client.armor.ArmorModelManager;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier.TextureType;
import slimeknights.tconstruct.library.client.armor.texture.TintedArmorTexture;
import slimeknights.tconstruct.library.client.armor.texture.TrimArmorTextureSupplier.TrimArmorTexture;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public class TinkerArmorExtractor {

    public static List<ArmorLayerInfo> getLayerPaths(ItemStack stack, EquipmentSlot slot, RegistryAccess registryAccess) {
        List<ArmorLayerInfo> layerInfos = new ArrayList<>();

        try {
            ResourceLocation modelId = getModelIdFromStack(stack);

            if (modelId == null) {
                // 如果这里打印了，说明没拿到 ModelID，可能是 JsonThings 的兼容问题
                TinkerAlsoSoul.LOGGER.warn("[Tinker Also Soul] [Debug] Unable to get Model Id for: {}", stack.getItem().getDescriptionId());
                return layerInfos;
            }

            ArmorModelManager.ArmorModel model = ArmorModelManager.INSTANCE.getModel(modelId);

            if (model == null || model == ArmorModelManager.ArmorModel.EMPTY) {
                TinkerAlsoSoul.LOGGER.warn("[Tinker Also Soul] [Debug] Model is empty for ID: {}", modelId);
                return layerInfos;
            }

            TextureType type = TextureType.fromSlot(slot);
            for (ArmorTextureSupplier supplier : model.layers()) {
                ArmorTextureSupplier.ArmorTexture tex = supplier.getArmorTexture(stack, type, registryAccess);

                if (tex != null && tex != ArmorTextureSupplier.ArmorTexture.EMPTY) {
                    processTextureLayer(tex, layerInfos);
                } else {
                    // 如果这里打印了，说明供应商没产出纹理（比如材料没匹配上，或者没有这个部位的纹理）
                    TinkerAlsoSoul.LOGGER.warn("[Tinker Also Soul] [Debug] Supplier {} returned EMPTY for slot {}", supplier.getClass().getSimpleName(), slot);
                }
            }
        } catch (Exception e) {
            TinkerAlsoSoul.LOGGER.error("[Tinker Also Soul] Failed to extract layer paths for item: {}", stack.getItem().getDescriptionId(), e);
        }

        // 最终检查：到底拿到了几层？如果是0，就说明必然回退到默认铁/钻套了
        if (layerInfos.isEmpty()) {
            TinkerAlsoSoul.LOGGER.warn("[Tinker Also Soul] [Debug] LayerInfos is empty! Falling back to default armor texture.");
        } else {
            TinkerAlsoSoul.LOGGER.info("[Tinker Also Soul] [Debug] Successfully extracted {} layers for item {}", layerInfos.size(), stack.getItem().getDescriptionId());
        }

        return layerInfos;
    }

    private static void processTextureLayer(ArmorTextureSupplier.ArmorTexture tex, List<ArmorLayerInfo> list) {
        try {
            if (tex instanceof TintedArmorTexture tintedTex) {
                // 【加固】尝试多种可能的字段名
                ResourceLocation path = null;
                int color = tintedTex.color();

                try {
                    Field textureField = TintedArmorTexture.class.getDeclaredField("texture");
                    textureField.setAccessible(true);
                    path = (ResourceLocation) textureField.get(tintedTex);
                } catch (NoSuchFieldException e) {
                    // 兼容 1.21.1 可能改名的字段
                    try {
                        Field pathField = TintedArmorTexture.class.getDeclaredField("texturePath");
                        pathField.setAccessible(true);
                        path = (ResourceLocation) pathField.get(tintedTex);
                    } catch (NoSuchFieldException ignored) {
                        TinkerAlsoSoul.LOGGER.warn("[Tinker Also Soul] [Debug] TintedArmorTexture field names changed! Cannot find 'texture' or 'texturePath'!");
                    }
                }

                if (path != null) {
                    list.add(new ArmorLayerInfo(path, color));
                }
            }
            else if (tex instanceof TrimArmorTexture trimTex) {
                Field spriteField = TrimArmorTexture.class.getDeclaredField("trimSprite");
                spriteField.setAccessible(true);
                TextureAtlasSprite sprite = (TextureAtlasSprite) spriteField.get(trimTex);

                if (sprite != null) {
                    ResourceLocation spriteId = sprite.contents().name();
                    ResourceLocation realTexturePath = ResourceLocation.fromNamespaceAndPath(
                            spriteId.getNamespace(),
                            "textures/" + spriteId.getPath() + ".png"
                    );

                    int trimColor = -1;
                    try {
                        Field colorField = TrimArmorTexture.class.getDeclaredField("color");
                        colorField.setAccessible(true);
                        trimColor = (int) colorField.get(trimTex);
                    } catch (NoSuchFieldException ignored) { }

                    list.add(new ArmorLayerInfo(realTexturePath, trimColor));
                }
            }
            else {
                // 【关键排查】如果你的代码进了这里，说明匠魂杂物返回了未知的纹理类！
                TinkerAlsoSoul.LOGGER.warn("[Tinker Also Soul] [Debug] Unknown ArmorTexture type: {}", tex.getClass().getName());
            }
        } catch (Exception e) {
            TinkerAlsoSoul.LOGGER.error("[Tinker Also Soul] Unable to process texture layer of type: {}", tex.getClass().getName(), e);
        }
    }

    @Nullable
    private static ResourceLocation getModelIdFromStack(ItemStack stack) {
        try {
            IClientItemExtensions extension = IClientItemExtensions.of(stack);
            if (!(extension instanceof ArmorModelManager.ArmorModelDispatcher dispatcher)) {
                return null;
            }

            Class<?> clazz = ArmorModelManager.ArmorModelDispatcher.class;

            try {
                Field nameField = clazz.getDeclaredField("name");
                nameField.setAccessible(true);
                Object val = nameField.get(dispatcher);
                if (val instanceof ResourceLocation rl) {
                    return rl;
                }
            } catch (NoSuchFieldException ignored) { }

            for (String methodName : new String[]{"getName", "name"}) {
                try {
                    Method m = clazz.getDeclaredMethod(methodName);
                    m.setAccessible(true);
                    Object val = m.invoke(dispatcher);
                    if (val instanceof ResourceLocation rl) {
                        return rl;
                    }
                } catch (NoSuchMethodException ignored) { }
            }
        } catch (Exception e) {
            TinkerAlsoSoul.LOGGER.debug("[Tinker Also Soul] Failed to resolve armor model id", e);
        }
        return null;
    }

    public record ArmorLayerInfo(ResourceLocation texturePath, int colorTint) {}
}
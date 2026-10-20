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
                TinkerAlsoSoul.LOGGER.debug("[Tinker Also Soul] Unable to get Model Id: {}", stack.getItem().getDescriptionId());
                return layerInfos;
            }

            ArmorModelManager.ArmorModel model = ArmorModelManager.INSTANCE.getModel(modelId);

            if (model == null || model == ArmorModelManager.ArmorModel.EMPTY) {
                return layerInfos;
            }

            TextureType type = TextureType.fromSlot(slot);
            // 这个循环天然支持 MultilayerArmorModel，直接遍历即可，无需额外处理
            for (ArmorTextureSupplier supplier : model.layers()) {
                ArmorTextureSupplier.ArmorTexture tex = supplier.getArmorTexture(stack, type, registryAccess);

                if (tex != null && tex != ArmorTextureSupplier.ArmorTexture.EMPTY) {
                    processTextureLayer(tex, layerInfos);
                }
            }
        } catch (Exception e) {
            TinkerAlsoSoul.LOGGER.error("[Tinker Also Soul] Failed to extract layer paths for item: {}", stack.getItem().getDescriptionId(), e);
        }

        return layerInfos;
    }

    private static void processTextureLayer(ArmorTextureSupplier.ArmorTexture tex, List<ArmorLayerInfo> list) {
        try {
            if (tex instanceof TintedArmorTexture tintedTex) {
                Field textureField = TintedArmorTexture.class.getDeclaredField("texture");
                textureField.setAccessible(true);
                ResourceLocation path = (ResourceLocation) textureField.get(tintedTex);
                int color = tintedTex.color();
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

                    // 尝试获取 trim 的颜色
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
                TinkerAlsoSoul.LOGGER.warn("[Tinker Also Soul] Unknown ArmorTexture type: {}", tex.getClass().getName());
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

            // 【关键修复】反编译确认，ArmorModelDispatcher 没有 getName()，只有私有字段 name
            try {
                Field nameField = clazz.getDeclaredField("name");
                nameField.setAccessible(true);
                Object val = nameField.get(dispatcher);
                if (val instanceof ResourceLocation rl) {
                    return rl;
                }
            } catch (NoSuchFieldException ignored) { }

            // 兜底方案：万一以后改了加了方法
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
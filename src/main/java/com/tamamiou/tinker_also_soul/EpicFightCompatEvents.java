package com.tamamiou.tinker_also_soul;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import yesman.epicfight.api.client.event.types.render.AnimatedArmorTextureEvent;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;

public class EpicFightCompatEvents {

    public static void register() {
        TinkerAlsoSoul.LOGGER.info("[Tinker Also Soul] Registering EpicFight Compat Events.");

        EpicFightClientEventHooks.Render.ANIMATED_ARMOR_TEXTURE.registerEvent(event -> {
            try {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());

                // 1. 打印日志，看看事件到底有没有触发
                if (itemId != null) {
                    TinkerAlsoSoul.LOGGER.info("[Tinker Also Soul] [Debug] Armor Render Event Triggered for: {}", itemId);
                }

                // 2. 放宽判断：只要名字里带有 tconstruct 或 tinkers_things 就放行
                if (itemId != null && (itemId.getNamespace().equals("tconstruct") || itemId.getNamespace().equals("tinkers_things"))) {

                    TinkerAlsoSoul.LOGGER.info("[Tinker Also Soul] [Debug] Processing armor for: {}", itemId);

                    ResourceLocation bakedTexture = ArmorTextureBaker.getOrBakeArmor(
                            event.getItemStack(),
                            event.getEquipmentSlot(),
                            event.getLivingEntity().level().registryAccess()
                    );

                    // 必须判空！只有成功烘焙出了合法贴图才接管渲染
                    if (bakedTexture != null) {
                        TinkerAlsoSoul.LOGGER.info("[Tinker Also Soul] [Debug] Successfully baked texture: {}", bakedTexture);
                        event.setResultLocation(bakedTexture);
                    } else {
                        TinkerAlsoSoul.LOGGER.warn("[Tinker Also Soul] [Debug] Failed to bake texture for: {}", itemId);
                    }
                }
            } catch (Throwable t) {
                TinkerAlsoSoul.LOGGER.error("[Tinker Also Soul] Error handling AnimatedArmorTextureEvent! Bypassing to default renderer.", t);
            }
        });
    }
}
package com.tamamiou.tinker_also_soul;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import yesman.epicfight.api.client.event.types.render.AnimatedArmorTextureEvent;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;

public class EpicFightCompatEvents {

    public static void register() {
        TinkerAlsoSoul.LOGGER.info("[Tinker Also Soul] Registering EpicFight Compat Events.");
        EpicFightClientEventHooks.Render.ANIMATED_ARMOR_TEXTURE.registerEvent(event -> {
            try {
                if (!(event.getItemStack().getItem() instanceof ArmorItem)) {
                    return;
                }

                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
                if (itemId != null && itemId.getNamespace().equals("tconstruct")) {

                    ResourceLocation bakedTexture = ArmorTextureBaker.getOrBakeArmor(
                            event.getItemStack(),
                            event.getEquipmentSlot(),
                            event.getLivingEntity().level().registryAccess()
                    );

                    // 必须判空！只有成功烘焙出了合法贴图才接管渲染
                    if (bakedTexture != null) {
                        event.setResultLocation(bakedTexture);
                    }
                }
            } catch (Throwable t) {
                // 捕获所有 Throwable，防止因为贴图问题导致 EpicFight 渲染线程崩溃
                TinkerAlsoSoul.LOGGER.error("[Tinker Also Soul] Error handling AnimatedArmorTextureEvent! Bypassing to default renderer.", t);
            }
        });
    }
}
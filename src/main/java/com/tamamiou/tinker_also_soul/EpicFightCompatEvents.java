package com.tamamiou.tinker_also_soul;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import yesman.epicfight.api.client.event.types.render.AnimatedArmorTextureEvent;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;

public class EpicFightCompatEvents {

    public static void register() {
        EpicFightClientEventHooks.Render.ANIMATED_ARMOR_TEXTURE.registerEvent(event -> {

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

                if (bakedTexture != null) {
                    event.setResultLocation(bakedTexture);
                }
            }
        });
    }
}
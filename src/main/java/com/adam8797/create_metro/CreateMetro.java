package com.adam8797.create_metro;

import com.adam8797.create_metro.config.MetroConfigs;
import com.mojang.logging.LogUtils;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import org.slf4j.Logger;

@Mod(CreateMetro.MOD_ID)
public class CreateMetro {
    public static final String MOD_ID = "create_metro";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID);

    static {
        REGISTRATE
                .defaultCreativeTab((ResourceKey<CreativeModeTab>) null)
                .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                        .andThen(TooltipModifier.mapNull(KineticStats.create(item))));
    }

    public CreateMetro(IEventBus modEventBus, ModContainer modContainer) {
        REGISTRATE.registerEventListeners(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onRegister);

        REGISTRATE.setCreativeTab(MetroCreativeTabs.MAIN);
        MetroBlocks.register();
        MetroItems.register();
        MetroBlockEntityTypes.register();
        MetroCreativeTabs.register(modEventBus);
        MetroPackets.register();

        MetroConfigs.register(modContainer);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
//            CCInteractionBehaviours.register();
//            CCMovementBehaviours.register();
//            CCMountedStorageTypes.register();
//            CCDisplaySources.register();
        });
    }

    public void onRegister(final RegisterEvent event) {
        if (event.getRegistry() == CreateBuiltInRegistries.ITEM_ATTRIBUTE_TYPE) {
            //CCItemAttributes.register();
        } else if (event.getRegistry() == BuiltInRegistries.TRIGGER_TYPES) {
//            CCAdvancements.register();
//            CCTriggers.register();
        }
    }

    public static CreateRegistrate getRegistrate() {
        return REGISTRATE;
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

}

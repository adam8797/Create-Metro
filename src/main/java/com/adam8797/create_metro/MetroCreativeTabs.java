package com.adam8797.create_metro;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllCreativeModeTabs;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MetroCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateMetro.MOD_ID);

    public static final List<ItemProviderEntry<?, ?>> ITEMS = List.of(
    );


    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("create_metro.creative_tab"))
            .withTabsBefore(AllCreativeModeTabs.PALETTES_CREATIVE_TAB.getKey())
            .icon(AllBlocks.GEARBOX::asStack)
            .displayItems(new DisplayItemsGenerator(ITEMS))
            .build());

    public static void hideItems(BuildCreativeModeTabContentsEvent event) {
        if (Objects.equals(event.getTabKey(), MAIN.getKey()) || Objects.equals(event.getTabKey(), CreativeModeTabs.SEARCH)) {
            Set<ItemStack> hiddenItems = ITEMS.stream()
                    //.filter(x -> !FeatureToggle.isEnabled(x.getId()))
                    .map(entry -> event.getSearchEntries().stream().filter(stack -> stack.getItem() == entry.asItem()).findFirst()
                            .orElse(event.getParentEntries().stream().filter(stack -> stack.getItem() == entry.asItem()).findFirst()
                                    .orElse(null)))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            for (ItemStack hiddenItem : hiddenItems) {
                event.remove(hiddenItem, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        }
    }

    private record DisplayItemsGenerator(List<ItemProviderEntry<?, ?>> items) implements CreativeModeTab.DisplayItemsGenerator {
        @Override
        public void accept(@NotNull CreativeModeTab.ItemDisplayParameters params, @NotNull CreativeModeTab.Output output) {
            for (ItemProviderEntry<?, ?> item : items) {
                //if (FeatureToggle.isEnabled(item.getId())) {
                    output.accept(item);
                //}
            }
        }
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(MetroCreativeTabs::hideItems);
    }

}

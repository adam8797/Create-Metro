package com.adam8797.create_metro;

import com.adam8797.create_metro.content.turnstile.TurnstileBlock;
import com.simibubi.create.AllTags.AllBlockTags;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

public class MetroBlocks {
    private static final CreateRegistrate REGISTRATE = CreateMetro.getRegistrate();

    public static final BlockEntry<TurnstileBlock> TURNSTILE = REGISTRATE.block("turnstile", TurnstileBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY))
            .properties(p -> p.sound(SoundType.NETHERITE_BLOCK))
            .properties(p -> p.strength(1.5F, 6.0F))
            .properties(BlockBehaviour.Properties::noOcclusion)
            .properties(p -> p.isRedstoneConductor((state, getter, pos) -> false))
            .transform(pickaxeOnly())
            .tag(AllBlockTags.RELOCATION_NOT_SUPPORTED.tag)
            .lang("Turnstile")
            .simpleItem()
            .register();

    public static void register() { }
}

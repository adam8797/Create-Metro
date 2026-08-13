package com.adam8797.create_metro;

import com.adam8797.create_metro.content.ticketprinter.TicketPrinterBlock;
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
            .tag(AllBlockTags.NON_MOVABLE.tag)
            // The blockstate + models are hand-authored (see tools/derive_turnstile_models.py); a no-op
            // data-gen provider stops Registrate emitting a cube_all model that references a
            // create_metro:block/turnstile texture (which doesn't exist) and crashing runData.
            .blockstate((c, p) -> { })
            .lang("Turnstile")
            .simpleItem()
            .register();

    public static final BlockEntry<TicketPrinterBlock> TICKET_PRINTER = REGISTRATE.block("ticket_printer", TicketPrinterBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY))
            .properties(p -> p.sound(SoundType.NETHERITE_BLOCK))
            .properties(p -> p.strength(1.5F, 6.0F))
            .properties(BlockBehaviour.Properties::noOcclusion)
            // The glowing indicator sits on the upper half, so only that half emits light.
            .properties(p -> p.lightLevel(TicketPrinterBlock::lightEmission))
            .transform(pickaxeOnly())
            .tag(AllBlockTags.NON_MOVABLE.tag)
            // Blockstate + model are hand-authored placeholders (GeckoLib animation to come); no-op
            // data-gen stops Registrate emitting a cube_all model that crashes runData.
            .blockstate((c, p) -> { })
            .lang("Ticket Printer")
            .simpleItem()
            .register();

    public static void register() { }
}

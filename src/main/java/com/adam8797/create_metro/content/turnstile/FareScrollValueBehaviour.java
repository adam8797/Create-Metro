package com.adam8797.create_metro.content.turnstile;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fare scroll value whose in-world wrench "quick set" board stays compact regardless of the configured
 * maximum. Create's value-settings board draws one column per unit, so a config max in the thousands
 * would make an absurdly wide panel; we cap the board here while the full range (0..configured max) is
 * still reachable via the block's GUI.
 */
public class FareScrollValueBehaviour extends ScrollValueBehaviour {

    /** Highest fare offered by the compact wrench board. Larger fares are set through the GUI. */
    public static final int QUICK_BOARD_MAX = 64;

    public FareScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
        super(label, be, slot);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        int boardMax = Math.min(max, QUICK_BOARD_MAX);
        return new ValueSettingsBoard(label, boardMax, 10, ImmutableList.of(Component.literal("Fare")),
                new ValueSettingsFormatter(ValueSettings::format));
    }
}

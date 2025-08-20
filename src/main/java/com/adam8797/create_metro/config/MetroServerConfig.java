package com.adam8797.create_metro.config;

import net.createmod.catnip.config.ConfigBase;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class MetroServerConfig extends ConfigBase {

    private final ConfigInt batteryDischargeRPM = i(64, 0, 256, "batteryDischargeRPM", Comments.batteryDischargeRPM);
    public static final Supplier<Integer> BatteryDischargeRPM = MetroConfigs.safeGetter(() -> MetroConfigs.server().batteryDischargeRPM.get(), 64);

    @Override
    public @NotNull String getName() {
        return "server";
    }

    private static class Comments {
        static String batteryDischargeRPM = "RPM of a Kinetic Battery when discharging";
    }
}
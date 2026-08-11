package com.adam8797.create_metro.config;

import net.createmod.catnip.config.ConfigBase;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class MetroServerConfig extends ConfigBase {

    private final ConfigInt batteryDischargeRPM = i(64, 0, 256, "batteryDischargeRPM", Comments.batteryDischargeRPM);
    public static final Supplier<Integer> BatteryDischargeRPM = MetroConfigs.safeGetter(() -> MetroConfigs.server().batteryDischargeRPM.get(), 64);

    private final ConfigInt defaultTurnstileFare = i(5, 0, 1_000_000, "defaultTurnstileFare", Comments.defaultTurnstileFare);
    public static final Supplier<Integer> DefaultTurnstileFare = MetroConfigs.safeGetter(() -> MetroConfigs.server().defaultTurnstileFare.get(), 5);

    private final ConfigInt maxTurnstileFare = i(4096, 1, 1_000_000, "maxTurnstileFare", Comments.maxTurnstileFare);
    public static final Supplier<Integer> MaxTurnstileFare = MetroConfigs.safeGetter(() -> MetroConfigs.server().maxTurnstileFare.get(), 4096);

    @Override
    public @NotNull String getName() {
        return "server";
    }

    private static class Comments {
        static String batteryDischargeRPM = "RPM of a Kinetic Battery when discharging";
        static String defaultTurnstileFare = "Fare (in spurs) a freshly placed Turnstile charges. Adjustable per-block with a wrench.";
        static String maxTurnstileFare = "Maximum fare (in spurs) a Turnstile can be configured to charge.";
    }
}
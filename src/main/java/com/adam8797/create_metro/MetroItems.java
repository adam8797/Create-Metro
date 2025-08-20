package com.adam8797.create_metro;

import com.simibubi.create.foundation.data.CreateRegistrate;

public class MetroItems {

    private static final CreateRegistrate REGISTRATE = CreateMetro.getRegistrate();

    static {
        REGISTRATE.setCreativeTab(MetroCreativeTabs.MAIN);
    }

    public static void register() { }
}

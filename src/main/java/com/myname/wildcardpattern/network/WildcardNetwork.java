package com.myname.wildcardpattern.network;

import com.myname.wildcardpattern.WildcardPatternMod;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class WildcardNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(WildcardPatternMod.MODID);

    private WildcardNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(MessageUpdateWildcardConfig.Handler.class, MessageUpdateWildcardConfig.class, 0, Side.SERVER);
    }
}

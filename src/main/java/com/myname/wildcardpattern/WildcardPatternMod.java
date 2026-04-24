package com.myname.wildcardpattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = WildcardPatternMod.MODID,
    name = "Wildcard Pattern",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:appliedenergistics2;required-after:gregtech;required-after:modularui")
public class WildcardPatternMod {

    public static final String MODID = "wildcardpattern";
    public static final int GUI_WILDCARD_PATTERN = 1;
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static WildcardPatternMod instance;

    @SidedProxy(
        clientSide = "com.myname.wildcardpattern.ClientProxy",
        serverSide = "com.myname.wildcardpattern.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOG.info("[wildcardpattern] Loaded build version={} (optimize: lag + matching)", Tags.VERSION);
        proxy.init(event);
    }
}

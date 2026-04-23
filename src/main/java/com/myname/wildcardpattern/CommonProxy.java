/*  */package com.myname.wildcardpattern;

import com.myname.wildcardpattern.crafting.RecipeInitializedWildcardPattern;
import com.myname.wildcardpattern.gui.WildcardGuiHandler;
import com.myname.wildcardpattern.network.WildcardNetwork;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ModItems.init();
        WildcardNetwork.init();
    }

    public void init(FMLInitializationEvent event) {
        GameRegistry.addRecipe(new RecipeInitializedWildcardPattern());
        NetworkRegistry.INSTANCE.registerGuiHandler(WildcardPatternMod.instance, new WildcardGuiHandler());
    }
}

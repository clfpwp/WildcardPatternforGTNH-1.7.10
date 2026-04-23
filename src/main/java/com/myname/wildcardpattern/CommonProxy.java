package com.myname.wildcardpattern;

import com.myname.wildcardpattern.gui.WildcardGuiHandler;
import com.myname.wildcardpattern.network.WildcardNetwork;

import appeng.api.AEApi;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.ItemStack;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ModItems.init();
        WildcardNetwork.init();
    }

    public void init(FMLInitializationEvent event) {
        ItemStack blankPattern = AEApi.instance().definitions().materials().blankPattern().maybeStack(1).orNull();
        if (blankPattern != null) {
            GameRegistry.addShapelessRecipe(new ItemStack(ModItems.wildcardPattern), blankPattern);
        }
        NetworkRegistry.INSTANCE.registerGuiHandler(WildcardPatternMod.instance, new WildcardGuiHandler());
    }
}

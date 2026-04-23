package com.myname.wildcardpattern;

import com.myname.wildcardpattern.item.ItemWildcardPattern;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public final class ModItems {

    public static Item wildcardPattern;

    private ModItems() {}

    public static void init() {
        wildcardPattern = new ItemWildcardPattern()
            .setUnlocalizedName("wildcard_pattern")
            .setTextureName("wildcardpattern:wildcard_pattern")
            .setCreativeTab(CreativeTabs.tabMisc);

        GameRegistry.registerItem(wildcardPattern, "wildcard_pattern");
    }
}

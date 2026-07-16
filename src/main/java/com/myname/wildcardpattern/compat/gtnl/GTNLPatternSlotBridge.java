package com.myname.wildcardpattern.compat.gtnl;

import java.util.List;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.world.World;

public interface GTNLPatternSlotBridge {

    boolean wildcardpattern$isOperational();

    boolean wildcardpattern$isWildcard();

    List<ICraftingPatternDetails> wildcardpattern$getRegistrationDetails(World world);

    boolean wildcardpattern$owns(ICraftingPatternDetails details, World world);

    GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> wildcardpattern$beginActivation(
        ICraftingPatternDetails details);

    void wildcardpattern$rollbackActivation(GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> snapshot);

    boolean wildcardpattern$insert(InventoryCrafting table);
}

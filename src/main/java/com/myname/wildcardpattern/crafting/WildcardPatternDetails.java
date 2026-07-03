package com.myname.wildcardpattern.crafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.PatternHelper;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class WildcardPatternDetails implements ICraftingPatternDetails {

    private final PatternHelper delegate;
    private final ItemStack stablePattern;
    private final String stablePatternIdentity;
    private final int stableHashCode;

    public WildcardPatternDetails(ItemStack stack, World world) {
        this.delegate = new PatternHelper(stack, world);
        ItemStack pattern = this.delegate.getPattern();
        this.stablePattern = pattern == null ? null : pattern.copy();
        this.stablePatternIdentity = WildcardPatternGenerator.getPatternIdentity(this.stablePattern);
        this.stableHashCode = this.stablePatternIdentity.hashCode();
    }

    @Override
    public ItemStack getPattern() {
        return this.delegate.getPattern();
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
        return this.delegate.isValidItemForSlot(slotIndex, itemStack, world);
    }

    @Override
    public boolean isCraftable() {
        return this.delegate.isCraftable();
    }

    @Override
    public IAEItemStack[] getInputs() {
        return this.delegate.getInputs();
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return this.delegate.getCondensedInputs();
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return this.delegate.getCondensedOutputs();
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return this.delegate.getOutputs();
    }

    @Override
    public boolean canSubstitute() {
        return this.delegate.canSubstitute();
    }

    @Override
    public boolean canBeSubstitute() {
        return this.delegate.canBeSubstitute();
    }

    @Override
    public ItemStack getOutput(InventoryCrafting craftingInv, World world) {
        return this.delegate.getOutput(craftingInv, world);
    }

    @Override
    public int getPriority() {
        return this.delegate.getPriority();
    }

    @Override
    public void setPriority(int priority) {
        this.delegate.setPriority(priority);
    }

    @Override
    public int hashCode() {
        return this.stableHashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WildcardPatternDetails)) {
            return false;
        }
        WildcardPatternDetails other = (WildcardPatternDetails) obj;
        return this.stablePatternIdentity.equals(other.stablePatternIdentity);
    }
}

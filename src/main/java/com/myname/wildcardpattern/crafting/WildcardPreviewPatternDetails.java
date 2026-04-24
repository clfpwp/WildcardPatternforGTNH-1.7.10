package com.myname.wildcardpattern.crafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class WildcardPreviewPatternDetails implements ICraftingPatternDetails {

    private final ItemStack pattern;
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;
    private final ItemStack outputStack;
    private int priority;

    public WildcardPreviewPatternDetails(ItemStack pattern, ItemStack inputStack, ItemStack outputStack) {
        this.pattern = pattern == null ? null : pattern.copy();
        this.outputStack = outputStack == null ? null : outputStack.copy();
        this.inputs = toArray(inputStack);
        this.outputs = toArray(this.outputStack);
    }

    private static IAEItemStack[] toArray(ItemStack stack) {
        if (stack == null) {
            return new IAEItemStack[0];
        }
        IAEItemStack aeStack = AEItemStack.create(stack.copy());
        return aeStack == null ? new IAEItemStack[0] : new IAEItemStack[] { aeStack };
    }

    @Override
    public ItemStack getPattern() {
        return this.pattern == null ? null : this.pattern.copy();
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
        return slotIndex == 0 && this.inputs.length > 0 && itemStack != null
            && this.inputs[0] != null
            && this.inputs[0].isSameType(itemStack);
    }

    @Override
    public boolean isCraftable() {
        return false;
    }

    @Override
    public IAEItemStack[] getInputs() {
        return copy(this.inputs);
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return copy(this.inputs);
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return copy(this.outputs);
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return copy(this.outputs);
    }

    @Override
    public boolean canSubstitute() {
        return false;
    }

    @Override
    public boolean canBeSubstitute() {
        return false;
    }

    @Override
    public ItemStack getOutput(InventoryCrafting craftingInv, World world) {
        return this.outputStack == null ? null : this.outputStack.copy();
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    private static IAEItemStack[] copy(IAEItemStack[] source) {
        IAEItemStack[] result = new IAEItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = source[index] == null ? null : source[index].copy();
        }
        return result;
    }
}

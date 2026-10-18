package com.myname.wildcardpattern.crafting;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import com.glodblock.github.common.item.ItemFluidDrop;
import com.myname.wildcardpattern.compat.FixedFluidSupport;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Preserve fluid-drop inputs for GT while exposing actual fluids to AE2's crafting planner. */
public final class CompositeFluidPatternDetails extends WildcardPatternDetails {

    private final IAEStack<?>[] aeInputs;
    private final IAEStack<?>[] condensedAeInputs;

    public CompositeFluidPatternDetails(ItemStack stack, World world) {
        super(stack, world);
        this.aeInputs = convert(getInputs());
        this.condensedAeInputs = convert(getCondensedInputs());
    }

    private static IAEStack<?>[] convert(IAEItemStack[] inputs) {
        IAEStack<?>[] result = new IAEStack<?>[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            IAEItemStack input = inputs[i];
            if (input == null) continue;
            if (FixedFluidSupport.isFluid(input.getItemStack())) {
                IAEFluidStack fluid = ItemFluidDrop.getAeFluidStack(input);
                if (fluid == null || fluid.getStackSize() <= 0 || fluid.getStackSize() > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Invalid fixed fluid input");
                }
                if (!java.util.Objects.equals(FixedFluidSupport.getFluid(input.getItemStack()).tag, fluid.getFluidStack().tag)) {
                    throw new IllegalArgumentException("Target AE2 does not preserve fluid NBT");
                }
                result[i] = fluid;
            } else {
                result[i] = input;
            }
        }
        return result;
    }

    @Override
    public IAEStack<?>[] getAEInputs() { return this.aeInputs; }

    @Override
    public IAEStack<?>[] getCondensedAEInputs() { return this.condensedAeInputs; }

    @Override
    public IAEStack<?>[] getAEOutputs() { return getOutputs(); }

    @Override
    public IAEStack<?>[] getCondensedAEOutputs() { return getCondensedOutputs(); }

    @Override
    public boolean isValidItemForSlot(int index, IAEStack<?> input, World world) {
        if (index < 0 || index >= this.aeInputs.length || input == null) return false;
        IAEStack<?> expected = this.aeInputs[index];
        if (expected instanceof IAEFluidStack fluid && input instanceof IAEFluidStack supplied) {
            return fluid.getFluidStack().isFluidEqual(supplied.getFluidStack());
        }
        return input instanceof IAEItemStack item && super.isValidItemForSlot(index, item.getItemStack(), world);
    }
}

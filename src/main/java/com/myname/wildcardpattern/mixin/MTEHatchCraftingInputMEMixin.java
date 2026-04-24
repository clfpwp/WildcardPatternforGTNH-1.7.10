package com.myname.wildcardpattern.mixin;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.storage.data.IAEItemStack;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.api.objects.GTDualInputPattern;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

@Mixin(value = MTEHatchCraftingInputME.class, remap = false)
public abstract class MTEHatchCraftingInputMEMixin {

    @Shadow
    private MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME>[] internalInventory;

    @Shadow
    private Map<ICraftingPatternDetails, MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME>>
        patternDetailsPatternSlotMap;

    @Shadow
    private boolean justHadNewItems;

    @Shadow
    public abstract IInventory getPatterns();

    @Shadow
    public abstract boolean isActive();

    @Inject(method = "provideCrafting", at = @At("HEAD"), cancellable = true)
    private void wildcardpattern$provideExpandedPatterns(ICraftingProviderHelper craftingTracker, CallbackInfo ci) {
        if (!hasWildcardPattern()) {
            return;
        }

        ci.cancel();
        if (!isActive()) {
            return;
        }

        IInventory patterns = getPatterns();
        World world = getWorld();
        for (int index = 0; index < this.internalInventory.length && index < patterns.getSizeInventory(); index++) {
            MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = getOrCreateWildcardSlot(index);
            if (slot == null) {
                continue;
            }

            ItemStack stack = patterns.getStackInSlot(index);
            if (WildcardPatternGenerator.isWildcardPattern(stack)) {
                List<ICraftingPatternDetails> detailsList = getExpandedDetails(slot, stack, world);
                for (ICraftingPatternDetails details : detailsList) {
                    this.patternDetailsPatternSlotMap.put(details, slot);
                    craftingTracker.addCraftingOption((ICraftingProvider) (Object) this, details);
                }
                continue;
            }

            ICraftingPatternDetails details = slot.getPatternDetails();
            if (details != null) {
                craftingTracker.addCraftingOption((ICraftingProvider) (Object) this, details);
            }
        }
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true)
    private void wildcardpattern$pushExpandedPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!hasWildcardPattern() || patternDetails == null) {
            return;
        }

        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = this.patternDetailsPatternSlotMap.get(patternDetails);
        if (slot == null) {
            slot = findWildcardPatternSlot(patternDetails);
            if (slot != null) {
                this.patternDetailsPatternSlotMap.put(patternDetails, slot);
            }
        }
        if (slot == null) {
            return;
        }
        if (slot instanceof WildcardPatternSlot wildcardSlot) {
            wildcardSlot.setActivePatternDetails(patternDetails);
        }

        MTEHatchCraftingInputME hatch = (MTEHatchCraftingInputME) (Object) this;
        if (!hatch.isActive() || !hatch.getBaseMetaTileEntity().isAllowedToWork()) {
            cir.setReturnValue(false);
            return;
        }

        if (!slot.insertItemsAndFluids(table)) {
            cir.setReturnValue(false);
            return;
        }

        this.justHadNewItems = true;
        cir.setReturnValue(true);
    }

    @Inject(method = "onPatternChange", at = @At("RETURN"))
    private void wildcardpattern$registerExpandedPatternMap(int index, ItemStack newItem, CallbackInfo ci) {
        registerExpandedPatterns(index, newItem);
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"))
    private void wildcardpattern$registerLoadedExpandedPatternMap(NBTTagCompound tag, CallbackInfo ci) {
        IInventory patterns = getPatterns();
        for (int index = 0; index < this.internalInventory.length && index < patterns.getSizeInventory(); index++) {
            registerExpandedPatterns(index, patterns.getStackInSlot(index));
        }
    }

    private boolean hasWildcardPattern() {
        IInventory patterns = getPatterns();
        for (int index = 0; index < this.internalInventory.length && index < patterns.getSizeInventory(); index++) {
            if (WildcardPatternGenerator.isWildcardPattern(patterns.getStackInSlot(index))) {
                return true;
            }
        }
        return false;
    }

    private void registerExpandedPatterns(int index, ItemStack stack) {
        if (index < 0 || index >= this.internalInventory.length || !WildcardPatternGenerator.isWildcardPattern(stack)) {
            return;
        }

        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = getOrCreateWildcardSlot(index);
        if (slot == null) {
            return;
        }

        World world = getWorld();
        for (ICraftingPatternDetails details : getExpandedDetails(slot, stack, world)) {
            this.patternDetailsPatternSlotMap.put(details, slot);
        }
    }

    private MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> findWildcardPatternSlot(
        ICraftingPatternDetails patternDetails) {
        ItemStack requestedPattern = patternDetails.getPattern();
        if (requestedPattern == null) {
            return null;
        }

        World world = getWorld();
        IInventory patterns = getPatterns();
        for (int index = 0; index < this.internalInventory.length && index < patterns.getSizeInventory(); index++) {
            MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = this.internalInventory[index];
            ItemStack stack = patterns.getStackInSlot(index);
            if (slot == null || !WildcardPatternGenerator.isWildcardPattern(stack)) {
                continue;
            }

            for (ICraftingPatternDetails generated : getExpandedDetails(slot, stack, world)) {
                if (arePatternDetailsEqual(generated, patternDetails)) {
                    return slot;
                }
            }
        }
        return null;
    }

    private MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> getOrCreateWildcardSlot(int index) {
        if (index < 0 || index >= this.internalInventory.length) {
            return null;
        }

        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = this.internalInventory[index];
        IInventory patterns = getPatterns();
        if (patterns == null || index >= patterns.getSizeInventory()) {
            return slot;
        }

        ItemStack stack = patterns.getStackInSlot(index);
        if (!WildcardPatternGenerator.isWildcardPattern(stack) || slot instanceof WildcardPatternSlot) {
            return slot;
        }

        WildcardPatternSlot wrapped = new WildcardPatternSlot((MTEHatchCraftingInputME) (Object) this, stack, slot);
        this.internalInventory[index] = wrapped;
        return wrapped;
    }

    private static List<ICraftingPatternDetails> getExpandedDetails(
        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot,
        ItemStack stack,
        World world) {
        if (slot instanceof WildcardPatternSlot wildcardSlot) {
            return wildcardSlot.getExpandedDetails(stack, world);
        }
        return WildcardPatternGenerator.generateAllDetails(stack, world);
    }

    private static boolean arePatternDetailsEqual(ICraftingPatternDetails left, ICraftingPatternDetails right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        ItemStack leftPattern = left.getPattern();
        ItemStack rightPattern = right.getPattern();
        if (leftPattern == rightPattern) {
            return true;
        }
        if (leftPattern == null || rightPattern == null) {
            return false;
        }
        if (leftPattern.getItem() != rightPattern.getItem() || leftPattern.getItemDamage() != rightPattern.getItemDamage()) {
            return false;
        }
        NBTTagCompound leftTag = leftPattern.getTagCompound();
        NBTTagCompound rightTag = rightPattern.getTagCompound();
        if (leftTag == rightTag) {
            return true;
        }
        if (leftTag == null || rightTag == null) {
            return false;
        }
        return leftTag.equals(rightTag);
    }

    private World getWorld() {
        return ((MTEHatchCraftingInputME) (Object) this).getBaseMetaTileEntity()
            .getWorld();
    }

    private static final class WildcardPatternSlot extends MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> {

        private ICraftingPatternDetails activePatternDetails;
        private String cachedSignature;
        private List<ICraftingPatternDetails> cachedExpandedDetails = java.util.Collections.emptyList();

        private WildcardPatternSlot(
            MTEHatchCraftingInputME parent,
            ItemStack pattern,
            MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> originalSlot) {
            super(pattern, parent);
            if (originalSlot != null) {
                for (ItemStack itemStack : originalSlot.getItemInputs()) {
                    if (itemStack != null) {
                        this.itemInventory.add(itemStack.copy());
                    }
                }
                for (net.minecraftforge.fluids.FluidStack fluidStack : originalSlot.getFluidInputs()) {
                    if (fluidStack != null) {
                        this.fluidInventory.add(fluidStack.copy());
                    }
                }
            }
        }

        private void setActivePatternDetails(ICraftingPatternDetails activePatternDetails) {
            this.activePatternDetails = activePatternDetails;
        }

        private List<ICraftingPatternDetails> getExpandedDetails(ItemStack patternStack, World world) {
            String signature = getPatternSignature(patternStack);
            if (!signature.equals(this.cachedSignature)) {
                this.cachedSignature = signature;
                this.cachedExpandedDetails = WildcardPatternGenerator.generateAllDetails(patternStack, world);
            }
            return this.cachedExpandedDetails;
        }

        @Override
        public ICraftingPatternDetails getPatternDetails() {
            return this.activePatternDetails != null ? this.activePatternDetails : super.getPatternDetails();
        }

        @Override
        public GTDualInputPattern getPatternInputs() {
            ICraftingPatternDetails details = getPatternDetails();
            GTDualInputPattern dualInputs = new GTDualInputPattern();
            ItemStack[] inputItems = this.parentMTE.getSharedItems();
            net.minecraftforge.fluids.FluidStack[] inputFluids = gregtech.api.enums.GTValues.emptyFluidStackArray;

            for (IAEItemStack singleInput : details.getInputs()) {
                if (singleInput == null) {
                    continue;
                }
                ItemStack singleInputItemStack = singleInput.getItemStack();
                if (singleInputItemStack.getItem() instanceof com.glodblock.github.common.item.ItemFluidDrop) {
                    net.minecraftforge.fluids.FluidStack fluidStack = com.glodblock.github.common.item.ItemFluidDrop
                        .getFluidStack(singleInputItemStack);
                    if (fluidStack != null) {
                        inputFluids = org.apache.commons.lang3.ArrayUtils.addAll(inputFluids, fluidStack);
                    }
                } else {
                    inputItems = org.apache.commons.lang3.ArrayUtils.addAll(inputItems, singleInputItemStack);
                }
            }

            dualInputs.inputItems = inputItems;
            dualInputs.inputFluid = inputFluids;
            return dualInputs;
        }

        private static String getPatternSignature(ItemStack stack) {
            if (stack == null) {
                return "";
            }
            NBTTagCompound tag = stack.getTagCompound();
            return stack.getItemDamage() + ":" + (tag == null ? "" : tag.toString());
        }
    }
}

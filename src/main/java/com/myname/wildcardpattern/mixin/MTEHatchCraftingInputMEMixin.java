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
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import net.minecraft.inventory.IInventory;
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
            MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = this.internalInventory[index];
            if (slot == null) {
                continue;
            }

            ItemStack stack = patterns.getStackInSlot(index);
            if (WildcardPatternGenerator.isWildcardPattern(stack)) {
                List<ICraftingPatternDetails> detailsList = WildcardPatternGenerator.generateAllDetails(stack, world);
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
            if (this.internalInventory[index] != null
                && WildcardPatternGenerator.isWildcardPattern(patterns.getStackInSlot(index))) {
                return true;
            }
        }
        return false;
    }

    private void registerExpandedPatterns(int index, ItemStack stack) {
        if (index < 0 || index >= this.internalInventory.length || !WildcardPatternGenerator.isWildcardPattern(stack)) {
            return;
        }

        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = this.internalInventory[index];
        if (slot == null) {
            return;
        }

        World world = getWorld();
        for (ICraftingPatternDetails details : WildcardPatternGenerator.generateAllDetails(stack, world)) {
            this.patternDetailsPatternSlotMap.put(details, slot);
        }
    }

    private World getWorld() {
        return ((MTEHatchCraftingInputME) (Object) this).getBaseMetaTileEntity()
            .getWorld();
    }
}

package com.myname.wildcardpattern.mixin;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.myname.wildcardpattern.WildcardPatternMod;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternCacheBridge;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternCompat;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternSlotBridge;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternSlotState;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

@Pseudo
@Mixin(targets = GTNLPatternCompat.HATCH_TARGET, remap = false)
public abstract class GTNLSuperCraftingInputHatchMEMixin implements GTNLPatternCacheBridge {

    @Shadow
    public Map<ICraftingPatternDetails, Object> patternDetailsPatternSlotMap;

    @Shadow
    public List<?> processingLogics;

    @Shadow
    public boolean justHadNewItems;

    @Shadow
    public boolean supportFluids;

    @Shadow
    public abstract boolean isActive();

    @Unique
    private Field wildcardpattern$internalInventoryField;

    @Unique
    private boolean wildcardpattern$reportedIncompatibleStructure;

    @Inject(method = "provideCrafting", at = @At("HEAD"), cancellable = true, require = 0)
    private void wildcardpattern$provideExpandedPatterns(
        ICraftingProviderHelper craftingTracker,
        CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ICraftingProvider)) {
            return;
        }
        ICraftingProvider provider = (ICraftingProvider) self;
        Object[] slots = wildcardpattern$getInternalSlots();
        if (slots == null || !wildcardpattern$hasWildcard(slots)) {
            return;
        }
        Map<ICraftingPatternDetails, Object> rebuilt = wildcardpattern$collectMappings(slots);
        if (rebuilt == null) {
            return;
        }

        ci.cancel();
        if (!isActive()) {
            return;
        }
        this.patternDetailsPatternSlotMap.clear();
        this.patternDetailsPatternSlotMap.putAll(rebuilt);
        for (ICraftingPatternDetails details : rebuilt.keySet()) {
            craftingTracker.addCraftingOption(provider, details);
        }
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true, require = 0)
    private void wildcardpattern$pushExpandedPattern(
        ICraftingPatternDetails requested,
        InventoryCrafting table,
        CallbackInfoReturnable<Boolean> cir) {
        if (!GTNLPatternCompat.isGeneratedWildcardDetail(requested)) {
            return;
        }

        Object[] slots = wildcardpattern$getInternalSlots();
        if (slots == null) {
            cir.setReturnValue(false);
            return;
        }
        if (!isActive() || table == null || wildcardpattern$hasUnsupportedFluidPacket(table)) {
            cir.setReturnValue(false);
            return;
        }

        Object preferred = this.patternDetailsPatternSlotMap.get(requested);
        GTNLPatternCompat.removeDetachedMappings(this.patternDetailsPatternSlotMap, slots);
        World world = wildcardpattern$getWorld();
        for (Object rawSlot : GTNLPatternCompat.orderedAttachedCandidates(slots, preferred)) {
            if (!(rawSlot instanceof GTNLPatternSlotBridge slot)
                || !slot.wildcardpattern$isOperational()
                || !slot.wildcardpattern$isWildcard()
                || !slot.wildcardpattern$owns(requested, world)) {
                continue;
            }
            GTNLPatternSlotState.Snapshot<ICraftingPatternDetails> snapshot =
                slot.wildcardpattern$beginActivation(requested);
            if (snapshot == null) {
                continue;
            }
            if (!slot.wildcardpattern$insert(table)) {
                slot.wildcardpattern$rollbackActivation(snapshot);
                cir.setReturnValue(false);
                return;
            }
            this.patternDetailsPatternSlotMap.put(requested, rawSlot);
            this.justHadNewItems = true;
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(false);
    }

    @Override
    @Unique
    public void wildcardpattern$invalidatePatternSlot(Object slot) {
        GTNLPatternCompat.invalidateRecipeCache(this.processingLogics, slot);
    }

    @Inject(method = "onPatternChange", at = @At("RETURN"), require = 0)
    private void wildcardpattern$rebuildChangedMappings(int index, ItemStack newItem, CallbackInfo ci) {
        wildcardpattern$rebuildCurrentMappings();
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), require = 0)
    private void wildcardpattern$rebuildLoadedMappings(NBTTagCompound tag, CallbackInfo ci) {
        wildcardpattern$rebuildCurrentMappings();
    }

    @Unique
    private void wildcardpattern$rebuildCurrentMappings() {
        Object[] slots = wildcardpattern$getInternalSlots();
        Map<ICraftingPatternDetails, Object> rebuilt = wildcardpattern$collectMappings(slots);
        if (rebuilt == null) {
            return;
        }
        this.patternDetailsPatternSlotMap.clear();
        this.patternDetailsPatternSlotMap.putAll(rebuilt);
    }

    @Unique
    private Map<ICraftingPatternDetails, Object> wildcardpattern$collectMappings(Object[] slots) {
        if (slots == null) {
            return null;
        }
        World world = wildcardpattern$getWorld();
        if (world == null) {
            return null;
        }
        Map<ICraftingPatternDetails, Object> rebuilt = new LinkedHashMap<>();
        for (Object rawSlot : slots) {
            if (rawSlot == null) {
                continue;
            }
            if (!(rawSlot instanceof GTNLPatternSlotBridge slot) || !slot.wildcardpattern$isOperational()) {
                wildcardpattern$reportIncompatibleStructure("GTNL PatternSlot bridge was not applied", null);
                return null;
            }
            GTNLPatternCompat.replaceOwnedMappings(
                rebuilt,
                rawSlot,
                slot.wildcardpattern$getRegistrationDetails(world));
        }
        return rebuilt;
    }

    @Unique
    private boolean wildcardpattern$hasWildcard(Object[] slots) {
        for (Object rawSlot : slots) {
            if (rawSlot instanceof GTNLPatternSlotBridge slot
                && slot.wildcardpattern$isOperational()
                && slot.wildcardpattern$isWildcard()) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean wildcardpattern$hasUnsupportedFluidPacket(InventoryCrafting table) {
        if (this.supportFluids) {
            return false;
        }
        for (int index = 0; index < table.getSizeInventory(); index++) {
            ItemStack stack = table.getStackInSlot(index);
            if (stack != null && stack.getItem() instanceof ItemFluidPacket) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private World wildcardpattern$getWorld() {
        try {
            return ((IMetaTileEntity) (Object) this).getBaseMetaTileEntity().getWorld();
        } catch (LinkageError | RuntimeException failure) {
            wildcardpattern$reportIncompatibleStructure("GTNL hatch no longer implements IMetaTileEntity", failure);
            return null;
        }
    }

    @Unique
    private Object[] wildcardpattern$getInternalSlots() {
        try {
            Field field = this.wildcardpattern$internalInventoryField;
            if (field == null) {
                field = wildcardpattern$findField(((Object) this).getClass(), "internalInventory");
                if (field == null) {
                    wildcardpattern$reportIncompatibleStructure("GTNL internalInventory field is missing", null);
                    return null;
                }
                field.setAccessible(true);
                this.wildcardpattern$internalInventoryField = field;
            }
            Object array = field.get(this);
            if (array == null || !array.getClass().isArray()) {
                wildcardpattern$reportIncompatibleStructure("GTNL internalInventory is not an array", null);
                return null;
            }
            int length = Array.getLength(array);
            Object[] result = new Object[length];
            for (int index = 0; index < length; index++) {
                result[index] = Array.get(array, index);
            }
            return result;
        } catch (IllegalAccessException | LinkageError | RuntimeException failure) {
            wildcardpattern$reportIncompatibleStructure("Cannot read GTNL internalInventory", failure);
            return null;
        }
    }

    @Unique
    private static Field wildcardpattern$findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @Unique
    private void wildcardpattern$reportIncompatibleStructure(String message, Throwable failure) {
        if (this.wildcardpattern$reportedIncompatibleStructure) {
            return;
        }
        this.wildcardpattern$reportedIncompatibleStructure = true;
        if (failure == null) {
            WildcardPatternMod.LOG.error("[wildcardpattern] {}. GTNL compatibility remains disabled.", message);
        } else {
            WildcardPatternMod.LOG.error(
                "[wildcardpattern] {}. GTNL compatibility remains disabled.",
                message,
                failure);
        }
    }
}

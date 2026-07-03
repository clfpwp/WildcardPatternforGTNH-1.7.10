package com.myname.wildcardpattern.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.storage.data.IAEItemStack;
import com.glodblock.github.common.item.ItemFluidDrop;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import gregtech.api.enums.GTValues;
import gregtech.api.objects.GTDualInputPattern;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

@Mixin(value = MTEHatchCraftingInputME.class, remap = false)
public abstract class MTEHatchCraftingInputMEMixin {

    private static final String AE2_ME_INVENTORY_CRAFTING_CLASS = "appeng.util.inv.MEInventoryCrafting";
    private static volatile Class<?> meInventoryCraftingClass;
    private static volatile boolean meInventoryCraftingClassResolved;

    @Shadow
    private MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME>[] internalInventory;

    @Shadow
    private Map<ICraftingPatternDetails, MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME>>
        patternDetailsPatternSlotMap;

    @Shadow
    private boolean justHadNewItems;

    @Shadow
    @Final
    private boolean supportFluids;

    @Shadow
    public abstract IInventory getPatterns();

    @Shadow
    public abstract boolean isActive();

    @Inject(method = "provideCrafting", at = @At("HEAD"), cancellable = true)
    private void wildcardpattern$provideExpandedPatterns(ICraftingProviderHelper craftingTracker, CallbackInfo ci) {
        cleanupStaleWildcardSlots();
        if (!hasWildcardPattern()) {
            return;
        }

        ci.cancel();
        if (!isActive()) {
            return;
        }

        this.patternDetailsPatternSlotMap.values().removeIf(s -> s instanceof WildcardPatternSlot);

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
        if (patternDetails == null) {
            cir.setReturnValue(false);
            return;
        }

        cleanupStaleWildcardSlots();
        boolean hasWildcardPattern = hasWildcardPattern();
        boolean isWildcardRequest = isWildcardPatternDetails(patternDetails);
        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = this.patternDetailsPatternSlotMap.get(patternDetails);
        if (slot != null && !isCurrentInternalSlot(slot)) {
            MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> detachedSlot = slot;
            this.patternDetailsPatternSlotMap.values()
                .removeIf(s -> s == detachedSlot);
            slot = null;
        }

        if (slot == null && (hasWildcardPattern || isWildcardRequest)) {
            slot = findWildcardPatternSlot(patternDetails);
            if (slot != null) {
                this.patternDetailsPatternSlotMap.put(patternDetails, slot);
            }
        }
        if (slot == null) {
            cir.setReturnValue(false);
            return;
        }
        if (!hasWildcardPattern && !isWildcardRequest) {
            return;
        }
        if (slot instanceof WildcardPatternSlot wildcardSlot) {
            if (!wildcardSlot.canAcceptPattern(patternDetails)) {
                cir.setReturnValue(false);
                return;
            }
            String previousActiveId = wildcardSlot.getActiveGeneratedPatternId();
            wildcardSlot.setActivePatternDetails(patternDetails);
            if (!previousActiveId.equals(wildcardSlot.getActiveGeneratedPatternId())) {
                removeInventoryRecipeCache(slot);
            }
        }

        MTEHatchCraftingInputME hatch = (MTEHatchCraftingInputME) (Object) this;
        if (!hatch.isActive() || !hatch.getBaseMetaTileEntity().isAllowedToWork()) {
            cir.setReturnValue(false);
            return;
        }
        if (hasUnsupportedFluidPacket(table)) {
            cir.setReturnValue(false);
            return;
        }

        if (!isSupportedCraftingTable(table)) {
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
        if (!WildcardPatternGenerator.isWildcardPattern(newItem)) {
            clearWildcardPatternSlot(index, newItem);
            return;
        }
        registerExpandedPatterns(index, newItem);
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"))
    private void wildcardpattern$registerLoadedExpandedPatternMap(NBTTagCompound tag, CallbackInfo ci) {
        IInventory patterns = getPatterns();
        Map<Integer, NBTTagCompound> activePatternTags = readSavedActivePatternTags(tag);
        boolean hasItems = false;
        for (int index = 0; index < this.internalInventory.length && index < patterns.getSizeInventory(); index++) {
            registerExpandedPatterns(index, patterns.getStackInSlot(index), activePatternTags.get(Integer.valueOf(index)));
            MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = this.internalInventory[index];
            if (slot != null && (slot.getItemInputs().length > 0 || slot.getFluidInputs().length > 0)) {
                hasItems = true;
            }
        }
        if (hasItems) {
            this.justHadNewItems = true;
        }
    }

    private static boolean isWildcardPatternDetails(ICraftingPatternDetails patternDetails) {
        ItemStack pattern = patternDetails == null ? null : patternDetails.getPattern();
        return WildcardPatternGenerator.isWildcardPattern(pattern) || WildcardPatternGenerator.isGeneratedPattern(pattern);
    }

    private boolean hasUnsupportedFluidPacket(InventoryCrafting table) {
        if (this.supportFluids || table == null) {
            return false;
        }
        for (int index = 0; index < table.getSizeInventory(); index++) {
            ItemStack itemStack = table.getStackInSlot(index);
            if (itemStack != null && itemStack.getItem() instanceof ItemFluidPacket) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSupportedCraftingTable(InventoryCrafting table) {
        if (table == null) {
            return false;
        }
        Class<?> meInventoryCrafting = getMEInventoryCraftingClass();
        return meInventoryCrafting == null || meInventoryCrafting.isInstance(table);
    }

    private static Class<?> getMEInventoryCraftingClass() {
        if (meInventoryCraftingClassResolved) {
            return meInventoryCraftingClass;
        }
        try {
            meInventoryCraftingClass = Class.forName(AE2_ME_INVENTORY_CRAFTING_CLASS);
        } catch (ClassNotFoundException | LinkageError ignored) {
            // AE2 rv3-beta-695/702 pass a plain InventoryCrafting. Newer AE2 exposes MEInventoryCrafting.
        }
        meInventoryCraftingClassResolved = true;
        return meInventoryCraftingClass;
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
        registerExpandedPatterns(index, stack, null);
    }

    private void registerExpandedPatterns(int index, ItemStack stack, NBTTagCompound savedActivePatternTag) {
        if (index < 0 || index >= this.internalInventory.length) {
            return;
        }

        removeDetachedWildcardMappings();

        if (!WildcardPatternGenerator.isWildcardPattern(stack)) {
            clearWildcardPatternSlot(index, stack);
            return;
        }

        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> targetSlot = this.internalInventory[index];
        if (targetSlot != null) {
            this.patternDetailsPatternSlotMap.values().removeIf(s -> s == targetSlot);
        }

        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = getOrCreateWildcardSlot(index);
        if (slot == null) {
            return;
        }

        if (slot != targetSlot) {
            this.patternDetailsPatternSlotMap.values().removeIf(s -> s == slot);
        }

        List<ICraftingPatternDetails> detailsList = getExpandedDetails(slot, stack, getWorld());
        if (slot instanceof WildcardPatternSlot wildcardSlot) {
            wildcardSlot.restoreActivePatternDetails(savedActivePatternTag, detailsList);
        }
        for (ICraftingPatternDetails details : detailsList) {
            this.patternDetailsPatternSlotMap.put(details, slot);
        }
    }

    private void cleanupStaleWildcardSlots() {
        IInventory patterns = getPatterns();
        for (int index = 0; index < this.internalInventory.length; index++) {
            MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = this.internalInventory[index];
            if (!(slot instanceof WildcardPatternSlot)) {
                continue;
            }
            ItemStack stack = patterns != null && index < patterns.getSizeInventory() ? patterns.getStackInSlot(index) : null;
            if (WildcardPatternGenerator.isWildcardPattern(stack)) {
                ensureWildcardSlotMatches(index, stack);
                continue;
            }
            clearWildcardPatternSlot(index, stack);
        }
        removeDetachedWildcardMappings();
    }

    private MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> clearWildcardPatternSlot(
        int index,
        ItemStack replacementStack) {
        if (index < 0 || index >= this.internalInventory.length) {
            return null;
        }

        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> previous = this.internalInventory[index];
        if (previous != null) {
            removeMappingsForSlot(previous);
            if (previous instanceof WildcardPatternSlot) {
                ((WildcardPatternSlot) previous).clearGeneratedPatternState();
            }
            removeInventoryRecipeCache(previous);
        }

        if (replacementStack == null) {
            this.internalInventory[index] = null;
            removeDetachedWildcardMappings();
            return null;
        }

        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> plain =
            new PlainPatternSlot((MTEHatchCraftingInputME) (Object) this, copyPatternStack(replacementStack), previous);
        this.internalInventory[index] = plain;
        ICraftingPatternDetails details = plain.getPatternDetails();
        if (details != null) {
            this.patternDetailsPatternSlotMap.put(details, plain);
        }
        removeDetachedWildcardMappings();
        return plain;
    }

    private void removeMappingsForSlot(MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot) {
        if (slot != null) {
            this.patternDetailsPatternSlotMap.values().removeIf(s -> s == slot);
        }
    }

    private MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> ensureWildcardSlotMatches(
        int index,
        ItemStack stack) {
        if (index < 0 || index >= this.internalInventory.length) {
            return null;
        }
        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = this.internalInventory[index];
        if (slot instanceof WildcardPatternSlot) {
            WildcardPatternSlot wildcardSlot = (WildcardPatternSlot) slot;
            if (wildcardSlot.hasBackingPattern(stack)) {
                return slot;
            }
        }
        return recreateWildcardSlot(index, stack, slot);
    }

    private MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> recreateWildcardSlot(
        int index,
        ItemStack stack,
        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> originalSlot) {
        removeMappingsForSlot(originalSlot);
        if (originalSlot instanceof WildcardPatternSlot) {
            ((WildcardPatternSlot) originalSlot).clearGeneratedPatternState();
        }
        if (originalSlot != null) {
            removeInventoryRecipeCache(originalSlot);
        }
        WildcardPatternSlot wrapped =
            new WildcardPatternSlot((MTEHatchCraftingInputME) (Object) this, copyPatternStack(stack), originalSlot);
        this.internalInventory[index] = wrapped;
        return wrapped;
    }

    private void removeDetachedWildcardMappings() {
        this.patternDetailsPatternSlotMap.values()
            .removeIf(s -> s instanceof WildcardPatternSlot && !isCurrentInternalSlot(s));
    }

    private boolean isCurrentInternalSlot(MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot) {
        for (MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> current : this.internalInventory) {
            if (current == slot) {
                return true;
            }
        }
        return false;
    }

    private static Map<Integer, NBTTagCompound> readSavedActivePatternTags(NBTTagCompound source) {
        Map<Integer, NBTTagCompound> result = new HashMap<>();
        if (source == null || !source.hasKey("internalInventory", NBT.TAG_LIST)) {
            return result;
        }
        NBTTagList inventory = source.getTagList("internalInventory", NBT.TAG_COMPOUND);
        for (int index = 0; index < inventory.tagCount(); index++) {
            NBTTagCompound slotWrapper = inventory.getCompoundTagAt(index);
            int patternSlot = slotWrapper.getInteger("patternSlot");
            NBTTagCompound slotTag = slotWrapper.getCompoundTag("patternSlotNBT");
            if (slotTag.hasKey(WildcardPatternSlot.KEY_ACTIVE_PATTERN, NBT.TAG_COMPOUND)
                || slotTag.hasKey(WildcardPatternSlot.KEY_ACTIVE_PATTERN_ID)) {
                result.put(Integer.valueOf(patternSlot), slotTag);
            }
        }
        return result;
    }

    private void removeInventoryRecipeCache(MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot) {
        Iterable<?> processingLogics = getProcessingLogics();
        if (processingLogics == null) {
            return;
        }
        for (Object processingLogic : processingLogics) {
            invokeRemoveInventoryRecipeCache(processingLogic, slot);
        }
    }

    private Iterable<?> getProcessingLogics() {
        Field field = findField(((Object) this).getClass(), "processingLogics");
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            Object value = field.get(this);
            if (value instanceof Iterable<?>) {
                return (Iterable<?>) value;
            }
        } catch (IllegalAccessException | SecurityException ignored) {
            // GTNH 2.9 removed this field; cache clearing is best-effort for older GT versions.
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
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

    private static void invokeRemoveInventoryRecipeCache(
        Object processingLogic,
        MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot) {
        if (processingLogic == null) {
            return;
        }
        for (Method method : processingLogic.getClass()
            .getMethods()) {
            if (!"removeInventoryRecipeCache".equals(method.getName()) || method.getParameterTypes().length != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isInstance(slot)) {
                continue;
            }
            try {
                method.invoke(processingLogic, slot);
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // Optional compatibility path only; stale cache is less dangerous than crashing the hatch.
            }
            return;
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
            ItemStack stack = patterns.getStackInSlot(index);
            if (!WildcardPatternGenerator.isWildcardPattern(stack)) {
                continue;
            }
            MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = getOrCreateWildcardSlot(index);
            if (!(slot instanceof WildcardPatternSlot)) {
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
        if (!WildcardPatternGenerator.isWildcardPattern(stack)) {
            if (slot instanceof WildcardPatternSlot) {
                return clearWildcardPatternSlot(index, stack);
            }
            return slot;
        }

        if (slot instanceof WildcardPatternSlot) {
            WildcardPatternSlot wildcardSlot = (WildcardPatternSlot) slot;
            if (wildcardSlot.hasBackingPattern(stack)) {
                return slot;
            }
        }

        return recreateWildcardSlot(index, stack, slot);
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
        String leftId = WildcardPatternGenerator.getGeneratedPatternId(leftPattern);
        String rightId = WildcardPatternGenerator.getGeneratedPatternId(rightPattern);
        if (!leftId.isEmpty() || !rightId.isEmpty()) {
            return leftId.equals(rightId);
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

    private static ItemStack copyPatternStack(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static final class PlainPatternSlot extends MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> {

        private PlainPatternSlot(
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
                for (FluidStack fluidStack : originalSlot.getFluidInputs()) {
                    if (fluidStack != null) {
                        this.fluidInventory.add(fluidStack.copy());
                    }
                }
            }
        }
    }

    private static final class WildcardPatternSlot extends MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> {

        private static final String KEY_ACTIVE_PATTERN = "WildcardActivePattern";
        private static final String KEY_ACTIVE_PATTERN_ID = "WildcardActivePatternId";

        private ICraftingPatternDetails activePatternDetails;
        private ItemStack activePatternStack;
        private String activeGeneratedPatternId = "";
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
                for (FluidStack fluidStack : originalSlot.getFluidInputs()) {
                    if (fluidStack != null) {
                        this.fluidInventory.add(fluidStack.copy());
                    }
                }
            }
        }

        private void setActivePatternDetails(ICraftingPatternDetails activePatternDetails) {
            this.activePatternDetails = activePatternDetails;
            setActivePatternStack(activePatternDetails == null ? null : activePatternDetails.getPattern());
        }

        private void setActivePatternStack(ItemStack activePattern) {
            this.activePatternStack = activePattern == null ? null : activePattern.copy();
            this.activeGeneratedPatternId = WildcardPatternGenerator.getGeneratedPatternId(activePattern);
        }

        private String getActiveGeneratedPatternId() {
            return this.activeGeneratedPatternId;
        }

        private void clearGeneratedPatternState() {
            this.activePatternDetails = null;
            this.activePatternStack = null;
            this.activeGeneratedPatternId = "";
            this.cachedSignature = null;
            this.cachedExpandedDetails = java.util.Collections.emptyList();
        }

        private boolean hasBackingPattern(ItemStack patternStack) {
            return getPatternSignature(this.pattern).equals(getPatternSignature(patternStack));
        }

        private List<ICraftingPatternDetails> getExpandedDetails(ItemStack patternStack, World world) {
            String signature = getPatternSignature(patternStack);
            if (!signature.equals(this.cachedSignature) || this.cachedExpandedDetails.isEmpty()) {
                this.cachedSignature = signature;
                this.cachedExpandedDetails = WildcardPatternGenerator.generateAllDetails(patternStack, world);
            }
            return this.cachedExpandedDetails;
        }

        private boolean canAcceptPattern(ICraftingPatternDetails patternDetails) {
            if (patternDetails == null || isEmpty()) {
                return true;
            }
            String requestedId = WildcardPatternGenerator.getGeneratedPatternId(patternDetails.getPattern());
            if (requestedId.isEmpty()) {
                return false;
            }
            if (this.activeGeneratedPatternId.isEmpty()) {
                setActivePatternDetails(recoverActiveDetails(getCachedOrGeneratedDetails()));
            }
            return requestedId.equals(this.activeGeneratedPatternId);
        }

        private void restoreActivePatternDetails(
            NBTTagCompound savedActivePatternTag,
            List<ICraftingPatternDetails> detailsList) {
            if (this.activePatternDetails != null || !hasStoredInputs()) {
                return;
            }
            String savedId = savedActivePatternTag == null ? "" : savedActivePatternTag.getString(KEY_ACTIVE_PATTERN_ID);
            ItemStack savedActivePattern = null;
            if (savedActivePatternTag != null && savedActivePatternTag.hasKey(KEY_ACTIVE_PATTERN, NBT.TAG_COMPOUND)) {
                savedActivePattern = ItemStack.loadItemStackFromNBT(savedActivePatternTag.getCompoundTag(KEY_ACTIVE_PATTERN));
                if (savedId.isEmpty()) {
                    savedId = WildcardPatternGenerator.getGeneratedPatternId(savedActivePattern);
                }
            }

            ICraftingPatternDetails restored = findMatchingPatternDetails(savedId, detailsList);
            if (restored == null) {
                restored = findMatchingPatternDetails(savedActivePattern, detailsList);
            }
            if (restored == null && savedActivePattern != null) {
                ICraftingPatternDetails savedDetails = createDetailsFromPattern(savedActivePattern);
                if (savedDetails != null && inputsMatchSlot(savedDetails)) {
                    restored = savedDetails;
                }
            }
            if (restored == null) {
                restored = recoverActiveDetails(detailsList);
            }
            setActivePatternDetails(restored);
        }

        @Override
        public ICraftingPatternDetails getPatternDetails() {
            if (this.activePatternDetails != null) {
                if (!hasStoredInputs()) {
                    setActivePatternDetails(null);
                    return super.getPatternDetails();
                }
                return this.activePatternDetails;
            }
            if (hasStoredInputs()) {
                if (this.activePatternStack != null) {
                    ICraftingPatternDetails savedDetails = createDetailsFromPattern(this.activePatternStack);
                    if (savedDetails != null && inputsMatchSlot(savedDetails)) {
                        setActivePatternDetails(savedDetails);
                        return this.activePatternDetails;
                    }
                }
                setActivePatternDetails(recoverActiveDetails(getCachedOrGeneratedDetails()));
                return this.activePatternDetails;
            }
            return super.getPatternDetails();
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
            boolean hasInputs = hasStoredInputs();
            NBTTagCompound written = super.writeToNBT(nbt);
            ItemStack activePattern = this.activePatternStack;
            if (hasInputs && activePattern != null && !this.activeGeneratedPatternId.isEmpty()) {
                NBTTagCompound activeTag = new NBTTagCompound();
                activePattern.writeToNBT(activeTag);
                written.setTag(KEY_ACTIVE_PATTERN, activeTag);
                written.setString(KEY_ACTIVE_PATTERN_ID, this.activeGeneratedPatternId);
            } else {
                written.removeTag(KEY_ACTIVE_PATTERN);
                written.removeTag(KEY_ACTIVE_PATTERN_ID);
            }
            return written;
        }

        private List<ICraftingPatternDetails> getCachedOrGeneratedDetails() {
            if (this.pattern == null) {
                return this.cachedExpandedDetails;
            }
            World world = this.parentMTE.getBaseMetaTileEntity().getWorld();
            if (world == null) {
                return this.cachedExpandedDetails;
            }
            return getExpandedDetails(this.pattern, world);
        }

        private ICraftingPatternDetails recoverActiveDetails(List<ICraftingPatternDetails> detailsList) {
            if (detailsList == null || detailsList.isEmpty()) {
                return null;
            }
            ICraftingPatternDetails recovered = null;
            for (ICraftingPatternDetails details : detailsList) {
                if (!inputsMatchSlot(details)) {
                    continue;
                }
                if (recovered != null && !arePatternStacksEqual(recovered.getPattern(), details.getPattern())) {
                    return null;
                }
                recovered = details;
            }
            return recovered;
        }

        private ICraftingPatternDetails createDetailsFromPattern(ItemStack patternStack) {
            if (patternStack == null) {
                return null;
            }
            World world = this.parentMTE.getBaseMetaTileEntity().getWorld();
            return WildcardPatternGenerator.createDetailForCurrentStack(patternStack.copy(), world);
        }

        private boolean inputsMatchSlot(ICraftingPatternDetails details) {
            IAEItemStack[] inputs = details == null ? null : details.getInputs();
            if (inputs == null) {
                return false;
            }
            boolean sawInput = false;
            for (IAEItemStack input : inputs) {
                if (input == null) {
                    continue;
                }
                ItemStack inputStack = input.getItemStack();
                if (inputStack == null) {
                    continue;
                }
                sawInput = true;
                if (inputStack.getItem() instanceof ItemFluidDrop) {
                    FluidStack fluidStack = ItemFluidDrop.getFluidStack(inputStack);
                    if (fluidStack == null || countStoredFluid(fluidStack) < getRequiredFluidAmount(fluidStack, inputs)) {
                        return false;
                    }
                    continue;
                }
                if (countStoredItems(inputStack) < getRequiredItemAmount(inputStack, inputs)) {
                    return false;
                }
            }
            return sawInput && storedItemsCoveredByPattern(inputs) && storedFluidsCoveredByPattern(inputs);
        }

        private long getRequiredItemAmount(ItemStack expected, IAEItemStack[] inputs) {
            long required = 0L;
            for (IAEItemStack input : inputs) {
                if (input == null) {
                    continue;
                }
                ItemStack inputStack = input.getItemStack();
                if (inputStack == null || inputStack.getItem() instanceof ItemFluidDrop
                    || !GTUtility.areStacksEqual(inputStack, expected)) {
                    continue;
                }
                required += Math.max(Math.max(1L, input.getStackSize()), inputStack.stackSize);
            }
            return required;
        }

        private long getRequiredFluidAmount(FluidStack expected, IAEItemStack[] inputs) {
            long required = 0L;
            for (IAEItemStack input : inputs) {
                if (input == null) {
                    continue;
                }
                ItemStack inputStack = input.getItemStack();
                if (inputStack == null || !(inputStack.getItem() instanceof ItemFluidDrop)) {
                    continue;
                }
                FluidStack fluidStack = ItemFluidDrop.getFluidStack(inputStack);
                if (fluidStack != null && GTUtility.areFluidsEqual(fluidStack, expected)) {
                    required += Math.max(1L, fluidStack.amount);
                }
            }
            return required;
        }

        private long countStoredItems(ItemStack expected) {
            long count = 0L;
            for (ItemStack stored : this.itemInventory) {
                if (stored != null && stored.stackSize > 0 && GTUtility.areStacksEqual(stored, expected)) {
                    count += stored.stackSize;
                }
            }
            return count;
        }

        private long countStoredFluid(FluidStack expected) {
            long amount = 0L;
            for (FluidStack stored : this.fluidInventory) {
                if (stored != null && stored.amount > 0 && GTUtility.areFluidsEqual(stored, expected)) {
                    amount += stored.amount;
                }
            }
            return amount;
        }

        private boolean storedItemsCoveredByPattern(IAEItemStack[] inputs) {
            for (ItemStack stored : this.itemInventory) {
                if (stored != null && stored.stackSize > 0 && !patternHasItemInput(stored, inputs)) {
                    return false;
                }
            }
            return true;
        }

        private boolean storedFluidsCoveredByPattern(IAEItemStack[] inputs) {
            for (FluidStack stored : this.fluidInventory) {
                if (stored != null && stored.amount > 0 && !patternHasFluidInput(stored, inputs)) {
                    return false;
                }
            }
            return true;
        }

        private boolean patternHasItemInput(ItemStack stored, IAEItemStack[] inputs) {
            for (IAEItemStack input : inputs) {
                if (input == null) {
                    continue;
                }
                ItemStack inputStack = input.getItemStack();
                if (inputStack != null && !(inputStack.getItem() instanceof ItemFluidDrop)
                    && GTUtility.areStacksEqual(stored, inputStack)) {
                    return true;
                }
            }
            return false;
        }

        private boolean patternHasFluidInput(FluidStack stored, IAEItemStack[] inputs) {
            for (IAEItemStack input : inputs) {
                if (input == null) {
                    continue;
                }
                ItemStack inputStack = input.getItemStack();
                if (inputStack == null || !(inputStack.getItem() instanceof ItemFluidDrop)) {
                    continue;
                }
                FluidStack fluidStack = ItemFluidDrop.getFluidStack(inputStack);
                if (fluidStack != null && GTUtility.areFluidsEqual(stored, fluidStack)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasStoredInputs() {
            return !isEmpty();
        }

        private static ICraftingPatternDetails findMatchingPatternDetails(
            String savedId,
            List<ICraftingPatternDetails> detailsList) {
            if (savedId == null || savedId.isEmpty() || detailsList == null || detailsList.isEmpty()) {
                return null;
            }
            for (ICraftingPatternDetails details : detailsList) {
                if (details != null && savedId.equals(WildcardPatternGenerator.getGeneratedPatternId(details.getPattern()))) {
                    return details;
                }
            }
            return null;
        }

        private static ICraftingPatternDetails findMatchingPatternDetails(
            ItemStack savedActivePattern,
            List<ICraftingPatternDetails> detailsList) {
            if (savedActivePattern == null || detailsList == null || detailsList.isEmpty()) {
                return null;
            }
            for (ICraftingPatternDetails details : detailsList) {
                if (details != null && arePatternStacksEqual(savedActivePattern, details.getPattern())) {
                    return details;
                }
            }
            return null;
        }

        private static boolean arePatternStacksEqual(ItemStack left, ItemStack right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            if (left.getItem() != right.getItem() || left.getItemDamage() != right.getItemDamage()) {
                return false;
            }
            String leftId = WildcardPatternGenerator.getGeneratedPatternId(left);
            String rightId = WildcardPatternGenerator.getGeneratedPatternId(right);
            if (!leftId.isEmpty() || !rightId.isEmpty()) {
                return leftId.equals(rightId);
            }
            NBTTagCompound leftTag = left.getTagCompound();
            NBTTagCompound rightTag = right.getTagCompound();
            if (leftTag == rightTag) {
                return true;
            }
            return leftTag != null && leftTag.equals(rightTag);
        }

        @Override
        public GTDualInputPattern getPatternInputs() {
            ICraftingPatternDetails details = getPatternDetails();
            GTDualInputPattern dualInputs = new GTDualInputPattern();
            if (details == null) {
                dualInputs.inputItems = GTValues.emptyItemStackArray;
                dualInputs.inputFluid = GTValues.emptyFluidStackArray;
                return dualInputs;
            }
            ItemStack[] inputItems = this.parentMTE.getSharedItems();
            FluidStack[] inputFluids = GTValues.emptyFluidStackArray;

            for (IAEItemStack singleInput : details.getInputs()) {
                if (singleInput == null) {
                    continue;
                }
                ItemStack singleInputItemStack = singleInput.getItemStack();
                if (singleInputItemStack.getItem() instanceof ItemFluidDrop) {
                    FluidStack fluidStack = ItemFluidDrop.getFluidStack(singleInputItemStack);
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
            String itemName = String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()));
            if (itemName == null || itemName.isEmpty() || "null".equals(itemName)) {
                itemName = stack.getItem() == null ? "null" : stack.getItem().getClass().getName();
            }
            return itemName + ":" + Item.getIdFromItem(stack.getItem()) + ":" + stack.getItemDamage() + ":"
                + getStablePatternConfigSignature(stack.getTagCompound());
        }

        private static String getStablePatternConfigSignature(NBTTagCompound tag) {
            if (tag == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            appendTagSignature(builder, tag, "WildcardGeneratedPatternId");
            appendTagSignature(builder, tag, "WildcardSelectedMaterial");
            appendTagSignature(builder, tag, "WildcardInputComponents");
            appendTagSignature(builder, tag, "WildcardOutputComponents");
            appendTagSignature(builder, tag, "WildcardGlobalExcludeMaterials");
            appendTagSignature(builder, tag, "WildcardRuleIncludeMaterials");
            appendTagSignature(builder, tag, "WildcardRuleExcludeMaterials");
            appendTagSignature(builder, tag, "WildcardOreDictPreferences");
            appendTagSignature(builder, tag, "in");
            appendTagSignature(builder, tag, "out");
            appendTagSignature(builder, tag, "crafting");
            return builder.toString();
        }

        private static void appendTagSignature(StringBuilder builder, NBTTagCompound tag, String key) {
            if (!tag.hasKey(key)) {
                return;
            }
            builder.append(key)
                .append('=')
                .append(getStableTagIdentity(tag.getTag(key)))
                .append(';');
        }

        private static String getStableTagIdentity(NBTBase tag) {
            if (tag == null) {
                return "";
            }
            if (tag instanceof NBTTagCompound) {
                NBTTagCompound compound = (NBTTagCompound) tag;
                List<String> keys = new ArrayList<>();
                for (Object key : compound.func_150296_c()) {
                    if (key != null) {
                        keys.add(String.valueOf(key));
                    }
                }
                Collections.sort(keys);
                StringBuilder builder = new StringBuilder("{");
                for (String key : keys) {
                    if (builder.length() > 1) {
                        builder.append(',');
                    }
                    builder.append(key)
                        .append(':')
                        .append(getStableTagIdentity(compound.getTag(key)));
                }
                return builder.append('}').toString();
            }
            return tag.toString();
        }
    }
}

package com.myname.wildcardpattern.compat.gtnl;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import com.glodblock.github.common.item.ItemFluidDrop;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

public final class GTNLPatternCompat {

    public static final String HATCH_TARGET =
        "com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME";
    public static final String SLOT_TARGET = HATCH_TARGET + "$PatternSlot";
    public static final String ACTIVE_PATTERN_ID_KEY = "WildcardActivePatternId";
    private static final String MIXIN_PREFIX = "com.myname.wildcardpattern.mixin.GTNL";

    private GTNLPatternCompat() {}

    public static boolean isOptionalGTNLMixin(String mixinClassName) {
        return mixinClassName != null && mixinClassName.startsWith(MIXIN_PREFIX);
    }

    public static List<ICraftingPatternDetails> expand(ItemStack pattern, World world) {
        return WildcardPatternGenerator.generateAllDetails(pattern, world);
    }

    public static String generatedId(ICraftingPatternDetails details) {
        return details == null ? "" : WildcardPatternGenerator.getGeneratedPatternId(details.getPattern());
    }

    public static boolean isGeneratedWildcardDetail(ICraftingPatternDetails details) {
        return !generatedId(details).isEmpty();
    }

    public static <D, S> void replaceOwnedMappings(Map<D, S> mappings, S slot, Collection<D> details) {
        mappings.entrySet().removeIf(entry -> entry.getValue() == slot);
        if (details == null) {
            return;
        }
        for (D detail : details) {
            if (detail != null) {
                mappings.put(detail, slot);
            }
        }
    }

    public static <D, S> void removeDetachedMappings(Map<D, S> mappings, Object[] attachedSlots) {
        mappings.entrySet().removeIf(entry -> !containsIdentity(attachedSlots, entry.getValue()));
    }

    public static boolean containsIdentity(Object[] values, Object candidate) {
        if (values == null || candidate == null) {
            return false;
        }
        for (Object value : values) {
            if (value == candidate) {
                return true;
            }
        }
        return false;
    }

    public static boolean inputsMatch(
        ICraftingPatternDetails details,
        ItemStack[] storedItems,
        FluidStack[] storedFluids) {
        IAEItemStack[] expected = details == null ? null : details.getInputs();
        if (expected == null || expected.length == 0) {
            return false;
        }
        boolean sawInput = false;
        for (IAEItemStack input : expected) {
            ItemStack stack = input == null ? null : input.getItemStack();
            if (stack == null) {
                continue;
            }
            sawInput = true;
            if (stack.getItem() instanceof ItemFluidDrop) {
                FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
                if (fluid == null || countFluid(storedFluids, fluid) < requiredFluid(expected, fluid)) {
                    return false;
                }
            } else if (countItem(storedItems, stack) < requiredItem(expected, stack)) {
                return false;
            }
        }
        return sawInput && allStoredItemsCovered(storedItems, expected) && allStoredFluidsCovered(storedFluids, expected);
    }

    public static void invalidateRecipeCache(Iterable<?> processingLogics, Object slot) {
        if (processingLogics == null || slot == null) {
            return;
        }
        for (Object logic : processingLogics) {
            if (logic == null) {
                continue;
            }
            for (Method method : logic.getClass().getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!"removeInventoryRecipeCache".equals(method.getName())
                    || parameters.length != 1
                    || !parameters[0].isInstance(slot)) {
                    continue;
                }
                try {
                    method.invoke(logic, slot);
                } catch (ReflectiveOperationException | SecurityException ignored) {
                    // Optional GTNL cache invalidation must never crash crafting dispatch.
                }
                break;
            }
        }
    }

    private static long requiredItem(IAEItemStack[] expected, ItemStack target) {
        long amount = 0;
        for (IAEItemStack input : expected) {
            ItemStack stack = input == null ? null : input.getItemStack();
            if (stack != null && !(stack.getItem() instanceof ItemFluidDrop) && sameItem(stack, target)) {
                amount += Math.max(Math.max(1L, input.getStackSize()), stack.stackSize);
            }
        }
        return amount;
    }

    private static long requiredFluid(IAEItemStack[] expected, FluidStack target) {
        long amount = 0;
        for (IAEItemStack input : expected) {
            ItemStack stack = input == null ? null : input.getItemStack();
            if (stack == null || !(stack.getItem() instanceof ItemFluidDrop)) {
                continue;
            }
            FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
            if (fluid != null && GTUtility.areFluidsEqual(fluid, target)) {
                amount += Math.max(1L, fluid.amount);
            }
        }
        return amount;
    }

    private static long countItem(ItemStack[] stored, ItemStack target) {
        long amount = 0;
        if (stored != null) {
            for (ItemStack stack : stored) {
                if (stack != null && stack.stackSize > 0 && sameItem(stack, target)) {
                    amount += stack.stackSize;
                }
            }
        }
        return amount;
    }

    private static long countFluid(FluidStack[] stored, FluidStack target) {
        long amount = 0;
        if (stored != null) {
            for (FluidStack fluid : stored) {
                if (fluid != null && fluid.amount > 0 && GTUtility.areFluidsEqual(fluid, target)) {
                    amount += fluid.amount;
                }
            }
        }
        return amount;
    }

    private static boolean allStoredItemsCovered(ItemStack[] stored, IAEItemStack[] expected) {
        if (stored == null) {
            return true;
        }
        for (ItemStack stack : stored) {
            if (stack != null && stack.stackSize > 0 && !hasItem(expected, stack)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allStoredFluidsCovered(FluidStack[] stored, IAEItemStack[] expected) {
        if (stored == null) {
            return true;
        }
        for (FluidStack fluid : stored) {
            if (fluid != null && fluid.amount > 0 && !hasFluid(expected, fluid)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasItem(IAEItemStack[] expected, ItemStack target) {
        for (IAEItemStack input : expected) {
            ItemStack stack = input == null ? null : input.getItemStack();
            if (stack != null && !(stack.getItem() instanceof ItemFluidDrop) && sameItem(stack, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        if (left == null || right == null || left.getItem() != right.getItem()) {
            return false;
        }
        int leftDamage = left.getItemDamage();
        int rightDamage = right.getItemDamage();
        if (leftDamage != rightDamage
            && leftDamage != OreDictionary.WILDCARD_VALUE
            && rightDamage != OreDictionary.WILDCARD_VALUE) {
            return false;
        }
        NBTTagCompound leftTag = left.getTagCompound();
        NBTTagCompound rightTag = right.getTagCompound();
        return leftTag == rightTag || leftTag != null && leftTag.equals(rightTag);
    }

    private static boolean hasFluid(IAEItemStack[] expected, FluidStack target) {
        for (IAEItemStack input : expected) {
            ItemStack stack = input == null ? null : input.getItemStack();
            if (stack == null || !(stack.getItem() instanceof ItemFluidDrop)) {
                continue;
            }
            FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
            if (fluid != null && GTUtility.areFluidsEqual(fluid, target)) {
                return true;
            }
        }
        return false;
    }
}

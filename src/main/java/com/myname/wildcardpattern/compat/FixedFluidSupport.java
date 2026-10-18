package com.myname.wildcardpattern.compat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.glodblock.github.common.item.ItemFluidDrop;
import com.glodblock.github.common.item.ItemFluidPacket;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

/** Fixed fluids use fluid drops internally; amounts are always integer millibuckets. */
public final class FixedFluidSupport {

    private FixedFluidSupport() {}

    public static boolean isFluid(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemFluidDrop;
    }

    public static FluidStack getFluid(ItemStack stack) {
        return isFluid(stack) ? ItemFluidDrop.getFluidStack(stack) : null;
    }

    public static ItemStack fromDroppedStack(ItemStack source) {
        if (source == null) return null;
        ItemStack copy = source.copy();
        FluidStack fluid;
        if (isFluid(copy)) {
            fluid = getFluid(copy);
        } else if (copy.getItem() instanceof ItemFluidPacket) {
            fluid = ItemFluidPacket.getFluidStack(copy);
        } else {
            copy.stackSize = 1;
            fluid = GTUtility.getFluidForFilledItem(copy, true);
        }
        if (fluid != null && fluid.amount > 0) return newDrop(fluid);
        if (isFluid(copy) || copy.getItem() instanceof ItemFluidPacket) {
            throw new IllegalArgumentException("Invalid fluid");
        }
        copy.stackSize = 1;
        return copy;
    }

    public static ItemStack newDrop(FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0 || fluid.getFluid() == null) {
            throw new IllegalArgumentException("Invalid fluid");
        }
        ItemStack drop = ItemFluidDrop.newStack(fluid);
        if (fluid.tag != null) drop.getTagCompound().setTag("FluidTag", fluid.tag.copy());
        return drop;
    }

    public static String normalizeUnit(String unit) {
        return "B".equals(unit) ? "B" : "mB";
    }

    public static int parseAmount(String text, String unit) {
        String value = text == null ? "" : text.trim();
        boolean buckets = "B".equals(unit);
        if (!value.matches(buckets ? "[0-9]+(?:\\.[0-9]{1,3})?" : "[0-9]+")) {
            throw new IllegalArgumentException("Invalid fluid amount");
        }
        try {
            int amount = new BigDecimal(value).movePointRight(buckets ? 3 : 0).intValueExact();
            if (amount <= 0) throw new IllegalArgumentException("Invalid fluid amount");
            return amount;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Fluid amount is out of range", e);
        }
    }

    public static String formatAmount(int millibuckets, String unit) {
        return "B".equals(unit)
            ? BigDecimal.valueOf(millibuckets, 3).stripTrailingZeros().toPlainString()
            : Integer.toString(millibuckets);
    }

    /** Bounds condensed fluid quantities to what GT's FluidStack representation can hold. */
    public static boolean areValidInputs(List<ItemStack> inputs) {
        List<FluidStack> fluids = new ArrayList<>();
        List<Long> amounts = new ArrayList<>();
        for (ItemStack input : inputs) {
            if (!isFluid(input)) continue;
            FluidStack fluid = getFluid(input);
            if (fluid == null || fluid.amount <= 0) return false;
            // Target AE2 discards fluid NBT in AEFluidStack.create/setTagCompound. Keep it
            // in saved configuration, but never plan an untagged substitute for tagged fluid.
            if (fluid.tag != null) return false;
            int existing = -1;
            for (int i = 0; i < fluids.size(); i++) {
                if (fluids.get(i).isFluidEqual(fluid)) { existing = i; break; }
            }
            if (existing < 0) {
                fluids.add(fluid);
                amounts.add((long) fluid.amount);
            } else {
                long total = amounts.get(existing) + fluid.amount;
                if (total > Integer.MAX_VALUE) return false;
                amounts.set(existing, total);
            }
        }
        return true;
    }

    public static String identity(ItemStack stack) {
        FluidStack fluid = getFluid(stack);
        if (fluid == null) throw new IllegalArgumentException("Invalid fluid");
        String name = fluid.getFluid().getName();
        return "fluid:" + name.length() + ":" + name + ":" + canonicalTag(fluid.tag) + ":" + fluid.amount;
    }

    private static String canonicalTag(NBTBase tag) {
        if (tag == null) return "null";
        StringBuilder value = new StringBuilder().append(tag.getId()).append(':');
        if (tag instanceof NBTTagCompound compound) {
            List<String> keys = new ArrayList<>(compound.func_150296_c());
            Collections.sort(keys);
            for (String key : keys) {
                String child = canonicalTag(compound.getTag(key));
                value.append(key.length()).append(':').append(key).append(child.length()).append(':').append(child);
            }
        } else if (tag instanceof NBTTagList list) {
            NBTTagList copy = (NBTTagList) list.copy();
            for (int i = copy.tagCount() - 1; i >= 0; i--) {
                String child = canonicalTag(copy.removeTag(i));
                value.append(child.length()).append(':').append(child);
            }
        } else {
            value.append(tag);
        }
        return value.toString();
    }
}

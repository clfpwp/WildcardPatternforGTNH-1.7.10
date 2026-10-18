package com.myname.wildcardpattern.item;

import java.util.List;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.items.misc.ItemEncodedPattern;
import com.myname.wildcardpattern.WildcardPatternMod;
import com.myname.wildcardpattern.crafting.CompositeWildcardPatternGenerator;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public class ItemCompositeWildcardPattern extends ItemEncodedPattern {

    public ItemCompositeWildcardPattern() {
        this.setMaxStackSize(1);
    }

    private static String getSavedRule(NBTTagCompound tag, String key) {
        NBTTagCompound entry = tag.getCompoundTag(key);
        return (entry.hasKey("Matcher") ? entry.getString("Matcher") : entry.getString("Prefix")).trim();
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        CompositeWildcardPatternGenerator.markAsCompositeWildcard(stack);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld) {
        if (stack != null && stack.getTagCompound() == null) {
            CompositeWildcardPatternGenerator.markAsCompositeWildcard(stack);
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        CompositeWildcardPatternGenerator.markAsCompositeWildcard(stack);
        if (!world.isRemote) {
            player.openGui(
                WildcardPatternMod.instance,
                WildcardPatternMod.GUI_COMPOSITE_WILDCARD_PATTERN,
                world,
                player.inventory.currentItem,
                0,
                0);
        }
        return stack;
    }

    @Override
    public boolean onItemUseFirst(
        ItemStack stack,
        EntityPlayer player,
        World world,
        int x,
        int y,
        int z,
        int side,
        float hitX,
        float hitY,
        float hitZ) {
        CompositeWildcardPatternGenerator.markAsCompositeWildcard(stack);
        return false;
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
        return CompositeWildcardPatternGenerator.getDetailsForItem(stack, world);
    }

    @Override
    public ItemStack getOutput(ItemStack item) {
        if (!CompositeWildcardPatternGenerator.isCompositeWildcardPattern(item)) {
            return super.getOutput(item);
        }
        return CompositeWildcardPatternGenerator.getOutputForItem(item, null);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(
        ItemStack stack,
        EntityPlayer player,
        List<String> lines,
        boolean advancedTooltips) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag != null && !WildcardPatternGenerator.isGeneratedPattern(stack)) {
            String input = getSavedRule(tag, "CompositeWildcardInput");
            String output = getSavedRule(tag, "CompositeWildcardOutput");
            if (!input.isEmpty() && !output.isEmpty()) {
                lines.add(StatCollector.translateToLocalFormatted("tooltip.composite_wildcardpattern.rule", input, output));
            }
        }
        lines.add(
            StatCollector.translateToLocalFormatted(
                "tooltip.composite_wildcardpattern.expand_count",
                CompositeWildcardPatternState.getExpandedPatternCount(stack)));
        lines.add(StatCollector.translateToLocal("tooltip.composite_wildcardpattern.usage"));
    }
}

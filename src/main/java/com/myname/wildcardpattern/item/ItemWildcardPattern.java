package com.myname.wildcardpattern.item;

import java.util.List;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.items.misc.ItemEncodedPattern;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import com.myname.wildcardpattern.WildcardPatternMod;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public class ItemWildcardPattern extends ItemEncodedPattern {

    public ItemWildcardPattern() {
        this.setMaxStackSize(1);
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        WildcardPatternGenerator.markAsWildcard(stack);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld) {
        if (stack != null && stack.getTagCompound() == null) {
            WildcardPatternGenerator.markAsWildcard(stack);
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        WildcardPatternGenerator.markAsWildcard(stack);
        if (!world.isRemote) {
            player.openGui(
                WildcardPatternMod.instance,
                WildcardPatternMod.GUI_WILDCARD_PATTERN,
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
        WildcardPatternGenerator.markAsWildcard(stack);
        return false;
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
        WildcardPatternGenerator.markAsWildcard(stack);
        return WildcardPatternGenerator.getFirstDetail(stack, world);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(
        ItemStack stack,
        EntityPlayer player,
        List<String> lines,
        boolean advancedTooltips) {
        if (!WildcardPatternGenerator.isWildcardPattern(stack)) {
            WildcardPatternGenerator.markAsWildcard(stack);
        }
        int count = WildcardPatternGenerator.countPatterns(stack);
        lines.add(
            EnumChatFormatting.GRAY
                + StatCollector.translateToLocalFormatted("tooltip.wildcardpattern.expand_count", Integer.valueOf(count)));
        lines.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.wildcardpattern.usage"));
    }
}

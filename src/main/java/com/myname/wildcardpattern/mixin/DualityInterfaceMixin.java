package com.myname.wildcardpattern.mixin;

import java.util.LinkedList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.tile.inventory.AppEngInternalInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

@Mixin(value = DualityInterface.class, remap = false)
public abstract class DualityInterfaceMixin {

    @Shadow
    private AppEngInternalInventory patterns;

    @Shadow
    private IInterfaceHost iHost;

    @Shadow
    public List<ICraftingPatternDetails> craftingList;

    @Shadow
    protected abstract int getPriority();

    @Inject(method = "addToCraftingList", at = @At("HEAD"), cancellable = true, remap = false)
    private void wildcardpattern$expandWildcardPattern(int slot, CallbackInfo ci) {
        ItemStack stack = this.patterns.getStackInSlot(slot);
        if (!WildcardPatternGenerator.isWildcardPattern(stack)) {
            return;
        }

        World world = this.iHost.getTileEntity().getWorldObj();
        List<ICraftingPatternDetails> detailsList = WildcardPatternGenerator.generateAllDetails(stack, world);
        if (this.craftingList == null) {
            this.craftingList = new LinkedList<>();
        }

        int priority = slot - 36 * this.getPriority();
        for (ICraftingPatternDetails details : detailsList) {
            details.setPriority(priority);
            this.craftingList.add(details);
        }
        ci.cancel();
    }
}

package com.myname.wildcardpattern.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.items.misc.ItemEncodedPattern;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

@Mixin(value = ItemEncodedPattern.class, remap = false)
public abstract class ItemEncodedPatternMixin {

    @Inject(method = "getPatternForItem", at = @At("HEAD"), cancellable = true)
    private void wildcardpattern$useLightweightPatternDetails(
        ItemStack item,
        World world,
        CallbackInfoReturnable<ICraftingPatternDetails> cir) {
        if (!WildcardPatternGenerator.isWildcardPattern(item)) {
            return;
        }
        cir.setReturnValue(WildcardPatternGenerator.getDetailsForItem(item, world));
    }

    @Inject(method = "getOutput", at = @At("HEAD"), cancellable = true)
    private void wildcardpattern$useRepresentativeOutput(ItemStack item, CallbackInfoReturnable<ItemStack> cir) {
        if (!WildcardPatternGenerator.isWildcardPattern(item)) {
            return;
        }
        cir.setReturnValue(WildcardPatternGenerator.getOutputForItem(item, null));
    }

    @Inject(method = "getOutputAE", at = @At("HEAD"), cancellable = true)
    private void wildcardpattern$useRepresentativeOutputAe(
        ItemStack item,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        if (!WildcardPatternGenerator.isWildcardPattern(item) || WildcardPatternGenerator.isGeneratedPattern(item)) {
            return;
        }
        // AE2 falls back to the pattern icon and skips its Shift output renderer when this is null.
        cir.setReturnValue(null);
    }

    @Inject(method = "addCheckedInformation", at = @At("HEAD"), cancellable = true)
    private void wildcardpattern$skipVanillaShiftPreview(
        ItemStack stack,
        EntityPlayer player,
        List<String> lines,
        boolean displayMoreInfo,
        CallbackInfo ci) {
        if (!WildcardPatternGenerator.isWildcardPattern(stack)) {
            return;
        }
        lines.add(StatCollector.translateToLocal("tooltip.wildcardpattern.usage"));
        ci.cancel();
    }
}

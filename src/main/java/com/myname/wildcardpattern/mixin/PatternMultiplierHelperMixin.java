package com.myname.wildcardpattern.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.util.PatternMultiplierHelper;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import com.myname.wildcardpattern.item.WildcardPatternState;
import net.minecraft.item.ItemStack;

@Mixin(value = PatternMultiplierHelper.class, remap = false)
public abstract class PatternMultiplierHelperMixin {

    @Inject(method = "applyModification", at = @At("HEAD"), cancellable = true)
    private static void wildcardpattern$applyModification(ItemStack stack, int bitMultiplier, CallbackInfo ci) {
        if (!WildcardPatternGenerator.isWildcardPattern(stack)) {
            return;
        }
        WildcardPatternState.applyBitModification(stack, bitMultiplier);
        ci.cancel();
    }

    @Inject(method = "getMaxBitMultiplier", at = @At("HEAD"), cancellable = true)
    private static void wildcardpattern$getMaxBitMultiplier(
        ICraftingPatternDetails details,
        CallbackInfoReturnable<Integer> cir) {
        ItemStack pattern = details == null ? null : details.getPattern();
        if (WildcardPatternGenerator.isWildcardPattern(pattern)) {
            cir.setReturnValue(Integer.valueOf(WildcardPatternState.getMaxBitMultiplier(pattern)));
        }
    }

    @Inject(method = "getMaxBitDivider", at = @At("HEAD"), cancellable = true)
    private static void wildcardpattern$getMaxBitDivider(
        ICraftingPatternDetails details,
        CallbackInfoReturnable<Integer> cir) {
        ItemStack pattern = details == null ? null : details.getPattern();
        if (WildcardPatternGenerator.isWildcardPattern(pattern)) {
            cir.setReturnValue(Integer.valueOf(WildcardPatternState.getMaxBitDivider(pattern)));
        }
    }
}

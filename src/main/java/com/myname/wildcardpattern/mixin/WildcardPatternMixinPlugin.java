package com.myname.wildcardpattern.mixin;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassVisitor;
import org.spongepowered.asm.lib.FieldVisitor;
import org.spongepowered.asm.lib.MethodVisitor;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.myname.wildcardpattern.WildcardPatternMod;
import com.myname.wildcardpattern.compat.gtnl.GTNLPatternCompat;

import net.minecraft.launchwrapper.Launch;

public final class WildcardPatternMixinPlugin implements IMixinConfigPlugin {

    private final Predicate<String> targetCompatible;
    private boolean reportedUnavailable;

    public WildcardPatternMixinPlugin() {
        this(WildcardPatternMixinPlugin::hasCompatibleStructure);
    }

    WildcardPatternMixinPlugin(Predicate<String> targetCompatible) {
        this.targetCompatible = targetCompatible;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!GTNLPatternCompat.isOptionalGTNLMixin(mixinClassName)) {
            return true;
        }
        boolean apply = this.targetCompatible.test(GTNLPatternCompat.HATCH_TARGET)
            && this.targetCompatible.test(GTNLPatternCompat.SLOT_TARGET);
        if (!apply && !this.reportedUnavailable) {
            this.reportedUnavailable = true;
            WildcardPatternMod.LOG.debug("GTNL pattern assembly compatibility is not applicable");
        }
        return apply;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    private static boolean hasCompatibleStructure(String className) {
        byte[] classBytes;
        try {
            classBytes = Launch.classLoader == null ? null : Launch.classLoader.getClassBytes(className);
        } catch (IOException | LinkageError | RuntimeException ignored) {
            return false;
        }
        return hasCompatibleStructure(className, classBytes);
    }

    static boolean hasCompatibleStructure(String className, byte[] classBytes) {
        if (classBytes == null) {
            return false;
        }

        Set<String> requiredFields = requiredFields(className);
        Set<String> requiredMethods = requiredMethods(className);
        Set<String> requiredInterfaces = requiredInterfaces(className);
        if (requiredFields == null || requiredMethods == null || requiredInterfaces == null) {
            return false;
        }

        Set<String> fields = new HashSet<>();
        Set<String> methods = new HashSet<>();
        Set<String> directInterfaces = new HashSet<>();
        try {
            new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {

                @Override
                public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces) {
                    if (interfaces != null) {
                        directInterfaces.addAll(Arrays.asList(interfaces));
                    }
                }

                @Override
                public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value) {
                    fields.add(name + descriptor);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                    methods.add(name + descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
        return fields.containsAll(requiredFields)
            && methods.containsAll(requiredMethods)
            && directInterfaces.containsAll(requiredInterfaces);
    }

    private static Set<String> requiredFields(String className) {
        if (GTNLPatternCompat.HATCH_TARGET.equals(className)) {
            return setOf(
                "internalInventory[Lcom/science/gtnl/common/machine/hatch/"
                    + "SuperCraftingInputHatchME$PatternSlot;",
                "patternDetailsPatternSlotMapLjava/util/Map;",
                "processingLogicsLjava/util/List;",
                "justHadNewItemsZ",
                "supportFluidsZ");
        }
        if (GTNLPatternCompat.SLOT_TARGET.equals(className)) {
            return setOf(
                "patternLnet/minecraft/item/ItemStack;",
                "patternDetailsLappeng/api/networking/crafting/ICraftingPatternDetails;",
                "parentMTELgregtech/api/interfaces/metatileentity/IMetaTileEntity;",
                "itemInventoryLjava/util/List;",
                "fluidInventoryLjava/util/List;");
        }
        return null;
    }

    private static Set<String> requiredMethods(String className) {
        if (GTNLPatternCompat.HATCH_TARGET.equals(className)) {
            return setOf(
                "isActive()Z",
                "provideCrafting(Lappeng/api/networking/crafting/ICraftingProviderHelper;)V",
                "pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;"
                    + "Lnet/minecraft/inventory/InventoryCrafting;)Z",
                "onPatternChange(ILnet/minecraft/item/ItemStack;)V",
                "loadNBTData(Lnet/minecraft/nbt/NBTTagCompound;)V");
        }
        if (GTNLPatternCompat.SLOT_TARGET.equals(className)) {
            return setOf(
                "<init>(Lnet/minecraft/item/ItemStack;Lnet/minecraft/nbt/NBTTagCompound;"
                    + "Lgregtech/api/interfaces/metatileentity/IMetaTileEntity;I)V",
                "getPatternDetails()Lappeng/api/networking/crafting/ICraftingPatternDetails;",
                "getPatternInputs()Lgregtech/api/objects/GTDualInputPattern;",
                "writeToNBT(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
                "isEmpty()Z",
                "getItemInputs()[Lnet/minecraft/item/ItemStack;",
                "getFluidInputs()[Lnet/minecraftforge/fluids/FluidStack;",
                "insertItemsAndFluids(Lnet/minecraft/inventory/InventoryCrafting;)Z");
        }
        return null;
    }

    private static Set<String> requiredInterfaces(String className) {
        if (GTNLPatternCompat.HATCH_TARGET.equals(className)) {
            return setOf("appeng/api/networking/crafting/ICraftingProvider");
        }
        if (GTNLPatternCompat.SLOT_TARGET.equals(className)) {
            return setOf();
        }
        return null;
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}

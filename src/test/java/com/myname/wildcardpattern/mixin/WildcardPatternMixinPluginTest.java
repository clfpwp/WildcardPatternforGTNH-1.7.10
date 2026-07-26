package com.myname.wildcardpattern.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;

import com.myname.wildcardpattern.compat.gtnl.GTNLPatternCompat;

class WildcardPatternMixinPluginTest {

    private static final String HATCH_MIXIN =
        "com.myname.wildcardpattern.mixin.GTNLSuperCraftingInputHatchMEMixin";
    private static final String CRAFTING_PROVIDER =
        "appeng/api/networking/crafting/ICraftingProvider";

    @Test
    void ordinaryMixinsAlwaysApply() {
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(name -> false);

        assertTrue(plugin.shouldApplyMixin("appeng.helpers.DualityInterface", "ordinary.Mixin"));
    }

    @Test
    void gtnlMixinsAreSkippedWhenGTNLIsAbsent() {
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(name -> false);

        assertFalse(plugin.shouldApplyMixin(GTNLPatternCompat.HATCH_TARGET, HATCH_MIXIN));
    }

    @Test
    void bothTargetClassesMustPassValidation() {
        Set<String> compatible = new HashSet<>();
        compatible.add(GTNLPatternCompat.HATCH_TARGET);
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(compatible::contains);

        assertFalse(plugin.shouldApplyMixin(GTNLPatternCompat.HATCH_TARGET, HATCH_MIXIN));
    }

    @Test
    void gtnlMixinsApplyWhenBothTargetClassesAreCompatible() {
        Set<String> compatible = new HashSet<>();
        compatible.add(GTNLPatternCompat.HATCH_TARGET);
        compatible.add(GTNLPatternCompat.SLOT_TARGET);
        WildcardPatternMixinPlugin plugin = new WildcardPatternMixinPlugin(compatible::contains);

        assertTrue(plugin.shouldApplyMixin(GTNLPatternCompat.HATCH_TARGET, HATCH_MIXIN));
    }

    @Test
    void runtimeBridgeMustLiveOutsideTheMixinPackage() {
        String bridgeClass = GTNLPatternSlotMixin.class.getInterfaces()[0].getName();

        assertFalse(
            bridgeClass.startsWith("com.myname.wildcardpattern.mixin."),
            "Mixin-owned packages cannot contain interfaces referenced directly by transformed targets");
    }

    @Test
    void pluginMustUseMixinRelocatedAsmOnly() throws IOException {
        String constantPool = classFileText(WildcardPatternMixinPlugin.class);

        assertFalse(
            constantPool.contains("org/objectweb/asm/"),
            "UniMixins cannot safely rewrite a plugin that mixes relocated and unrelocated ASM types");
    }

    @Test
    void gtnlSlotRefreshMustAllowNullWorldDuringNbtLoad() throws IOException {
        AtomicBoolean guardedWorldParameter = new AtomicBoolean();
        new ClassReader(GTNLPatternSlotMixin.class.getName()).accept(new ClassVisitor(Opcodes.ASM5) {

            @Override
            public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions) {
                if (!"wildcardpattern$refreshDetails".equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM5) {

                    private int loadedReference = -1;

                    @Override
                    public void visitVarInsn(int opcode, int variable) {
                        this.loadedReference = opcode == Opcodes.ALOAD ? variable : -1;
                    }

                    @Override
                    public void visitJumpInsn(int opcode, Label label) {
                        if (this.loadedReference == 1 && (opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL)) {
                            guardedWorldParameter.set(true);
                        }
                        this.loadedReference = -1;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertFalse(
            guardedWorldParameter.get(),
            "Wildcard expansion supports a null world and must run during early NBT construction");
    }

    @Test
    void gtnlSlotMixinMustReadAeBuffersSeparatelyFromManualSupplements() throws IOException {
        Set<String> fields = new HashSet<>();
        new ClassReader(GTNLPatternSlotMixin.class.getName()).accept(new ClassVisitor(Opcodes.ASM5) {

            @Override
            public org.objectweb.asm.FieldVisitor visitField(
                int access,
                String name,
                String descriptor,
                String signature,
                Object value) {
                fields.add(name + descriptor);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue(fields.contains("itemInventoryLjava/util/List;"));
        assertTrue(fields.contains("fluidInventoryLjava/util/List;"));
    }

    @Test
    void slotStateTransitionsCanInvalidateTheParentRecipeCache() throws IOException {
        String cacheBridge = Arrays.stream(GTNLSuperCraftingInputHatchMEMixin.class.getInterfaces())
            .map(Class::getName)
            .filter(name -> name.endsWith("GTNLPatternCacheBridge"))
            .findFirst()
            .orElse(null);
        assertNotNull(cacheBridge, "The GTNL hatch mixin must expose cache invalidation to its pattern slots");
        assertFalse(cacheBridge.startsWith("com.myname.wildcardpattern.mixin."));
        assertTrue(classFileText(GTNLPatternSlotMixin.class).contains(cacheBridge.replace('.', '/')));
    }

    @Test
    void isEmptyHeadInjectionSynchronizesTheActiveDetail() throws IOException {
        Method injection = Arrays.stream(GTNLPatternSlotMixin.class.getDeclaredMethods())
            .filter(method -> {
                Inject annotation = method.getAnnotation(Inject.class);
                return annotation != null
                    && Arrays.asList(annotation.method()).contains("isEmpty")
                    && Arrays.stream(annotation.at()).anyMatch(at -> "HEAD".equals(at.value()));
            })
            .findFirst()
            .orElse(null);
        assertNotNull(injection, "isEmpty must synchronize wildcard state before GTNL checks its cached recipe");

        AtomicBoolean callsSync = new AtomicBoolean();
        String injectionName = injection.getName();
        new ClassReader(GTNLPatternSlotMixin.class.getName()).accept(new ClassVisitor(Opcodes.ASM5) {

            @Override
            public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions) {
                if (!injectionName.equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM5) {

                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                        if ("wildcardpattern$syncActiveDetail".equals(name)) {
                            callsSync.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue(callsSync.get(), "The isEmpty HEAD hook must clear stale active IDs and invalidate caches");
    }

    @Test
    void slotOwnershipUsesTheCachedStateIndexWithoutRefreshingDetails() throws IOException {
        Set<String> calls = new HashSet<>();
        new ClassReader(GTNLPatternSlotMixin.class.getName()).accept(new ClassVisitor(Opcodes.ASM5) {

            @Override
            public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions) {
                if (!"wildcardpattern$owns".equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM5) {

                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                        calls.add(owner + "." + name);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        String slotMixin = GTNLPatternSlotMixin.class.getName().replace('.', '/');
        String compat = GTNLPatternCompat.class.getName().replace('.', '/');
        String state = "com/myname/wildcardpattern/compat/gtnl/GTNLPatternSlotState";
        assertFalse(calls.contains(slotMixin + ".wildcardpattern$getRegistrationDetails"));
        assertFalse(calls.contains(compat + ".expand"));
        assertTrue(calls.contains(state + ".containsId"));
    }

    @Test
    void hatchBytecodeMustContainEveryRequiredMember() {
        assertTrue(
            WildcardPatternMixinPlugin.hasCompatibleStructure(
                GTNLPatternCompat.HATCH_TARGET,
                hatchClassBytes(true)));
        assertFalse(
            WildcardPatternMixinPlugin.hasCompatibleStructure(
                GTNLPatternCompat.HATCH_TARGET,
                hatchClassBytes(false)));
    }

    @Test
    void hatchBytecodeMustDirectlyImplementCraftingProvider() {
        assertTrue(
            WildcardPatternMixinPlugin.hasCompatibleStructure(
                GTNLPatternCompat.HATCH_TARGET,
                hatchClassBytes(true, true)));
        assertFalse(
            WildcardPatternMixinPlugin.hasCompatibleStructure(
                GTNLPatternCompat.HATCH_TARGET,
                hatchClassBytes(true, false)));
    }

    @Test
    void java17NestMemberMustNotRejectCompatibleGTNLHatch() {
        assertTrue(
            WildcardPatternMixinPlugin.hasCompatibleStructure(
                GTNLPatternCompat.HATCH_TARGET,
                withNestMember(hatchClassBytes(true, true))));
    }

    @Test
    void java17NestHostMustNotRejectCompatibleGTNLSlot() {
        assertTrue(
            WildcardPatternMixinPlugin.hasCompatibleStructure(
                GTNLPatternCompat.SLOT_TARGET,
                withNestHost(slotClassBytes(true))));
    }

    @Test
    void provideCraftingChecksProviderBeforeCancelling() throws IOException {
        int[] instruction = { 0 };
        int[] providerGuard = { -1 };
        int[] cancellation = { -1 };
        boolean[] awaitingGuardJump = { false };
        boolean[] falsePath = { false };
        boolean[] falsePathReturned = { false };
        boolean[] falsePathReachedProviderUse = { false };
        Label[] successLabel = { null };
        Label[] failureLabel = { null };
        new ClassReader(GTNLSuperCraftingInputHatchMEMixin.class.getName()).accept(
            new ClassVisitor(Opcodes.ASM5) {

                @Override
                public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                    if (!"wildcardpattern$provideExpandedPatterns".equals(name)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM5) {

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.INSTANCEOF && CRAFTING_PROVIDER.equals(type)) {
                                providerGuard[0] = instruction[0];
                                awaitingGuardJump[0] = true;
                            } else if (falsePath[0]
                                && opcode == Opcodes.CHECKCAST
                                && CRAFTING_PROVIDER.equals(type)) {
                                falsePathReachedProviderUse[0] = true;
                            }
                            instruction[0]++;
                        }

                        @Override
                        public void visitJumpInsn(int opcode, Label label) {
                            if (awaitingGuardJump[0]) {
                                if (opcode == Opcodes.IFNE) {
                                    falsePath[0] = true;
                                    successLabel[0] = label;
                                } else if (opcode == Opcodes.IFEQ) {
                                    failureLabel[0] = label;
                                }
                                awaitingGuardJump[0] = false;
                            }
                        }

                        @Override
                        public void visitLabel(Label label) {
                            if (label == successLabel[0]) {
                                falsePath[0] = false;
                            } else if (label == failureLabel[0]) {
                                falsePath[0] = true;
                            }
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (falsePath[0] && opcode == Opcodes.RETURN) {
                                falsePathReturned[0] = true;
                                falsePath[0] = false;
                            }
                        }

                        @Override
                        public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface) {
                            if ("org/spongepowered/asm/mixin/injection/callback/CallbackInfo".equals(owner)
                                && "cancel".equals(name)) {
                                cancellation[0] = instruction[0];
                                if (falsePath[0]) {
                                    falsePathReachedProviderUse[0] = true;
                                }
                            }
                            instruction[0]++;
                        }
                    };
                }
            },
            ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue(providerGuard[0] >= 0, "The optional mixin must fail closed when the target is not a provider");
        assertTrue(cancellation[0] > providerGuard[0], "Provider compatibility must be checked before cancellation");
        assertTrue(falsePathReturned[0], "A non-provider target must return from the injection");
        assertFalse(
            falsePathReachedProviderUse[0],
            "A non-provider target must return before provider casts or cancellation");
    }

    @Test
    void slotBytecodeMustExposeSeparateAeBufferFields() {
        assertTrue(
            WildcardPatternMixinPlugin.hasCompatibleStructure(
                GTNLPatternCompat.SLOT_TARGET,
                slotClassBytes(true)));
        assertFalse(
            WildcardPatternMixinPlugin.hasCompatibleStructure(
                GTNLPatternCompat.SLOT_TARGET,
                slotClassBytes(false)));
    }

    private static byte[] hatchClassBytes(boolean includePushPattern) {
        return hatchClassBytes(includePushPattern, true);
    }

    private static byte[] hatchClassBytes(boolean includePushPattern, boolean implementsCraftingProvider) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            GTNLPatternCompat.HATCH_TARGET.replace('.', '/'),
            null,
            "java/lang/Object",
            implementsCraftingProvider ? new String[] { CRAFTING_PROVIDER } : null);
        writer.visitField(
            Opcodes.ACC_PUBLIC,
            "internalInventory",
            "[Lcom/science/gtnl/common/machine/hatch/SuperCraftingInputHatchME$PatternSlot;",
            null,
            null);
        writer.visitField(Opcodes.ACC_PUBLIC, "patternDetailsPatternSlotMap", "Ljava/util/Map;", null, null);
        writer.visitField(Opcodes.ACC_PUBLIC, "processingLogics", "Ljava/util/List;", null, null);
        writer.visitField(Opcodes.ACC_PUBLIC, "justHadNewItems", "Z", null, null);
        writer.visitField(Opcodes.ACC_PUBLIC, "supportFluids", "Z", null, null);
        abstractMethod(writer, "isActive", "()Z");
        abstractMethod(
            writer,
            "provideCrafting",
            "(Lappeng/api/networking/crafting/ICraftingProviderHelper;)V");
        if (includePushPattern) {
            abstractMethod(
                writer,
                "pushPattern",
                "(Lappeng/api/networking/crafting/ICraftingPatternDetails;"
                    + "Lnet/minecraft/inventory/InventoryCrafting;)Z");
        }
        abstractMethod(writer, "onPatternChange", "(ILnet/minecraft/item/ItemStack;)V");
        abstractMethod(writer, "loadNBTData", "(Lnet/minecraft/nbt/NBTTagCompound;)V");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] slotClassBytes(boolean includeBufferedInventory) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            GTNLPatternCompat.SLOT_TARGET.replace('.', '/'),
            null,
            "java/lang/Object",
            null);
        writer.visitField(Opcodes.ACC_PUBLIC, "pattern", "Lnet/minecraft/item/ItemStack;", null, null);
        writer.visitField(
            Opcodes.ACC_PUBLIC,
            "patternDetails",
            "Lappeng/api/networking/crafting/ICraftingPatternDetails;",
            null,
            null);
        writer.visitField(
            Opcodes.ACC_PUBLIC,
            "parentMTE",
            "Lgregtech/api/interfaces/metatileentity/IMetaTileEntity;",
            null,
            null);
        if (includeBufferedInventory) {
            writer.visitField(Opcodes.ACC_PUBLIC, "itemInventory", "Ljava/util/List;", null, null);
            writer.visitField(Opcodes.ACC_PUBLIC, "fluidInventory", "Ljava/util/List;", null, null);
        }
        abstractMethod(
            writer,
            "<init>",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/nbt/NBTTagCompound;"
                + "Lgregtech/api/interfaces/metatileentity/IMetaTileEntity;I)V");
        abstractMethod(
            writer,
            "getPatternDetails",
            "()Lappeng/api/networking/crafting/ICraftingPatternDetails;");
        abstractMethod(writer, "getPatternInputs", "()Lgregtech/api/objects/GTDualInputPattern;");
        abstractMethod(
            writer,
            "writeToNBT",
            "(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;");
        abstractMethod(writer, "isEmpty", "()Z");
        abstractMethod(writer, "getItemInputs", "()[Lnet/minecraft/item/ItemStack;");
        abstractMethod(writer, "getFluidInputs", "()[Lnet/minecraftforge/fluids/FluidStack;");
        abstractMethod(
            writer,
            "insertItemsAndFluids",
            "(Lnet/minecraft/inventory/InventoryCrafting;)Z");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] withNestMember(byte[] classBytes) {
        return withNestAttribute(classBytes, true);
    }

    private static byte[] withNestHost(byte[] classBytes) {
        return withNestAttribute(classBytes, false);
    }

    private static byte[] withNestAttribute(byte[] classBytes, boolean member) {
        org.spongepowered.asm.lib.ClassWriter writer = new org.spongepowered.asm.lib.ClassWriter(0);
        new org.spongepowered.asm.lib.ClassReader(classBytes).accept(
            new org.spongepowered.asm.lib.ClassVisitor(org.spongepowered.asm.lib.Opcodes.ASM9, writer) {

                @Override
                public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces) {
                    super.visit(61, access, name, signature, superName, interfaces);
                    if (member) {
                        super.visitNestMember(GTNLPatternCompat.SLOT_TARGET.replace('.', '/'));
                    } else {
                        super.visitNestHost(GTNLPatternCompat.HATCH_TARGET.replace('.', '/'));
                    }
                }
            },
            0);
        return writer.toByteArray();
    }

    private static void abstractMethod(ClassWriter writer, String name, String descriptor) {
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, descriptor, null, null)
            .visitEnd();
    }

    private static String classFileText(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        InputStream input = type.getClassLoader().getResourceAsStream(resource);
        assertNotNull(input);
        try (InputStream classBytes = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = classBytes.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}

package com.myname.wildcardpattern.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.gtnewhorizons.modularui.api.ModularUITextures;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.Text;
import com.gtnewhorizons.modularui.api.drawable.shapes.Rectangle;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;
import com.myname.wildcardpattern.compat.GTCompat;
import com.myname.wildcardpattern.compat.NechSearchCompat;
import com.myname.wildcardpattern.crafting.CompositeWildcardPatternGenerator;
import com.myname.wildcardpattern.crafting.WildcardPatternEntry;
import com.myname.wildcardpattern.item.CompositeWildcardPatternState;
import com.myname.wildcardpattern.item.WildcardPatternConfig;
import com.myname.wildcardpattern.network.MessageUpdateCompositeWildcardConfig;
import com.myname.wildcardpattern.network.WildcardNetwork;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

public final class CompositeWildcardPatternWindow {

    private static final int GUI_WIDTH = 452;
    private static final int GUI_HEIGHT = 292;
    private static final int FIXED_INPUTS = CompositeWildcardPatternState.MAX_FIXED_INPUTS;
    private static final int PREVIEW_LINES = 12;
    private static final int DEDUPE_LINES = 4;
    private static final int EXCLUDE_LINES = 7;
    private static final int ENTRY_TEXT_WIDTH = 72;
    private static final int ENTRY_MODE_X = ENTRY_TEXT_WIDTH + 3;
    private static final int ENTRY_MODE_WIDTH = 34;
    private static final int ENTRY_AMOUNT_X = ENTRY_MODE_X + ENTRY_MODE_WIDTH + 3;
    private static final int ENTRY_AMOUNT_WIDTH = 40;

    private static final int PANEL_COLOR = 0xF2C6C6C6;
    private static final int PANEL_LINE_DARK = 0xFF4E4E4E;
    private static final int PANEL_LINE_LIGHT = 0xFFE5E5E5;
    private static final int FIELD_COLOR = 0xFF4A4D50;
    private static final int FIELD_LINE_DARK = 0xFF3A3D40;
    private static final int FIELD_LINE_LIGHT = 0xFFE8E8E8;
    private static final int FIELD_TEXT_COLOR = 0xFFFFFFFF;
    private static final int BUTTON_TEXT_COLOR = 0xFF222222;
    private static final int PANEL_SHADOW_COLOR = 0x22000000;
    private static final int CARD_FILL_COLOR = 0x66FFFFFF;
    private static final int CARD_SHADOW_COLOR = 0x16000000;

    private CompositeWildcardPatternWindow() {}

    public static ModularWindow createWindow(UIBuildContext buildContext, EntityPlayer player, int slot) {
        WindowState state = new WindowState(player, slot);
        ModularWindow.Builder builder = ModularWindow.builder(GUI_WIDTH, GUI_HEIGHT);
        builder.setBackground(ModularUITextures.VANILLA_BACKGROUND);

        addHeader(builder, state);
        addMainPage(builder, state);
        addPreviewPage(builder, state);
        addExcludePage(builder, state);
        addDedupePage(builder, state);

        return builder.build();
    }

    private static void addHeader(ModularWindow.Builder builder, WindowState state) {
        TextWidget title = new TextWidget("");
        title.setPos(10, 9);
        title.setScale(0.95f);
        title.setStringSupplier(() -> EnumChatFormatting.BLACK + "" + EnumChatFormatting.BOLD
            + tr(state.dedupePage
                ? "gui.composite_wildcardpattern.dedupe_page"
                : state.excludePage
                    ? "gui.wildcardpattern.exclude_page"
                    : state.previewPage ? "gui.composite_wildcardpattern.preview_page" : "gui.composite_wildcardpattern.title"));
        builder.widget(title);

        TextWidget hint = new TextWidget("");
        hint.setPos(150, 11);
        hint.setSize(280, 10);
        hint.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY
            + tr(state.dedupePage
                ? "gui.wildcardpattern.dedupe_hint"
                : state.excludePage
                    ? "gui.wildcardpattern.exclude_hint"
                    : state.previewPage
                        ? "gui.composite_wildcardpattern.preview_hint"
                        : "gui.composite_wildcardpattern.drag_hint"));
        builder.widget(hint);
    }

    private static void addMainPage(ModularWindow.Builder builder, WindowState state) {
        addPanel(builder, state, 8, 28, 436, 214, Page.MAIN);

        addPageText(builder, state, Page.MAIN, EnumChatFormatting.DARK_GRAY + tr("gui.composite_wildcardpattern.wildcard_rule"), 18, 40);
        addPageText(builder, state, Page.MAIN, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_input"), 18, 58);
        addPageText(builder, state, Page.MAIN, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_output"), 246, 58);
        EntryCellRefs inputRef = addEntryCell(builder, state, Page.MAIN, state.wildcardInput, 18, 70);
        EntryCellRefs outputRef = addEntryCell(builder, state, Page.MAIN, state.wildcardOutput, 246, 70);
        List<FixedCellRefs> fixedRefs = new ArrayList<>();
        ButtonWidget multiply = button("gui.wildcardpattern.multiply_short");
        multiply.setPos(188, 70);
        multiply.setSize(42, 16);
        multiply.setOnClick((clickData, widget) -> {
            if (clickData.mouseButton == 1) {
                state.divideAll();
            } else {
                state.multiplyAll();
            }
            inputRef.updateAmount();
            outputRef.updateAmount();
            for (FixedCellRefs ref : fixedRefs) {
                ref.update();
            }
        });
        addPageWidget(builder, state, multiply, Page.MAIN);

        addSeparator(builder, state, 18, 96, 416, 2, Page.MAIN);
        addPageText(builder, state, Page.MAIN, EnumChatFormatting.DARK_GRAY + tr("gui.composite_wildcardpattern.fixed_inputs"), 18, 106);
        for (int index = 0; index < FIXED_INPUTS; index++) {
            int column = index % 2;
            int row = index / 2;
            fixedRefs.add(addFixedInputCell(builder, state, index, 18 + column * 214, 122 + row * 24));
        }

        addGlobalExclude(builder, state, 8, 248);

        ButtonWidget clear = button("gui.wildcardpattern.clear");
        clear.setPos(306, 248);
        clear.setSize(68, 18);
        clear.setOnClick((clickData, widget) -> {
            state.wildcardInput.set(WildcardPatternEntry.fromStack(null));
            state.wildcardOutput.set(WildcardPatternEntry.fromStack(null));
            for (int i = 0; i < state.fixedInputs.size(); i++) {
                state.fixedInputs.set(i, null);
            }
            state.globalExclude = "";
            inputRef.clear();
            outputRef.clear();
            for (FixedCellRefs ref : fixedRefs) {
                ref.clear();
            }
            state.refreshActivePage();
        });
        addPageWidget(builder, state, clear, Page.MAIN);

        ButtonWidget dedupe = button("gui.wildcardpattern.dedupe");
        dedupe.setPos(382, 248);
        dedupe.setSize(68, 18);
        dedupe.setOnClick((clickData, widget) -> state.openDedupe());
        addPageWidget(builder, state, dedupe, Page.MAIN);

        ButtonWidget preview = button("gui.wildcardpattern.preview_all");
        preview.setPos(306, 270);
        preview.setSize(68, 18);
        preview.setOnClick((clickData, widget) -> state.openPreview());
        addPageWidget(builder, state, preview, Page.MAIN);

        ButtonWidget save = button("gui.wildcardpattern.save");
        save.setPos(382, 270);
        save.setSize(68, 18);
        save.setOnClick((clickData, widget) -> {
            state.save();
            widget.getWindow().closeWindow();
        });
        addPageWidget(builder, state, save, Page.MAIN);
    }

    private static void addGlobalExclude(ModularWindow.Builder builder, WindowState state, int x, int y) {
        addPanel(builder, state, x, y, 148, 40, Page.MAIN);
        addPageText(builder, state, Page.MAIN, EnumChatFormatting.BLACK + tr("gui.wildcardpattern.global_exclude"), x + 12, y + 10);
        TextWidget summary = new TextWidget("");
        summary.setPos(x + 12, y + 24);
        summary.setScale(0.72f);
        summary.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + trim(state.getExcludeSummary(), 24));
        addPageWidget(builder, state, summary, Page.MAIN);

        ButtonWidget edit = button("gui.wildcardpattern.exclude_short");
        edit.setPos(x + 96, y + 8);
        edit.setSize(42, 18);
        edit.setOnClick((clickData, widget) -> state.openExcludeEditor());
        addPageWidget(builder, state, edit, Page.MAIN);
    }

    private static EntryCellRefs addEntryCell(
        ModularWindow.Builder builder,
        WindowState state,
        Page page,
        EntryRef entryRef,
        int x,
        int y) {
        final boolean[] suppressNextSetter = { false };
        final WildcardEntryDropTextField[] textRef = new WildcardEntryDropTextField[1];
        WildcardEntryDropTextField text = new WildcardEntryDropTextField(stack -> {
            WildcardPatternEntry next = CompositeWildcardRuleEditing.fromDroppedStack(stack);
            entryRef.set(next);
            state.refreshActivePage();
            if (textRef[0] != null) {
                suppressNextSetter[0] = true;
                textRef[0].setText(trim(next.getLabel(), 16));
                textRef[0].markForUpdate();
            }
        });
        textRef[0] = text;
        text.setSynced(false, false);
        text.setSetter(value -> {
            if (suppressNextSetter[0]) {
                suppressNextSetter[0] = false;
                return;
            }
            entryRef.set(CompositeWildcardRuleEditing.updateMatcher(entryRef.get(), value));
            state.refreshActivePage();
        });
        text.setText(entryRef.get().isEmpty() ? "" : trim(entryRef.get().getLabel(), 16));
        text.setTextColor(FIELD_TEXT_COLOR);
        text.setBackground(CompositeWildcardPatternWindow::fieldBackground);
        text.setTextAlignment(Alignment.CenterLeft);
        text.setMaxLength(80);
        text.setPos(x, y);
        text.setSize(ENTRY_TEXT_WIDTH, 16);
        addPageWidget(builder, state, text, page);

        ButtonWidget mode = button(
            () -> buttonBackground(
                entryRef.get().isOreDict(),
                tr(entryRef.get().isOreDict() ? "gui.wildcardpattern.mode_oredict" : "gui.wildcardpattern.mode_name"),
                BUTTON_TEXT_COLOR));
        mode.setPos(x + ENTRY_MODE_X, y);
        mode.setSize(ENTRY_MODE_WIDTH, 16);
        mode.setOnClick((clickData, widget) -> {
            WildcardPatternEntry entry = entryRef.get();
            if (entry.isOreDict()) {
                entry.convertToItem();
            } else {
                entry = CompositeWildcardRuleEditing.toOreDict(entry);
            }
            entryRef.set(entry);
            suppressNextSetter[0] = true;
            text.setText(trim(entry.getLabel(), 16));
            text.markForUpdate();
            state.refreshActivePage();
        });
        addPageWidget(builder, state, mode, page);

        TextFieldWidget amount = new TextFieldWidget();
        amount.setSynced(false, false);
        amount.setSetter(value -> {
            entryRef.get().setAmount(parseAmount(value));
            state.refreshActivePage();
        });
        amount.setText(formatAmount(entryRef.get().getAmountLong()));
        amount.setTextColor(FIELD_TEXT_COLOR);
        amount.setBackground(CompositeWildcardPatternWindow::fieldBackground);
        amount.setTextAlignment(Alignment.Center);
        amount.setMaxLength(8);
        amount.setPos(x + ENTRY_AMOUNT_X, y);
        amount.setSize(ENTRY_AMOUNT_WIDTH, 16);
        addPageWidget(builder, state, amount, page);

        return new EntryCellRefs(text, amount, () -> formatAmount(entryRef.get().getAmountLong()));
    }

    private static FixedCellRefs addFixedInputCell(ModularWindow.Builder builder, WindowState state, int index, int x, int y) {
        addPageText(builder, state, Page.MAIN, EnumChatFormatting.DARK_GRAY + String.valueOf(index + 1), x, y + 5);

        final TextFieldWidget[] amountRef = { null };
        WildcardEntryDropButton drop = new WildcardEntryDropButton(stack -> {
            ItemStack copy = stack == null ? null : stack.copy();
            if (copy != null) {
                copy.stackSize = 1;
            }
            state.fixedInputs.set(index, copy);
            state.refreshActivePage();
            if (amountRef[0] != null) {
                amountRef[0].setText(formatAmount(state.getFixedAmount(index)));
                amountRef[0].markForUpdate();
            }
        });
        drop.setSynced(false, false);
        drop.setBackground(() -> buttonBackground(false, trim(state.getFixedLabel(index), 16), BUTTON_TEXT_COLOR));
        drop.setPlayClickSound(true);
        drop.setPos(x + 14, y);
        drop.setSize(124, 18);
        drop.setOnClick((clickData, widget) -> {
            if (clickData.mouseButton == 1) {
                state.fixedInputs.set(index, null);
                state.refreshActivePage();
                if (amountRef[0] != null) {
                    amountRef[0].setText("1");
                    amountRef[0].markForUpdate();
                }
                drop.markForUpdate();
            }
        });
        addPageWidget(builder, state, drop, Page.MAIN);

        TextFieldWidget amount = new TextFieldWidget();
        amountRef[0] = amount;
        amount.setSynced(false, false);
        amount.setSetter(value -> {
            ItemStack fixed = state.fixedInputs.get(index);
            if (fixed != null) {
                fixed.stackSize = (int) Math.min(Integer.MAX_VALUE, parseAmount(value));
                state.refreshActivePage();
            }
        });
        amount.setText(formatAmount(state.getFixedAmount(index)));
        amount.setTextColor(FIELD_TEXT_COLOR);
        amount.setBackground(CompositeWildcardPatternWindow::fieldBackground);
        amount.setTextAlignment(Alignment.Center);
        amount.setMaxLength(8);
        amount.setPos(x + 162, y);
        amount.setSize(40, 18);
        addPageWidget(builder, state, amount, Page.MAIN);

        ButtonWidget remove = button("X");
        remove.setPos(x + 142, y);
        remove.setSize(16, 18);
        remove.setOnClick((clickData, widget) -> {
            state.fixedInputs.set(index, null);
            amount.setText("1");
            amount.markForUpdate();
            drop.markForUpdate();
            state.refreshActivePage();
        });
        addPageWidget(builder, state, remove, Page.MAIN);

        return new FixedCellRefs(drop, amount, () -> formatAmount(state.getFixedAmount(index)));
    }

    private static void addPreviewPage(ModularWindow.Builder builder, WindowState state) {
        addPanel(builder, state, 8, 28, 436, 262, Page.PREVIEW);

        TextWidget source = new TextWidget("");
        source.setPos(18, 38);
        source.setStringSupplier(() -> EnumChatFormatting.BLACK + tr("gui.composite_wildcardpattern.preview_all"));
        addPageWidget(builder, state, source, Page.PREVIEW);

        addSearchField(
            builder,
            state,
            Page.PREVIEW,
            EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.search"),
            () -> state.previewSearch,
            value -> {
                state.previewSearch = value == null ? "" : value;
                state.previewPageIndex = 0;
                state.refreshActivePage();
            },
            192,
            34,
            240);

        ButtonWidget exclude = button("gui.wildcardpattern.exclude_short");
        exclude.setPos(18, 58);
        exclude.setSize(64, 18);
        exclude.setOnClick((clickData, widget) -> state.excludeSelectedPreviewRow());
        addPageWidget(builder, state, exclude, Page.PREVIEW);

        TextWidget count = new TextWidget("");
        count.setPos(96, 62);
        count.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY
            + StatCollector.translateToLocalFormatted(
                "gui.wildcardpattern.preview",
                Integer.valueOf(state.previewRows.size())));
        addPageWidget(builder, state, count, Page.PREVIEW);

        addSeparator(builder, state, 18, 81, 406, 2, Page.PREVIEW);
        for (int i = 0; i < PREVIEW_LINES; i++) {
            final int lineIndex = i;
            TextWidget line = new TextWidget("");
            line.setPos(18, 88 + i * 14);
            line.setStringSupplier(() -> {
                PreviewRow row = state.getPreviewRow(lineIndex);
                return row == null ? "" : EnumChatFormatting.DARK_GRAY + row.line;
            });
            addPageWidget(builder, state, line, Page.PREVIEW);

            ButtonWidget excludeRow = button("gui.wildcardpattern.exclude_short");
            excludeRow.setPos(388, 86 + i * 14);
            excludeRow.setSize(38, 12);
            excludeRow.setOnClick((clickData, widget) -> state.excludePreviewRow(lineIndex));
            addPageWidget(builder, state, excludeRow, Page.PREVIEW);
        }

        ButtonWidget back = button("gui.wildcardpattern.back");
        back.setPos(18, 264);
        back.setSize(64, 17);
        back.setOnClick((clickData, widget) -> state.previewPage = false);
        addPageWidget(builder, state, back, Page.PREVIEW);

        addPager(builder, state, Page.PREVIEW, 182, 264, () -> state.previewPageIndex, value -> state.previewPageIndex = value,
            state::getPreviewPageCount);

        ButtonWidget open = button("gui.wildcardpattern.preview_all");
        open.setPos(366, 264);
        open.setSize(58, 17);
        open.setOnClick((clickData, widget) -> state.rebuildPreview());
        addPageWidget(builder, state, open, Page.PREVIEW);
    }

    private static void addExcludePage(ModularWindow.Builder builder, WindowState state) {
        addPanel(builder, state, 8, 28, 436, 262, Page.EXCLUDE);
        addSeparator(builder, state, 18, 55, 406, 2, Page.EXCLUDE);

        TextWidget title = new TextWidget("");
        title.setPos(18, 38);
        title.setStringSupplier(() -> EnumChatFormatting.BLACK + tr("gui.wildcardpattern.global_exclude"));
        addPageWidget(builder, state, title, Page.EXCLUDE);

        TextWidget tip1 = new TextWidget("");
        tip1.setPos(18, 68);
        tip1.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.exclude_tip1"));
        addPageWidget(builder, state, tip1, Page.EXCLUDE);

        TextWidget tip2 = new TextWidget("");
        tip2.setPos(18, 81);
        tip2.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.exclude_tip2"));
        addPageWidget(builder, state, tip2, Page.EXCLUDE);

        TextFieldWidget field = new WildcardFilterDropTextField(value -> state.excludeDraft = value == null ? "" : value)
            .setOnEnter(state::addCurrentExcludeDraft);
        field.setSynced(false, false);
        field.setGetter(() -> state.excludeDraft);
        field.setSetter(value -> state.excludeDraft = value == null ? "" : value);
        field.setText(state.excludeDraft);
        field.setTextColor(FIELD_TEXT_COLOR);
        field.setBackground(CompositeWildcardPatternWindow::fieldBackground);
        field.setTextAlignment(Alignment.CenterLeft);
        field.setMaxLength(256);
        field.setPos(18, 104);
        field.setSize(330, 18);
        addPageWidget(builder, state, field, Page.EXCLUDE);

        ButtonWidget add = button("+");
        add.setPos(356, 104);
        add.setSize(68, 18);
        add.setOnClick((clickData, widget) -> state.addCurrentExcludeDraft());
        addPageWidget(builder, state, add, Page.EXCLUDE);

        TextWidget current = new TextWidget("");
        current.setPos(18, 132);
        current.setScale(0.78f);
        current.setStringSupplier(() -> EnumChatFormatting.BLACK + tr("gui.wildcardpattern.exclude_current"));
        addPageWidget(builder, state, current, Page.EXCLUDE);

        for (int i = 0; i < EXCLUDE_LINES; i++) {
            final int lineIndex = i;
            TextWidget line = new TextWidget("");
            line.setPos(18, 144 + i * 14);
            line.setScale(0.72f);
            line.setStringSupplier(() -> {
                String token = state.getCurrentExcludeToken(lineIndex);
                return token.isEmpty() ? "" : EnumChatFormatting.DARK_GRAY + "- " + trimMiddle(token, 44);
            });
            addPageWidget(builder, state, line, Page.EXCLUDE);

            ButtonWidget delete = button("X");
            delete.setPos(404, 142 + i * 14);
            delete.setSize(20, 12);
            delete.setOnClick((clickData, widget) -> state.removeCurrentExcludeToken(lineIndex));
            addPageWidget(builder, state, delete, Page.EXCLUDE);
        }

        ButtonWidget prev = button("<");
        prev.setPos(18, 264);
        prev.setSize(34, 17);
        prev.setOnClick((clickData, widget) -> {
            if (state.excludePageIndex > 0) {
                state.excludePageIndex--;
            }
        });
        addPageWidget(builder, state, prev, Page.EXCLUDE);

        TextWidget page = new TextWidget("");
        page.setPos(62, 268);
        page.setStringSupplier(() -> EnumChatFormatting.BLACK
            + StatCollector.translateToLocalFormatted(
                "gui.wildcardpattern.page",
                Integer.valueOf(state.excludePageIndex + 1),
                Integer.valueOf(Math.max(1, state.getExcludePageCount()))));
        addPageWidget(builder, state, page, Page.EXCLUDE);

        ButtonWidget next = button(">");
        next.setPos(138, 264);
        next.setSize(34, 17);
        next.setOnClick((clickData, widget) -> {
            if (state.excludePageIndex + 1 < state.getExcludePageCount()) {
                state.excludePageIndex++;
            }
        });
        addPageWidget(builder, state, next, Page.EXCLUDE);

        ButtonWidget clear = button("gui.wildcardpattern.clear");
        clear.setPos(286, 264);
        clear.setSize(64, 17);
        clear.setOnClick((clickData, widget) -> {
            state.excludeDraft = "";
            state.setCurrentExcludeValue("");
        });
        addPageWidget(builder, state, clear, Page.EXCLUDE);

        ButtonWidget back = button("gui.wildcardpattern.back");
        back.setPos(360, 264);
        back.setSize(64, 17);
        back.setOnClick((clickData, widget) -> state.closeExcludeEditor());
        addPageWidget(builder, state, back, Page.EXCLUDE);
    }

    private static void addDedupePage(ModularWindow.Builder builder, WindowState state) {
        addPanel(builder, state, 8, 28, 436, 262, Page.DEDUPE);
        addSeparator(builder, state, 18, 55, 406, 2, Page.DEDUPE);

        TextWidget title = new TextWidget("");
        title.setPos(18, 38);
        title.setStringSupplier(() -> EnumChatFormatting.BLACK + tr("gui.composite_wildcardpattern.dedupe_page"));
        addPageWidget(builder, state, title, Page.DEDUPE);

        addSearchField(
            builder,
            state,
            Page.DEDUPE,
            EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.search"),
            () -> state.dedupeSearch,
            value -> {
                state.dedupeSearch = value == null ? "" : value;
                state.dedupePageIndex = 0;
                state.rebuildDedupe();
            },
            18,
            76,
            250);

        for (int i = 0; i < DEDUPE_LINES; i++) {
            int rowY = 98 + i * 39;
            final int lineIndex = i;
            addDedupeCard(builder, state, 14, rowY - 2, 418, 33);

            TextWidget inputOre = new TextWidget("");
            inputOre.setPos(42, rowY + 2);
            inputOre.setScale(0.74f);
            inputOre.setStringSupplier(() -> {
                DedupeRow row = state.getDedupeRow(lineIndex);
                return row == null ? "" : row.getTopColor() + trimMiddle(row.getInputLine(), 26);
            });
            addPageWidget(builder, state, inputOre, Page.DEDUPE);

            TextWidget topArrow = new TextWidget(EnumChatFormatting.BLACK + "->");
            topArrow.setPos(174, rowY + 2);
            addPageWidget(builder, state, topArrow, Page.DEDUPE);

            TextWidget outputOre = new TextWidget("");
            outputOre.setPos(194, rowY + 2);
            outputOre.setScale(0.74f);
            outputOre.setStringSupplier(() -> {
                DedupeRow row = state.getDedupeRow(lineIndex);
                return row == null ? "" : row.getTopColor() + trimMiddle(row.getOutputLine(), 26);
            });
            addPageWidget(builder, state, outputOre, Page.DEDUPE);

            TextWidget inputChoice = new TextWidget("");
            inputChoice.setPos(42, rowY + 17);
            inputChoice.setScale(0.7f);
            inputChoice.setStringSupplier(() -> {
                DedupeRow row = state.getDedupeRow(lineIndex);
                return row == null ? "" : row.getDetailColor() + trimMiddle(row.getInputChoiceLine(state), 28);
            });
            addPageWidget(builder, state, inputChoice, Page.DEDUPE);

            TextWidget bottomArrow = new TextWidget(EnumChatFormatting.DARK_GRAY + "->");
            bottomArrow.setPos(174, rowY + 17);
            addPageWidget(builder, state, bottomArrow, Page.DEDUPE);

            TextWidget outputChoice = new TextWidget("");
            outputChoice.setPos(194, rowY + 17);
            outputChoice.setScale(0.7f);
            outputChoice.setStringSupplier(() -> {
                DedupeRow row = state.getDedupeRow(lineIndex);
                return row == null ? "" : row.getDetailColor() + trimMiddle(row.getOutputChoiceLine(state), 28);
            });
            addPageWidget(builder, state, outputChoice, Page.DEDUPE);

            ButtonWidget cycleInput = button("gui.wildcardpattern.dedupe_input");
            cycleInput.setPos(322, rowY + 3);
            cycleInput.setSize(50, 18);
            cycleInput.setOnClick((clickData, widget) -> {
                DedupeRow row = state.getDedupeRow(lineIndex);
                if (row != null) {
                    state.cycleDedupeChoice(row.inputOreName);
                }
            });
            cycleInput.setEnabled(widget -> state.currentPage() == Page.DEDUPE
                && state.getDedupeRow(lineIndex) != null
                && state.getDedupeRow(lineIndex).inputDuplicate);
            addPageWidget(builder, state, cycleInput, Page.DEDUPE);

            ButtonWidget cycleOutput = button("gui.wildcardpattern.dedupe_output");
            cycleOutput.setPos(378, rowY + 3);
            cycleOutput.setSize(50, 18);
            cycleOutput.setOnClick((clickData, widget) -> {
                DedupeRow row = state.getDedupeRow(lineIndex);
                if (row != null) {
                    state.cycleDedupeChoice(row.outputOreName);
                }
            });
            cycleOutput.setEnabled(widget -> state.currentPage() == Page.DEDUPE
                && state.getDedupeRow(lineIndex) != null
                && state.getDedupeRow(lineIndex).outputDuplicate);
            addPageWidget(builder, state, cycleOutput, Page.DEDUPE);
        }

        ButtonWidget back = button("gui.wildcardpattern.back");
        back.setPos(18, 264);
        back.setSize(64, 17);
        back.setOnClick((clickData, widget) -> state.dedupePage = false);
        addPageWidget(builder, state, back, Page.DEDUPE);

        addPager(builder, state, Page.DEDUPE, 182, 264, () -> state.dedupePageIndex, value -> state.dedupePageIndex = value,
            state::getDedupePageCount);
    }

    private static void addSearchField(
        ModularWindow.Builder builder,
        WindowState state,
        Page page,
        String label,
        java.util.function.Supplier<String> getter,
        java.util.function.Consumer<String> setter,
        int x,
        int y,
        int width) {
        TextWidget labelText = new TextWidget(label);
        labelText.setPos(x, y + 4);
        addPageWidget(builder, state, labelText, page);

        TextFieldWidget field = new WildcardFilterDropTextField(setter);
        field.setSynced(false, false);
        field.setText(getter.get());
        field.setSetter(setter);
        field.setTextColor(FIELD_TEXT_COLOR);
        field.setBackground(CompositeWildcardPatternWindow::fieldBackground);
        field.setTextAlignment(Alignment.CenterLeft);
        field.setMaxLength(256);
        field.setPos(x + 44, y);
        field.setSize(width - 44, 18);
        addPageWidget(builder, state, field, page);
    }

    private static void addPager(
        ModularWindow.Builder builder,
        WindowState state,
        Page page,
        int x,
        int y,
        java.util.function.Supplier<Integer> pageIndex,
        java.util.function.Consumer<Integer> pageSetter,
        java.util.function.Supplier<Integer> pageCount) {
        ButtonWidget prev = button("<");
        prev.setPos(x, y);
        prev.setSize(34, 17);
        prev.setOnClick((clickData, widget) -> {
            if (pageIndex.get() > 0) {
                pageSetter.accept(pageIndex.get() - 1);
            }
        });
        addPageWidget(builder, state, prev, page);

        TextWidget current = new TextWidget("");
        current.setPos(x + 46, y + 4);
        current.setStringSupplier(() -> EnumChatFormatting.BLACK
            + StatCollector.translateToLocalFormatted(
                "gui.wildcardpattern.page",
                Integer.valueOf(pageIndex.get() + 1),
                Integer.valueOf(Math.max(1, pageCount.get()))));
        addPageWidget(builder, state, current, page);

        ButtonWidget next = button(">");
        next.setPos(x + 122, y);
        next.setSize(34, 17);
        next.setOnClick((clickData, widget) -> {
            if (pageIndex.get() + 1 < pageCount.get()) {
                pageSetter.accept(pageIndex.get() + 1);
            }
        });
        addPageWidget(builder, state, next, page);
    }

    private static ButtonWidget button(String text) {
        return button(() -> buttonBackground(false, resolveButtonText(text), BUTTON_TEXT_COLOR));
    }

    private static ButtonWidget button(Supplier<IDrawable[]> backgroundSupplier) {
        ButtonWidget button = new AnimatedButtonWidget();
        button.setSynced(false, false);
        button.setBackground(backgroundSupplier::get);
        button.setPlayClickSound(true);
        return button;
    }

    private static IDrawable[] buttonBackground(boolean active, String text, int textColor) {
        if (active) {
            return new IDrawable[] { ModularUITextures.VANILLA_BUTTON_NORMAL,
                new Rectangle().setColor(0x223E6FB0),
                new Text(text).color(textColor).alignment(Alignment.Center) };
        }
        return new IDrawable[] { ModularUITextures.VANILLA_BUTTON_NORMAL,
            new Text(text).color(textColor).alignment(Alignment.Center) };
    }

    private static IDrawable[] fieldBackground() {
        return new IDrawable[] { CompositeWildcardPatternWindow::drawInsetField };
    }

    private static String resolveButtonText(String text) {
        return text != null && text.contains(".") ? tr(text) : text;
    }

    private static void addPanel(ModularWindow.Builder builder, WindowState state, int x, int y, int width, int height, Page page) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 2, y + 2);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(PANEL_SHADOW_COLOR));
        addPageWidget(builder, state, shadow, page);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(CompositeWildcardPatternWindow::drawInsetPanel);
        addPageWidget(builder, state, border, page);

        DrawableWidget fill = new DrawableWidget();
        fill.setPos(x + 3, y + 3);
        fill.setSize(width - 6, height - 6);
        fill.setDrawable(new Rectangle().setColor(PANEL_COLOR));
        addPageWidget(builder, state, fill, page);
    }

    private static void addDedupeCard(ModularWindow.Builder builder, WindowState state, int x, int y, int width, int height) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 1, y + 1);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(CARD_SHADOW_COLOR));
        addPageWidget(builder, state, shadow, Page.DEDUPE);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(CompositeWildcardPatternWindow::drawDedupeCard);
        addPageWidget(builder, state, border, Page.DEDUPE);
    }

    private static void addSeparator(ModularWindow.Builder builder, WindowState state, int x, int y, int width, int height, Page page) {
        DrawableWidget dark = new DrawableWidget();
        dark.setPos(x, y);
        dark.setSize(width, Math.max(1, height / 2));
        dark.setDrawable(new Rectangle().setColor(PANEL_LINE_DARK));
        addPageWidget(builder, state, dark, page);

        DrawableWidget light = new DrawableWidget();
        light.setPos(x, y + Math.max(1, height / 2));
        light.setSize(width, Math.max(1, height - Math.max(1, height / 2)));
        light.setDrawable(new Rectangle().setColor(PANEL_LINE_LIGHT));
        addPageWidget(builder, state, light, page);
    }

    private static void drawInsetField(float x, float y, float width, float height, float partialTicks) {
        drawRect(x, y, width, height, FIELD_LINE_DARK, partialTicks);
        drawRect(x + 2, y + 2, Math.max(0, width - 4), Math.max(0, height - 4), FIELD_COLOR, partialTicks);
        drawRect(x, y + height - 2, width, 2, FIELD_LINE_LIGHT, partialTicks);
        drawRect(x + width - 2, y, 2, height, FIELD_LINE_LIGHT, partialTicks);
        drawRect(x + 1, y + height - 3, Math.max(0, width - 3), 1, 0xFFBFC0C0, partialTicks);
    }

    private static void drawInsetPanel(float x, float y, float width, float height, float partialTicks) {
        drawRect(x, y, width, height, PANEL_LINE_LIGHT, partialTicks);
        drawRect(x, y, width, 2, PANEL_LINE_DARK, partialTicks);
        drawRect(x, y, 2, height, PANEL_LINE_DARK, partialTicks);
        drawRect(x + 2, y + 2, Math.max(0, width - 4), Math.max(0, height - 4), 0xFF9A9A9A, partialTicks);
        drawRect(x + 3, y + 3, Math.max(0, width - 6), Math.max(0, height - 6), PANEL_COLOR, partialTicks);
    }

    private static void drawDedupeCard(float x, float y, float width, float height, float partialTicks) {
        drawRect(x, y, width, height, PANEL_LINE_LIGHT, partialTicks);
        drawRect(x, y, width, 1, 0xFF8B8B8B, partialTicks);
        drawRect(x, y, 1, height, 0xFF8B8B8B, partialTicks);
        drawRect(x + 1, y + 1, Math.max(0, width - 2), Math.max(0, height - 2), CARD_FILL_COLOR, partialTicks);
    }

    private static void drawRect(float x, float y, float width, float height, int color, float partialTicks) {
        if (width <= 0 || height <= 0) {
            return;
        }
        new Rectangle().setColor(color).draw(x, y, width, height, partialTicks);
    }

    private static void addPageText(ModularWindow.Builder builder, WindowState state, Page page, String text, int x, int y) {
        TextWidget widget = new TextWidget(text);
        widget.setPos(x, y);
        addPageWidget(builder, state, widget, page);
    }

    private static void addPageWidget(ModularWindow.Builder builder, WindowState state, Widget widget, Page page) {
        widget.setEnabled(w -> state.currentPage() == page);
        builder.widget(widget);
    }

    private static String summarize(List<ItemStack> inputStacks, ItemStack outputStack) {
        StringBuilder builder = new StringBuilder();
        if (inputStacks == null || inputStacks.isEmpty()) {
            builder.append("-");
        } else {
            for (ItemStack input : inputStacks) {
                if (builder.length() > 0) {
                    builder.append(" + ");
                }
                builder.append(formatPreviewStack(input));
            }
        }
        builder.append(" -> ").append(formatPreviewStack(outputStack));
        return trim(builder.toString(), 78);
    }

    private static String formatPreviewStack(ItemStack stack) {
        if (stack == null) {
            return "-";
        }
        return formatStackSize(Math.max(1, stack.stackSize)) + "x" + stack.getDisplayName();
    }

    private static String formatPreviewLine(String line, int duplicateOutputCount) {
        if (duplicateOutputCount <= 1) {
            return line == null ? "" : line;
        }
        return trim(
            tr("gui.wildcardpattern.duplicate_output") + " x" + duplicateOutputCount + " " + (line == null ? "" : line),
            82);
    }

    private static Map<String, Integer> countOutputIdentities(List<PreviewRow> rows) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        if (rows == null) {
            return counts;
        }
        for (PreviewRow row : rows) {
            if (row == null || row.outputKey.isEmpty()) {
                continue;
            }
            Integer count = counts.get(row.outputKey);
            counts.put(row.outputKey, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }
        return counts;
    }

    private static int getDuplicateCount(Map<String, Integer> counts, String outputKey) {
        if (counts == null || outputKey == null || outputKey.isEmpty()) {
            return 0;
        }
        Integer count = counts.get(outputKey);
        return count == null ? 0 : count.intValue();
    }

    private static void sortDuplicateOutputsFirst(List<PreviewRow> rows) {
        rows.sort((left, right) -> Boolean.compare(right.hasDuplicateOutput(), left.hasDuplicateOutput()));
    }

    private static void sortDuplicateDedupeRowsFirst(List<DedupeRow> rows) {
        rows.sort((left, right) -> Boolean.compare(right.hasDuplicateOutput(), left.hasDuplicateOutput()));
    }

    private static String getOutputIdentity(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        String itemName = String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
        if (itemName == null || itemName.isEmpty() || "null".equals(itemName)) {
            itemName = stack.getItem().getClass().getName();
        }
        NBTTagCompound tag = stack.getTagCompound();
        return itemName + "@" + stack.getItemDamage() + "x" + Math.max(1, stack.stackSize)
            + "#" + (tag == null ? "" : Integer.toHexString(tag.hashCode()));
    }

    private static String getOutputExcludeToken(ItemStack outputStack, String materialName) {
        String outputOre = getAssociatedOreNameStatic(outputStack);
        if (outputOre != null && !outputOre.isEmpty()) {
            return outputOre;
        }
        return materialName == null ? "" : materialName;
    }

    private static String getAssociatedOreNameStatic(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemData association = GTOreDictUnificator.getAssociation(stack);
        if (association != null && association.hasValidPrefixMaterialData()) {
            return getPrefixName(association.mPrefix) + association.mMaterial.mMaterial.mName;
        }
        int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds == null || oreIds.length == 0) {
            return null;
        }
        for (int oreId : oreIds) {
            String oreName = OreDictionary.getOreName(oreId);
            if (oreName == null || oreName.isEmpty()) continue;
            for (gregtech.api.enums.OrePrefixes prefix : GTCompat.orePrefixes()) {
                String prefixName = getPrefixName(prefix);
                if (!prefixName.isEmpty() && oreName.regionMatches(true, 0, prefixName, 0, prefixName.length())) {
                    return oreName;
                }
            }
        }
        return null;
    }

    private static String formatStackSize(long amount) {
        if (amount >= 100_000_000L) {
            return formatCompact(amount, 1_000_000_000L, "g");
        }
        if (amount >= 1_000_000L) {
            return formatCompact(amount, 1_000_000L, "m");
        }
        if (amount >= 1_000L) {
            return formatCompact(amount, 1_000L, "k");
        }
        return String.valueOf(Math.max(0L, amount));
    }

    private static String trim(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String trimMiddle(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max || max <= 3) {
            return trim(text, max);
        }
        int head = (max - 3) / 2;
        int tail = max - 3 - head;
        return text.substring(0, head) + "..." + text.substring(text.length() - tail);
    }

    private static String formatAmount(long amount) {
        if (amount >= 100_000_000L) {
            return formatCompact(amount, 1_000_000_000L, "g");
        }
        if (amount >= 1_000_000L) {
            return formatCompact(amount, 1_000_000L, "m");
        }
        if (amount >= 1_000L) {
            return formatCompact(amount, 1_000L, "k");
        }
        return String.valueOf(Math.max(1L, amount));
    }

    private static String formatCompact(long amount, long unit, String suffix) {
        if (amount % unit == 0) {
            return (amount / unit) + suffix;
        }
        long scaled = Math.round((double) amount * 10D / unit);
        if (scaled % 10 == 0) {
            return (scaled / 10) + suffix;
        }
        return (scaled / 10) + "." + (scaled % 10) + suffix;
    }

    private static long parseAmount(String value) {
        String token = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (token.isEmpty()) {
            return 1L;
        }
        long multiplier = 1L;
        if (token.endsWith("k")) {
            multiplier = 1_000L;
            token = token.substring(0, token.length() - 1).trim();
        } else if (token.endsWith("m")) {
            multiplier = 1_000_000L;
            token = token.substring(0, token.length() - 1).trim();
        } else if (token.endsWith("g")) {
            multiplier = 1_000_000_000L;
            token = token.substring(0, token.length() - 1).trim();
        }
        try {
            double parsed = Double.parseDouble(token);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return 1L;
            }
            long result = Math.round(parsed * multiplier);
            return Math.max(1L, Math.min(WildcardPatternEntry.MAX_AMOUNT, result));
        } catch (NumberFormatException ignored) {
            return 1L;
        }
    }

    private static String getPrefixName(gregtech.api.enums.OrePrefixes prefix) {
        if (prefix == null) {
            return "";
        }
        try {
            return (String) prefix.getClass().getMethod("getName").invoke(prefix);
        } catch (Exception ignored) {}
        try {
            return (String) prefix.getClass().getMethod("name").invoke(prefix);
        } catch (Exception ignored) {}
        return prefix.toString();
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    private enum Page {
        MAIN,
        PREVIEW,
        EXCLUDE,
        DEDUPE
    }

    private interface EntryRef {

        WildcardPatternEntry get();

        void set(WildcardPatternEntry entry);
    }

    private static final class EntryCellRefs {
        private final TextFieldWidget textField;
        private final TextFieldWidget amountField;
        private final java.util.function.Supplier<String> amountSupplier;

        private EntryCellRefs(
            TextFieldWidget textField,
            TextFieldWidget amountField,
            java.util.function.Supplier<String> amountSupplier) {
            this.textField = textField;
            this.amountField = amountField;
            this.amountSupplier = amountSupplier;
        }

        private void clear() {
            this.textField.setText("");
            this.amountField.setText("1");
            this.textField.markForUpdate();
            this.amountField.markForUpdate();
        }

        private void updateAmount() {
            this.amountField.setText(this.amountSupplier.get());
            this.amountField.markForUpdate();
        }
    }

    private static final class FixedCellRefs {
        private final ButtonWidget button;
        private final TextFieldWidget amountField;
        private final java.util.function.Supplier<String> amountSupplier;

        private FixedCellRefs(ButtonWidget button, TextFieldWidget amountField, java.util.function.Supplier<String> amountSupplier) {
            this.button = button;
            this.amountField = amountField;
            this.amountSupplier = amountSupplier;
        }

        private void clear() {
            this.amountField.setText("1");
            this.amountField.markForUpdate();
            this.button.markForUpdate();
        }

        private void update() {
            this.amountField.setText(this.amountSupplier.get());
            this.amountField.markForUpdate();
            this.button.markForUpdate();
        }
    }

    private static final class AnimatedButtonWidget extends ButtonWidget {

        @Override
        public IDrawable[] getBackground() {
            IDrawable[] base = super.getBackground();
            if (!isHovering()) {
                return base;
            }
            IDrawable highlight = new Rectangle().setColor(0x332A62A5);
            if (base == null || base.length == 0) {
                return new IDrawable[] { highlight };
            }
            IDrawable[] result = new IDrawable[base.length + 1];
            System.arraycopy(base, 0, result, 0, base.length);
            result[base.length] = highlight;
            return result;
        }
    }

    private static final class PreviewRow {
        private final String materialName;
        private final String excludeToken;
        private final String outputExcludeToken;
        private final String line;
        private final String outputKey;
        private final String outputLabel;
        private final int duplicateOutputCount;

        private PreviewRow(
            String materialName,
            String excludeToken,
            String outputExcludeToken,
            String line,
            String outputKey,
            String outputLabel,
            int duplicateOutputCount) {
            this.materialName = materialName == null ? "" : materialName;
            this.excludeToken = excludeToken == null ? "" : excludeToken;
            this.outputExcludeToken = outputExcludeToken == null ? "" : outputExcludeToken;
            this.line = line == null ? "" : line;
            this.outputKey = outputKey == null ? "" : outputKey;
            this.outputLabel = outputLabel == null ? "" : outputLabel;
            this.duplicateOutputCount = duplicateOutputCount;
        }

        private boolean hasDuplicateOutput() {
            return this.duplicateOutputCount > 1;
        }
    }

    private static final class DedupeRow {
        private final String materialName;
        private final String inputOreName;
        private final String outputOreName;
        private final boolean inputDuplicate;
        private final boolean outputDuplicate;
        private final int duplicateOutputCount;

        private DedupeRow(
            String materialName,
            String inputOreName,
            String outputOreName,
            boolean inputDuplicate,
            boolean outputDuplicate) {
            this(materialName, inputOreName, outputOreName, inputDuplicate, outputDuplicate, 0);
        }

        private DedupeRow(
            String materialName,
            String inputOreName,
            String outputOreName,
            boolean inputDuplicate,
            boolean outputDuplicate,
            int duplicateOutputCount) {
            this.materialName = materialName == null ? "" : materialName;
            this.inputOreName = inputOreName;
            this.outputOreName = outputOreName;
            this.inputDuplicate = inputDuplicate;
            this.outputDuplicate = outputDuplicate;
            this.duplicateOutputCount = duplicateOutputCount;
        }

        private boolean matches(WindowState state, String filter) {
            String search = filter == null ? "" : filter.trim();
            return search.isEmpty()
                || NechSearchCompat.matches(getLine(), search)
                || NechSearchCompat.matches(getChoiceLine(state), search)
                || NechSearchCompat.matches(getDuplicateLine(), search)
                || NechSearchCompat.matches(this.materialName, search)
                || NechSearchCompat.matches(this.inputOreName, search)
                || NechSearchCompat.matches(this.outputOreName, search);
        }

        private String getLine() {
            return label(this.inputOreName) + " -> " + label(this.outputOreName);
        }

        private String getInputLine() {
            return label(this.inputOreName);
        }

        private String getOutputLine() {
            return label(this.outputOreName);
        }

        private String getChoiceLine(WindowState state) {
            if (state == null) {
                return "";
            }
            return label(state.getSelectedDedupeLabel(this.inputOreName))
                + " -> "
                + label(state.getSelectedDedupeLabel(this.outputOreName));
        }

        private String getInputChoiceLine(WindowState state) {
            return state.getSelectedDedupeLabel(this.inputOreName);
        }

        private String getOutputChoiceLine(WindowState state) {
            return state.getSelectedDedupeLabel(this.outputOreName);
        }

        private boolean hasDuplicateOutput() {
            return this.duplicateOutputCount > 1;
        }

        private EnumChatFormatting getTopColor() {
            return hasDuplicateOutput() ? EnumChatFormatting.RED : EnumChatFormatting.BLACK;
        }

        private EnumChatFormatting getDetailColor() {
            return hasDuplicateOutput() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY;
        }

        private String getDuplicateLine() {
            if (!hasDuplicateOutput()) {
                return "";
            }
            return tr("gui.wildcardpattern.duplicate_output")
                + " x"
                + Math.max(2, this.duplicateOutputCount)
                + " "
                + label(this.outputOreName);
        }

        private static String label(String value) {
            return value == null || value.isEmpty() ? "-" : value;
        }
    }

    private static final class WindowState {
        private final EntityPlayer player;
        private final int slot;
        private final EntryRef wildcardInput;
        private final EntryRef wildcardOutput;
        private final List<ItemStack> fixedInputs;
        private final List<PreviewRow> previewRows = new ArrayList<>();
        private final List<DedupeRow> dedupeRows = new ArrayList<>();
        private final java.util.Map<String, ItemStack> preferredOreStacks = new java.util.LinkedHashMap<>();

        private WildcardPatternEntry inputEntry;
        private WildcardPatternEntry outputEntry;
        private String globalExclude = "";
        private String previewSearch = "";
        private String dedupeSearch = "";
        private String excludeDraft = "";
        private boolean previewPage;
        private boolean dedupePage;
        private boolean excludePage;
        private boolean excludeReturnPreview;
        private int previewPageIndex;
        private int dedupePageIndex;
        private int excludePageIndex;

        private WindowState(EntityPlayer player, int slot) {
            this.player = player;
            this.slot = slot;
            this.wildcardInput = new EntryRef() {

                @Override
                public WildcardPatternEntry get() {
                    return WindowState.this.inputEntry;
                }

                @Override
                public void set(WildcardPatternEntry entry) {
                    WindowState.this.inputEntry = entry == null ? WildcardPatternEntry.fromStack(null) : entry;
                }
            };
            this.wildcardOutput = new EntryRef() {

                @Override
                public WildcardPatternEntry get() {
                    return WindowState.this.outputEntry;
                }

                @Override
                public void set(WildcardPatternEntry entry) {
                    WindowState.this.outputEntry = entry == null ? WildcardPatternEntry.fromStack(null) : entry;
                }
            };

            ItemStack stack = getHeldStack();
            if (stack != null) {
                CompositeWildcardPatternGenerator.markAsCompositeWildcard(stack);
                this.inputEntry = CompositeWildcardPatternState.getWildcardInput(stack);
                this.outputEntry = CompositeWildcardPatternState.getWildcardOutput(stack);
                this.fixedInputs = new ArrayList<>(CompositeWildcardPatternState.getFixedInputs(stack));
                this.globalExclude = WildcardPatternConfig.getGlobalExcludeMaterials(stack);
                for (String oreName : WildcardPatternConfig.getPreferredOreNames(stack)) {
                    ItemStack preferred = WildcardPatternConfig.getPreferredOreStack(stack, oreName);
                    if (preferred != null) {
                        this.preferredOreStacks.put(oreName, preferred);
                    }
                }
            } else {
                this.inputEntry = WildcardPatternEntry.fromStack(null);
                this.outputEntry = WildcardPatternEntry.fromStack(null);
                this.fixedInputs = new ArrayList<>();
            }
            while (this.fixedInputs.size() < FIXED_INPUTS) {
                this.fixedInputs.add(null);
            }
        }

        private Page currentPage() {
            if (this.dedupePage) {
                return Page.DEDUPE;
            }
            if (this.excludePage) {
                return Page.EXCLUDE;
            }
            return this.previewPage ? Page.PREVIEW : Page.MAIN;
        }

        private void openPreview() {
            this.dedupePage = false;
            this.excludePage = false;
            this.previewPage = true;
            this.previewPageIndex = 0;
            rebuildPreview();
        }

        private void openExcludeEditor() {
            this.excludeReturnPreview = this.previewPage;
            this.previewPage = false;
            this.dedupePage = false;
            this.excludeDraft = "";
            this.excludePageIndex = 0;
            this.excludePage = true;
        }

        private void closeExcludeEditor() {
            this.excludePage = false;
            if (this.excludeReturnPreview) {
                this.previewPage = true;
                rebuildPreview();
            }
            this.excludeReturnPreview = false;
        }

        private void openDedupe() {
            this.previewPage = false;
            this.excludePage = false;
            this.dedupePage = true;
            this.dedupePageIndex = 0;
            rebuildDedupe();
        }

        private void setCurrentExcludeValue(String value) {
            this.globalExclude = value == null ? "" : value;
            if (this.globalExclude.trim().isEmpty()) {
                this.excludePageIndex = 0;
            }
            refreshActivePage();
        }

        private String getExcludeSummary() {
            return this.globalExclude == null || this.globalExclude.trim().isEmpty()
                ? tr("gui.wildcardpattern.exclude_empty")
                : this.globalExclude;
        }

        private List<String> getCurrentExcludeTokens() {
            List<String> tokens = new ArrayList<>();
            if (this.globalExclude == null || this.globalExclude.trim().isEmpty()) {
                return tokens;
            }
            for (String token : this.globalExclude.split("[,;\\s]+")) {
                String trimmed = token == null ? "" : token.trim();
                if (!trimmed.isEmpty()) {
                    tokens.add(trimmed);
                }
            }
            return tokens;
        }

        private String getCurrentExcludeToken(int lineIndex) {
            int absolute = this.excludePageIndex * EXCLUDE_LINES + lineIndex;
            List<String> tokens = getCurrentExcludeTokens();
            return absolute >= 0 && absolute < tokens.size() ? tokens.get(absolute) : "";
        }

        private void addCurrentExcludeDraft() {
            addCurrentExcludeToken(this.excludeDraft);
            this.excludeDraft = "";
        }

        private void addCurrentExcludeToken(String value) {
            String token = value == null ? "" : value.trim();
            if (token.isEmpty()) {
                return;
            }
            List<String> tokens = getCurrentExcludeTokens();
            for (String existing : tokens) {
                if (existing.equalsIgnoreCase(token)) {
                    return;
                }
            }
            tokens.add(token);
            setCurrentExcludeValue(String.join(" ", tokens));
            this.excludePageIndex = Math.max(0, getExcludePageCount() - 1);
        }

        private void removeCurrentExcludeToken(int lineIndex) {
            int absolute = this.excludePageIndex * EXCLUDE_LINES + lineIndex;
            List<String> tokens = getCurrentExcludeTokens();
            if (absolute < 0 || absolute >= tokens.size()) {
                return;
            }
            tokens.remove(absolute);
            setCurrentExcludeValue(String.join(" ", tokens));
            if (this.excludePageIndex >= getExcludePageCount()) {
                this.excludePageIndex = Math.max(0, getExcludePageCount() - 1);
            }
        }

        private void multiplyAll() {
            scaleAll(true);
        }

        private void divideAll() {
            scaleAll(false);
        }

        private void scaleAll(boolean multiplying) {
            scaleEntry(this.inputEntry, multiplying);
            scaleEntry(this.outputEntry, multiplying);
            for (ItemStack fixed : this.fixedInputs) {
                scaleFixed(fixed, multiplying);
            }
            refreshActivePage();
        }

        private static void scaleEntry(WildcardPatternEntry entry, boolean multiplying) {
            if (entry == null || entry.isEmpty()) {
                return;
            }
            if (multiplying) {
                entry.multiplyAmount(2);
            } else {
                entry.divideAmount(2);
            }
        }

        private static void scaleFixed(ItemStack fixed, boolean multiplying) {
            if (fixed == null || fixed.getItem() == null) {
                return;
            }
            if (multiplying) {
                fixed.stackSize = fixed.stackSize > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : Math.max(1, fixed.stackSize * 2);
            } else {
                fixed.stackSize = Math.max(1, fixed.stackSize / 2);
            }
        }

        private void refreshActivePage() {
            if (this.previewPage) {
                rebuildPreview();
            } else if (this.dedupePage) {
                rebuildDedupe();
            }
        }

        private void rebuildPreview() {
            this.previewRows.clear();
            ItemStack preview = buildStack();
            if (preview == null) {
                return;
            }
            List<PreviewRow> rawRows = new ArrayList<>();
            String filter = this.previewSearch == null ? "" : this.previewSearch.trim();
            for (CompositeWildcardPatternGenerator.GeneratedPattern pattern : CompositeWildcardPatternGenerator.generatePreviewPatterns(preview)) {
                String line = summarize(pattern.inputStacks, pattern.outputStack);
                String excludeToken = getPreviewExcludeToken(pattern.inputStack, pattern.outputStack, pattern.materialName);
                rawRows.add(
                    new PreviewRow(
                        pattern.materialName,
                        excludeToken,
                        getOutputExcludeToken(pattern.outputStack, pattern.materialName),
                        line,
                        getOutputIdentity(pattern.outputStack),
                        formatPreviewStack(pattern.outputStack),
                        0));
            }
            Map<String, Integer> outputCounts = countOutputIdentities(rawRows);
            for (PreviewRow row : rawRows) {
                int duplicateCount = getDuplicateCount(outputCounts, row.outputKey);
                PreviewRow displayRow = new PreviewRow(
                    row.materialName,
                    row.excludeToken,
                    row.outputExcludeToken,
                    formatPreviewLine(row.line, duplicateCount),
                    row.outputKey,
                    row.outputLabel,
                    duplicateCount);
                if (!filter.isEmpty()
                    && !NechSearchCompat.matches(displayRow.line, filter)
                    && !NechSearchCompat.matches(displayRow.materialName, filter)
                    && !NechSearchCompat.matches(displayRow.excludeToken, filter)
                    && !NechSearchCompat.matches(displayRow.outputExcludeToken, filter)
                    && !NechSearchCompat.matches(displayRow.outputLabel, filter)) {
                    continue;
                }
                this.previewRows.add(displayRow);
            }
            sortDuplicateOutputsFirst(this.previewRows);
            if (this.previewPageIndex >= getPreviewPageCount()) {
                this.previewPageIndex = Math.max(0, getPreviewPageCount() - 1);
            }
        }

        private void rebuildDedupe() {
            this.dedupeRows.clear();
            ItemStack preview = buildStack();
            if (preview == null) {
                return;
            }
            List<DedupeRow> rows = new ArrayList<>();
            Map<String, Integer> duplicateOutputs = collectDuplicateOutputCounts(preview);
            String filter = this.dedupeSearch == null ? "" : this.dedupeSearch.trim();
            for (String materialName : CompositeWildcardPatternGenerator.getCandidateMaterials(preview)) {
                collectDedupeRow(rows, materialName, duplicateOutputs);
            }
            sortDuplicateDedupeRowsFirst(rows);
            for (DedupeRow row : rows) {
                if (row.matches(this, filter)) {
                    this.dedupeRows.add(row);
                }
            }
            if (this.dedupePageIndex >= getDedupePageCount()) {
                this.dedupePageIndex = Math.max(0, getDedupePageCount() - 1);
            }
        }

        private void collectDedupeRow(List<DedupeRow> rows, String materialName, Map<String, Integer> duplicateOutputs) {
            String inputOreName = getOreName(this.inputEntry, materialName);
            String outputOreName = getOreName(this.outputEntry, materialName);
            boolean inputDuplicate = hasDuplicateOptions(inputOreName);
            boolean outputDuplicate = hasDuplicateOptions(outputOreName);
            if (!inputDuplicate && !outputDuplicate) {
                return;
            }
            int duplicateCount = getDuplicateCount(duplicateOutputs, getDedupeDuplicateKey(materialName));
            rows.add(new DedupeRow(materialName, inputOreName, outputOreName, inputDuplicate, outputDuplicate, duplicateCount));
        }

        private Map<String, Integer> collectDuplicateOutputCounts(ItemStack preview) {
            List<PreviewRow> generatedRows = new ArrayList<>();
            for (CompositeWildcardPatternGenerator.GeneratedPattern pattern : CompositeWildcardPatternGenerator.generatePreviewPatterns(preview)) {
                String outputKey = getOutputIdentity(pattern.outputStack);
                if (outputKey.isEmpty()) {
                    continue;
                }
                generatedRows.add(
                    new PreviewRow(
                        pattern.materialName,
                        "",
                        getOutputExcludeToken(pattern.outputStack, pattern.materialName),
                        "",
                        outputKey,
                        "",
                        0));
            }
            Map<String, Integer> outputCounts = countOutputIdentities(generatedRows);
            Map<String, Integer> duplicateOutputs = new java.util.LinkedHashMap<>();
            for (PreviewRow row : generatedRows) {
                int duplicateCount = getDuplicateCount(outputCounts, row.outputKey);
                if (duplicateCount <= 1) {
                    continue;
                }
                String key = getDedupeDuplicateKey(row.materialName);
                Integer current = duplicateOutputs.get(key);
                if (current == null || duplicateCount > current.intValue()) {
                    duplicateOutputs.put(key, Integer.valueOf(duplicateCount));
                }
            }
            return duplicateOutputs;
        }

        private PreviewRow getPreviewRow(int lineIndex) {
            int absolute = this.previewPageIndex * PREVIEW_LINES + lineIndex;
            return absolute >= 0 && absolute < this.previewRows.size() ? this.previewRows.get(absolute) : null;
        }

        private DedupeRow getDedupeRow(int lineIndex) {
            int absolute = this.dedupePageIndex * DEDUPE_LINES + lineIndex;
            return absolute >= 0 && absolute < this.dedupeRows.size() ? this.dedupeRows.get(absolute) : null;
        }

        private int getPreviewPageCount() {
            return Math.max(1, (this.previewRows.size() + PREVIEW_LINES - 1) / PREVIEW_LINES);
        }

        private int getDedupePageCount() {
            return Math.max(1, (this.dedupeRows.size() + DEDUPE_LINES - 1) / DEDUPE_LINES);
        }

        private int getExcludePageCount() {
            return Math.max(1, (this.getCurrentExcludeTokens().size() + EXCLUDE_LINES - 1) / EXCLUDE_LINES);
        }

        private void excludeSelectedPreviewRow() {
            excludePreviewRow(0);
        }

        private void excludePreviewRow(int lineIndex) {
            PreviewRow row = getPreviewRow(lineIndex);
            if (row == null) {
                return;
            }
            String token = row.hasDuplicateOutput() && !row.outputExcludeToken.isEmpty()
                ? row.outputExcludeToken
                : row.excludeToken.isEmpty() ? row.materialName : row.excludeToken;
            if (token.isEmpty()) {
                return;
            }
            List<String> tokens = new ArrayList<>();
            if (this.globalExclude != null && !this.globalExclude.trim().isEmpty()) {
                for (String part : this.globalExclude.split("[,;\\s]+")) {
                    String trimmed = part == null ? "" : part.trim();
                    if (!trimmed.isEmpty()) {
                        tokens.add(trimmed);
                    }
                }
            }
            for (String existing : tokens) {
                if (existing.equalsIgnoreCase(token)) {
                    return;
                }
            }
            tokens.add(token);
            setCurrentExcludeValue(String.join(" ", tokens));
            rebuildPreview();
        }

        private String getFixedLabel(int index) {
            if (index < 0 || index >= this.fixedInputs.size()) {
                return tr("gui.composite_wildcardpattern.fixed_empty");
            }
            ItemStack fixed = this.fixedInputs.get(index);
            return fixed == null || fixed.getItem() == null
                ? tr("gui.composite_wildcardpattern.fixed_empty")
                : fixed.getDisplayName();
        }

        private int getFixedAmount(int index) {
            if (index < 0 || index >= this.fixedInputs.size()) {
                return 1;
            }
            ItemStack fixed = this.fixedInputs.get(index);
            return fixed == null ? 1 : Math.max(1, fixed.stackSize);
        }

        private String getOreName(WildcardPatternEntry entry, String materialName) {
            if (entry == null || entry.isEmpty()) {
                return null;
            }
            ItemStack displayStack = entry.getDisplayStack();
            if (displayStack == null) {
                return null;
            }
            ItemData association = GTOreDictUnificator.getAssociation(displayStack);
            if (association != null && association.hasValidPrefixMaterialData()) {
                return getPrefixName(association.mPrefix) + materialName;
            }
            int[] oreIds = OreDictionary.getOreIDs(displayStack);
            if (oreIds == null || oreIds.length == 0) {
                return null;
            }
            String bestPrefix = null;
            int bestPrefixLen = 0;
            for (int oreId : oreIds) {
                String oreName = OreDictionary.getOreName(oreId);
                if (oreName == null || oreName.isEmpty()) continue;
                for (gregtech.api.enums.OrePrefixes prefix : GTCompat.orePrefixes()) {
                    String prefixName = getPrefixName(prefix);
                    if (!prefixName.isEmpty()
                        && oreName.regionMatches(true, 0, prefixName, 0, prefixName.length())
                        && prefixName.length() > bestPrefixLen) {
                        bestPrefix = prefixName;
                        bestPrefixLen = prefixName.length();
                    }
                }
            }
            return bestPrefix == null ? null : bestPrefix + materialName;
        }

        private boolean hasDuplicateOptions(String oreName) {
            return oreName != null && OreDictionary.getOres(oreName) != null && OreDictionary.getOres(oreName).size() > 1;
        }

        private String getDedupeDuplicateKey(String materialName) {
            return materialName == null ? "" : materialName;
        }

        private String getSelectedDedupeLabel(String oreName) {
            if (oreName == null || oreName.isEmpty()) {
                return "";
            }
            java.util.List<ItemStack> options = OreDictionary.getOres(oreName);
            if (options == null || options.isEmpty()) {
                return tr("gui.wildcardpattern.preview_empty");
            }
            ItemStack current = this.preferredOreStacks.get(oreName);
            ItemStack target = current != null ? current : getDefaultDedupeChoice(options);
            return formatDedupeChoice(target);
        }

        private void cycleDedupeChoice(String oreName) {
            if (oreName == null || oreName.isEmpty()) {
                return;
            }
            java.util.List<ItemStack> options = OreDictionary.getOres(oreName);
            if (options == null || options.isEmpty()) {
                return;
            }
            ItemStack current = this.preferredOreStacks.get(oreName);
            int nextIndex = 0;
            if (current != null) {
                for (int index = 0; index < options.size(); index++) {
                    if (OreDictionary.itemMatches(options.get(index), current, false)) {
                        nextIndex = (index + 1) % options.size();
                        break;
                    }
                }
            } else {
                ItemStack defaultChoice = getDefaultDedupeChoice(options);
                for (int index = 0; index < options.size(); index++) {
                    if (OreDictionary.itemMatches(options.get(index), defaultChoice, false)) {
                        nextIndex = (index + 1) % options.size();
                        break;
                    }
                }
            }
            ItemStack next = options.get(nextIndex);
            if (next != null) {
                this.preferredOreStacks.put(oreName, next.copy());
            }
            rebuildPreview();
            rebuildDedupe();
        }

        private String formatDedupeChoice(ItemStack stack) {
            if (stack == null) {
                return "";
            }
            GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
            String mod = id == null || id.modId == null || id.modId.isEmpty() ? "unknown" : id.modId;
            return "[" + mod + "] " + stack.getDisplayName();
        }

        private ItemStack getDefaultDedupeChoice(java.util.List<ItemStack> options) {
            for (ItemStack option : options) {
                if (option == null || option.getItem() == null) {
                    continue;
                }
                GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(option.getItem());
                if (id != null && "gregtech".equalsIgnoreCase(id.modId)) {
                    return option;
                }
            }
            return options.get(0);
        }

        private String getPreviewExcludeToken(ItemStack inputStack, ItemStack outputStack, String materialName) {
            String inputOre = getAssociatedOreName(inputStack);
            if (inputOre != null && !inputOre.isEmpty()) {
                return inputOre;
            }
            String outputOre = getAssociatedOreName(outputStack);
            if (outputOre != null && !outputOre.isEmpty()) {
                return outputOre;
            }
            return materialName == null ? "" : materialName;
        }

        private String getAssociatedOreName(ItemStack stack) {
            if (stack == null) {
                return null;
            }
            ItemData association = GTOreDictUnificator.getAssociation(stack);
            if (association != null && association.hasValidPrefixMaterialData()) {
                return getPrefixName(association.mPrefix) + association.mMaterial.mMaterial.mName;
            }
            int[] oreIds = OreDictionary.getOreIDs(stack);
            if (oreIds == null || oreIds.length == 0) {
                return null;
            }
            for (int oreId : oreIds) {
                String oreName = OreDictionary.getOreName(oreId);
                if (oreName == null || oreName.isEmpty()) continue;
                for (gregtech.api.enums.OrePrefixes prefix : GTCompat.orePrefixes()) {
                    String prefixName = getPrefixName(prefix);
                    if (!prefixName.isEmpty() && oreName.regionMatches(true, 0, prefixName, 0, prefixName.length())) {
                        return oreName;
                    }
                }
            }
            return null;
        }

        private void save() {
            ItemStack preview = buildStack();
            ItemStack held = getHeldStack();
            if (preview == null || held == null) {
                return;
            }
            CompositeWildcardPatternState.setExpandedPatternCount(preview, CompositeWildcardPatternGenerator.countPreviewPatterns(preview));
            CompositeWildcardPatternGenerator.markAsCompositeWildcard(held);
            CompositeWildcardPatternState.applyConfig(held, CompositeWildcardPatternState.exportConfig(preview));
            WildcardNetwork.CHANNEL.sendToServer(
                new MessageUpdateCompositeWildcardConfig(this.slot, CompositeWildcardPatternState.exportConfig(preview)));
        }

        private ItemStack buildStack() {
            ItemStack held = getHeldStack();
            if (held == null) {
                return null;
            }
            ItemStack preview = held.copy();
            CompositeWildcardPatternGenerator.markAsCompositeWildcard(preview);
            CompositeWildcardPatternState.setWildcardInput(preview, this.inputEntry);
            CompositeWildcardPatternState.setWildcardOutput(preview, this.outputEntry);
            CompositeWildcardPatternState.setFixedInputs(preview, this.fixedInputs);
            List<String> includes = new ArrayList<>();
            includes.add("");
            List<String> excludes = new ArrayList<>();
            excludes.add("");
            WildcardPatternConfig.apply(preview, this.globalExclude, includes, excludes);
            for (java.util.Map.Entry<String, ItemStack> entry : this.preferredOreStacks.entrySet()) {
                WildcardPatternConfig.setPreferredOreStack(preview, entry.getKey(), entry.getValue());
            }
            return preview;
        }

        private ItemStack getHeldStack() {
            return this.player.inventory.getStackInSlot(this.slot);
        }
    }
}

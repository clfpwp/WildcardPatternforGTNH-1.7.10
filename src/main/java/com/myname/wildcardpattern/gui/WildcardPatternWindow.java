package com.myname.wildcardpattern.gui;

import java.util.ArrayList;
import java.util.List;
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

import appeng.api.networking.crafting.ICraftingPatternDetails;
import com.myname.wildcardpattern.compat.NechSearchCompat;
import com.myname.wildcardpattern.crafting.WildcardPatternEntry;
import com.myname.wildcardpattern.crafting.WildcardPatternGenerator;
import com.myname.wildcardpattern.item.WildcardPatternConfig;
import com.myname.wildcardpattern.item.WildcardPatternState;
import com.myname.wildcardpattern.network.MessageUpdateWildcardConfig;
import com.myname.wildcardpattern.network.WildcardNetwork;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.Materials;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

public final class WildcardPatternWindow {

    private static final int GUI_WIDTH = 452;
    private static final int GUI_HEIGHT = 292;
    private static final int RULE_ROWS = 9;
    private static final int PREVIEW_LINES = 12;
    private static final int DEDUPE_LINES = 4;
    private static final int EXCLUDE_LINES = 9;
    private static final int ENTRY_TEXT_WIDTH = 50;
    private static final int ENTRY_MODE_X = ENTRY_TEXT_WIDTH + 3;
    private static final int ENTRY_MODE_WIDTH = 34;
    private static final int ENTRY_AMOUNT_X = ENTRY_MODE_X + ENTRY_MODE_WIDTH + 3;
    private static final int ENTRY_AMOUNT_WIDTH = 40;

    private static final int BACKGROUND_COLOR = 0xFFF0F0F0;
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

    private WildcardPatternWindow() {}

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
                ? "gui.wildcardpattern.dedupe_page"
                : state.excludePage
                    ? "gui.wildcardpattern.exclude_page"
                    : state.previewPage ? "gui.wildcardpattern.preview_page" : "gui.wildcardpattern.title"));
        builder.widget(title);

        TextWidget hint = new TextWidget("");
        hint.setPos(132, 11);
        hint.setSize(280, 10);
        hint.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY
            + tr(state.dedupePage
                ? "gui.wildcardpattern.dedupe_hint"
                : state.excludePage
                    ? "gui.wildcardpattern.exclude_hint"
                    : state.previewPage ? "gui.wildcardpattern.preview_hint" : "gui.wildcardpattern.drag_hint"));
        builder.widget(hint);
    }

    private static void addMainPage(ModularWindow.Builder builder, WindowState state) {
        addPanel(builder, state, 8, 28, 436, 215, false);
        addRulesHeader(builder, state, 12, 40);
        addSeparator(builder, state, 12, 53, 424, 2, false);

        List<EntryCellRefs> refs = new ArrayList<>();
        for (int row = 0; row < RULE_ROWS; row++) {
            addRuleRow(builder, state, refs, row, 12, 58 + row * 17);
        }
        addSeparator(builder, state, 12, 213, 424, 2, false);

        addGlobalExclude(builder, state, 8, 248);
        addRuleExcludeEditor(builder, state, 164, 248);

        ButtonWidget clearAll = button("gui.wildcardpattern.clear");
        clearAll.setPos(306, 248);
        clearAll.setSize(68, 18);
        clearAll.setOnClick((clickData, widget) -> {
            clearEntries(state.inputs);
            clearEntries(state.outputs);
            state.globalExclude = "";
            clearStrings(state.ruleIncludes);
            clearStrings(state.ruleExcludes);
            for (EntryCellRefs ref : refs) {
                ref.clear();
            }
            state.refreshActivePage();
        });
        addMainWidget(builder, state, clearAll);

        ButtonWidget dedupe = button("gui.wildcardpattern.dedupe");
        dedupe.setPos(382, 248);
        dedupe.setSize(68, 18);
        dedupe.setOnClick((clickData, widget) -> state.openDedupe());
        addMainWidget(builder, state, dedupe);

        ButtonWidget previewAll = button("gui.wildcardpattern.preview_all");
        previewAll.setPos(306, 270);
        previewAll.setSize(68, 18);
        previewAll.setOnClick((clickData, widget) -> state.openPreview(-1));
        addMainWidget(builder, state, previewAll);

        ButtonWidget save = button("gui.wildcardpattern.save");
        save.setPos(382, 270);
        save.setSize(68, 18);
        save.setOnClick((clickData, widget) -> {
            state.save();
            widget.getWindow().closeWindow();
        });
        addMainWidget(builder, state, save);
    }

    private static void addRulesHeader(ModularWindow.Builder builder, WindowState state, int x, int y) {
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + "#", x + 4, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_input"), x + 26, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_mode"), x + 82, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_amount"), x + 114, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_output"), x + 158, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_mode"), x + 214, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_amount"), x + 246, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.actions"), x + 302, y);
    }

    private static void addRuleRow(
        ModularWindow.Builder builder,
        WindowState state,
        List<EntryCellRefs> refs,
        int row,
        int x,
        int y) {
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + String.valueOf(row + 1), x + 4, y + 4);

        EntryCellRefs input = addEntryCell(builder, state, state.inputs, row, x + 22, y);
        EntryCellRefs output = addEntryCell(builder, state, state.outputs, row, x + 154, y);
        refs.add(input);
        refs.add(output);

        TextWidget arrow = new TextWidget(EnumChatFormatting.BLACK + ">");
        arrow.setPos(x + 146, y + 4);
        addMainWidget(builder, state, arrow);

        ButtonWidget preview = button("gui.wildcardpattern.preview_short");
        preview.setPos(x + 292, y);
        preview.setSize(22, 15);
        preview.setOnClick((clickData, widget) -> state.openPreview(row));
        addMainWidget(builder, state, preview);

        ButtonWidget filter = button(
            () -> buttonBackground(
                state.selectedRule == row,
                tr("gui.wildcardpattern.filter_short"),
                state.selectedRule == row ? 0xFF1B4E8A : BUTTON_TEXT_COLOR));
        filter.setPos(x + 318, y);
        filter.setSize(22, 15);
        filter.setOnClick((clickData, widget) -> {
            state.selectedRule = row;
            state.refreshActivePage();
        });
        addMainWidget(builder, state, filter);

        ButtonWidget multiply = button("gui.wildcardpattern.multiply_short");
        multiply.setPos(x + 344, y);
        multiply.setSize(28, 15);
        multiply.setOnClick((clickData, widget) -> {
            if (clickData.mouseButton == 1) {
                state.divideRule(row);
            } else {
                state.multiplyRule(row);
            }
            input.updateAmount();
            output.updateAmount();
        });
        addMainWidget(builder, state, multiply);

        ButtonWidget clear = button("gui.wildcardpattern.clear_short");
        clear.setPos(x + 376, y);
        clear.setSize(28, 15);
        clear.setOnClick((clickData, widget) -> {
            state.inputs.set(row, WildcardPatternEntry.fromStack(null));
            state.outputs.set(row, WildcardPatternEntry.fromStack(null));
            state.ruleIncludes.set(row, "");
            state.ruleExcludes.set(row, "");
            input.clear();
            output.clear();
            state.refreshActivePage();
        });
        addMainWidget(builder, state, clear);
    }

    private static EntryCellRefs addEntryCell(
        ModularWindow.Builder builder,
        WindowState state,
        List<WildcardPatternEntry> entries,
        int index,
        int x,
        int y) {
        final boolean[] suppressNextSetter = { false };
        final WildcardEntryDropTextField[] textRef = new WildcardEntryDropTextField[1];
        WildcardEntryDropTextField text = new WildcardEntryDropTextField(stack -> {
            WildcardPatternEntry next = WildcardPatternEntry.fromStack(stack);
            if (next.canOreDict()) {
                next.convertToOreDict();
            } else {
                next.convertToItem();
            }
            entries.set(index, next);
            state.refreshActivePage();
            if (textRef[0] != null) {
                suppressNextSetter[0] = true;
                textRef[0].setText(trim(next.getLabel(), 11));
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
            WildcardPatternEntry entry = entries.get(index);
            if (!entry.isOreDict() && entry.isEmpty() && WildcardPatternEntry.looksLikeOreDictPattern(value)) {
                entry.convertToOreDict();
            }
            if (entry.isOreDict()) {
                entry.setOreNameOrPrefix(value);
            } else {
                entry.setMatcher(value);
            }
            state.refreshActivePage();
        });
        text.setText(entries.get(index).isEmpty() ? "" : trim(entries.get(index).getLabel(), 11));
        text.setTextColor(FIELD_TEXT_COLOR);
        text.setBackground(WildcardPatternWindow::fieldBackground);
        text.setTextAlignment(Alignment.CenterLeft);
        text.setMaxLength(80);
        text.setPos(x, y);
        text.setSize(ENTRY_TEXT_WIDTH, 16);
        addMainWidget(builder, state, text);

        ButtonWidget mode = button(
            () -> buttonBackground(
                entries.get(index).isOreDict(),
                tr(entries.get(index).isOreDict() ? "gui.wildcardpattern.mode_oredict" : "gui.wildcardpattern.mode_name"),
                BUTTON_TEXT_COLOR));
        mode.setPos(x + ENTRY_MODE_X, y);
        mode.setSize(ENTRY_MODE_WIDTH, 16);
        mode.setOnClick((clickData, widget) -> {
            if (clickData.mouseButton == 0 && !clickData.doubleClick) {
                switchEntryMode(entries, index, text, state, suppressNextSetter);
            }
        });
        addMainWidget(builder, state, mode);

        TextFieldWidget amount = new TextFieldWidget();
        amount.setSynced(false, false);
        amount.setSetter(value -> {
            entries.get(index).setAmount(parseAmount(value));
            state.refreshActivePage();
        });
        amount.setText(formatAmount(entries.get(index).getAmountLong()));
        amount.setTextColor(FIELD_TEXT_COLOR);
        amount.setBackground(WildcardPatternWindow::fieldBackground);
        amount.setTextAlignment(Alignment.Center);
        amount.setMaxLength(8);
        amount.setPos(x + ENTRY_AMOUNT_X, y);
        amount.setSize(ENTRY_AMOUNT_WIDTH, 16);
        addMainWidget(builder, state, amount);

        return new EntryCellRefs(text, amount, () -> formatAmount(entries.get(index).getAmountLong()));
    }

    private static void switchEntryMode(
        List<WildcardPatternEntry> entries,
        int index,
        WildcardEntryDropTextField text,
        WindowState state,
        boolean[] suppressNextSetter) {
        WildcardPatternEntry entry = entries.get(index);
        if (entry.isOreDict()) {
            entry.convertToItem();
        } else {
            entry.convertToOreDict();
        }
        suppressNextSetter[0] = true;
        text.setText(trim(entry.getLabel(), 11));
        text.markForUpdate();
        state.refreshActivePage();
    }

    private static void addGlobalExclude(ModularWindow.Builder builder, WindowState state, int x, int y) {
        addPanel(builder, state, x, y, 148, 40, false);
        addMainText(builder, state, EnumChatFormatting.BLACK + tr("gui.wildcardpattern.global_exclude"), x + 12, y + 10);
        TextWidget summary = new TextWidget("");
        summary.setPos(x + 12, y + 24);
        summary.setScale(0.72f);
        summary.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + trim(state.getExcludeSummary(-1), 24));
        addMainWidget(builder, state, summary);

        ButtonWidget edit = button("gui.wildcardpattern.exclude_short");
        edit.setPos(x + 96, y + 8);
        edit.setSize(42, 18);
        edit.setOnClick((clickData, widget) -> state.openExcludeEditor(-1));
        addMainWidget(builder, state, edit);
    }

    private static void addRuleExcludeEditor(ModularWindow.Builder builder, WindowState state, int x, int y) {
        addPanel(builder, state, x, y, 138, 40, false);
        TextWidget label = new TextWidget("");
        label.setPos(x + 8, y + 10);
        label.setStringSupplier(() -> EnumChatFormatting.BLACK
            + StatCollector.translateToLocalFormatted("gui.wildcardpattern.rule_exclude", state.selectedRule + 1));
        addMainWidget(builder, state, label);
        TextWidget summary = new TextWidget("");
        summary.setPos(x + 8, y + 24);
        summary.setScale(0.72f);
        summary.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + trim(state.getExcludeSummary(state.selectedRule), 23));
        addMainWidget(builder, state, summary);

        ButtonWidget edit = button("gui.wildcardpattern.exclude_short");
        edit.setPos(x + 88, y + 8);
        edit.setSize(42, 18);
        edit.setOnClick((clickData, widget) -> state.openExcludeEditor(state.selectedRule));
        addMainWidget(builder, state, edit);
    }

    private static void addPreviewPage(ModularWindow.Builder builder, WindowState state) {
        addPanel(builder, state, 8, 28, 436, 262, true);
        addSeparator(builder, state, 18, 81, 406, 2, true);

        TextWidget source = new TextWidget("");
        source.setPos(18, 18);
        source.setStringSupplier(() -> EnumChatFormatting.BLACK + state.getPreviewTitle());
        addPreviewWidget(builder, state, source);

        addPreviewTextField(
            builder,
            state,
            EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.search"),
            () -> state.previewSearch,
            value -> {
                state.previewSearch = value == null ? "" : value;
                state.previewPageIndex = 0;
                state.refreshActivePage();
            },
            192,
            34,
            240,
            true);

        if (state.previewRule >= 0) {
            addPreviewTextField(
                builder,
                state,
                EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.include_short"),
                () -> state.ruleIncludes.get(state.previewRule),
                value -> {
                    state.ruleIncludes.set(state.previewRule, value == null ? "" : value);
                    state.previewPageIndex = 0;
                    state.refreshActivePage();
                },
                18,
                58,
                206,
                true);
        }

        for (int i = 0; i < PREVIEW_LINES; i++) {
            final int lineIndex = i;
            TextWidget line = new TextWidget("");
            line.setPos(18, 88 + i * 14);
            line.setStringSupplier(() -> {
                int absolute = state.previewPageIndex * PREVIEW_LINES + lineIndex;
                PreviewRow row = state.getPreviewRow(lineIndex);
                return row != null ? EnumChatFormatting.DARK_GRAY + row.line : "";
            });
            addPreviewWidget(builder, state, line);

            ButtonWidget exclude = button("gui.wildcardpattern.exclude_short");
            exclude.setPos(388, 86 + i * 14);
            exclude.setSize(38, 12);
            exclude.setOnClick((clickData, widget) -> state.excludePreviewRow(lineIndex));
            addPreviewWidget(builder, state, exclude);
        }

        ButtonWidget back = button("gui.wildcardpattern.back");
        back.setPos(18, 264);
        back.setSize(64, 17);
        back.setOnClick((clickData, widget) -> state.previewPage = false);
        addPreviewWidget(builder, state, back);

        ButtonWidget prev = button("<");
        prev.setPos(182, 264);
        prev.setSize(34, 17);
        prev.setOnClick((clickData, widget) -> state.prevPreviewPage());
        addPreviewWidget(builder, state, prev);

        TextWidget page = new TextWidget("");
        page.setPos(228, 268);
        page.setStringSupplier(() -> EnumChatFormatting.BLACK
            + StatCollector.translateToLocalFormatted(
                "gui.wildcardpattern.page",
                Integer.valueOf(state.previewPageIndex + 1),
                Integer.valueOf(Math.max(1, state.getPreviewPageCount()))));
        addPreviewWidget(builder, state, page);

        ButtonWidget next = button(">");
        next.setPos(304, 264);
        next.setSize(34, 17);
        next.setOnClick((clickData, widget) -> state.nextPreviewPage());
        addPreviewWidget(builder, state, next);
    }

    private static void addExcludePage(ModularWindow.Builder builder, WindowState state) {
        addExcludePanel(builder, state, 8, 28, 436, 262);
        addExcludeSeparator(builder, state, 18, 55, 406, 2);

        TextWidget title = new TextWidget("");
        title.setPos(18, 38);
        title.setStringSupplier(() -> EnumChatFormatting.BLACK + state.getExcludeTitle());
        addExcludeWidget(builder, state, title);

        TextWidget tip1 = new TextWidget("");
        tip1.setPos(18, 68);
        tip1.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.exclude_tip1"));
        addExcludeWidget(builder, state, tip1);

        TextWidget tip2 = new TextWidget("");
        tip2.setPos(18, 81);
        tip2.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.exclude_tip2"));
        addExcludeWidget(builder, state, tip2);

        TextFieldWidget field = new WildcardFilterDropTextField(value -> state.excludeDraft = value == null ? "" : value)
            .setOnEnter(state::addCurrentExcludeDraft);
        field.setSynced(false, false);
        field.setGetter(() -> state.excludeDraft);
        field.setSetter(value -> state.excludeDraft = value == null ? "" : value);
        field.setText(state.excludeDraft);
        field.setTextColor(FIELD_TEXT_COLOR);
        field.setBackground(WildcardPatternWindow::fieldBackground);
        field.setTextAlignment(Alignment.CenterLeft);
        field.setMaxLength(256);
        field.setPos(18, 104);
        field.setSize(330, 18);
        addExcludeWidget(builder, state, field);

        ButtonWidget add = button("+");
        add.setPos(356, 104);
        add.setSize(68, 18);
        add.setOnClick((clickData, widget) -> state.addCurrentExcludeDraft());
        addExcludeWidget(builder, state, add);

        TextWidget current = new TextWidget("");
        current.setPos(18, 132);
        current.setScale(0.78f);
        current.setStringSupplier(() -> EnumChatFormatting.BLACK + tr("gui.wildcardpattern.exclude_current"));
        addExcludeWidget(builder, state, current);

        for (int i = 0; i < EXCLUDE_LINES; i++) {
            final int lineIndex = i;
            int rowY = 144 + i * 14;
            addExcludeCard(builder, state, 18, rowY, 394, 13);
            TextWidget line = new TextWidget("");
            line.setPos(24, rowY + 3);
            line.setScale(0.72f);
            line.setStringSupplier(() -> {
                String token = state.getCurrentExcludeToken(lineIndex);
                return token.isEmpty() ? "" : EnumChatFormatting.DARK_GRAY + trimMiddle(token, 48);
            });
            addExcludeWidget(builder, state, line);

            ButtonWidget delete = button("X");
            delete.setPos(388, rowY + 1);
            delete.setSize(20, 12);
            delete.setOnClick((clickData, widget) -> state.removeCurrentExcludeToken(lineIndex));
            addExcludeWidget(builder, state, delete);
        }

        ButtonWidget prev = button("<");
        prev.setPos(18, 264);
        prev.setSize(34, 17);
        prev.setOnClick((clickData, widget) -> {
            if (state.excludePageIndex > 0) {
                state.excludePageIndex--;
            }
        });
        addExcludeWidget(builder, state, prev);

        TextWidget page = new TextWidget("");
        page.setPos(64, 268);
        page.setStringSupplier(() -> EnumChatFormatting.BLACK
            + StatCollector.translateToLocalFormatted(
                "gui.wildcardpattern.page",
                Integer.valueOf(state.excludePageIndex + 1),
                Integer.valueOf(Math.max(1, state.getExcludePageCount()))));
        addExcludeWidget(builder, state, page);

        ButtonWidget next = button(">");
        next.setPos(140, 264);
        next.setSize(34, 17);
        next.setOnClick((clickData, widget) -> {
            if (state.excludePageIndex + 1 < state.getExcludePageCount()) {
                state.excludePageIndex++;
            }
        });
        addExcludeWidget(builder, state, next);

        ButtonWidget clear = button("gui.wildcardpattern.clear");
        clear.setPos(286, 264);
        clear.setSize(64, 17);
        clear.setOnClick((clickData, widget) -> state.setCurrentExcludeValue(""));
        addExcludeWidget(builder, state, clear);

        ButtonWidget back = button("gui.wildcardpattern.back");
        back.setPos(360, 264);
        back.setSize(64, 17);
        back.setOnClick((clickData, widget) -> state.closeExcludeEditor());
        addExcludeWidget(builder, state, back);
    }

    private static void addDedupePage(ModularWindow.Builder builder, WindowState state) {
        addDedupePanel(builder, state, 8, 28, 436, 262);
        addDedupeSeparator(builder, state, 18, 55, 406, 2);

        TextWidget title = new TextWidget("");
        title.setPos(18, 38);
        title.setStringSupplier(() -> EnumChatFormatting.BLACK + tr("gui.wildcardpattern.dedupe_page"));
        addDedupeWidget(builder, state, title);

        TextWidget hint = new TextWidget("");
        hint.setPos(18, 64);
        hint.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.dedupe_hint"));
        addDedupeWidget(builder, state, hint);

        addDedupeTextField(
            builder,
            state,
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
            final int lineIndex = i;
            int rowY = 98 + i * 39;
            addDedupeCard(builder, state, 14, rowY - 2, 418, 33);

            TextWidget rule = new TextWidget("");
            rule.setPos(18, rowY + 2);
            rule.setStringSupplier(() -> {
                int absolute = state.dedupePageIndex * DEDUPE_LINES + lineIndex;
                return absolute < state.dedupeRows.size()
                    ? EnumChatFormatting.BLACK + "R" + (state.dedupeRows.get(absolute).rule + 1)
                    : "";
            });
            addDedupeWidget(builder, state, rule);

            TextWidget inputOre = new TextWidget("");
            inputOre.setPos(42, rowY + 2);
            inputOre.setScale(0.74f);
            inputOre.setStringSupplier(() -> {
                int absolute = state.dedupePageIndex * DEDUPE_LINES + lineIndex;
                return absolute < state.dedupeRows.size()
                    ? EnumChatFormatting.BLACK + trimMiddle(state.dedupeRows.get(absolute).getInputLine(), 26)
                    : "";
            });
            addDedupeWidget(builder, state, inputOre);

            TextWidget topArrow = new TextWidget(EnumChatFormatting.BLACK + "->");
            topArrow.setPos(174, rowY + 2);
            addDedupeWidget(builder, state, topArrow);

            TextWidget outputOre = new TextWidget("");
            outputOre.setPos(194, rowY + 2);
            outputOre.setScale(0.74f);
            outputOre.setStringSupplier(() -> {
                int absolute = state.dedupePageIndex * DEDUPE_LINES + lineIndex;
                return absolute < state.dedupeRows.size()
                    ? EnumChatFormatting.BLACK + trimMiddle(state.dedupeRows.get(absolute).getOutputLine(), 26)
                    : "";
            });
            addDedupeWidget(builder, state, outputOre);

            TextWidget inputChoice = new TextWidget("");
            inputChoice.setPos(42, rowY + 17);
            inputChoice.setScale(0.7f);
            inputChoice.setStringSupplier(() -> {
                int absolute = state.dedupePageIndex * DEDUPE_LINES + lineIndex;
                return absolute < state.dedupeRows.size()
                    ? EnumChatFormatting.DARK_GRAY
                        + trimMiddle(state.getSelectedDedupeLabel(state.dedupeRows.get(absolute).inputOreName), 28)
                    : "";
            });
            addDedupeWidget(builder, state, inputChoice);

            TextWidget bottomArrow = new TextWidget(EnumChatFormatting.DARK_GRAY + "->");
            bottomArrow.setPos(174, rowY + 17);
            addDedupeWidget(builder, state, bottomArrow);

            TextWidget outputChoice = new TextWidget("");
            outputChoice.setPos(194, rowY + 17);
            outputChoice.setScale(0.7f);
            outputChoice.setStringSupplier(() -> {
                int absolute = state.dedupePageIndex * DEDUPE_LINES + lineIndex;
                return absolute < state.dedupeRows.size()
                    ? EnumChatFormatting.DARK_GRAY
                        + trimMiddle(state.getSelectedDedupeLabel(state.dedupeRows.get(absolute).outputOreName), 28)
                    : "";
            });
            addDedupeWidget(builder, state, outputChoice);

            ButtonWidget cycleInput = button("gui.wildcardpattern.dedupe_input");
            cycleInput.setPos(322, rowY + 3);
            cycleInput.setSize(50, 18);
            cycleInput.setOnClick((clickData, widget) -> {
                int absolute = state.dedupePageIndex * DEDUPE_LINES + lineIndex;
                if (absolute < state.dedupeRows.size()) {
                    state.cycleDedupeChoice(state.dedupeRows.get(absolute).inputOreName);
                }
            });
            cycleInput.setEnabled(widget -> state.dedupePage
                && state.getDedupeRow(lineIndex) != null
                && state.getDedupeRow(lineIndex).inputDuplicate);
            addDedupeWidget(builder, state, cycleInput);

            ButtonWidget cycleOutput = button("gui.wildcardpattern.dedupe_output");
            cycleOutput.setPos(378, rowY + 3);
            cycleOutput.setSize(50, 18);
            cycleOutput.setOnClick((clickData, widget) -> {
                int absolute = state.dedupePageIndex * DEDUPE_LINES + lineIndex;
                if (absolute < state.dedupeRows.size()) {
                    state.cycleDedupeChoice(state.dedupeRows.get(absolute).outputOreName);
                }
            });
            cycleOutput.setEnabled(widget -> state.dedupePage
                && state.getDedupeRow(lineIndex) != null
                && state.getDedupeRow(lineIndex).outputDuplicate);
            addDedupeWidget(builder, state, cycleOutput);
        }

        ButtonWidget back = button("gui.wildcardpattern.back");
        back.setPos(18, 264);
        back.setSize(64, 17);
        back.setOnClick((clickData, widget) -> state.dedupePage = false);
        addDedupeWidget(builder, state, back);

        ButtonWidget prev = button("<");
        prev.setPos(182, 264);
        prev.setSize(34, 17);
        prev.setOnClick((clickData, widget) -> {
            if (state.dedupePageIndex > 0) {
                state.dedupePageIndex--;
            }
        });
        addDedupeWidget(builder, state, prev);

        TextWidget page = new TextWidget("");
        page.setPos(228, 268);
        page.setStringSupplier(() -> EnumChatFormatting.BLACK
            + StatCollector.translateToLocalFormatted(
                "gui.wildcardpattern.page",
                Integer.valueOf(state.dedupePageIndex + 1),
                Integer.valueOf(Math.max(1, state.getDedupePageCount()))));
        addDedupeWidget(builder, state, page);

        ButtonWidget next = button(">");
        next.setPos(304, 264);
        next.setSize(34, 17);
        next.setOnClick((clickData, widget) -> {
            if (state.dedupePageIndex + 1 < state.getDedupePageCount()) {
                state.dedupePageIndex++;
            }
        });
        addDedupeWidget(builder, state, next);
    }

    private static void addDedupeTextField(
        ModularWindow.Builder builder,
        WindowState state,
        String label,
        java.util.function.Supplier<String> getter,
        java.util.function.Consumer<String> setter,
        int x,
        int y,
        int width) {
        TextWidget labelText = new TextWidget(label);
        labelText.setPos(x, y + 4);
        addDedupeWidget(builder, state, labelText);

        TextFieldWidget field = new WildcardFilterDropTextField(setter);
        field.setSynced(false, false);
        field.setText(getter.get());
        field.setSetter(setter);
        field.setTextColor(FIELD_TEXT_COLOR);
        field.setBackground(WildcardPatternWindow::fieldBackground);
        field.setTextAlignment(Alignment.CenterLeft);
        field.setMaxLength(256);
        field.setPos(x + 44, y);
        field.setSize(width - 44, 18);
        addDedupeWidget(builder, state, field);
    }

    private static void addPreviewTextField(
        ModularWindow.Builder builder,
        WindowState state,
        String label,
        java.util.function.Supplier<String> getter,
        java.util.function.Consumer<String> setter,
        int x,
        int y,
        int width,
        boolean previewPage) {
        TextWidget labelText = new TextWidget(label);
        labelText.setPos(x, y + 4);
        addPageWidget(builder, state, labelText, previewPage);

        TextFieldWidget field = new WildcardFilterDropTextField(setter);
        field.setSynced(false, false);
        field.setText(getter.get());
        field.setSetter(setter);
        field.setTextColor(FIELD_TEXT_COLOR);
        field.setBackground(WildcardPatternWindow::fieldBackground);
        field.setTextAlignment(Alignment.CenterLeft);
        field.setMaxLength(256);
        field.setPos(x + 44, y);
        field.setSize(width - 44, 18);
        addPageWidget(builder, state, field, previewPage);
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
        return new IDrawable[] { WildcardPatternWindow::drawInsetField };
    }

    private static String resolveButtonText(String text) {
        return text != null && text.contains(".") ? tr(text) : text;
    }

    private static void addPanel(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height,
        boolean previewPage) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 2, y + 2);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(PANEL_SHADOW_COLOR));
        addPageWidget(builder, state, shadow, previewPage);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(WildcardPatternWindow::drawInsetPanel);
        addPageWidget(builder, state, border, previewPage);

        DrawableWidget fill = new DrawableWidget();
        fill.setPos(x + 3, y + 3);
        fill.setSize(width - 6, height - 6);
        fill.setDrawable(new Rectangle().setColor(PANEL_COLOR));
        addPageWidget(builder, state, fill, previewPage);
    }

    private static void addSeparator(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height,
        boolean previewPage) {
        DrawableWidget dark = new DrawableWidget();
        dark.setPos(x, y);
        dark.setSize(width, Math.max(1, height / 2));
        dark.setDrawable(new Rectangle().setColor(PANEL_LINE_DARK));
        addPageWidget(builder, state, dark, previewPage);

        DrawableWidget light = new DrawableWidget();
        light.setPos(x, y + Math.max(1, height / 2));
        light.setSize(width, Math.max(1, height - Math.max(1, height / 2)));
        light.setDrawable(new Rectangle().setColor(PANEL_LINE_LIGHT));
        addPageWidget(builder, state, light, previewPage);
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

    private static void addMainText(ModularWindow.Builder builder, WindowState state, String text, int x, int y) {
        TextWidget widget = new TextWidget(text);
        widget.setPos(x, y);
        addMainWidget(builder, state, widget);
    }

    private static void addPreviewText(ModularWindow.Builder builder, WindowState state, String text, int x, int y) {
        TextWidget widget = new TextWidget(text);
        widget.setPos(x, y);
        addPreviewWidget(builder, state, widget);
    }

    private static void addMainWidget(ModularWindow.Builder builder, WindowState state, Widget widget) {
        addPageWidget(builder, state, widget, false);
    }

    private static void addPreviewWidget(ModularWindow.Builder builder, WindowState state, Widget widget) {
        addPageWidget(builder, state, widget, true);
    }

    private static void addDedupeWidget(ModularWindow.Builder builder, WindowState state, Widget widget) {
        widget.setEnabled(w -> state.dedupePage);
        builder.widget(widget);
    }

    private static void addExcludeWidget(ModularWindow.Builder builder, WindowState state, Widget widget) {
        widget.setEnabled(w -> state.excludePage);
        builder.widget(widget);
    }

    private static void addDedupePanel(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 2, y + 2);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(PANEL_SHADOW_COLOR));
        addDedupeWidget(builder, state, shadow);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(WildcardPatternWindow::drawInsetPanel);
        addDedupeWidget(builder, state, border);

        DrawableWidget fill = new DrawableWidget();
        fill.setPos(x + 3, y + 3);
        fill.setSize(width - 6, height - 6);
        fill.setDrawable(new Rectangle().setColor(PANEL_COLOR));
        addDedupeWidget(builder, state, fill);
    }

    private static void addDedupeCard(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 1, y + 1);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(CARD_SHADOW_COLOR));
        addDedupeWidget(builder, state, shadow);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(WildcardPatternWindow::drawDedupeCard);
        addDedupeWidget(builder, state, border);
    }

    private static void addExcludeCard(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 1, y + 1);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(CARD_SHADOW_COLOR));
        addExcludeWidget(builder, state, shadow);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(WildcardPatternWindow::drawDedupeCard);
        addExcludeWidget(builder, state, border);
    }

    private static void addExcludePanel(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 2, y + 2);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(PANEL_SHADOW_COLOR));
        addExcludeWidget(builder, state, shadow);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(WildcardPatternWindow::drawInsetPanel);
        addExcludeWidget(builder, state, border);

        DrawableWidget fill = new DrawableWidget();
        fill.setPos(x + 3, y + 3);
        fill.setSize(width - 6, height - 6);
        fill.setDrawable(new Rectangle().setColor(PANEL_COLOR));
        addExcludeWidget(builder, state, fill);
    }

    private static void addDedupeSeparator(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget dark = new DrawableWidget();
        dark.setPos(x, y);
        dark.setSize(width, Math.max(1, height / 2));
        dark.setDrawable(new Rectangle().setColor(PANEL_LINE_DARK));
        addDedupeWidget(builder, state, dark);

        DrawableWidget light = new DrawableWidget();
        light.setPos(x, y + Math.max(1, height / 2));
        light.setSize(width, Math.max(1, height - Math.max(1, height / 2)));
        light.setDrawable(new Rectangle().setColor(PANEL_LINE_LIGHT));
        addDedupeWidget(builder, state, light);
    }

    private static void addExcludeSeparator(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget dark = new DrawableWidget();
        dark.setPos(x, y);
        dark.setSize(width, Math.max(1, height / 2));
        dark.setDrawable(new Rectangle().setColor(PANEL_LINE_DARK));
        addExcludeWidget(builder, state, dark);

        DrawableWidget light = new DrawableWidget();
        light.setPos(x, y + Math.max(1, height / 2));
        light.setSize(width, Math.max(1, height - Math.max(1, height / 2)));
        light.setDrawable(new Rectangle().setColor(PANEL_LINE_LIGHT));
        addExcludeWidget(builder, state, light);
    }

    private static void addPageWidget(ModularWindow.Builder builder, WindowState state, Widget widget, boolean previewPage) {
        widget.setEnabled(w -> !state.dedupePage && !state.excludePage && state.previewPage == previewPage);
        builder.widget(widget);
    }

    private static String summarize(ItemStack input, ItemStack output) {
        return trim(formatPreviewStack(input) + " -> " + formatPreviewStack(output), 64);
    }

    private static String formatPreviewStack(ItemStack stack) {
        if (stack == null) {
            return "-";
        }
        return formatStackSize(Math.max(1, stack.stackSize)) + "x" + stack.getDisplayName();
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

    private static void ensureSize(List<WildcardPatternEntry> entries, int size) {
        while (entries.size() < size) {
            entries.add(WildcardPatternEntry.fromStack(null));
        }
        while (entries.size() > size) {
            entries.remove(entries.size() - 1);
        }
    }

    private static void clearEntries(List<WildcardPatternEntry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            entries.set(i, WildcardPatternEntry.fromStack(null));
        }
    }

    private static void clearStrings(List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            values.set(i, "");
        }
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
        if (amount >= 100_000_000) {
            return formatCompact(amount, 1_000_000_000L, "g");
        }
        if (amount >= 1_000_000) {
            return formatCompact(amount, 1_000_000, "m");
        }
        if (amount >= 1_000) {
            return formatCompact(amount, 1_000, "k");
        }
        return String.valueOf(Math.max(1, amount));
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
            return 1;
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
                return 1;
            }
            long result = Math.round(parsed * multiplier);
            return Math.max(1L, Math.min(WildcardPatternEntry.MAX_AMOUNT, result));
        } catch (NumberFormatException ignored) {
            return 1;
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
            if (base.length > 0) {
                result[0] = base[0];
            }
            result[1] = highlight;
            for (int index = 1; index < base.length; index++) {
                result[index + 1] = base[index];
            }
            return result;
        }
    }

    private static final class DedupeRow {
        private final int rule;
        private final String materialName;
        private final String inputOreName;
        private final String outputOreName;
        private final boolean inputDuplicate;
        private final boolean outputDuplicate;

        private DedupeRow(
            int rule,
            String materialName,
            String inputOreName,
            String outputOreName,
            boolean inputDuplicate,
            boolean outputDuplicate) {
            this.rule = rule;
            this.materialName = materialName == null ? "" : materialName;
            this.inputOreName = inputOreName;
            this.outputOreName = outputOreName;
            this.inputDuplicate = inputDuplicate;
            this.outputDuplicate = outputDuplicate;
        }

        private String getLine() {
            return "R" + (this.rule + 1) + " "
                + label(this.inputOreName)
                + " -> "
                + label(this.outputOreName);
        }

        private String getInputLine() {
            return label(this.inputOreName);
        }

        private String getOutputLine() {
            return label(this.outputOreName);
        }

        private String getChoiceLine(WindowState state) {
            return label(state.getSelectedDedupeLabel(this.inputOreName))
                + " -> "
                + label(state.getSelectedDedupeLabel(this.outputOreName));
        }

        private boolean matches(WindowState state, String search) {
            return NechSearchCompat.matches(getLine(), search)
                || NechSearchCompat.matches(getChoiceLine(state), search)
                || NechSearchCompat.matches(this.materialName, search);
        }

        private static String label(String value) {
            return value == null || value.isEmpty() ? "-" : value;
        }
    }

    private static final class PreviewRow {
        private final int rule;
        private final String materialName;
        private final String excludeToken;
        private final String line;

        private PreviewRow(int rule, String materialName, String excludeToken, String line) {
            this.rule = rule;
            this.materialName = materialName == null ? "" : materialName;
            this.excludeToken = excludeToken == null ? "" : excludeToken;
            this.line = line == null ? "" : line;
        }
    }

    private static final class WindowState {
        private final EntityPlayer player;
        private final int slot;
        private final List<WildcardPatternEntry> inputs;
        private final List<WildcardPatternEntry> outputs;
        private final List<String> ruleIncludes;
        private final List<String> ruleExcludes;
        private final List<PreviewRow> previewRows = new ArrayList<>();
        private final java.util.Map<String, ItemStack> preferredOreStacks = new java.util.LinkedHashMap<>();
        private final List<DedupeRow> dedupeRows = new ArrayList<>();

        private String globalExclude;
        private String previewSearch = "";
        private String dedupeSearch = "";
        private String excludeDraft = "";
        private boolean previewFullyLoaded;
        private boolean previewPage;
        private boolean dedupePage;
        private boolean excludePage;
        private boolean excludeReturnPreview;
        private int selectedRule;
        private int previewRule = -1;
        private int excludeRule = -1;
        private int previewPageIndex;
        private int dedupePageIndex;
        private int excludePageIndex;

        private WindowState(EntityPlayer player, int slot) {
            this.player = player;
            this.slot = slot;
            ItemStack stack = getHeldStack();
            if (stack != null) {
                WildcardPatternGenerator.markAsWildcard(stack);
                this.inputs = new ArrayList<>(WildcardPatternState.getInputEntries(stack));
                this.outputs = new ArrayList<>(WildcardPatternState.getOutputEntries(stack));
                this.globalExclude = WildcardPatternConfig.getGlobalExcludeMaterials(stack);
                this.ruleIncludes = new ArrayList<>(WildcardPatternConfig.getRuleIncludeList(stack, RULE_ROWS));
                this.ruleExcludes = new ArrayList<>(WildcardPatternConfig.getRuleExcludeList(stack, RULE_ROWS));
                for (String oreName : WildcardPatternConfig.getPreferredOreNames(stack)) {
                    ItemStack preferred = WildcardPatternConfig.getPreferredOreStack(stack, oreName);
                    if (preferred != null) {
                        this.preferredOreStacks.put(oreName, preferred);
                    }
                }
            } else {
                this.inputs = new ArrayList<>();
                this.outputs = new ArrayList<>();
                this.globalExclude = "";
                this.ruleIncludes = new ArrayList<>();
                this.ruleExcludes = new ArrayList<>();
            }
            ensureSize(this.inputs, RULE_ROWS);
            ensureSize(this.outputs, RULE_ROWS);
            while (this.ruleIncludes.size() < RULE_ROWS) this.ruleIncludes.add("");
            while (this.ruleExcludes.size() < RULE_ROWS) this.ruleExcludes.add("");
        }

        private void openPreview(int rule) {
            this.dedupePage = false;
            this.excludePage = false;
            this.previewRule = rule;
            this.previewPageIndex = 0;
            this.previewPage = true;
            rebuildPreview();
        }

        private void openExcludeEditor(int rule) {
            this.excludeReturnPreview = this.previewPage;
            this.previewPage = false;
            this.dedupePage = false;
            this.excludeRule = rule;
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

        private String getExcludeTitle() {
            if (this.excludeRule >= 0) {
                return StatCollector.translateToLocalFormatted("gui.wildcardpattern.rule_exclude", this.excludeRule + 1);
            }
            return tr("gui.wildcardpattern.global_exclude");
        }

        private String getCurrentExcludeValue() {
            return this.excludeRule >= 0 ? this.ruleExcludes.get(this.excludeRule) : this.globalExclude;
        }

        private void setCurrentExcludeValue(String value) {
            String next = value == null ? "" : value;
            if (this.excludeRule >= 0) {
                this.ruleExcludes.set(this.excludeRule, next);
            } else {
                this.globalExclude = next;
            }
            refreshActivePage();
        }

        private String getExcludeSummary(int rule) {
            String value = rule >= 0 ? this.ruleExcludes.get(rule) : this.globalExclude;
            if (value == null || value.trim().isEmpty()) {
                return tr("gui.wildcardpattern.exclude_empty");
            }
            return value;
        }

        private List<String> getCurrentExcludeTokens() {
            List<String> tokens = new ArrayList<>();
            String value = getCurrentExcludeValue();
            if (value == null || value.trim().isEmpty()) {
                return tokens;
            }
            for (String token : value.split("[,;\\s]+")) {
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

        private String getPreviewTitle() {
            if (this.previewRule >= 0) {
                return StatCollector.translateToLocalFormatted("gui.wildcardpattern.preview_rule", this.previewRule + 1);
            }
            return tr("gui.wildcardpattern.preview_all");
        }

        private int getPreviewPageCount() {
            if (this.previewFullyLoaded) {
                return Math.max(1, (this.previewRows.size() + PREVIEW_LINES - 1) / PREVIEW_LINES);
            }
            return Math.max(1, (this.previewRows.size() + PREVIEW_LINES - 1) / PREVIEW_LINES);
        }

        private int getDedupePageCount() {
            return Math.max(1, (this.dedupeRows.size() + DEDUPE_LINES - 1) / DEDUPE_LINES);
        }

        private int getExcludePageCount() {
            return Math.max(1, (this.getCurrentExcludeTokens().size() + EXCLUDE_LINES - 1) / EXCLUDE_LINES);
        }

        private void multiplyRule(int rule) {
            scaleRule(rule, true);
        }

        private void divideRule(int rule) {
            scaleRule(rule, false);
        }

        private void scaleRule(int rule, boolean multiplying) {
            if (rule < 0 || rule >= this.inputs.size() || rule >= this.outputs.size()) {
                return;
            }
            scaleEntry(this.inputs.get(rule), multiplying);
            scaleEntry(this.outputs.get(rule), multiplying);
            refreshActivePage();
        }

        private boolean canMultiplyRule(int rule) {
            return canMultiplyEntry(this.inputs.get(rule)) && canMultiplyEntry(this.outputs.get(rule));
        }

        private boolean canMultiplyEntry(WildcardPatternEntry entry) {
            return entry == null || entry.isEmpty() || entry.getAmountLong() <= WildcardPatternEntry.MAX_AMOUNT / 2L;
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

        private void rebuildPreview() {
            this.previewRows.clear();
            loadPreviewPage(this.previewPageIndex);
            if (this.previewPageIndex >= getPreviewPageCount()) {
                this.previewPageIndex = Math.max(0, getPreviewPageCount() - 1);
                loadPreviewPage(this.previewPageIndex);
            }
        }

        private void prevPreviewPage() {
            if (this.previewPageIndex <= 0) {
                return;
            }
            this.previewPageIndex--;
            loadPreviewPage(this.previewPageIndex);
        }

        private void nextPreviewPage() {
            loadPreviewPage(this.previewPageIndex);
            if (this.previewPageIndex + 1 >= getPreviewPageCount()) {
                return;
            }
            this.previewPageIndex++;
            loadPreviewPage(this.previewPageIndex);
        }

        private void loadPreviewPage(int pageIndex) {
            int safePageIndex = Math.max(0, pageIndex);
            int targetRows = (safePageIndex + 1) * PREVIEW_LINES + 1;
            PreviewScanResult result = scanPreviewRows(getPreviewFilter(), targetRows);
            this.previewRows.clear();
            this.previewRows.addAll(result.rows);
            this.previewFullyLoaded = result.fullyLoaded;
        }

        private void rebuildDedupe() {
            this.dedupeRows.clear();
            ItemStack preview = buildStack(null);
            if (preview == null) {
                return;
            }
            List<DedupeRow> rows = new ArrayList<>();
            for (int rule = 0; rule < RULE_ROWS; rule++) {
                collectDuplicateRows(preview, rows, rule);
            }
            String filter = this.dedupeSearch == null ? "" : this.dedupeSearch.trim();
            for (DedupeRow row : rows) {
                if (filter.isEmpty() || row.matches(this, filter)) {
                    this.dedupeRows.add(row);
                }
            }
            if (this.dedupePageIndex >= getDedupePageCount()) {
                this.dedupePageIndex = Math.max(0, getDedupePageCount() - 1);
            }
        }

        private void refreshActivePage() {
            if (this.dedupePage) {
                rebuildDedupe();
            } else if (this.previewPage) {
                rebuildPreview();
            }
        }

        private DedupeRow getDedupeRow(int lineIndex) {
            int absolute = this.dedupePageIndex * DEDUPE_LINES + lineIndex;
            return absolute >= 0 && absolute < this.dedupeRows.size() ? this.dedupeRows.get(absolute) : null;
        }

        private void collectDuplicateRows(ItemStack preview, List<DedupeRow> rows, int rule) {
            for (Materials material : WildcardPatternGenerator.getCandidateMaterials(preview, rule)) {
                String inputOreName = getOreName(this.inputs.get(rule), material);
                String outputOreName = getOreName(this.outputs.get(rule), material);
                boolean inputDuplicate = hasDuplicateOptions(inputOreName);
                boolean outputDuplicate = hasDuplicateOptions(outputOreName);
                if (!inputDuplicate && !outputDuplicate) {
                    continue;
                }
                rows.add(new DedupeRow(rule, material.mName, inputOreName, outputOreName, inputDuplicate, outputDuplicate));
            }
        }

        private String getOreName(WildcardPatternEntry entry, Materials material) {
            if (entry == null || entry.isEmpty()) {
                return null;
            }
            ItemData association = GTOreDictUnificator.getAssociation(entry.getDisplayStack());
            if (association == null || !association.hasValidPrefixMaterialData()) {
                return null;
            }
            return getPrefixName(association.mPrefix) + material.mName;
        }

        private boolean hasDuplicateOptions(String oreName) {
            return oreName != null && OreDictionary.getOres(oreName) != null && OreDictionary.getOres(oreName).size() > 1;
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

        private void collectPreviewLines(int rule, List<PreviewRow> target, String filter) {
            collectPreviewLines(rule, target, filter, Integer.MAX_VALUE);
        }

        private boolean collectPreviewLines(int rule, List<PreviewRow> target, String filter, int maxRows) {
            ItemStack preview = buildStack(rule);
            if (preview == null) {
                return true;
            }

            List<WildcardPatternEntry> previewInputs = WildcardPatternState.getInputEntries(preview);
            List<WildcardPatternEntry> previewOutputs = WildcardPatternState.getOutputEntries(preview);
            WildcardPatternEntry input = rule >= 0 && rule < previewInputs.size() ? previewInputs.get(rule) : null;
            WildcardPatternEntry output = rule >= 0 && rule < previewOutputs.size() ? previewOutputs.get(rule) : null;
            if ((input == null || input.isEmpty()) && (output == null || output.isEmpty())) {
                return true;
            }

            for (Materials candidate : WildcardPatternGenerator.getCandidateMaterials(preview, rule)) {
                List<ItemStack> inputStacks = input == null || input.isEmpty()
                    ? java.util.Collections.<ItemStack>singletonList(null)
                    : input.createStacks(candidate, preview);
                List<ItemStack> outputStacks = output == null || output.isEmpty()
                    ? java.util.Collections.<ItemStack>singletonList(null)
                    : output.createStacks(candidate, preview);
                for (ItemStack inputStack : inputStacks) {
                    for (ItemStack outputStack : outputStacks) {
                        if (!WildcardPatternConfig.acceptsCandidate(preview, rule, candidate, inputStack, outputStack)) {
                            continue;
                        }
                        String line = "R" + (rule + 1) + " " + summarize(inputStack, outputStack);
                        if (!filter.isEmpty() && !NechSearchCompat.matches(line, filter)
                            && !NechSearchCompat.matches(candidate.mName, filter)) {
                            continue;
                        }
                        target.add(
                            new PreviewRow(
                                rule,
                                candidate.mName,
                                getPreviewExcludeToken(inputStack, outputStack, candidate.mName),
                                line));
                        if (target.size() >= maxRows) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private List<Integer> getPreviewRules() {
            List<Integer> rules = new ArrayList<>();
            if (this.previewRule >= 0) {
                rules.add(this.previewRule);
                return rules;
            }
            for (int rule = 0; rule < RULE_ROWS; rule++) {
                rules.add(rule);
            }
            return rules;
        }

        private String getPreviewFilter() {
            return this.previewSearch == null ? "" : this.previewSearch.trim();
        }

        private PreviewRow getPreviewRow(int lineIndex) {
            int absolute = this.previewPageIndex * PREVIEW_LINES + lineIndex;
            return absolute >= 0 && absolute < this.previewRows.size() ? this.previewRows.get(absolute) : null;
        }

        private void excludePreviewRow(int lineIndex) {
            PreviewRow row = getPreviewRow(lineIndex);
            if (row == null) {
                return;
            }
            appendRuleExclude(row.rule, row.excludeToken.isEmpty() ? row.materialName : row.excludeToken);
            rebuildPreview();
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
            if (association == null || !association.hasValidPrefixMaterialData()) {
                return null;
            }
            return getPrefixName(association.mPrefix) + association.mMaterial.mMaterial.mName;
        }

        private void appendRuleExclude(int rule, String materialName) {
            if (rule < 0 || rule >= this.ruleExcludes.size()) {
                return;
            }
            String token = materialName == null ? "" : materialName.trim();
            if (token.isEmpty()) {
                return;
            }
            List<String> parts = new ArrayList<>();
            String current = this.ruleExcludes.get(rule);
            if (current != null && !current.trim().isEmpty()) {
                for (String part : current.split("[,;\\s]+")) {
                    String trimmed = part == null ? "" : part.trim();
                    if (!trimmed.isEmpty()) {
                        parts.add(trimmed);
                    }
                }
            }
            for (String part : parts) {
                if (part.equalsIgnoreCase(token)) {
                    return;
                }
            }
            parts.add(token);
            this.ruleExcludes.set(rule, String.join(" ", parts));
        }

        private PreviewScanResult scanPreviewRows(String filter, int maxRows) {
            List<PreviewRow> rows = new ArrayList<>();
            boolean fullyLoaded = true;
            for (Integer rule : getPreviewRules()) {
                if (!collectPreviewLines(rule, rows, filter, maxRows)) {
                    fullyLoaded = false;
                    break;
                }
            }
            return new PreviewScanResult(rows, fullyLoaded);
        }

        private static final class PreviewScanResult {
            private final List<PreviewRow> rows;
            private final boolean fullyLoaded;

            private PreviewScanResult(List<PreviewRow> rows, boolean fullyLoaded) {
                this.rows = rows;
                this.fullyLoaded = fullyLoaded;
            }
        }


        private void save() {
            ItemStack preview = buildStack(null);
            ItemStack held = getHeldStack();
            if (preview == null || held == null) {
                return;
            }
            WildcardPatternGenerator.markAsWildcard(held);
            WildcardPatternState.applyConfig(held, WildcardPatternState.exportConfig(preview));
            WildcardNetwork.CHANNEL.sendToServer(new MessageUpdateWildcardConfig(this.slot, WildcardPatternState.exportConfig(preview)));
        }

        private ItemStack buildStack(Integer onlyRule) {
            ItemStack held = getHeldStack();
            if (held == null) {
                return null;
            }
            ItemStack preview = held.copy();
            WildcardPatternGenerator.markAsWildcard(preview);

            if (onlyRule == null) {
                WildcardPatternState.setInputEntries(preview, this.inputs);
                WildcardPatternState.setOutputEntries(preview, this.outputs);
            } else {
                List<WildcardPatternEntry> inputs = new ArrayList<>();
                List<WildcardPatternEntry> outputs = new ArrayList<>();
                for (int i = 0; i < RULE_ROWS; i++) {
                    inputs.add(i == onlyRule.intValue() ? this.inputs.get(i) : WildcardPatternEntry.fromStack(null));
                    outputs.add(i == onlyRule.intValue() ? this.outputs.get(i) : WildcardPatternEntry.fromStack(null));
                }
                WildcardPatternState.setInputEntries(preview, inputs);
                WildcardPatternState.setOutputEntries(preview, outputs);
            }

            WildcardPatternConfig.apply(preview, this.globalExclude, this.ruleIncludes, this.ruleExcludes);
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

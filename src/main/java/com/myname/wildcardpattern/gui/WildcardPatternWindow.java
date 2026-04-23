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
    private static final int ENTRY_TEXT_WIDTH = 54;
    private static final int ENTRY_MODE_X = ENTRY_TEXT_WIDTH + 3;
    private static final int ENTRY_MODE_WIDTH = 30;
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

    private WildcardPatternWindow() {}

    public static ModularWindow createWindow(UIBuildContext buildContext, EntityPlayer player, int slot) {
        WindowState state = new WindowState(player, slot);
        ModularWindow.Builder builder = ModularWindow.builder(GUI_WIDTH, GUI_HEIGHT);
        builder.setBackground(ModularUITextures.VANILLA_BACKGROUND);

        addHeader(builder, state);
        addMainPage(builder, state);
        addPreviewPage(builder, state);
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
                : state.previewPage ? "gui.wildcardpattern.preview_page" : "gui.wildcardpattern.title"));
        builder.widget(title);

        TextWidget hint = new TextWidget("");
        hint.setPos(132, 11);
        hint.setSize(280, 10);
        hint.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY
            + tr(state.dedupePage
                ? "gui.wildcardpattern.dedupe_hint"
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
            state.rebuildPreview();
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
            state.rebuildPreview();
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
            state.rebuildPreview();
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
        final WildcardEntryDropTextField[] textRef = new WildcardEntryDropTextField[1];
        WildcardEntryDropTextField text = new WildcardEntryDropTextField(stack -> {
            WildcardPatternEntry next = WildcardPatternEntry.fromStack(stack);
            if (next.canOreDict()) {
                next.convertToOreDict();
            } else {
                next.convertToItem();
            }
            entries.set(index, next);
            state.rebuildPreview();
            if (textRef[0] != null) {
                textRef[0].setText(trim(next.getLabel(), 11));
                textRef[0].markForUpdate();
            }
        });
        textRef[0] = text;
        text.setSynced(false, false);
        text.setGetter(() -> entries.get(index).isEmpty() ? "" : trim(entries.get(index).getLabel(), 11));
        text.setSetter(value -> {
            entries.get(index).setMatcher(value);
            state.rebuildPreview();
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
            WildcardPatternEntry entry = entries.get(index);
            if (entry.isOreDict()) {
                entry.convertToItem();
            } else {
                entry.convertToOreDict();
            }
            text.setText(trim(entry.getLabel(), 11));
            text.markForUpdate();
            state.rebuildPreview();
        });
        addMainWidget(builder, state, mode);

        TextFieldWidget amount = new TextFieldWidget();
        amount.setSynced(false, false);
        amount.setGetter(() -> formatAmount(entries.get(index).getAmount()));
        amount.setSetter(value -> {
            entries.get(index).setAmount(parseAmount(value));
            state.rebuildPreview();
        });
        amount.setText(formatAmount(entries.get(index).getAmount()));
        amount.setTextColor(FIELD_TEXT_COLOR);
        amount.setBackground(WildcardPatternWindow::fieldBackground);
        amount.setTextAlignment(Alignment.Center);
        amount.setMaxLength(8);
        amount.setPos(x + ENTRY_AMOUNT_X, y);
        amount.setSize(ENTRY_AMOUNT_WIDTH, 16);
        addMainWidget(builder, state, amount);

        return new EntryCellRefs(text, amount, () -> formatAmount(entries.get(index).getAmount()));
    }

    private static void addGlobalExclude(ModularWindow.Builder builder, WindowState state, int x, int y) {
        addPanel(builder, state, x, y, 148, 40, false);
        addMainText(builder, state, EnumChatFormatting.BLACK + tr("gui.wildcardpattern.global_exclude"), x + 12, y + 10);

        TextFieldWidget field = new WildcardFilterDropTextField(value -> {
            state.globalExclude = value == null ? "" : value;
            state.rebuildPreview();
        });
        field.setSynced(false, false);
        field.setGetter(() -> state.globalExclude);
        field.setText(state.globalExclude);
        field.setSetter(value -> {
            state.globalExclude = value == null ? "" : value;
            state.rebuildPreview();
        });
        field.setTextColor(FIELD_TEXT_COLOR);
        field.setBackground(WildcardPatternWindow::fieldBackground);
        field.setTextAlignment(Alignment.CenterLeft);
        field.setMaxLength(256);
        field.setPos(x + 62, y + 7);
        field.setSize(76, 17);
        addMainWidget(builder, state, field);
    }

    private static void addRuleExcludeEditor(ModularWindow.Builder builder, WindowState state, int x, int y) {
        addPanel(builder, state, x, y, 138, 40, false);
        TextWidget label = new TextWidget("");
        label.setPos(x + 8, y + 10);
        label.setStringSupplier(() -> EnumChatFormatting.BLACK
            + StatCollector.translateToLocalFormatted("gui.wildcardpattern.rule_exclude", state.selectedRule + 1));
        addMainWidget(builder, state, label);

        TextFieldWidget field = new WildcardFilterDropTextField(value -> {
            state.ruleExcludes.set(state.selectedRule, value == null ? "" : value);
            state.rebuildPreview();
        });
        field.setSynced(false, false);
        field.setGetter(() -> state.ruleExcludes.get(state.selectedRule));
        field.setSetter(value -> {
            state.ruleExcludes.set(state.selectedRule, value == null ? "" : value);
            state.rebuildPreview();
        });
        field.setText(state.ruleExcludes.get(state.selectedRule));
        field.setTextColor(FIELD_TEXT_COLOR);
        field.setBackground(WildcardPatternWindow::fieldBackground);
        field.setTextAlignment(Alignment.CenterLeft);
        field.setMaxLength(256);
        field.setPos(x + 64, y + 7);
        field.setSize(64, 17);
        addMainWidget(builder, state, field);
    }

    private static void addPreviewPage(ModularWindow.Builder builder, WindowState state) {
        addPanel(builder, state, 8, 28, 436, 262, true);
        addSeparator(builder, state, 18, 81, 406, 2, true);

        TextWidget source = new TextWidget("");
        source.setPos(18, 38);
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
                state.rebuildPreview();
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
                    state.rebuildPreview();
                },
                18,
                58,
                206,
                true);

            addPreviewTextField(
                builder,
                state,
                EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.exclude_short"),
                () -> state.ruleExcludes.get(state.previewRule),
                value -> {
                    state.ruleExcludes.set(state.previewRule, value == null ? "" : value);
                    state.previewPageIndex = 0;
                    state.rebuildPreview();
                },
                226,
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
                return absolute < state.previewLines.size() ? EnumChatFormatting.DARK_GRAY + state.previewLines.get(absolute) : "";
            });
            addPreviewWidget(builder, state, line);

            ButtonWidget exclude = button("gui.wildcardpattern.exclude_short");
            exclude.setPos(400, 86 + i * 14);
            exclude.setSize(34, 12);
            exclude.setOnClick((clickData, widget) -> {
                int absolute = state.previewPageIndex * PREVIEW_LINES + lineIndex;
                if (absolute < state.previewMaterialNames.size()) {
                    state.excludeMaterial(state.previewMaterialNames.get(absolute));
                }
            });
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
        prev.setOnClick((clickData, widget) -> {
            if (state.previewPageIndex > 0) {
                state.previewPageIndex--;
            }
        });
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
        next.setOnClick((clickData, widget) -> {
            if (state.previewPageIndex + 1 < state.getPreviewPageCount()) {
                state.previewPageIndex++;
            }
        });
        addPreviewWidget(builder, state, next);
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

        for (int i = 0; i < 10; i++) {
            final int lineIndex = i;
            TextWidget oreName = new TextWidget("");
            oreName.setPos(18, 102 + i * 16);
            oreName.setStringSupplier(() -> {
                int absolute = state.dedupePageIndex * 10 + lineIndex;
                return absolute < state.dedupeOreNames.size()
                    ? EnumChatFormatting.BLACK + state.dedupeOreNames.get(absolute)
                    : "";
            });
            addDedupeWidget(builder, state, oreName);

            TextWidget selected = new TextWidget("");
            selected.setPos(150, 102 + i * 16);
            selected.setStringSupplier(() -> {
                int absolute = state.dedupePageIndex * 10 + lineIndex;
                if (absolute >= state.dedupeOreNames.size()) {
                    return "";
                }
                return EnumChatFormatting.DARK_GRAY + trim(state.getSelectedDedupeLabel(state.dedupeOreNames.get(absolute)), 24);
            });
            addDedupeWidget(builder, state, selected);

            ButtonWidget cycle = button("gui.wildcardpattern.dedupe_cycle");
            cycle.setPos(360, 98 + i * 16);
            cycle.setSize(64, 14);
            cycle.setOnClick((clickData, widget) -> {
                int absolute = state.dedupePageIndex * 10 + lineIndex;
                if (absolute < state.dedupeOreNames.size()) {
                    state.cycleDedupeChoice(state.dedupeOreNames.get(absolute));
                }
            });
            addDedupeWidget(builder, state, cycle);
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
        ButtonWidget button = new ButtonWidget();
        button.setSynced(false, false);
        button.setBackground(backgroundSupplier::get);
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

    private static void addPageWidget(ModularWindow.Builder builder, WindowState state, Widget widget, boolean previewPage) {
        widget.setEnabled(w -> !state.dedupePage && state.previewPage == previewPage);
        builder.widget(widget);
    }

    private static String summarize(ICraftingPatternDetails details) {
        return trim(join(details.getInputs()) + " -> " + join(details.getOutputs()), 64);
    }

    private static String join(appeng.api.storage.data.IAEItemStack[] stacks) {
        StringBuilder builder = new StringBuilder();
        if (stacks == null) {
            return "";
        }
        for (appeng.api.storage.data.IAEItemStack stack : stacks) {
            if (stack == null || stack.getItemStack() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" + ");
            }
            builder.append(stack.getItemStack().stackSize).append('x').append(stack.getItemStack().getDisplayName());
        }
        return builder.toString();
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

    private static String formatAmount(int amount) {
        if (amount >= 1_000_000) {
            return formatCompact(amount, 1_000_000, "m");
        }
        if (amount >= 1_000) {
            return formatCompact(amount, 1_000, "k");
        }
        return String.valueOf(Math.max(1, amount));
    }

    private static String formatCompact(int amount, int unit, String suffix) {
        if (amount % unit == 0) {
            return (amount / unit) + suffix;
        }
        long scaled = Math.round((double) amount * 10D / unit);
        if (scaled % 10 == 0) {
            return (scaled / 10) + suffix;
        }
        return (scaled / 10) + "." + (scaled % 10) + suffix;
    }

    private static int parseAmount(String value) {
        String token = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (token.isEmpty()) {
            return 1;
        }
        int multiplier = 1;
        if (token.endsWith("k")) {
            multiplier = 1_000;
            token = token.substring(0, token.length() - 1).trim();
        } else if (token.endsWith("m")) {
            multiplier = 1_000_000;
            token = token.substring(0, token.length() - 1).trim();
        }
        try {
            double parsed = Double.parseDouble(token);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return 1;
            }
            long result = Math.round(parsed * multiplier);
            return (int) Math.max(1L, Math.min((long) Integer.MAX_VALUE, result));
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

    private static final class WindowState {
        private final EntityPlayer player;
        private final int slot;
        private final List<WildcardPatternEntry> inputs;
        private final List<WildcardPatternEntry> outputs;
        private final List<String> ruleIncludes;
        private final List<String> ruleExcludes;
        private final List<String> previewLines = new ArrayList<>();
        private final List<String> previewMaterialNames = new ArrayList<>();
        private final java.util.Map<String, ItemStack> preferredOreStacks = new java.util.LinkedHashMap<>();
        private final List<String> dedupeOreNames = new ArrayList<>();

        private String globalExclude;
        private String previewSearch = "";
        private String dedupeSearch = "";
        private boolean previewPage;
        private boolean dedupePage;
        private int selectedRule;
        private int previewRule = -1;
        private int previewPageIndex;
        private int dedupePageIndex;

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
            rebuildPreview();
        }

        private void openPreview(int rule) {
            this.dedupePage = false;
            this.previewRule = rule;
            this.previewPageIndex = 0;
            this.previewPage = true;
            rebuildPreview();
        }

        private void openDedupe() {
            this.previewPage = false;
            this.dedupePage = true;
            this.dedupePageIndex = 0;
            rebuildDedupe();
        }

        private String getPreviewTitle() {
            if (this.previewRule >= 0) {
                return StatCollector.translateToLocalFormatted("gui.wildcardpattern.preview_rule", this.previewRule + 1);
            }
            return tr("gui.wildcardpattern.preview_all");
        }

        private int getPreviewPageCount() {
            return Math.max(1, (this.previewLines.size() + PREVIEW_LINES - 1) / PREVIEW_LINES);
        }

        private int getDedupePageCount() {
            return Math.max(1, (this.dedupeOreNames.size() + 9) / 10);
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
            rebuildPreview();
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
            this.previewLines.clear();
            this.previewMaterialNames.clear();

            if (this.previewRule >= 0) {
                collectPreviewLines(this.previewRule);
            } else {
                for (int rule = 0; rule < RULE_ROWS; rule++) {
                    collectPreviewLines(rule);
                }
            }
            if (this.previewPageIndex >= getPreviewPageCount()) {
                this.previewPageIndex = Math.max(0, getPreviewPageCount() - 1);
            }
        }

        private void rebuildDedupe() {
            this.dedupeOreNames.clear();
            ItemStack preview = buildStack(null);
            if (preview == null) {
                return;
            }
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            for (int rule = 0; rule < RULE_ROWS; rule++) {
                collectDuplicateOreNames(preview, names, this.inputs.get(rule), rule);
                collectDuplicateOreNames(preview, names, this.outputs.get(rule), rule);
            }
            String filter = this.dedupeSearch == null ? "" : this.dedupeSearch.trim().toLowerCase(java.util.Locale.ROOT);
            for (String oreName : names) {
                if (filter.isEmpty()) {
                    this.dedupeOreNames.add(oreName);
                    continue;
                }
                String selected = getSelectedDedupeLabel(oreName).toLowerCase(java.util.Locale.ROOT);
                if (oreName.toLowerCase(java.util.Locale.ROOT).contains(filter) || selected.contains(filter)) {
                    this.dedupeOreNames.add(oreName);
                }
            }
            if (this.dedupePageIndex >= getDedupePageCount()) {
                this.dedupePageIndex = Math.max(0, getDedupePageCount() - 1);
            }
        }

        private void collectDuplicateOreNames(
            ItemStack preview,
            java.util.Set<String> result,
            WildcardPatternEntry entry,
            int rule) {
            if (entry == null || entry.isEmpty()) {
                return;
            }
            ItemData association = GTOreDictUnificator.getAssociation(entry.getDisplayStack());
            if (association == null || !association.hasValidPrefixMaterialData()) {
                return;
            }
            for (Materials material : WildcardPatternGenerator.getCandidateMaterials(preview, rule)) {
                String oreName = getPrefixName(association.mPrefix) + material.mName;
                if (OreDictionary.getOres(oreName) != null && OreDictionary.getOres(oreName).size() > 1) {
                    result.add(oreName);
                }
            }
        }

        private String getSelectedDedupeLabel(String oreName) {
            java.util.List<ItemStack> options = OreDictionary.getOres(oreName);
            if (options == null || options.isEmpty()) {
                return tr("gui.wildcardpattern.preview_empty");
            }
            ItemStack current = this.preferredOreStacks.get(oreName);
            ItemStack target = current != null ? current : getDefaultDedupeChoice(options);
            return formatDedupeChoice(target);
        }

        private void cycleDedupeChoice(String oreName) {
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

        private void collectPreviewLines(int rule) {
            ItemStack preview = buildStack(rule);
            List<ICraftingPatternDetails> details = preview == null
                ? new ArrayList<>()
                : WildcardPatternGenerator.generateRuleDetails(preview, this.player.worldObj, rule);
            List<Materials> materials = preview == null
                ? new ArrayList<>()
                : WildcardPatternGenerator.getCandidateMaterials(preview, rule);

            String filter = this.previewSearch == null ? "" : this.previewSearch.trim().toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < details.size(); i++) {
                String materialName = i < materials.size() ? materials.get(i).mName : "";
                String line = "R" + (rule + 1) + " " + summarize(details.get(i));
                if (!filter.isEmpty() && !line.toLowerCase(java.util.Locale.ROOT).contains(filter)
                    && !materialName.toLowerCase(java.util.Locale.ROOT).contains(filter)) {
                    continue;
                }
                this.previewLines.add(line);
                this.previewMaterialNames.add(materialName);
            }
        }

        private void excludeMaterial(String materialName) {
            if (materialName == null || materialName.trim().isEmpty()) {
                return;
            }
            String normalized = materialName.trim().toLowerCase(java.util.Locale.ROOT);
            String current = this.globalExclude == null ? "" : this.globalExclude.trim();
            for (String part : current.split("[,;锛岋紱\\s]+")) {
                if (part.equalsIgnoreCase(normalized)) {
                    rebuildPreview();
                    return;
                }
            }
            this.globalExclude = current.isEmpty() ? normalized : current + "," + normalized;
            rebuildPreview();
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

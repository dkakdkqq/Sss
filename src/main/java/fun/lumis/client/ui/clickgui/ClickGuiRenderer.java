package fun.lumis.client.ui.clickgui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.MathHelper;
import fun.lumis.Lumis;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.input.KeyBoardUtils;
import fun.lumis.api.utils.math.HoveringUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.api.utils.scissor.ScissorUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.Setting;

import java.util.ArrayList;
import java.util.List;

public class ClickGuiRenderer {
    private final ClickGuiState state;
    private final ClickGuiSettingRenderer settingRenderer;
    private final ClickGuiThemeSelector themeSelector;

    public ClickGuiRenderer(ClickGuiState state, ClickGuiSettingRenderer settingRenderer, ClickGuiThemeSelector themeSelector) {
        this.state = state;
        this.settingRenderer = settingRenderer;
        this.themeSelector = themeSelector;
    }

    public void render(DrawContext context, int mouseX, int mouseY, Window window, float animationProgress) {
        if (window == null) {
            return;
        }

        float alphaMul = MathHelper.clamp(animationProgress, 0.0f, 1.0f);
        int shadeColor = getFadeShadeColor(alphaMul, 120);
        int colorTheme = getThemeColor();
        Module hoveredModule = null;

        Module.ModuleCategory[] categories = Module.ModuleCategory.values();
        for (int i = 0; i < categories.length; i++) {
            Module.ModuleCategory category = categories[i];
            float panelX = ClickGuiLayout.getCategoryPanelX(state.getX(), i);
            Module categoryHoveredModule = renderCategoryPanel(context, mouseX, mouseY, panelX, category, colorTheme, alphaMul, shadeColor);
            if (categoryHoveredModule != null) {
                hoveredModule = categoryHoveredModule;
            }
        }

        renderSearch(context, categories.length, colorTheme, alphaMul, getFadeShadeColor(alphaMul, 95));
        themeSelector.render(context, window, state.getRenderOffsetY(), alphaMul, getFadeShadeColor(alphaMul, 95));
        renderDescription(context, window, hoveredModule, colorTheme, animationProgress);
    }

    private Module renderCategoryPanel(DrawContext context, int mouseX, int mouseY, float panelX, Module.ModuleCategory category, int colorTheme, float alphaMul, int shadeColor) {
        float panelY = state.getY() + state.getRenderOffsetY();
        RenderUtils.drawRoundedRect(context.getMatrices(), panelX, panelY, ClickGuiLayout.WIDTH, ClickGuiLayout.HEIGHT, 8, ColorUtils.darken(colorTheme, 0.07f));
        RenderUtils.drawRoundedRect(context.getMatrices(), panelX, panelY + 23, ClickGuiLayout.WIDTH, 0.5F, 0, ColorUtils.rgb(19, 18, 24));
        if (((shadeColor >> 24) & 0xFF) > 0) {
            RenderUtils.drawRoundedRect(context.getMatrices(), panelX, panelY, ClickGuiLayout.WIDTH, ClickGuiLayout.HEIGHT, 8, shadeColor);
        }

        icons(14).drawCenteredString(context.getMatrices(), category.getIcons(), panelX + 50 - (issue(15).getWidth(category.getName()) / 2F) - 4, panelY + 10, alpha(colorTheme, alphaMul));
        issue(15).drawCenteredString(context.getMatrices(), category.getName(), panelX + 52, panelY + 9, alpha(-1, alphaMul));

        float contentY = ClickGuiLayout.getContentY(panelY);
        float contentHeight = ClickGuiLayout.getContentHeight();
        state.clampScroll(category, contentHeight);
        float moduleY = contentY + state.getScroll(category);
        Module hoveredModule = null;

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(panelX, contentY, ClickGuiLayout.WIDTH, contentHeight);

        for (Module module : state.getModules(category)) {
            float openProgress = state.getOpenProgress(module);
            float moduleHeight = ClickGuiLayout.getModuleHeight(module, openProgress);

            if (moduleY + moduleHeight + ClickGuiLayout.MODULE_GAP >= contentY && moduleY <= contentY + contentHeight) {
                Module moduleHover = renderModule(context, mouseX, mouseY, panelX, moduleY, module, openProgress, moduleHeight, colorTheme, alphaMul, shadeColor);
                if (moduleHover != null) {
                    hoveredModule = moduleHover;
                }
            }

            moduleY += ClickGuiLayout.MODULE_GAP + moduleHeight;
        }

        ScissorUtils.pop();
        return hoveredModule;
    }

    private Module renderModule(DrawContext context, int mouseX, int mouseY, float panelX, float moduleY, Module module, float openProgress, float moduleHeight, int colorTheme, float alphaMul, int shadeColor) {
        List<Setting> settings = module.getSettings();
        renderModuleBackground(context, panelX, moduleY, moduleHeight, module.isEnable(), colorTheme, shadeColor);

        String moduleName = module.getName();
        String bindText = "";
        if (state.getBindingModule() == module) {
            bindText = " [...]";
        } else if (module.getKey() != -1) {
            bindText = " [" + state.toEnglish(KeyBoardUtils.getBindName(module.getKey())) + "]";
        }

        int nameColor = module.isEnable() ? alpha(-1, alphaMul) : alpha(ColorUtils.rgba(255, 255, 255, 170), alphaMul);
        int bindColor = module.isEnable() ? alpha(ColorUtils.rgba(255, 255, 255, 150), alphaMul) : alpha(ColorUtils.rgba(255, 255, 255, 100), alphaMul);

        issue(14).draw(context.getMatrices(), moduleName, panelX + ClickGuiLayout.SETTING_LEFT, moduleY + 8, nameColor);
        if (!bindText.isEmpty()) {
            float nameWidth = issue(14).getWidth(moduleName);
            issue(11).draw(context.getMatrices(), bindText, panelX + ClickGuiLayout.SETTING_LEFT + nameWidth, moduleY + 9, bindColor);
        }

        if (settings != null && !settings.isEmpty() && ClickGuiLayout.hasVisibleSettings(settings)) {
            renderModuleDots(context, panelX, moduleY, module, module.isEnable(), alphaMul);
        }

        if (settings != null && !settings.isEmpty()) {
            settingRenderer.render(context, module, panelX, moduleY, openProgress, colorTheme, mouseX, mouseY, state);
        }

        if (HoveringUtils.isHovered(mouseX, mouseY, panelX + ClickGuiLayout.MODULE_PADDING, moduleY, ClickGuiLayout.MODULE_INNER_WIDTH, moduleHeight)) {
            return module;
        }
        return null;
    }

    private void renderModuleBackground(DrawContext context, float panelX, float moduleY, float moduleHeight, boolean enabled, int colorTheme, int shadeColor) {
        if (enabled) {
            RenderUtils.drawRoundedRect(context.getMatrices(), panelX + ClickGuiLayout.MODULE_PADDING, moduleY - 0.5f, ClickGuiLayout.MODULE_INNER_WIDTH, moduleHeight + 1, 5, ColorUtils.darken(colorTheme, 0.17f));
            RenderUtils.drawGradientRect(context.getMatrices(), panelX + ClickGuiLayout.MODULE_PADDING + 0.5f, moduleY, ClickGuiLayout.MODULE_INNER_WIDTH - 1f, moduleHeight, 4, ColorUtils.darken(colorTheme, 0.15f), ColorUtils.darken(colorTheme, 0.1f), false);
            if (((shadeColor >> 24) & 0xFF) > 0) {
                RenderUtils.drawRoundedRect(context.getMatrices(), panelX + ClickGuiLayout.MODULE_PADDING + 0.5f, moduleY, ClickGuiLayout.MODULE_INNER_WIDTH - 1f, moduleHeight, 4, shadeColor);
            }
            return;
        }

        RenderUtils.drawRoundedRect(context.getMatrices(), panelX + ClickGuiLayout.MODULE_PADDING, moduleY - 0.5f, ClickGuiLayout.MODULE_INNER_WIDTH, moduleHeight + 1, 5, ColorUtils.darken(colorTheme, 0.10f));
        RenderUtils.drawGradientRect(context.getMatrices(), panelX + ClickGuiLayout.MODULE_PADDING + 0.5f, moduleY, ClickGuiLayout.MODULE_INNER_WIDTH - 1f, moduleHeight, 4, ColorUtils.darken(colorTheme, 0.09f), ColorUtils.darken(colorTheme, 0.08f), false);
        if (((shadeColor >> 24) & 0xFF) > 0) {
            RenderUtils.drawRoundedRect(context.getMatrices(), panelX + ClickGuiLayout.MODULE_PADDING + 0.5f, moduleY, ClickGuiLayout.MODULE_INNER_WIDTH - 1f, moduleHeight, 4, shadeColor);
        }
    }

    private void renderModuleDots(DrawContext context, float panelX, float moduleY, Module module, boolean enabled, float alphaMul) {
        int dotsColor = enabled ? alpha(ColorUtils.rgba(255, 255, 255, 220), alphaMul) : alpha(ColorUtils.rgba(255, 255, 255, 100), alphaMul);
        float dotsX = panelX + 87.5f;
        float baseY = moduleY + 10f;
        float spacing = 2f;
        float radius = 2.1f;
        float bottomXOffset = 2.1f;
        float angle = state.updateDotsRotation(module, module.isOpen() ? (float) (Math.PI / 2f) : 0f);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float[][] offsets = {
                {0f, -spacing},
                {-bottomXOffset, spacing},
                {bottomXOffset, spacing}
        };

        for (float[] offset : offsets) {
            float rx = offset[0] * cos - offset[1] * sin;
            float ry = offset[0] * sin + offset[1] * cos;
            RenderUtils.drawRoundCircle(context.getMatrices(), dotsX + rx, baseY + ry, radius, dotsColor);
        }
    }

    private int getThemeColor() {
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            return Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        }
        return ColorUtils.getThemeColor();
    }

    private void renderSearch(DrawContext context, int categoryCount, int colorTheme, float alphaMul, int shadeColor) {
        float searchY = ClickGuiLayout.getSearchY(state.getY() + state.getRenderOffsetY());
        float searchW = getSearchWidth();
        float searchX = ClickGuiLayout.getSearchX(state.getX(), categoryCount, searchW);
        float searchH = ClickGuiLayout.SEARCH_HEIGHT;
        float selectionPaddingLeft = 3.0f;
        float selectionPaddingRight = 1.5f;
        int borderColor = ColorUtils.darken(colorTheme, 0.12f);

        RenderUtils.drawRoundedRect(context.getMatrices(), searchX - 0.5f, searchY - 0.5f, searchW + 1f, searchH + 1f, 5.5f, borderColor);
        RenderUtils.drawGradientRect(context.getMatrices(), searchX, searchY, searchW, searchH, 5f,
                ColorUtils.darken(colorTheme, 0.12f),
                ColorUtils.darken(colorTheme, 0.08f), false);
        if (((shadeColor >> 24) & 0xFF) > 0) {
            RenderUtils.drawRoundedRect(context.getMatrices(), searchX, searchY, searchW, searchH, 5f, shadeColor);
        }

        String query = state.getSearchText();
        String text = query.isEmpty() ? "Search..." : query;
        int textColor = query.isEmpty()
                ? alpha(ColorUtils.rgba(255, 255, 255, 110), alphaMul)
                : alpha(ColorUtils.rgba(255, 255, 255, 230), alphaMul);

        float iconX = searchX + ClickGuiLayout.SEARCH_ICON_X;
        float textX = searchX + ClickGuiLayout.SEARCH_TEXT_X;
        float textY = searchY + 6.2f;
        iconsNew(18).drawGradientStringHorizontal(context.getMatrices(), "l", iconX + 2, searchY + 6.5f, alpha(colorTheme, alphaMul), alpha(colorTheme, alphaMul));

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(
                textX - selectionPaddingLeft,
                searchY,
                searchW - ClickGuiLayout.SEARCH_TEXT_X - ClickGuiLayout.SEARCH_RIGHT_PADDING + selectionPaddingLeft,
                searchH
        );
        if (!query.isEmpty() && state.hasSearchSelection()) {
            int selectionStart = state.getSearchSelectionStart();
            int selectionEnd = state.getSearchSelectionEnd();
            float selectedX = textX + issue(14).getWidth(query.substring(0, selectionStart)) - selectionPaddingLeft;
            float selectedW = issue(14).getWidth(query.substring(selectionStart, selectionEnd)) + selectionPaddingLeft + selectionPaddingRight;
            RenderUtils.drawRoundedRect(context.getMatrices(), selectedX, searchY + 3.8f, selectedW, 10.5f, 1.5f, alpha(ColorUtils.rgba(42, 115, 255, 155), alphaMul));
        }

        issue(14).draw(context.getMatrices(), text, textX, textY + 1, textColor);
        if (state.isSearchActive() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float cursorX = textX + issue(14).getWidth(query.substring(0, Math.min(state.getSearchCursor(), query.length())));
            RenderUtils.drawRoundedRect(context.getMatrices(), cursorX + 1f, searchY + 4.5f, 0.8f, 9f, 0f, alpha(ColorUtils.applyAlpha(colorTheme, 0.9f), alphaMul));
        }
        ScissorUtils.pop();
    }

    private void renderDescription(DrawContext context, Window window, Module hoveredModule, int colorTheme, float alphaMul) {
        if (hoveredModule == null) {
            return;
        }

        String description = hoveredModule.getDisplayDescription();
        if (description == null || description.isBlank() || "NULLABLE".equalsIgnoreCase(description) || "desc".equalsIgnoreCase(description)) {
            return;
        }

        Font descriptionFont = issue(16);
        float maxWidth = window.getScaledWidth() - 40.0f;
        List<String> lines = wrapDescription(descriptionFont, description, maxWidth);
        if (lines.isEmpty()) {
            return;
        }

        float lineHeight = descriptionFont.getHeight() - 2.0f;
        float boxHeight = lines.size() * lineHeight;
        float centerX = window.getScaledWidth() * 0.5f;
        float startY = ClickGuiLayout.THEME_PANEL_Y - boxHeight - 6.0f;

        for (int i = 0; i < lines.size(); i++) {
            descriptionFont.drawCenteredString(context.getMatrices(), lines.get(i), centerX, startY + i * lineHeight, ColorUtils.applyAlpha(-1, alphaMul));
        }
    }

    private List<String> wrapDescription(Font font, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.trim().split("\\s+");
        if (words.length == 0) {
            return lines;
        }

        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (font.getWidth(candidate) <= maxWidth || currentLine.isEmpty()) {
                currentLine.setLength(0);
                currentLine.append(candidate);
                continue;
            }

            lines.add(currentLine.toString());
            currentLine.setLength(0);
            currentLine.append(word);
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private float getSearchWidth() {
        String query = state.getSearchText();
        String text = query.isEmpty() ? "Search..." : query;
        float contentWidth = ClickGuiLayout.SEARCH_TEXT_X + issue(14).getWidth(text) + ClickGuiLayout.SEARCH_RIGHT_PADDING;
        return Math.max(ClickGuiLayout.SEARCH_WIDTH, contentWidth);
    }

    private Font issue(int size) {
        return Fonts.getFont("suisse", size);
    }

    private Font icons(int size) {
        return Fonts.getFont("icon", size);
    }

    private Font iconsNew(int size) {
        return Fonts.getFont("icon1", size);
    }

    private int alpha(int color, float alphaMul) {
        return ColorUtils.applyAlpha(color, alphaMul);
    }

    private int getFadeShadeColor(float alphaMul, int maxAlpha) {
        int alpha = MathHelper.clamp((int) ((1.0f - alphaMul) * maxAlpha), 0, 255);
        return ColorUtils.rgba(0, 0, 0, alpha);
    }
}

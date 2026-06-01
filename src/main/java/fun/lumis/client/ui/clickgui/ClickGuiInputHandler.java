package fun.lumis.client.ui.clickgui;

import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import fun.lumis.api.QClient;
import fun.lumis.api.utils.input.KeyBoardUtils;
import fun.lumis.api.utils.math.HoveringUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.Setting;
import fun.lumis.client.modules.settings.implement.BindSetting;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;
import fun.lumis.client.modules.settings.implement.TextSetting;

import java.util.List;

public class ClickGuiInputHandler implements QClient {
    private final ClickGuiState state;
    private final ClickGuiThemeSelector themeSelector;

    public ClickGuiInputHandler(ClickGuiState state, ClickGuiThemeSelector themeSelector) {
        this.state = state;
        this.themeSelector = themeSelector;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, Window window) {
        if (window != null && button == 0) {
            int categoryCount = Module.ModuleCategory.values().length;
            float searchW = getSearchWidth();
            float searchX = ClickGuiLayout.getSearchX(state.getX(), categoryCount, searchW);
            float searchY = ClickGuiLayout.getSearchY(state.getY() + state.getRenderOffsetY());
            boolean searchHovered = HoveringUtils.isHovered(mouseX, mouseY, searchX, searchY, searchW, ClickGuiLayout.SEARCH_HEIGHT);
            state.setSearchActive(searchHovered);
            if (searchHovered) {
                state.setEditingTextSetting(null);
                state.startSearchSelection(getSearchIndexAt(mouseX, searchX));
                return true;
            }
        }

        if (state.getBindingModule() != null && button >= 2) {
            state.getBindingModule().setKey(KeyBoardUtils.createMouseBind(button));
            state.setBindingModule(null);
            return true;
        }

        if (state.getBindingSetting() != null && button >= 2) {
            state.getBindingSetting().setKey(KeyBoardUtils.createMouseBind(button));
            state.setBindingSetting(null);
            return true;
        }

        state.setEditingTextSetting(null);

        if (themeSelector.handleClick(window, mouseX, mouseY, button, state.getRenderOffsetY())) {
            return true;
        }

        Module.ModuleCategory[] categories = Module.ModuleCategory.values();
        for (int i = 0; i < categories.length; i++) {
            Module.ModuleCategory category = categories[i];
            float panelX = ClickGuiLayout.getCategoryPanelX(state.getX(), i);
            float contentY = ClickGuiLayout.getContentY(state.getY() + state.getRenderOffsetY());
            float contentHeight = ClickGuiLayout.getContentHeight();

            if (!HoveringUtils.isHovered(mouseX, mouseY, panelX, contentY, ClickGuiLayout.WIDTH, contentHeight)) {
                continue;
            }

            float moduleY = contentY + state.getScroll(category);
            for (Module module : state.getModules(category)) {
                float openProgress = state.getOpenProgress(module);
                float moduleHeight = ClickGuiLayout.getModuleHeight(module, openProgress);
                if (HoveringUtils.isHovered(mouseX, mouseY, panelX + ClickGuiLayout.MODULE_PADDING, moduleY, ClickGuiLayout.MODULE_INNER_WIDTH, ClickGuiLayout.MODULE_HEADER_HEIGHT)) {
                    if (button == 0) {
                        module.toggle();
                        return true;
                    }
                    if (button == 1) {
                        module.setOpen(!module.isOpen());
                        state.clampScroll(category, contentHeight);
                        return true;
                    }
                    if (button == 2) {
                        state.setBindingModule(module);
                        return true;
                    }
                    return true;
                }

                if (module.isOpen() && openProgress > 0.1f) {
                    List<Setting> settings = module.getSettings();
                    if (settings != null && handleSettingClick(mouseX, mouseY, button, panelX, moduleY, settings)) {
                        return true;
                    }
                }

                moduleY += ClickGuiLayout.MODULE_GAP + moduleHeight;
            }
        }

        return false;
    }

    public boolean mouseReleased(int button) {
        state.stopSearchSelection();
        if (button == 0) {
            for (Module module : state.getAllModules()) {
                List<Setting> settings = module.getSettings();
                if (settings == null) {
                    continue;
                }
                for (Setting setting : settings) {
                    if (setting instanceof FloatSetting floatSetting) {
                        floatSetting.setActive(false);
                        state.endSliderDrag(floatSetting);
                    }
                }
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0 || !state.isSearchActive() || !state.isSearchDragging()) {
            return false;
        }

        int categoryCount = Module.ModuleCategory.values().length;
        float searchX = ClickGuiLayout.getSearchX(state.getX(), categoryCount, getSearchWidth());
        state.updateSearchSelection(getSearchIndexAt(mouseX, searchX));
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        Module.ModuleCategory[] categories = Module.ModuleCategory.values();
        for (int i = 0; i < categories.length; i++) {
            Module.ModuleCategory category = categories[i];
            float panelX = ClickGuiLayout.getCategoryPanelX(state.getX(), i);
            float contentY = ClickGuiLayout.getContentY(state.getY() + state.getRenderOffsetY());
            float contentHeight = ClickGuiLayout.getContentHeight();
            if (HoveringUtils.isHovered(mouseX, mouseY, panelX, contentY, ClickGuiLayout.WIDTH, contentHeight)) {
                state.addScroll(category, verticalAmount, contentHeight);
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        if (state.getEditingTextSetting() != null) {
            TextSetting textSetting = state.getEditingTextSetting();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                state.setEditingTextSetting(null);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String current = textSetting.get();
                if (current != null && !current.isEmpty()) {
                    textSetting.setText(current.substring(0, current.length() - 1));
                }
                return true;
            }
            return true;
        }

        if (state.isSearchActive()) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                if (keyCode == GLFW.GLFW_KEY_A) {
                    state.selectAllSearchText();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_C) {
                    if (state.hasSearchSelection() && mc != null && mc.keyboard != null) {
                        mc.keyboard.setClipboard(state.getSelectedSearchText());
                    }
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_V) {
                    if (mc != null && mc.keyboard != null) {
                        state.replaceSearchSelection(mc.keyboard.getClipboard());
                    }
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_Z) {
                    state.restoreSearchUndo();
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                state.setSearchActive(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                state.removeLastSearchChar();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                state.clearSearchText();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                state.setSearchCursor(state.getSearchCursor() - 1, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                state.setSearchCursor(state.getSearchCursor() + 1, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
                return true;
            }
        }

        if (state.getBindingModule() != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                state.setBindingModule(null);
            } else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                state.getBindingModule().setKey(-1);
                state.setBindingModule(null);
            } else {
                state.getBindingModule().setKey(keyCode);
                state.setBindingModule(null);
            }
            return true;
        }

        if (state.getBindingSetting() != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                state.setBindingSetting(null);
            } else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                state.getBindingSetting().setKey(-1);
                state.setBindingSetting(null);
            } else {
                state.getBindingSetting().setKey(keyCode);
                state.setBindingSetting(null);
            }
            return true;
        }

        return false;
    }

    public boolean charTyped(char chr) {
        if (state.getEditingTextSetting() != null) {
            if (!Character.isISOControl(chr)) {
                TextSetting textSetting = state.getEditingTextSetting();
                textSetting.setText(textSetting.get() + chr);
            }
            return true;
        }

        if (!state.isSearchActive()) {
            return false;
        }
        state.appendSearchChar(chr);
        return true;
    }

    private int getSearchIndexAt(double mouseX, float searchX) {
        String text = state.getSearchText();
        float textX = searchX + ClickGuiLayout.SEARCH_TEXT_X;
        float localX = (float) mouseX - textX;
        if (localX <= 0f || text.isEmpty()) {
            return 0;
        }

        for (int i = 1; i <= text.length(); i++) {
            float previousWidth = issue(14).getWidth(text.substring(0, i - 1));
            float currentWidth = issue(14).getWidth(text.substring(0, i));
            float midpoint = previousWidth + (currentWidth - previousWidth) * 0.5f;
            if (localX < midpoint) {
                return i - 1;
            }
        }
        return text.length();
    }

    private float getSearchWidth() {
        String query = state.getSearchText();
        String text = query.isEmpty() ? "Search..." : query;
        float contentWidth = ClickGuiLayout.SEARCH_TEXT_X + issue(14).getWidth(text) + ClickGuiLayout.SEARCH_RIGHT_PADDING;
        return Math.max(ClickGuiLayout.SEARCH_WIDTH, contentWidth);
    }

    private boolean handleSettingClick(double mouseX, double mouseY, int button, float panelX, float moduleY, List<Setting> settings) {
        float settingYoffset = ClickGuiLayout.SETTING_START_Y;
        for (Setting setting : settings) {
            if (setting == null || !setting.visible()) {
                continue;
            }

            float settingY = moduleY + settingYoffset + ClickGuiLayout.SETTING_PADDING;
            if (setting instanceof BooleanSetting booleanSetting) {
                if (button == 0 && HoveringUtils.isHovered(mouseX, mouseY, panelX + 75, settingY - 2, 16, 10)) {
                    booleanSetting.setState(!booleanSetting.isState());
                    return true;
                }
                settingYoffset += 12f;
            } else if (setting instanceof TextSetting textSetting) {
                float boxWidth = ClickGuiLayout.TEXT_SETTING_WIDTH;
                float boxX = panelX + 49f;
                if (button == 0 && HoveringUtils.isHovered(mouseX, mouseY, boxX, settingY - 2.5f, boxWidth, 9)) {
                    state.setSearchActive(false);
                    state.stopSearchSelection();
                    state.setEditingTextSetting(textSetting);
                    return true;
                }
                settingYoffset += 12f;
            } else if (setting instanceof FloatSetting floatSetting) {
                if (button == 0 && HoveringUtils.isHovered(mouseX, mouseY, panelX + ClickGuiLayout.SETTING_LEFT, settingY + 9, ClickGuiLayout.SLIDER_WIDTH, 6)) {
                    floatSetting.setActive(true);
                    floatSetting.setValue(state.getSliderValue(floatSetting, panelX + ClickGuiLayout.SETTING_LEFT, mouseX));
                    state.beginSliderDrag(floatSetting, mouseX);
                    return true;
                }
                settingYoffset += 22f;
            } else if (setting instanceof ModeSetting modeSetting) {
                float modeY = settingY + 10;
                for (String mode : modeSetting.getMods()) {
                    if (button == 0 && HoveringUtils.isHovered(mouseX, mouseY, panelX + ClickGuiLayout.SETTING_LEFT, modeY - 2, ClickGuiLayout.CLICKABLE_WIDTH, 10)) {
                        modeSetting.set(mode);
                        return true;
                    }
                    modeY += 10f;
                }
                settingYoffset += ClickGuiLayout.calculateModeSettingHeight(modeSetting);
            } else if (setting instanceof ListSetting listSetting) {
                float listY = settingY + 10;
                for (BooleanSetting entry : listSetting.getSettings()) {
                    if (!entry.visible()) {
                        continue;
                    }
                    if (button == 0 && HoveringUtils.isHovered(mouseX, mouseY, panelX + ClickGuiLayout.SETTING_LEFT, listY - 2, ClickGuiLayout.CLICKABLE_WIDTH, 10)) {
                        entry.setState(!entry.isState());
                        return true;
                    }
                    listY += 10f;
                }
                settingYoffset += ClickGuiLayout.calculateListSettingHeight(listSetting);
            } else if (setting instanceof BindSetting bindSetting) {
                String bindString = state.getBindingSetting() == bindSetting ? "..." : state.toEnglish(KeyBoardUtils.getBindName(bindSetting.getKey()));
                float bindWidth = issue(12).getWidth(bindString) + 6f;
                float bindX = panelX + ClickGuiLayout.SETTING_RIGHT - bindWidth;
                if (button == 0 && HoveringUtils.isHovered(mouseX, mouseY, bindX, settingY - 2.5f, bindWidth, 9)) {
                    state.setBindingSetting(bindSetting);
                    return true;
                }
                settingYoffset += 12f;
            }
        }
        return false;
    }

    private Font issue(int size) {
        return Fonts.getFont("suisse", size);
    }
}

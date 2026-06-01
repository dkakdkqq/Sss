package fun.lumis.client.ui.clickgui;

import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.Setting;
import fun.lumis.client.modules.settings.implement.BindSetting;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;
import fun.lumis.client.modules.settings.implement.TextSetting;

import java.util.List;

public final class ClickGuiLayout {
    public static final float WIDTH = 100f;
    public static final float HEIGHT = 275f;
    public static final float CATEGORY_PANEL_STEP = 108f;

    public static final float THEME_PANEL_Y = 100f;
    public static final float THEME_PANEL_H = 15f;
    public static final float THEME_BOX_SIZE = 8f;
    public static final float THEME_BOX_GAP = 4f;
    public static final float THEME_BOX_RADIUS = 2f;
    public static final float THEME_SIDE_PADDING = 4f;

    public static final float MODULE_PADDING = 3f;
    public static final float MODULE_GAP = 4f;
    public static final float MODULE_HEADER_HEIGHT = 20f;
    public static final float MODULE_INNER_WIDTH = 93.5f;
    public static final float SETTING_START_Y = 20f;
    public static final float SETTING_PADDING = 4f;
    public static final float SETTING_BOTTOM_PADDING = 3f;
    public static final float SETTING_LEFT = 10f;
    public static final float SETTING_RIGHT = 89f;
    public static final float SLIDER_WIDTH = 79f;
    public static final float TEXT_SETTING_WIDTH = 42f;
    public static final float CLICKABLE_WIDTH = 79f;
    public static final int SEARCH_MAX_CHARS = 24;
    public static final float SEARCH_WIDTH = 75f;
    public static final float SEARCH_HEIGHT = 18f;
    public static final float SEARCH_GAP = 8f;
    public static final float SEARCH_ICON_X = 3.5f;
    public static final float SEARCH_TEXT_X = 19f;
    public static final float SEARCH_RIGHT_PADDING = 8f;

    private ClickGuiLayout() {
    }

    public static float getTotalCategoriesWidth(int categoryCount) {
        return (WIDTH * categoryCount) + (8f * (categoryCount - 1));
    }

    public static float getCategoryPanelX(float x, int index) {
        return x + (index * CATEGORY_PANEL_STEP);
    }

    public static float getContentY(float y) {
        return y + 25f;
    }

    public static float getContentHeight() {
        return HEIGHT - 30f;
    }

    public static float getSearchX(float x, int categoryCount) {
        return x + (getTotalCategoriesWidth(categoryCount) / 2f) - (SEARCH_WIDTH / 2f);
    }

    public static float getSearchX(float x, int categoryCount, float searchWidth) {
        return x + (getTotalCategoriesWidth(categoryCount) / 2f) - (searchWidth / 2f);
    }

    public static float getSearchY(float y) {
        return y + HEIGHT + SEARCH_GAP;
    }

    public static boolean hasVisibleSettings(List<Setting> settings) {
        for (Setting setting : settings) {
            if (setting != null && setting.visible()) {
                return true;
            }
        }
        return false;
    }

    public static float calculateModeSettingHeight(ModeSetting modeSetting) {
        return modeSetting.getMods().size() * 10 + 12;
    }

    public static float calculateListSettingHeight(ListSetting listSetting) {
        int visibleCount = 0;
        for (BooleanSetting entry : listSetting.getSettings()) {
            if (entry.visible()) {
                visibleCount++;
            }
        }
        return visibleCount * 10 + 12;
    }

    public static float calculateSettingsHeight(Module module) {
        float height = 0f;
        List<Setting> settings = module.getSettings();
        if (settings == null || settings.isEmpty()) {
            return 0f;
        }

        boolean hasVisibleSetting = false;
        for (Setting setting : settings) {
            if (setting == null || !setting.visible()) {
                continue;
            }

            hasVisibleSetting = true;
            if (setting instanceof BooleanSetting || setting instanceof BindSetting) {
                height += 12f;
            } else if (setting instanceof TextSetting) {
                height += 12f;
            } else if (setting instanceof FloatSetting) {
                height += 22f;
            } else if (setting instanceof ModeSetting modeSetting) {
                height += calculateModeSettingHeight(modeSetting);
            } else if (setting instanceof ListSetting listSetting) {
                height += calculateListSettingHeight(listSetting);
            }
        }

        if (hasVisibleSetting) {
            height += SETTING_BOTTOM_PADDING;
        }
        return height;
    }

    public static float getModuleHeight(Module module, float openProgress) {
        return MODULE_HEADER_HEIGHT + (calculateSettingsHeight(module) * openProgress);
    }
}

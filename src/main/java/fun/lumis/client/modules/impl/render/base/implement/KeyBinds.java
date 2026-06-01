package fun.lumis.client.modules.impl.render.base.implement;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import fun.lumis.Lumis;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.animation.AnimationUtils;
import fun.lumis.api.utils.animation.Easings;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.draggable.Draggable;
import fun.lumis.api.utils.input.KeyBoardUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.api.utils.scissor.ScissorUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.render.base.InterfaceProcessing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyBinds extends InterfaceProcessing {
    private final Map<Module, AnimationUtils> animations = new HashMap<>();
    private final AnimationUtils widthAnimation = new AnimationUtils(60, 10.5f, Easings.QUAD_OUT);

    private static final Map<Character, Character> RU_TO_EN = new HashMap<>();
    static {
        String ru = "йцукенгшщзхъфывапролджэячсмитьбюЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ";
        String en = "qwertyuiop[]asdfghjkl;'zxcvbnm,.QWERTYUIOP[]ASDFGHJKL;'ZXCVBNM,.";
        int length = Math.min(ru.length(), en.length());
        for (int i = 0; i < length; i++) {
            RU_TO_EN.put(ru.charAt(i), en.charAt(i));
        }
    }

    public KeyBinds(Draggable draggable) {
        super(draggable);
    }

    private Font issue(int size) { return Fonts.getFont("suisse", size); }
    private Font icon(int size) { return Fonts.getFont("icon1", size); }

    private AnimationUtils getAnimation(Module module) {
        return animations.computeIfAbsent(module, m -> new AnimationUtils(0, 10.5f, Easings.QUAD_OUT));
    }

    private String toEnglish(String text) {
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(RU_TO_EN.getOrDefault(c, c));
        }
        return result.toString();
    }

    private int getStaticThemeColor() {
        int[] colors = Lumis.INSTANCE.themeStorage.getThemes().getTheme().getColor();
        if (colors == null || colors.length == 0) {
            return 0xFFFFFFFF;
        }

        int color = colors[0];
        if (((color >> 24) & 0xFF) == 0) {
            color = (color & 0x00FFFFFF) | 0xFF000000;
        }
        return color;
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        if (ModuleClass.interfaceModule.style.is("Обычный")) DefaultStyle(eventRender);
        else WaveStyle(eventRender);
        super.onRender(eventRender);
    }

    public void DefaultStyle(EventRender.Default eventRender) {
        float baseX = draggable.getX(), y = draggable.getY();
        int colorTheme;
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            colorTheme = Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            colorTheme = ColorUtils.getThemeColor();
        }
        int staticAccentColor = getStaticThemeColor();

        float targetWidth = 64;
        float targetHeight = 16;
        int visibleCount = 0;

        for (Module module : ModuleClass.INSTANCE.getObject()) {
            if (module.getKey() != -1) {
                AnimationUtils anim = getAnimation(module);
                anim.update(module.isEnable() ? 1 : 0);
            }
        }

        for (Module module : ModuleClass.INSTANCE.getObject()) {
            if (module.getKey() != -1) {
                AnimationUtils anim = getAnimation(module);
                float animValue = anim.getValue();

                if (animValue > 0.01f) {
                    visibleCount++;
                    String keyName = toEnglish(KeyBoardUtils.getKeyName(module.getKey()));
                    float keyWidth = issue(10).getWidth(keyName);
                    float moduleWidth = issue(12).getWidth(module.getDisplayName()) + keyWidth + 25;
                    if (moduleWidth > targetWidth) targetWidth = moduleWidth;
                    targetHeight += 12 * animValue;
                }
            }
        }

        if (visibleCount > 0) targetHeight += 2;

        widthAnimation.update(targetWidth);
        float width = widthAnimation.getValue() + 7;
        float height = targetHeight;
        float rightEdge = baseX + 60;
        float x = rightEdge - width;

        RenderUtils.drawDefaultHudElementRects(eventRender.getContext().getMatrices(), x, y, width, height, colorTheme, isUnusualRectType());
        issue(14).draw(eventRender.getContext().getMatrices(), "Binds", x + 5, y + 6f, -1);
        icon(13).draw(eventRender.getContext().getMatrices(), "f", rightEdge - 13f, y + 7.5f, colorTheme);

        float offsetY = 18;
        for (Module module : ModuleClass.INSTANCE.getObject()) {
            if (module.getKey() != -1) {
                AnimationUtils anim = getAnimation(module);
                float animValue = anim.getValue();

                if (animValue > 0.01f) {
                    ScissorUtils.push();
                    ScissorUtils.setFromComponentCoordinates(x, y, width, height);
                    String keyName = toEnglish(KeyBoardUtils.getBindName(module.getKey()));
                    float keyBoxWidth = Math.max(issue(10).getWidth(keyName) + 4, 9f);

                    int alpha = (int) (255 * animValue);
                    int textColor = ColorUtils.rgba(255, 255, 255, alpha);
                    int accentColor = ColorUtils.setAlphaColor(getStableThemeColor(), alpha);
                    int grayColor = ColorUtils.rgba(55, 55, 55, alpha);
                    int darkColor = ColorUtils.rgba(35, 35, 35, alpha);

                    issue(12).draw(eventRender.getContext().getMatrices(), module.getDisplayName(), x + 12, y + 2 + offsetY, textColor);
                    RenderUtils.drawRoundedRect(eventRender.getContext().getMatrices(), x + 5.2f, y + offsetY + 0.3f, 2.55f, 5.7f, 0.15f, accentColor);

                    float keyBoxX = rightEdge - keyBoxWidth - 5;
                    RenderUtils.drawDefaultHudInfoBox(eventRender.getContext().getMatrices(), keyBoxX, y + offsetY, keyBoxWidth, grayColor, darkColor);
                    issue(10).drawCenteredString(eventRender.getContext().getMatrices(), keyName, keyBoxX + keyBoxWidth / 2, y + offsetY + 2.8f, textColor);

                    offsetY += 12 * animValue;
                    ScissorUtils.pop();
                    ScissorUtils.unset();
                }
            }
        }

        draggable.setWidth(60);
        draggable.setHeight(height);
    }
    private int getStableThemeColor() {
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            return Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        }
        return ColorUtils.getThemeColor();
    }

    public void WaveStyle(EventRender.Default eventRender) {
        final MatrixStack context = eventRender.getContext().getMatrices();
        float x = draggable.getX(), y = draggable.getY();

        int time = (int) ((System.currentTimeMillis() % 2000) / 2000f * 360f);

        int leftTop = ColorUtils.getThemeColor(time);
        int leftBottom = ColorUtils.getThemeColor(time + 30);
        int centerTop = ColorUtils.getThemeColor(time + 90);
        int centerBottom = ColorUtils.getThemeColor(time + 120);
        int rightTop = ColorUtils.getThemeColor(time + 180);
        int rightBottom = ColorUtils.getThemeColor(time + 210);

        List<Module> activeModules = new ArrayList<>();
        for (final Module module : ModuleClass.INSTANCE.getObject()) {
            if (module.getKey() <= 0) {
                module.getAnimka().update(0);
                continue;
            }
            module.getAnimka().update(module.isEnable() ? 1 : 0);
            if (module.getAnimka().getValue() > 0.01f) {
                activeModules.add(module);
            }
        }

        float targetWidth = 84f;
        float height = 18f;
        int visibleModules = 0;

        for (final Module module : activeModules) {
            float animValue = module.getAnimka().getValue();
            if (animValue <= 0.01f) continue;
            visibleModules++;

            String line = module.getDisplayName().toLowerCase() + " >> toggle";
            targetWidth = Math.max(targetWidth, issue(14).getWidth(line) + 7f);
            height += 12f * animValue;
        }

        widthAnimation.update(targetWidth);
        float animatedWidth = widthAnimation.getValue();

        if (visibleModules == 0) {
            float headerHeight = 18f;
            RenderUtils.drawWaveHudHeader(context, x, y, animatedWidth, 15, 0,
                    10, 10, leftTop, leftBottom, centerTop, centerBottom, rightTop, rightBottom);

            String title = "keybinds";
            float titleX = x + (animatedWidth - issue(15).getWidth(title)) / 2.0f;
            issue(15).drawStringWithShadow(eventRender.getContext().getMatrices(), title, titleX, y + 5, -1);

            draggable.setWidth(animatedWidth);
            draggable.setHeight(headerHeight);
            return;
        }

        RenderUtils.drawWaveHudPanel(context, x, y, animatedWidth, height, ColorUtils.rgba(25, 25, 25, 150),
                15, 0, 10, 10,
                leftTop, leftBottom, centerTop, centerBottom, rightTop, rightBottom);

        String title = "keybinds";
        float titleX = x + (animatedWidth - issue(15).getWidth(title)) / 2.0f;
        issue(15).drawStringWithShadow(eventRender.getContext().getMatrices(), title, titleX, y + 5, -1);

        float yOffset = 18f;
        for (final Module module : activeModules) {
            float animValue = module.getAnimka().getValue();
            if (animValue <= 0.01f) continue;

            ScissorUtils.push();
            ScissorUtils.setFromComponentCoordinates(x, y, animatedWidth, height);

            int alpha = (int) (255 * animValue);
            int textColor = ColorUtils.rgba(255, 255, 255, alpha);

            String text = module.getDisplayName().toLowerCase() + " >> toggle";
            float textX = x + 5.5f;

            issue(14).draw(context, text, textX, y + yOffset + 2, textColor);

            yOffset += 12f * animValue;

            ScissorUtils.unset();
            ScissorUtils.pop();
        }

        draggable.setWidth(animatedWidth);
        draggable.setHeight(height);
    }
}

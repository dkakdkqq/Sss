package fun.lumis.client.modules.impl.render.base.implement;

import fun.lumis.Lumis;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.animation.AnimationUtils;
import fun.lumis.api.utils.animation.Easings;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.draggable.Draggable;
import fun.lumis.api.utils.notification.NotificationManager;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.impl.render.base.InterfaceProcessing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Notifications extends InterfaceProcessing {
    private static final float DEFAULT_PAD_X = 7f;
    private static final float DEFAULT_ICON_TEXT_GAP = 1f;
    private static final float PREVIEW_ICON_TEXT_GAP = 2f;

    private final Map<NotificationManager.Entry, AnimationUtils> appearAnimations = new HashMap<>();
    private final Map<NotificationManager.Entry, Float> currentYPositions = new HashMap<>();
    private final Set<NotificationManager.Entry> activeEntriesScratch = new HashSet<>();
    private long lastRenderTime = System.currentTimeMillis();
    private float previewAlpha = 0f;

    public Notifications(Draggable draggable) {
        super(draggable);
    }

    private Font issue(int size) { return Fonts.getFont("suisse", size); }
    private Font icons(int size) { return Fonts.getFont("icon", size); }
    private Font iconNew(int size) { return Fonts.getFont("icon", size); }

    private String getEntryText(NotificationManager.Entry entry) {
        if (entry.isCustom()) {
            return entry.customText;
        }
        String state = entry.enabled ? "Включен!" : "Выключен!";
        return entry.moduleName + " " + state;
    }

    private String getWaveBodyText(NotificationManager.Entry entry) {
        if (entry.isCustom()) {
            return entry.customText;
        }
        return "Module '" + entry.moduleName + "' is " + (entry.enabled ? "enabled." : "disabled.");
    }

    private float getDefaultEntryWidth(NotificationManager.Entry entry, float padX) {
        String text = getEntryText(entry);
        String iconGlyph = entry.categoryIcon != null && !entry.categoryIcon.isEmpty() ? entry.categoryIcon : "?";
        return issue(13).getWidth(text) + icons(14).getWidth(iconGlyph) + padX * 2f + DEFAULT_ICON_TEXT_GAP;
    }

    private float getPreviewWidth(String previewText, String previewIconGlyph, float padX) {
        return issue(13).getWidth(previewText) + icons(16).getWidth(previewIconGlyph) + padX * 2f + PREVIEW_ICON_TEXT_GAP;
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        if (!ModuleClass.interfaceModule.style.is("Wave")) {
            DefaultStyle(eventRender);
        } else {
            WaveStyle(eventRender);
        }
        super.onRender(eventRender);
    }

    private void DefaultStyle(EventRender.Default eventRender) {
        if (mc == null) return;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastRenderTime) / 1000f;
        lastRenderTime = currentTime;

        List<NotificationManager.Entry> entries = NotificationManager.getActive();
        boolean isChatOpen = mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;

        boolean shouldRender = !entries.isEmpty() || isChatOpen;

        float targetPreviewAlpha = isChatOpen ? 0.7f : 0f;
        float alphaSpeed = 8f;
        previewAlpha += (targetPreviewAlpha - previewAlpha) * Math.min(1f, alphaSpeed * deltaTime);

        if (!shouldRender && previewAlpha < 0.01f) {
            appearAnimations.clear();
            currentYPositions.clear();
            previewAlpha = 0f;
            return;
        }

        float baseX = draggable.getX();
        float baseY = draggable.getY();

        int colorTheme;
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            colorTheme = Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            colorTheme = ColorUtils.getThemeColor();
        }
        boolean drawSquares = isUnusualRectType();

        long now = System.currentTimeMillis();
        float height = 16f;
        float spacing = 3f;
        float lerpSpeed = 12f;
        float padX = DEFAULT_PAD_X;

        String previewText = "Кликни на меня для открытия настроек!";
        String previewIconGlyph = "A";
        float previewIconW = icons(16).getWidth(previewIconGlyph);
        float previewWidth = getPreviewWidth(previewText, previewIconGlyph, padX);

        float maxWidth = previewWidth;
        for (NotificationManager.Entry entry : entries) {
            float width = getDefaultEntryWidth(entry, padX);
            if (width > maxWidth) maxWidth = width;
        }

        float targetY = baseY;

        if (previewAlpha > 0.01f) {
            float x = baseX + (maxWidth - previewWidth) * 0.5f;
            float renderY = targetY;
            float alpha = previewAlpha;
            float scale = 0.86f + 0.14f * alpha;

            int base = ColorUtils.setAlphaColor(ColorUtils.rgba(50, 50, 50, 255), (int) (255 * alpha));
            int top = ColorUtils.setAlphaColor(ColorUtils.darken(colorTheme, 0.15F), (int) (255 * alpha));
            int bottom = ColorUtils.setAlphaColor(ColorUtils.darken(colorTheme, 0.05F), (int) (255 * alpha));

            float cx = x + previewWidth * 0.5f;
            float cy = renderY + height * 0.5f;
            var ms = eventRender.getContext().getMatrices();
            ms.push();
            ms.translate(cx, cy, 0);
            ms.scale(scale, scale, 1.0f);
            ms.translate(-cx, -cy, 0);

            RenderUtils.drawDefaultHudPanel(ms, x, renderY, previewWidth, height, 3f, 3.5f, base, top, bottom);
            float squareAlpha = alpha * alpha * (3.0f - 2.0f * alpha);
            if (drawSquares && squareAlpha > 0.08f) {
                RenderUtils.drawHudSquarePattern(ms, x, renderY, previewWidth, height, ColorUtils.setAlphaColor(colorTheme, (int) (255 * squareAlpha)));
            }

            int textColor = ColorUtils.setAlphaColor(-1, (int) (255 * alpha));
            int iconColor = ColorUtils.setAlphaColor(colorTheme, (int) (255 * alpha));
            icons(16).draw(ms, previewIconGlyph, x + padX - 3.5f, renderY + 6.6f, iconColor);
            issue(13).draw(ms, previewText, x + padX + previewIconW + 5.5f, renderY + 6.6f, textColor);

            ms.pop();

            targetY += height + spacing;
        }

        for (NotificationManager.Entry entry : entries) {
            AnimationUtils anim = appearAnimations.computeIfAbsent(entry, e -> new AnimationUtils(0f, 12f, Easings.QUAD_OUT));
            long age = now - entry.startTime;
            anim.update(1f);
            float appear = anim.getValue();
            float alpha = appear;
            if (age > NotificationManager.DURATION_MS - 200) {
                alpha = (1f - (age - (NotificationManager.DURATION_MS - 200)) / 200f) * appear;
            }
            if (alpha <= 0f) {
                targetY += height + spacing;
                continue;
            }

            Float currentY = currentYPositions.get(entry);
            if (currentY == null) {
                currentY = targetY;
            }

            float diff = targetY - currentY;
            if (Math.abs(diff) > 0.01f) {
                currentY = currentY + diff * Math.min(1f, lerpSpeed * deltaTime);
            } else {
                currentY = targetY;
            }
            currentYPositions.put(entry, currentY);

            String text = getEntryText(entry);
            String iconGlyph = entry.categoryIcon != null && !entry.categoryIcon.isEmpty() ? entry.categoryIcon : "?";

            float iconW = icons(14).getWidth(iconGlyph);
            float width = getDefaultEntryWidth(entry, padX);
            float x = baseX + (maxWidth - width) * 0.5f;
            float slide = 6f * (1f - appear);

            float renderY = currentY + slide;
            float scale = 0.86f + 0.14f * alpha;
            boolean disabled = !entry.isCustom() && !entry.enabled;
            int disabledRed = ColorUtils.rgba(200, 55, 55, 255);

            int base = ColorUtils.setAlphaColor(ColorUtils.rgba(50, 50, 50, 255), (int) (255 * alpha));
            int top = ColorUtils.setAlphaColor(ColorUtils.darken(colorTheme, 0.15F), (int) (255 * alpha));
            int bottom = ColorUtils.setAlphaColor(ColorUtils.darken(colorTheme, 0.05F), (int) (255 * alpha));

            float cx = x + width * 0.5f;
            float cy = renderY + height * 0.5f;
            var ms = eventRender.getContext().getMatrices();
            ms.push();
            ms.translate(cx, cy, 0);
            ms.scale(scale, scale, 1.0f);
            ms.translate(-cx, -cy, 0);

            RenderUtils.drawDefaultHudPanel(ms, x, renderY, width, height, 3f, 3.5f, base, top, bottom);
            float squareAlpha = alpha * alpha * (3.0f - 2.0f * alpha);
            if (drawSquares && squareAlpha > 0.08f) {
                RenderUtils.drawHudSquarePattern(ms, x, renderY, width, height, ColorUtils.setAlphaColor(colorTheme, (int) (255 * squareAlpha)));
            }

            int textColor = ColorUtils.setAlphaColor(-1, (int) (255 * alpha));
            int iconColor = ColorUtils.setAlphaColor(colorTheme, (int) (255 * alpha));
            icons(14).draw(ms, iconGlyph, x + padX - 1.5f, renderY + 7.3f, iconColor);
            float textX = x + padX + iconW + DEFAULT_ICON_TEXT_GAP;
            if (!entry.isCustom()) {
                String modulePart = entry.moduleName + " ";
                String statePart = text.length() > modulePart.length() ? text.substring(modulePart.length()) : "";
                int stateColor = disabled ? disabledRed : iconColor;
                issue(13).draw(ms, modulePart, textX + 2, renderY + 6.8f, textColor);
                issue(13).draw(ms, statePart, textX + issue(13).getWidth(modulePart) - 0.5f + 2, renderY + 7f, stateColor);
            } else {
                issue(13).draw(ms, text, textX, renderY + 6.8f, textColor);
            }

            ms.pop();

            targetY += height + spacing;
        }

        activeEntriesScratch.clear();
        activeEntriesScratch.addAll(entries);
        appearAnimations.keySet().removeIf(entry -> !activeEntriesScratch.contains(entry));
        currentYPositions.keySet().removeIf(entry -> !activeEntriesScratch.contains(entry));

        draggable.setWidth(maxWidth);
        draggable.setHeight(Math.max(1f, targetY - baseY));
    }

    private void WaveStyle(EventRender.Default eventRender) {
        if (mc == null) return;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastRenderTime) / 1000f;
        lastRenderTime = currentTime;

        List<NotificationManager.Entry> entries = NotificationManager.getActive();
        if (entries.isEmpty()) {
            appearAnimations.clear();
            currentYPositions.clear();
            return;
        }

        int time = (int) ((System.currentTimeMillis() % 2000) / 2000f * 360f);

        int leftTop = ColorUtils.getThemeColor(time);
        int leftBottom = ColorUtils.getThemeColor(time + 30);
        int centerTop = ColorUtils.getThemeColor(time + 90);
        int centerBottom = ColorUtils.getThemeColor(time + 120);
        int rightTop = ColorUtils.getThemeColor(time + 180);
        int rightBottom = ColorUtils.getThemeColor(time + 210);

        long now = System.currentTimeMillis();

        float spacing = 4f;
        float lerpSpeed = 14f;
        float screenW = mc.getWindow().getScaledWidth();
        float screenH = mc.getWindow().getScaledHeight();
        float rightPadding = 5f;
        float bottomPadding = 5f;

        float stackOffset = 0f;
        float maxWidth = 120f;

        for (NotificationManager.Entry entry : entries) {
            String title = "Notify";
            String body = getWaveBodyText(entry);
            float iconW = iconNew(14).getWidth("j");
            float titleW = issue(15).getWidth(title);
            float bodyW = issue(13).getWidth(body);
            float width = Math.max(120f, Math.max(titleW + iconW + 18f, bodyW + 14f));
            maxWidth = Math.max(maxWidth, width);
        }

        for (NotificationManager.Entry entry : entries) {
            AnimationUtils anim = appearAnimations.computeIfAbsent(entry, e -> new AnimationUtils(0f, 12f, Easings.QUAD_OUT));
            anim.update(1f);
            float appear = anim.getValue();

            long age = now - entry.startTime;
            float alphaMul = appear;
            if (age > NotificationManager.DURATION_MS - 200) {
                alphaMul = (1f - (age - (NotificationManager.DURATION_MS - 200)) / 200f) * appear;
            }
            if (alphaMul <= 0.01f) continue;

            String title = "Notify";
            String body = getWaveBodyText(entry);
            String warningGlyph = "j";

            float iconW = iconNew(14).getWidth(warningGlyph);
            float titleW = issue(15).getWidth(title);
            float bodyW = issue(13).getWidth(body);
            float width = Math.max(120f, Math.max(titleW + iconW + 18f, bodyW + 14f));
            float height = 24f;

            float x = screenW - width - rightPadding;
            float targetY = screenH - bottomPadding - height - stackOffset;

            Float currentY = currentYPositions.get(entry);
            if (currentY == null) currentY = targetY;
            currentY += (targetY - currentY) * Math.min(1f, lerpSpeed * deltaTime);
            currentYPositions.put(entry, currentY);

            float y = currentY;
            float scale = 0.86f + 0.14f * alphaMul;

            int bg = ColorUtils.rgba(25, 25, 25, (int) (150 * alphaMul));
            int txt = ColorUtils.setAlphaColor(-1, (int) (255 * alphaMul));
            int iconCol = ColorUtils.setAlphaColor(ColorUtils.rgba(235, 0, 0, 255), (int) (255 * alphaMul));

            float cx = x + width * 0.5f;
            float cy = y + height * 0.5f;
            var ms = eventRender.getContext().getMatrices();
            ms.push();
            ms.translate(cx, cy, 0);
            ms.scale(scale, scale, 1.0f);
            ms.translate(-cx, -cy, 0);

            RenderUtils.drawWaveHudPanel(ms, x, y, width, height - 1.5f, bg,
                    3.5f, 0, 10, 10,
                    leftTop, leftBottom, centerTop, centerBottom, rightTop, rightBottom);

            iconNew(28).draw(ms, warningGlyph, x + 3f, y + 8, iconCol);
            issue(15).draw(ms, title, x + 19, y + 6.5f, txt);
            issue(13).draw(ms, body, x + 19, y + 15.0f, txt);

            ms.pop();

            stackOffset += (height + spacing) * appear;
        }

        appearAnimations.keySet().removeIf(entry -> !entries.contains(entry));
        currentYPositions.keySet().removeIf(entry -> !entries.contains(entry));

        draggable.setWidth(0);
        draggable.setHeight(0);
    }
}

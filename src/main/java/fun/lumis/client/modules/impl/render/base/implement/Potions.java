package fun.lumis.client.modules.impl.render.base.implement;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import fun.lumis.Lumis;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.animation.AnimationUtils;
import fun.lumis.api.utils.animation.Easings;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.draggable.Draggable;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.api.utils.scissor.ScissorUtils;
import fun.lumis.client.modules.impl.render.base.InterfaceProcessing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Potions extends InterfaceProcessing {
    private static final class PotionSnapshot {
        RegistryEntry<StatusEffect> entry;
        String baseName;
        int amplifier;
        int duration;
        boolean infinite;
    }

    private final Map<StatusEffect, AnimationUtils> animations = new LinkedHashMap<>();
    private final Map<StatusEffect, PotionSnapshot> snapshots = new HashMap<>();
    private final Map<StatusEffect, Integer> maxDurations = new HashMap<>();
    private final Set<StatusEffect> renderOrderSeen = new HashSet<>();
    private final AnimationUtils widthAnimation = new AnimationUtils(70, 10.5f, Easings.QUAD_OUT);

    public Potions(Draggable draggable) {
        super(draggable);
    }

    private Font issue(int size) { return Fonts.getFont("suisse", size); }
    private Font icon(int size) { return Fonts.getFont("icon", size); }

    private AnimationUtils getAnimation(StatusEffect effect) {
        return animations.computeIfAbsent(effect, e -> new AnimationUtils(0, 10.5f, Easings.QUAD_OUT));
    }

    private static String getLevelSuffix(int level) {
        return String.valueOf(Math.max(1, level));
    }

    private static String formatDuration(StatusEffectInstance effect) {
        return formatDuration(effect.getDuration(), effect.isInfinite());
    }

    private static String formatDuration(int duration, boolean infinite) {
        if (infinite) {
            return "inf";
        }
        int seconds = Math.max(0, duration / 20);
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return minutes + ":" + (secs < 10 ? "0" + secs : String.valueOf(secs));
    }

    private void updateSnapshot(StatusEffectInstance effect) {
        StatusEffect type = effect.getEffectType().value();
        PotionSnapshot snapshot = snapshots.computeIfAbsent(type, e -> new PotionSnapshot());
        snapshot.entry = effect.getEffectType();
        snapshot.baseName = I18n.translate(effect.getTranslationKey());
        snapshot.amplifier = effect.getAmplifier() + 1;
        snapshot.duration = effect.getDuration();
        snapshot.infinite = effect.isInfinite();
    }

    private List<StatusEffect> buildRenderOrder(Collection<StatusEffectInstance> effects, Set<StatusEffect> active) {
        List<StatusEffect> order = new ArrayList<>();
        renderOrderSeen.clear();
        for (StatusEffectInstance effect : effects) {
            StatusEffect type = effect.getEffectType().value();
            if (renderOrderSeen.add(type)) {
                order.add(type);
            }
        }
        for (StatusEffect type : animations.keySet()) {
            if (!active.contains(type)) {
                order.add(type);
            }
        }
        return order;
    }

    private void drawEffectIcon(EventRender.Default eventRender, RegistryEntry<StatusEffect> effect, float x, float y, int size, int alpha) {
        Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(effect);
        int color = ColorUtils.rgba(255, 255, 255, alpha);
        RenderUtils.drawSprite(eventRender.getContext().getMatrices(), sprite, x, y, size, color);
    }

    private void drawTextWithShadow(EventRender.Default eventRender, Font font, String text, float x, float y, int color) {
        int shadow = ColorUtils.rgba(20, 20, 20, 145);
        font.draw(eventRender.getContext().getMatrices(), text, x + 0.8f, y + 0.8f, shadow);
        font.draw(eventRender.getContext().getMatrices(), text, x, y, color);
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        if (ModuleClass.interfaceModule.style.is("Обычный")) {
            DefaultStyle(eventRender);
        } else {
            WaveStyle(eventRender);
        }
        super.onRender(eventRender);
    }

    public void DefaultStyle(EventRender.Default eventRender) {
        float x = draggable.getX();
        float y = draggable.getY();
        int colorTheme;
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            colorTheme = Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            colorTheme = ColorUtils.getThemeColor();
        }
        float targetWidth = 70;
        float targetHeight = 16;
        int visibleCount = 0;

        Collection<StatusEffectInstance> effects = mc != null && mc.player != null
                ? mc.player.getStatusEffects()
                : java.util.List.of();

        Set<StatusEffect> active = new HashSet<>();
        for (StatusEffectInstance effect : effects) {
            StatusEffect type = effect.getEffectType().value();
            active.add(type);
            getAnimation(type).update(1);
            updateSnapshot(effect);

            int duration = effect.getDuration();
            Integer prevMax = maxDurations.get(type);

            if (prevMax == null || duration > prevMax) {
                maxDurations.put(type, duration);
            }
        }

        for (Map.Entry<StatusEffect, AnimationUtils> entry : animations.entrySet()) {
            if (!active.contains(entry.getKey())) {
                entry.getValue().update(0);
            }
        }

        List<StatusEffect> renderOrder = buildRenderOrder(effects, active);

        for (StatusEffect type : renderOrder) {
            AnimationUtils anim = getAnimation(type);
            float animValue = anim.getValue();
            PotionSnapshot snapshot = snapshots.get(type);
            if (animValue > 0.01f) {
                if (snapshot == null) {
                    continue;
                }
                visibleCount++;
                String baseName = snapshot.baseName != null ? snapshot.baseName : I18n.translate(type.getTranslationKey());
                String levelSuffix = getLevelSuffix(snapshot.amplifier);
                String time = formatDuration(snapshot.duration, snapshot.infinite);
                float nameWidth = issue(12).getWidth(baseName);
                if (!levelSuffix.isEmpty()) {
                    nameWidth += issue(11).getWidth(" LVL") + issue(12).getWidth(levelSuffix);
                }
                float timeWidth = issue(10).getWidth(time) + 6;
                float rowWidth = nameWidth + timeWidth + 25 + 9 + 9;
                if (rowWidth > targetWidth) targetWidth = rowWidth;
                targetHeight += 12 * animValue;
            }
        }

        if (visibleCount > 0) targetHeight += 2;

        widthAnimation.update(targetWidth);
        float width = widthAnimation.getValue();
        float height = targetHeight;

        RenderUtils.drawDefaultHudElementRects(eventRender.getContext().getMatrices(), x, y, width, height, colorTheme, isUnusualRectType());
        issue(14).draw(eventRender.getContext().getMatrices(), "Effects", x + 5, y + 6f, -1);
        icon(13).draw(eventRender.getContext().getMatrices(), "d", x + width - 12.5f, y + 7.5f, colorTheme);

        float offsetY = 18;
        for (StatusEffect type : renderOrder) {
            AnimationUtils anim = getAnimation(type);
            float animValue = anim.getValue();
            PotionSnapshot snapshot = snapshots.get(type);

            if (animValue > 0.01f) {
                if (snapshot == null) {
                    continue;
                }
                ScissorUtils.push();
                ScissorUtils.setFromComponentCoordinates(x, y, width, height);

                int alpha = (int) (255 * animValue);
                int textColor = ColorUtils.rgba(255, 255, 255, alpha);
                int grayColor = ColorUtils.rgba(55, 55, 55, alpha);
                int darkColor = ColorUtils.rgba(35, 35, 35, alpha);

                float iconSize = 7;
                float iconX = x + 5;
                float iconY = y + offsetY;
                if (snapshot.entry != null) {
                    drawEffectIcon(eventRender, snapshot.entry, iconX, iconY, (int) iconSize, alpha);
                }

                String baseName = snapshot.baseName != null ? snapshot.baseName : I18n.translate(type.getTranslationKey());
                String levelSuffix = getLevelSuffix(snapshot.amplifier);
                float textX = iconX + iconSize + 3;
                float textY = y + 2 + offsetY;
                issue(12).draw(eventRender.getContext().getMatrices(), baseName, textX, textY, textColor);
                if (!levelSuffix.isEmpty()) {
                    float baseWidth = issue(12).getWidth(baseName);
                    int levelThemeColor = ColorUtils.setAlphaColor(colorTheme, alpha);
                    float lvlX = textX + baseWidth;
                    issue(10).draw(eventRender.getContext().getMatrices(), " LVL", lvlX, textY + 1, levelThemeColor);
                    issue(11).draw(eventRender.getContext().getMatrices(), levelSuffix, lvlX + issue(11).getWidth(" LVL"), textY + 0.5, levelThemeColor);
                }

                String time = formatDuration(snapshot.duration, snapshot.infinite);
                float timeBoxWidth = Math.max(issue(10).getWidth(time) + 4, 12f);
                float ringSize = 6f;
                float ringGap = 3f;
                float timeBoxX = x + width - timeBoxWidth - 5;
                float ringX = timeBoxX - ringGap - ringSize;
                float ringY = y + offsetY + 0.3f;
                RenderUtils.drawDefaultHudInfoBox(eventRender.getContext().getMatrices(), timeBoxX, y + offsetY, timeBoxWidth, grayColor, darkColor);
                issue(10).drawCenteredString(eventRender.getContext().getMatrices(), time, timeBoxX + timeBoxWidth / 2, y + offsetY + 3, textColor);

                float progress = 1f;
                if (!snapshot.infinite) {
                    int currentDuration = snapshot.duration;
                    int maxDuration = maxDurations.getOrDefault(type, currentDuration);

                    if (maxDuration > 0) {
                        progress = MathHelper.clamp((float) currentDuration / (float) maxDuration, 0f, 1f);
                    } else {
                        progress = 0f;
                    }
                }

                int ringColor = ColorUtils.setAlphaColor(colorTheme, alpha);
                float thickness = 1.75f;
                RenderUtils.drawRingArc(eventRender.getContext().getMatrices(), ringX, ringY, ringSize, thickness, -90f, 270f, grayColor);
                if (progress > 0f) {
                    float endAngle = -90f + 360f * progress;
                    RenderUtils.drawRingArc(eventRender.getContext().getMatrices(), ringX, ringY, ringSize, thickness, -90f, endAngle, ringColor);
                }

                offsetY += 12 * animValue;
                ScissorUtils.pop();
                ScissorUtils.unset();
            }
        }

        animations.entrySet().removeIf(entry -> !active.contains(entry.getKey()) && entry.getValue().getValue() <= 0.01f);
        snapshots.keySet().removeIf(type -> !animations.containsKey(type));
        maxDurations.keySet().removeIf(type -> !animations.containsKey(type));

        draggable.setWidth(width);
        draggable.setHeight(height);
    }

    public void WaveStyle(EventRender.Default eventRender) {
        float x = draggable.getX();
        float y = draggable.getY();

        int time = (int) ((System.currentTimeMillis() % 2000) / 2000f * 360f);

        int leftTop = ColorUtils.getThemeColor(time);
        int leftBottom = ColorUtils.getThemeColor(time + 30);
        int centerTop = ColorUtils.getThemeColor(time + 90);
        int centerBottom = ColorUtils.getThemeColor(time + 120);
        int rightTop = ColorUtils.getThemeColor(time + 180);
        int rightBottom = ColorUtils.getThemeColor(time + 210);

        Collection<StatusEffectInstance> effects = mc != null && mc.player != null
                ? mc.player.getStatusEffects()
                : java.util.List.of();

        Set<StatusEffect> active = new HashSet<>();
        for (StatusEffectInstance effect : effects) {
            StatusEffect type = effect.getEffectType().value();
            active.add(type);
            getAnimation(type).update(1);
        }
        for (Map.Entry<StatusEffect, AnimationUtils> entry : animations.entrySet()) {
            if (!active.contains(entry.getKey())) {
                entry.getValue().update(0);
            }
        }

        float width = 84f;
        float height = 18;
        int visibleEffects = 0;

        for (StatusEffectInstance effect : effects) {
            AnimationUtils anim = getAnimation(effect.getEffectType().value());
            float animValue = anim.getValue();
            if (animValue <= 0.01f) continue;
            visibleEffects++;

            String baseName = I18n.translate(effect.getTranslationKey());
            String levelSuffix = getLevelSuffix(effect.getAmplifier() + 1);
            String line = baseName + (levelSuffix.isEmpty() ? "" : " > " + levelSuffix);
            width = Math.max(width, issue(16).getWidth(line) + 38f);
            width = Math.max(width, issue(15).getWidth(formatDuration(effect)) + 38f);
            height += 18f * animValue;
        }

        if (visibleEffects == 0) {
            float headerHeight = 18f;
            RenderUtils.drawWaveHudHeader(eventRender.getContext().getMatrices(), x, y, width, 15, 0,
                    10, 10, leftTop, leftBottom, centerTop, centerBottom, rightTop, rightBottom);
            String title = "potions";
            float titleX = x + (width - issue(16).getWidth(title)) / 2.0f;
            drawTextWithShadow(eventRender, issue(16), title, titleX, y + 5, -1);
            draggable.setWidth(width);
            draggable.setHeight(headerHeight);
            return;
        }

        RenderUtils.drawWaveHudPanel(eventRender.getContext().getMatrices(), x, y, width, height, ColorUtils.rgba(25, 25, 25, 150),
                15, 0, 10, 10,
                leftTop, leftBottom, centerTop, centerBottom, rightTop, rightBottom);

        String title = "potions";
        float titleX = x + (width - issue(16).getWidth(title)) / 1.9f;
        drawTextWithShadow(eventRender, issue(16), title, titleX, y + 5, -1);

        float yOffset = 20f;
        for (StatusEffectInstance effect : effects) {
            AnimationUtils anim = getAnimation(effect.getEffectType().value());
            float animValue = anim.getValue();
            if (animValue <= 0.01f) continue;

            ScissorUtils.push();
            ScissorUtils.setFromComponentCoordinates(x, y, width, height);

            int alpha = (int) (255 * animValue);
            int textColor = ColorUtils.rgba(255, 255, 255, alpha);
            int levelColor = ColorUtils.rgba(20, 185, 45, alpha);

            float iconX = x + 5f;
            float iconY = y + yOffset;
            drawEffectIcon(eventRender, effect.getEffectType(), iconX, iconY, 11, alpha);

            String baseName = I18n.translate(effect.getTranslationKey()).toLowerCase();
            String levelSuffix = getLevelSuffix(effect.getAmplifier() + 1);
            float textX = iconX + 14f;

            issue(15).draw(eventRender.getContext().getMatrices(), baseName + " >", textX, y + yOffset - 1, textColor);
            if (!levelSuffix.isEmpty()) {
                float nameW = issue(14).getWidth(baseName + " >");
                issue(14).draw(eventRender.getContext().getMatrices(), " " + levelSuffix, textX + nameW + 2, y + yOffset - 0.5, levelColor);
            }

            issue(14).draw(eventRender.getContext().getMatrices(), formatDuration(effect), textX, y + yOffset + 7.5, textColor);

            yOffset += 18f * animValue;
            ScissorUtils.pop();
            ScissorUtils.unset();
        }

        draggable.setWidth(width);
        draggable.setHeight(height);
    }
}

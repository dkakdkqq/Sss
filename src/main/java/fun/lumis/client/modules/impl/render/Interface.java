package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.math.MathHelper;
import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.Priority;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.utils.animation.AnimationUtils;
import fun.lumis.api.utils.animation.Easings;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.math.HoveringUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.render.base.InterfaceProcessing;
import fun.lumis.client.modules.impl.render.base.implement.*;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Interface extends Module {

    public static Interface INSTANCE = new Interface();
    private static final ConcurrentHashMap<String, Long> PERF_WARNINGS = new ConcurrentHashMap<>();
    private static final boolean PERF_DEBUG = Boolean.parseBoolean(System.getProperty("lumis.perf.debug", "false"));
    private static final long SLOW_HUD_ELEMENT_NANOS = Long.getLong("lumis.perf.hudMs", 5L) * 1_000_000L;
    private static final long PERF_WARN_COOLDOWN_NANOS = Long.getLong("lumis.perf.cooldownMs", 1000L) * 1_000_000L;

    private final WaterMark waterMark;
    private final ArrayListHud arrayListHud;
    private final KeyBinds keyBinds;
    private final HelperBinds helperBinds;
    private final Potions potions;
    private final KeyStrokes keyStrokes;
    private final Notifications notifications;
    private final TargetHud targetHud;
    private final Session session;
    private final Information information;
    private final StaffList staffList;
    private boolean targetHudMenuOpen;
    private float targetHudMenuX;
    private float targetHudMenuY;
    private InterfaceProcessing hudContextElement;
    private InterfaceProcessing pendingHudContextElement;
    private float pendingTargetHudMenuX;
    private float pendingTargetHudMenuY;
    private final AnimationUtils targetHudMenuAnimation = new AnimationUtils(0f, 12.5f, Easings.CUBIC_OUT);
    private final AnimationUtils targetHudParticlesBgAnimation = new AnimationUtils(1f, 15.0f, Easings.CUBIC_OUT);
    private final AnimationUtils targetHudParticlesCircleAnimation = new AnimationUtils(1f, 8.2f, Easings.BACK_OUT);
    private final AnimationUtils targetHudBarSwitchAnimation = new AnimationUtils(0f, 7.0f, Easings.CUBIC_OUT);
    private final AnimationUtils waterMarkFpsBgAnimation = new AnimationUtils(1f, 15.0f, Easings.CUBIC_OUT);
    private final AnimationUtils waterMarkFpsCircleAnimation = new AnimationUtils(1f, 8.2f, Easings.BACK_OUT);
    private final AnimationUtils waterMarkMsBgAnimation = new AnimationUtils(1f, 15.0f, Easings.CUBIC_OUT);
    private final AnimationUtils waterMarkMsCircleAnimation = new AnimationUtils(1f, 8.2f, Easings.BACK_OUT);
    private final AnimationUtils waterMarkServerBgAnimation = new AnimationUtils(1f, 15.0f, Easings.CUBIC_OUT);
    private final AnimationUtils waterMarkServerCircleAnimation = new AnimationUtils(1f, 8.2f, Easings.BACK_OUT);
    private final AnimationUtils waterMarkTpsBgAnimation = new AnimationUtils(1f, 15.0f, Easings.CUBIC_OUT);
    private final AnimationUtils waterMarkTpsCircleAnimation = new AnimationUtils(1f, 8.2f, Easings.BACK_OUT);
    private final AnimationUtils hudRectTypeSwitchAnimation = new AnimationUtils(1f, 7.0f, Easings.CUBIC_OUT);
    private static final String HUD_HINT_TEXT = "ПКМ - по элементу для открытия настроек";

    public ModeSetting style = new ModeSetting("Стиль", "Обычный", "Обычный");

    private final ListSetting hudModules = new ListSetting("Элементы",
            new BooleanSetting("Ватермарка", true),
            new BooleanSetting("Аррай лист", true),
            new BooleanSetting("Горячие клавиши", true),
            new BooleanSetting("Серверные бинды", true),
            new BooleanSetting("Зелья", true),
            new BooleanSetting("Таргет худ", true),
            new BooleanSetting("Уведомления", true),
            new BooleanSetting("Стафф", true),
            new BooleanSetting("Сессия", true).visible(() -> style.is("Wave")),
            new BooleanSetting("КейСтроки", true).visible(() -> style.is("Wave")),
            new BooleanSetting("Информация", true));

    public Interface() {
        super("Interface", "Интерфейс клиента", ModuleCategory.RENDER);
        addSettings(hudModules, style);
        this.waterMark = new WaterMark(Lumis.draggable(this, "WaterMark", 10, 10));
        this.arrayListHud = new ArrayListHud(Lumis.draggable(this, "ArrayList", 5, 24));
        this.keyBinds = new KeyBinds(Lumis.draggable(this, "KeyBinds", 30, 30));
        this.helperBinds = new HelperBinds(Lumis.draggable(this, "HelperBinds", 90, 30));
        this.potions = new Potions(Lumis.draggable(this, "Potions", 30, 60));
        this.staffList = new StaffList(Lumis.draggable(this, "StaffList", 60, 100));
        this.session = new Session(Lumis.draggable(this, "Session", 70, 30));
        this.keyStrokes = new KeyStrokes(Lumis.draggable(this, "KeyStrokes", 150, 120));
        this.information = new Information(Lumis.draggable(this, "Information", 50, 100));
        this.notifications = new Notifications(Lumis.draggable(this, "Notifications", 0, 0));
        this.targetHud = new TargetHud(Lumis.draggable(this, "TargetHud", 30, 90));
    }

    private Font issue(int size) {
        return Fonts.getFont("suisse", size);
    }

    private int fadeColorSafe(int color, float progress, int minAlpha) {
        int faded = ColorUtils.applyAlpha(color, progress);
        int a = ColorUtils.getAlpha(faded);
        if (a == 0 && progress > 0.001f) {
            return ColorUtils.setAlphaColor(faded, minAlpha);
        }
        return faded;
    }

    private int fadeTextAlphaSafe(float progress, int maxAlpha, int minAlpha) {
        int alpha = MathHelper.clamp((int) (maxAlpha * progress), 0, maxAlpha);
        if (alpha == 0 && progress > 0.001f) {
            return minAlpha;
        }
        return alpha;
    }

    private int getThemeColor() {
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            return Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        }
        return ColorUtils.getThemeColor();
    }
    private boolean isHudElementHovered(InterfaceProcessing element, double mouseX, double mouseY) {
        float width = element.draggable.getWidth();
        float height = element.draggable.getHeight();
        if (width <= 1.0f || height <= 1.0f) {
            return false;
        }
        return HoveringUtils.isHovered(mouseX, mouseY, element.draggable.getX(), element.draggable.getY(), width, height);
    }
    private boolean isHudElementEnabled(InterfaceProcessing element) {
        return element != null && element.draggable.getWidth() > 1.0f && element.draggable.getHeight() > 1.0f;
    }
    private InterfaceProcessing getHoveredHudElement(double mouseX, double mouseY) {
        if (isHudElementEnabled(targetHud) && isHudElementHovered(targetHud, mouseX, mouseY)) return targetHud;
        if (isHudElementEnabled(waterMark) && isHudElementHovered(waterMark, mouseX, mouseY)) return waterMark;
        if (isHudElementEnabled(arrayListHud) && isHudElementHovered(arrayListHud, mouseX, mouseY)) return arrayListHud;
        if (isHudElementEnabled(keyBinds) && isHudElementHovered(keyBinds, mouseX, mouseY)) return keyBinds;
        if (isHudElementEnabled(helperBinds) && isHudElementHovered(helperBinds, mouseX, mouseY)) return helperBinds;
        if (isHudElementEnabled(potions) && isHudElementHovered(potions, mouseX, mouseY)) return potions;
        if (isHudElementEnabled(keyStrokes) && isHudElementHovered(keyStrokes, mouseX, mouseY)) return keyStrokes;
        if (isHudElementEnabled(information) && isHudElementHovered(information, mouseX, mouseY)) return information;
        if (isHudElementEnabled(staffList) && isHudElementHovered(staffList, mouseX, mouseY)) return staffList;
        if (isHudElementEnabled(session) && isHudElementHovered(session, mouseX, mouseY)) return session;
        if (isHudElementEnabled(notifications) && isHudElementHovered(notifications, mouseX, mouseY)) return notifications;
        return null;
    }
    private float getTargetHudMenuWidth() {
        return 100.0f;
    }

    private float getMenuHeightForElement(InterfaceProcessing element) {
        if (element == targetHud) return 59.0f;
        if (element == waterMark) return 69.0f;
        return 29.0f;
    }

    private float getTargetHudMenuHeight() {
        return getMenuHeightForElement(hudContextElement);
    }

    private void clampTargetHudMenuToWindow(float menuWidth, float menuHeight) {
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        float maxX = Math.max(2.0f, mc.getWindow().getScaledWidth() - menuWidth - 2.0f);
        float maxY = Math.max(2.0f, mc.getWindow().getScaledHeight() - menuHeight - 2.0f);
        targetHudMenuX = MathHelper.clamp(targetHudMenuX, 2.0f, maxX);
        targetHudMenuY = MathHelper.clamp(targetHudMenuY, 2.0f, maxY);
    }
    public boolean handleHudContextClick(double mouseX, double mouseY, int button) {
        InterfaceProcessing hoveredElement = getHoveredHudElement(mouseX, mouseY);
        if (button == 1 && hoveredElement != null) {
            if (targetHudMenuOpen && hudContextElement == hoveredElement) {
                targetHudMenuOpen = false;
                pendingHudContextElement = null;
            } else if (targetHudMenuOpen && hudContextElement != null && hudContextElement != hoveredElement) {
                pendingHudContextElement = hoveredElement;
                float menuWidth = getTargetHudMenuWidth();
                float menuHeight = getMenuHeightForElement(hoveredElement);
                pendingTargetHudMenuX = hoveredElement.draggable.getX() + hoveredElement.draggable.getWidth() + 4.0f;
                pendingTargetHudMenuY = hoveredElement.draggable.getY() + 1.5f;
                float saveX = targetHudMenuX;
                float saveY = targetHudMenuY;
                targetHudMenuX = pendingTargetHudMenuX;
                targetHudMenuY = pendingTargetHudMenuY;
                clampTargetHudMenuToWindow(menuWidth, menuHeight);
                pendingTargetHudMenuX = targetHudMenuX;
                pendingTargetHudMenuY = targetHudMenuY;
                targetHudMenuX = saveX;
                targetHudMenuY = saveY;
                targetHudMenuOpen = false;
            } else {
                hudContextElement = hoveredElement;
                pendingHudContextElement = null;
                targetHudMenuOpen = true;
                float menuWidth = getTargetHudMenuWidth();
                float menuHeight = getTargetHudMenuHeight();
                targetHudMenuX = hoveredElement.draggable.getX() + hoveredElement.draggable.getWidth() + 4.0f;
                targetHudMenuY = hoveredElement.draggable.getY() + 1.5f;
                clampTargetHudMenuToWindow(menuWidth, menuHeight);
            }
            return true;
        }
        if (!targetHudMenuOpen || hudContextElement == null) {
            return false;
        }
        float menuWidth = getTargetHudMenuWidth();
        float menuHeight = getTargetHudMenuHeight();
        clampTargetHudMenuToWindow(menuWidth, menuHeight);
        float buttonGap = 3.0f;
        float buttonX = targetHudMenuX + 5.0f;
        float buttonW = (menuWidth - 10.0f - buttonGap) / 2.0f;
        float buttonH = 10.0f;
        float normalButtonX = buttonX;
        float unusualButtonX = buttonX + buttonW + buttonGap;
        float rectButtonY = hudContextElement == targetHud ? targetHudMenuY + 46.0f : (hudContextElement == waterMark ? targetHudMenuY + 56.0f : targetHudMenuY + 14.0f);
        boolean menuHovered = HoveringUtils.isHovered(mouseX, mouseY, targetHudMenuX, targetHudMenuY, menuWidth, menuHeight);
        boolean rectNormalHovered = HoveringUtils.isHovered(mouseX, mouseY, normalButtonX, rectButtonY, buttonW, buttonH);
        boolean rectUnusualHovered = HoveringUtils.isHovered(mouseX, mouseY, unusualButtonX, rectButtonY, buttonW, buttonH);
        if (button == 0 && !menuHovered && hoveredElement == hudContextElement) {
            targetHudMenuOpen = false;
            pendingHudContextElement = null;
            return false;
        }
        if (button == 0 && rectNormalHovered) {
            hudContextElement.setUnusualRectType(false);
            return true;
        }
        if (button == 0 && rectUnusualHovered) {
            hudContextElement.setUnusualRectType(true);
            return true;
        }
        if (hudContextElement == targetHud) {
            float buttonY = targetHudMenuY + 25.0f;
            float particlesToggleX = targetHudMenuX + menuWidth - 21.0f;
            float particlesToggleY = targetHudMenuY + 4.0f;
            boolean normalHovered = HoveringUtils.isHovered(mouseX, mouseY, normalButtonX, buttonY, buttonW, buttonH);
            boolean unusualHovered = HoveringUtils.isHovered(mouseX, mouseY, unusualButtonX, buttonY, buttonW, buttonH);
            boolean particlesHovered = HoveringUtils.isHovered(mouseX, mouseY, particlesToggleX, particlesToggleY, 16.0f, 9.0f);
            if (button == 0 && normalHovered) {
                targetHud.setHealthBarStyleEnabled(false);
                return true;
            }
            if (button == 0 && unusualHovered) {
                targetHud.setHealthBarStyleEnabled(true);
                return true;
            }
            if (button == 0 && particlesHovered) {
                targetHud.setHeadParticlesEnabled(!targetHud.isHeadParticlesEnabled());
                return true;
            }
        } else if (hudContextElement == waterMark) {
            float baseY = targetHudMenuY + 4.5f;
            float toggleX = targetHudMenuX + menuWidth - 21.0f;
            boolean fpsHovered = HoveringUtils.isHovered(mouseX, mouseY, toggleX, baseY, 16.0f, 9.0f);
            boolean msHovered = HoveringUtils.isHovered(mouseX, mouseY, toggleX, baseY + 10.0f, 16.0f, 9.0f);
            boolean serverHovered = HoveringUtils.isHovered(mouseX, mouseY, toggleX, baseY + 20.0f, 16.0f, 9.0f);
            boolean tpsHovered = HoveringUtils.isHovered(mouseX, mouseY, toggleX, baseY + 30.0f, 16.0f, 9.0f);
            if (button == 0 && fpsHovered) {
                waterMark.setShowFps(!waterMark.isShowFps());
                return true;
            }
            if (button == 0 && msHovered) {
                waterMark.setShowMs(!waterMark.isShowMs());
                return true;
            }
            if (button == 0 && serverHovered) {
                waterMark.setShowServer(!waterMark.isShowServer());
                return true;
            }
            if (button == 0 && tpsHovered) {
                waterMark.setShowTps(!waterMark.isShowTps());
                return true;
            }
        }
        if (button == 0 || button == 1) {
            if (menuHovered) {
                return true;
            }
            if (hoveredElement != hudContextElement) {
                targetHudMenuOpen = false;
                pendingHudContextElement = null;
            }
        }
        return false;
    }
    public void renderHudContextMenu(DrawContext context, int mouseX, int mouseY) {
        if (hudContextElement != null && !isHudElementEnabled(hudContextElement)) {
            targetHudMenuOpen = false;
            hudContextElement = null;
            pendingHudContextElement = null;
        }
        targetHudMenuAnimation.update(targetHudMenuOpen ? 1.0f : 0.0f);
        float targetMenuProgress = MathHelper.clamp(targetHudMenuAnimation.getValue(), 0.0f, 1.0f);
        if (!targetHudMenuOpen && targetMenuProgress <= 0.01f) {
            if (pendingHudContextElement != null) {
                hudContextElement = pendingHudContextElement;
                pendingHudContextElement = null;
                targetHudMenuX = pendingTargetHudMenuX;
                targetHudMenuY = pendingTargetHudMenuY;
                targetHudMenuOpen = true;
            } else {
                hudContextElement = null;
            }
        }
        if (!targetHudMenuOpen && targetMenuProgress <= 0.01f && hudContextElement == null) {
            hudContextElement = null;
            return;
        }
        if (hudContextElement == null) {
            return;
        }
        boolean targetContext = hudContextElement == targetHud;
        boolean waterMarkContext = hudContextElement == waterMark;
        boolean notificationsContext = hudContextElement == notifications;
        float menuWidth = getTargetHudMenuWidth();
        float menuHeight = getTargetHudMenuHeight();
        clampTargetHudMenuToWindow(menuWidth, menuHeight);
        float x = targetHudMenuX;
        float y = targetHudMenuY;
        int themeColor = getThemeColor();
        float contentProgress = MathHelper.clamp((targetMenuProgress - 0.06f) / 0.94f, 0.0f, 1.0f);
        int textAlpha = fadeTextAlphaSafe(contentProgress, 255, 2);
        var matrices = context.getMatrices();
        matrices.push();
        RenderUtils.drawDefaultHudPanel(matrices, x, y, menuWidth, menuHeight, 3.0f, 3.5f,
                ColorUtils.applyAlpha(ColorUtils.rgba(50, 50, 50, 255), targetMenuProgress),
                ColorUtils.applyAlpha(ColorUtils.darken(themeColor, 0.15f), targetMenuProgress),
                ColorUtils.applyAlpha(ColorUtils.darken(themeColor, 0.05f), targetMenuProgress));
        if (contentProgress <= 0.02f) {
            matrices.pop();
            return;
        }
        float buttonGap = 3.0f;
        float buttonX = x + 5.0f;
        float buttonW = (menuWidth - 10.0f - buttonGap) / 2.0f;
        float buttonH = 10.0f;
        float normalX = buttonX;
        float unusualX = buttonX + buttonW + buttonGap;
        int inactiveColor = ColorUtils.applyAlpha(ColorUtils.rgba(70, 70, 70, 255), contentProgress);
        int activeLeftColor = fadeColorSafe(ColorUtils.darken(themeColor, 0.4f), contentProgress, 2);
        int activeRightColor = fadeColorSafe(themeColor, contentProgress, 2);
        if (targetContext) {
            issue(12).drawStringWithFade(matrices, "Партиклы с головы", x + 4.7f, y + 7.5f, menuWidth - 28.0f,
                    ColorUtils.rgba(255, 255, 255, textAlpha));
            targetHudParticlesBgAnimation.update(targetHud.isHeadParticlesEnabled() ? 1.0f : 0.0f);
            targetHudParticlesCircleAnimation.update(targetHud.isHeadParticlesEnabled() ? 1.0f : 0.0f);
            float bgProgress = targetHudParticlesBgAnimation.getValue();
            float circleProgress = targetHudParticlesCircleAnimation.getValue();
            int particlesOffColor = ColorUtils.darken(themeColor, 0.05f);
            int particlesColor = ColorUtils.interpolateColor(particlesOffColor, themeColor, bgProgress);
            float particlesToggleX = x + menuWidth - 21.0f;
            float particlesToggleY = y + 4.5f;
            RenderUtils.drawGradientRect(matrices, particlesToggleX, particlesToggleY, 16.0f, 9.0f, 3,
                    fadeColorSafe(particlesColor, contentProgress, 2), fadeColorSafe(ColorUtils.darken(particlesColor, 0.65f), contentProgress, 2));
            float particlesCircleX = particlesToggleX + 4.5f + (circleProgress * 6.2f);
            RenderUtils.drawRoundCircle(matrices, particlesCircleX + 0.5f, particlesToggleY + 4.5f, 6.85f,
                    ColorUtils.rgba(255, 255, 255, textAlpha));
            issue(12).draw(matrices, "Вид полоски", x + 4.7f, y + 18.0f,
                    ColorUtils.rgba(255, 255, 255, fadeTextAlphaSafe(contentProgress, 225, 2)));
            float buttonY = y + 25.0f;
            boolean healthBarStyle = targetHud.isHealthBarStyleEnabled();
            targetHudBarSwitchAnimation.update(healthBarStyle ? 1.0f : 0.0f);
            float typeSwitchProgress = MathHelper.clamp(targetHudBarSwitchAnimation.getValue(), 0.0f, 1.0f);
            RenderUtils.drawRoundedRect(matrices, normalX, buttonY, buttonW, buttonH, 1.5f, inactiveColor);
            RenderUtils.drawRoundedRect(matrices, unusualX, buttonY, buttonW, buttonH, 1.5f, inactiveColor);
            float activeX = MathHelper.lerp(typeSwitchProgress, normalX, unusualX);
            RenderUtils.drawGradientRect(matrices, activeX, buttonY, buttonW, buttonH, 1.5f, activeLeftColor, activeRightColor, true);
            String normalText = "Клиентский";
            String unusualText = "Здоровье";
            float normalTextX = normalX + (buttonW - issue(12).getWidth(normalText)) * 0.5f;
            float unusualTextX = unusualX + (buttonW - issue(12).getWidth(unusualText)) * 0.55f;
            int normalTextAlpha = MathHelper.clamp((int) (textAlpha * (0.65f + 0.35f * (1.0f - typeSwitchProgress))), 0, 255);
            int unusualTextAlpha = MathHelper.clamp((int) (textAlpha * (0.65f + 0.35f * typeSwitchProgress)), 0, 255);
            issue(12).draw(matrices, normalText, normalTextX, buttonY + 3.8f, ColorUtils.rgba(255, 255, 255, normalTextAlpha));
            issue(12).draw(matrices, unusualText, unusualTextX, buttonY + 3.8f, ColorUtils.rgba(255, 255, 255, unusualTextAlpha));
        } else if (waterMarkContext) {
            float wmToggleX = x + menuWidth - 21.0f;
            float wmBaseY = y + 3.5f;
            float wmLabelX = x + 5;
            drawWaterMarkToggle(matrices, "Отображать фпс", wmLabelX, wmToggleX, wmBaseY, waterMark.isShowFps(), waterMarkFpsBgAnimation, waterMarkFpsCircleAnimation, themeColor, contentProgress, textAlpha);
            drawWaterMarkToggle(matrices, "Отображать пинг", wmLabelX, wmToggleX, wmBaseY + 10.0f, waterMark.isShowMs(), waterMarkMsBgAnimation, waterMarkMsCircleAnimation, themeColor, contentProgress, textAlpha);
            drawWaterMarkToggle(matrices, "Отображать сервер", wmLabelX, wmToggleX, wmBaseY + 20.0f, waterMark.isShowServer(), waterMarkServerBgAnimation, waterMarkServerCircleAnimation, themeColor, contentProgress, textAlpha);
            drawWaterMarkToggle(matrices, "Отображать тпс", wmLabelX, wmToggleX, wmBaseY + 30.0f, waterMark.isShowTps(), waterMarkTpsBgAnimation, waterMarkTpsCircleAnimation, themeColor, contentProgress, textAlpha);
        } else if (notificationsContext) {
            float ntToggleX = x + menuWidth - 21.0f;
            float ntBaseY = y + 3.5f;
            float ntLabelX = x + 5;
        }
        issue(12).draw(matrices, "Тип ректа", x + 4.7f, targetContext ? y + 39.5f : (waterMarkContext ? y + 49.5f : y + 7.5f),
                ColorUtils.rgba(255, 255, 255, fadeTextAlphaSafe(contentProgress, 225, 2)));
        float rectButtonY = targetContext ? y + 46.0f : (waterMarkContext ? y + 56.0f : y + 14.0f);
        boolean unusualRect = hudContextElement.isUnusualRectType();
        hudRectTypeSwitchAnimation.update(unusualRect ? 1.0f : 0.0f);
        float rectSwitchProgress = MathHelper.clamp(hudRectTypeSwitchAnimation.getValue(), 0.0f, 1.0f);
        RenderUtils.drawRoundedRect(matrices, normalX, rectButtonY, buttonW, buttonH, 1.5f, inactiveColor);
        RenderUtils.drawRoundedRect(matrices, unusualX, rectButtonY, buttonW, buttonH, 1.5f, inactiveColor);
        float rectActiveX = MathHelper.lerp(rectSwitchProgress, normalX, unusualX);
        RenderUtils.drawGradientRect(matrices, rectActiveX, rectButtonY, buttonW, buttonH, 1.5f, activeLeftColor, activeRightColor, true);
        String normalRectText = "Обычный";
        String unusualRectText = "Необычный";
        float normalRectTextX = normalX + (buttonW - issue(12).getWidth(normalRectText)) * 0.5f;
        float unusualRectTextX = unusualX + (buttonW - issue(12).getWidth(unusualRectText)) * 0.5f;
        int normalRectAlpha = MathHelper.clamp((int) (textAlpha * (0.65f + 0.35f * (1.0f - rectSwitchProgress))), 0, 255);
        int unusualRectAlpha = MathHelper.clamp((int) (textAlpha * (0.65f + 0.35f * rectSwitchProgress)), 0, 255);
        issue(12).draw(matrices, normalRectText, normalRectTextX, rectButtonY + 3.8f, ColorUtils.rgba(255, 255, 255, normalRectAlpha));
        issue(12).draw(matrices, unusualRectText, unusualRectTextX, rectButtonY + 3.8f, ColorUtils.rgba(255, 255, 255, unusualRectAlpha));
        matrices.pop();
    }
    private void renderHudElement(InterfaceProcessing element, EventRender.Default event) {
        long start = PERF_DEBUG ? System.nanoTime() : 0L;
        element.draggable.beginRenderTilt(event.getContext().getMatrices());
        try {
            element.onRender(event);
        } finally {
            element.draggable.endRenderTilt(event.getContext().getMatrices());
            if (PERF_DEBUG) {
                long elapsed = System.nanoTime() - start;
                if (elapsed >= SLOW_HUD_ELEMENT_NANOS) {
                    logSlowHudElement(element, elapsed);
                }
            }
        }
    }

    private void logSlowHudElement(InterfaceProcessing element, long elapsedNanos) {
        String name = element.getClass().getSimpleName();
        long now = System.nanoTime();
        Long lastWarn = PERF_WARNINGS.get(name);
        if (lastWarn != null && now - lastWarn < PERF_WARN_COOLDOWN_NANOS) {
            return;
        }

        PERF_WARNINGS.put(name, now);
        System.out.println(String.format(Locale.ROOT,
                "[PerfDebug] Slow HUD element: Interface -> %s took %.2f ms",
                name,
                elapsedNanos / 1_000_000.0D));
    }

    private void drawWaterMarkToggle(net.minecraft.client.util.math.MatrixStack matrices,
                                     String label,
                                     float labelX,
                                     float toggleX,
                                     float toggleY,
                                     boolean enabled,
                                     AnimationUtils bgAnimation,
                                     AnimationUtils circleAnimation,
                                     int themeColor,
                                     float contentProgress,
                                     int textAlpha) {
        issue(12).draw(matrices, label, labelX, toggleY + 3.0f, ColorUtils.rgba(255, 255, 255, textAlpha));
        bgAnimation.update(enabled ? 1.0f : 0.0f);
        circleAnimation.update(enabled ? 1.0f : 0.0f);
        float bgProgress = bgAnimation.getValue();
        float circleProgress = circleAnimation.getValue();
        int offColor = ColorUtils.darken(themeColor, 0.05f);
        int toggleColor = ColorUtils.interpolateColor(offColor, themeColor, bgProgress);
        RenderUtils.drawGradientRect(matrices, toggleX, toggleY, 16.0f, 9.0f, 3,
                fadeColorSafe(toggleColor, contentProgress, 2),
                fadeColorSafe(ColorUtils.darken(toggleColor, 0.65f), contentProgress, 2));
        float circleX = toggleX + 4.5f + (circleProgress * 6.2f);
        RenderUtils.drawRoundCircle(matrices, circleX + 0.5f, toggleY + 4.5f, 6.85f, ColorUtils.rgba(255, 255, 255, textAlpha));
    }

    public Map<String, InterfaceProcessing> getConfigurableHudElements() {
        Map<String, InterfaceProcessing> elements = new LinkedHashMap<>();
        elements.put("waterMark", waterMark);
        elements.put("arrayList", arrayListHud);
        elements.put("keyBinds", keyBinds);
        elements.put("helperBinds", helperBinds);
        elements.put("potions", potions);
        elements.put("keyStrokes", keyStrokes);
        elements.put("notifications", notifications);
        elements.put("targetHud", targetHud);
        elements.put("session", session);
        elements.put("information", information);
        elements.put("staffList", staffList);
        return elements;
    }

    @EventLink(priority = Priority.LOWEST)
    public void onEvent(final EventRender.Default event) {
        boolean waveStyle = style.is("Wave");
        boolean modernStyle = style.is("Modern");
        boolean showWaterMark = hudModules.is("Ватермарка");
        boolean showArrayList = hudModules.is("Аррай лист");
        boolean showKeyBinds = hudModules.is("Горячие клавиши");
        boolean showHelperBinds = hudModules.is("Серверные бинды");
        boolean showPotions = hudModules.is("Зелья");
        boolean showKeyStrokes = hudModules.is("КейСтроки");
        boolean showInformation = hudModules.is("Информация");
        boolean showStaff = hudModules.is("Стафф");
        boolean showSession = hudModules.is("Сессия");
        boolean showNotifications = hudModules.is("Уведомления");
        boolean showTargetHud = hudModules.is("Таргет худ");

        if (mc != null && mc.getWindow() != null && mc.currentScreen instanceof ChatScreen) {
            Font hintFont = issue(18);
            float x = (mc.getWindow().getScaledWidth() * 0.5f) - (hintFont.getWidth(HUD_HINT_TEXT) * 0.5f);
            hintFont.draw(event.getContext().getMatrices(), HUD_HINT_TEXT, x, 40.0f, -1);
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            if (showWaterMark) renderHudElement(this.waterMark, event);
            if (showArrayList && (waveStyle || modernStyle)) renderHudElement(this.arrayListHud, event);
            if (showKeyBinds) renderHudElement(this.keyBinds, event);
            if (showHelperBinds) renderHudElement(this.helperBinds, event);
            if (showPotions) renderHudElement(this.potions, event);
            if (showKeyStrokes && waveStyle) renderHudElement(this.keyStrokes, event);
            if (showInformation) renderHudElement(this.information, event);
            if (showStaff) renderHudElement(this.staffList, event);
            if (showSession && waveStyle) renderHudElement(this.session, event);
            if (showNotifications) renderHudElement(this.notifications, event);
            if (showTargetHud) renderHudElement(this.targetHud, event);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
        if (!(mc.currentScreen instanceof ChatScreen)) {
            targetHudMenuOpen = false;
            pendingHudContextElement = null;
        }
    }
}

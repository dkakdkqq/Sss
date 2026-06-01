package fun.lumis.client.modules.impl.render.base.implement;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.ItemStack;
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
import fun.lumis.client.modules.impl.combat.Aura;
import fun.lumis.client.modules.impl.render.base.InterfaceProcessing;

import java.awt.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TargetHud extends InterfaceProcessing {
    private final AnimationUtils alphaAnimation = new AnimationUtils(0.0f, 9.0f, Easings.QUAD_OUT);
    private final AnimationUtils unusualRevealAnimation = new AnimationUtils(0.0f, 4.6f, Easings.QUAD_OUT);
    private final AnimationUtils hpAnimation = new AnimationUtils(1.0f, 9.2f, Easings.QUAD_OUT);
    private final AnimationUtils hpTrailAnimation = new AnimationUtils(1.0f, 7.4f, Easings.QUAD_OUT);
    private final AnimationUtils hpValueAnimation = new AnimationUtils(20.0f, 7.0f, Easings.QUAD_OUT);
    private final AnimationUtils ABValueAnimation = new AnimationUtils(0.0f, 7.0f, Easings.QUAD_OUT);
    private final AnimationUtils goldenHpAnimation = new AnimationUtils(0.0f, 9.2f, Easings.QUAD_OUT);
    private final AnimationUtils goldenHpTrailAnimation = new AnimationUtils(0.0f, 7.4f, Easings.QUAD_OUT);
    private final AnimationUtils goldenAlphaAnimation = new AnimationUtils(0.0f, 9.0f, Easings.QUAD_OUT);
    private final List<HeadParticle> headParticles = new ObjectArrayList<>();

    private LivingEntity lastTarget;
    private float maxAbsorption = 20.0f;
    private boolean headParticlesEnabled = true;
    private boolean healthBarStyleEnabled = false;
    private long lastParticleUpdateNs = System.nanoTime();
    private LivingEntity particleTarget;
    private int lastTargetHurtTime = 0;
    private int cachedBarThemeColor = ColorUtils.rgba(124, 91, 242, 255);
    private int cachedBarThemeColor2 = ColorUtils.rgba(93, 67, 175, 255);
    private Framebuffer burnBuffer;
    private int burnBufferWidth = -1;
    private int burnBufferHeight = -1;
    private final ItemStack[] displayItems = new ItemStack[6];
    private final ItemStack[] armorScratch = new ItemStack[4];

    public TargetHud(Draggable draggable) {
        super(draggable);
    }

    private Font issue(int size) {
        return Fonts.getFont("suisse", size);
    }

    public boolean isHeadParticlesEnabled() {
        return headParticlesEnabled;
    }

    public void setHeadParticlesEnabled(boolean headParticlesEnabled) {
        this.headParticlesEnabled = headParticlesEnabled;
        if (!headParticlesEnabled) {
            headParticles.clear();
        }
    }

    public boolean isHealthBarStyleEnabled() {
        return healthBarStyleEnabled;
    }

    public void setHealthBarStyleEnabled(boolean healthBarStyleEnabled) {
        this.healthBarStyleEnabled = healthBarStyleEnabled;
    }

    private int interpolateColor(int color1, int color2, float ratio) {
        ratio = MathHelper.clamp(ratio, 0.0f, 1.0f);
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int a1 = (color1 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF;
        int r = (int)(r1 + (r2 - r1) * ratio);
        int g = (int)(g1 + (g2 - g1) * ratio);
        int b = (int)(b1 + (b2 - b1) * ratio);
        int a = (int)(a1 + (a2 - a1) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void ensureBurnBuffer() {
        if (mc == null || mc.getWindow() == null) {
            return;
        }

        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();
        if (burnBuffer == null || burnBufferWidth != w || burnBufferHeight != h) {
            if (burnBuffer != null) {
                burnBuffer.delete();
            }
            burnBuffer = new SimpleFramebuffer(w, h, true);
            burnBufferWidth = w;
            burnBufferHeight = h;
        }
    }

    private int collectDisplayItems(LivingEntity target) {
        int armorCount = 0;
        for (ItemStack stack : target.getArmorItems()) {
            if (armorCount < armorScratch.length) {
                armorScratch[armorCount++] = stack;
            }
        }

        int count = 0;
        for (int i = armorCount - 1; i >= 0; i--) {
            displayItems[count++] = armorScratch[i];
            armorScratch[i] = ItemStack.EMPTY;
        }

        ItemStack mainHand = target.getMainHandStack();
        if (!mainHand.isEmpty()) {
            displayItems[count++] = mainHand;
        }

        ItemStack offHand = target.getOffHandStack();
        if (!offHand.isEmpty()) {
            displayItems[count++] = offHand;
        }

        return count;
    }

    private boolean beginBurnPassIfNeeded(boolean unusualAnimation) {
        if (!unusualAnimation) {
            return false;
        }
        ensureBurnBuffer();
        if (burnBuffer == null) {
            return false;
        }

        burnBuffer.setClearColor(0f, 0f, 0f, 0f);
        burnBuffer.clear();
        burnBuffer.beginWrite(false);
        return true;
    }

    private void applyHudTransform(MatrixStack matrices, float x, float y, float width, float height, float scaleX, float scaleY) {
        float centerX = x + width * 0.5f;
        float centerY = y + height * 0.5f;
        matrices.translate(centerX, centerY, 0.0f);
        matrices.scale(scaleX, scaleY, 1.0f);
        matrices.translate(-centerX, -centerY, 0.0f);
    }

    private static final class HeadParticle {
        float x;
        float y;
        float vx;
        float vy;
        float size;
        float age;
        float maxAge;
    }

    private void updateAndRenderHeadParticles(MatrixStack matrices, LivingEntity target, float headX, float headY, float headSize, float alpha, int themeColor) {
        if (target == null || alpha <= 0.02f) {
            headParticles.clear();
            particleTarget = target;
            lastTargetHurtTime = 0;
            return;
        }

        long now = System.nanoTime();
        float deltaTicks = MathHelper.clamp((now - lastParticleUpdateNs) / 1_000_000_000.0f * 60.0f, 0.2f, 3.0f);
        lastParticleUpdateNs = now;

        if (particleTarget != target) {
            headParticles.clear();
            particleTarget = target;
            lastTargetHurtTime = Math.max(0, target.hurtTime);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float centerX = headX + headSize * 0.5f;
        float centerY = headY + headSize * 0.5f;
        int hurtTime = Math.max(0, target.hurtTime);
        boolean spawnBurst = hurtTime > 0 && (hurtTime > lastTargetHurtTime || hurtTime % 3 == 0);
        lastTargetHurtTime = hurtTime;

        if (spawnBurst) {
            int burstCount = 1 + random.nextInt(2);
            for (int n = 0; n < burstCount && headParticles.size() < 14; n++) {
                float angle = (float) (random.nextDouble() * Math.PI * 2.0);
                float radius = random.nextFloat() * headSize * 0.24f;
                float spreadAngle = (float) (random.nextDouble() * Math.PI * 2.0);
                float speed = 0.58f + random.nextFloat() * 0.9f;

                HeadParticle p = new HeadParticle();
                p.x = centerX + MathHelper.cos(angle) * radius;
                p.y = centerY + MathHelper.sin(angle) * radius;
                p.vx = MathHelper.cos(spreadAngle) * speed + (p.x - centerX) * 0.025f;
                p.vy = MathHelper.sin(spreadAngle) * speed + (p.y - centerY) * 0.025f;
                p.size = 3.8f + random.nextFloat() * 1.4f;
                p.age = 0.0f;
                p.maxAge = 74.0f + random.nextFloat() * 42.0f;
                headParticles.add(p);
            }
        }

        for (int i = headParticles.size() - 1; i >= 0; i--) {
            HeadParticle p = headParticles.get(i);
            p.age += deltaTicks;
            if (p.age >= p.maxAge) {
                headParticles.remove(i);
                continue;
            }

            p.x += p.vx * deltaTicks;
            p.y += p.vy * deltaTicks;
            p.vx *= (float) Math.pow(0.975f, deltaTicks);
            p.vy *= (float) Math.pow(0.975f, deltaTicks);
            p.vy += 0.0012f * deltaTicks;

            float life = 1.0f - (p.age / p.maxAge);
            float smoothLife = life * life * (3.0f - 2.0f * life);
            float particleAlpha = alpha * smoothLife;
            if (particleAlpha <= 0.02f) {
                continue;
            }

            float drawX = p.x - p.size * 0.5f;
            float drawY = p.y - p.size * 0.5f;
            int coreColor = ColorUtils.applyAlpha(themeColor, particleAlpha * 0.58f);

            RenderUtils.drawRoundedRect(matrices, drawX, drawY, p.size, p.size, p.size * 0.45f, coreColor);
        }
    }

    private void drawTargetHudItem(EventRender.Default eventRender, MatrixStack matrices, ItemStack stack,
                                   float slotX, float slotY, float itemScale) {
        if (stack.isEmpty()) return;

        matrices.push();
        matrices.translate(slotX + 4.0f, slotY + 4.0f, 0);
        matrices.scale(itemScale, itemScale, 1f);
        matrices.translate(-4, -4, 0);
        eventRender.getContext().drawItem(stack, 0, 0);
        matrices.pop();
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        if (!ModuleClass.interfaceModule.style.is("Wave")) DefaultStyle(eventRender);
        else WaveStyle(eventRender);
        super.onRender(eventRender);
    }

    public void DefaultStyle(EventRender.Default eventRender) {
        if (mc.player == null) {
            headParticles.clear();
            lastTargetHurtTime = 0;
            draggable.setWidth(0);
            draggable.setHeight(0);
            return;
        }

        Aura aura = ModuleClass.INSTANCE.aura;
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        LivingEntity auraTarget = aura != null ? aura.getTarget() : null;
        boolean showTargetHud = chatOpen || auraTarget != null;

        alphaAnimation.setSpeed(showTargetHud ? 9.0f : 5.0f);
        alphaAnimation.update(showTargetHud ? 1.0f : 0.0f);
        float alpha = MathHelper.clamp(alphaAnimation.getValue(), 0.0f, 1.0f);
        float renderProgress = alpha;

        if (showTargetHud) {
            lastTarget = chatOpen ? mc.player : auraTarget;
        }

        LivingEntity target = showTargetHud ? (chatOpen ? mc.player : auraTarget) : lastTarget;
        if (target == null || renderProgress <= 0.01f) {
            headParticles.clear();
            lastTargetHurtTime = 0;
            draggable.setWidth(0);
            draggable.setHeight(0);
            goldenAlphaAnimation.setValue(0.0f);
            ABValueAnimation.setValue(0.0f);
            goldenHpAnimation.setValue(0.0f);
            goldenHpTrailAnimation.setValue(0.0f);
            return;
        }

        float currentAbsorption = target.getAbsorptionAmount();
        boolean hasAbsorption = currentAbsorption > 0;

        goldenAlphaAnimation.setSpeed(hasAbsorption ? 9.0f : 5.0f);
        goldenAlphaAnimation.update(hasAbsorption ? 1.0f : 0.0f);
        float goldenAlpha = MathHelper.clamp(goldenAlphaAnimation.getValue(), 0.0f, 1.0f);

        float x = draggable.getX();
        float y = draggable.getY();
        int colorTheme;
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            colorTheme = Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            colorTheme = ColorUtils.getThemeColor();
        }
        int colorTheme2;
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            colorTheme2 = ColorUtils.darken(Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0], 0.4f);
        } else {
            colorTheme2 = ColorUtils.getThemeColor();
        }
        if (showTargetHud) {
            cachedBarThemeColor = colorTheme;
            cachedBarThemeColor2 = colorTheme2;
        }
        int barThemeColor = showTargetHud ? colorTheme : cachedBarThemeColor;
        int barThemeColor2 = showTargetHud ? colorTheme2 : cachedBarThemeColor2;
        float maxHealth = Math.max(1.0f, target.getMaxHealth());
        float maxAB = Math.max(1.0f, maxAbsorption);
        float targetHealthForAnim = showTargetHud ? target.getHealth() : 0.0f;
        float targetABForAnim = showTargetHud ? currentAbsorption : 0.0f;
        hpValueAnimation.update(targetHealthForAnim);
        ABValueAnimation.update(targetABForAnim);
        float animatedHealthValue = MathHelper.clamp(hpValueAnimation.getValue(), 0.0f, maxHealth);
        float animatedABValue = MathHelper.clamp(ABValueAnimation.getValue(), 0.0f, maxAB);

        float healthProgress = MathHelper.clamp(targetHealthForAnim / maxHealth, 0.0f, 1.0f);
        hpAnimation.update(healthProgress);
        float hpProgressAnimated = MathHelper.clamp(hpAnimation.getValue(), 0.0f, 1.0f);
        if (hpProgressAnimated > hpTrailAnimation.getValue()) {
            hpTrailAnimation.setValue(MathHelper.lerp(0.78f, hpTrailAnimation.getValue(), hpProgressAnimated));
        } else {
            hpTrailAnimation.update(hpProgressAnimated);
        }
        float hpTrailProgressAnimated = MathHelper.clamp(hpTrailAnimation.getValue(), 0.0f, 1.0f);
        boolean hidingHud = !showTargetHud;
        if (hidingHud) {
            hpTrailProgressAnimated = hpProgressAnimated;
        }

        float absorptionProgress = MathHelper.clamp(currentAbsorption / maxAB, 0.0f, 1.0f);
        goldenHpAnimation.update(absorptionProgress);
        float goldenProgressAnimated = MathHelper.clamp(goldenHpAnimation.getValue(), 0.0f, 1.0f);

        if (goldenProgressAnimated > goldenHpTrailAnimation.getValue()) {
            goldenHpTrailAnimation.setValue(MathHelper.lerp(0.78f, goldenHpTrailAnimation.getValue(), goldenProgressAnimated));
        } else {
            goldenHpTrailAnimation.update(goldenProgressAnimated);
        }
        float goldenTrailProgressAnimated = MathHelper.clamp(goldenHpTrailAnimation.getValue(), 0.0f, 1.0f);
        if (hidingHud || !hasAbsorption) {
            goldenTrailProgressAnimated = goldenProgressAnimated;
        }

        String name = target.getName().getString();
        String hpText = "HP: " + String.format("%.1f", animatedHealthValue);
        String abText = " (" + String.format("%.1f", animatedABValue) + ")";

        float padding = 4.0f;
        float headSize = 27.5f;
        float gap = 5.0f;
        float rightPad = 6.0f;
        float textX = x + padding + headSize + gap - 5;
        float textWidth = Math.max(issue(13).getWidth(name), issue(12).getWidth(hpText));
        float width = Math.max(92.5f, padding + headSize + gap + textWidth + rightPad) - 3.5f;
        float height = 32.0f;
        float headX = x + padding;
        float headY = y + 3.5f;
        float textMaxWidth = Math.max(10.0f, width - (textX - x) - rightPad) + 2;

        MatrixStack matrices = eventRender.getContext().getMatrices();
        float drawAlpha = alpha;
        float hpBarAlpha = drawAlpha;
        boolean drawSquares = isUnusualRectType();
        int drawAlphaInt = (int) (255.0f * drawAlpha);

        matrices.push();

        RenderUtils.drawDefaultHudPanel(matrices, x, y, width, height, 4, 4.5f,
                ColorUtils.applyAlpha(ColorUtils.rgba(50, 50, 50, 255), drawAlpha),
                ColorUtils.applyAlpha(ColorUtils.darken(colorTheme, 0.15f), drawAlpha),
                ColorUtils.applyAlpha(ColorUtils.darken(colorTheme, 0.05f), drawAlpha));
        if (drawSquares && drawAlpha > 0.06f) {
            RenderUtils.drawHudSquarePattern(matrices, x + 8, y, width, height, ColorUtils.applyAlpha(barThemeColor, drawAlpha));
        }

        if (headParticlesEnabled) {
            updateAndRenderHeadParticles(matrices, target, headX - 1.85f, headY - 1.0f, headSize, drawAlpha, barThemeColor);
        } else {
            headParticles.clear();
        }

        if (target instanceof PlayerEntity playerEntity) {
            RenderUtils.drawPlayerHead(matrices, playerEntity.getUuid(), headX - 1.85f, headY - 1, headSize, 3.5f, drawAlpha, 0.0f);
        } else {
            RenderUtils.drawTargetHudDefaultPlaceholder(matrices, headX - 1.85f, headY - 1, drawAlpha);
            issue(26).drawCenteredString(matrices, "?", headX + headSize / 2.25f, headY + 7.5,
                    ColorUtils.rgba(220, 220, 220, drawAlphaInt));
        }

        issue(14).drawStringWithFade(matrices, name, textX + 0.7f, y + 5.5f, textMaxWidth, ColorUtils.rgba(255, 255, 255, drawAlphaInt));

        issue(13).drawStringWithFade(matrices, hpText, textX + 1, y + 14.5f, textMaxWidth, ColorUtils.rgba(232, 232, 232, drawAlphaInt));

        if (goldenAlpha > 0.01f) {
            float hpTextWidth = issue(13).getWidth(hpText);
            float abTextX = textX + 1 + hpTextWidth;
            int goldenAlphaInt = (int) (255.0f * goldenAlpha * alpha);
            issue(13).drawGradientStringHorizontal(matrices, abText, abTextX, y + 14.5f, ColorUtils.rgba(236, 183, 39, goldenAlphaInt), ColorUtils.rgba(147, 108, 16, goldenAlphaInt));
        }

        float barX = textX;
        float barY = y + height - 10.45f;
        float barW = Math.max(19.0f, width - rightPad - (barX - x));
        float barH = 3.85f;

        float hpRatio = healthProgress;

        int barBgLeft;
        int barBgRight;
        int barTrailLeft;
        int barTrailRight;
        int barFillLeft;
        int barFillRight;

        if (healthBarStyleEnabled) {
            int redDarkBg = ColorUtils.rgba(70, 5, 10, (int)(115.0f * hpBarAlpha));
            int greenDarkBg = ColorUtils.rgba(0, 70, 0, (int)(115.0f * hpBarAlpha));
            int redBrightBg = ColorUtils.rgba(155, 5, 15, (int)(115.0f * hpBarAlpha));
            int greenBrightBg = ColorUtils.rgba(55, 205, 15, (int)(115.0f * hpBarAlpha));
            barBgLeft = interpolateColor(redDarkBg, greenDarkBg, hpRatio);
            barBgRight = interpolateColor(redBrightBg, greenBrightBg, hpRatio);

            int redDarkTrail = ColorUtils.rgba(70, 5, 10, (int)(200.0f * hpBarAlpha));
            int greenDarkTrail = ColorUtils.rgba(0, 70, 0, (int)(200.0f * hpBarAlpha));
            int redBrightTrail = ColorUtils.rgba(155, 5, 15, (int)(200.0f * hpBarAlpha));
            int greenBrightTrail = ColorUtils.rgba(55, 205, 15, (int)(200.0f * hpBarAlpha));
            barTrailLeft = interpolateColor(redDarkTrail, greenDarkTrail, hpRatio);
            barTrailRight = interpolateColor(redBrightTrail, greenBrightTrail, hpRatio);

            int redDarkFill = ColorUtils.rgba(70, 5, 10, (int)(250.0f * hpBarAlpha));
            int greenDarkFill = ColorUtils.rgba(0, 70, 0, (int)(250.0f * hpBarAlpha));
            int redBrightFill = ColorUtils.rgba(155, 5, 15, (int)(250.0f * hpBarAlpha));
            int greenBrightFill = ColorUtils.rgba(55, 205, 15, (int)(250.0f * hpBarAlpha));
            barFillLeft = interpolateColor(redDarkFill, greenDarkFill, hpRatio);
            barFillRight = interpolateColor(redBrightFill, greenBrightFill, hpRatio);
        } else {
            barBgLeft = ColorUtils.applyAlpha(ColorUtils.darken(barThemeColor2, 0.72f), hpBarAlpha * 0.26f);
            barBgRight = ColorUtils.applyAlpha(ColorUtils.darken(barThemeColor, 0.72f), hpBarAlpha * 0.26f);
            barTrailLeft = ColorUtils.applyAlpha(ColorUtils.darken(barThemeColor2, 0.9f), hpBarAlpha * 0.42f);
            barTrailRight = ColorUtils.applyAlpha(ColorUtils.darken(barThemeColor, 0.9f), hpBarAlpha * 0.42f);
            barFillLeft = ColorUtils.applyAlpha(barThemeColor2, hpBarAlpha);
            barFillRight = ColorUtils.applyAlpha(barThemeColor, hpBarAlpha);
        }

        if (hpBarAlpha > 0.025f) {
            RenderUtils.drawGradientRect(matrices, barX, barY, barW + 3, barH + 4.25f, 1.95f, barBgLeft, barBgRight, true);

            if (!hidingHud) {
                float trailProgressDraw = MathHelper.lerp(0.58f, hpTrailProgressAnimated, hpProgressAnimated);
                float trailW = barW * trailProgressDraw;
                if (trailW > 1.15f) {
                    RenderUtils.drawGradientRect(matrices, barX, barY, trailW + 3, barH + 4.25f, 1.95f, barTrailLeft, barTrailRight, true);
                }
            }

            float filledW = barW * hpProgressAnimated;
            if (filledW > 1.15f) {
                RenderUtils.drawGradientRect(matrices, barX, barY, filledW + 3, barH + 4.25f, 1.95f, barFillLeft, barFillRight, true);
            }

            if (goldenAlpha > 0.01f) {
                float goldenBarAlpha = goldenAlpha * hpBarAlpha;

                int goldenBaseLeft = ColorUtils.rgba(147, 108, 16, 255);
                int goldenBaseRight = ColorUtils.rgba(236, 183, 39, 255);
                int goldenTrailLeft = ColorUtils.applyAlpha(ColorUtils.darken(goldenBaseLeft, 0.7f), goldenBarAlpha * 0.5f);
                int goldenTrailRight = ColorUtils.applyAlpha(ColorUtils.darken(goldenBaseRight, 0.7f), goldenBarAlpha * 0.5f);
                int goldenFillLeft = ColorUtils.applyAlpha(ColorUtils.darken(goldenBaseLeft, 0.7f), goldenBarAlpha);
                int goldenFillRight = ColorUtils.applyAlpha(goldenBaseRight, goldenBarAlpha);

                if (!hidingHud && hasAbsorption) {
                    float goldenTrailProgressDraw = MathHelper.lerp(0.58f, goldenTrailProgressAnimated, goldenProgressAnimated);
                    float goldenTrailW = barW * goldenTrailProgressDraw;
                    if (goldenTrailW > 1.15f) {
                        RenderUtils.drawGradientRect(matrices, barX, barY, goldenTrailW + 3, barH + 4.25f, 1.95f, goldenTrailLeft, goldenTrailRight, true);
                    }
                }

                float goldenFilledW = barW * goldenProgressAnimated;
                if (goldenFilledW > 1.15f) {
                    RenderUtils.drawGradientRect(matrices, barX, barY, goldenFilledW + 3, barH + 4.25f, 1.95f, goldenFillLeft, goldenFillRight, true);
                }
            }
        }

        float itemY = y - 11.5f;
        float itemSpacing = 9;
        float itemScale = 0.5f * alpha;

        int totalSlots = collectDisplayItems(target);
        float itemX = x + width - (totalSlots * itemSpacing) - 3f;

        for (int itemIndex = 0; itemIndex < totalSlots; itemIndex++) {
            ItemStack stack = displayItems[itemIndex];
            if (!stack.isEmpty()) {
                float slotX = itemX + itemIndex * itemSpacing;
                drawTargetHudItem(eventRender, matrices, stack, slotX, itemY, itemScale);
            }
            displayItems[itemIndex] = ItemStack.EMPTY;
        }

        matrices.pop();

        draggable.setWidth(width);
        draggable.setHeight(height);
    }

    private final AnimationUtils scaleAnimation = new AnimationUtils(0.0f, 12.0f, Easings.BACK_OUT);
    private final AnimationUtils hideScaleAnimation = new AnimationUtils(1.0f, 8.0f, Easings.BACK_IN);
    private boolean wasVisible = false;

    public void WaveStyle(EventRender.Default eventRender) {
        if (mc.player == null) {
            draggable.setWidth(0);
            draggable.setHeight(0);
            return;
        }

        Aura aura = ModuleClass.INSTANCE.aura;
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        LivingEntity auraTarget = aura != null ? aura.getTarget() : null;
        boolean showTargetHud = chatOpen || auraTarget != null;
        alphaAnimation.update(showTargetHud ? 1.0f : 0.0f);

        if (showTargetHud && !wasVisible) {
            scaleAnimation.setValue(0.0f);
            hideScaleAnimation.setValue(1.0f);
            wasVisible = true;
        } else if (!showTargetHud && wasVisible) {
            hideScaleAnimation.setValue(1.0f);
            wasVisible = false;
        }

        if (showTargetHud) {
            scaleAnimation.update(1.0f);
            lastTarget = chatOpen ? mc.player : auraTarget;
        } else {
            hideScaleAnimation.update(0.0f);
        }

        float scale;
        if (showTargetHud) {
            scale = MathHelper.clamp(scaleAnimation.getValue(), 0.0f, 1.0f);
        } else {
            scale = MathHelper.clamp(hideScaleAnimation.getValue(), 0.0f, 1.0f);
        }
        float progress = scale;

        LivingEntity target = showTargetHud ? (chatOpen ? mc.player : auraTarget) : lastTarget;
        if (target == null || progress <= 0.01f) {
            draggable.setWidth(0);
            draggable.setHeight(0);
            return;
        }

        float x = draggable.getX(), y = draggable.getY();
        float padding = 3f;
        float width = 110;
        float height = 46;

        float health = target.getHealth();
        float maxHealth = Math.max(1.0f, target.getMaxHealth());
        hpValueAnimation.update(health);
        float animatedHealthValue = MathHelper.clamp(hpValueAnimation.getValue(), 0.0f, maxHealth);
        float healthProgress = MathHelper.clamp(health / maxHealth, 0.0f, 1.0f);
        hpAnimation.update(healthProgress);
        float hpProgressAnimated = MathHelper.clamp(hpAnimation.getValue(), 0.0f, 1.0f);

        if (hpProgressAnimated > hpTrailAnimation.getValue()) {
            hpTrailAnimation.setValue(hpProgressAnimated);
        } else {
            hpTrailAnimation.update(hpProgressAnimated);
        }

        MatrixStack matrices = eventRender.getContext().getMatrices();
        matrices.push();
        applyHudTransform(matrices, x, y, width, height, scale, scale);
        float visualAlpha = progress;
        int alphaInt = (int)(255 * visualAlpha);

        float entityBoxSize = height - padding * 2 - 4;

        RenderUtils.drawTargetHudWaveFrame(matrices, x, y, width, height, padding, entityBoxSize, visualAlpha);

        matrices.pop();

        int entityX = (int)(x + padding + 3f);
        int entityY = (int)(y + padding + 3f);
        int entityX2 = (int)(x + padding + 3f + entityBoxSize - 2);
        int entityY2 = (int)(y + padding + 3f + entityBoxSize - 2);
        int entitySize = (int)(15 * progress);

        float entityCenterX = x + padding + 3f + (entityBoxSize - 2) / 2;
        float entityCenterY = y + padding + 3f + (entityBoxSize - 2) / 3;

        float yaw = target.bodyYaw;
        float pitch = target.getPitch();

        double yawRadians = Math.toRadians(yaw + 180);
        float lookX = entityCenterX + (float)(Math.sin(yawRadians) * 50);
        float lookY = entityCenterY - pitch;

        if (progress > 0.5f) {
            InventoryScreen.drawEntity(
                    eventRender.getContext(),
                    entityX,
                    entityY,
                    entityX2,
                    entityY2,
                    entitySize,
                    0.0f,
                    lookX,
                    lookY,
                    target
            );
        }

        matrices.push();
        applyHudTransform(matrices, x, y, width, height, scale, scale);

        String name = target.getName().getString();
        float textX = x + padding + entityBoxSize + 6;
        float waveNameFadeMaxWidth = Math.max(8.0f, (x + width - padding - 4.0f) - textX);

        issue(14).drawStringWithFade(matrices, name, textX, y + padding + 5, waveNameFadeMaxWidth, ColorUtils.rgba(255, 255, 255, alphaInt));
        issue(14).draw(matrices, "HP: " + String.format("%.1f", animatedHealthValue) + " | Dist: " + (int) target.distanceTo(mc.player), textX, y + padding + 20, ColorUtils.rgba(255, 255, 255, alphaInt));

        float heartsX = textX;
        float heartsY = y + padding + 15;
        float heartSize = 5f;
        float heartSpacing = 0.5f;
        int totalHearts = 10;
        float healthPerHeart = maxHealth / totalHearts;
        float currentHealth = health;

        int heartColor;
        if (health <= maxHealth * 0.25f) {
            heartColor = ColorUtils.rgba(255, 50, 50, alphaInt);
        } else if (health <= maxHealth * 0.5f) {
            heartColor = ColorUtils.rgba(255, 220, 0, alphaInt);
        } else {
            heartColor = ColorUtils.rgba(0, 255, 0, alphaInt);
        }

        int shadowColor = ColorUtils.applyAlpha(heartColor, visualAlpha * 0.5f);

        for (int i = 0; i < totalHearts; i++) {
            float hx = heartsX + i * (heartSize + heartSpacing);
            float hy = heartsY;

            RenderUtils.drawTargetHudHeartBase(matrices, hx, hy - 3, visualAlpha);

            if (currentHealth > 0) {
                float fillAmount = MathHelper.clamp(currentHealth / healthPerHeart, 0.0f, 1.0f);
                float filledWidth = heartSize * fillAmount;

                if (filledWidth > 0) {
                    RenderUtils.drawTargetHudHeartFill(matrices, hx, hy - 3, filledWidth, heartColor, shadowColor);
                }

                currentHealth -= healthPerHeart;
            }
        }

        float itemX = textX - 1;
        float itemY = y + padding + 28;
        float itemSpacing = 10;
        int waveThemeColor = !Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")
                ? Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0]
                : ColorUtils.getThemeColor();
        int waveSlotBorderColor = ColorUtils.applyAlpha(ColorUtils.rgba(50, 50, 50, 255), visualAlpha);
        int waveSlotTopColor = ColorUtils.applyAlpha(ColorUtils.darken(waveThemeColor, 0.15f), visualAlpha);
        int waveSlotBottomColor = ColorUtils.applyAlpha(ColorUtils.darken(waveThemeColor, 0.05f), visualAlpha);

        int totalSlots = collectDisplayItems(target);

        if (totalSlots > 0) {
            float containerX = itemX - 0.85f;
            float containerY = itemY - 0.85f;
            float containerW = (totalSlots - 1) * itemSpacing + 9.8f;
            float containerH = 9.8f;
        }

        float itemScale = 0.5f;
        for (int itemIndex = 0; itemIndex < totalSlots; itemIndex++) {
            ItemStack stack = displayItems[itemIndex];
            if (!stack.isEmpty()) {
                float slotX = itemX + itemIndex * itemSpacing;
                drawTargetHudItem(eventRender, matrices, stack, slotX, itemY, itemScale);
            }
            displayItems[itemIndex] = ItemStack.EMPTY;
        }

        matrices.pop();

        draggable.setWidth(width);
        draggable.setHeight(height);
    }
}


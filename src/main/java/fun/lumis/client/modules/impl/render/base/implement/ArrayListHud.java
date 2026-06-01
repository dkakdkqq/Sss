package fun.lumis.client.modules.impl.render.base.implement;

import net.minecraft.client.util.math.MatrixStack;
import fun.lumis.Lumis;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.draggable.Draggable;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.render.base.InterfaceProcessing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArrayListHud extends InterfaceProcessing {

    private static final float LINE_HEIGHT = 9.5f;
    private static final float FLOW_SPEED = 1000f;
    private static final Comparator<ModuleEntry> MODULE_WIDTH_COMPARATOR = Comparator.comparingDouble((ModuleEntry entry) -> entry.width).reversed();

    private final List<ModuleEntry> visibleModules = new ArrayList<>();

    public ArrayListHud(Draggable draggable) {
        super(draggable);
    }

    private Font font() {
        return Fonts.getFont("suisse", 14);
    }

    private void drawFlowingText(MatrixStack matrices, Font font, String text, float x, float y, int color, float alphaMul) {
        int textColor = ColorUtils.setAlphaColor(color, (int) (255 * alphaMul));
        font.draw(matrices, text, x, y, textColor);
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        if (ModuleClass.interfaceModule.style.is("Modern")) {
            renderModern(eventRender);
            return;
        }
        final MatrixStack matrices = eventRender.getContext().getMatrices();
        final Font font = font();
        final List<Module> modules = ModuleClass.INSTANCE.getObject();
        visibleModules.clear();
        for (Module module : modules) {
            module.getArrayAnimka().update(module.isEnable() ? 1.0f : 0.0f);
            float anim = module.getArrayAnimka().getValue();
            if (anim <= 0.03f) continue;

            String displayName = module.getDisplayName();
            visibleModules.add(new ModuleEntry(displayName.toLowerCase(), font.getWidth(displayName), anim));
        }
        visibleModules.sort(MODULE_WIDTH_COMPARATOR);
        final long now = System.currentTimeMillis();

        float x = draggable.getX();
        float y = draggable.getY();
        float maxWidth = 0.0f;
        boolean leftHalf = x <= mc.getWindow().getScaledWidth() * 0.5f;

        for (ModuleEntry entry : visibleModules) {
            maxWidth = Math.max(maxWidth, entry.width);
        }

        float yOffset = 0.0f;
        for (int i = 0; i < visibleModules.size(); i++) {
            ModuleEntry entry = visibleModules.get(i);
            float anim = entry.anim;
            float lineStep = LINE_HEIGHT * anim;

            int indexShift = (int) ((now * FLOW_SPEED) / 10.0f) + i * 42;
            int rowColor = ColorUtils.getThemeColor(indexShift);
            int rowColor2 = ColorUtils.getThemeColor(indexShift + 90);
            int glowAlpha = (int) ((leftHalf ? 140 : 170) * anim);
            int glow1 = ColorUtils.setAlphaColor(rowColor, glowAlpha);
            int glow2 = ColorUtils.setAlphaColor(rowColor2, glowAlpha);

            float textWidth = entry.width;
            float drawX;
            if (leftHalf) {
                drawX = x - 3.0f;
            } else {
                drawX = x + (maxWidth - textWidth) - 3.0f;
            }
            float drawY = y + yOffset + (1.0f - anim) * 7.0f;

            float shadowX = leftHalf ? drawX - 0.6f : drawX - 1.5f;
            float shadowW = leftHalf ? textWidth - 4.0f : textWidth;
            RenderUtils.drawShadow(matrices, shadowX, drawY, shadowW, 6.0f, 5, 11, glow2, glow2, glow1, glow1);
            float textX = leftHalf ? drawX - 0.8f : drawX - 2.0f;
            drawFlowingText(matrices, font, entry.lowerName, textX, drawY + 1.5f, rowColor, anim);

            yOffset += lineStep;
        }

        if (yOffset > 0.5f) {
            float lineX = leftHalf ? x - 6.5f : x + maxWidth - 7;
            float lineWidth = 2.5f;
            int topLineColor = ColorUtils.setAlphaColor(ColorUtils.getThemeColor(0), 220);
            int bottomLineColor = ColorUtils.setAlphaColor(ColorUtils.getThemeColor(180), 220);

            RenderUtils.drawGradientRect(matrices, lineX, y, lineWidth, yOffset - 2.0f, 0, topLineColor, bottomLineColor);
        }

        draggable.setWidth(maxWidth + 4.0f);
        draggable.setHeight(yOffset);

        super.onRender(eventRender);
    }

    private void renderModern(EventRender.Default eventRender) {
        final MatrixStack matrices = eventRender.getContext().getMatrices();
        final Font font = font();
        final List<Module> modules = ModuleClass.INSTANCE.getObject();
        visibleModules.clear();
        for (Module module : modules) {
            module.getArrayAnimka().update(module.isEnable() ? 1.0f : 0.0f);
            float anim = module.getArrayAnimka().getValue();
            if (anim <= 0.03f) continue;
            String displayName = module.getDisplayName();
            visibleModules.add(new ModuleEntry(displayName, font.getWidth(displayName), anim));
        }
        visibleModules.sort(MODULE_WIDTH_COMPARATOR);
        final long now = System.currentTimeMillis();

        float x = draggable.getX();
        float y = draggable.getY();
        boolean leftHalf = x <= mc.getWindow().getScaledWidth() * 0.5f;

        float maxTextWidth = 0.0f;
        for (ModuleEntry entry : visibleModules) {
            maxTextWidth = Math.max(maxTextWidth, entry.width());
        }

        float pad = 5.0f;
        float barW = 2.0f;
        float rowH = 13.0f;
        float gap = 1.5f;
        float fullW = maxTextWidth + pad * 2.0f + barW + 2.0f;

        float yOffset = 0.0f;
        for (int i = 0; i < visibleModules.size(); i++) {
            ModuleEntry entry = visibleModules.get(i);
            float anim = entry.anim();
            float textW = entry.width();
            float cardW = textW + pad * 2.0f + barW + 2.0f;

            int shift = (int) ((now * FLOW_SPEED) / 10.0f) + i * 42;
            int c1 = ColorUtils.getThemeColor(shift);
            int c2 = ColorUtils.getThemeColor(shift + 120);

            float slide = (1.0f - anim) * 14.0f;
            int bgAlpha = (int) (190 * anim);
            int barAlpha = (int) (255 * anim);
            int textColor = ColorUtils.setAlphaColor(-1, (int) (255 * anim));

            float cardX, barX, textX;
            if (leftHalf) {
                cardX = x - slide;
                barX = cardX;
                textX = cardX + barW + pad;
            } else {
                float anchorRight = x + fullW;
                cardX = anchorRight - cardW + slide;
                barX = cardX + cardW - barW;
                textX = cardX + pad;
            }
            float cardY = y + yOffset;

            int bg = ColorUtils.setAlphaColor(ColorUtils.rgba(14, 14, 18, 255), bgAlpha);
            RenderUtils.drawShadow(matrices, cardX, cardY, cardW, rowH, 4.0f, 8.0f, ColorUtils.setAlphaColor(c1, (int) (70 * anim)));
            RenderUtils.drawRoundedRect(matrices, cardX, cardY, cardW, rowH, 3.0f, bg);
            RenderUtils.drawGradientRect(matrices, barX, cardY + 2.0f, barW, rowH - 4.0f, 1.0f,
                    ColorUtils.setAlphaColor(c1, barAlpha), ColorUtils.setAlphaColor(c2, barAlpha));
            font.draw(matrices, entry.lowerName(), textX, cardY + (rowH - 6.0f) / 2.0f, textColor);

            yOffset += (rowH + gap) * anim;
        }

        draggable.setWidth(fullW);
        draggable.setHeight(Math.max(0.0f, yOffset - gap));
        super.onRender(eventRender);
    }

    private record ModuleEntry(String lowerName, float width, float anim) {
    }
}

package fun.lumis.client.modules.impl.render.base.implement;

import net.minecraft.client.MinecraftClient;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.draggable.Draggable;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.impl.render.base.InterfaceProcessing;

import java.util.ArrayList;
import java.util.List;

public class KeyStrokes extends InterfaceProcessing {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final List<Long> leftClicks = new ArrayList<>();
    private boolean wasLmbPressed = false;

    public KeyStrokes(Draggable draggable) {
        super(draggable);
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        float x = draggable.getX(), y = draggable.getY();

        var font = Fonts.getFont("suisse", 15);
        var smallFont = Fonts.getFont("suisse", 10);

        float keySize = 20;
        float gap = 2;

        boolean wPressed = mc.options.forwardKey.isPressed();
        boolean aPressed = mc.options.leftKey.isPressed();
        boolean sPressed = mc.options.backKey.isPressed();
        boolean dPressed = mc.options.rightKey.isPressed();
        boolean spacePressed = mc.options.jumpKey.isPressed();
        boolean lmbPressed = mc.options.attackKey.isPressed();
        boolean rmbPressed = mc.options.useKey.isPressed();

        long currentTime = System.currentTimeMillis();

        if (lmbPressed && !wasLmbPressed) {
            leftClicks.add(currentTime);
        }
        wasLmbPressed = lmbPressed;

        leftClicks.removeIf(time -> currentTime - time > 1000);

        int lmbCps = leftClicks.size();

        float wX = x + keySize + gap;
        float wY = y;
        drawKey(eventRender, wX, wY, keySize, keySize, "W", wPressed, font);

        float aX = x;
        float aY = y + keySize + gap;
        drawKey(eventRender, aX, aY, keySize, keySize, "A", aPressed, font);

        float sX = x + keySize + gap;
        float sY = y + keySize + gap;
        drawKey(eventRender, sX, sY, keySize, keySize, "S", sPressed, font);

        float dX = x + (keySize + gap) * 2;
        float dY = y + keySize + gap;
        drawKey(eventRender, dX, dY, keySize, keySize, "D", dPressed, font);

        float spaceWidth = keySize * 3 + gap * 2;
        float spaceHeight = 20;
        float spaceX = x;
        float spaceY = y + (keySize + gap) * 2;
        drawKey(eventRender, spaceX, spaceY, spaceWidth, spaceHeight, "Space", spacePressed, font);

        float mouseWidth = (spaceWidth - gap) / 2;
        float mouseHeight = 20;
        float lmbX = x;
        float lmbY = y + (keySize + gap) * 2 + spaceHeight + gap;

        float time = (System.currentTimeMillis() % 2000) / 2000f * 360f;
        int themeColor = ColorUtils.getThemeColor((int) time);

        drawKeyWithCps(eventRender, lmbX, lmbY, mouseWidth, mouseHeight, "LMB", lmbPressed, font, smallFont, lmbCps, themeColor);

        float rmbX = x + mouseWidth + gap;
        float rmbY = y + (keySize + gap) * 2 + spaceHeight + gap;
        drawKey(eventRender, rmbX, rmbY, mouseWidth, mouseHeight, "RMB", rmbPressed, font);

        float totalWidth = keySize * 3 + gap * 2;
        float totalHeight = keySize * 2 + gap + spaceHeight + gap + mouseHeight + gap;

        draggable.setWidth(totalWidth);
        draggable.setHeight(totalHeight);

        super.onRender(eventRender);
    }

    private void drawKey(EventRender.Default eventRender, float x, float y, float width, float height, String text, boolean pressed, Object font) {
        int bgColor = pressed ? ColorUtils.rgba(180, 180, 180, 200) : ColorUtils.rgba(25, 25, 25, 150);
        int textColor = pressed ? ColorUtils.rgba(0, 0, 0, 255) : ColorUtils.rgba(255, 255, 255, 255);

        RenderUtils.drawKeyStrokeRect(eventRender.getContext().getMatrices(), x, y, width, height, 3, bgColor);

        var f = Fonts.getFont("suisse", 15);
        float textWidth = f.getWidth(text);
        float textHeight = 8;

        float textX = x + (width - textWidth) / 2;
        float textY = y + (height - textHeight) / 2;

        f.draw(eventRender.getContext().getMatrices(), text, textX - 0.5f, textY + 2, textColor);
    }

    private void drawKeyWithCps(EventRender.Default eventRender, float x, float y, float width, float height, String text, boolean pressed, Object font, Object smallFont, int cps, int themeColor) {
        int bgColor = pressed ? ColorUtils.rgba(180, 180, 180, 200) : ColorUtils.rgba(25, 25, 25, 150);
        int textColor = pressed ? ColorUtils.rgba(0, 0, 0, 255) : ColorUtils.rgba(255, 255, 255, 255);

        RenderUtils.drawKeyStrokeRect(eventRender.getContext().getMatrices(), x, y, width, height, 3, bgColor);

        var f = Fonts.getFont("suisse", 15);
        var sf = Fonts.getFont("suisse", 12);

        float textWidth = f.getWidth(text);
        float textX = x + (width - textWidth) / 2;
        float textHeight = 8;
        float textY = y + (height - textHeight) / 2;
        f.draw(eventRender.getContext().getMatrices(), text, textX - 0.5f, textY + 2, textColor);

        String cpsText = "cps: " + cps;
        float cpsWidth = sf.getWidth(cpsText);
        float cpsX = x + (width - cpsWidth) / 2;
        float cpsY = textY + 12;
        sf.draw(eventRender.getContext().getMatrices(), cpsText, cpsX, cpsY - 3 , themeColor);
    }
}

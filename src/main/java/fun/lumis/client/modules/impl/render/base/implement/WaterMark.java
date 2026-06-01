package fun.lumis.client.modules.impl.render.base.implement;

import net.minecraft.client.util.math.MatrixStack;
import fun.lumis.Lumis;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.draggable.Draggable;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.impl.render.base.InterfaceProcessing;

import java.awt.*;

public class WaterMark extends InterfaceProcessing {
    private boolean showFps = true;
    private boolean showMs = true;
    private boolean showServer = true;
    private boolean showTps = true;

    public static String getUsername() {
        return "t.me/lumisclient";
    }

    public static String getUID() {
        return "1";
    }

    public WaterMark(Draggable draggable) {
        super(draggable);
    }

    public boolean isShowFps() {
        return showFps;
    }

    public void setShowFps(boolean showFps) {
        this.showFps = showFps;
    }

    public boolean isShowMs() {
        return showMs;
    }

    public void setShowMs(boolean showMs) {
        this.showMs = showMs;
    }

    public boolean isShowServer() {
        return showServer;
    }

    public void setShowServer(boolean showServer) {
        this.showServer = showServer;
    }

    public boolean isShowTps() {
        return showTps;
    }

    public void setShowTps(boolean showTps) {
        this.showTps = showTps;
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        if (ModuleClass.interfaceModule.style.is("Modern")) ModernStyle(eventRender);
        else if (ModuleClass.interfaceModule.style.is("Wave")) WaveStyle(eventRender);
        else DefaultStyle(eventRender);
        super.onRender(eventRender);
    }

    public void ModernStyle(EventRender.Default eventRender) {
        var matrices = eventRender.getContext().getMatrices();
        float x = draggable.getX();
        float y = draggable.getY();
        var titleFont = Fonts.getFont("suisse", 15);
        var infoFont = Fonts.getFont("suisse", 13);

        int theme;
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            theme = Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            theme = ColorUtils.getThemeColor();
        }
        int theme2 = ColorUtils.getThemeColor(120);
        int white = ColorUtils.rgba(255, 255, 255, 255);

        String name = "lumis";

        int fps = mc != null ? mc.getCurrentFps() : 0;
        int ping = 0;
        if (mc != null && mc.player != null && mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) ping = entry.getLatency();
        }

        StringBuilder info = new StringBuilder();
        if (showFps) info.append(fps).append(" fps");
        if (showMs) {
            if (info.length() > 0) info.append("  ");
            info.append(ping).append(" ms");
        }
        String infoText = info.toString();

        float pad = 6.0f;
        float barW = 2.0f;
        float h = 17.0f;
        float nameW = titleFont.getStringWidth(name);
        float infoW = infoText.isEmpty() ? 0.0f : infoFont.getStringWidth(infoText);
        float sep = infoText.isEmpty() ? 0.0f : 6.0f;
        float w = pad + barW + 4.0f + nameW + sep + infoW + pad;

        RenderUtils.drawShadow(matrices, x, y, w, h, 4.0f, 9.0f, ColorUtils.setAlphaColor(theme, 80));
        RenderUtils.drawRoundedRect(matrices, x, y, w, h, 4.0f, ColorUtils.rgba(14, 14, 18, 205));
        RenderUtils.drawGradientRect(matrices, x + 3.0f, y + 3.0f, barW, h - 6.0f, 1.0f, theme, theme2);

        float tx = x + 3.0f + barW + 4.0f;
        titleFont.drawGradientStringHorizontal(matrices, name, tx, y + 5.0f, theme, theme2);
        if (!infoText.isEmpty()) {
            infoFont.drawString(matrices, infoText, tx + nameW + sep, y + 6.4f, white);
        }

        draggable.setWidth(w);
        draggable.setHeight(h);
    }

    public void DefaultStyle(EventRender.Default eventRender) {
        var matrices = eventRender.getContext().getMatrices();
        float x = draggable.getX();
        float y = draggable.getY();
        var logoFont = Fonts.getFont("logo", 17);
        var iconNew14 = Fonts.getFont("iconnew", 14);
        var iconNew15 = Fonts.getFont("iconnew", 15);
        var icon14 = Fonts.getFont("icon", 14);
        var statsIconFont = Fonts.getFont("lumis", 14);
        if (statsIconFont == null) statsIconFont = iconNew14 != null ? iconNew14 : icon14;
        var suisse13 = Fonts.getFont("suisse", 13);

        float lumisRectH = 16;
        int iconSize = 17;
        String iconGlyph = "A";
        float iconW = logoFont.getStringWidth(iconGlyph);
        float iconX = x + (17 - iconW) / 2;
        float iconY = y + 5.5f;
        int iconTop;
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            iconTop = Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            iconTop = ColorUtils.getThemeColor();
        }
        boolean drawSquares = isUnusualRectType();
        float rect2Pad = 3;
        String username = getUsername();
        String UID = getUID();
        int whiteColor = new Color(255, 255, 255, 255).getRGB();
        float textY = y + 6.8f;

        String brandText = "";
        float brandTextX = iconX + iconW + 2.5f;
        float brandTextW = suisse13.getStringWidth(brandText);

        float lumisRectX = x;
        float lumisRectY = y;
        float lumisRectW = brandTextX + brandTextW + 1.5f - x;

        RenderUtils.drawDefaultHudThemedPanel(matrices, lumisRectX, lumisRectY, lumisRectW, lumisRectH, 2.8f, 3.3f, iconTop);
        if (drawSquares) {
        }

        int logoShadow = ColorUtils.applyAlpha(iconTop, 0.32f);
        RenderUtils.drawShadow(matrices, iconX + 0.3f, iconY - 1.25f, iconW - 1, iconSize - 11, 3, 5f, logoShadow);
        logoFont.drawGradientStringHorizontal(matrices, iconGlyph, iconX - 0.25f, iconY, iconTop, iconTop);
        suisse13.drawString(matrices, brandText, brandTextX, textY, whiteColor);

        float rect2X = lumisRectX + lumisRectW + 2.5f;

        float rect2H = 15.85f;
        int icon2Size = 14;
        String iconGlyph2 = "e";
        float icon2Y = y + 7.45f;

        int icon3Size = 14;
        String fpsIconGlyph = "j";
        String pingIconGlyph = "f";
        float icon3Y = y + 7.25f;

        int fps = mc != null ? mc.getCurrentFps() : 0;
        String fpsValue = String.valueOf(fps);
        String fpsSuffix = "fps";
        String fpsText = fpsValue + fpsSuffix;

        int ping = 0;
        if (mc != null && mc.player != null && mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) ping = entry.getLatency();
        }
        String pingValue = String.valueOf(ping);
        String pingSuffix = "ms";
        String pingText = pingValue + pingSuffix;

        float contentW = rect2Pad;
        contentW += iconNew14.getStringWidth(iconGlyph2) + 1f;
        if (!username.isEmpty()) {
            contentW += suisse13.getStringWidth(username) + 2f;
        }
        if (showFps) {
            contentW += statsIconFont != null ? statsIconFont.getStringWidth(fpsIconGlyph) + 2f : 0f;
            contentW += suisse13.getStringWidth(fpsText) + 2f;
        }
        if (showMs) {
            contentW += statsIconFont != null ? statsIconFont.getStringWidth(pingIconGlyph) + 2f : 0f;
            contentW += suisse13.getStringWidth(pingText) + 2f;
        }
        contentW += rect2Pad;

        float rect2W = contentW - 1.05f;
        RenderUtils.drawDefaultHudThemedPanel(matrices, rect2X, lumisRectY, rect2W, rect2H, 2.8f, 3.3f, iconTop);
        if (drawSquares) {
            RenderUtils.drawHudSquarePattern(matrices, rect2X, lumisRectY, rect2W, rect2H, iconTop);
        }

        float drawX = rect2X + rect2Pad + 1.5f;

        iconNew14.drawGradientStringHorizontal(matrices, iconGlyph2, drawX - 1f, icon2Y, iconTop, iconTop);
        drawX += iconNew14.getStringWidth(iconGlyph2) + 1f;

        if (!username.isEmpty()) {
            suisse13.drawString(matrices, username, drawX, textY, whiteColor);
            drawX += suisse13.getStringWidth(username) + 2f;
        }

        if (showFps) {
            if (statsIconFont != null) {
                statsIconFont.drawGradientStringHorizontal(matrices, fpsIconGlyph, drawX, icon3Y, iconTop, iconTop);
                drawX += statsIconFont.getStringWidth(fpsIconGlyph) + 2f;
            }
            suisse13.drawString(matrices, fpsValue, drawX, textY, whiteColor);
            suisse13.drawString(matrices, fpsSuffix, drawX + suisse13.getStringWidth(fpsValue) - 1, textY, iconTop);
            drawX += suisse13.getStringWidth(fpsText) + 2f;
        }

        if (showMs) {
            if (statsIconFont != null) {
                statsIconFont.drawGradientStringHorizontal(matrices, pingIconGlyph, drawX, icon3Y, iconTop, iconTop);
                drawX += statsIconFont.getStringWidth(pingIconGlyph) + 2f;
            }
            suisse13.drawString(matrices, pingValue, drawX, textY, whiteColor);
            suisse13.drawString(matrices, pingSuffix, drawX + suisse13.getStringWidth(pingValue) - 0.5, textY, iconTop);
        }

        String serverName = "Singleplayer";
        if (mc != null) {
            var info = mc.getCurrentServerEntry();
            if (info != null && info.address != null && !info.address.isEmpty()) {
                serverName = info.address;
            }
        }

        boolean showBottom = showServer || showTps;
        float rectBtmY = lumisRectY + lumisRectH + 2f;
        float rectBtmH = 15.85f;

        int iconSmallSize = 15;
        float iconSmallW = iconNew15.getStringWidth(iconGlyph);
        float iconSmallY = rectBtmY + (rectBtmH - iconSmallSize) / 2f + 6.5f;
        float serverTextY = rectBtmY + (rectBtmH - 12f) / 2f + 4.8f;
        String serverDisplayName = formatServerNameForDisplay(serverName);
        float serverTextW = suisse13.getStringWidth(serverDisplayName);
        int extraIconSize = 15;
        String extraIconGlyph = "y";
        float extraIconW = iconNew15.getStringWidth(extraIconGlyph);
        float extraIconY = rectBtmY + (rectBtmH - extraIconSize) / 2f + 6.4f;
        String tpsValue = formatOneDecimal(getServerTps());
        String tpsSuffix = "tps";
        String tpsText = tpsValue + tpsSuffix;
        float tpsTextW = suisse13.getStringWidth(tpsText);
        float rectBtmW = 0f;
        if (showBottom) {
            float bottomX = x + rect2Pad + 8.5f;
            if (showServer) {
                bottomX += iconSmallW + 3f + serverTextW;
            }
            if (showTps) {
                if (showServer) bottomX += 3f;
                bottomX += extraIconW + 3f + tpsTextW;
            }
            rectBtmW = Math.max(40f, (bottomX + rect2Pad) - x);

            RenderUtils.drawDefaultHudThemedPanel(matrices, x, rectBtmY, rectBtmW - 2.85f, rectBtmH, 2.8f, 3.3f, iconTop);
            if (drawSquares) {
                RenderUtils.drawHudSquarePattern(matrices, x, rectBtmY, rectBtmW, rectBtmH, iconTop);
            }

            float drawBottomX = x + rect2Pad + 7;
            if (showServer) {
                iconNew15.drawGradientStringHorizontal(matrices, "n", drawBottomX - 6.5f, iconSmallY, iconTop, iconTop);
                drawBottomX += iconSmallW + 3f;
                drawServerNameWithThemeParts(matrices, serverDisplayName, drawBottomX, serverTextY, iconTop, whiteColor);
                drawBottomX += serverTextW;
            }
            if (showTps) {
                if (showServer) drawBottomX += 3f;
                iconNew15.drawGradientStringHorizontal(matrices, extraIconGlyph, drawBottomX - 1.5f, extraIconY, iconTop, iconTop);
                drawBottomX += extraIconW + 3f;
                suisse13.drawString(matrices, tpsValue, drawBottomX - 1.75f, serverTextY, whiteColor);
                suisse13.drawString(matrices, tpsSuffix, drawBottomX + suisse13.getStringWidth(tpsValue) - 2.5f, serverTextY, iconTop);
            }
        }

        float totalW = Math.max(lumisRectW + 2f + rect2W, rectBtmW);
        draggable.setWidth(totalW);
        draggable.setHeight(showBottom ? (lumisRectH + 1f + rectBtmH) : lumisRectH);
    }

    public void WaveStyle(EventRender.Default eventRender) {
        float x = draggable.getX(), y = draggable.getY();
        var matrices = eventRender.getContext().getMatrices();
        var waveFont = Fonts.getFont("wave", 30);
        String watermarkText = "Lumis";

        int indexColor = ColorUtils.getThemeColor(90);
        int indexColor2 = ColorUtils.getThemeColor(180);
        int indexColor3 = ColorUtils.getThemeColor(270);
        int indexColor4 = ColorUtils.getColor(360);
        float glowWidth = 95.0f + waveFont.getStringWidth("ful");

        RenderUtils.drawShadow(matrices, x, y, glowWidth, 12, 10, 15, indexColor4, indexColor2, indexColor, indexColor3);
        waveFont.drawGradientStringHorizontal(matrices, watermarkText, x, y, indexColor, indexColor2);

        draggable.setWidth(Math.max(glowWidth, waveFont.getStringWidth(watermarkText)));
        draggable.setHeight(12);
    }

    private void drawServerNameWithThemeParts(MatrixStack matrices, String serverName, float x, float y, int themeColor, int whiteColor) {
        var font = Fonts.getFont("suisse", 13);
        String[] parts = serverName.split("\\.");
        if (parts.length < 2) {
            font.drawString(matrices, serverName, x, y, whiteColor);
            return;
        }

        String mainPart = String.join(".", java.util.Arrays.copyOf(parts, parts.length - 1));
        String suffixPart = "." + parts[parts.length - 1];

        font.drawString(matrices, mainPart, x, y, whiteColor);
        float suffixX = x + font.getStringWidth(mainPart) - 2f;
        font.drawString(matrices, suffixPart, suffixX, y, themeColor);
    }

    private String formatServerNameForDisplay(String serverName) {
        if (serverName == null || serverName.isEmpty()) {
            return "";
        }

        String host = serverName;
        int portIndex = host.indexOf(':');
        if (portIndex > 0) {
            host = host.substring(0, portIndex);
        }

        String[] parts = host.split("\\.");
        if (parts.length >= 3) {
            return String.join(".", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        }
        return host;
    }

    private float getServerTps() {
        if (Lumis.INSTANCE == null || Lumis.INSTANCE.tpsCalc == null) {
            return 20.0f;
        }
        return Math.max(0.0f, Math.min(20.0f, Lumis.INSTANCE.tpsCalc.getTPS()));
    }

    private String formatOneDecimal(float value) {
        int scaled = Math.round(value * 10.0f);
        return (scaled / 10) + "." + Math.abs(scaled % 10);
    }
}

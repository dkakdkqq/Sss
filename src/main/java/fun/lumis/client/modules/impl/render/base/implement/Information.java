package fun.lumis.client.modules.impl.render.base.implement;

import fun.lumis.Lumis;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.draggable.Draggable;
import fun.lumis.api.utils.math.MathUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.impl.render.base.InterfaceProcessing;

public class Information extends InterfaceProcessing {

    public Information(Draggable draggable) {
        super(draggable);
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        if (!ModuleClass.interfaceModule.style.is("Wave")) DefaultStyle(eventRender);
        else WaveStyle(eventRender);
        super.onRender(eventRender);
    }

    public void DefaultStyle(EventRender.Default eventRender) {
        float x = draggable.getX(), y = draggable.getY();
        Font font = Fonts.getFont("suisse", 13);
        Font iconFont = Fonts.getFont("icon", 16);
        Font smallIconFont = Fonts.getFont("icon", 15);

        int colorTheme;
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            colorTheme = Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            colorTheme = ColorUtils.getThemeColor();
        }
        boolean drawSquares = isUnusualRectType();

        int px = (int)Math.floor(mc.player.getX());
        int py = (int)Math.floor(mc.player.getY());
        int pz = (int)Math.floor(mc.player.getZ());

        float height = 16f;
        double bps = MathUtils.calculateBPS();
        String xValue = String.valueOf(px);
        String yValue = String.valueOf(py);
        String zValue = String.valueOf(pz);
        String coordsText = xValue + "x " + yValue + "y " + zValue + "z";
        String bpsValue = formatTwoDecimals(bps);
        String bpsSuffix = " b/s";
        float widthbps = font.getWidth(bpsValue + bpsSuffix);
        float xbps = x + 17 + widthbps;
        float widthCords = font.getWidth(coordsText);
        float totalWidth = 13 + widthCords + widthbps + 2 + 13.8f;

        RenderUtils.drawDefaultHudThemedPanel(eventRender.getContext().getMatrices(), x, y, totalWidth, height, 3f, 3.5f, colorTheme);
        if (drawSquares) {
            RenderUtils.drawHudSquarePattern(eventRender.getContext().getMatrices(), x, y, totalWidth, height, colorTheme);
        }

        float speedTextX = x + 13.5f;
        float bpsValueWidth = font.getWidth(bpsValue);
        font.draw(eventRender.getContext().getMatrices(), bpsValue, speedTextX, y + 6.6, -1);
        font.draw(eventRender.getContext().getMatrices(), bpsSuffix, speedTextX + bpsValueWidth - 2, y + 6.6, colorTheme);
        float coordsX = xbps + 9f;
        font.draw(eventRender.getContext().getMatrices(), xValue, coordsX, y + 6.6, -1);
        coordsX += font.getWidth(xValue);
        font.draw(eventRender.getContext().getMatrices(), "x", coordsX - 1, y + 6.6, colorTheme);
        coordsX += font.getWidth("x ");
        font.draw(eventRender.getContext().getMatrices(), yValue, coordsX, y + 6.6, -1);
        coordsX += font.getWidth(yValue);
        font.draw(eventRender.getContext().getMatrices(), "y", coordsX - 1, y + 6.6, colorTheme);
        coordsX += font.getWidth("y ");
        font.draw(eventRender.getContext().getMatrices(), zValue, coordsX, y + 6.6, -1);
        coordsX += font.getWidth(zValue);
        font.draw(eventRender.getContext().getMatrices(), "z", coordsX - 1, y + 6.6, colorTheme);
        iconFont.draw(eventRender.getContext().getMatrices(), "c", x + 3.25, y + 6.6, colorTheme);
        smallIconFont.draw(eventRender.getContext().getMatrices(), "x", xbps - 1, y + 6.85, colorTheme);


        draggable.setHeight(height);
        draggable.setWidth(totalWidth);
    }

    public void WaveStyle(EventRender.Default eventRender) {
        float x = draggable.getX(), y = draggable.getY();

        float time = (System.currentTimeMillis() % 2000) / 2000f * 360f;

        int leftTop1 = ColorUtils.getThemeColor((int) time);
        int leftBottom1 = ColorUtils.getThemeColor((int) (time + 30));
        int centerTop1 = ColorUtils.getThemeColor((int) (time + 90));
        int centerBottom1 = ColorUtils.getThemeColor((int) (time + 120));
        int rightTop1 = ColorUtils.getThemeColor((int) (time + 180));
        int rightBottom1 = ColorUtils.getThemeColor((int) (time + 210));

        String title = "coords";
        String xText = "x: " + (int) mc.player.getPos().getX();
        String yText = "y: " + (int) mc.player.getPos().getY();
        String zText = "z: " + (int) mc.player.getPos().getZ();

        var font = Fonts.getFont("suisse", 15);

        float xWidth = font.getWidth(xText);
        float yWidth = font.getWidth(yText);
        float zWidth = font.getWidth(zText);
        float titleWidth = font.getWidth(title);

        float maxCoordWidth = Math.max(xWidth, Math.max(yWidth, zWidth));

        float padding = 9f;
        float rectWidth = maxCoordWidth + padding;
        float rectHeight = 40;

        rectWidth = Math.max(rectWidth, 35);

        float centerX = x + rectWidth / 2;

        RenderUtils.drawWaveHudPanel(eventRender.getContext().getMatrices(), x, y, rectWidth, rectHeight, ColorUtils.rgba(25, 25, 25, 150),
                3.5f, 0, 10, 10,
                leftTop1, leftBottom1, centerTop1, centerBottom1, rightTop1, rightBottom1);

        float barPadding = 5f;
        RenderUtils.drawWaveHudHeader(eventRender.getContext().getMatrices(), x + barPadding, y + 12, rectWidth - barPadding * 2, 2.5f, 0,
                10, 10, leftTop1, leftBottom1, centerTop1, centerBottom1, rightTop1, rightBottom1);

        font.drawStringWithShadow(eventRender.getContext().getMatrices(), title, centerX - titleWidth / 2, y + 5, -1);
        font.drawStringWithShadow(eventRender.getContext().getMatrices(), xText, x + 4.5f, y + 17f, -1);
        font.drawStringWithShadow(eventRender.getContext().getMatrices(), yText, x + 4.5f, y + 24f, -1);
        font.drawStringWithShadow(eventRender.getContext().getMatrices(), zText, x + 4.5f, y + 31f, -1);

        float bpsX = x + rectWidth + 5;
        float bpsY = y;

        double bps = MathUtils.calculateBPS();

        String bpsTitle = "bps";
        String bpsText = String.valueOf((int) bps);

        float bpsTitleWidth = font.getWidth(bpsTitle);
        float bpsTextWidth = font.getWidth(bpsText);

        float bpsRectWidth = Math.max(bpsTitleWidth, bpsTextWidth) + 10;
        float bpsRectHeight = 25;

        bpsRectWidth = Math.max(bpsRectWidth, 30);

        float bpsCenterX = bpsX + bpsRectWidth / 2;

        RenderUtils.drawWaveHudPanel(eventRender.getContext().getMatrices(), bpsX, bpsY, bpsRectWidth, bpsRectHeight, ColorUtils.rgba(25, 25, 25, 150),
                3.5f, 0, 10, 10,
                leftTop1, leftBottom1, centerTop1, centerBottom1, rightTop1, rightBottom1);

        RenderUtils.drawWaveHudHeader(eventRender.getContext().getMatrices(), bpsX + barPadding, bpsY + 12, bpsRectWidth - barPadding * 2, 2.5f, 0,
                10, 10, leftTop1, leftBottom1, centerTop1, centerBottom1, rightTop1, rightBottom1);

        font.drawStringWithShadow(eventRender.getContext().getMatrices(), bpsTitle, bpsCenterX - bpsTitleWidth / 2, bpsY + 5, -1);
        font.drawStringWithShadow(eventRender.getContext().getMatrices(), bpsText, bpsCenterX - bpsTextWidth / 2, bpsY + 17f, -1);

        float totalWidth = rectWidth + 5 + bpsRectWidth;
        draggable.setWidth(totalWidth);
        draggable.setHeight(rectHeight);
    }

    private String formatTwoDecimals(double value) {
        int scaled = (int) Math.round(value * 100.0D);
        int fraction = Math.abs(scaled % 100);
        return (scaled / 100) + "." + (fraction < 10 ? "0" : "") + fraction;
    }
}

package fun.lumis.client.modules.impl.render.base.implement;

import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.draggable.Draggable;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.impl.render.base.InterfaceProcessing;

public class Session extends InterfaceProcessing {

    private long sessionStartTime = System.currentTimeMillis();

    public Session(Draggable draggable) {
        super(draggable);
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        float x = draggable.getX(), y = draggable.getY();
        long now = System.currentTimeMillis();

        float height = 18;

        String serverName = "local";
        if (mc != null) {
            var info = mc.getCurrentServerEntry();
            if (info != null && info.address != null && !info.address.isEmpty()) {
                serverName = info.address;
            }
        }

        String playerName = "unknown";
        if (mc != null && mc.player != null) {
            playerName = mc.player.getName().getString();
        } else if (mc != null && mc.getSession() != null) {
            playerName = mc.getSession().getUsername();
        }

        long elapsed = now - sessionStartTime;
        long totalSeconds = elapsed / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        String playTime = hours + "h " + minutes + "m " + seconds + "s";

        String titleText = "sessioninfo";
        String serverText = "server: " + serverName;
        String nameText = "name: " + playerName;
        String playTimeText = "playtime: " + playTime;

        Font font = Fonts.getFont("suisse", 15);

        float titleWidth = font.getWidth(titleText);
        float serverWidth = font.getWidth(serverText);
        float nameWidth = font.getWidth(nameText);
        float playTimeWidth = font.getWidth(playTimeText);

        float maxTextWidth = Math.max(titleWidth, Math.max(serverWidth, Math.max(nameWidth, playTimeWidth)));
        float width = maxTextWidth + 10;

        int time = (int) ((now % 2000) / 2000f * 360f);

        int leftTop = ColorUtils.getThemeColor(time);
        int leftBottom = ColorUtils.getThemeColor(time + 30);
        int centerTop = ColorUtils.getThemeColor(time + 90);
        int centerBottom = ColorUtils.getThemeColor(time + 120);
        int rightTop = ColorUtils.getThemeColor(time + 180);
        int rightBottom = ColorUtils.getThemeColor(time + 210);

        RenderUtils.drawWaveHudPanel(eventRender.getContext().getMatrices(), x, y, width, height + 25, ColorUtils.rgba(25, 25, 25, 150),
                height - 3, 0, 10, 10,
                leftTop, leftBottom, centerTop, centerBottom, rightTop, rightBottom);

        font.drawStringWithShadow(eventRender.getContext().getMatrices(), titleText, x + 3, y + 5, -1);
        font.drawStringWithShadow(eventRender.getContext().getMatrices(), serverText, x + 3, y + 18, -1);
        font.drawStringWithShadow(eventRender.getContext().getMatrices(), nameText, x + 3, y + 25.5f, -1);
        font.drawStringWithShadow(eventRender.getContext().getMatrices(), playTimeText, x + 3, y + 33.5f, -1);

        draggable.setHeight(height + 25);
        draggable.setWidth(width);
        super.onRender(eventRender);
    }
}

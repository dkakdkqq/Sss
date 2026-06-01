package fun.lumis.api.storages.implement;

import lombok.Getter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import fun.lumis.api.QClient;
import fun.lumis.api.events.EventInvoker;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.utils.animation.AnimationUtils;
import fun.lumis.api.utils.animation.Easings;
import fun.lumis.api.utils.cmd.waypoint.Waypoint;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.math.MathUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;

public class WaypointStorage implements QClient {

    private static final Identifier ARROW_TEXTURE = Identifier.of("lumis", "textures/arrows/gps.png");

    private final AnimationUtils alphaAnimation = new AnimationUtils(0.0f, 8.5f, Easings.CUBIC_OUT);
    private float animatedYaw;

    public WaypointStorage() {
        EventInvoker.register(this);
    }

    @Getter
    private Waypoint activeWaypoint = null;

    public void set(Waypoint waypoint) {
        this.activeWaypoint = waypoint;
    }

    public void remove(Waypoint waypoint) {
        if (this.activeWaypoint != null && this.activeWaypoint.equals(waypoint)) {
            this.activeWaypoint = null;
        }
    }

    public void clear() {
        this.activeWaypoint = null;
    }

    public boolean isEmpty() {
        return activeWaypoint == null;
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        alphaAnimation.update(activeWaypoint == null ? 0.0f : 1.0f);
        float alpha = MathHelper.clamp(alphaAnimation.getValue(), 0.0f, 1.0f);
        if (activeWaypoint == null || alpha <= 0.02f) {
            return;
        }

        float centerX = mc.getWindow().getScaledWidth() * 0.5f;
        float centerY = mc.getWindow().getScaledHeight() * 0.25f;
        float size = 40.0f;

        double deltaX = activeWaypoint.getX() - mc.player.getX();
        double deltaZ = activeWaypoint.getZ() - mc.player.getZ();
        int distance = (int) MathUtils.round(MathHelper.sqrt((float) (deltaX * deltaX + deltaZ * deltaZ)));

        float targetYaw = (float) -Math.toDegrees(Math.atan2(deltaX, deltaZ)) - mc.gameRenderer.getCamera().getYaw();
        animatedYaw = interpolateAngle(animatedYaw, targetYaw, 0.18f);

        int color = ColorUtils.applyAlpha(ColorUtils.getThemeColor(), alpha);

        Font font = Fonts.getFont("sf_regular", 12);
        if (font != null) {
            String distanceText = distance + "m.";
            font.draw(event.getContext().getMatrices(), distanceText,
                    (centerX - font.getWidth(distanceText) * 0.5f) + 1.5f,
                    centerY + 7.5f,
                    ColorUtils.applyAlpha(0xFFFFFFFF, alpha));
        }

        event.getContext().getMatrices().push();
        event.getContext().getMatrices().translate(centerX, centerY, 0.0f);
        event.getContext().getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(animatedYaw));
        event.getContext().getMatrices().translate(-centerX, -centerY, 0.0f);

        float drawX = centerX - size * 0.5f;
        float drawY = centerY - size * 0.5f;

        RenderUtils.drawImage(event.getContext().getMatrices(), ARROW_TEXTURE, drawX, drawY, size, size, color);
        event.getContext().getMatrices().pop();
    }

    private float interpolateAngle(float current, float target, float factor) {
        float delta = MathHelper.wrapDegrees(target - current);
        return current + delta * factor;
    }
}

package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

import java.util.ArrayList;
import java.util.List;

public class Trails extends Module {

    public static Trails INSTANCE = new Trails();

    private final FloatSetting duration = new FloatSetting("Длительность", 300f, 100f, 1000f, 10f);

    private final List<Point> points = new ArrayList<>();

    public Trails() {
        super("Trails", "Красивый след за игроком", ModuleCategory.RENDER);
        addSettings(duration);
    }

    @Override
    public void onDisable() {
        points.clear();
        super.onDisable();
    }

    @EventLink
    public void onRender(Event3DRender event) {
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            return;
        }

        if (mc.player == null || mc.world == null) return;

        long currentTime = System.currentTimeMillis();

        points.removeIf(p -> (currentTime - p.time) > duration.get());

        Vec3d playerPos = interpolatePlayerPosition(event.getTickDelta());

        points.add(new Point(playerPos));

        render3DPoints(event.getMatrices());
    }

    private Vec3d interpolatePlayerPosition(float partialTicks) {
        return new Vec3d(
                MathHelper.lerp(partialTicks, mc.player.prevX, mc.player.getX()),
                MathHelper.lerp(partialTicks, mc.player.prevY, mc.player.getY()),
                MathHelper.lerp(partialTicks, mc.player.prevZ, mc.player.getZ())
        );
    }

    private Vec3d interpolatePlayerPosition(PlayerEntity playerEntity, float partialTicks) {
        return new Vec3d(
                MathHelper.lerp(partialTicks, playerEntity.prevX, playerEntity.getX()),
                MathHelper.lerp(partialTicks, playerEntity.prevY, playerEntity.getY()),
                MathHelper.lerp(partialTicks, playerEntity.prevZ, playerEntity.getZ())
        );
    }

    private void render3DPoints(MatrixStack matrixStack) {
        if (points.size() < 2) return;

        startRendering();

        matrixStack.push();

        Vec3d view = mc.gameRenderer.getCamera().getPos();
        matrixStack.translate(-view.x, -view.y, -view.z);

        Matrix4f matrix = matrixStack.peek().getPositionMatrix();

        int themeColor = ColorUtils.getThemeColor();
        float red = ColorUtils.redf(themeColor);
        float green = ColorUtils.greenf(themeColor);
        float blue = ColorUtils.bluef(themeColor);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        int index = 0;
        for (Point p : points) {
            float alpha = (float) index / (float) points.size() * 0.7f;
            int alphaInt = (int) (alpha * 255);

            buffer.vertex(matrix, (float) p.pos.x, (float) (p.pos.y + mc.player.getHeight()), (float) p.pos.z)
                    .color((int)(red * 255), (int)(green * 255), (int)(blue * 255), alphaInt);
            buffer.vertex(matrix, (float) p.pos.x, (float) p.pos.y, (float) p.pos.z)
                    .color((int)(red * 255), (int)(green * 255), (int)(blue * 255), alphaInt);
            index++;
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.lineWidth(2);

        renderLineStrip(matrix, points, true, red, green, blue);
        renderLineStrip(matrix, points, false, red, green, blue);

        matrixStack.pop();
        stopRendering();
    }

    private void renderLineStrip(Matrix4f matrix, List<Point> points, boolean withHeight, float red, float green, float blue) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

        int index = 0;
        for (Point p : points) {
            float alpha = Math.min((float) index / (float) points.size() * 1.5f, 1f);
            int alphaInt = (int) (alpha * 255);

            float y = withHeight ? (float) (p.pos.y + mc.player.getHeight()) : (float) p.pos.y;

            buffer.vertex(matrix, (float) p.pos.x, y, (float) p.pos.z)
                    .color((int)(red * 255), (int)(green * 255), (int)(blue * 255), alphaInt);
            index++;
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void startRendering() {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    private void stopRendering() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static class Point {
        public Vec3d pos;
        public long time;

        public Point(Vec3d pos) {
            this.pos = pos;
            this.time = System.currentTimeMillis();
        }
    }
}
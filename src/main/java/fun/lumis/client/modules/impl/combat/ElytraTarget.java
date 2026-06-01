package fun.lumis.client.modules.impl.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.combat.PredictUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public class ElytraTarget extends Module {

    public static ElytraTarget INSTANCE = new ElytraTarget();

    private static final Identifier GLOW_TEXTURE = Identifier.of("lumis", "textures/trajectories/glow.png");
    private static final float BOX_GLOW_OUTER_THICKNESS = 0.17f;
    private static final float BOX_GLOW_MID_THICKNESS = 0.13f;
    private static final float BOX_GLOW_CORE_THICKNESS = 0.11f;
    private static final float BOX_GLOW_LINE_U = 0.4f;
    private static final int[][] BOX_EDGES = new int[][]{
            {0, 2}, {2, 6}, {6, 4}, {4, 0},
            {1, 3}, {3, 7}, {7, 5}, {5, 1},
            {0, 1}, {2, 3}, {6, 7}, {4, 5}
    };
    private Box smoothedPredictionBox;
    private LivingEntity smoothedTarget;

    public final FloatSetting forward = new FloatSetting("Сила предикта", 3f, 1, 6, 1);

    public ElytraTarget() {
        super("ElytraSample", "Таргетит игрока на элитрах", ModuleCategory.COMBAT);
        addSettings(forward);
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (mc.player == null || mc.world == null) {
            resetPredictionSmoothing();
            return;
        }
        if (!mc.player.isGliding()) {
            resetPredictionSmoothing();
            return;
        }

        Aura aura = ModuleClass.aura;
        if (aura == null || !aura.isEnable()) {
            resetPredictionSmoothing();
            return;
        }

        LivingEntity target = aura.getTarget();
        if (target == null || !target.isAlive() || !target.isGliding()) {
            resetPredictionSmoothing();
            return;
        }

        Box predictedBox = buildPredictedBox(target);
        renderPredictionBox(event, smoothPredictionBox(target, predictedBox));
    }

    @Override
    public void onDisable() {
        resetPredictionSmoothing();
        super.onDisable();
    }

    private Box smoothPredictionBox(LivingEntity target, Box predictedBox) {
        if (smoothedPredictionBox == null || smoothedTarget != target || smoothedPredictionBox.getCenter().squaredDistanceTo(predictedBox.getCenter()) > 144.0D) {
            smoothedPredictionBox = predictedBox;
            smoothedTarget = target;
            return predictedBox;
        }

        double distance = Math.sqrt(smoothedPredictionBox.getCenter().squaredDistanceTo(predictedBox.getCenter()));
        double smoothFactor = MathHelper.clamp(0.08D + distance * 0.035D, 0.08D, 0.18D);
        smoothedPredictionBox = lerpBox(smoothedPredictionBox, predictedBox, smoothFactor);
        return smoothedPredictionBox;
    }

    private void resetPredictionSmoothing() {
        smoothedPredictionBox = null;
        smoothedTarget = null;
    }

    private Box buildPredictedBox(LivingEntity target) {
        Box currentBox = target.getBoundingBox();
        Vec3d predictedCenter = PredictUtils.predict(target, currentBox.getCenter(), Math.max(0, forward.getValue().intValue()));
        Vec3d offset = predictedCenter.subtract(currentBox.getCenter());
        return currentBox.offset(offset);
    }

    private Box lerpBox(Box from, Box to, double factor) {
        return new Box(
                MathHelper.lerp(factor, from.minX, to.minX),
                MathHelper.lerp(factor, from.minY, to.minY),
                MathHelper.lerp(factor, from.minZ, to.minZ),
                MathHelper.lerp(factor, from.maxX, to.maxX),
                MathHelper.lerp(factor, from.maxY, to.maxY),
                MathHelper.lerp(factor, from.maxZ, to.maxZ)
        );
    }

    private void renderPredictionBox(Event3DRender event, Box box) {
        MatrixStack matrices = event.getMatrices();
        Camera camera = event.getCamera();
        Vec3d cameraPos = camera.getPos();

        int themeColor = ColorUtils.getThemeColor();
        int outerColor = ColorUtils.setAlphaColor(themeColor, 118);
        int midColor = ColorUtils.setAlphaColor(ColorUtils.interpolateColor(themeColor, 0xFFFFFFFF, 0.24f), 210);
        int coreColor = ColorUtils.setAlphaColor(ColorUtils.interpolateColor(themeColor, 0xFFFFFFFF, 0.6f), 255);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        addGlowBox(quads, matrix, cameraPos, box, outerColor, BOX_GLOW_OUTER_THICKNESS);
        addGlowBox(quads, matrix, cameraPos, box, midColor, BOX_GLOW_MID_THICKNESS);
        addGlowBox(quads, matrix, cameraPos, box, coreColor, BOX_GLOW_CORE_THICKNESS);
        BufferRenderer.drawWithGlobalProgram(quads.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void addGlowBox(BufferBuilder buffer, Matrix4f matrix, Vec3d camera, Box box, int color, float thickness) {
        Vec3d[] corners = getBoxVectors(box);
        for (int[] edge : BOX_EDGES) {
            addGlowEdge(buffer, matrix, camera, corners[edge[0]], corners[edge[1]], color, thickness);
        }
    }

    private void addGlowEdge(BufferBuilder buffer, Matrix4f matrix, Vec3d camera, Vec3d start, Vec3d end, int color, float thickness) {
        Vec3d edge = end.subtract(start);
        if (edge.lengthSquared() <= 1.0E-6) return;

        Vec3d direction = edge.normalize();
        double overlap = thickness * 0.22f;
        start = start.subtract(direction.multiply(overlap));
        end = end.add(direction.multiply(overlap));
        edge = end.subtract(start);

        Vec3d center = start.add(end).multiply(0.5);
        Vec3d toCamera = camera.subtract(center);
        if (toCamera.lengthSquared() <= 1.0E-6) {
            toCamera = new Vec3d(0.0, 1.0, 0.0);
        }

        Vec3d side = edge.crossProduct(toCamera);
        if (side.lengthSquared() <= 1.0E-6) {
            side = edge.crossProduct(new Vec3d(0.0, 1.0, 0.0));
            if (side.lengthSquared() <= 1.0E-6) {
                side = edge.crossProduct(new Vec3d(1.0, 0.0, 0.0));
            }
        }

        side = side.normalize().multiply(thickness * 0.48f);

        Vec3d p1 = start.add(side).subtract(camera);
        Vec3d p2 = start.subtract(side).subtract(camera);
        Vec3d p3 = end.subtract(side).subtract(camera);
        Vec3d p4 = end.add(side).subtract(camera);

        float[] rgba = ColorUtils.rgba(color);
        buffer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).texture(BOX_GLOW_LINE_U, 0.0f).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).texture(BOX_GLOW_LINE_U, 1.0f).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, (float) p3.x, (float) p3.y, (float) p3.z).texture(BOX_GLOW_LINE_U, 1.0f).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.vertex(matrix, (float) p4.x, (float) p4.y, (float) p4.z).texture(BOX_GLOW_LINE_U, 0.0f).color(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private Vec3d[] getBoxVectors(Box box) {
        return new Vec3d[]{
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.maxZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ)
        };
    }
}

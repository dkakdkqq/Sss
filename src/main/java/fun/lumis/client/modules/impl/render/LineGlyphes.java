package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.animation.AnimationUtils;
import fun.lumis.api.utils.animation.Easings;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class LineGlyphes extends Module {

    public static LineGlyphes INSTANCE = new LineGlyphes();
    private static final Vec3i[] AXIS_DIRECTIONS = new Vec3i[]{
            new Vec3i(1, 0, 0),
            new Vec3i(-1, 0, 0),
            new Vec3i(0, 1, 0),
            new Vec3i(0, -1, 0),
            new Vec3i(0, 0, 1),
            new Vec3i(0, 0, -1)
    };

    private final FloatSetting glyphsCount = new FloatSetting("Количество Линий", 70.0f, 10.0f, 200.0f, 1.0f);
    private final BooleanSetting slowSpeed = new BooleanSetting("Медленная Скорость", false);
    private final BooleanSetting applyStippleLines = new BooleanSetting("Пунктирные Линии", false);
    private final FloatSetting stippleStepPixels = new FloatSetting("Шаг Пунктира", 3.0f, 0.5f, 20.0f, 0.5f)
            .visible(() -> applyStippleLines.isState());
    private final BooleanSetting linesGlowing = new BooleanSetting("Свечение", false);

    private final Random rand = new Random(93882L);
    private final List<GlyphPath> glyphs = new ArrayList<>();

    public LineGlyphes() {
        super("LineGlyphes", "Анимированные линии по миру", ModuleCategory.RENDER);
        addSettings(glyphsCount, slowSpeed, applyStippleLines, stippleStepPixels, linesGlowing);
    }

    @Override
    public void onDisable() {
        glyphs.clear();
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate ignored) {
        if (mc.player == null || mc.world == null) {
            glyphs.clear();
            return;
        }

        updateGlyphs();
        maintainGlyphCount();
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (mc.player == null || mc.world == null || event.getCamera() == null) {
            return;
        }
        drawAllGlyphs(event);
    }

    private void drawAllGlyphs(Event3DRender event) {
        if (glyphs.isEmpty()) {
            return;
        }

        List<GlyphPath> drawable = new ArrayList<>();
        for (GlyphPath glyph : glyphs) {
            if (glyph.getAlpha() > 0.01f && glyph.getPointCount() >= 2) {
                drawable.add(glyph);
            }
        }

        if (drawable.isEmpty()) {
            return;
        }

        MatrixStack matrices = event.getMatrices();
        Vec3d cameraPos = event.getCamera().getPos();

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        try {
            setupRenderState();

            if (linesGlowing.isState()) {
                setAdditiveBlend();
                drawGlyphPass(matrices, drawable, event.getTickDelta(), 4.0f, 0.08f);
                drawGlyphPass(matrices, drawable, event.getTickDelta(), 2.5f, 0.15f);
                drawGlyphPass(matrices, drawable, event.getTickDelta(), 1.5f, 0.25f);
            }

            setNormalBlend();
            drawGlyphPass(matrices, drawable, event.getTickDelta(), 1.0f, 1.0f);
        } finally {
            restoreRenderState();
            matrices.pop();
        }
    }

    private void drawGlyphPass(MatrixStack matrices, List<GlyphPath> drawable, float partialTicks, float widthMul, float alphaMul) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        boolean dashed = applyStippleLines.isState();
        float dashStep = Math.max(0.04f, stippleStepPixels.get() * 0.08f);
        float gapStep = dashStep * 0.85f;

        RenderSystem.lineWidth(Math.min(1.15f * widthMul, 12.0f));

        List<VertexData> vertices = new ArrayList<>();
        int colorIndex = 0;

        for (GlyphPath glyph : drawable) {
            float glyphAlpha = glyph.getAlpha() * alphaMul;
            if (glyphAlpha <= 0.01f) continue;

            List<Vec3d> points = glyph.getSmoothedPositions(partialTicks);
            int pointCount = points.size();
            if (pointCount < 2) continue;

            for (int i = 0; i < pointCount - 1; i++) {
                Vec3d from = points.get(i);
                Vec3d to = points.get(i + 1);
                float segmentAlpha = glyphAlpha * (0.25f + (float) i / (float) pointCount / 1.75f);
                int color = getThemeColorWithAlpha(segmentAlpha);
                colorIndex += 180;

                if (dashed) {
                    collectDashedSegment(vertices, from, to, color, dashStep, gapStep);
                } else {
                    collectSegment(vertices, from, to, color);
                }
            }
        }

        float markerSize = 0.018f * widthMul;
        int pointColorIndex = 0;

        for (GlyphPath glyph : drawable) {
            float glyphAlpha = glyph.getAlpha() * alphaMul;
            if (glyphAlpha <= 0.01f) continue;

            List<Vec3d> points = glyph.getSmoothedPositions(partialTicks);
            int pointCount = points.size();
            if (pointCount < 2) continue;

            for (int i = 0; i < pointCount; i++) {
                Vec3d pos = points.get(i);
                float localAlpha = glyphAlpha * (0.25f + (float) i / (float) pointCount / 1.75f);
                int color = getThemeColorWithAlpha(localAlpha);
                pointColorIndex += 180;
                collectPointCross(vertices, pos, color, markerSize);
            }
        }

        if (!vertices.isEmpty()) {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

            for (VertexData v : vertices) {
                buffer.vertex(matrix, v.x, v.y, v.z)
                        .color(v.r, v.g, v.b, v.a);
            }

            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        RenderSystem.lineWidth(1.0f);
    }

    private int getThemeColorWithAlpha(float alphaMul) {
        int themed = ColorUtils.getThemeColor();
        float alpha = MathHelper.clamp(alphaMul, 0.0f, 1.0f);
        int r = (themed >> 16) & 0xFF;
        int g = (themed >> 8) & 0xFF;
        int b = themed & 0xFF;
        int a = (int) (alpha * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void collectSegment(List<VertexData> vertices, Vec3d from, Vec3d to, int color) {
        vertices.add(new VertexData((float) from.x, (float) from.y, (float) from.z, color));
        vertices.add(new VertexData((float) to.x, (float) to.y, (float) to.z, color));
    }

    private void collectDashedSegment(List<VertexData> vertices, Vec3d from, Vec3d to, int color, float dashLen, float gapLen) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4) {
            return;
        }

        double nx = dx / length;
        double ny = dy / length;
        double nz = dz / length;
        double cursor = 0.0;
        boolean draw = true;

        while (cursor < length) {
            double step = draw ? dashLen : gapLen;
            double next = Math.min(length, cursor + step);

            if (draw) {
                float sx = (float) (from.x + nx * cursor);
                float sy = (float) (from.y + ny * cursor);
                float sz = (float) (from.z + nz * cursor);
                float ex = (float) (from.x + nx * next);
                float ey = (float) (from.y + ny * next);
                float ez = (float) (from.z + nz * next);

                vertices.add(new VertexData(sx, sy, sz, color));
                vertices.add(new VertexData(ex, ey, ez, color));
            }

            cursor = next;
            draw = !draw;
        }
    }

    private void collectPointCross(List<VertexData> vertices, Vec3d pos, int color, float size) {
        float x = (float) pos.x;
        float y = (float) pos.y;
        float z = (float) pos.z;
        vertices.add(new VertexData(x - size, y, z, color));
        vertices.add(new VertexData(x + size, y, z, color));
        vertices.add(new VertexData(x, y - size, z, color));
        vertices.add(new VertexData(x, y + size, z, color));
        vertices.add(new VertexData(x, y, z - size, color));
        vertices.add(new VertexData(x, y, z + size, color));
    }

    private void setupRenderState() {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    }

    private void setAdditiveBlend() {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ZERO
        );
    }

    private void setNormalBlend() {
        RenderSystem.defaultBlendFunc();
    }

    private void restoreRenderState() {
        RenderSystem.lineWidth(1.0f);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void updateGlyphs() {
        Iterator<GlyphPath> iterator = glyphs.iterator();
        while (iterator.hasNext()) {
            GlyphPath glyph = iterator.next();
            glyph.update();
            if (glyph.isDead()) {
                iterator.remove();
            }
        }
    }

    private void maintainGlyphCount() {
        int targetCount = maxGlyphCount();
        int activeCount = countActiveGlyphs();

        while (activeCount < targetCount) {
            glyphs.add(new GlyphPath(randomGlyphSpawnPos(), randInt(7, 12)));
            activeCount++;
        }

        if (activeCount > targetCount) {
            for (GlyphPath glyph : glyphs) {
                if (activeCount <= targetCount) break;
                if (!glyph.isRemoving()) {
                    glyph.setWantToRemove();
                    activeCount--;
                }
            }
        }
    }

    private int countActiveGlyphs() {
        int count = 0;
        for (GlyphPath glyph : glyphs) {
            if (!glyph.isRemoving()) {
                count++;
            }
        }
        return count;
    }

    private int maxGlyphCount() {
        return Math.max(1, Math.round(glyphsCount.get()));
    }

    private Vec3i randomGlyphSpawnPos() {
        final int minDistance = 6;
        final int maxDistance = 24;
        final int minY = 0;
        final int maxY = 12;

        Vec3d cameraPos = getCameraPos();

        for (int attempt = 0; attempt < 16; attempt++) {
            int distance = randInt(minDistance, maxDistance + 1);
            float yawBase = mc.player != null ? mc.player.getYaw() : 0.0f;
            float randomYaw = yawBase + randFloat(-135.0f, 135.0f);
            float yawRad = (float) Math.toRadians(randomYaw);

            int offsetX = (int) (-(MathHelper.sin(yawRad) * distance));
            int offsetY = randInt(minY, maxY + 1);
            int offsetZ = (int) (MathHelper.cos(yawRad) * distance);

            Vec3i spawn = new Vec3i(
                    (int) Math.floor(cameraPos.x) + offsetX,
                    (int) Math.floor(cameraPos.y) + offsetY,
                    (int) Math.floor(cameraPos.z) + offsetZ
            );

            if (isSpawnPosFree(spawn)) {
                return spawn;
            }
        }

        return new Vec3i(
                (int) Math.floor(cameraPos.x),
                (int) Math.floor(cameraPos.y),
                (int) Math.floor(cameraPos.z)
        );
    }

    private boolean isSpawnPosFree(Vec3i pos) {
        if (mc.world == null) {
            return true;
        }

        BlockPos bp = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        BlockState state = mc.world.getBlockState(bp);
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.getCollisionShape(mc.world, bp).isEmpty();
    }

    private Vec3d getCameraPos() {
        Camera camera = mc.gameRenderer != null ? mc.gameRenderer.getCamera() : null;
        if (camera != null) {
            return camera.getPos();
        }
        return mc.player != null ? mc.player.getPos() : Vec3d.ZERO;
    }

    private Vec3i randomAxisDirection() {
        return AXIS_DIRECTIONS[rand.nextInt(AXIS_DIRECTIONS.length)];
    }

    private Vec3i nextOrthogonalDirection(Vec3i previousDirection) {
        for (int i = 0; i < 12; i++) {
            Vec3i candidate = randomAxisDirection();
            int dot = candidate.getX() * previousDirection.getX()
                    + candidate.getY() * previousDirection.getY()
                    + candidate.getZ() * previousDirection.getZ();
            if (dot == 0) {
                return candidate;
            }
        }
        return randomAxisDirection();
    }

    private int randInt(int minInclusive, int maxExclusive) {
        if (maxExclusive <= minInclusive) {
            return minInclusive;
        }
        return rand.nextInt(maxExclusive - minInclusive) + minInclusive;
    }

    private float randFloat(float minInclusive, float maxInclusive) {
        if (maxInclusive <= minInclusive) {
            return minInclusive;
        }
        return minInclusive + rand.nextFloat() * (maxInclusive - minInclusive);
    }

    private float moveAdvanceFromTicks(int ticksSet, int ticksLeft, float partialTicks) {
        if (ticksSet <= 0) {
            return 1.0f;
        }
        float progress = 1.0f - ((float) ticksLeft - partialTicks) / (float) ticksSet;
        return MathHelper.clamp(progress, 0.0f, 1.0f);
    }

    private static double lerp(double start, double end, double delta) {
        return start + (end - start) * delta;
    }

    private static class VertexData {
        final float x, y, z;
        final int r, g, b, a;

        VertexData(float x, float y, float z, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.a = (color >> 24) & 0xFF;
            this.r = (color >> 16) & 0xFF;
            this.g = (color >> 8) & 0xFF;
            this.b = color & 0xFF;
        }
    }

    private class GlyphPath {
        private final List<Vec3i> points = new ArrayList<>();
        private final AnimationUtils alpha = new AnimationUtils(0.0f, 8.0f, Easings.QUAD_OUT);
        private Vec3i lastDirection;
        private int currentStepTicks;
        private int lastStepSet;
        private int stepsLeft;
        private boolean removing;

        GlyphPath(Vec3i spawnPos, int maxSteps) {
            points.add(spawnPos);
            lastDirection = randomAxisDirection();
            stepsLeft = maxSteps;
            alpha.setValue(0.0f);
        }

        void update() {
            alpha.update(removing ? 0.0f : 1.0f);

            if (removing) {
                return;
            }

            if (stepsLeft <= 0) {
                setWantToRemove();
                return;
            }

            if (currentStepTicks > 0) {
                currentStepTicks -= slowSpeed.isState() ? 1 : 2;
                if (currentStepTicks < 0) {
                    currentStepTicks = 0;
                }
                return;
            }

            Vec3i last = points.get(points.size() - 1);
            boolean added = false;

            for (int attempt = 0; attempt < 8; attempt++) {
                Vec3i nextDirection = nextOrthogonalDirection(lastDirection);
                int step = randInt(1, 4);
                Vec3i next = new Vec3i(
                        last.getX() + nextDirection.getX() * step,
                        last.getY() + nextDirection.getY() * step,
                        last.getZ() + nextDirection.getZ() * step
                );

                if (isSpawnPosFree(next)) {
                    lastDirection = nextDirection;
                    lastStepSet = step;
                    currentStepTicks = step;
                    points.add(next);
                    stepsLeft--;
                    added = true;
                    break;
                }
            }

            if (!added) {
                setWantToRemove();
            }
        }

        List<Vec3d> getSmoothedPositions(float partialTicks) {
            List<Vec3d> smoothed = new ArrayList<>(points.size());
            float moveAdvance = moveAdvanceFromTicks(lastStepSet, currentStepTicks, partialTicks);

            for (int i = 0; i < points.size(); i++) {
                Vec3i point = points.get(i);
                double x = point.getX();
                double y = point.getY();
                double z = point.getZ();

                if (points.size() >= 2 && i == points.size() - 1) {
                    Vec3i previous = points.get(points.size() - 2);
                    x = lerp(previous.getX(), x, moveAdvance);
                    y = lerp(previous.getY(), y, moveAdvance);
                    z = lerp(previous.getZ(), z, moveAdvance);
                }

                smoothed.add(new Vec3d(x, y, z));
            }

            return smoothed;
        }

        int getPointCount() {
            return points.size();
        }

        float getAlpha() {
            return MathHelper.clamp(alpha.getValue(), 0.0f, 1.0f);
        }

        boolean isRemoving() {
            return removing;
        }

        void setWantToRemove() {
            removing = true;
        }

        boolean isDead() {
            return removing && getAlpha() <= 0.03f;
        }
    }
}

package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventAttackEntity;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Cubes extends Module {

    public static Cubes INSTANCE = new Cubes();

    private static final Identifier GLOW_TEX = Identifier.of("lumis", "textures/particle/bloom.png");
    private static final float SPAWN_RADIUS = 12.0f;
    private static final float PARTICLE_SIZE = 0.18f;
    private static final float PARTICLE_SPEED = 0.25f;
    private static final float GLOW_INTENSITY = 1.7f;
    private static final float MAX_RENDER_DISTANCE_SQ = 900.0f;

    private static final byte[][] CUBE_EDGES = {
            {-1,-1,-1, 1,-1,-1}, {1,-1,-1, 1,-1,1}, {1,-1,1, -1,-1,1}, {-1,-1,1, -1,-1,-1},
            {-1,1,-1, 1,1,-1}, {1,1,-1, 1,1,1}, {1,1,1, -1,1,1}, {-1,1,1, -1,1,-1},
            {-1,-1,-1, -1,1,-1}, {1,-1,-1, 1,1,-1}, {1,-1,1, 1,1,1}, {-1,-1,1, -1,1,1}
    };
    private static final byte[][] TRIANGLE_EDGES = {
            {0, 1}, {0, 2}, {0, 3}, {0, 4},
            {1, 2}, {2, 3}, {3, 4}, {4, 1}
    };
    private static final float[] GLOW_SCALES = {10.0f, 6.0f, 3.5f};
    private static final float[] GLOW_ALPHA_SCALES = {0.06f, 0.14f, 0.25f};

    private final ModeSetting animation = new ModeSetting("Анимация", "Разлет", "Разлет", "Падение");
    private final ModeSetting shape = new ModeSetting("Форма", "Кубы", "Кубы", "Треугольники");
    private final FloatSetting count = new FloatSetting("Количество", 30.0f, 5.0f, 100.0f, 1.0f);
    private final FloatSetting size = new FloatSetting("Размер", 1.0f, 0.1f, 3.0f, 0.1f);
    private final FloatSetting speed = new FloatSetting("Скорость", 1.0f, 0.1f, 5.0f, 0.1f);

    private final List<CubeParticle> cubes = new ArrayList<>();
    private final List<CubeParticle> visibleCubes = new ArrayList<>();
    private final Random random = new Random();
    private boolean lastAttackPressed;
    private float cr, cg, cb;
    private int updateCounter = 0;

    public Cubes() {
        super("Cubes", "3D Кубы по миру", ModuleCategory.RENDER);
        addSettings(animation, shape, count, size, speed);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        cubes.clear();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        cubes.clear();
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (mc.player == null || mc.world == null) return;

        boolean attackPressed = mc.options.attackKey.isPressed();
        if (attackPressed && !lastAttackPressed) applyHitImpulseFromCrosshair(event.getCamera());
        lastAttackPressed = attackPressed;

        updateCounter++;
        if (updateCounter % 2 == 0) updateCubes();

        renderCubes(event);
    }

    @EventLink
    public void onAttack(EventAttackEntity event) {
        if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
            applyHitImpulseFromCrosshair(mc.gameRenderer.getCamera());
        }
    }

    private void applyHitImpulseFromCrosshair(Camera camera) {
        if (cubes.isEmpty() || camera == null) return;

        Vec3d origin = camera.getPos();
        float yaw = (float) Math.toRadians(camera.getYaw());
        float pitch = (float) Math.toRadians(camera.getPitch());

        double dirX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        double dirY = -MathHelper.sin(pitch);
        double dirZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);

        CubeParticle best = null;
        double bestT = Double.MAX_VALUE;

        for (int i = 0, sz = cubes.size(); i < sz; i++) {
            CubeParticle p = cubes.get(i);
            double opX = p.x - origin.x;
            double opY = p.y - origin.y;
            double opZ = p.z - origin.z;
            double t = opX * dirX + opY * dirY + opZ * dirZ;

            if (t < 0.0 || t > 128.0) continue;

            double closestX = origin.x + dirX * t;
            double closestY = origin.y + dirY * t;
            double closestZ = origin.z + dirZ * t;
            double dx = p.x - closestX;
            double dy = p.y - closestY;
            double dz = p.z - closestZ;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > 1.32 || t >= bestT) continue;
            bestT = t;
            best = p;
        }

        if (best != null) {
            double force = 0.08 * speed.get();
            best.vx += dirX * force;
            best.vy += dirY * force + 0.02;
            best.vz += dirZ * force;
        }
    }

    private void updateCubes() {
        int target = (int) count.get();
        int currentSize = cubes.size();

        if (currentSize < target) {
            int toAdd = Math.min(target - currentSize, 5);
            for (int i = 0; i < toAdd; i++) cubes.add(spawnCube());
        } else if (currentSize > target) {
            cubes.subList(target, currentSize).clear();
        }

        float spd = PARTICLE_SPEED * speed.get();
        float maxR = SPAWN_RADIUS;
        boolean falling = animation.is("Падение");
        Vec3d playerPos = mc.player.getPos();
        double maxRSq = maxR * maxR * 6.25;

        for (int i = cubes.size() - 1; i >= 0; i--) {
            CubeParticle p = cubes.get(i);

            if (falling) {
                p.wobblePhase += 0.06f * spd;
                p.x += p.vx * spd + Math.sin(p.wobblePhase + p.wobbleOffset) * 0.0024f * spd;
                p.y += p.vy * spd;
                p.z += p.vz * spd + Math.cos(p.wobblePhase * 0.8f + p.wobbleOffset) * 0.0020f * spd;
                p.vy = Math.max(p.vy - 0.00008f * spd, -0.032f);
                p.rotX += p.rotSpeedX * 0.20f * spd;
                p.rotY += p.rotSpeedY * 0.20f * spd;
                p.rotZ += p.rotSpeedZ * 0.20f * spd;
            } else {
                p.x += p.vx * spd;
                p.y += p.vy * spd;
                p.z += p.vz * spd;
                p.rotX += p.rotSpeedX * spd;
                p.rotY += p.rotSpeedY * spd;
                p.rotZ += p.rotSpeedZ * spd;
                p.vx *= 0.995f;
                p.vy *= 0.995f;
                p.vz *= 0.995f;
            }

            p.life--;

            double dx = p.x - playerPos.x;
            double dy = p.y - playerPos.y;
            double dz = p.z - playerPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (p.life <= 0 || distSq > maxRSq || (falling && p.y < playerPos.y - 2.5)) {
                cubes.remove(i);
                cubes.add(spawnCube());
            }
        }
    }

    private void renderCubes(Event3DRender e) {
        if (mc.player == null) return;

        MatrixStack ms = e.getMatrices();
        Vec3d cam = e.getCamera().getPos();
        Camera camera = e.getCamera();
        float s = PARTICLE_SIZE * size.get();
        float glow = GLOW_INTENSITY;

        int baseRGB = ColorUtils.getThemeColor();
        cr = ((baseRGB >> 16) & 0xFF) / 255f;
        cg = ((baseRGB >> 8) & 0xFF) / 255f;
        cb = (baseRGB & 0xFF) / 255f;

        visibleCubes.clear();
        float yaw = (float) Math.toRadians(camera.getYaw());
        float pitch = (float) Math.toRadians(camera.getPitch());
        double lookX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        double lookY = -MathHelper.sin(pitch);
        double lookZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);

        for (int i = 0, sz = cubes.size(); i < sz; i++) {
            CubeParticle p = cubes.get(i);
            double dx = p.x - cam.x;
            double dy = p.y - cam.y;
            double dz = p.z - cam.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > MAX_RENDER_DISTANCE_SQ) continue;
            if (dx * lookX + dy * lookY + dz * lookZ < -1.0) continue;
            p.renderAlpha = getAlpha(p);
            if (p.renderAlpha < 0.01f) continue;

            visibleCubes.add(p);
        }

        if (visibleCubes.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEX);
        drawGlowBatch(ms, camera, cam, s, glow);

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        boolean isCubes = shape.is("Кубы");
        boolean isTriangles = shape.is("Треугольники");

        if (isCubes) {
            drawCubeFacesBatch(ms, cam, s);
        }

        if (isTriangles) {
            drawTriangleFacesBatch(ms, cam, s);
        }

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

        if (isCubes) {
            drawCubeDashedEdgesBatch(ms, cam, s);
        } else if (isTriangles) {
            drawTriangleDashedEdgesBatch(ms, cam, s);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void drawGlowBatch(MatrixStack ms, Camera camera, Vec3d cam, float s, float glow) {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int particleIndex = 0, sz = visibleCubes.size(); particleIndex < sz; particleIndex++) {
            CubeParticle p = visibleCubes.get(particleIndex);
            float alpha = p.renderAlpha;

            ms.push();
            ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

            Matrix4f matrix = ms.peek().getPositionMatrix();
            for (int i = 0; i < 3; i++) {
                float scale = s * GLOW_SCALES[i] * glow;
                float a = alpha * GLOW_ALPHA_SCALES[i] * glow;
                float hs = scale * 0.5f;

                builder.vertex(matrix, -hs, hs, 0).texture(0f, 1f).color(cr, cg, cb, a);
                builder.vertex(matrix, hs, hs, 0).texture(1f, 1f).color(cr, cg, cb, a);
                builder.vertex(matrix, hs, -hs, 0).texture(1f, 0f).color(cr, cg, cb, a);
                builder.vertex(matrix, -hs, -hs, 0).texture(0f, 0f).color(cr, cg, cb, a);
            }
            ms.pop();
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private float getAlpha(CubeParticle p) {
        float lifePct = MathHelper.clamp(p.life / (float) p.maxLife, 0f, 1f);
        float fadeIn = Math.min(1f, (p.maxLife - p.life) / 20f);
        return lifePct * fadeIn;
    }

    private void drawCubeFacesBatch(MatrixStack ms, Vec3d cam, float s) {
        if (!hasFaceRenderableParticles()) return;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0, sz = visibleCubes.size(); i < sz; i++) {
            CubeParticle p = visibleCubes.get(i);
            float alpha = p.renderAlpha * 0.4f;
            if (alpha < 0.01f) continue;

            ms.push();
            ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.rotX));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.rotY));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.rotZ));
            appendCubeFaces(buffer, ms.peek().getPositionMatrix(), s, alpha);
            ms.pop();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void drawTriangleFacesBatch(MatrixStack ms, Vec3d cam, float s) {
        if (!hasFaceRenderableParticles()) return;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0, sz = visibleCubes.size(); i < sz; i++) {
            CubeParticle p = visibleCubes.get(i);
            float alpha = p.renderAlpha * 0.4f;
            if (alpha < 0.01f) continue;

            ms.push();
            ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.rotX));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.rotY));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.rotZ));
            appendTriangleFaces(buffer, ms.peek().getPositionMatrix(), s, alpha);
            ms.pop();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private boolean hasFaceRenderableParticles() {
        for (int i = 0, sz = visibleCubes.size(); i < sz; i++) {
            if (visibleCubes.get(i).renderAlpha >= 0.025f) return true;
        }
        return false;
    }

    private void appendCubeFaces(BufferBuilder buffer, Matrix4f m, float s, float a) {
        buffer.vertex(m, -s, -s, s).color(cr, cg, cb, a);
        buffer.vertex(m, s, -s, s).color(cr, cg, cb, a);
        buffer.vertex(m, s, s, s).color(cr, cg, cb, a);
        buffer.vertex(m, -s, s, s).color(cr, cg, cb, a);

        buffer.vertex(m, s, -s, -s).color(cr, cg, cb, a);
        buffer.vertex(m, -s, -s, -s).color(cr, cg, cb, a);
        buffer.vertex(m, -s, s, -s).color(cr, cg, cb, a);
        buffer.vertex(m, s, s, -s).color(cr, cg, cb, a);

        buffer.vertex(m, -s, s, s).color(cr, cg, cb, a);
        buffer.vertex(m, s, s, s).color(cr, cg, cb, a);
        buffer.vertex(m, s, s, -s).color(cr, cg, cb, a);
        buffer.vertex(m, -s, s, -s).color(cr, cg, cb, a);

        buffer.vertex(m, -s, -s, -s).color(cr, cg, cb, a);
        buffer.vertex(m, s, -s, -s).color(cr, cg, cb, a);
        buffer.vertex(m, s, -s, s).color(cr, cg, cb, a);
        buffer.vertex(m, -s, -s, s).color(cr, cg, cb, a);

        buffer.vertex(m, s, -s, s).color(cr, cg, cb, a);
        buffer.vertex(m, s, -s, -s).color(cr, cg, cb, a);
        buffer.vertex(m, s, s, -s).color(cr, cg, cb, a);
        buffer.vertex(m, s, s, s).color(cr, cg, cb, a);

        buffer.vertex(m, -s, -s, -s).color(cr, cg, cb, a);
        buffer.vertex(m, -s, -s, s).color(cr, cg, cb, a);
        buffer.vertex(m, -s, s, s).color(cr, cg, cb, a);
        buffer.vertex(m, -s, s, -s).color(cr, cg, cb, a);
    }

    private void appendTriangleFaces(BufferBuilder buffer, Matrix4f m, float s, float a) {
        float top = s;
        float bottom = -s;
        float halfBase = s * 0.866f;

        buffer.vertex(m, 0, top, 0).color(cr, cg, cb, a);
        buffer.vertex(m, -halfBase, bottom, halfBase).color(cr, cg, cb, a);
        buffer.vertex(m, halfBase, bottom, halfBase).color(cr, cg, cb, a);

        buffer.vertex(m, 0, top, 0).color(cr, cg, cb, a);
        buffer.vertex(m, halfBase, bottom, halfBase).color(cr, cg, cb, a);
        buffer.vertex(m, halfBase, bottom, -halfBase).color(cr, cg, cb, a);

        buffer.vertex(m, 0, top, 0).color(cr, cg, cb, a);
        buffer.vertex(m, halfBase, bottom, -halfBase).color(cr, cg, cb, a);
        buffer.vertex(m, -halfBase, bottom, -halfBase).color(cr, cg, cb, a);

        buffer.vertex(m, 0, top, 0).color(cr, cg, cb, a);
        buffer.vertex(m, -halfBase, bottom, -halfBase).color(cr, cg, cb, a);
        buffer.vertex(m, -halfBase, bottom, halfBase).color(cr, cg, cb, a);

        buffer.vertex(m, -halfBase, bottom, halfBase).color(cr, cg, cb, a);
        buffer.vertex(m, halfBase, bottom, halfBase).color(cr, cg, cb, a);
        buffer.vertex(m, halfBase, bottom, -halfBase).color(cr, cg, cb, a);

        buffer.vertex(m, -halfBase, bottom, halfBase).color(cr, cg, cb, a);
        buffer.vertex(m, halfBase, bottom, -halfBase).color(cr, cg, cb, a);
        buffer.vertex(m, -halfBase, bottom, -halfBase).color(cr, cg, cb, a);
    }

    private void drawCubeDashedEdgesBatch(MatrixStack ms, Vec3d cam, float s) {
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        int lineCount = 0;
        for (int i = 0, sz = visibleCubes.size(); i < sz; i++) {
            CubeParticle p = visibleCubes.get(i);
            float alpha = p.renderAlpha;

            ms.push();
            ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.rotX));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.rotY));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.rotZ));
            lineCount += appendCubeDashedEdges(buf, ms.peek().getPositionMatrix(), s, alpha);
            ms.pop();
        }

        if (lineCount > 0) {
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
    }

    private void drawTriangleDashedEdgesBatch(MatrixStack ms, Vec3d cam, float s) {
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        int lineCount = 0;
        for (int i = 0, sz = visibleCubes.size(); i < sz; i++) {
            CubeParticle p = visibleCubes.get(i);
            float alpha = p.renderAlpha;

            ms.push();
            ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.rotX));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.rotY));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.rotZ));
            lineCount += appendTriangleDashedEdges(buf, ms.peek().getPositionMatrix(), s, alpha);
            ms.pop();
        }

        if (lineCount > 0) {
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
    }

    private int appendCubeDashedEdges(BufferBuilder buf, Matrix4f mat, float s, float alpha) {
        int color = colorToInt(Math.min(1f, cr * 1.5f), Math.min(1f, cg * 1.5f), Math.min(1f, cb * 1.5f), alpha);

        float dashLen = s * 0.3f;
        float gapLen = s * 0.25f;

        int lineCount = 0;

        for (byte[] edge : CUBE_EDGES) {
            float x1 = edge[0] * s;
            float y1 = edge[1] * s;
            float z1 = edge[2] * s;
            float x2 = edge[3] * s;
            float y2 = edge[4] * s;
            float z2 = edge[5] * s;

            float dx = x2 - x1;
            float dy = y2 - y1;
            float dz = z2 - z1;
            float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);

            if (len < 0.001f) continue;

            float nx = dx / len;
            float ny = dy / len;
            float nz = dz / len;

            float pos = 0;
            boolean drawing = true;

            while (pos < len) {
                float segLen = drawing ? dashLen : gapLen;
                float end = Math.min(pos + segLen, len);

                if (drawing) {
                    buf.vertex(mat, x1 + nx * pos, y1 + ny * pos, z1 + nz * pos).color(color);
                    buf.vertex(mat, x1 + nx * end, y1 + ny * end, z1 + nz * end).color(color);
                    lineCount++;
                }

                pos = end;
                drawing = !drawing;
            }
        }

        return lineCount;
    }

    private int appendTriangleDashedEdges(BufferBuilder buf, Matrix4f mat, float s, float alpha) {
        int color = colorToInt(Math.min(1f, cr * 1.5f), Math.min(1f, cg * 1.5f), Math.min(1f, cb * 1.5f), alpha);

        float dashLen = s * 0.3f;
        float gapLen = s * 0.25f;

        int lineCount = 0;

        float top = s;
        float bottom = -s;
        float halfBase = s * 0.866f;

        for (byte[] edge : TRIANGLE_EDGES) {
            float x1 = trianglePointX(edge[0], halfBase);
            float y1 = edge[0] == 0 ? top : bottom;
            float z1 = trianglePointZ(edge[0], halfBase);
            float x2 = trianglePointX(edge[1], halfBase);
            float y2 = edge[1] == 0 ? top : bottom;
            float z2 = trianglePointZ(edge[1], halfBase);

            float dx = x2 - x1;
            float dy = y2 - y1;
            float dz = z2 - z1;
            float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);

            if (len < 0.001f) continue;

            float nx = dx / len;
            float ny = dy / len;
            float nz = dz / len;

            float pos = 0;
            boolean drawing = true;

            while (pos < len) {
                float segLen = drawing ? dashLen : gapLen;
                float end = Math.min(pos + segLen, len);

                if (drawing) {
                    buf.vertex(mat, x1 + nx * pos, y1 + ny * pos, z1 + nz * pos).color(color);
                    buf.vertex(mat, x1 + nx * end, y1 + ny * end, z1 + nz * end).color(color);
                    lineCount++;
                }

                pos = end;
                drawing = !drawing;
            }
        }

        return lineCount;
    }

    private float trianglePointX(int index, float halfBase) {
        return switch (index) {
            case 1, 4 -> -halfBase;
            case 2, 3 -> halfBase;
            default -> 0.0f;
        };
    }

    private float trianglePointZ(int index, float halfBase) {
        return switch (index) {
            case 1, 2 -> halfBase;
            case 3, 4 -> -halfBase;
            default -> 0.0f;
        };
    }

    private CubeParticle spawnCube() {
        float r = SPAWN_RADIUS;
        boolean falling = animation.is("Падение");
        int life = falling ? 260 + random.nextInt(220) : 420 + random.nextInt(420);

        double x = mc.player.getX() + (random.nextDouble() * 2 - 1) * r;
        double y = falling ? mc.player.getY() + 4.0 + random.nextDouble() * (r * 0.55) : mc.player.getY() + 2.0 + random.nextDouble() * (r * 0.8);
        double z = mc.player.getZ() + (random.nextDouble() * 2 - 1) * r;

        float speedMult = speed.get();
        float vx, vy, vz;

        if (falling) {
            vx = (random.nextFloat() - 0.5f) * 0.008f * speedMult;
            vy = (-0.012f - random.nextFloat() * 0.012f) * speedMult;
            vz = (random.nextFloat() - 0.5f) * 0.008f * speedMult;
        } else {
            float yaw = random.nextFloat() * 360f;
            float vel = (0.010f + random.nextFloat() * 0.020f) * speedMult;
            vx = -MathHelper.sin((float) Math.toRadians(yaw)) * vel;
            vz = MathHelper.cos((float) Math.toRadians(yaw)) * vel;
            vy = (random.nextFloat() - 0.5f) * 0.010f * speedMult;
        }

        return new CubeParticle(x, y, z, vx, vy, vz,
                random.nextFloat() * 360, random.nextFloat() * 360, random.nextFloat() * 360,
                (random.nextFloat() - 0.5f) * 1.5f, (random.nextFloat() - 0.5f) * 1.5f, (random.nextFloat() - 0.5f) * 1.5f,
                life, (float)(random.nextDouble() * Math.PI * 2), random.nextFloat() * 10f);
    }

    private static int colorToInt(float r, float g, float b, float a) {
        return ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    private static class CubeParticle {
        double x, y, z;
        float vx, vy, vz, rotX, rotY, rotZ, rotSpeedX, rotSpeedY, rotSpeedZ, wobblePhase, wobbleOffset;
        float renderAlpha;
        int life, maxLife;

        CubeParticle(double x, double y, double z, float vx, float vy, float vz, float rotX, float rotY, float rotZ,
                     float rotSpeedX, float rotSpeedY, float rotSpeedZ, int life, float wobblePhase, float wobbleOffset) {
            this.x = x; this.y = y; this.z = z; this.vx = vx; this.vy = vy; this.vz = vz;
            this.rotX = rotX; this.rotY = rotY; this.rotZ = rotZ;
            this.rotSpeedX = rotSpeedX; this.rotSpeedY = rotSpeedY; this.rotSpeedZ = rotSpeedZ;
            this.life = this.maxLife = life;
            this.wobblePhase = wobblePhase; this.wobbleOffset = wobbleOffset;
        }
    }
}

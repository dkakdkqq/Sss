package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

import java.util.ArrayList;
import java.util.List;

public class Projectile extends Module {
    private final Font impactFont = Fonts.getFont("sf_regular", 14);

    public static Projectile INSTANCE = new Projectile();

    private record ImpactPoint(Vec3d pos, float seconds) {
    }

    private static final Identifier BLOOM_TEXTURE = Identifier.of("lumis", "textures/particle/bloom.png");

    private final FloatSetting size = new FloatSetting("Размер", 1.2f, 0.6f, 2.4f, 0.1f);

    private final List<ImpactPoint> impactPoints = new ArrayList<>();
    private final Matrix4f lastProjectionMatrix = new Matrix4f();
    private final Quaternionf lastCameraRotation = new Quaternionf();
    private Vec3d lastCameraPos = Vec3d.ZERO;
    private boolean hasMatrices;

    public Projectile() {
        super("Projectile", "Траектория жемчуга эндера", ModuleCategory.RENDER);
        addSettings(size);
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (mc.world == null || mc.player == null) return;

        impactPoints.clear();
        hasMatrices = true;
        lastProjectionMatrix.set(event.getProjectionMatrix());
        lastCameraPos = event.getCamera().getPos();
        lastCameraRotation.set(event.getCamera().getRotation());

        MatrixStack matrices = event.getMatrices();
        Camera camera = event.getCamera();
        Vec3d cameraPos = camera.getPos();
        Quaternionf cameraRotation = camera.getRotation();
        float tickDelta = event.getTickDelta();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, BLOOM_TEXTURE);

        Box searchBox = mc.player.getBoundingBox().expand(128.0D);
        for (EnderPearlEntity pearl : mc.world.getEntitiesByClass(EnderPearlEntity.class, searchBox, Entity::isAlive)) {

            List<Vec3d> points = simulate(pearl, tickDelta);
            if (points.size() < 2) continue;

            float seconds = (points.size() - 1) / 20.0f;
            Vec3d impactPos = points.get(points.size() - 1);
            impactPoints.add(new ImpactPoint(impactPos, seconds));

            float quadSize = size.get() * 0.2f;
            int color = ColorUtils.setAlphaColor(ColorUtils.getThemeColor(), 40);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int a = (color >> 24) & 0xFF;

            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3d start = points.get(i);
                Vec3d end = points.get(i + 1);

                int samples = Math.max(2, Math.min(12, (int) Math.ceil(start.distanceTo(end) / Math.max(quadSize * 1.75f, 0.08f))));
                for (int j = 0; j < samples; j++) {
                    Vec3d interp = start.lerp(end, j / (double) samples);

                    matrices.push();
                    matrices.translate(interp.x, interp.y, interp.z);
                    matrices.multiply(cameraRotation);
                    Matrix4f matrix = matrices.peek().getPositionMatrix();

                    buffer.vertex(matrix, -quadSize, -quadSize, 0).texture(0, 0).color(r, g, b, a);
                    buffer.vertex(matrix, -quadSize, quadSize, 0).texture(0, 1).color(r, g, b, a);
                    buffer.vertex(matrix, quadSize, quadSize, 0).texture(1, 1).color(r, g, b, a);
                    buffer.vertex(matrix, quadSize, -quadSize, 0).texture(1, 0).color(r, g, b, a);
                    matrices.pop();
                }
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            matrices.pop();

            float markerSize = quadSize * 1.6f;
            int markerColor = ColorUtils.setAlphaColor(ColorUtils.getThemeColor(), 170);
            int mr = (markerColor >> 16) & 0xFF;
            int mg = (markerColor >> 8) & 0xFF;
            int mb = markerColor & 0xFF;
            int ma = (markerColor >> 24) & 0xFF;
            float mx = (float) (impactPos.x - cameraPos.x);
            float my = (float) (impactPos.y - cameraPos.y + 0.03f);
            float mz = (float) (impactPos.z - cameraPos.z);

            matrices.push();
            matrices.translate(mx, my, mz);
            matrices.multiply(cameraRotation);
            Matrix4f markerMatrix = matrices.peek().getPositionMatrix();
            BufferBuilder marker = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            marker.vertex(markerMatrix, -markerSize, -markerSize, 0).texture(0, 0).color(mr, mg, mb, ma);
            marker.vertex(markerMatrix, -markerSize, markerSize, 0).texture(0, 1).color(mr, mg, mb, ma);
            marker.vertex(markerMatrix, markerSize, markerSize, 0).texture(1, 1).color(mr, mg, mb, ma);
            marker.vertex(markerMatrix, markerSize, -markerSize, 0).texture(1, 0).color(mr, mg, mb, ma);
            BufferRenderer.drawWithGlobalProgram(marker.end());
            matrices.pop();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (!hasMatrices || impactPoints.isEmpty() || mc.player == null) return;

        MatrixStack matrices = event.getContext().getMatrices();
        Font font = impactFont;
        if (font == null) return;

        int themeColor = ColorUtils.getThemeColor();

        for (ImpactPoint impact : impactPoints) {
            Vec3d screen = worldToScreen(impact.pos());
            if (screen == null) continue;

            String text = formatOneDecimal(impact.seconds()) + " сек";
            float textWidth = font.getStringWidth(text);
            float boxWidth = textWidth + 10.0f;
            float boxHeight = 12.5f;
            float x = (float) screen.x - (boxWidth / 2.0f);
            float y = (float) screen.y - 6;

            RenderUtils.drawDefaultHudThemedPanel(matrices, x, y, boxWidth, boxHeight, 3, 3.5f, themeColor);
            font.drawString(matrices, text, x + 5.5f, y + 4.55f, 0xFFFFFFFF);
        }
    }

    private Vec3d worldToScreen(Vec3d worldPos) {
        if (mc == null || mc.getWindow() == null) return null;

        Vector3f relative = new Vector3f(
                (float) (worldPos.x - lastCameraPos.x),
                (float) (worldPos.y - lastCameraPos.y),
                (float) (worldPos.z - lastCameraPos.z)
        );

        Quaternionf invCameraRot = new Quaternionf(lastCameraRotation).conjugate();
        relative.rotate(invCameraRot);

        Vector4f clip = new Vector4f(relative.x, relative.y, relative.z, 1.0f);
        lastProjectionMatrix.transform(clip);

        float w = clip.w;
        if (w <= 0.00001f) return null;

        float ndcX = clip.x / w;
        float ndcY = clip.y / w;
        float ndcZ = clip.z / w;

        float screenX = (ndcX * 0.5f + 0.5f) * mc.getWindow().getScaledWidth();
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * mc.getWindow().getScaledHeight();

        if (Float.isNaN(screenX) || Float.isNaN(screenY) || Float.isInfinite(screenX) || Float.isInfinite(screenY)) {
            return null;
        }

        if (screenX < -400 || screenY < -400 || screenX > mc.getWindow().getScaledWidth() + 400 || screenY > mc.getWindow().getScaledHeight() + 400) {
            return null;
        }

        return new Vec3d(screenX, screenY, ndcZ);
    }

    private String formatOneDecimal(float value) {
        int scaled = Math.round(value * 10.0f);
        return (scaled / 10) + "." + Math.abs(scaled % 10);
    }

    private List<Vec3d> simulate(EnderPearlEntity pearl, float tickDelta) {
        List<Vec3d> points = new ArrayList<>();

        Vec3d pos = new Vec3d(
                net.minecraft.util.math.MathHelper.lerp(tickDelta, pearl.prevX, pearl.getX()),
                net.minecraft.util.math.MathHelper.lerp(tickDelta, pearl.prevY, pearl.getY()),
                net.minecraft.util.math.MathHelper.lerp(tickDelta, pearl.prevZ, pearl.getZ())
        );
        Vec3d motion = pearl.getVelocity();
        points.add(pos);

        for (int i = 0; i < 300; i++) {
            Vec3d lastPos = pos;
            Vec3d nextPos = pos.add(motion);

            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                    lastPos,
                    nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            ));

            if (hit.getType() == HitResult.Type.BLOCK) {
                points.add(hit.getPos());
                break;
            }

            points.add(nextPos);
            pos = nextPos;

            boolean inWater = mc.world.getBlockState(net.minecraft.util.math.BlockPos.ofFloored(pos)).isOf(net.minecraft.block.Blocks.WATER);
            double drag = inWater ? 0.8 : 0.99;
            motion = motion.multiply(drag).subtract(0.0, 0.03, 0.0);

            if (pos.y <= mc.world.getBottomY()) break;
        }

        return points;
    }
}


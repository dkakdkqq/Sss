package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class TotemAngel extends Module {

    public static TotemAngel INSTANCE = new TotemAngel();

    private final ModeSetting mode = new ModeSetting("Режим", "Angel", "Angel");
    private final BooleanSetting visuals = new BooleanSetting("Визуал", true);
    private final BooleanSetting chatInfo = new BooleanSetting("Чат инфо", true);
    private final FloatSetting riseHeight = new FloatSetting("Высота", 4f, 0.2f, 10.0f, 0.1f);
    private final FloatSetting duration = new FloatSetting("Длительность", 3f, 0.2f, 6.0f, 0.1f);

    private final ListSetting renderModes = new ListSetting("Режим",
            new BooleanSetting("Ангел", true));

    private static final float WING_SCALE = 1.0f;
    private static final float FLAP_SPEED = 1.6f;
    private static final float FLAP_AMPLITUDE = 25f;
    private static final float GLOW_INTENSITY = 0.10f;
    private static final float HALO_SIZE = 0.4f;
    private static final Identifier SPARKLE_TEXTURE = Identifier.of("lumis", "textures/particle/sparkle.png");
    private static final int GREEN_COLOR = 0xFF35FF2B;
    private static final int YELLOW_COLOR = 0xFFFFF12B;

    private final List<TotemGhost> ghosts = new CopyOnWriteArrayList<>();
    private final List<TotemSphereEffect> sphereEffects = new CopyOnWriteArrayList<>();
    private final Map<Integer, Long> recentSphereSpawns = new ConcurrentHashMap<>();

    public TotemAngel() {
        super("TotemPop", "Отображает эффект и пишет в чат при срабатывании тотема", ModuleCategory.RENDER);
        addSettings(renderModes, mode.visible(() -> false), visuals.visible(() -> false), chatInfo, riseHeight, duration);
    }

    @Override
    public void onDisable() {
        ghosts.clear();
        super.onDisable();
    }

    private Identifier getGlowTexture() {
        return Identifier.of("lumis", "textures/targetesp/bloom.png");
    }

    private Identifier getSkinTexture() {
        return Identifier.of("lumis", "textures/skin/skin.png");
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (mc.world == null || mc.player == null || event.getType() != EventPacket.Type.RECEIVE) return;

        if (event.getPacket() instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35) {
            mc.execute(() -> handleTotemPopPacket(packet));
        }
    }

    private void handleTotemPopPacket(EntityStatusS2CPacket packet) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            return;
        }

        if (renderModes.is("Ангел")) {
            addGhost(player);
        }

        if (chatInfo.isState() && player != mc.player) {
            String name = player.getName().getString();
            ChatUtils.sendMessage(name + " \u00A77снёс тотем!");
        }
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (mc.world == null || mc.player == null) return;

        if (renderModes.is("Ангел") && !ghosts.isEmpty()) {
            renderGhosts(event.getMatrices(), event.getTickDelta());
        }
    }

    private void addGhost(AbstractClientPlayerEntity player) {
        float partialTicks = mc.getRenderTickCounter().getTickDelta(true);

        double x = MathHelper.lerp(partialTicks, player.lastRenderX, player.getX());
        double y = MathHelper.lerp(partialTicks, player.lastRenderY, player.getY());
        double z = MathHelper.lerp(partialTicks, player.lastRenderZ, player.getZ());

        float bodyYaw = MathHelper.lerp(partialTicks, player.prevBodyYaw, player.bodyYaw);
        float headYaw = MathHelper.lerp(partialTicks, player.prevHeadYaw, player.headYaw);
        float headPitch = MathHelper.lerp(partialTicks, player.prevPitch, player.getPitch());
        float limbSwing = player.limbAnimator.getPos(partialTicks);
        float limbSwingAmount = player.limbAnimator.getSpeed(partialTicks);
        boolean sneaking = player.isSneaking();
        float height = player.getHeight();

        ghosts.add(new TotemGhost(
                new Vec3d(x, y, z),
                bodyYaw,
                headYaw - bodyYaw,
                headPitch,
                limbSwing,
                limbSwingAmount,
                sneaking,
                height,
                System.currentTimeMillis()
        ));
    }

    private void addSphereEffect(AbstractClientPlayerEntity player) {
        if (player == null || player == mc.player) {
            return;
        }

        long now = System.currentTimeMillis();
        recentSphereSpawns.entrySet().removeIf(entry -> now - entry.getValue() > 1000L);

        Long lastSpawn = recentSphereSpawns.get(player.getId());
        if (lastSpawn != null && now - lastSpawn < 120L) {
            return;
        }
        recentSphereSpawns.put(player.getId(), now);

        double centerY = player.getY() + player.getHeight() * 0.62;
        List<SphereParticle> particles = new ArrayList<>(64);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < 64; i++) {
            double yaw = random.nextDouble(0.0, Math.PI * 2.0);
            double pitch = random.nextDouble(-0.8, 0.8);
            Vec3d direction = new Vec3d(
                    Math.cos(yaw) * Math.cos(pitch),
                    Math.sin(pitch) * 0.62 + random.nextDouble(-0.12, 0.24),
                    Math.sin(yaw) * Math.cos(pitch)
            ).normalize();

            particles.add(new SphereParticle(
                    direction,
                    random.nextFloat(0.28f, 1.08f),
                    random.nextFloat(0.85f, 1.55f),
                    random.nextFloat(1.05f, 1.85f),
                    random.nextFloat(0.95f, 1.45f),
                    random.nextFloat(0.0f, 1.0f),
                    random.nextBoolean() ? GREEN_COLOR : YELLOW_COLOR
            ));
        }

        sphereEffects.add(new TotemSphereEffect(
                new Vec3d(player.getX(), centerY, player.getZ()),
                now,
                random.nextFloat(0f, 360f),
                particles,
                createSphereOrbitLines()
        ));
    }

    private void renderGhosts(MatrixStack matrices, float tickDelta) {
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        long now = System.currentTimeMillis();
        List<TotemGhost> toRemove = new ArrayList<>();

        int themeColor = ColorUtils.getThemeColor();
        float r = ColorUtils.redf(themeColor);
        float g = ColorUtils.greenf(themeColor);
        float b = ColorUtils.bluef(themeColor);

        for (TotemGhost ghost : ghosts) {
            float progress = (now - ghost.startTime) / (duration.get() * 1000.0f);

            if (progress >= 1.0f) {
                toRemove.add(ghost);
                continue;
            }

            double motionY = riseHeight.get() * easeOutCubic(progress);
            float alpha = (1.0f - easeInCubic(progress)) * 0.85f;

            double renderX = ghost.position.x - cameraPos.x;
            double renderY = ghost.position.y - cameraPos.y + motionY;
            double renderZ = ghost.position.z - cameraPos.z;

            matrices.push();
            matrices.translate(renderX, renderY, renderZ);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-ghost.bodyYaw));

            renderGlowingPlayerModel(matrices, r, g, b, alpha, ghost);
            renderWings(matrices, ghost, progress, tickDelta, themeColor, alpha);
            renderHalo(matrices, ghost, themeColor, alpha);

            matrices.pop();
        }

        if (!toRemove.isEmpty()) {
            ghosts.removeAll(toRemove);
        }
    }

    private void renderSphereEffects(MatrixStack matrices, Vec3d cameraPos) {
        long now = System.currentTimeMillis();
        float sphereDurationMs = duration.get() * 1000.0f;

        sphereEffects.removeIf(effect -> now - effect.startTime >= sphereDurationMs);
        if (sphereEffects.isEmpty()) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        renderSphereParticles(matrices, cameraPos, now, sphereDurationMs);
        renderSphereArcs(matrices, cameraPos, now, sphereDurationMs);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderSphereParticles(MatrixStack matrices, Vec3d cameraPos, long now, float durationMs) {
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShaderTexture(0, SPARKLE_TEXTURE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        float cameraYaw = mc.gameRenderer.getCamera().getYaw();
        float cameraPitch = mc.gameRenderer.getCamera().getPitch();
        float baseRadius = 1.18f;
        float baseSize = 0.28f;

        for (TotemSphereEffect effect : sphereEffects) {
            float age = (now - effect.startTime) / durationMs;
            float appear = MathHelper.clamp(1.0f - age, 0.0f, 1.0f);
            float burstProgress = easeOutQuad(Math.min(1.0f, age * 1.12f));

            for (SphereParticle particle : effect.particles) {
                float localProgress = MathHelper.clamp((age * particle.timeScale) + particle.progressOffset * 0.1f, 0.0f, 1.0f);
                float launchProgress = easeOutQuad(localProgress);
                float radial = (0.34f + launchProgress * (1.20f + particle.spread * 1.05f) + burstProgress * 0.32f) * baseRadius;
                float orbit = (now * 0.0012f * particle.rotationScale) + particle.progressOffset * 5.4f;
                double swirlScale = (1.0f - localProgress) * 0.18f;
                double swirlX = Math.cos(orbit) * swirlScale * particle.swirlAmount;
                double swirlY = Math.sin(orbit * 1.3f) * swirlScale * 0.75f * particle.swirlAmount + localProgress * 0.08f;
                double swirlZ = Math.sin(orbit) * swirlScale * particle.swirlAmount;
                double dragY = localProgress * localProgress * 0.14f;

                Vec3d worldPos = effect.origin
                        .add(particle.direction.multiply(radial))
                        .add(swirlX, swirlY - dragY, swirlZ);

                double x = worldPos.x - cameraPos.x;
                double y = worldPos.y - cameraPos.y;
                double z = worldPos.z - cameraPos.z;

                int color = setAlpha(particle.color, (int) (255 * appear * (0.50f + 0.50f * (1.0f - localProgress))));
                float drawSize = baseSize * (0.68f + particle.spread * 0.34f) * (0.70f + appear * 0.52f);

                matrices.push();
                matrices.translate(x, y, z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
                drawSphereBillboard(matrices.peek().getPositionMatrix(), drawSize, color);
                matrices.pop();
            }
        }
    }

    private void renderSphereArcs(MatrixStack matrices, Vec3d cameraPos, long now, float durationMs) {
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(1.05f);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        for (TotemSphereEffect effect : sphereEffects) {
            float age = (now - effect.startTime) / durationMs;
            float appear = MathHelper.clamp(1.0f - age, 0.0f, 1.0f);
            float grow = easeOutQuad(Math.min(1.0f, age * 1.25f));
            float elapsedSec = (now - effect.startTime) / 1000.0f;
            float scale = 1.18f * (0.78f + grow * 0.10f);

            double x = effect.origin.x - cameraPos.x;
            double y = effect.origin.y - cameraPos.y;
            double z = effect.origin.z - cameraPos.z;

            for (OrbitLine line : effect.orbitLines) {
                matrices.push();
                matrices.translate(x, y, z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(effect.baseRotation + line.baseYaw + elapsedSec * line.speedDeg));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(line.tiltX));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(line.tiltZ));
                drawSphereOrbitArc(
                        matrices,
                        line.radiusX * scale,
                        line.radiusZ * scale,
                        line.yOffset,
                        line.startDeg,
                        line.arcDeg,
                        appear * line.alphaMul,
                        line.startColor,
                        line.endColor
                );
                matrices.pop();
            }
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private void drawSphereOrbitArc(MatrixStack matrices, float radiusX, float radiusZ, float y, float startDeg, float arcDeg,
                                    float alphaMul, int startColor, int endColor) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int segments = 28;
        float from = (float) Math.toRadians(startDeg);
        float to = (float) Math.toRadians(startDeg + arcDeg);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float progress = i / (float) segments;
            float angle = MathHelper.lerp(progress, from, to);
            float px = MathHelper.cos(angle) * radiusX;
            float pz = MathHelper.sin(angle) * radiusZ;
            float localY = y + MathHelper.sin(angle * 1.35f) * 0.010f;
            float edgeFade = MathHelper.clamp(1.0f - Math.abs(progress - 0.5f) * 2.0f, 0.0f, 1.0f);
            int color = fadeLerp(startColor, endColor, progress, alphaMul * (0.22f + 0.78f * edgeFade));
            buffer.vertex(matrix, px, localY, pz).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        BufferBuilder echo = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float progress = i / (float) segments;
            float angle = MathHelper.lerp(progress, from + 0.015f, to - 0.012f);
            float px = MathHelper.cos(angle) * (radiusX + 0.012f);
            float pz = MathHelper.sin(angle) * (radiusZ + 0.012f);
            float localY = y + 0.004f + MathHelper.sin(angle * 1.35f + 0.9f) * 0.008f;
            float edgeFade = MathHelper.clamp(1.0f - Math.abs(progress - 0.5f) * 2.0f, 0.0f, 1.0f);
            int color = fadeLerp(startColor, endColor, progress, alphaMul * 0.14f * edgeFade);
            echo.vertex(matrix, px, localY, pz).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(echo.end());
    }

    private List<OrbitLine> createSphereOrbitLines() {
        List<OrbitLine> lines = new ArrayList<>(5);
        lines.add(new OrbitLine(1.02f, 0.66f, 0.20f, 196f, 156f, 14f, -12f, 54f, 0.46f, GREEN_COLOR, GREEN_COLOR));
        lines.add(new OrbitLine(0.92f, 0.60f, 0.16f, 188f, 148f, 14f, -12f, 54f, 0.22f, GREEN_COLOR, GREEN_COLOR));
        lines.add(new OrbitLine(0.86f, 0.54f, -0.12f, 122f, 112f, 78f, 4f, -68f, 0.65f, YELLOW_COLOR, YELLOW_COLOR));
        lines.add(new OrbitLine(0.74f, 0.46f, -0.02f, 314f, 88f, 62f, -18f, 76f, 0.58f, GREEN_COLOR, YELLOW_COLOR));
        lines.add(new OrbitLine(0.68f, 0.34f, 0.00f, 202f, 44f, 8f, 52f, -44f, 0.18f, GREEN_COLOR, GREEN_COLOR));
        return lines;
    }

    private void drawSphereBillboard(Matrix4f matrix, float size, int color) {
        float half = size * 0.5f;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, -half, -half, 0).texture(0, 1).color(r, g, b, a);
        buffer.vertex(matrix, -half, half, 0).texture(0, 0).color(r, g, b, a);
        buffer.vertex(matrix, half, half, 0).texture(1, 0).color(r, g, b, a);
        buffer.vertex(matrix, half, -half, 0).texture(1, 1).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private int fadeLerp(int start, int end, float progress, float alphaMul) {
        int sr = (start >> 16) & 0xFF;
        int sg = (start >> 8) & 0xFF;
        int sb = start & 0xFF;

        int er = (end >> 16) & 0xFF;
        int eg = (end >> 8) & 0xFF;
        int eb = end & 0xFF;

        int r = (int) MathHelper.lerp(progress, sr, er);
        int g = (int) MathHelper.lerp(progress, sg, eg);
        int b = (int) MathHelper.lerp(progress, sb, eb);
        int a = MathHelper.clamp((int) (255 * alphaMul), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int setAlpha(int color, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private float easeOutQuad(float value) {
        float inv = 1.0f - value;
        return 1.0f - inv * inv;
    }

    private void renderSkinPlayerModel(MatrixStack matrices, float alpha, TotemGhost ghost) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, getSkinTexture());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        float unit = 1.0f / 16.0f;
        float sneakOffset = ghost.sneaking ? 0.25f : 0f;

        float limbSwing = ghost.limbSwing;
        float limbSwingAmount = Math.min(1.0f, ghost.limbSwingAmount);
        float legSwing = MathHelper.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        float armSwing = MathHelper.cos(limbSwing * 0.6662f + (float) Math.PI) * 2.0f * limbSwingAmount;

        int alphaInt = (int) (alpha * 255);

        matrices.push();
        matrices.translate(0, 24 * unit - sneakOffset, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ghost.netHeadYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ghost.headPitch));
        renderSkinBox(matrices, -4 * unit, -8 * unit, -4 * unit, 8 * unit, 8 * unit, 8 * unit,
                8, 8, 16, 16, 64, 64, alphaInt);
        matrices.pop();

        matrices.push();
        if (ghost.sneaking) {
            matrices.translate(0, 12 * unit, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(28f));
            matrices.translate(0, -12 * unit, 0);
        }
        renderSkinBox(matrices, -4 * unit, 12 * unit - sneakOffset, -2 * unit, 8 * unit, 12 * unit, 4 * unit,
                20, 20, 28, 32, 64, 64, alphaInt);
        matrices.pop();

        matrices.push();
        matrices.translate(-5 * unit, 22 * unit - sneakOffset, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(armSwing * (180f / (float) Math.PI)));
        renderSkinBox(matrices, -2 * unit, -12 * unit, -2 * unit, 4 * unit, 12 * unit, 4 * unit,
                44, 20, 48, 32, 64, 64, alphaInt);
        matrices.pop();

        matrices.push();
        matrices.translate(5 * unit, 22 * unit - sneakOffset, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-armSwing * (180f / (float) Math.PI)));
        renderSkinBox(matrices, -2 * unit, -12 * unit, -2 * unit, 4 * unit, 12 * unit, 4 * unit,
                36, 52, 40, 64, 64, 64, alphaInt);
        matrices.pop();

        matrices.push();
        matrices.translate(-2 * unit, 12 * unit - sneakOffset, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(legSwing * (180f / (float) Math.PI)));
        renderSkinBox(matrices, -2 * unit, -12 * unit, -2 * unit, 4 * unit, 12 * unit, 4 * unit,
                4, 20, 8, 32, 64, 64, alphaInt);
        matrices.pop();

        matrices.push();
        matrices.translate(2 * unit, 12 * unit - sneakOffset, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-legSwing * (180f / (float) Math.PI)));
        renderSkinBox(matrices, -2 * unit, -12 * unit, -2 * unit, 4 * unit, 12 * unit, 4 * unit,
                20, 52, 24, 64, 64, 64, alphaInt);
        matrices.pop();

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderSkinBox(MatrixStack matrices, float x, float y, float z,
                               float width, float height, float depth,
                               int u, int v, int u2, int v2,
                               int texWidth, int texHeight, int alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float x1 = x;
        float y1 = y;
        float z1 = z;
        float x2 = x + width;
        float y2 = y + height;
        float z2 = z + depth;

        float w = width * 16;
        float h = height * 16;
        float d = depth * 16;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float uMin = u / (float) texWidth;
        float vMin = v / (float) texHeight;
        float uMax = u2 / (float) texWidth;
        float vMax = v2 / (float) texHeight;

        float frontU1 = (u + d) / (float) texWidth;
        float frontU2 = (u + d + w) / (float) texWidth;
        float frontV1 = (v + d) / (float) texHeight;
        float frontV2 = (v + d + h) / (float) texHeight;

        buffer.vertex(matrix, x1, y1, z2).texture(frontU1, frontV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x2, y1, z2).texture(frontU2, frontV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x2, y2, z2).texture(frontU2, frontV1).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x1, y2, z2).texture(frontU1, frontV1).color(255, 255, 255, alpha);

        float backU1 = (u + d + w + d) / (float) texWidth;
        float backU2 = (u + d + w + d + w) / (float) texWidth;
        float backV1 = (v + d) / (float) texHeight;
        float backV2 = (v + d + h) / (float) texHeight;

        buffer.vertex(matrix, x2, y1, z1).texture(backU1, backV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x1, y1, z1).texture(backU2, backV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x1, y2, z1).texture(backU2, backV1).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x2, y2, z1).texture(backU1, backV1).color(255, 255, 255, alpha);

        float topU1 = (u + d) / (float) texWidth;
        float topU2 = (u + d + w) / (float) texWidth;
        float topV1 = v / (float) texHeight;
        float topV2 = (v + d) / (float) texHeight;

        buffer.vertex(matrix, x1, y2, z1).texture(topU1, topV1).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x1, y2, z2).texture(topU1, topV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x2, y2, z2).texture(topU2, topV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x2, y2, z1).texture(topU2, topV1).color(255, 255, 255, alpha);

        float bottomU1 = (u + d + w) / (float) texWidth;
        float bottomU2 = (u + d + w + w) / (float) texWidth;
        float bottomV1 = v / (float) texHeight;
        float bottomV2 = (v + d) / (float) texHeight;

        buffer.vertex(matrix, x1, y1, z2).texture(bottomU1, bottomV1).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x1, y1, z1).texture(bottomU1, bottomV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x2, y1, z1).texture(bottomU2, bottomV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x2, y1, z2).texture(bottomU2, bottomV1).color(255, 255, 255, alpha);

        float rightU1 = u / (float) texWidth;
        float rightU2 = (u + d) / (float) texWidth;
        float rightV1 = (v + d) / (float) texHeight;
        float rightV2 = (v + d + h) / (float) texHeight;

        buffer.vertex(matrix, x1, y1, z1).texture(rightU1, rightV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x1, y1, z2).texture(rightU2, rightV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x1, y2, z2).texture(rightU2, rightV1).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x1, y2, z1).texture(rightU1, rightV1).color(255, 255, 255, alpha);

        float leftU1 = (u + d + w) / (float) texWidth;
        float leftU2 = (u + d + w + d) / (float) texWidth;
        float leftV1 = (v + d) / (float) texHeight;
        float leftV2 = (v + d + h) / (float) texHeight;

        buffer.vertex(matrix, x2, y1, z2).texture(leftU1, leftV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x2, y1, z1).texture(leftU2, leftV2).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x2, y2, z1).texture(leftU2, leftV1).color(255, 255, 255, alpha);
        buffer.vertex(matrix, x2, y2, z2).texture(leftU1, leftV1).color(255, 255, 255, alpha);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void renderWings(MatrixStack matrices, TotemGhost ghost, float progress, float tickDelta, int themeColor, float alpha) {
        float anim = (System.currentTimeMillis() / 50.0f) * 0.22f * FLAP_SPEED + progress * 2.0f;
        float sin = MathHelper.sin(anim);
        float cos = MathHelper.cos(anim);

        float spreadAngle = 18.0f + progress * 15.0f;
        float pitchAngle = 13f + cos * 4.0f;
        float rollAngle = sin * FLAP_AMPLITUDE;

        if (ghost.sneaking) {
            spreadAngle -= 3.0f;
            pitchAngle += 8.0f;
        }

        int topColor = ColorUtils.setAlphaColor(themeColor, (int)(132 * alpha));
        int bottomColor = ColorUtils.setAlphaColor(ColorUtils.darken(themeColor, 0.85f), (int)(102 * alpha));
        int edgeColor = ColorUtils.setAlphaColor(ColorUtils.darken(themeColor, 0.70f), (int)(190 * alpha));
        int boneColorA = ColorUtils.setAlphaColor(ColorUtils.darken(themeColor, 0.52f), (int)(175 * alpha));
        int boneColorB = ColorUtils.setAlphaColor(ColorUtils.darken(themeColor, 0.58f), (int)(150 * alpha));

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        matrices.push();

        float sneakOffset = ghost.sneaking ? 0.25f : 0f;
        matrices.translate(0, 1.30f - sneakOffset, -0.08f);

        if (ghost.sneaking) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(24.0f));
        }

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        renderButterflyWing(buffer, matrices, 1.0f, spreadAngle, pitchAngle, rollAngle, WING_SCALE, topColor, bottomColor, edgeColor, boneColorA, boneColorB);
        renderButterflyWing(buffer, matrices, -1.0f, spreadAngle, pitchAngle, rollAngle, WING_SCALE, topColor, bottomColor, edgeColor, boneColorA, boneColorB);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        int glowA = ColorUtils.setAlphaColor(themeColor, (int)(72 * alpha));
        int glowB = ColorUtils.setAlphaColor(ColorUtils.darken(themeColor, 0.82f), (int)(66 * alpha));
        BufferBuilder glowBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        renderButterflyGlow(glowBuffer, matrices, 1.0f, spreadAngle, pitchAngle, rollAngle, WING_SCALE, glowA, glowB);
        renderButterflyGlow(glowBuffer, matrices, -1.0f, spreadAngle, pitchAngle, rollAngle, WING_SCALE, glowA, glowB);
        BufferRenderer.drawWithGlobalProgram(glowBuffer.end());

        matrices.pop();

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private void renderButterflyWing(BufferBuilder buffer, MatrixStack matrices, float side, float spread, float pitch, float roll, float scale,
                                     int topColor, int bottomColor, int edgeColor, int boneColorA, int boneColorB) {
        float root = 0.12f * scale;
        float topW = 1.5f * scale;
        float topH = 0.61f * scale;
        float lowW = 1.10f * scale;
        float lowH = 0.35f * scale;

        matrices.push();
        matrices.translate(0.15f * side, 0f, -0.17f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * spread));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * roll));

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        addDoubleSidedGradientQuad(buffer, matrix,
                side * root, 0.02f, 0.0f,
                side * (root + topW * 0.18f), topH * 0.95f, -0.06f,
                side * (root + topW), topH * 0.30f, -0.13f,
                side * (root + topW * 0.2f), 0.06f, -0.03f,
                topColor, bottomColor
        );

        addDoubleSidedGradientQuad(buffer, matrix,
                side * root, -0.01f, -0.02f,
                side * (root + lowW * 0.18f), -lowH * 0.94f, -0.10f,
                side * (root + lowW), -lowH * 0.36f, -0.17f,
                side * (root + lowW * 0.60f), -0.10f, -0.07f,
                bottomColor, topColor
        );

        addDoubleSidedQuad(buffer, matrix,
                side * root, 0.012f, 0.01f,
                side * root, -0.032f, -0.01f,
                side * (root + topW * 0.56f), -0.008f, -0.08f,
                side * (root + topW * 0.56f), 0.008f, -0.04f,
                (edgeColor >> 16) & 0xFF,
                (edgeColor >> 8) & 0xFF,
                edgeColor & 0xFF,
                (edgeColor >> 24) & 0xFF
        );

        renderWingBoneLine(buffer, matrix,
                side * root, 0.00f, -0.02f,
                side * (root + topW * 0.22f), topH * 0.82f, -0.07f,
                side * (root + topW), topH * 0.30f, -0.13f,
                0.016f * scale, boneColorB, boneColorB
        );
        renderWingBoneLine(buffer, matrix,
                side * root, 0.012f, -0.008f,
                side * (root + topW * 0.36f), topH * 0.56f, -0.065f,
                side * (root + topW * 0.86f), topH * 0.26f, -0.115f,
                0.012f * scale, boneColorA, boneColorB
        );
        renderWingBoneLine(buffer, matrix,
                side * root, -0.02f, -0.04f,
                side * (root + lowW * 0.22f), -lowH * 0.84f, -0.11f,
                side * (root + lowW), -lowH * 0.34f, -0.18f,
                0.009f * scale, boneColorB, boneColorB
        );
        renderWingBoneLine(buffer, matrix,
                side * root, -0.004f, -0.018f,
                side * (root + lowW * 0.34f), -lowH * 0.52f, -0.085f,
                side * (root + lowW * 0.88f), -lowH * 0.30f, -0.145f,
                0.010f * scale, boneColorB, boneColorA
        );

        matrices.pop();
    }

    private void renderButterflyGlow(BufferBuilder buffer, MatrixStack matrices, float side, float spread, float pitch, float roll,
                                     float scale, int glowA, int glowB) {
        float root = 0.12f * scale;
        float topW = 1.5f * scale;
        float topH = 0.61f * scale;
        float lowW = 1.10f * scale;
        float lowH = 0.35f * scale;

        matrices.push();
        matrices.translate(0.15f * side, 0f, -0.17f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * spread));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * roll));

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        renderWingBoneLine(buffer, matrix,
                side * root, 0.00f, -0.02f,
                side * (root + topW * 0.20f), topH * 0.86f, -0.08f,
                side * (root + topW), topH * 0.30f, -0.16f,
                0.020f * scale, glowA, glowB
        );
        renderWingBoneLine(buffer, matrix,
                side * root, -0.02f, -0.05f,
                side * (root + lowW * 0.20f), -lowH * 0.86f, -0.13f,
                side * (root + lowW), -lowH * 0.32f, -0.20f,
                0.018f * scale, glowB, glowA
        );
        renderWingBoneLine(buffer, matrix,
                side * root, 0.012f, -0.008f,
                side * (root + topW * 0.36f), topH * 0.56f, -0.070f,
                side * (root + topW * 0.84f), topH * 0.25f, -0.125f,
                0.016f * scale, glowA, glowB
        );
        matrices.pop();
    }

    private void renderHalo(MatrixStack matrices, TotemGhost ghost, int themeColor, float alpha) {
        float sneakOffset = ghost.sneaking ? 0.25f : 0f;
        float rotation = (System.currentTimeMillis() / 30.0f) % 360;

        matrices.push();
        matrices.translate(0, 1.9f - sneakOffset, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(15f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        int haloColor = ColorUtils.setAlphaColor(themeColor, (int)(200 * alpha));
        int haloGlow = ColorUtils.setAlphaColor(themeColor, (int)(100 * alpha));

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        renderHaloRing(matrix, HALO_SIZE, 0.03f, haloColor);
        renderHaloRing(matrix, HALO_SIZE + 0.02f, 0.05f, haloGlow);
        renderHaloRing(matrix, HALO_SIZE - 0.02f, 0.02f, haloGlow);

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private void renderHaloRing(Matrix4f matrix, float radius, float thickness, int color) {
        int segments = 36;
        float angleStep = (float) (Math.PI * 2 / segments);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < segments; i++) {
            float angle1 = i * angleStep;
            float angle2 = (i + 1) * angleStep;

            float x1Inner = MathHelper.cos(angle1) * (radius - thickness / 2);
            float z1Inner = MathHelper.sin(angle1) * (radius - thickness / 2);
            float x1Outer = MathHelper.cos(angle1) * (radius + thickness / 2);
            float z1Outer = MathHelper.sin(angle1) * (radius + thickness / 2);

            float x2Inner = MathHelper.cos(angle2) * (radius - thickness / 2);
            float z2Inner = MathHelper.sin(angle2) * (radius - thickness / 2);
            float x2Outer = MathHelper.cos(angle2) * (radius + thickness / 2);
            float z2Outer = MathHelper.sin(angle2) * (radius + thickness / 2);

            buffer.vertex(matrix, x1Inner, 0.01f, z1Inner).color(r, g, b, a);
            buffer.vertex(matrix, x1Outer, 0.01f, z1Outer).color(r, g, b, a);
            buffer.vertex(matrix, x2Outer, 0.01f, z2Outer).color(r, g, b, a);
            buffer.vertex(matrix, x2Inner, 0.01f, z2Inner).color(r, g, b, a);

            buffer.vertex(matrix, x1Inner, -0.01f, z1Inner).color(r, g, b, a);
            buffer.vertex(matrix, x2Inner, -0.01f, z2Inner).color(r, g, b, a);
            buffer.vertex(matrix, x2Outer, -0.01f, z2Outer).color(r, g, b, a);
            buffer.vertex(matrix, x1Outer, -0.01f, z1Outer).color(r, g, b, a);

            buffer.vertex(matrix, x1Outer, -0.01f, z1Outer).color(r, g, b, a);
            buffer.vertex(matrix, x2Outer, -0.01f, z2Outer).color(r, g, b, a);
            buffer.vertex(matrix, x2Outer, 0.01f, z2Outer).color(r, g, b, a);
            buffer.vertex(matrix, x1Outer, 0.01f, z1Outer).color(r, g, b, a);

            buffer.vertex(matrix, x1Inner, 0.01f, z1Inner).color(r, g, b, a);
            buffer.vertex(matrix, x2Inner, 0.01f, z2Inner).color(r, g, b, a);
            buffer.vertex(matrix, x2Inner, -0.01f, z2Inner).color(r, g, b, a);
            buffer.vertex(matrix, x1Inner, -0.01f, z1Inner).color(r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void addDoubleSidedQuad(BufferBuilder buffer, Matrix4f matrix,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3,
                                    float x4, float y4, float z4,
                                    int r, int g, int b, int a) {
        addQuad(buffer, matrix, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, r, g, b, a);
        addQuad(buffer, matrix, x4, y4, z4, x3, y3, z3, x2, y2, z2, x1, y1, z1, r, g, b, a);
    }

    private void addDoubleSidedGradientQuad(BufferBuilder buffer, Matrix4f matrix,
                                            float x1, float y1, float z1,
                                            float x2, float y2, float z2,
                                            float x3, float y3, float z3,
                                            float x4, float y4, float z4,
                                            int nearColor, int farColor) {
        int nr = (nearColor >> 16) & 0xFF;
        int ng = (nearColor >> 8) & 0xFF;
        int nb = nearColor & 0xFF;
        int na = (nearColor >> 24) & 0xFF;
        int fr = (farColor >> 16) & 0xFF;
        int fg = (farColor >> 8) & 0xFF;
        int fb = farColor & 0xFF;
        int fa = (farColor >> 24) & 0xFF;

        buffer.vertex(matrix, x1, y1, z1).color(nr, ng, nb, na);
        buffer.vertex(matrix, x2, y2, z2).color(fr, fg, fb, fa);
        buffer.vertex(matrix, x3, y3, z3).color(fr, fg, fb, fa);
        buffer.vertex(matrix, x4, y4, z4).color(nr, ng, nb, na);

        buffer.vertex(matrix, x4, y4, z4).color(nr, ng, nb, na);
        buffer.vertex(matrix, x3, y3, z3).color(fr, fg, fb, fa);
        buffer.vertex(matrix, x2, y2, z2).color(fr, fg, fb, fa);
        buffer.vertex(matrix, x1, y1, z1).color(nr, ng, nb, na);
    }

    private void renderWingBoneLine(BufferBuilder buffer, Matrix4f matrix,
                                    float x0, float y0, float z0,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float thickness, int colorA, int colorB) {
        float vx1 = x1 - x0;
        float vy1 = y1 - y0;
        float len1 = Math.max(1.0E-4f, (float) Math.sqrt(vx1 * vx1 + vy1 * vy1));
        float nx1 = -vy1 / len1 * thickness;
        float ny1 = vx1 / len1 * thickness;

        int aR = (colorA >> 16) & 0xFF;
        int aG = (colorA >> 8) & 0xFF;
        int aB = colorA & 0xFF;
        int aA = (colorA >> 24) & 0xFF;
        int bR = (colorB >> 16) & 0xFF;
        int bG = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;
        int bA = (colorB >> 24) & 0xFF;

        addDoubleSidedQuad(buffer, matrix,
                x0 + nx1, y0 + ny1, z0,
                x0 - nx1, y0 - ny1, z0,
                x1 - nx1, y1 - ny1, z1,
                x1 + nx1, y1 + ny1, z1,
                aR, aG, aB, aA
        );

        float vx2 = x2 - x1;
        float vy2 = y2 - y1;
        float len2 = Math.max(1.0E-4f, (float) Math.sqrt(vx2 * vx2 + vy2 * vy2));
        float nx2 = -vy2 / len2 * thickness;
        float ny2 = vx2 / len2 * thickness;

        addDoubleSidedQuad(buffer, matrix,
                x1 + nx2, y1 + ny2, z1,
                x1 - nx2, y1 - ny2, z1,
                x2 - nx2, y2 - ny2, z2,
                x2 + nx2, y2 + ny2, z2,
                bR, bG, bB, bA
        );
    }

    private void addQuad(BufferBuilder buffer, Matrix4f matrix,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         float x4, float y4, float z4,
                         int r, int g, int b, int a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x3, y3, z3).color(r, g, b, a);
        buffer.vertex(matrix, x4, y4, z4).color(r, g, b, a);
    }

    private void renderGlowingPlayerModel(MatrixStack matrices, float r, float g, float b, float alpha, TotemGhost ghost) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, getGlowTexture());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        float unit = 1.0f / 16.0f;
        float sneakOffset = ghost.sneaking ? 0.25f : 0f;

        float limbSwing = ghost.limbSwing;
        float limbSwingAmount = Math.min(1.0f, ghost.limbSwingAmount);
        float legSwing = MathHelper.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        float armSwing = MathHelper.cos(limbSwing * 0.6662f + (float) Math.PI) * 1.4f * limbSwingAmount;

        matrices.push();
        matrices.translate(0, 24 * unit - sneakOffset, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ghost.netHeadYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ghost.headPitch));
        renderGlowBox(matrices, -4 * unit, -8 * unit, -4 * unit, 8 * unit, 8 * unit, 8 * unit, r, g, b, alpha * GLOW_INTENSITY);
        matrices.pop();

        matrices.push();
        if (ghost.sneaking) {
            matrices.translate(0, 12 * unit, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25f));
            matrices.translate(0, -12 * unit, 0);
        }
        renderGlowBox(matrices, -4 * unit, 12 * unit - sneakOffset, -2 * unit, 8 * unit, 12 * unit, 4 * unit, r, g, b, alpha * GLOW_INTENSITY);
        matrices.pop();

        float armWidth = 3 * unit;

        matrices.push();
        matrices.translate(-4 * unit - armWidth / 2, 22 * unit - sneakOffset, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(armSwing * (180f / (float) Math.PI)));
        renderGlowBox(matrices, -armWidth / 2, -10 * unit, -2 * unit, armWidth, 12 * unit, 4 * unit, r, g, b, alpha * GLOW_INTENSITY);
        matrices.pop();

        matrices.push();
        matrices.translate(4 * unit + armWidth / 2, 22 * unit - sneakOffset, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-armSwing * (180f / (float) Math.PI)));
        renderGlowBox(matrices, -armWidth / 2, -10 * unit, -2 * unit, armWidth, 12 * unit, 4 * unit, r, g, b, alpha * GLOW_INTENSITY);
        matrices.pop();

        matrices.push();
        matrices.translate(-2 * unit, 12 * unit, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(legSwing * (180f / (float) Math.PI)));
        renderGlowBox(matrices, -2 * unit, -12 * unit, -2 * unit, 4 * unit, 12 * unit, 4 * unit, r, g, b, alpha * GLOW_INTENSITY);
        matrices.pop();

        matrices.push();
        matrices.translate(2 * unit, 12 * unit, 0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-legSwing * (180f / (float) Math.PI)));
        renderGlowBox(matrices, -2 * unit, -12 * unit, -2 * unit, 4 * unit, 12 * unit, 4 * unit, r, g, b, alpha * GLOW_INTENSITY);
        matrices.pop();

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderGlowBox(MatrixStack matrices, float x, float y, float z, float width, float height, float depth, float r, float g, float b, float alpha) {
        float centerX = x + width / 2;
        float centerY = y + height / 2;
        float centerZ = z + depth / 2;

        float glowSize = Math.max(width, Math.max(height, depth)) * 1.8f;

        renderGlowSprite(matrices, centerX, centerY, centerZ + depth / 2 + 0.01f, glowSize, width, height, r, g, b, alpha);
        renderGlowSprite(matrices, centerX, centerY, centerZ - depth / 2 - 0.01f, glowSize, width, height, r, g, b, alpha);

        matrices.push();
        matrices.translate(centerX, centerY, centerZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
        renderGlowSpriteRotated(matrices, 0, 0, depth / 2 + 0.01f, glowSize, depth, height, r, g, b, alpha);
        renderGlowSpriteRotated(matrices, 0, 0, -depth / 2 - 0.01f, glowSize, depth, height, r, g, b, alpha);
        matrices.pop();

        matrices.push();
        matrices.translate(centerX, centerY, centerZ);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        renderGlowSpriteRotated(matrices, 0, 0, height / 2 + 0.01f, glowSize, width, depth, r, g, b, alpha);
        renderGlowSpriteRotated(matrices, 0, 0, -height / 2 - 0.01f, glowSize, width, depth, r, g, b, alpha);
        matrices.pop();

        float innerAlpha = alpha * 0.4f;
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        renderSolidBox(matrices, x, y, z, width, height, depth, r, g, b, innerAlpha);
        RenderSystem.setShaderTexture(0, getGlowTexture());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
    }

    private void renderGlowSprite(MatrixStack matrices, float x, float y, float z, float glowSize, float boxWidth, float boxHeight, float r, float g, float b, float alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int rInt = (int) (r * 255);
        int gInt = (int) (g * 255);
        int bInt = (int) (b * 255);
        int aInt = (int) (MathHelper.clamp(alpha, 0f, 1f) * 255);

        float halfW = glowSize / 2;
        float halfH = glowSize / 2;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x - halfW, y - halfH, z).texture(0, 0).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x - halfW, y + halfH, z).texture(0, 1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x + halfW, y + halfH, z).texture(1, 1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x + halfW, y - halfH, z).texture(1, 0).color(rInt, gInt, bInt, aInt);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void renderGlowSpriteRotated(MatrixStack matrices, float x, float y, float z, float glowSize, float boxWidth, float boxHeight, float r, float g, float b, float alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int rInt = (int) (r * 255);
        int gInt = (int) (g * 255);
        int bInt = (int) (b * 255);
        int aInt = (int) (MathHelper.clamp(alpha, 0f, 1f) * 255);

        float halfW = glowSize / 2;
        float halfH = glowSize / 2;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x - halfW, y - halfH, z).texture(0, 0).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x - halfW, y + halfH, z).texture(0, 1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x + halfW, y + halfH, z).texture(1, 1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x + halfW, y - halfH, z).texture(1, 0).color(rInt, gInt, bInt, aInt);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void renderSolidBox(MatrixStack matrices, float x, float y, float z, float width, float height, float depth, float r, float g, float b, float alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float x1 = x;
        float y1 = y;
        float z1 = z;
        float x2 = x + width;
        float y2 = y + height;
        float z2 = z + depth;

        int rInt = (int) (r * 255);
        int gInt = (int) (g * 255);
        int bInt = (int) (b * 255);
        int aInt = (int) (MathHelper.clamp(alpha, 0f, 1f) * 255);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        buffer.vertex(matrix, x1, y1, z2).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x2, y1, z2).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x2, y2, z2).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x1, y2, z2).color(rInt, gInt, bInt, aInt);

        buffer.vertex(matrix, x2, y1, z1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x1, y1, z1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x1, y2, z1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x2, y2, z1).color(rInt, gInt, bInt, aInt);

        buffer.vertex(matrix, x1, y1, z1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x1, y1, z2).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x1, y2, z2).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x1, y2, z1).color(rInt, gInt, bInt, aInt);

        buffer.vertex(matrix, x2, y1, z2).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x2, y1, z1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x2, y2, z1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x2, y2, z2).color(rInt, gInt, bInt, aInt);

        buffer.vertex(matrix, x1, y2, z1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x1, y2, z2).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x2, y2, z2).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x2, y2, z1).color(rInt, gInt, bInt, aInt);

        buffer.vertex(matrix, x1, y1, z2).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x1, y1, z1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x2, y1, z1).color(rInt, gInt, bInt, aInt);
        buffer.vertex(matrix, x2, y1, z2).color(rInt, gInt, bInt, aInt);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0 - t, 3);
    }

    private float easeInCubic(float t) {
        return t * t * t;
    }

    private static class TotemSphereEffect {
        private final Vec3d origin;
        private final long startTime;
        private final float baseRotation;
        private final List<SphereParticle> particles;
        private final List<OrbitLine> orbitLines;

        private TotemSphereEffect(Vec3d origin, long startTime, float baseRotation, List<SphereParticle> particles, List<OrbitLine> orbitLines) {
            this.origin = origin;
            this.startTime = startTime;
            this.baseRotation = baseRotation;
            this.particles = particles;
            this.orbitLines = orbitLines;
        }
    }

    private static class OrbitLine {
        private final float radiusX;
        private final float radiusZ;
        private final float yOffset;
        private final float startDeg;
        private final float arcDeg;
        private final float tiltX;
        private final float tiltZ;
        private final float speedDeg;
        private final float alphaMul;
        private final int startColor;
        private final int endColor;
        private final float baseYaw;

        private OrbitLine(float radiusX, float radiusZ, float yOffset, float startDeg, float arcDeg, float tiltX, float tiltZ,
                          float speedDeg, float alphaMul, int startColor, int endColor) {
            this.radiusX = radiusX;
            this.radiusZ = radiusZ;
            this.yOffset = yOffset;
            this.startDeg = startDeg;
            this.arcDeg = arcDeg;
            this.tiltX = tiltX;
            this.tiltZ = tiltZ;
            this.speedDeg = speedDeg;
            this.alphaMul = alphaMul;
            this.startColor = startColor;
            this.endColor = endColor;
            this.baseYaw = startDeg * 0.35f;
        }
    }

    private static class SphereParticle {
        private final Vec3d direction;
        private final float spread;
        private final float swirlAmount;
        private final float rotationScale;
        private final float timeScale;
        private final float progressOffset;
        private final int color;

        private SphereParticle(Vec3d direction, float spread, float swirlAmount, float rotationScale, float timeScale, float progressOffset, int color) {
            this.direction = direction;
            this.spread = spread;
            this.swirlAmount = swirlAmount;
            this.rotationScale = rotationScale;
            this.timeScale = timeScale;
            this.progressOffset = progressOffset;
            this.color = color;
        }
    }

    private static class TotemGhost {
        final Vec3d position;
        final float bodyYaw;
        final float netHeadYaw;
        final float headPitch;
        final float limbSwing;
        final float limbSwingAmount;
        final boolean sneaking;
        final float height;
        final long startTime;

        TotemGhost(Vec3d position, float bodyYaw, float netHeadYaw, float headPitch,
                   float limbSwing, float limbSwingAmount, boolean sneaking, float height, long startTime) {
            this.position = position;
            this.bodyYaw = bodyYaw;
            this.netHeadYaw = netHeadYaw;
            this.headPitch = headPitch;
            this.limbSwing = limbSwing;
            this.limbSwingAmount = limbSwingAmount;
            this.sneaking = sneaking;
            this.height = height;
            this.startTime = startTime;
        }
    }
}


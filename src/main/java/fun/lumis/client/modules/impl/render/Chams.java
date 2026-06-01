package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.Priority;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.render.ShaderUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.mixin.LivingEntityRendererAccessor;

import java.awt.Color;

public class Chams extends Module {

    public static final Chams INSTANCE = new Chams();

    public static final String TARGET_PLAYERS = "Игроков";
    public static final String TARGET_FRIENDS = "Друзей";
    public static final String TARGET_SELF = "Себя";

    private static final int DEFAULT_FILL_ALPHA = 130;
    private static final float DEFAULT_LINE_WIDTH = 0.5f;
    private static final float CLIENT_FILL_SATURATION = 1.18f;
    private static final float CLIENT_FILL_BRIGHTNESS = 1.12f;
    private static final float CLIENT_OUTLINE_SATURATION = 1.12f;
    private static final float CLIENT_OUTLINE_BRIGHTNESS = 1.08f;
    private static final float MIN_PULSE_ALPHA = 0.65f;
    private static final float PULSE_SWING = 0.35f;
    private static final int FRIEND_FILL_COLOR = new Color(85, 255, 85, 60).getRGB();
    private static final int FRIEND_OUTLINE_COLOR = new Color(100, 255, 100, 255).getRGB();
    private static final long OUTLINE_RETRY_DELAY_MS = 3000L;

    private final ListSetting rendering = new ListSetting("Отображать",
            new BooleanSetting(TARGET_PLAYERS, true),
            new BooleanSetting(TARGET_FRIENDS, true),
            new BooleanSetting(TARGET_SELF, false)
    );
    private final BooleanSetting waves = new BooleanSetting("Волны", true);
    private final FloatSetting waveSpeedX = new FloatSetting("Скорость X", 0.22f, 0.0f, 1.5f, 0.01f)
            .visible(waves::isState);
    private final FloatSetting waveSpeedY = new FloatSetting("Скорость Y", 0.15f, 0.0f, 1.5f, 0.01f)
            .visible(waves::isState);
    private final FloatSetting waveScale = new FloatSetting("Размер волн", 1.35f, 0.2f, 4.0f, 0.05f)
            .visible(waves::isState);
    private final FloatSetting waveDensity = new FloatSetting("Плотность волн", 1.15f, 0.5f, 3.0f, 0.05f)
            .visible(waves::isState);
    private final FloatSetting waveGlow = new FloatSetting("Сила волн", 1.0f, 0.2f, 3.0f, 0.05f)
            .visible(waves::isState);
    private final BooleanSetting glow = new BooleanSetting("Свечение", true);
    private final FloatSetting glowIntensity = new FloatSetting("Сила свечения", 2.0f, 1.0f, 5.0f, 0.1f)
            .visible(glow::isState);
    private final FloatSetting glowLayers = new FloatSetting("Слои свечения", 3.0f, 1.0f, 6.0f, 1.0f)
            .visible(glow::isState);
    private final BooleanSetting pulse = new BooleanSetting("Пульсирование", false);
    private final FloatSetting pulseSpeed = new FloatSetting("Скорость пульсации", 2.0f, 0.5f, 5.0f, 0.1f)
            .visible(pulse::isState);
    private final BooleanSetting hideOriginal = new BooleanSetting("Скрыть оригинал", false);
    private final BooleanSetting hideItemsAndCape = new BooleanSetting("Скрывать предметы и плащ", false);

    private final long startTime = System.currentTimeMillis();
    private boolean outlineAssistReady;
    private long nextOutlineRetryAt;

    private Chams() {
        super("Chams", "Чамсы по модели игрока", ModuleCategory.RENDER);
        addSettings(
                rendering,
                waves,
                waveSpeedX,
                waveSpeedY,
                waveScale,
                waveDensity,
                waveGlow,
                glow,
                glowIntensity,
                glowLayers,
                pulse,
                pulseSpeed,
                hideOriginal,
                hideItemsAndCape
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
        outlineAssistReady = false;
        nextOutlineRetryAt = 0L;
        tryEnsureOutlineProcessor();
    }

    @Override
    public void onDisable() {
        outlineAssistReady = false;
        nextOutlineRetryAt = 0L;
        super.onDisable();
    }

    @EventLink(priority = Priority.HIGH)
    public void onRender3D(Event3DRender event) {
        if (!isEnable() || mc.world == null || mc.player == null) {
            return;
        }

        if (hasOutlineAssistTargets() && !outlineAssistReady && System.currentTimeMillis() >= nextOutlineRetryAt) {
            tryEnsureOutlineProcessor();
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!affects(player)) {
                continue;
            }
            if (player == mc.player && mc.options.getPerspective() == Perspective.FIRST_PERSON) {
                continue;
            }
            renderManualPlayer(event, player);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    private void renderManualPlayer(Event3DRender event, PlayerEntity player) {
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) {
            return;
        }

        EntityRenderer<?, ?> rawRenderer = (EntityRenderer<?, ?>) mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(rawRenderer instanceof PlayerEntityRenderer renderer)) {
            return;
        }

        PlayerEntityRenderState state = renderer.createRenderState();
        renderer.updateRenderState(clientPlayer, state, event.getTickDelta());
        PlayerEntityModel model = renderer.getModel();
        model.setAngles(state);

        MatrixStack matrices = event.getMatrices();
        matrices.push();
        setupModelMatrix(matrices, state, renderer, event.getCamera().getPos(), player, event.getTickDelta());

        int fillColor = resolveFillColor(player);
        int outlineColor = resolveOutlineColor(player);
        renderShaderFillModel(matrices, model, 0.0f, fillColor);
        renderOutlineModel(matrices, model, 0.0f, outlineColor);

        matrices.pop();
    }

    private void setupModelMatrix(MatrixStack matrices, PlayerEntityRenderState state, PlayerEntityRenderer renderer, Vec3d cameraPos, PlayerEntity player, float tickDelta) {
        Vec3d pos = player.getLerpedPos(tickDelta);
        double x = pos.x - cameraPos.x;
        double y = pos.y - cameraPos.y;
        double z = pos.z - cameraPos.z;
        matrices.translate(x, y, z);

        if (state.sleepingDirection != null) {
            float eyeOffset = state.standingEyeHeight - 0.1f;
            matrices.translate(-state.sleepingDirection.getOffsetX() * eyeOffset, 0.0f, -state.sleepingDirection.getOffsetZ() * eyeOffset);
        }

        float baseScale = state.baseScale;
        matrices.scale(baseScale, baseScale, baseScale);
        LivingEntityRendererAccessor accessor = (LivingEntityRendererAccessor) renderer;
        accessor.lumis$setupTransforms(state, matrices, state.bodyYaw, baseScale);
        matrices.scale(-1.0f, -1.0f, 1.0f);
        accessor.lumis$scale(state, matrices);
        matrices.translate(0.0f, -1.501f, 0.0f);
    }

    private void renderShaderFillModel(MatrixStack matrices, BipedEntityModel<?> model, float expand, int color) {
        if (!waves.isState()) {
            renderSolidFillModel(matrices, model, expand, color);
            return;
        }

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.chamsFill);
        if (shader == null) {
            return;
        }

        RenderSystem.setShader(ShaderUtils.chamsFill);
        setUniform(shader, "time", waves.isState() ? (System.currentTimeMillis() - startTime) / 1000.0f : 0.0f);
        setUniform(shader, "speedX", waveSpeedX.get());
        setUniform(shader, "speedY", waveSpeedY.get());
        setUniform(shader, "scale", waveScale.get());
        setUniform(shader, "density", waveDensity.get());
        setUniform(shader, "glowStrength", waveGlow.get());

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        var root = model.getRootPart();
        renderFillPart(matrices, buffer, root, model.head, -4, -8, -4, 8, 8, 8, expand, color);
        renderFillPart(matrices, buffer, root, model.body, -4, 0, -2, 8, 12, 4, expand, color);
        renderFillPart(matrices, buffer, root, model.rightArm, -3, -2, -2, 4, 12, 4, expand, color);
        renderFillPart(matrices, buffer, root, model.leftArm, -1, -2, -2, 4, 12, 4, expand, color);
        renderFillPart(matrices, buffer, root, model.rightLeg, -2, 0, -2, 4, 12, 4, expand, color);
        renderFillPart(matrices, buffer, root, model.leftLeg, -2, 0, -2, 4, 12, 4, expand, color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void renderSolidFillModel(MatrixStack matrices, BipedEntityModel<?> model, float expand, int color) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        var root = model.getRootPart();
        renderSolidFillPart(matrices, buffer, root, model.head, -4, -8, -4, 8, 8, 8, expand, color);
        renderSolidFillPart(matrices, buffer, root, model.body, -4, 0, -2, 8, 12, 4, expand, color);
        renderSolidFillPart(matrices, buffer, root, model.rightArm, -3, -2, -2, 4, 12, 4, expand, color);
        renderSolidFillPart(matrices, buffer, root, model.leftArm, -1, -2, -2, 4, 12, 4, expand, color);
        renderSolidFillPart(matrices, buffer, root, model.rightLeg, -2, 0, -2, 4, 12, 4, expand, color);
        renderSolidFillPart(matrices, buffer, root, model.leftLeg, -2, 0, -2, 4, 12, 4, expand, color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void renderSolidFillPart(MatrixStack baseStack, BufferBuilder buffer, net.minecraft.client.model.ModelPart root, net.minecraft.client.model.ModelPart part,
                                     float offX, float offY, float offZ, float width, float height, float depth, float expand, int color) {
        baseStack.push();
        root.rotate(baseStack);
        part.rotate(baseStack);

        Matrix4f matrix = baseStack.peek().getPositionMatrix();
        float scale = 1f / 16f;
        float expandScale = expand * scale;

        float minX = offX * scale - expandScale;
        float minY = offY * scale - expandScale;
        float minZ = offZ * scale - expandScale;
        float maxX = (offX + width) * scale + expandScale;
        float maxY = (offY + height) * scale + expandScale;
        float maxZ = (offZ + depth) * scale + expandScale;

        addSolidQuad(buffer, matrix, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color);
        addSolidQuad(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, color);
        addSolidQuad(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, color);
        addSolidQuad(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ, color);
        addSolidQuad(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, color);
        addSolidQuad(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, color);

        baseStack.pop();
    }

    private void renderFillPart(MatrixStack baseStack, BufferBuilder buffer, net.minecraft.client.model.ModelPart root, net.minecraft.client.model.ModelPart part,
                                float offX, float offY, float offZ, float width, float height, float depth, float expand, int color) {
        baseStack.push();
        root.rotate(baseStack);
        part.rotate(baseStack);

        Matrix4f matrix = baseStack.peek().getPositionMatrix();
        float scale = 1f / 16f;
        float expandScale = expand * scale;

        float minX = offX * scale - expandScale;
        float minY = offY * scale - expandScale;
        float minZ = offZ * scale - expandScale;
        float maxX = (offX + width) * scale + expandScale;
        float maxY = (offY + height) * scale + expandScale;
        float maxZ = (offZ + depth) * scale + expandScale;

        addQuad(buffer, matrix, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color);
        addQuad(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, color);
        addQuad(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, color);
        addQuad(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ, color);
        addQuad(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, color);
        addQuad(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, color);

        baseStack.pop();
    }

    private void addQuad(BufferBuilder buffer, Matrix4f matrix,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         float x4, float y4, float z4,
                         int color) {
        int r = ColorUtils.red(color);
        int g = ColorUtils.green(color);
        int b = ColorUtils.blue(color);
        int a = ColorUtils.alpha(color);

        float u1 = waveU(x1, y1, z1);
        float v1 = waveV(x1, y1, z1);
        float u2 = waveU(x2, y2, z2);
        float v2 = waveV(x2, y2, z2);
        float u3 = waveU(x3, y3, z3);
        float v3 = waveV(x3, y3, z3);
        float u4 = waveU(x4, y4, z4);
        float v4 = waveV(x4, y4, z4);

        buffer.vertex(matrix, x1, y1, z1).texture(u1, v1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).texture(u2, v2).color(r, g, b, a);
        buffer.vertex(matrix, x3, y3, z3).texture(u3, v3).color(r, g, b, a);
        buffer.vertex(matrix, x4, y4, z4).texture(u4, v4).color(r, g, b, a);
    }

    private void addSolidQuad(BufferBuilder buffer, Matrix4f matrix,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float x3, float y3, float z3,
                              float x4, float y4, float z4,
                              int color) {
        int r = ColorUtils.red(color);
        int g = ColorUtils.green(color);
        int b = ColorUtils.blue(color);
        int a = ColorUtils.alpha(color);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x3, y3, z3).color(r, g, b, a);
        buffer.vertex(matrix, x4, y4, z4).color(r, g, b, a);
    }

    private float waveU(float x, float y, float z) {
        return x * 1.15f + z * 0.72f;
    }

    private float waveV(float x, float y, float z) {
        return y * 1.05f - z * 0.38f + x * 0.18f;
    }

    private void renderOutlineModel(MatrixStack matrices, BipedEntityModel<?> model, float expand, int color) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        RenderSystem.lineWidth(DEFAULT_LINE_WIDTH);

        if (glow.isState()) {
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
            int layers = Math.max(1, Math.round(glowLayers.get()));
            float intensity = Math.max(1.0f, glowIntensity.get());
            for (int index = layers; index >= 1; index--) {
                float layerExpand = expand + index * 0.5f * intensity;
                float alphaMul = (1.0f / (index + 1)) * 0.7f;
                int alpha = Math.max(1, Math.min(255, Math.round(ColorUtils.alpha(color) * alphaMul)));
                drawOutlineParts(matrices, model, layerExpand, withAlpha(color, alpha));
            }
        }

        RenderSystem.defaultBlendFunc();
        drawOutlineParts(matrices, model, expand, color);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private void drawOutlineParts(MatrixStack matrices, BipedEntityModel<?> model, float expand, int color) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        var root = model.getRootPart();
        renderPartOutlineLines(matrices, buffer, root, model.head, -4, -8, -4, 8, 8, 8, expand, color);
        renderPartOutlineLines(matrices, buffer, root, model.body, -4, 0, -2, 8, 12, 4, expand, color);
        renderPartOutlineLines(matrices, buffer, root, model.rightArm, -3, -2, -2, 4, 12, 4, expand, color);
        renderPartOutlineLines(matrices, buffer, root, model.leftArm, -1, -2, -2, 4, 12, 4, expand, color);
        renderPartOutlineLines(matrices, buffer, root, model.rightLeg, -2, 0, -2, 4, 12, 4, expand, color);
        renderPartOutlineLines(matrices, buffer, root, model.leftLeg, -2, 0, -2, 4, 12, 4, expand, color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void renderPartOutlineLines(MatrixStack baseStack, BufferBuilder buffer, net.minecraft.client.model.ModelPart root, net.minecraft.client.model.ModelPart part,
                                        float offX, float offY, float offZ, float width, float height, float depth, float expand, int color) {
        baseStack.push();
        root.rotate(baseStack);
        part.rotate(baseStack);

        float scale = 1f / 16f;
        float expandScale = expand * scale;
        float minX = offX * scale - expandScale;
        float minY = offY * scale - expandScale;
        float minZ = offZ * scale - expandScale;
        float maxX = (offX + width) * scale + expandScale;
        float maxY = (offY + height) * scale + expandScale;
        float maxZ = (offZ + depth) * scale + expandScale;
        Matrix4f matrix = baseStack.peek().getPositionMatrix();

        addLine(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, color);
        addLine(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, color);
        addLine(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, color);
        addLine(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, color);

        addLine(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, color);
        addLine(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        addLine(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        addLine(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, color);

        addLine(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, color);
        addLine(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, color);
        addLine(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        addLine(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, color);

        baseStack.pop();
    }

    private void addLine(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        int r = ColorUtils.red(color);
        int g = ColorUtils.green(color);
        int b = ColorUtils.blue(color);
        int a = ColorUtils.alpha(color);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
    }

    private void setUniform(ShaderProgram shader, String name, float value) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    public boolean affects(PlayerEntity player) {
        if (!isEnable() || player == null || !player.isAlive()) {
            return false;
        }
        if (player == mc.player) {
            return rendering.is(TARGET_SELF) && mc.options.getPerspective() != Perspective.FIRST_PERSON;
        }
        if (isFriend(player)) {
            return rendering.is(TARGET_FRIENDS);
        }
        return rendering.is(TARGET_PLAYERS);
    }

    public boolean shouldHideBaseModel(PlayerEntity player) {
        return hideOriginal.isState() && affects(player);
    }

    public boolean shouldHideItemsAndCape(PlayerEntity player) {
        return hideItemsAndCape.isState() && affects(player);
    }

    public boolean shouldUseOutlineAssist(PlayerEntity player) {
        return affects(player);
    }

    public boolean shouldHideOutlineFramebuffer() {
        return isEnable() && hasOutlineAssistTargets();
    }

    public int resolveFillColor(PlayerEntity player) {
        return applyPulse(baseFillColor(player));
    }

    public int resolveOutlineColor(PlayerEntity player) {
        return applyPulse(baseOutlineColor(player));
    }

    private int baseFillColor(PlayerEntity player) {
        if (isFriend(player)) {
            return FRIEND_FILL_COLOR;
        }
        return vividWithAlpha(ColorUtils.getThemeColor(), CLIENT_FILL_SATURATION, CLIENT_FILL_BRIGHTNESS, DEFAULT_FILL_ALPHA);
    }

    private int baseOutlineColor(PlayerEntity player) {
        if (isFriend(player)) {
            return FRIEND_OUTLINE_COLOR;
        }
        return vividWithAlpha(ColorUtils.getThemeColor(), CLIENT_OUTLINE_SATURATION, CLIENT_OUTLINE_BRIGHTNESS, 255);
    }

    private int applyPulse(int color) {
        if (!pulse.isState()) {
            return color;
        }
        float elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0f;
        float pulseValue = (float) ((Math.sin(elapsedSeconds * pulseSpeed.get() * Math.PI) + 1.0) * 0.5);
        float alphaMul = MIN_PULSE_ALPHA + PULSE_SWING * pulseValue;
        return ColorUtils.multAlpha(color, alphaMul);
    }

    private int vividWithAlpha(int color, float saturationBoost, float brightnessBoost, int alpha) {
        float[] hsb = Color.RGBtoHSB(ColorUtils.red(color), ColorUtils.green(color), ColorUtils.blue(color), null);
        float saturation = MathHelper.clamp(hsb[1] * saturationBoost, 0.0f, 1.0f);
        float brightness = MathHelper.clamp(Math.max(hsb[2], 0.8f) * brightnessBoost, 0.0f, 1.0f);
        int rgb = Color.HSBtoRGB(hsb[0], saturation, brightness);
        return ColorUtils.rgba(ColorUtils.red(rgb), ColorUtils.green(rgb), ColorUtils.blue(rgb), alpha);
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private boolean isFriend(PlayerEntity player) {
        return Lumis.INSTANCE != null
                && Lumis.INSTANCE.friendStorage != null
                && Lumis.INSTANCE.friendStorage.isFriend(player.getName().getString());
    }

    private boolean hasOutlineAssistTargets() {
        if (!isEnable() || mc.world == null || mc.player == null) {
            return false;
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (shouldUseOutlineAssist(player)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryEnsureOutlineProcessor() {
        if (mc.worldRenderer == null) {
            outlineAssistReady = false;
            return false;
        }

        if (mc.worldRenderer.getEntityOutlinesFramebuffer() != null) {
            outlineAssistReady = true;
            return true;
        }

        try {
            mc.worldRenderer.loadEntityOutlinePostProcessor();
        } catch (Exception ignored) {
        }

        outlineAssistReady = mc.worldRenderer.getEntityOutlinesFramebuffer() != null;
        if (!outlineAssistReady) {
            nextOutlineRetryAt = System.currentTimeMillis() + OUTLINE_RETRY_DELAY_MS;
        }
        return outlineAssistReady;
    }

}

package fun.lumis.client.modules.impl.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventGameUpdate;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.RotationStorage;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.rotate.Rotation;
import fun.lumis.api.utils.rotate.RotationUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.combat.components.gcd.GCDUtil;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AimBot extends Module {

    public static AimBot INSTANCE = new AimBot();

    private final ListSetting targetTypes = new ListSetting("Типы целей",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("В броне", true),
            new BooleanSetting("Без брони", false),
            new BooleanSetting("Мобы", false),
            new BooleanSetting("Зомби", false)
    );
    private final FloatSetting range = new FloatSetting("Дистанция", 40f, 10f, 100f, 1f);
    private final FloatSetting aimTime = new FloatSetting("Время наводки (тики)", 10f, 0f, 40f, 1f);
    private final BooleanSetting silentRotations = new BooleanSetting("Тихие повороты", true);
    private final BooleanSetting showCrosshair = new BooleanSetting("Показать прицел", true);
    private final FloatSetting crosshairSize = new FloatSetting("Размер прицела", 1.0f, 0.3f, 3.0f, 0.1f);

    private LivingEntity target = null;
    private boolean isAiming = false;
    private float aimProgress = 0f;
    private Rotation targetRotation = null;

    public AimBot() {
        super("AimBot", "Авто-наведение для лука и арбалета", ModuleCategory.COMBAT);
        addSettings(targetTypes, range, aimTime, silentRotations, showCrosshair, crosshairSize);
    }

    private Identifier getCrosshairTexture() {
        return Identifier.of("lumis", "textures/cross/hit.png");
    }

    private boolean isHoldingBowOrCrossbow() {
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();
        return mainHand.getItem() instanceof BowItem ||
                mainHand.getItem() instanceof CrossbowItem ||
                offHand.getItem() instanceof BowItem ||
                offHand.getItem() instanceof CrossbowItem;
    }

    private boolean isUsingBowOrCrossbow() {
        return mc.player.isUsingItem() && isHoldingBowOrCrossbow();
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player) return false;
        if (!entity.isAlive() || entity.getHealth() <= 0) return false;

        if (entity instanceof PlayerEntity) {
            if (!targetTypes.is("Игроки")) return false;
            if (Lumis.INSTANCE.friendStorage.isFriend(entity.getName().getString())) return false;

            boolean hasArmor = false;
            PlayerEntity player = (PlayerEntity) entity;
            for (ItemStack armor : player.getArmorItems()) {
                if (!armor.isEmpty()) {
                    hasArmor = true;
                    break;
                }
            }

            if (targetTypes.is("В броне") && hasArmor) return true;
            if (targetTypes.is("Без брони") && !hasArmor) return true;
            if (!targetTypes.is("В броне") && !targetTypes.is("Без брони")) return true;

            return false;
        }

        if (entity instanceof ZombieEntity) {
            return targetTypes.is("Зомби");
        }

        if (entity instanceof HostileEntity) {
            return targetTypes.is("Мобы");
        }

        return false;
    }

    private LivingEntity findBestTarget() {
        List<LivingEntity> targets = new ArrayList<>();

        Box searchBox = mc.player.getBoundingBox().expand(range.getValue().floatValue());

        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, searchBox, e -> true)) {
            if (!isValidTarget(entity)) continue;

            double dist = mc.player.distanceTo(entity);
            if (dist > range.getValue().floatValue()) continue;

            targets.add(entity);
        }

        if (targets.isEmpty()) return null;

        targets.sort(Comparator.comparingDouble(entity -> mc.player.distanceTo(entity)));
        return targets.get(0);
    }

    
    private Rotation calculateBowRotation(LivingEntity target) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d targetPos = target.getBoundingBox().getCenter();

        double dx = targetPos.x - eyes.x;
        double dy = targetPos.y - eyes.y;
        double dz = targetPos.z - eyes.z;

        double distance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distance));

        return new Rotation(yaw, pitch);
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (!showCrosshair.isState() || target == null || !isAiming) return;

        float partialTicks = event.getTickDelta();
        Vec3d targetPos = new Vec3d(
                MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()) + target.getHeight() / 2.0,
                MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ())
        );

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrices = event.getMatrices();

        double renderX = targetPos.x - cameraPos.x;
        double renderY = targetPos.y - cameraPos.y;
        double renderZ = targetPos.z - cameraPos.z;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, getCrosshairTexture());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

        float size = crosshairSize.get() * 0.5f;
        int alpha = (int) (255 * aimProgress);
        int color = ColorUtils.getThemeColor();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buffer.vertex(matrix, -size, -size, 0).texture(0, 1).color(r, g, b, alpha);
        buffer.vertex(matrix, -size, size, 0).texture(0, 0).color(r, g, b, alpha);
        buffer.vertex(matrix, size, size, 0).texture(1, 0).color(r, g, b, alpha);
        buffer.vertex(matrix, size, -size, 0).texture(1, 1).color(r, g, b, alpha);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        matrices.pop();

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    @EventLink
    public void onGameUpdate(EventGameUpdate e) {
        if (mc.player == null || mc.world == null) return;

        isAiming = isUsingBowOrCrossbow();

        if (isAiming) {
            LivingEntity newTarget = findBestTarget();

            if (newTarget != null) {
                if (target != newTarget) {
                    target = newTarget;
                    aimProgress = 0f;
                }

                Rotation newRotation = calculateBowRotation(target);

                float maxStep = 1f / Math.max(1f, aimTime.getValue().floatValue());
                aimProgress = Math.min(aimProgress + maxStep, 1f);

                float currentYaw = mc.player.getYaw();
                float currentPitch = mc.player.getPitch();
                float targetYaw = newRotation.getYaw();
                float targetPitch = newRotation.getPitch();

                float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
                float pitchDiff = targetPitch - currentPitch;

                float stepYaw = yawDiff * aimProgress;
                float stepPitch = pitchDiff * aimProgress;

                targetRotation = new Rotation(
                        currentYaw + stepYaw,
                        currentPitch + stepPitch
                );
            }
        } else {
            target = null;
            targetRotation = null;
            aimProgress = 0f;
        }
    }

    
    @EventLink
    public void onUpdate(final EventGameUpdate ignoredghj) {
        if (target != null && isAiming && targetRotation != null) {
            if (silentRotations.isState()) {
                float gcd = GCDUtil.getGCD();
                float yaw = targetRotation.getYaw();
                float pitch = targetRotation.getPitch();

                yaw -= (yaw - mc.player.getYaw()) % gcd;
                pitch -= (pitch - mc.player.getPitch()) % gcd;

                RotationStorage.update(new Rotation(yaw, pitch), 180f, 180f, 45f, 45f, 0, 2, false);
            } else {
                mc.player.setYaw(targetRotation.getYaw());
                mc.player.setPitch(targetRotation.getPitch());
            }
        }
    }

    public LivingEntity getTarget() {
        return target;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        target = null;
        isAiming = false;
        aimProgress = 0f;
        targetRotation = null;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        target = null;
        isAiming = false;
        aimProgress = 0f;
        targetRotation = null;
    }
}

package fun.lumis.client.modules.impl.misc;

import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventBinding;
import fun.lumis.api.events.implement.EventGameUpdate;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.RotationStorage;
import fun.lumis.api.utils.rotate.Rotation;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BindSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public class EcOpen extends Module {

    public static EcOpen INSTANCE = new EcOpen();

    private final BindSetting openKey = new BindSetting("Открыть", -1);
    private final FloatSetting range = new FloatSetting("Дистанция", 6f, 3f, 6f, 0.1f);

    private BlockPos targetChest = null;
    private boolean shouldRotate = false;
    private int rotationTicks = 0;
    private float currentYaw, currentPitch;

    public EcOpen() {
        super("EcOpen", "Открывает эндер сундук по бинду", ModuleCategory.MISC);
        addSettings(openKey, range);
    }

    @Override
    public void onEnable() {
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        reset();
        super.onDisable();
    }

    @EventLink
    public void onBinding(EventBinding event) {
        if (mc.currentScreen != null || mc.player == null || mc.world == null) return;

        if (event.getKey() == openKey.getKey()) {
            findEnderChest();
        }
    }

    @EventLink
    public void onGameUpdate(EventGameUpdate event) {
        if (!shouldRotate || targetChest == null || mc.player == null) return;

        if (!mc.world.getBlockState(targetChest).isOf(Blocks.ENDER_CHEST)) {
            reset();
            return;
        }

        Vec3d target = Vec3d.ofCenter(targetChest);
        float[] rotations = calculateRotation(target);

        float deltaYaw = MathHelper.wrapDegrees(rotations[0] - currentYaw);
        float deltaPitch = rotations[1] - currentPitch;

        currentYaw += deltaYaw * 0.8f;
        currentPitch = MathHelper.clamp(currentPitch + deltaPitch * 0.8f, -90f, 90f);

        RotationStorage.update(new Rotation(currentYaw, currentPitch), 360, 360, 360, 360, 1, 1, false);
        rotationTicks++;
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!shouldRotate || targetChest == null || mc.player == null) return;

        if (rotationTicks >= 2) {
            Vec3d hitVec = Vec3d.ofCenter(targetChest).add(0, 0.5, 0);
            BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, targetChest, false);

            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            mc.player.swingHand(Hand.MAIN_HAND);
            reset();
        }

        if (rotationTicks > 20) reset();
    }

    private void findEnderChest() {
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        double maxDist = range.getValue().floatValue() * range.getValue().floatValue();
        double closestDist = Double.MAX_VALUE;
        BlockPos closest = null;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).isOf(Blocks.ENDER_CHEST)) {
                        double dist = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
                        if (dist < closestDist && dist <= maxDist) {
                            closestDist = dist;
                            closest = pos;
                        }
                    }
                }
            }
        }

        if (closest != null) {
            targetChest = closest;
            shouldRotate = true;
            rotationTicks = 0;
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }
    }

    private float[] calculateRotation(Vec3d target) {
        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        return new float[]{yaw, MathHelper.clamp(pitch, -90f, 90f)};
    }

    private void reset() {
        targetChest = null;
        shouldRotate = false;
        rotationTicks = 0;
    }
}
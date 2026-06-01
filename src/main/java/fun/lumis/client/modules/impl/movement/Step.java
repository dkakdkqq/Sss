package fun.lumis.client.modules.impl.movement;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

public class Step extends Module {

    public static Step INSTANCE = new Step();

    public ModeSetting mode = new ModeSetting("Режим", "Vanilla", "Vanilla", "NCP", "Motion");
    public FloatSetting height = new FloatSetting("Высота", 1.0f, 1.0f, 10.0f, 0.5f);
    public BooleanSetting reverse = new BooleanSetting("Reverse", false);
    public FloatSetting reverseHeight = new FloatSetting("Высота Reverse", 1.0f, 1.0f, 10.0f, 0.5f);

    private int timer = 0;

    public Step() {
        super("Step", "Моментально взбирается на блок", ModuleCategory.MOVEMENT);
        addSettings(mode, height, reverse, reverseHeight);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        timer = 0;
    }

    @EventLink
    public void onUpdate(final EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        if (reverse.isState() && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()
                && !mc.player.isSneaking() && !isBlockAbove()) {

            float fallDistance = reverseHeight.get();

            if (canFall(fallDistance)) {
                Vec3d vel = mc.player.getVelocity();
                mc.player.setVelocity(vel.x, -fallDistance, vel.z);
            }
        }

        if (!mc.player.horizontalCollision || !mc.player.isOnGround() || mc.options.jumpKey.isPressed()) {
            timer = 0;
            return;
        }

        float stepHeight = getStepHeight();

        if (stepHeight > 0.6f && stepHeight <= height.get()) {
            if (mode.is("Vanilla")) {
                handleVanillaStep(stepHeight);
            }

            if (mode.is("NCP")) {
                handleNCPStep(stepHeight);
            }

            if (mode.is("Motion")) {
                handleMotionStep(stepHeight);
            }
        }
    }

    private void handleVanillaStep(float stepHeight) {
        mc.player.setPosition(
                mc.player.getX(),
                mc.player.getY() + stepHeight,
                mc.player.getZ()
        );
    }

    private void handleNCPStep(float stepHeight) {
        double[] offsets = null;
        double baseY = mc.player.getY();

        if (stepHeight <= 1.0f) {
            offsets = new double[]{0.42, 0.753};
        } else if (stepHeight <= 1.5f) {
            offsets = new double[]{0.42, 0.75, 1.0, 1.16, 1.23, 1.2};
        } else if (stepHeight <= 2.0f) {
            offsets = new double[]{0.42, 0.78, 0.63, 0.51, 0.9, 1.21, 1.45, 1.43};
        } else if (stepHeight <= 2.5f) {
            offsets = new double[]{0.425, 0.821, 0.699, 0.599, 1.022, 1.372, 1.652, 1.869, 2.019, 1.907};
        } else if (stepHeight <= 3.0f) {
            offsets = new double[]{0.42, 0.78, 0.63, 0.51, 0.9, 1.21, 1.45, 1.43, 1.78, 2.1, 2.4, 2.7};
        }

        if (offsets != null) {
            for (double offset : offsets) {
                mc.player.setPosition(
                        mc.player.getX(),
                        baseY + offset,
                        mc.player.getZ()
                );
            }
        }
    }

    private void handleMotionStep(float stepHeight) {
        Vec3d velocity = mc.player.getVelocity();
        double motionY = 0.42;

        if (stepHeight <= 1.0f) {
            motionY = 0.42;
        } else if (stepHeight <= 1.5f) {
            motionY = 0.52;
        } else if (stepHeight <= 2.0f) {
            motionY = 0.62;
        } else if (stepHeight <= 2.5f) {
            motionY = 0.72;
        } else if (stepHeight <= 3.0f) {
            motionY = 0.82;
        }

        mc.player.setVelocity(velocity.x, motionY, velocity.z);
    }

    private float getStepHeight() {
        Box box = mc.player.getBoundingBox();
        float maxY = 0;

        double checkDistance = 0.3;
        double playerYaw = Math.toRadians(mc.player.getYaw());
        double offsetX = -Math.sin(playerYaw) * checkDistance;
        double offsetZ = Math.cos(playerYaw) * checkDistance;

        for (double y = 0.6; y <= height.get() + 0.6; y += 0.1) {
            Box testBox = box.offset(offsetX, y, offsetZ);

            for (BlockPos pos : BlockPos.iterate(
                    (int) Math.floor(testBox.minX),
                    (int) Math.floor(testBox.minY),
                    (int) Math.floor(testBox.minZ),
                    (int) Math.floor(testBox.maxX),
                    (int) Math.floor(testBox.maxY),
                    (int) Math.floor(testBox.maxZ)
            )) {
                BlockState state = mc.world.getBlockState(pos);
                if (state.isAir()) continue;

                VoxelShape shape = state.getCollisionShape(mc.world, pos);
                if (shape.isEmpty()) continue;

                for (Box collisionBox : shape.getBoundingBoxes()) {
                    Box offsetBox = collisionBox.offset(pos);
                    float blockHeight = (float) (offsetBox.maxY - mc.player.getY());

                    if (blockHeight > 0.6f && blockHeight <= height.get()) {
                        maxY = Math.max(maxY, blockHeight);
                    }
                }
            }
        }

        return maxY;
    }

    private boolean isBlockAbove() {
        Box box = mc.player.getBoundingBox().offset(0, 1, 0);

        for (BlockPos pos : BlockPos.iterate(
                (int) Math.floor(box.minX),
                (int) Math.floor(box.minY),
                (int) Math.floor(box.minZ),
                (int) Math.floor(box.maxX),
                (int) Math.floor(box.maxY),
                (int) Math.floor(box.maxZ)
        )) {
            if (!mc.world.getBlockState(pos).isAir()) {
                return true;
            }
        }

        return false;
    }

    private boolean canFall(float distance) {
        Box box = mc.player.getBoundingBox();

        for (double y = 0.1; y <= distance; y += 0.1) {
            Box testBox = box.offset(0, -y, 0);

            for (BlockPos pos : BlockPos.iterate(
                    (int) Math.floor(testBox.minX),
                    (int) Math.floor(testBox.minY),
                    (int) Math.floor(testBox.minZ),
                    (int) Math.floor(testBox.maxX),
                    (int) Math.floor(testBox.maxY),
                    (int) Math.floor(testBox.maxZ)
            )) {
                if (!mc.world.getBlockState(pos).isAir()) {
                    return false;
                }
            }
        }

        return true;
    }
}
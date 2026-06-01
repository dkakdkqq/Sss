package fun.lumis.client.modules.impl.movement;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.BlockPos;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

public class HighJump extends Module {

    public static HighJump INSTANCE = new HighJump();

    private final ModeSetting mode = new ModeSetting("Режим", "Shulker", "Shulker", "Slime", "Boat");
    private final FloatSetting slimeMultiplier = new FloatSetting("Множитель", 2.0f, 1.1f, 5.0f, 0.1f);

    private boolean wasInBoat;
    private double lastVelY;
    private int cooldown;

    public HighJump() {
        super("HighJump", "Высокий прыжок от различных источников", ModuleCategory.MOVEMENT);
        addSettings(mode, slimeMultiplier);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        wasInBoat = false;
        lastVelY = 0;
        cooldown = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        wasInBoat = false;
        lastVelY = 0;
        cooldown = 0;
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        if (cooldown > 0) cooldown--;

        if (mode.is("Shulker")) {
            handleShulker();
        }

        if (mode.is("Slime")) {
            handleSlime();
        }

        if (mode.is("Boat")) {
            handleBoat();
        }
    }

    private void handleShulker() {
        if (!(mc.currentScreen instanceof ShulkerBoxScreen)) return;

        BlockPos playerPos = mc.player.getBlockPos();

        BlockPos[] checkPositions = {
                playerPos.down(),
                playerPos,
                playerPos.north(),
                playerPos.south(),
                playerPos.east(),
                playerPos.west()
        };

        boolean onShulker = false;
        for (BlockPos pos : checkPositions) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.getBlock() instanceof ShulkerBoxBlock) {
                onShulker = true;
                break;
            }
        }

        if (onShulker) {
            mc.player.setVelocity(mc.player.getVelocity().x, 2.0, mc.player.getVelocity().z);
            mc.player.closeHandledScreen();
        }
    }

    private void handleSlime() {
        double velY = mc.player.getVelocity().y;

        BlockPos below = mc.player.getBlockPos().down();
        BlockPos belowTwo = mc.player.getBlockPos().down(2);

        boolean onSlime = mc.world.getBlockState(below).isOf(Blocks.SLIME_BLOCK)
                || mc.world.getBlockState(belowTwo).isOf(Blocks.SLIME_BLOCK);

        if (lastVelY < -0.1 && velY > 0.1 && onSlime && cooldown == 0) {
            double boostedVel = velY * slimeMultiplier.get();
            mc.player.setVelocity(mc.player.getVelocity().x, boostedVel, mc.player.getVelocity().z);
            cooldown = 5;
        }

        lastVelY = velY;
    }

    private void handleBoat() {
        boolean inBoat = mc.player.getVehicle() instanceof BoatEntity;

        if (wasInBoat && !inBoat && cooldown == 0) {
            mc.player.setVelocity(mc.player.getVelocity().x, 1.5, mc.player.getVelocity().z);
            cooldown = 20;
        }

        wasInBoat = inBoat;
    }
}

package fun.lumis.client.modules.impl.player;

import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public class FastBreak extends Module {
    public static FastBreak INSTANCE = new FastBreak();

    private final FloatSetting speed = new FloatSetting("Ускорение", 0.5f, 0.3f, 1.0f, 0.1f);

    public FastBreak() {
        super("FastBreak", "Ускоряет ломание блоков", ModuleCategory.PLAYER);
        addSettings(speed);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) {
            return;
        }

        if (!mc.options.attackKey.isPressed()) {
            return;
        }

        accelerateClientBreak(mc.interactionManager, mc.player, mc.world, hit.getBlockPos(), hit.getSide(), speed.get(), true);
    }

    public float getSpeed() {
        return speed.get();
    }

    public static int getExtraTicks(float speed) {
        return Math.max(1, Math.round(Math.max(0.3f, speed) / 0.35f));
    }

    public static boolean accelerateClientBreak(ClientPlayerInteractionManager interactionManager,
                                                ClientPlayerEntity player,
                                                ClientWorld world,
                                                BlockPos pos,
                                                Direction side,
                                                float speed,
                                                boolean swing) {
        if (interactionManager == null || player == null || world == null || pos == null) {
            return false;
        }

        BlockState state = world.getBlockState(pos);
        if (state == null || state.isAir()) {
            return false;
        }

        Direction breakSide = side == null ? Direction.UP : side;
        int extraTicks = getExtraTicks(speed);
        for (int i = 0; i < extraTicks; i++) {
            interactionManager.updateBlockBreakingProgress(pos, breakSide);
        }

        if (swing) {
            player.swingHand(Hand.MAIN_HAND);
        }

        return true;
    }

    public static boolean packetBreak(net.minecraft.client.network.ClientPlayNetworkHandler handler,
                                      ClientPlayerEntity player,
                                      BlockPos pos,
                                      Direction side,
                                      boolean swing) {
        if (handler == null || player == null || pos == null) {
            return false;
        }

        Direction breakSide = side == null ? Direction.UP : side;
        handler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, breakSide));
        handler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, breakSide));

        if (swing) {
            handler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        return true;
    }
}

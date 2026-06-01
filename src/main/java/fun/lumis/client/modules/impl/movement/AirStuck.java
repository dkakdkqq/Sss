package fun.lumis.client.modules.impl.movement;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventMove;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.utils.network.NetworkUtils;
import fun.lumis.api.utils.player.InventoryUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

public class AirStuck extends Module {

    public static AirStuck INSTANCE = new AirStuck();

    private final ModeSetting mode = new ModeSetting("Мод", "Обычный", "Обычный", "LonyGrief");
    private final BooleanSetting cancelPackets = new BooleanSetting("Отменять пакеты", true);
    private final BooleanSetting swapElytra = new BooleanSetting("Свапать элитру", true);

    private Vec3d freezePosition = Vec3d.ZERO;
    private boolean frozen = false;

    public AirStuck() {
        super("AirStuck", "Зависает в воздухе", ModuleCategory.MOVEMENT);
        addSettings(mode, cancelPackets, swapElytra);
    }

    @Override
    public void onEnable() {
        frozen = false;

        if (mc.player != null && swapElytra.isState()) {
            swapChestEquipment();
        }

        if (mc.player != null && mode.is("Обычный")) {
            freezePosition = mc.player.getPos();
            frozen = true;
        }

        super.onEnable();
    }

    @Override
    public void onDisable() {
        frozen = false;
        super.onDisable();
    }

    private void swapChestEquipment() {
        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);

        if (!chestStack.isOf(Items.ELYTRA)) {
            return;
        }

        int chestplateSlot = InventoryUtils.findBestChestplateSlot();
        if (chestplateSlot != -1) {
            doSwap(chestplateSlot);
        }
    }

    private void doSwap(int slot) {
        if (slot >= 0 && slot < 9) {
            mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, 6, 0, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
        }

        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
    }

    @EventLink
    public void onMove(final EventMove e) {
        if (mc.player == null) return;

        if (mode.is("LonyGrief") && !frozen) {
            if (mc.player.fallDistance > 0.0F && mc.player.getVelocity().y < 0.0D) {
                freezePosition = mc.player.getPos();
                frozen = true;
            }
        }

        if (frozen) {
            e.setMovePos(Vec3d.ZERO);
            mc.player.setPosition(freezePosition.x, freezePosition.y, freezePosition.z);
            mc.player.setVelocity(0, 0, 0);
        }
    }

    @EventLink
    public void onPacket(final EventPacket e) {
        if (!frozen || e.getType() != EventPacket.Type.SEND) return;

        if (e.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (cancelPackets.isState()) {
                e.cancel();
            } else {
                e.cancel();
                NetworkUtils.sendSilentPacket(createFrozenPacket(packet));
            }
        }
    }

    private PlayerMoveC2SPacket createFrozenPacket(PlayerMoveC2SPacket packet) {
        boolean onGround = packet.isOnGround();
        boolean horizontalCollision = packet.horizontalCollision();

        if (packet.changesPosition() && packet.changesLook()) {
            return new PlayerMoveC2SPacket.Full(
                    freezePosition.x,
                    freezePosition.y,
                    freezePosition.z,
                    packet.getYaw(mc.player.getYaw()),
                    packet.getPitch(mc.player.getPitch()),
                    onGround,
                    horizontalCollision
            );
        }

        if (packet.changesPosition()) {
            return new PlayerMoveC2SPacket.PositionAndOnGround(
                    freezePosition.x,
                    freezePosition.y,
                    freezePosition.z,
                    onGround,
                    horizontalCollision
            );
        }

        if (packet.changesLook()) {
            return new PlayerMoveC2SPacket.LookAndOnGround(
                    packet.getYaw(mc.player.getYaw()),
                    packet.getPitch(mc.player.getPitch()),
                    onGround,
                    horizontalCollision
            );
        }

        return new PlayerMoveC2SPacket.OnGroundOnly(onGround, horizontalCollision);
    }
}

package fun.lumis.client.modules.impl.combat;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventBinding;
import fun.lumis.api.events.implement.EventGameUpdate;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.FreeLookStorage;
import fun.lumis.api.storages.implement.RotationStorage;
import fun.lumis.api.utils.rotate.Rotation;
import fun.lumis.api.utils.rotate.RotationUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BindSetting;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoTrap extends Module {

    public static AutoTrap INSTANCE = new AutoTrap();

    private final ModeSetting mode = new ModeSetting("Мод", "Obsidian", "Obsidian", "CobWeb");
    private final FloatSetting distance = new FloatSetting("Дистанция", 3f, 1f, 5f, 0.1f);
    private final BindSetting bind = new BindSetting("Бинд", -1);
    private final BooleanSetting fromInventory = new BooleanSetting("Из инвентаря", false);
    private final BooleanSetting rotation = new BooleanSetting("Ротация", true);

    private final ListSetting targets = new ListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Невидимые", true),
            new BooleanSetting("Себя", false)
    );

    private final BooleanSetting reverseRotate = new BooleanSetting("Реверс ротейт", true).visible(rotation::isState);

    private PlayerEntity target;
    private int oldSlot = -1;
    private int inventorySlot = -1;
    private boolean placing = false;
    private boolean use = false;
    private final List<BlockPos> blocksToPlace = new ArrayList<>();
    private int placeIndex = 0;
    private BlockPos currentBlock = null;
    private boolean waitingForRotation = false;
    private int rotationTicks = 0;
    private float restoreYaw;
    private float restorePitch;

    public AutoTrap() {
        super("AutoTrap", "Автоматически ставит ловушку вокруг игрока", ModuleCategory.COMBAT);
        addSettings(mode, distance, bind, fromInventory, rotation, reverseRotate, targets);
    }

    @EventLink
    public void onBinding(EventBinding event) {
        if (mc.currentScreen != null) return;

        if (event.getKey() == bind.getKey()) {
            this.use = true;
        }
    }

    @EventLink
    public void onGameUpdate(EventGameUpdate e) {
        if (mc.player == null || mc.world == null) return;
        if (!placing || currentBlock == null || !rotation.isState()) return;

        rotateToBlock(currentBlock);
    }

    
    @EventLink
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) {
            this.use = false;
            return;
        }

        if (use && !placing) {
            target = findTarget();
            if (target != null) {
                startPlacing();
            }
            use = false;
        }

        if (placing) {
            processPlacing();
        }
    }

    
    private void rotateToBlock(BlockPos pos) {
        Direction side = getPlaceSide(pos);
        if (side == null) return;

        BlockPos neighbor = pos.offset(side);
        Direction opposite = side.getOpposite();
        Vec3d hitVec = getHitVec(neighbor, opposite);

        Vec2f targetRot = RotationUtils.getRotations(hitVec);

        RotationStorage.update(
                new Rotation(targetRot.x, targetRot.y),
                360, 360,
                360, 360, 5,
                1, false
        );
    }

    private Vec3d getHitVec(BlockPos neighbor, Direction face) {
        Vec3d center = Vec3d.ofCenter(neighbor);
        return center.add(
                face.getOffsetX() * 0.5,
                face.getOffsetY() * 0.5,
                face.getOffsetZ() * 0.5
        );
    }

    private boolean isRotatedToBlock(BlockPos pos) {
        if (!rotation.isState()) return true;

        Direction side = getPlaceSide(pos);
        if (side == null) return false;

        BlockPos neighbor = pos.offset(side);
        Direction opposite = side.getOpposite();
        Vec3d hitVec = getHitVec(neighbor, opposite);

        Vec2f targetRot = RotationUtils.getRotations(hitVec);
        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetRot.x - mc.player.getYaw()));
        float pitchDiff = Math.abs(MathHelper.wrapDegrees(targetRot.y - mc.player.getPitch()));

        return yawDiff < 5 && pitchDiff < 5;
    }

    private void startPlacing() {
        blocksToPlace.clear();
        placeIndex = 0;
        waitingForRotation = false;
        rotationTicks = 0;

        BlockPos targetPos = target.getBlockPos();

        if (mode.is("Obsidian")) {
            blocksToPlace.add(targetPos.add(1, 0, 0));
            blocksToPlace.add(targetPos.add(-1, 0, 0));
            blocksToPlace.add(targetPos.add(0, 0, 1));
            blocksToPlace.add(targetPos.add(0, 0, -1));
            blocksToPlace.add(targetPos.add(1, 1, 0));
            blocksToPlace.add(targetPos.add(-1, 1, 0));
            blocksToPlace.add(targetPos.add(0, 1, 1));
            blocksToPlace.add(targetPos.add(0, 1, -1));
            blocksToPlace.add(targetPos.add(0, 2, 0));
            blocksToPlace.add(targetPos.add(1, 2, 0));
            blocksToPlace.add(targetPos.add(-1, 2, 0));
            blocksToPlace.add(targetPos.add(0, 2, 1));
            blocksToPlace.add(targetPos.add(0, 2, -1));
        } else {
            blocksToPlace.add(targetPos);
            blocksToPlace.add(targetPos.up());
        }

        if (fromInventory.isState()) {
            oldSlot = mc.player.getInventory().selectedSlot;
            int slot = findItemSlot();
            if (slot == -1) {
                placing = false;
                return;
            }

            if (slot < 9) {
                mc.player.getInventory().selectedSlot = slot;
                inventorySlot = -1;
            } else {
                inventorySlot = slot;
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, oldSlot, SlotActionType.SWAP, mc.player);
            }
        }

        placing = true;
    }

    private void processPlacing() {
        if (target != null && (!target.isAlive() || AntiBot.checkBot(target) || mc.player.distanceTo(target) > distance.getValue().floatValue())) {
            finishPlacing();
            return;
        }

        if (placeIndex >= blocksToPlace.size()) {
            finishPlacing();
            return;
        }

        BlockPos pos = blocksToPlace.get(placeIndex);
        currentBlock = pos;

        if (!mc.world.getBlockState(pos).isReplaceable()) {
            placeIndex++;
            waitingForRotation = false;
            rotationTicks = 0;
            return;
        }

        Direction side = getPlaceSide(pos);
        if (side == null) {
            placeIndex++;
            waitingForRotation = false;
            rotationTicks = 0;
            return;
        }

        if (rotation.isState()) {
            if (!waitingForRotation) {
                rotateToBlock(pos);
                waitingForRotation = true;
                rotationTicks = 0;
                return;
            }

            rotationTicks++;

            if (!isRotatedToBlock(pos) || rotationTicks < 2) {
                rotateToBlock(pos);
                return;
            }
        }

        placeBlock(pos);
        placeIndex++;
        waitingForRotation = false;
        rotationTicks = 0;
    }

    private void finishPlacing() {
        if (fromInventory.isState()) {
            if (inventorySlot != -1) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, inventorySlot, oldSlot, SlotActionType.SWAP, mc.player);
                inventorySlot = -1;
            } else if (oldSlot != -1) {
                mc.player.getInventory().selectedSlot = oldSlot;
            }
            oldSlot = -1;
        }
        placing = false;
        target = null;
        currentBlock = null;
        blocksToPlace.clear();
        placeIndex = 0;
        waitingForRotation = false;
        rotationTicks = 0;
    }

    private Direction getPlaceSide(BlockPos pos) {
        Direction[] priority = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction dir : priority) {
            BlockPos neighbor = pos.offset(dir);
            BlockState state = mc.world.getBlockState(neighbor);
            if (!state.isReplaceable() && !state.isLiquid() && state.isSolidBlock(mc.world, neighbor)) {
                return dir;
            }
        }
        for (Direction dir : priority) {
            BlockPos neighbor = pos.offset(dir);
            BlockState state = mc.world.getBlockState(neighbor);
            if (!state.isReplaceable() && !state.isLiquid()) {
                return dir;
            }
        }
        return null;
    }

    
    private void placeBlock(BlockPos pos) {
        Direction side = getPlaceSide(pos);
        if (side == null) return;

        BlockPos neighbor = pos.offset(side);
        Direction opposite = side.getOpposite();
        Vec3d hitVec = getHitVec(neighbor, opposite);

        BlockHitResult result = new BlockHitResult(hitVec, opposite, neighbor, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findItemSlot() {
        Item item = mode.is("Obsidian") ? Items.OBSIDIAN : Items.COBWEB;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private PlayerEntity findTarget() {
        if (targets.is("Себя")) {
            return mc.player;
        }

        List<PlayerEntity> playerTargets = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player) continue;
            if (!player.isAlive()) continue;
            if (AntiBot.checkBot(player)) continue;
            if (!targets.is("Игроки")) continue;
            if (player.hasStatusEffect(StatusEffects.INVISIBILITY) && !targets.is("Невидимые")) continue;
            if (Lumis.INSTANCE.friendStorage.isFriend(player.getName().getString())) continue;
            if (mc.player.distanceTo(player) > distance.getValue().floatValue()) continue;
            playerTargets.add(player);
        }
        if (playerTargets.isEmpty()) return null;
        playerTargets.sort(Comparator.comparingDouble(p -> mc.player.distanceTo(p)));
        return playerTargets.get(0);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (placing) {
            finishPlacing();
        }
        target = null;
        placing = false;
        use = false;
        currentBlock = null;
        blocksToPlace.clear();
        placeIndex = 0;
        oldSlot = -1;
        inventorySlot = -1;
        waitingForRotation = false;
        rotationTicks = 0;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        placing = false;
        use = false;
        currentBlock = null;
        blocksToPlace.clear();
        placeIndex = 0;
        oldSlot = -1;
        inventorySlot = -1;
        waitingForRotation = false;
        rotationTicks = 0;
    }
}

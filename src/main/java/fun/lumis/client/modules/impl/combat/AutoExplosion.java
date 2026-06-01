package fun.lumis.client.modules.impl.combat;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventBinding;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.RotationStorage;
import fun.lumis.api.utils.input.KeyBoardUtils;
import fun.lumis.api.utils.rotate.Rotation;
import fun.lumis.api.utils.rotate.RotationUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BindSetting;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

@Getter
@Setter
public final class AutoExplosion extends Module {

    public static AutoExplosion INSTANCE = new AutoExplosion();

    private final ModeSetting modeBaxa = new ModeSetting("Режим взрыва", "Авто", "Авто", "По бинду");
    private final BindSetting bind = new BindSetting("Бинд", -1)
            .visible(() -> modeBaxa.is("По бинду"));
    private final BooleanSetting explosionOnRightClick = new BooleanSetting("Взрыв по ПКМ", true);
    private final BooleanSetting keepCrystal = new BooleanSetting("Оставлять кристалл", false);

    private static final double INTERACT_RANGE = 4.5;

    private BlockPos targetPos;
    private int targetSlot = -1;
    private int oldSlot = -1;
    private boolean needSync;
    private Box crystalArea;
    private boolean blocked;
    private boolean internalInteract;

    public AutoExplosion() {
        super("AutoExplosion", "Автоматически взрывает кристалл", ModuleCategory.COMBAT);
        addSettings(modeBaxa, bind, explosionOnRightClick, keepCrystal);
    }

    @EventLink
    public void onBinding(final EventBinding event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (!modeBaxa.is("По бинду")) return;

        boolean pressed = bind.getKey() == -1
                ? event.getKey() == KeyBoardUtils.createMouseBind(2)
                : event.getKey() == bind.getKey();

        if (pressed) {
            placeObsidianByCrosshair();
        }
    }

    @EventLink
    public void onPacket(final EventPacket event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getType() != EventPacket.Type.SEND) return;
        if (internalInteract) return;

        if (event.getPacket() instanceof PlayerInteractBlockC2SPacket packet) {
            BlockHitResult hit = packet.getBlockHitResult();
            BlockPos clickedPos = hit.getBlockPos();
            BlockPos placePos = clickedPos.offset(hit.getSide());

            if (isHoldingObsidian() && isInRange(placePos) && !mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.END_CRYSTAL))) {
                int crystalSlot = findCrystalSlot();
                if (crystalSlot != -1) {
                    targetPos = placePos;
                    targetSlot = crystalSlot;
                    blocked = true;
                }
            }

            if (explosionOnRightClick.isState() && shouldPlaceByRightClick(clickedPos)) {
                if (placeCrystalFromOffhand(hit, clickedPos)) {
                    event.cancel();
                }
            }
        }
    }

    @EventLink
    public void onTick(final EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        if (needSync) {
            needSync = false;
            restoreSelectedSlot();
        }

        if (targetPos != null) {
            if (mc.world.getBlockState(targetPos).isAir()) {
                targetPos = null;
            } else if (blocked) {
                blocked = false;
            } else {
                tryPlaceCrystalFast(targetPos);
            }
        }

        processCrystalArea();
    }

    private void tryPlaceCrystalFast(BlockPos pos) {
        if (targetSlot < 0 || targetSlot > 8 || !canPlaceCrystal(pos)) {
            return;
        }

        rotateTo(Vec3d.ofCenter(pos));

        oldSlot = mc.player.getInventory().selectedSlot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(targetSlot));
        mc.player.getInventory().selectedSlot = targetSlot;

        Vec3d hitVec = Vec3d.ofCenter(pos).add(0.0, 0.5, 0.0);
        BlockHitResult result = new BlockHitResult(hitVec, Direction.UP, pos, false);
        sendInteract(Hand.MAIN_HAND, result);
        mc.player.swingHand(Hand.MAIN_HAND);

        needSync = true;
        crystalArea = boxFromBlock(pos.up()).expand(0.1);
        targetPos = null;
    }

    private void processCrystalArea() {
        if (crystalArea == null) return;

        for (Entity entity : mc.world.getOtherEntities(null, crystalArea)) {
            if (!(entity instanceof EndCrystalEntity crystal) || !crystal.isAlive()) continue;

            if (!crystal.getBoundingBox().contains(mc.player.getEyePos())) {
                rotateTo(crystal.getBoundingBox().getCenter());
            }
            attackCrystal(crystal);
            crystalArea = null;
            if (!keepCrystal.isState()) {
                restoreSelectedSlot();
            }
            return;
        }
    }

    private boolean shouldPlaceByRightClick(BlockPos clickedPos) {
        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.END_CRYSTAL))) return false;
        if (isHoldingBlockForPlace()) return false;

        Block block = mc.world.getBlockState(clickedPos).getBlock();
        if (block != Blocks.OBSIDIAN && block != Blocks.BEDROCK) return false;

        return mc.world.getBlockState(clickedPos.up()).isAir();
    }

    private boolean placeCrystalFromOffhand(BlockHitResult hit, BlockPos clickedPos) {
        int slot = findScreenSlot(Items.END_CRYSTAL);
        if (slot == -1 && mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL) return false;

        boolean swapped = false;
        if (mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL) {
            swapSlotToOffhand(slot);
            swapped = true;
        }

        sendInteract(Hand.OFF_HAND, hit);
        mc.player.swingHand(Hand.OFF_HAND);
        crystalArea = boxFromBlock(clickedPos.up()).expand(0.1);

        if (swapped) {
            swapSlotToOffhand(slot);
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
        }
        return true;
    }

    private void placeObsidianByCrosshair() {
        int obsidianSlot = findScreenSlot(Items.OBSIDIAN);
        int crystalSlot = findCrystalSlot();
        if (obsidianSlot == -1 || crystalSlot == -1) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        if (hit.getType() != HitResult.Type.BLOCK) return;
        if (mc.world.getBlockState(hit.getBlockPos()).isAir()) return;

        BlockPos placePos = hit.getBlockPos().offset(hit.getSide());
        targetPos = placePos;
        targetSlot = crystalSlot;
        blocked = true;

        swapSlotToOffhand(obsidianSlot);
        sendInteract(Hand.OFF_HAND, hit);
        mc.player.swingHand(Hand.OFF_HAND);
        swapSlotToOffhand(obsidianSlot);
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, false));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void sendInteract(Hand hand, BlockHitResult hitResult) {
        internalInteract = true;
        try {
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, hitResult, 0));
        } finally {
            internalInteract = false;
        }
    }

    private void rotateTo(Vec3d vec) {
        Vec2f rotation = RotationUtils.getRotations(vec);
        RotationStorage.update(new Rotation(rotation.x, rotation.y), 360, 360, 360, 360, 1, 2, false);
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        BlockPos up1 = pos.up();
        BlockPos up2 = pos.up(2);

        if (!mc.world.getBlockState(up1).isAir()) return false;
        if (!mc.world.getBlockState(up2).isAir()) return false;

        Box box = new Box(
                up1.getX(), up1.getY(), up1.getZ(),
                up1.getX() + 1.0, up1.getY() + 2.0, up1.getZ() + 1.0
        );

        for (Entity entity : mc.world.getOtherEntities(null, box)) {
            if (!(entity instanceof EndCrystalEntity)) {
                return false;
            }
        }
        return true;
    }

    private int findCrystalSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.END_CRYSTAL) {
                return i;
            }
        }
        return -1;
    }

    private int findScreenSlot(Item item) {
        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private void swapSlotToOffhand(int slot) {
        if (slot >= 36 && slot <= 44) {
            mc.interactionManager.clickSlot(0, 45, slot - 36, SlotActionType.SWAP, mc.player);
            return;
        }

        mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
        mc.interactionManager.clickSlot(0, 45, 0, SlotActionType.SWAP, mc.player);
        mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
    }

    private void restoreSelectedSlot() {
        if (oldSlot != -1) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
            mc.player.getInventory().selectedSlot = oldSlot;
            oldSlot = -1;
        }
    }

    private Box boxFromBlock(BlockPos pos) {
        return new Box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0
        );
    }

    private boolean isHoldingObsidian() {
        return mc.player.getMainHandStack().getItem() == Items.OBSIDIAN
                || mc.player.getOffHandStack().getItem() == Items.OBSIDIAN;
    }

    private boolean isHoldingBlockForPlace() {
        Item main = mc.player.getMainHandStack().getItem();
        Item off = mc.player.getOffHandStack().getItem();

        return main instanceof BlockItem && main != Items.PLAYER_HEAD
                || off instanceof BlockItem && off != Items.PLAYER_HEAD;
    }

    private boolean isInRange(BlockPos pos) {
        return mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) <= INTERACT_RANGE;
    }

    private void reset() {
        if (oldSlot != -1 && mc.player != null && mc.getNetworkHandler() != null) {
            restoreSelectedSlot();
        }
        targetPos = null;
        targetSlot = -1;
        needSync = false;
        crystalArea = null;
        blocked = false;
        internalInteract = false;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
    }
}

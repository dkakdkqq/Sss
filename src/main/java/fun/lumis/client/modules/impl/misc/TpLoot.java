package fun.lumis.client.modules.impl.misc;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.math.Vec3d;

import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.api.utils.math.TimerUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

public final class TpLoot extends Module {

    public static TpLoot INSTANCE = new TpLoot();

    private final FloatSetting range = new FloatSetting("Дистанция", 10.0F, 3.0F, 50.0F, 1.0F);
    private final FloatSetting lootDelay = new FloatSetting("Задержка лута", 500.0F, 100.0F, 5000.0F, 50.0F);
    private final ModeSetting afterLoot = new ModeSetting("После лута", "Возвращаться", "Возвращаться", "Тепаться на спавн");
    private final FloatSetting actionDelay = new FloatSetting("Задержка действия", 1000.0F, 200.0F, 10000.0F, 100.0F);

    private final TimerUtils lootTimer = new TimerUtils();
    private final TimerUtils actionTimer = new TimerUtils();
    private Vec3d originalPos = null;
    private boolean waitingAction = false;

    private static final List<Item> TARGET_ITEMS = List.of(
            Items.NETHERITE_SWORD,
            Items.NETHERITE_HELMET,
            Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS,
            Items.PLAYER_HEAD,
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.END_CRYSTAL,
            Items.TOTEM_OF_UNDYING,
            Items.ELYTRA
    );

    public TpLoot() {
        super("TPLoot", "Телепортирует к ресурсам", ModuleCategory.MISC);
        addSettings(range, lootDelay, afterLoot, actionDelay);
    }

    
    @EventLink
    @SuppressWarnings("unused")
    public void onTick(final EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        if (this.waitingAction) {
            if (this.actionTimer.finished((long) this.actionDelay.getValue().floatValue())) {
                if (afterLoot.is("Возвращаться") && this.originalPos != null) {
                    this.teleportTo(this.originalPos);
                    ChatUtils.sendMessage("TpLoot: возврат на исходную позицию");
                }
                if (afterLoot.is("Тепаться на спавн")) {
                    mc.player.networkHandler.sendChatCommand("spawn");
                    ChatUtils.sendMessage("TpLoot: выполнен /spawn");
                }
                this.waitingAction = false;
                this.originalPos = null;
                this.lootTimer.reset();
            }
            return;
        }

        if (!this.lootTimer.finished((long) this.lootDelay.getValue().floatValue())) return;

        ItemEntity targetItem = this.findTargetItem();
        if (targetItem == null) return;

        this.originalPos = mc.player.getPos();

        Vec3d itemPos = targetItem.getPos();
        this.teleportTo(itemPos);

        ItemStack stack = targetItem.getStack();
        ChatUtils.sendMessage("TpLoot: подобран " + stack.getName().getString());

        this.lootTimer.reset();
        this.waitingAction = true;
        this.actionTimer.reset();
    }

    private ItemEntity findTargetItem() {
        double maxRange = this.range.getValue().doubleValue();
        ItemEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            ItemStack stack = itemEntity.getStack();
            if (!this.isTargetItem(stack.getItem())) continue;

            double dist = mc.player.squaredDistanceTo(entity);
            if (dist > maxRange * maxRange) continue;

            if (dist < closestDist) {
                closestDist = dist;
                closest = itemEntity;
            }
        }

        return closest;
    }

    private boolean isTargetItem(Item item) {
        return TARGET_ITEMS.contains(item);
    }

    
    private void teleportTo(Vec3d pos) {
        int packets = (int) Math.ceil(mc.player.getPos().distanceTo(pos) / 10.0);
        packets = Math.max(packets, 3);

        for (int i = 0; i < packets; i++) {
            mc.player.networkHandler.sendPacket(new OnGroundOnly(mc.player.isOnGround(), mc.player.horizontalCollision));
        }

        mc.player.networkHandler.sendPacket(new PositionAndOnGround(pos.x, pos.y, pos.z, false, mc.player.horizontalCollision));
        mc.player.setPosition(pos.x, pos.y, pos.z);
    }

    public void onEnable() {
        this.originalPos = null;
        this.waitingAction = false;
        this.lootTimer.reset();
        this.actionTimer.reset();
        super.onEnable();
    }

    public void onDisable() {
        this.originalPos = null;
        this.waitingAction = false;
        super.onDisable();
    }
}

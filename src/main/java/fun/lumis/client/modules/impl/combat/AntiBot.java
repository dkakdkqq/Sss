package fun.lumis.client.modules.impl.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;

public class AntiBot extends Module {

    public static AntiBot INSTANCE = new AntiBot();

    public static final List<Entity> isBot = new ArrayList<>();

    public AntiBot() {
        super("AntiBot", "Определяет ботов на сервере", ModuleCategory.COMBAT);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        this.newMatrix();
    }

    public void newMatrix() {
        if (mc.world == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (mc.player != player
                    && player.getInventory().armor.get(0).getItem() != Items.AIR
                    && player.getInventory().armor.get(1).getItem() != Items.AIR
                    && player.getInventory().armor.get(2).getItem() != Items.AIR
                    && player.getInventory().armor.get(3).getItem() != Items.AIR
                    && player.getInventory().armor.get(0).isEnchantable()
                    && player.getInventory().armor.get(1).isEnchantable()
                    && player.getInventory().armor.get(2).isEnchantable()
                    && player.getInventory().armor.get(3).isEnchantable()
                    && player.getOffHandStack().getItem() == Items.AIR
                    && (player.getInventory().armor.get(0).getItem() == Items.LEATHER_BOOTS
                    || player.getInventory().armor.get(1).getItem() == Items.LEATHER_LEGGINGS
                    || player.getInventory().armor.get(2).getItem() == Items.LEATHER_CHESTPLATE
                    || player.getInventory().armor.get(3).getItem() == Items.LEATHER_HELMET
                    || player.getInventory().armor.get(0).getItem() == Items.IRON_BOOTS
                    || player.getInventory().armor.get(1).getItem() == Items.IRON_LEGGINGS
                    || player.getInventory().armor.get(2).getItem() == Items.IRON_CHESTPLATE
                    || player.getInventory().armor.get(3).getItem() == Items.IRON_HELMET)
                    && player.getMainHandStack().getItem() != Items.AIR
                    && !player.getInventory().armor.get(0).isDamaged()
                    && !player.getInventory().armor.get(1).isDamaged()
                    && !player.getInventory().armor.get(2).isDamaged()
                    && !player.getInventory().armor.get(3).isDamaged()
                    && player.getHungerManager().getFoodLevel() == 20) {
                if (!isBot.contains(player)) {
                    isBot.add(player);
                }
                return;
            }
            isBot.remove(player);
        }
    }

    public static boolean checkBot(LivingEntity entity) {
        return entity instanceof PlayerEntity && isBot.contains(entity);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        isBot.clear();
    }
}
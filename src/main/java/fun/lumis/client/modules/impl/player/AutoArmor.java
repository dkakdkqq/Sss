package fun.lumis.client.modules.impl.player;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public class AutoArmor extends Module {

    public static AutoArmor INSTANCE = new AutoArmor();

    private final FloatSetting delay = new FloatSetting("Задержка", 25.0F, 1.0F, 1000.0F, 1.0F);
    private long lastEquipTime = 0L;

    public AutoArmor() {
        super("AutoArmor", "Автоматически одевает броню", ModuleCategory.PLAYER);
        addSettings(delay);
    }

    @EventLink
    public void onEvent(final EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        if (isMoving()) return;

        long currentTime = System.currentTimeMillis();
        if ((float) (currentTime - lastEquipTime) < delay.get()) return;

        for (int i = 0; i < 4; ++i) {
            ItemStack currentArmor = mc.player.getInventory().getArmorStack(i);

            if (currentArmor.isEmpty()) {
                for (int j = 0; j < 36; ++j) {
                    ItemStack stack = mc.player.getInventory().getStack(j);

                    if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem armorItem) {
                        if (getArmorSlotIndex(armorItem) == i) {
                            int slotToEquip = j;

                            if (j < 9) {
                                slotToEquip = j + 36;
                            }

                            mc.interactionManager.clickSlot(0, slotToEquip, 0, SlotActionType.QUICK_MOVE, mc.player);
                            lastEquipTime = currentTime;
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean isMoving() {
        return mc.player.input.movementForward != 0.0F || mc.player.input.movementSideways != 0.0F;
    }

    private int getArmorSlotIndex(ArmorItem armor) {
        String itemName = armor.toString().toLowerCase();

        if (itemName.contains("helmet") || itemName.contains("skull")) {
            return 3;
        } else if (itemName.contains("chestplate") || itemName.contains("tunic")) {
            return 2;
        } else if (itemName.contains("leggings") || itemName.contains("pants")) {
            return 1;
        } else if (itemName.contains("boots") || itemName.contains("shoes")) {
            return 0;
        }

        return 0;
    }
}

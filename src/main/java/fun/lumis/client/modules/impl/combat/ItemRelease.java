package fun.lumis.client.modules.impl.combat;

import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.TridentItem;

import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;

public class ItemRelease extends Module {

    public static ItemRelease INSTANCE = new ItemRelease();

    private final ListSetting items = new ListSetting("Предметы",
            new BooleanSetting("Лук", true),
            new BooleanSetting("Трезубец", false),
            new BooleanSetting("Арбалет", true)
    );

    private final FloatSetting tickBow = new FloatSetting("Задержка выстрела", 2.5f, 2.0f, 5f, 0.05f)
            .visible(() -> items.is("Лук"));

    public ItemRelease() {
        super("ItemRelease", "Автоматически выпускает предмет когда он полностью натянут", ModuleCategory.COMBAT);
        addSettings(items, tickBow);
    }

    
    @EventLink
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;

        if (items.is("Лук")) {
            if (mc.player.getMainHandStack().getItem() instanceof BowItem && mc.player.isUsingItem() && mc.player.getItemUseTime() >= tickBow.getValue().floatValue()) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
        }

        if (items.is("Трезубец")) {
            if (mc.player.getMainHandStack().getItem() instanceof TridentItem && mc.player.isUsingItem() && mc.player.getItemUseTime() >= 10) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
        }

        if (items.is("Арбалет")) {
            if (mc.player.getMainHandStack().getItem() instanceof CrossbowItem && mc.player.isUsingItem() && mc.player.getItemUseTime() >= CrossbowItem.getPullTime(mc.player.getMainHandStack(), mc.player)) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
        }
    }
}

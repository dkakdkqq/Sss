package fun.lumis.client.modules.impl.player;

import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public class ItemScroller extends Module {

    public static ItemScroller INSTANCE = new ItemScroller();

    public final FloatSetting delay = new FloatSetting("Задержка", 50.0f, 0.0f, 200.0f, 1.0f);

    private long lastQuickMoveAt;

    public ItemScroller() {
        super("ItemScroller", "Убирает задержку перемещения предметов", ModuleCategory.PLAYER);
        addSettings(delay);
    }

    public boolean canQuickMove() {
        long now = System.currentTimeMillis();
        if (now - lastQuickMoveAt < (long) delay.get()) {
            return false;
        }

        lastQuickMoveAt = now;
        return true;
    }

    public void resetTimer() {
        lastQuickMoveAt = 0L;
    }

    @Override
    public void onDisable() {
        resetTimer();
        super.onDisable();
    }
}

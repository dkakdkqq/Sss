package fun.lumis.client.modules.impl.combat;

import net.minecraft.util.math.MathHelper;
import fun.lumis.Lumis;
import fun.lumis.client.modules.Module;

public class TpsSync extends Module {

    public static TpsSync INSTANCE = new TpsSync();

    public TpsSync() {
        super("TpsSync", "Синхронизация с TPS сервера", ModuleCategory.COMBAT);
    }

    public float getCurrentTPS() {
        if (Lumis.INSTANCE == null || Lumis.INSTANCE.tpsCalc == null) {
            return 20.0f;
        }
        float tps = Lumis.INSTANCE.tpsCalc.getTPS();
        return MathHelper.clamp(tps, 0.1f, 20.0f);
    }

    public long getAdjustedCooldown(long baseCooldown) {
        if (!isEnable()) {
            return baseCooldown;
        }

        float tps = getCurrentTPS();
        if (tps >= 20.0f) {
            return baseCooldown;
        }

        float multiplier = 20.0f / tps;
        float additionalFactor = 1.0f + (20.0f - tps) * 0.05f;
        long adjusted = (long) (baseCooldown * multiplier * additionalFactor);

        return Math.min(adjusted, 3000);
    }

    public boolean canAttack(long lastAttackTime, long baseCooldown, long currentTime) {
        if (!isEnable()) {
            return currentTime >= lastAttackTime + baseCooldown;
        }

        long adjustedCooldown = getAdjustedCooldown(baseCooldown);
        return currentTime >= lastAttackTime + adjustedCooldown;
    }
}
package fun.lumis.client.modules.impl.render;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;

public class SeeInvisibles extends Module {

    public static final float INVISIBLE_ALPHA = 0.7F;
    public static final int INVISIBLE_COLOR = (Math.round(INVISIBLE_ALPHA * 255.0F) << 24) | 0xFFFFFF;
    public static SeeInvisibles INSTANCE = new SeeInvisibles();

    public SeeInvisibles() {
        super("SeeInvisibles", "Показывает невидимых игроков", ModuleCategory.RENDER);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (shouldRenderInvisible(player)) {
                player.setInvisible(false);
            }
        }
    }

    public boolean shouldRenderInvisible(PlayerEntity player) {
        return isEnable()
                && mc.player != null
                && player != null
                && player != mc.player
                && (player.isInvisible() || player.hasStatusEffect(StatusEffects.INVISIBILITY));
    }
}

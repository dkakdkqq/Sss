package fun.lumis.client.modules.impl.misc;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;

public class DeathCoord extends Module {

    public static DeathCoord INSTANCE = new DeathCoord();

    private final BooleanSetting copyToClipboard = new BooleanSetting("Копировать в буфер", true);

    private BlockPos deathPos = null;
    private boolean isDead = false;

    public DeathCoord() {
        super("DeathCoord", "Показывает координаты смерти", ModuleCategory.MISC);
        addSettings(copyToClipboard);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        isDead = false;
        deathPos = null;
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.getHealth() <= 0 && !isDead) {
            isDead = true;
            deathPos = mc.player.getBlockPos();
            
            String coords = "X: " + deathPos.getX() + " Y: " + deathPos.getY() + " Z: " + deathPos.getZ();
            String dimension = getDimension();
            String message = "§cВы умерли! §f" + coords + " §7(" + dimension + ")";
            
            mc.player.sendMessage(Text.literal(message), false);
            
            if (copyToClipboard.isState()) {
                mc.keyboard.setClipboard(deathPos.getX() + " " + deathPos.getY() + " " + deathPos.getZ());
            }
        }

        if (mc.player.getHealth() > 0 && isDead) {
            isDead = false;
        }
    }

    private String getDimension() {
        if (mc.world == null) return "Unknown";
        
        String dimension = mc.world.getRegistryKey().getValue().toString();
        
        if (dimension.contains("overworld")) return "Overworld";
        if (dimension.contains("nether")) return "Nether";
        if (dimension.contains("end")) return "End";
        
        return dimension;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        isDead = false;
        deathPos = null;
    }
}
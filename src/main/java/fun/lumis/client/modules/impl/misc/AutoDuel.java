package fun.lumis.client.modules.impl.misc;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;

import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.math.TimerUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AutoDuel extends Module {

    public static AutoDuel INSTANCE = new AutoDuel();

    public ModeSetting mode = new ModeSetting("Режим", "Шары",
            "Щит", "Шипы", "Лук", "Тотемы", "Нодебафф", "Шары", "Классик", "Читер", "Незер");

    private static final Pattern NAME_PATTERN = Pattern.compile("^\\w{3,16}$");

    private final List<String> sent = new ArrayList<>();
    private final TimerUtils duelT = new TimerUtils();
    private final TimerUtils clrT = new TimerUtils();
    private final TimerUtils pickT = new TimerUtils();
    private final TimerUtils setT = new TimerUtils();

    private Vec3d lastPos;
    private boolean inDuel;

    public AutoDuel() {
        super("AutoDuel", "Автоматически кидает дуель", ModuleCategory.MISC);
        addSettings(mode);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        sent.clear();
        inDuel = false;
        if (mc.player != null) {
            lastPos = mc.player.getPos();
        }
        duelT.reset();
        clrT.reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        sent.clear();
        inDuel = false;
    }

    
    @EventLink
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null || inDuel) return;

        if (lastPos != null && mc.player.getPos().distanceTo(lastPos) > 500) {
            toggle();
            return;
        }
        lastPos = mc.player.getPos();

        if (clrT.getElapsedTime() >= 30000) {
            sent.clear();
            clrT.reset();
        }

        if (duelT.getElapsedTime() >= 1000) {
            sendDuel();
            duelT.reset();
        }

        handleGui();
    }

    @EventLink
    public void onPacket(EventPacket e) {
        if (mc.player == null || mc.world == null) return;

        if (e.getType() == EventPacket.Type.RECEIVE && e.getPacket() instanceof GameMessageS2CPacket p) {
            String msg = p.content().getString().toLowerCase();
            if ((msg.contains("начало") && msg.contains("через") && msg.contains("секунд")) ||
                    msg.contains("поединок начался") ||
                    msg.contains("во время поединка")) {
                inDuel = true;
                toggle();
            }
        }
    }

    
    private void sendDuel() {
        for (String p : getPlayers()) {
            if (!sent.contains(p) && !p.equals(mc.player.getName().getString())) {
                mc.getNetworkHandler().sendChatCommand("duel " + p);
                sent.add(p);
                break;
            }
        }
    }

    private void handleGui() {
        if (!(mc.currentScreen instanceof GenericContainerScreen s)) return;
        int id = s.getScreenHandler().syncId;
        String t = s.getTitle().getString();

        if (t.contains("Выбор набора") && pickT.getElapsedTime() >= 150) {
            mc.interactionManager.clickSlot(id, getModeSlot(), 0, SlotActionType.QUICK_MOVE, mc.player);
            pickT.reset();
        } else if (t.contains("Настройка поединка") && setT.getElapsedTime() >= 150) {
            mc.interactionManager.clickSlot(id, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
            setT.reset();
        }
    }

    private int getModeSlot() {
        if (mode.is("Щит")) return 0;
        if (mode.is("Шипы")) return 1;
        if (mode.is("Лук")) return 2;
        if (mode.is("Тотемы")) return 3;
        if (mode.is("Нодебафф")) return 4;
        if (mode.is("Шары")) return 5;
        if (mode.is("Классик")) return 6;
        if (mode.is("Читер")) return 7;
        if (mode.is("Незер")) return 8;
        return 5;
    }

    private List<String> getPlayers() {
        List<String> list = new ArrayList<>();
        if (mc.getNetworkHandler() == null) return list;
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().getName();
            if (NAME_PATTERN.matcher(n).matches()) {
                list.add(n);
            }
        }
        return list;
    }
}

package fun.lumis.client.modules.impl.misc;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AutoLeave extends Module {

    public static final AutoLeave INSTANCE = new AutoLeave();

    private static final Set<String> STAFF_PREFIXES = new HashSet<>(Arrays.asList(
            "supp", "mod", "der", "adm", "wne", "curat", "dev", "yt",
            "мод", "помо", "адм", "владе", "курато", "сапп", "ютуб", "стажер", "сотрудник"
    ));

    private final FloatSetting leaveDistance = new FloatSetting("Дистанция срабатывания", 5.0f, 3.0f, 50.0f, 1.0f);
    private final ListSetting leaveIfSeen = new ListSetting("Выходить если замечен",
            new BooleanSetting("Игрок", true),
            new BooleanSetting("Модератор", false)
    );
    private final ModeSetting leaveType = new ModeSetting("Тип выхода", "В мейн меню", "В мейн меню", "/hub", "/home", "/spawn");
    private final BooleanSetting stopBaritone = new BooleanSetting("Выключать баритон", false);
    private final BooleanSetting leaveDisable = new BooleanSetting("Выключать после выхода", true);

    private int cooldownTicks;

    public AutoLeave() {
        super("AutoLeave", "Выходит с сервера, когда замечает поблизости игрока", ModuleCategory.MISC);
        addSettings(leaveDistance, leaveIfSeen, leaveType, stopBaritone, leaveDisable);
    }

    @Override
    public void onEnable() {
        cooldownTicks = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        cooldownTicks = 0;
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        float maxDistance = leaveDistance.get();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || player == mc.player) {
                continue;
            }

            if (mc.player.distanceTo(player) <= maxDistance && shouldLeaveFor(player)) {
                triggerLeave();
                break;
            }
        }
    }

    private boolean shouldLeaveFor(PlayerEntity player) {
        if (isModerator(player)) {
            return leaveIfSeen.is("Модератор");
        }
        return leaveIfSeen.is("Игрок");
    }

    private boolean isModerator(PlayerEntity player) {
        if (player == null) {
            return false;
        }

        String name = player.getName().getString();
        if (Lumis.INSTANCE != null && Lumis.INSTANCE.staffStorage != null && Lumis.INSTANCE.staffStorage.isStaff(name)) {
            return true;
        }

        Team team = player.getScoreboardTeam();
        if (team == null) {
            return false;
        }

        String prefix = team.getPrefix().getString().toLowerCase(Locale.ROOT);
        for (String candidate : STAFF_PREFIXES) {
            if (prefix.contains(candidate)) {
                return true;
            }
        }

        return false;
    }

    private void triggerLeave() {
        tryStopBaritone();

        switch (leaveType.getCurrent()) {
            case "В мейн меню" -> disconnectLeave();
            case "/hub" -> commandLeave("hub");
            case "/home" -> commandLeave("home home");
            case "/spawn" -> commandLeave("spawn");
        }
    }

    private void tryStopBaritone() {
        if (!stopBaritone.isState() || mc.getNetworkHandler() == null) {
            return;
        }
        mc.getNetworkHandler().sendChatMessage("#stop");
    }

    private void disconnectLeave() {
        if (mc.getNetworkHandler() == null) {
            ChatUtils.sendMessage("Модуль не работает в одиночном мире");
            return;
        }

        mc.getNetworkHandler().getConnection().disconnect(Text.literal("AutoLeave"));
        if (leaveDisable.isState()) {
            toggle();
        }
    }

    private void commandLeave(String command) {
        if (mc.getNetworkHandler() == null) {
            ChatUtils.sendMessage("AutoLeave нельзя использовать в одиночной игре!");
            return;
        }

        mc.getNetworkHandler().sendChatCommand(command);
        cooldownTicks = leaveDisable.isState() ? 10 : 30;

        if (leaveDisable.isState()) {
            toggle();
        }
    }
}

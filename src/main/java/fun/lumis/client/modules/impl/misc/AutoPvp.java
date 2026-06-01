package fun.lumis.client.modules.impl.misc;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;
import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.math.TimerUtils;
import fun.lumis.api.utils.render.font.ReplaceSymbols;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoPvp extends Module {

    public static AutoPvp INSTANCE = new AutoPvp();

    private static final Pattern SEARCH_PATTERN = Pattern.compile("Игрок\\s+(\\S+)\\s+ищет себе соперника!", Pattern.CASE_INSENSITIVE);
    private static final long OPEN_DELAY_MS = 250L;
    private static final long RETRY_DELAY_MS = 1000L;
    private static final int SEARCH_MENU_SLOT = 20;

    private final ListSetting donateSettings = new ListSetting("С кем идти...",
            new BooleanSetting("CUSTOM", false),
            new BooleanSetting("D.HELPER", false),
            new BooleanSetting("FROSTINE", false),
            new BooleanSetting("SPRING", false),
            new BooleanSetting("AUTUMN", false),
            new BooleanSetting("GLADIATOR", false),
            new BooleanSetting("PALADIN", false),
            new BooleanSetting("LUXE", false),
            new BooleanSetting("STAFF", false),
            new BooleanSetting("SUPPORT", false),
            new BooleanSetting("ETERNITY", false),
            new BooleanSetting("OVERLORD", false),
            new BooleanSetting("D.ADMIN", false),
            new BooleanSetting("POVELITEL", false),
            new BooleanSetting("IMPERATOR", false),
            new BooleanSetting("LEGENDA", false),
            new BooleanSetting("PRAVITEL", false),
            new BooleanSetting("PHOENIX", false),
            new BooleanSetting("PLAYER", false)
    );

    private final TimerUtils actionTimer = new TimerUtils();
    private boolean queuedJoin;
    private String queuedNickname;

    public AutoPvp() {
        super("AutoPvP", "Помощник в подборе пвп для сервера LonyGrief", ModuleCategory.MISC);
        addSettings(donateSettings);
    }

    @Override
    public void onEnable() {
        queuedJoin = false;
        queuedNickname = null;
        actionTimer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        queuedJoin = false;
        queuedNickname = null;
        super.onDisable();
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (mc.player == null || mc.world == null || event.getType() != EventPacket.Type.RECEIVE) {
            return;
        }

        if (!(event.getPacket() instanceof GameMessageS2CPacket packet)) {
            return;
        }

        String raw = packet.content().getString();
        if (raw == null) {
            return;
        }

        String plain = Formatting.strip(raw);
        if (plain == null) {
            plain = raw;
        }

        Matcher matcher = SEARCH_PATTERN.matcher(plain);
        if (!matcher.find()) {
            return;
        }

        String nickname = matcher.group(1);
        if (nickname == null || nickname.isBlank()) {
            return;
        }

        if (Lumis.INSTANCE != null
                && Lumis.INSTANCE.friendStorage != null
                && Lumis.INSTANCE.friendStorage.isFriend(nickname)) {
            return;
        }

        DonateRank rank = resolveDonateRank(nickname);
        if (!isAllowed(rank)) {
            return;
        }

        queuedJoin = true;
        queuedNickname = nickname;
        actionTimer.setMillis(0L);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!queuedJoin || mc.player == null || mc.world == null || mc.getNetworkHandler() == null || mc.interactionManager == null) {
            return;
        }

        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            String title = screen.getTitle().getString().toLowerCase();
            if (title.contains("поиск поединка") && actionTimer.finished(OPEN_DELAY_MS)) {
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, SEARCH_MENU_SLOT, 0, SlotActionType.PICKUP, mc.player);
                queuedJoin = false;
                queuedNickname = null;
                actionTimer.reset();
            }
            return;
        }

        if (mc.currentScreen != null) {
            return;
        }

        if (actionTimer.finished(RETRY_DELAY_MS)) {
            mc.getNetworkHandler().sendChatCommand("pvp");
            actionTimer.reset();
        }
    }

    private DonateRank resolveDonateRank(String nickname) {
        if (mc.getNetworkHandler() == null) {
            return null;
        }

        for (PlayerListEntry player : mc.getNetworkHandler().getPlayerList()) {
            String displayName = player.getDisplayName() != null
                    ? player.getDisplayName().getString()
                    : player.getProfile().getName();

            String cleanDisplayName = Formatting.strip(displayName);
            if (cleanDisplayName == null) {
                cleanDisplayName = displayName;
            }

            int nameIndex = indexOfIgnoreCase(cleanDisplayName, nickname);
            if (nameIndex < 0) {
                continue;
            }

            String donatePrefix = cleanDisplayName.substring(0, nameIndex).trim();
            if (donatePrefix.isEmpty()) {
                return null;
            }

            String cleanDonate = decodeDonatePrefix(donatePrefix).replace("+", "");
            return DonateRank.fromString(cleanDonate);
        }

        return null;
    }

    private boolean isAllowed(DonateRank rank) {
        if (rank == null) {
            return donateSettings.is("CUSTOM");
        }
        return donateSettings.is(rank.getName());
    }

    private String safeStrip(String text) {
        String stripped = Formatting.strip(text);
        return stripped == null ? text : stripped;
    }

    private int indexOfIgnoreCase(String text, String target) {
        return text.toLowerCase().indexOf(target.toLowerCase());
    }

    private String decodeDonatePrefix(String prefix) {
        StringBuilder decoded = new StringBuilder(prefix.length());
        for (int offset = 0; offset < prefix.length(); ) {
            int codePoint = prefix.codePointAt(offset);
            offset += Character.charCount(codePoint);

            String replacement = ReplaceSymbols.replaceCodePoint(codePoint);
            if (replacement != null) {
                decoded.append(replacement);
            } else {
                decoded.appendCodePoint(codePoint);
            }
        }

        return convertStyledToNormal(decoded.toString()).trim();
    }

    private String convertStyledToNormal(String styledText) {
        String styled = "бґЂК™бґ„бґ…бґ‡књ°ЙўКњЙЄбґЉбґ‹КџбґЌЙґбґЏбґКЂкњ±бґ›бґњбґ бґЎКЏбґўЙґ";
        String normal = "ABCDEFGHIJKLMNOPRSTUVWYZN";

        StringBuilder result = new StringBuilder();
        for (char c : styledText.toCharArray()) {
            int index = styled.indexOf(c);
            if (index != -1) {
                result.append(normal.charAt(index));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public enum DonateRank {
        CUSTOM("CUSTOM"),
        D_HELPER("D.HELPER"),
        FROSTINE("FROSTINE"),
        SPRING("SPRING"),
        AUTUMN("AUTUMN"),
        GLADIATOR("GLADIATOR"),
        PALADIN("PALADIN"),
        LUXE("LUXE"),
        STAFF("STAFF"),
        SUPPORT("SUPPORT"),
        ETERNITY("ETERNITY"),
        OVERLORD("OVERLORD"),
        D_ADMIN("D.ADMIN"),
        POVELITEL("POVELITEL"),
        IMPERATOR("IMPERATOR"),
        LEGENDA("LEGENDA"),
        PRAVITEL("PRAVITEL"),
        PHOENIX("PHOENIX"),
        PLAYER("PLAYER");

        private final String name;

        DonateRank(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static DonateRank fromString(String text) {
            for (DonateRank rank : values()) {
                if (rank.name.equalsIgnoreCase(text)) {
                    return rank;
                }
            }
            return null;
        }
    }
}

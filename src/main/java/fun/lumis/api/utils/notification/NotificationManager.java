package fun.lumis.api.utils.notification;

import fun.lumis.api.utils.client.ClientSoundPlayer;
import fun.lumis.client.modules.impl.misc.ClientSounds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager {
    public static final long DURATION_MS = 2500;
    private static final long MODULE_SOUND_STARTUP_MUTE_MS = 4000L;
    private static final long INIT_TIME_MS = System.currentTimeMillis();
    private static final List<Entry> entries = new ArrayList<>();

    public static void push(String moduleName, String categoryIcon, boolean enabled) {
        if (moduleName == null || moduleName.isEmpty()) return;
        entries.add(new Entry(moduleName, categoryIcon, enabled, null, System.currentTimeMillis()));
        playModuleSound(enabled);
    }

    public static void pushCustom(String text, String categoryIcon) {
        if (text == null || text.isEmpty()) return;
        entries.add(new Entry(text, categoryIcon, false, text, System.currentTimeMillis()));
    }

    public static List<Entry> getActive() {
        long now = System.currentTimeMillis();
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (now - e.startTime > DURATION_MS) {
                it.remove();
            }
        }
        return entries;
    }

    public static class Entry {
        public final String moduleName;
        public final String categoryIcon;
        public final boolean enabled;
        public final String customText;
        public final long startTime;

        public Entry(String moduleName, String categoryIcon, boolean enabled, String customText, long startTime) {
            this.moduleName = moduleName;
            this.categoryIcon = categoryIcon;
            this.enabled = enabled;
            this.customText = customText;
            this.startTime = startTime;
        }

        public boolean isCustom() {
            return customText != null && !customText.isEmpty();
        }
    }

    private static void playModuleSound(boolean enabled) {
        if (System.currentTimeMillis() - INIT_TIME_MS < MODULE_SOUND_STARTUP_MUTE_MS) {
            return;
        }

        ClientSounds clientSounds = ClientSounds.INSTANCE;
        if (clientSounds == null || !clientSounds.isEnable()) {
            return;
        }

        String soundName = clientSounds.stateSounds.getCurrent();
        if ("Нет".equals(soundName)) {
            return;
        }

        float pitch = enabled ? 1.0f : 0.95f;
        ClientSoundPlayer.playSound(soundName + ".wav", clientSounds.volume.get() / clientSounds.volume.getMax(), pitch);
    }
}

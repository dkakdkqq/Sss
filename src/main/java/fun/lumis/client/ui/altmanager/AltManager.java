package fun.lumis.client.ui.altmanager;

import fun.lumis.Lumis;
import fun.lumis.mixin.IMinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Хранилище и менеджер альтернативных аккаунтов (ников).
 *
 * <ul>
 *   <li>Список ников сохраняется на диск ({@code C:\\LumisClient\\lumis\\alts.txt}).</li>
 *   <li>Выбор ника переключает игровую сессию на офлайн-аккаунт с этим ником.</li>
 * </ul>
 */
public final class AltManager {

    private AltManager() {
    }

    /** Допустимый ник Minecraft: буквы/цифры/подчёркивание, длина 1..16. */
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    private static final List<String> ALTS = new ArrayList<>();
    private static boolean loaded = false;

    private static File file() {
        File dir = Lumis.INSTANCE != null && Lumis.INSTANCE.globalsDir != null
                ? Lumis.INSTANCE.globalsDir
                : new File(".");
        return new File(dir, "alts.txt");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        load();
    }

    /** Возвращает копию списка ников (для безопасной итерации в рендере). */
    public static synchronized List<String> getAlts() {
        ensureLoaded();
        return new ArrayList<>(ALTS);
    }

    public static synchronized int size() {
        ensureLoaded();
        return ALTS.size();
    }

    public static boolean isValid(String name) {
        return name != null && VALID_NAME.matcher(name.trim()).matches();
    }

    /**
     * Добавляет ник, если он валиден и ещё не существует (без учёта регистра).
     *
     * @return true если ник добавлен
     */
    public static synchronized boolean add(String name) {
        ensureLoaded();
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (!isValid(trimmed)) {
            return false;
        }
        for (String existing : ALTS) {
            if (existing.equalsIgnoreCase(trimmed)) {
                return false;
            }
        }
        ALTS.add(trimmed);
        save();
        return true;
    }

    public static synchronized void remove(String name) {
        ensureLoaded();
        if (name == null) {
            return;
        }
        ALTS.removeIf(existing -> existing.equalsIgnoreCase(name));
        save();
    }

    /** Текущий ник активной сессии. */
    public static String currentName() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getSession() != null) {
                return mc.getSession().getUsername();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Переключает игровую сессию на офлайн-аккаунт с указанным ником.
     *
     * @return true при успешном переключении
     */
    public static boolean apply(String name) {
        if (!isValid(name)) {
            return false;
        }
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Session session = createOfflineSession(name.trim());
            ((IMinecraftClientAccessor) (Object) mc).setSession(session);
            return true;
        } catch (Exception e) {
            System.err.println("[AltManager] Failed to switch session: " + e.getMessage());
            return false;
        }
    }

    private static Session createOfflineSession(String name) throws Exception {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        Constructor<Session> constructor = Session.class.getDeclaredConstructor(
                String.class,
                UUID.class,
                String.class,
                Optional.class,
                Optional.class,
                Session.AccountType.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                name,
                uuid,
                "0",
                Optional.empty(),
                Optional.empty(),
                Session.AccountType.LEGACY
        );
    }

    private static void load() {
        ALTS.clear();
        try {
            File f = file();
            if (!f.exists()) {
                return;
            }
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (isValid(trimmed)) {
                    unique.add(trimmed);
                }
            }
            ALTS.addAll(unique);
        } catch (Exception e) {
            System.err.println("[AltManager] Failed to load alts: " + e.getMessage());
        }
    }

    private static void save() {
        try {
            File f = file();
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(f.toPath(), ALTS, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[AltManager] Failed to save alts: " + e.getMessage());
        }
    }
}

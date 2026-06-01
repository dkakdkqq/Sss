package fun.lumis.client.ui.altmanager;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Генератор случайных никнеймов для кнопки "ChatGPT" в Alt Manager.
 *
 * <p>Сетевой доступ к реальному OpenAI API в окружении игры недоступен (нужны ключ и интернет),
 * поэтому генерация выполняется локально, но даёт абсолютно случайные никнеймы каждый раз.
 * Результат всегда валиден для Minecraft: символы [A-Za-z0-9_], длина 1..16.</p>
 */
public final class RandomNickGenerator {

    private RandomNickGenerator() {
    }

    private static final String[] PREFIX = {
            "Dark", "Shadow", "Ghost", "Toxic", "Cyber", "Frost", "Blaze", "Night", "Storm", "Iron",
            "Void", "Neo", "Pixel", "Rapid", "Silent", "Crazy", "Mega", "Ultra", "Pro", "Lethal",
            "Mystic", "Phantom", "Savage", "Epic", "Hyper", "Lunar", "Solar", "Venom", "Rogue", "Swift",
            "Arctic", "Crimson", "Golden", "Royal", "Atomic", "Turbo", "Quantum", "Nano", "Omega", "Alpha"
    };

    private static final String[] CORE = {
            "Wolf", "Dragon", "Sniper", "Gamer", "Slayer", "Knight", "Reaper", "Hunter", "Demon", "Tiger",
            "Falcon", "Viper", "Raven", "Bear", "Shark", "Fox", "Hawk", "Lion", "Cobra", "Panda",
            "Wizard", "Ninja", "Samurai", "Pilot", "Rider", "Mage", "Beast", "Bot", "King", "Lord",
            "Eagle", "Ghoul", "Titan", "Spectre", "Comet", "Blade", "Frost", "Storm", "Pulse", "Byte"
    };

    private static final String[] SUFFIX = {
            "X", "HD", "YT", "TV", "MC", "Pro", "Z", "Q", "XD", "GG", "FX", "OG"
    };

    private static final char[] VALID = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_".toCharArray();

    /**
     * Возвращает случайный валидный никнейм (1..16 символов из набора [A-Za-z0-9_]).
     */
    public static String generate() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int style = r.nextInt(6);

        StringBuilder sb = new StringBuilder();

        switch (style) {
            case 0 -> sb.append(pick(PREFIX, r)).append(pick(CORE, r));
            case 1 -> sb.append(pick(PREFIX, r)).append(pick(CORE, r)).append(r.nextInt(10, 100));
            case 2 -> sb.append(pick(CORE, r)).append(r.nextInt(100, 10000));
            case 3 -> sb.append(pick(PREFIX, r)).append('_').append(pick(CORE, r));
            case 4 -> sb.append(pick(PREFIX, r)).append(pick(CORE, r)).append(pick(SUFFIX, r));
            default -> sb.append(pick(CORE, r)).append(pick(SUFFIX, r)).append(r.nextInt(0, 1000));
        }

        // С небольшой вероятностью добавляем leet-замены для большей случайности.
        if (r.nextInt(100) < 35) {
            applyLeet(sb, r);
        }

        // С небольшой вероятностью полностью случайная "мусорная" строка.
        if (r.nextInt(100) < 10) {
            return randomGarbage(r);
        }

        return sanitize(sb.toString(), r);
    }

    private static String pick(String[] array, ThreadLocalRandom r) {
        return array[r.nextInt(array.length)];
    }

    private static void applyLeet(StringBuilder sb, ThreadLocalRandom r) {
        for (int i = 0; i < sb.length(); i++) {
            char c = Character.toLowerCase(sb.charAt(i));
            if (r.nextInt(100) >= 45) {
                continue;
            }
            char replacement = switch (c) {
                case 'a' -> '4';
                case 'e' -> '3';
                case 'i' -> '1';
                case 'o' -> '0';
                case 's' -> '5';
                case 't' -> '7';
                default -> sb.charAt(i);
            };
            sb.setCharAt(i, replacement);
        }
    }

    private static String randomGarbage(ThreadLocalRandom r) {
        int length = r.nextInt(6, 13);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(VALID[r.nextInt(VALID.length)]);
        }
        return sanitize(sb.toString(), r);
    }

    private static String sanitize(String raw, ThreadLocalRandom r) {
        StringBuilder clean = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length() && clean.length() < 16; i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                clean.append(c);
            }
        }
        if (clean.length() == 0) {
            clean.append("Player").append(r.nextInt(1000, 10000));
        }
        return clean.length() > 16 ? clean.substring(0, 16) : clean.toString();
    }
}

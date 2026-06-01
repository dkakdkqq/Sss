package fun.lumis.api.utils.render.font;

import fun.lumis.api.utils.color.ColorUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ReplaceSymbols {
    private static final Map<Integer, String> REPLACEMENTS = new HashMap<>();
    private static final Map<Integer, Integer> RANK_COLORS = new HashMap<>();

    private static final int[] RANKS = {
            (int) 'ꔀ', (int) 'ꔄ', (int) 'ꔈ', (int) 'ꔒ', (int) 'ꔖ', (int) 'ꔠ', (int) 'ꔤ', (int) 'ꔨ',
            (int) 'ꕠ', (int) 'ꔲ', (int) 'ꔶ', (int) 'ꕄ', (int) 'ꕖ', (int) 'ꕈ', (int) 'ꕀ', (int) 'ꕒ',
            (int) 'ꔉ', (int) 'ꔓ', (int) 'ꔗ', (int) 'ꔡ', (int) 'ꔥ', (int) 'ꔩ', (int) 'ꔳ', (int) 'ꔷ',
            (int) 'ꔁ', (int) 'ꔅ', (int) 'ꕉ', (int) 'ူ', (int) 'ဪ', (int) 'ဤ', (int) 'ဦ', (int) 'ာ',
            (int) 'ါ', (int) '့', (int) 'ဢ', (int) 'ဴ', (int) 'ိ', (int) 'း', (int) 'ဥ', (int) 'ဣ', (int) 'ဵ',
            (int) 'ံ', (int) 'ဩ', (int) 'အ', (int) 'ေ', (int) 'ု', (int) 'ဲ', (int) 'ဧ', (int) '္', (int) 'သ'
    };

    static {
        REPLACEMENTS.put((int) '⚡', "");
        REPLACEMENTS.put((int) '★', "");

        REPLACEMENTS.put((int) 'ꔀ', "PLAYER");
        REPLACEMENTS.put((int) 'ꔄ', "HERO");
        REPLACEMENTS.put((int) 'ꔈ', "TITAN");
        REPLACEMENTS.put((int) 'ꔒ', "AVENGER");
        REPLACEMENTS.put((int) 'ꔖ', "OVERLORD");
        REPLACEMENTS.put((int) 'ꔠ', "MAGISTER");
        REPLACEMENTS.put((int) 'ꔤ', "IMPERATOR");
        REPLACEMENTS.put((int) 'ꔨ', "DRAGON");
        REPLACEMENTS.put((int) 'ꕠ', "D.HELPER");
        REPLACEMENTS.put((int) 'ꔲ', "BULL");
        REPLACEMENTS.put((int) 'ꔶ', "TIGER");
        REPLACEMENTS.put((int) 'ꕄ', "VAMPIRE");
        REPLACEMENTS.put((int) 'ꕖ', "BUNNY");
        REPLACEMENTS.put((int) 'ꕈ', "COBRA");
        REPLACEMENTS.put((int) 'ꕀ', "HYDRA");
        REPLACEMENTS.put((int) 'ꕒ', "RABBIT");
        REPLACEMENTS.put((int) 'ꔉ', "HELPER");
        REPLACEMENTS.put((int) 'ꔓ', "ML.MODER");
        REPLACEMENTS.put((int) 'ꔗ', "MODER");
        REPLACEMENTS.put((int) 'ꔡ', "MODER+");
        REPLACEMENTS.put((int) 'ꔥ', "ST.MODER");
        REPLACEMENTS.put((int) 'ꔩ', "GL.MODER");
        REPLACEMENTS.put((int) 'ꔳ', "ML.ADMIN");
        REPLACEMENTS.put((int) 'ꔷ', "ADMIN");
        REPLACEMENTS.put((int) 'ꔁ', "MEDIA");
        REPLACEMENTS.put((int) 'ꔅ', "YT");
        REPLACEMENTS.put((int) 'ꕁ', "GOD");
        REPLACEMENTS.put((int) 'ူ', "HERO");
        REPLACEMENTS.put((int) 'ဪ', "TITAN");
        REPLACEMENTS.put((int) 'ဤ', "PRINCE");
        REPLACEMENTS.put((int) 'ဦ', "PHOENIX");
        REPLACEMENTS.put((int) 'ာ', "OVERLORD");
        REPLACEMENTS.put((int) 'ါ', "GUARDIAN");
        REPLACEMENTS.put((int) '့', "KRATOS");
        REPLACEMENTS.put((int) 'ဢ', "PHANTOM");
        REPLACEMENTS.put((int) 'ဴ', "CUSTOM");
        REPLACEMENTS.put((int) 'ိ', "WINTER");
        REPLACEMENTS.put((int) 'း', "SAKURA");
        REPLACEMENTS.put((int) 'ဥ', "SUMMER");
        REPLACEMENTS.put((int) 'ဣ', "HALLOWEEN");
        REPLACEMENTS.put((int) 'ဵ', "TIKTOK");
        REPLACEMENTS.put((int) 'ံ', "TIKTOK+");
        REPLACEMENTS.put((int) 'ဩ', "MEDIA");
        REPLACEMENTS.put((int) 'အ', "YOUTUBE");
        REPLACEMENTS.put((int) 'ေ', "HELPER");
        REPLACEMENTS.put((int) 'ု', "ML.ADMIN");
        REPLACEMENTS.put((int) 'ဲ', "MODER");
        REPLACEMENTS.put((int) 'ဧ', "CURATOR");
        REPLACEMENTS.put((int) '္', "SPECTATOR");
        REPLACEMENTS.put((int) 'သ', "DEVELOPER");

        REPLACEMENTS.put((int) 'ᴀ', "A");
        REPLACEMENTS.put((int) 'ʙ', "B");
        REPLACEMENTS.put((int) 'ᴄ', "C");
        REPLACEMENTS.put((int) 'ᴅ', "D");
        REPLACEMENTS.put((int) 'ᴇ', "E");
        REPLACEMENTS.put((int) 'ꜰ', "F");
        REPLACEMENTS.put((int) 'ɢ', "G");
        REPLACEMENTS.put((int) 'ʜ', "H");
        REPLACEMENTS.put((int) 'ɪ', "I");
        REPLACEMENTS.put((int) 'ᴊ', "J");
        REPLACEMENTS.put((int) 'ᴋ', "K");
        REPLACEMENTS.put((int) 'ʟ', "L");
        REPLACEMENTS.put((int) 'ᴍ', "M");
        REPLACEMENTS.put((int) 'ɴ', "N");
        REPLACEMENTS.put((int) 'ᴏ', "O");
        REPLACEMENTS.put((int) 'ᴘ', "P");
        REPLACEMENTS.put((int) 'ǫ', "Q");
        REPLACEMENTS.put((int) 'ʀ', "R");
        REPLACEMENTS.put((int) 'ᴛ', "T");
        REPLACEMENTS.put((int) 'ᴜ', "U");
        REPLACEMENTS.put((int) 'ꜱ', "S");
        REPLACEMENTS.put((int) 'ᴠ', "V");
        REPLACEMENTS.put((int) 'ᴡ', "W");
        REPLACEMENTS.put((int) 'ᵡ', "X");
        REPLACEMENTS.put((int) 'ʏ', "Y");
        REPLACEMENTS.put((int) 'ᴢ', "Z");

        RANK_COLORS.put((int) 'ꔀ', ColorUtils.rgb(141, 143, 141));
        RANK_COLORS.put((int) 'ꔄ', ColorUtils.rgb(100, 113, 251));
        RANK_COLORS.put((int) 'ꔈ', ColorUtils.rgb(245, 220, 29));
        RANK_COLORS.put((int) 'ꔒ', ColorUtils.rgb(79, 201, 83));
        RANK_COLORS.put((int) 'ꔖ', ColorUtils.rgb(85, 255, 255));
        RANK_COLORS.put((int) 'ꔠ', ColorUtils.rgb(224, 138, 52));
        RANK_COLORS.put((int) 'ꔤ', ColorUtils.rgb(202, 60, 60));
        RANK_COLORS.put((int) 'ꔨ', ColorUtils.rgb(245, 51, 238));
        RANK_COLORS.put((int) 'ꕠ', ColorUtils.rgb(214, 200, 42));
        RANK_COLORS.put((int) 'ꔲ', ColorUtils.rgb(121, 81, 202));
        RANK_COLORS.put((int) 'ꔶ', ColorUtils.rgb(202, 130, 60));
        RANK_COLORS.put((int) 'ꕄ', ColorUtils.rgb(202, 60, 60));
        RANK_COLORS.put((int) 'ꕖ', ColorUtils.rgb(68, 65, 66));
        RANK_COLORS.put((int) 'ꕈ', ColorUtils.rgb(127, 214, 86));
        RANK_COLORS.put((int) 'ꕀ', ColorUtils.rgb(92, 120, 7));
        RANK_COLORS.put((int) 'ꕒ', ColorUtils.rgb(230, 232, 230));
        RANK_COLORS.put((int) 'ꔉ', ColorUtils.rgb(214, 200, 42));
        RANK_COLORS.put((int) 'ꔓ', ColorUtils.rgb(100, 113, 251));
        RANK_COLORS.put((int) 'ꔗ', ColorUtils.rgb(100, 113, 251));
        RANK_COLORS.put((int) 'ꔡ', ColorUtils.rgb(121, 81, 202));
        RANK_COLORS.put((int) 'ꔥ', ColorUtils.rgb(100, 113, 251));
        RANK_COLORS.put((int) 'ꔩ', ColorUtils.rgb(121, 81, 202));
        RANK_COLORS.put((int) 'ꔳ', ColorUtils.rgb(64, 151, 214));
        RANK_COLORS.put((int) 'ꔷ', ColorUtils.rgb(202, 60, 60));
        RANK_COLORS.put((int) 'ꔁ', ColorUtils.rgb(121, 81, 202));
        RANK_COLORS.put((int) 'ꔅ', ColorUtils.rgb(255, 255, 255));
        RANK_COLORS.put((int) 'ꕁ', ColorUtils.rgb(245, 198, 29));
        RANK_COLORS.put((int) 'ꕉ', ColorUtils.rgb(202, 130, 60));
        RANK_COLORS.put((int) 'ူ', ColorUtils.rgb(13, 176, 209));
        RANK_COLORS.put((int) 'ဪ', ColorUtils.rgb(21, 232, 24));
        RANK_COLORS.put((int) 'ဤ', ColorUtils.rgb(232, 169, 21));
        RANK_COLORS.put((int) 'ဦ', ColorUtils.rgb(237, 215, 19));
        RANK_COLORS.put((int) 'ာ', ColorUtils.rgb(64, 163, 152));
        RANK_COLORS.put((int) 'ါ', ColorUtils.rgb(86, 196, 99));
        RANK_COLORS.put((int) '့', ColorUtils.rgb(147, 46, 230));
        RANK_COLORS.put((int) 'ဢ', ColorUtils.rgb(230, 46, 46));
        RANK_COLORS.put((int) 'ဴ', ColorUtils.rgb(16, 35, 179));
        RANK_COLORS.put((int) 'ိ', ColorUtils.rgb(55, 154, 184));
        RANK_COLORS.put((int) 'း', ColorUtils.rgb(184, 39, 159));
        RANK_COLORS.put((int) 'ဥ', ColorUtils.rgb(255, 182, 56));
        RANK_COLORS.put((int) 'ဣ', ColorUtils.rgb(232, 60, 30));
        RANK_COLORS.put((int) 'ဵ', ColorUtils.rgb(0, 0, 0));
        RANK_COLORS.put((int) 'ံ', ColorUtils.rgb(0, 0, 0));
        RANK_COLORS.put((int) 'ဩ', ColorUtils.rgb(37, 232, 30));
        RANK_COLORS.put((int) 'အ', ColorUtils.rgb(232, 30, 30));
        RANK_COLORS.put((int) 'ေ', ColorUtils.rgb(30, 134, 232));
        RANK_COLORS.put((int) 'ု', ColorUtils.rgb(89, 167, 227));
        RANK_COLORS.put((int) 'ဲ', ColorUtils.rgb(62, 137, 194));
        RANK_COLORS.put((int) 'ဧ', ColorUtils.rgb(56, 235, 74));
        RANK_COLORS.put((int) '္', ColorUtils.rgb(173, 184, 174));
        RANK_COLORS.put((int) 'သ', ColorUtils.rgb(255, 0, 25));

    }

    public static String replaceCodePoint(int codePoint) {
        return REPLACEMENTS.get(codePoint);
    }

    public static int getGradientColorForReplacement(int codePoint, int charIndex, int totalChars, float alpha, int currentColor) {
        if (isRank(codePoint)) {
            Integer baseColor = RANK_COLORS.get(codePoint);
            if (baseColor == null) {
                return withOpacity(currentColor, alpha);
            }
            int endColor = ColorUtils.darken(baseColor, 0.8f);
            float ratio = totalChars <= 1 ? 1.0f : (float) charIndex / (float) (totalChars - 1);
            int interpolatedColor = ColorUtils.interpolateColor(endColor, baseColor, ratio);
            return withOpacity(interpolatedColor, alpha);
        }
        return withOpacity(currentColor, alpha);
    }

    private static boolean isRank(int codePoint) {
        for (int rank : RANKS) {
            if (rank == codePoint) return true;
        }
        return false;
    }

    private static int withOpacity(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0f)));
        return ColorUtils.setAlphaColor(color, a);
    }
}

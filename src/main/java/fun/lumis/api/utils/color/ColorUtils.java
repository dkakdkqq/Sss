package fun.lumis.api.utils.color;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;
import fun.lumis.Lumis;
import fun.lumis.api.utils.math.MathUtils;

import java.awt.*;

public class ColorUtils {
    public final static Color green = new Color(36, 218, 118);
    public final static Color yellow = new Color(255, 196, 67);
    public final static Color orange = new Color(255, 134, 0);
    public final static Color red = new Color(239, 72, 54);
    public final static Color Blues = new Color(125, 217, 250);

    public static int red(int c) {
        return c >> 16 & 0xFF;
    }
    public static int green(int c) {
        return c >> 8 & 0xFF;
    }
    public static float redf(int c) {
        return (float) red(c) / 255.F;
    }

    public static float greenf(int c) {
        return (float) green(c) / 255.F;
    }

    public static float bluef(int c) {
        return (float) blue(c) / 255.F;
    }

    public static float alphaf(int c) {
        return alpha(c) / 255.F;
    }
    public static int getColor(int brightness, int alpha) {
        return ColorUtils.getColor(brightness, brightness, brightness, alpha);
    }

    public static int gradient(int color1, int color2, float amount) {
        amount = MathHelper.clamp(amount, 0, 1);
        int r = MathHelper.lerp(amount, red(color1), red(color2));
        int g = MathHelper.lerp(amount, green(color1), green(color2));
        int b = MathHelper.lerp(amount, blue(color1), blue(color2));
        int a = MathHelper.lerp(amount, alpha(color1), alpha(color2));

        return rgba(r, g, b, a);
    }

    public static int toColor(String hexColor) {
        if (hexColor == null || hexColor.length() != 7 || !hexColor.startsWith("#")) {
            return 0xFF000000;
        }
        try {
            int rgb = Integer.parseInt(hexColor.substring(1), 16);
            return 0xFF000000 | rgb;
        } catch (NumberFormatException e) {
            return 0xFF000000;
        }
    }

    public static int applyAlpha(int color, float alphaMul) {
        int a = (color >> 24) & 0xFF;
        int na = (int) (a * Math.max(0f, Math.min(1f, alphaMul)));
        return (color & 0x00FFFFFF) | (na << 24);
    }

    public static int r(int color) {
        return color >> 16 & 0xFF;
    }
    public static int g(int color) {
        return color >> 8 & 0xFF;
    }

    public static int b(int color) {
        return color & 0xFF;
    }

    public static int a(int color) {
        return color >> 24 & 0xFF;
    }

    public static int hexToRgb(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        if (hex.length() != 6) {
            throw new IllegalArgumentException("Недопустимый формат HEX: " + hex);
        }

        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);

        return rgb(r, g, b);
    }



    public static int getThemeColor() {
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            return Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            return getThemeColor(0);
        }
    }
    public static int getThemeColor(int index) {
        return Lumis.INSTANCE.themeStorage.getThemes().getTheme().getColor(index);
    }
    public static int getThemeStaticColor() {
        return Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
    }
    public static int rainbow(int speed, int index, float saturation, float brightness, float opacity) {
        int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        float hue = angle / 360f;
        int color = Color.HSBtoRGB(hue, saturation, brightness);
        return getColor(
                red(color),
                green(color),
                blue(color),
                Math.max(0, Math.min(255, (int) (opacity * 255)))
        );
    }

    public static int interpolate(int color1, int color2, double amount) {
        amount = (float) MathUtils.clamp(0, 1, amount);
        return getColor(
                MathUtils.ler1p(red(color1), red(color2), amount),
                MathUtils.ler1p(green(color1), green(color2), amount),
                MathUtils.ler1p(blue(color1), blue(color2), amount),
                MathUtils.ler1p(alpha(color1), alpha(color2), amount)
        );
    }

    public static int[] genGradientForText(int color1, int color2, int length) {
        int[] gradient = new int[length];
        for (int i = 0; i < length; i++) {
            double pc = (double) i / (length - 1);
            gradient[i] = interpolate(color1, color2, pc);
        }
        return gradient;
    }

    public static int blue(int c) {
        return c & 0xFF;
    }

    public static int overCol(int c1, int c2, float pc01) {
        return getColor((float) red(c1) * (1 - pc01) + (float) red(c2) * pc01, (float) green(c1) * (1 - pc01) + (float) green(c2) * pc01, (float) blue(c1) * (1 - pc01) + (float) blue(c2) * pc01, (float) alpha(c1) * (1 - pc01) + (float) alpha(c2) * pc01);
    }

    public static int darken(int color, float factor) {
        float[] rgb = getColorT(color);
        float[] hsb = Color.RGBtoHSB((int) (rgb[0] * 255), (int) (rgb[1] * 255), (int) (rgb[2] * 255), null);

        hsb[2] *= factor;
        hsb[2] = Math.max(0.0f, Math.min(1.0f, hsb[2]));

        int darkenedRGB = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        return applyOpacity(darkenedRGB, (int) (rgb[3] * 255));
    }

    public static int multDark(int c, float brpc) {
        return getColor((float) red(c) * brpc, (float) green(c) * brpc, (float) blue(c) * brpc, (float) alpha(c));
    }

    public static int overCol(int c1, int c2) {
        return overCol(c1, c2, 0.5f);
    }

    public static int alpha(int c) {
        return c >> 24 & 0xFF;
    }

    public static int multAlpha(int c, float apc) {
        return getColor(red(c), green(c), blue(c), (float) alpha(c) * apc);
    }

    public static int replAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }


    public static Color random() {
        return new Color(Color.HSBtoRGB((float) Math.random(), (float) (0.75F + (Math.random() / 4F)), (float) (0.75F + (Math.random() / 4F))));
    }
    public static int getColor(float r, float g, float b, float a) {
        return new Color((int) r, (int) g, (int) b, (int) a).getRGB();
    }

    public static float[] getRGBAf(int c) {
        return new float[]{ redf(c), greenf(c), bluef(c), alphaf(c) };
    }

    public static float[] getRGBAf1(int c) {
        return new float[]{(float) red(c) / 255.F, (float) green(c) / 255.F, (float) blue(c) / 255.F, (float) alpha(c) / 255.F};
    }
    public static Color interpolateTwoColors(int speed, int index, Color start, Color end, boolean trueColor) {
        int angle = 0;
        if (speed == 0) {
            angle = index % 360;
        } else {
            angle = (int) (((System.currentTimeMillis()) / speed + index) % 360);
        }
        angle = (angle >= 180 ? 360 - angle : angle) * 2;
        boolean tur = trueColor;
        return tur ? interpolateColorHue(start, end, angle / 360f) : interpolateColorC(start, end, angle / 360f);
    }
    public static Color interpolateTwoColors(int speed, int index, Color start, Color end) {
        return interpolateTwoColors(speed, index, start, end, false);
    }
    public static Color astolfo(float yDist, float yTotal, float saturation, float speedt) {
        float speed = 1800f;
        float hue = (System.currentTimeMillis() % (int) speed) + (yTotal - yDist) * speedt;
        while (hue > speed) {
            hue -= speed;
        }
        hue /= speed;
        if (hue > 1) {
            hue = 1 - (hue - 1);
        }
        hue += 1;
        return Color.getHSBColor(hue, saturation, 1);
    }

    private static int calculateHueDegrees(int divisor, int offset) {
        long currentTime = System.currentTimeMillis();
        long calculatedValue = (currentTime / divisor + offset) % 360L;
        return (int)calculatedValue;
    }

    public static void setColor(final Color color, final float alpha) {
        final float red = color.getRed() / 255F;
        final float green = color.getGreen() / 255F;
        final float blue = color.getBlue() / 255F;

        RenderSystem.setShaderColor(red, green, blue, alpha);
    }

    public static int rgb(int r, int g, int b) {
        return 255 << 24 | r << 16 | g << 8 | b;
    }
    public static int rgba(int r, int g, int b, int a) {
        return a << 24 | r << 16 | g << 8 | b;
    }
    public static float[] rgba(final int color) {
        return new float[] {
                (color >> 16 & 0xFF) / 255f,
                (color >> 8 & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                (color >> 24 & 0xFF) / 255f
        };
    }

    public static int rgba(double r, double g, double b, double a) {
        return rgba((int) r, (int) g, (int) b, (int) a);
    }
    public static int getRed(final int hex) {
        return hex >> 16 & 255;
    }

    public static int getGreen(final int hex) {
        return hex >> 8 & 255;
    }
    public static int interpolate(int start, int end, float value) {
        float[] startColor = rgba(start);
        float[] endColor = rgba(end);

        return rgba((int) MathUtils.interpolate(startColor[0] * 255, endColor[0] * 255, value),
                (int) MathUtils.interpolate(startColor[1] * 255, endColor[1] * 255, value),
                (int) MathUtils.interpolate(startColor[2] * 255, endColor[2] * 255, value),
                (int) MathUtils.interpolate(startColor[3] * 255, endColor[3] * 255, value));
    }
    public static int interpolateColor(int color1, int color2, float amount) {
        amount = Math.min(1, Math.max(0, amount));

        int red1 = getRed(color1);
        int green1 = getGreen(color1);
        int blue1 = getBlue(color1);
        int alpha1 = getAlpha(color1);

        int red2 = getRed(color2);
        int green2 = getGreen(color2);
        int blue2 = getBlue(color2);
        int alpha2 = getAlpha(color2);

        int interpolatedRed = interpolateInt(red1, red2, amount);
        int interpolatedGreen = interpolateInt(green1, green2, amount);
        int interpolatedBlue = interpolateInt(blue1, blue2, amount);
        int interpolatedAlpha = interpolateInt(alpha1, alpha2, amount);

        return (interpolatedAlpha << 24) | (interpolatedRed << 16) | (interpolatedGreen << 8) | interpolatedBlue;
    }

    public static MutableText gradient(String message, int first, int end) {
        MutableText text = Text.empty();

        for (int i = 0; i < message.length(); i++) {
            int color = ColorUtils.interpolateColor(first, end, (float) i / message.length());
            MutableText charText = Text.literal(String.valueOf(message.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
            text.append(charText);
        }

        return text;
    }

    public static Text replace(Text original, String find, String replaceWith) {
        if (original == null || find == null || replaceWith == null) {
            return original;
        }

        String originalText = original.getString();
        String replacedText = originalText.replace(find, replaceWith);
        return Text.literal(replacedText);
    }

    public static int gradient(int speed, int index, int... colors) {
        int angle = (int) ((System.currentTimeMillis() / speed + index) % 360);
        angle = (angle > 180 ? 360 - angle : angle) + 180;
        int colorIndex = (int) (angle / 360f * colors.length);
        if (colorIndex == colors.length) {
            colorIndex--;
        }
        int color1 = colors[colorIndex];
        int color2 = colors[colorIndex == colors.length - 1 ? 0 : colorIndex + 1];
        return interpolateColor(color1, color2, angle / 360f * colors.length - colorIndex);
    }

    public static int themeGradient(int speed, int index, float darkenFactor) {
        int theme = getThemeColor();
        return gradient(speed, index, theme, darken(theme, darkenFactor));
    }

    public static int getBlue(final int hex) {
        return hex & 255;
    }

    public static int getAlpha(final int hex) {
        return hex >> 24 & 255;
    }
    public static int getColor(int red, int green, int blue, int alpha) {
        int color = 0;
        color |= alpha << 24;
        color |= red << 16;
        color |= green << 8;
        return color | blue;
    }

    public static int getColor(int bright) {
        return getColor(bright, bright, bright, 255);
    }
    public static float[] getColorA(int color) {
        return new float[]{red(color) / 255f, green(color) / 255f, blue(color) / 255f, alphaf(color)};
    }

    public static float[] getColorT(int color) {
        return new float[]{red(color) / 255f, green(color) / 255f, blue(color) / 255f, alphaf(color)};
    }

    public static void setColor(double red, double green, double blue, double alpha) {
        GL11.glColor4d(red, green, blue, alpha);
    }

    public static int setAlphaColor(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }


    public static float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }

    public static Color interpolateColorC(Color color1, Color color2, float amount) {
        amount = Math.min(1, Math.max(0, amount));
        return new Color(interpolateInt(color1.getRed(), color2.getRed(), amount),
                interpolateInt(color1.getGreen(), color2.getGreen(), amount),
                interpolateInt(color1.getBlue(), color2.getBlue(), amount),
                interpolateInt(color1.getAlpha(), color2.getAlpha(), amount));
    }

    public static Double interpolate(double oldValue, double newValue, double interpolationValue){
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }

    public static float interpolateFloat(float oldValue, float newValue, double interpolationValue){
        return interpolate(oldValue, newValue, (float) interpolationValue).floatValue();
    }

    public static int interpolateInt(int oldValue, int newValue, double interpolationValue){
        return interpolate(oldValue, newValue, (float) interpolationValue);
    }


    public static Color interpolateColorHue(Color color1, Color color2, float amount) {
        amount = Math.min(1, Math.max(0, amount));

        float[] color1HSB = Color.RGBtoHSB(color1.getRed(), color1.getGreen(), color1.getBlue(), null);
        float[] color2HSB = Color.RGBtoHSB(color2.getRed(), color2.getGreen(), color2.getBlue(), null);

        Color resultColor = Color.getHSBColor(interpolateFloat(color1HSB[0], color2HSB[0], amount),
                interpolateFloat(color1HSB[1], color2HSB[1], amount), interpolateFloat(color1HSB[2], color2HSB[2], amount));

        return new Color(resultColor.getRed(), resultColor.getGreen(), resultColor.getBlue(),
                interpolateInt(color1.getAlpha(), color2.getAlpha(), amount));
    }

    public static void setColor(Color color) {
        if (color == null)
            color = Color.white;
        setColor(color.getRed() / 255F, color.getGreen() / 255F, color.getBlue() / 255F, color.getAlpha() / 255F);
    }

    public static void setColor(int color) {
        setColor(color, (float) (color >> 24 & 255) / 255.0F);
    }

    public static void setColor(int color, float alpha) {
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        RenderSystem.setShaderColor(r, g, b, alpha);
    }

    public static int applyOpacity(int color, float alpha) {
        return rgba(getRed(color), getGreen(color), getBlue(color), getAlpha(color) * alpha / 255f);
    }

    public static int reFactorColor(int color, float factor) {
        return rgba(extractRedf(color) * factor, extractGreenf(color) * factor, extractBluef(color) * factor, extractAlphaf(color));
    }

    public static float extractRedf(int color) {
        return (color >> 16 & 0xFF) / 255.0f;
    }

    public static int extractRed(int color) {
        return (color >> 16 & 0xFF);
    }

    public static float extractBluef(int color) {
        return (color & 0xFF) / 255.0f;
    }

    public static int extractBlue(int color) {
        return (color & 0xFF);
    }

    public static float extractGreenf(int color) {
        return (color >> 8 & 0xFF) / 255.0f;
    }

    public static int extractGreen(int color) {
        return (color >> 8 & 0xFF);
    }

    public static float extractAlphaf(int color) {
        return (color >> 24 & 0xFF) / 255.0f;
    }

    public static int extractAlpha(int color) {
        return (color >> 24 & 0xFF);
    }
}

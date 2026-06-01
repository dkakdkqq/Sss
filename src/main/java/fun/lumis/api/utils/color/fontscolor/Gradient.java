package fun.lumis.api.utils.color.fontscolor;

import java.util.List;
import lombok.Generated;

public class Gradient {
   protected final ColorRGBA topLeftColor;
   protected final ColorRGBA bottomLeftColor;
   protected final ColorRGBA topRightColor;
   protected final ColorRGBA bottomRightColor;

   protected Gradient(ColorRGBA topLeftColor, ColorRGBA bottomLeftColor, ColorRGBA topRightColor, ColorRGBA bottomRightColor) {
      this.topLeftColor = topLeftColor;
      this.bottomLeftColor = bottomLeftColor;
      this.topRightColor = topRightColor;
      this.bottomRightColor = bottomRightColor;
   }

   public static Gradient of(ColorRGBA topLeftColor, ColorRGBA bottomLeftColor, ColorRGBA topRightColor, ColorRGBA bottomRightColor) {
      return new Gradient(topLeftColor, bottomLeftColor, topRightColor, bottomRightColor);
   }

   public static Gradient of(List<ColorRGBA> colors) {
      return new Gradient((ColorRGBA)colors.get(0), (ColorRGBA)colors.get(1), (ColorRGBA)colors.get(2), (ColorRGBA)colors.get(3));
   }

   public Gradient rotate() {
      return this;
   }

   public Gradient mulAlpha(float alphaMultiplier) {
      return new Gradient(this.topLeftColor.mulAlpha(alphaMultiplier), this.bottomLeftColor.mulAlpha(alphaMultiplier), this.topRightColor.mulAlpha(alphaMultiplier), this.bottomRightColor.mulAlpha(alphaMultiplier));
   }

   @Generated
   public ColorRGBA getTopLeftColor() {
      return this.topLeftColor;
   }

   @Generated
   public ColorRGBA getBottomLeftColor() {
      return this.bottomLeftColor;
   }

   @Generated
   public ColorRGBA getTopRightColor() {
      return this.topRightColor;
   }

   @Generated
   public ColorRGBA getBottomRightColor() {
      return this.bottomRightColor;
   }
}

package fun.lumis.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Camera.class})
public interface ICameraMixin {
   @Invoker("setRotation")
   void setCustomRotation(float var1, float var2);

   @Invoker("clipToSpace")
   float setClipToSpace(float var1);

   @Invoker("moveBy")
   void setCustomMoveBy(float x, float y, float z);
}

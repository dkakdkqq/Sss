package fun.lumis.api.events.implement;

import lombok.Generated;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import fun.lumis.api.events.Event;

public class Event3DRender extends Event {
   private final MatrixStack matrices;
   private final Matrix4f positionMatrix;
   private final Matrix4f projectionMatrix;
   private final Camera camera;
   private final float tickDelta;

   @Generated
   public MatrixStack getMatrices() {
      return this.matrices;
   }

   @Generated
   public Matrix4f getPositionMatrix() {
      return this.positionMatrix;
   }

   @Generated
   public Matrix4f getProjectionMatrix() {
      return this.projectionMatrix;
   }

   @Generated
   public Camera getCamera() {
      return this.camera;
   }

   @Generated
   public float getTickDelta() {
      return this.tickDelta;
   }

   @Generated
   public Event3DRender(MatrixStack matrices, Matrix4f positionMatrix, Matrix4f projectionMatrix, Camera camera, float tickDelta) {
      this.matrices = matrices;
      this.positionMatrix = positionMatrix;
      this.projectionMatrix = projectionMatrix;
      this.camera = camera;
      this.tickDelta = tickDelta;
   }
}

package fun.lumis.api.events.implement;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import fun.lumis.api.events.Event;

public class EventRender extends Event {
    @Getter
    @Setter
    @AllArgsConstructor
    public static class Default extends Event {
        private final DrawContext context;
        private final float partialTicks;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class World extends Event {
        private final Window scaledResolution;
        private final float partialTicks;
        private final Matrix4f matrix;
        private final MatrixStack matrixStack;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class Game extends Event {
        private final WorldRenderer context;
        private final MatrixStack matrix;
        private final Matrix4f projectionMatrix;
        private final Camera camera;
        private final float partialTicks;
        private final long finishTimeNano;
    }
}

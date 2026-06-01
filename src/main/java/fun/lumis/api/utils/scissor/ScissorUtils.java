package fun.lumis.api.utils.scissor;

import com.google.common.collect.Lists;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL30;

import java.awt.*;
import java.util.List;

public class ScissorUtils {
    private static class State implements Cloneable {
        public boolean enabled;
        public int transX;
        public int transY;
        public int x;
        public int y;
        public int width;
        public int height;

        @Override
        public State clone() {
            try {
                return (State) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static State state = new State();

    private static final List<State> stateStack = Lists.newArrayList();

    public static void push() {
        stateStack.add(state.clone());
    }

    public static void pop() {
        if (stateStack.isEmpty()) {
            return;
        }
        state = stateStack.remove(stateStack.size() - 1);
        if (state.enabled) {
            GL30.glEnable(GL30.GL_SCISSOR_TEST);
            GL30.glScissor(state.x, state.y, state.width, state.height);
        } else {
            GL30.glDisable(GL30.GL_SCISSOR_TEST);
        }
    }

    public static void unset() {
        GL30.glDisable(GL30.GL_SCISSOR_TEST);
        state.enabled = false;
    }

    private static Window getWindow() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : client.getWindow();
    }

    private static double getScaleFactor() {
        Window window = getWindow();
        return window == null ? 1.0D : window.getScaleFactor();
    }

    public static void setFromComponentCoordinates(int x, int y, int width, int height) {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        double scaleFactor = getScaleFactor();

        int screenX = (int) (x * scaleFactor);
        int screenY = (int) (y * scaleFactor);
        int screenWidth = (int) (width * scaleFactor);
        int screenHeight = (int) (height * scaleFactor);
        screenY = window.getHeight() - screenY - screenHeight;
        set(screenX, screenY, screenWidth, screenHeight);
    }

    public static void setFromComponentCoordinates(double x, double y, double width, double height) {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        double scaleFactor = getScaleFactor();

        int screenX = (int) (x * scaleFactor);
        int screenY = (int) (y * scaleFactor);
        int screenWidth = (int) (width * scaleFactor);
        int screenHeight = (int) (height * scaleFactor);
        screenY = window.getHeight() - screenY - screenHeight;
        set(screenX, screenY, screenWidth, screenHeight);
    }

    public static void setFromComponentCoordinates(double x, double y, double width, double height, float scale) {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        double scaleFactor = getScaleFactor();

        float animationValue = scale;

        float halfAnimationValueRest = (1 - animationValue) / 2f;
        double testX = x + (width * halfAnimationValueRest);
        double testY = y + (height * halfAnimationValueRest);
        double testW = width * animationValue;
        double testH = height * animationValue;

        testX = testX * animationValue + ((window.getScaledWidth() - testW) * halfAnimationValueRest);

        int screenX = (int) (testX * scaleFactor);
        int screenY = (int) (testY * scaleFactor);
        int screenWidth = (int) (testW * scaleFactor);
        int screenHeight = (int) (testH * scaleFactor);
        screenY = window.getHeight() - screenY - screenHeight;
        set(screenX, screenY, screenWidth, screenHeight);
    }

    public static void set(int x, int y, int width, int height) {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        Rectangle screen = new Rectangle(0, 0, window.getWidth(), window.getHeight());
        Rectangle current;
        if (state.enabled) {
            current = new Rectangle(state.x, state.y, state.width, state.height);
        } else {
            current = screen;
        }
        Rectangle target = new Rectangle(x + state.transX, y + state.transY, width, height);
        Rectangle result = current.intersection(target);
        result = result.intersection(screen);
        if (result.width < 0)
            result.width = 0;
        if (result.height < 0)
            result.height = 0;
        state.enabled = true;
        state.x = result.x;
        state.y = result.y;
        state.width = result.width;
        state.height = result.height;
        GL30.glEnable(GL30.GL_SCISSOR_TEST);
        GL30.glScissor(result.x, result.y, result.width, result.height);
    }

    public static void translate(int x, int y) {
        state.transX = x;
        state.transY = y;
    }

    public static void translateFromComponentCoordinates(int x, int y) {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        int totalHeight = window.getScaledHeight();
        double scaleFactor = getScaleFactor();

        int screenX = (int) (x * scaleFactor);
        int screenY = (int) (y * scaleFactor);
        screenY = (int) (totalHeight * scaleFactor) - screenY;
        translate(screenX, screenY);
    }
}

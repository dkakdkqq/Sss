package fun.lumis.api.utils.draggable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import fun.lumis.api.QClient;
import fun.lumis.api.utils.math.HoveringUtils;
import fun.lumis.api.utils.math.MathUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.client.modules.Module;

public class Draggable implements QClient {
    @Expose
    @SerializedName("x")
    private float xPos;
    @Expose
    @SerializedName("y")
    private float yPos;
    public float initialXVal;
    public float initialYVal;
    private float startX;
    private float startY;
    private boolean dragging;
    @Setter
    @Getter
    private float width;
    @Setter
    @Getter
    private float height;
    @Getter
    @Expose
    @SerializedName("name")
    private String name;
    @Getter
    private final Module module;

    private float targetXPos;
    private float targetYPos;

    private static final float CENTER_LINE_WIDTH = 1f;
    private static final float SNAP_THRESHOLD = 10.0f;

    private float lineAlpha = 0.0f;
    private long lastUpdateTime;

    private boolean snapToCenter, snapToCenterx, snapToCenter2x, snapToCenter3x, snapToCenter4x, snapToCenter5x, snapToCenter2, snapToCenter3, snapToCenter4, snapToCenter5;

    private static final float LERP_SPEED = 0.19f;
    private static final float MAX_TILT_DEGREES = 25f;
    private static final float TILT_FROM_MOUSE_DELTA = 4f;
    private static final float DRAG_TILT_LERP = 0.14f;
    private static final float RELEASE_TILT_LERP = 0.10f;
    private static final float TILT_DELTA_SMOOTHING = 0.18f;
    private static final float TILT_TARGET_SMOOTHING = 0.22f;
    private static final float TILT_DEADZONE = 0.18f;
    private static final float DRAG_SCALE_MULTIPLIER = 1.01f;
    private static final float DRAG_SCALE_LERP = 0.10f;
    private static final float RELEASE_SCALE_LERP = 0.02f;

    private float dragTiltDegrees;
    private float targetTiltDegrees;
    private float smoothedMouseDeltaX;
    private float lastDragMouseX;
    private boolean hasLastDragMouseX;
    private boolean tiltMatrixPushed;
    private float dragScale = 1.0f;
    private float targetScale = 1.0f;

    public Draggable(Module module, String name, float initialXVal, float initialYVal) {
        this.module = module;
        this.name = name;
        this.xPos = initialXVal;
        this.yPos = initialYVal;
        this.initialXVal = initialXVal;
        this.initialYVal = initialYVal;
    }

    public float getX() {
        return this.xPos;
    }

    public void setX(float x) {
        this.xPos = x;
    }

    public float getY() {
        return this.yPos;
    }

    public void setY(float y) {
        this.yPos = y;
    }

    private Vec2i getMouse(int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client == null ? null : client.getWindow();
        double scaleFactor = window == null ? 1.0D : window.getScaleFactor();
        return new Vec2i((int) (mouseX * scaleFactor / 2), (int) (mouseY * scaleFactor / 2));
    }

    public final void onDraw(int mouseX, int mouseY, Window res, MatrixStack ms) {
        Vec2i fixed = this.getMouse(mouseX, mouseY);
        mouseX = fixed.getX();
        mouseY = fixed.getY();

        float centerX = res.getScaledWidth() / 2.0f;
        float centerY = res.getScaledHeight() / 2.0f;

        float centerX2 = res.getScaledWidth() / 4.0f;
        float centerY2 = res.getScaledHeight() / 4.0f;
        float centerX3 = res.getScaledWidth() / 8.0f;
        float centerY3 = res.getScaledHeight() / 8.0f;

        float centerX4 = res.getScaledWidth() / 1.15f;
        float centerY4 = res.getScaledHeight() / 1.15f;
        float centerX5 = res.getScaledWidth() / 1.35f;
        float centerY5 = res.getScaledHeight() / 1.35f;

        snapToCenter = snapToCenterx = snapToCenter2x = snapToCenter3x = snapToCenter4x = snapToCenter5x = snapToCenter2 = snapToCenter3 = snapToCenter4 = snapToCenter5 = false;

        if (dragging) {
            targetScale = DRAG_SCALE_MULTIPLIER;
            if (hasLastDragMouseX) {
                float mouseDeltaX = mouseX - lastDragMouseX;
                if (Math.abs(mouseDeltaX) < TILT_DEADZONE) {
                    mouseDeltaX = 0.0f;
                }

                smoothedMouseDeltaX = MathUtils.lerp(smoothedMouseDeltaX, mouseDeltaX, TILT_DELTA_SMOOTHING);
                float desiredTilt = Math.max(-MAX_TILT_DEGREES, Math.min(MAX_TILT_DEGREES, smoothedMouseDeltaX * TILT_FROM_MOUSE_DELTA));
                targetTiltDegrees = MathUtils.lerp(targetTiltDegrees, desiredTilt, TILT_TARGET_SMOOTHING);
            }
            lastDragMouseX = mouseX;
            hasLastDragMouseX = true;

            targetXPos = (mouseX - startX);
            targetYPos = (mouseY - startY);

            boolean snapped = false;

            if (Math.abs(targetXPos + width / 2.0f - centerX) < SNAP_THRESHOLD) {
                targetXPos = centerX - width / 2.0f;
                snapToCenterx = true;
                snapped = true;
            }

            if (Math.abs(targetYPos + height / 2.0f - centerY) < SNAP_THRESHOLD) {
                targetYPos = centerY - height / 2.0f;
                snapToCenter = true;
                snapped = true;
            }

            if (Math.abs(targetXPos + width / 2.0f - centerX2) < SNAP_THRESHOLD) {
                targetXPos = centerX2 - width / 2.0f;
                snapToCenter2x = true;
                snapped = true;
            }

            if (Math.abs(targetYPos + height / 2.0f - centerY2) < SNAP_THRESHOLD) {
                targetYPos = centerY2 - height / 2.0f;
                snapToCenter2 = true;
                snapped = true;
            }

            if (Math.abs(targetXPos + width / 2.0f - centerX3) < SNAP_THRESHOLD) {
                targetXPos = centerX3 - width / 2.0f;
                snapToCenter3x = true;
                snapped = true;
            }

            if (Math.abs(targetYPos + height / 2.0f - centerY3) < SNAP_THRESHOLD) {
                targetYPos = centerY3 - height / 2.0f;
                snapToCenter3 = true;
                snapped = true;
            }

            if (Math.abs(targetXPos + width / 2.0f - centerX4) < SNAP_THRESHOLD) {
                targetXPos = centerX4 - width / 2.0f;
                snapToCenter4x = true;
                snapped = true;
            }

            if (Math.abs(targetYPos + height / 2.0f - centerY4) < SNAP_THRESHOLD) {
                targetYPos = centerY4 - height / 2.0f;
                snapToCenter4 = true;
                snapped = true;
            }

            if (Math.abs(targetXPos + width / 2.0f - centerX5) < SNAP_THRESHOLD) {
                targetXPos = centerX5 - width / 2.0f;
                snapToCenter5x = true;
                snapped = true;
            }

            if (Math.abs(targetYPos + height / 2.0f - centerY5) < SNAP_THRESHOLD) {
                targetYPos = centerY5 - height / 2.0f;
                snapToCenter5 = true;
                snapped = true;
            }

            if (targetXPos + width > res.getScaledWidth()) {
                targetXPos = res.getScaledWidth() - width;
            }
            if (targetYPos + height > res.getScaledHeight()) {
                targetYPos = res.getScaledHeight() - height;
            }
            if (targetXPos < 0) {
                targetXPos = 0;
            }
            if (targetYPos < 0) {
                targetYPos = 0;
            }

            xPos = MathUtils.lerp(xPos, targetXPos, LERP_SPEED);
            yPos = MathUtils.lerp(yPos, targetYPos, LERP_SPEED);

            updateLineAlpha(snapped);
        } else {
            targetScale = 1.0f;
            targetTiltDegrees = 0.0f;
            smoothedMouseDeltaX = MathUtils.lerp(smoothedMouseDeltaX, 0.0f, TILT_DELTA_SMOOTHING);
            hasLastDragMouseX = false;
            updateLineAlpha(false);
        }
        updateTilt();

        drawCenterLines(ms, res);
    }

    private void updateTilt() {
        float lerp = dragging ? DRAG_TILT_LERP : RELEASE_TILT_LERP;
        dragTiltDegrees = MathUtils.lerp(dragTiltDegrees, targetTiltDegrees, lerp);
        if (!dragging && Math.abs(dragTiltDegrees) < 0.02f) {
            dragTiltDegrees = 0.0f;
        }

        float scaleLerp = dragging ? DRAG_SCALE_LERP : RELEASE_SCALE_LERP;
        dragScale = MathUtils.lerp(dragScale, targetScale, scaleLerp);
        if (!dragging && Math.abs(dragScale - 1.0f) < 0.002f) {
            dragScale = 1.0f;
        }
    }

    public void beginRenderTilt(MatrixStack ms) {
        updateTilt();
        tiltMatrixPushed = false;
        if (Math.abs(dragTiltDegrees) < 0.05f && Math.abs(dragScale - 1.0f) < 0.002f) {
            return;
        }

        float centerX = xPos + width / 2.0f;
        float centerY = yPos + height / 2.0f;

        ms.push();
        ms.translate(centerX, centerY, 0.0f);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(dragTiltDegrees));
        ms.scale(dragScale, dragScale, 1.0f);
        ms.translate(-centerX, -centerY, 0.0f);
        tiltMatrixPushed = true;
    }

    public void endRenderTilt(MatrixStack ms) {
        if (tiltMatrixPushed) {
            ms.pop();
            tiltMatrixPushed = false;
        }
    }

    private void updateLineAlpha(boolean active) {
        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;

        float fadeSpeed = 2.0f;
        float fadeOutSpeed = 2.0f;

        if (active) {
            lineAlpha += deltaTime * fadeSpeed;
            if (lineAlpha > 1.0f) {
                lineAlpha = 1.0f;
            }
        } else {
            lineAlpha -= deltaTime * fadeOutSpeed;
            if (lineAlpha < 0.0f) {
                lineAlpha = 0.0f;
            }
        }
    }

    private void drawCenterLines(MatrixStack ms, Window res) {
        if (lineAlpha > 0.0f) {
            float centerX = res.getScaledWidth() / 2.0f;
            float centerY = res.getScaledHeight() / 2.0f;
            float centerX2 = res.getScaledWidth() / 4.0f;
            float centerY2 = res.getScaledHeight() / 4.0f;
            float centerX3 = res.getScaledWidth() / 8.0f;
            float centerY3 = res.getScaledHeight() / 8.0f;
            float centerX4 = res.getScaledWidth() / 1.15f;
            float centerY4 = res.getScaledHeight() / 1.15f;
            float centerX5 = res.getScaledWidth() / 1.35f;
            float centerY5 = res.getScaledHeight() / 1.35f;

            int color = (int) (lineAlpha * 255) << 24 | 0xFFFFFF;
            if (snapToCenterx) {
                RenderUtils.drawRoundedRect(ms ,centerX - CENTER_LINE_WIDTH / 3.0f, 0, CENTER_LINE_WIDTH, res.getScaledHeight(), 1f, color);
            }
            if (snapToCenter) {
                RenderUtils.drawRoundedRect(ms,0, centerY - CENTER_LINE_WIDTH / 3.0f, res.getScaledWidth(), CENTER_LINE_WIDTH, 1f, color);
            }
            if (snapToCenter2x) {
                RenderUtils.drawRoundedRect(ms,centerX2 - CENTER_LINE_WIDTH / 3.0f, 0, CENTER_LINE_WIDTH, res.getScaledHeight(), 1f, color);
            }
            if (snapToCenter2) {
                RenderUtils.drawRoundedRect(ms,0, centerY2 - CENTER_LINE_WIDTH / 3.0f, res.getScaledWidth(), CENTER_LINE_WIDTH, 1f, color);
            }
            if (snapToCenter3x) {
                RenderUtils.drawRoundedRect(ms,centerX3 - CENTER_LINE_WIDTH / 3.0f, 0, CENTER_LINE_WIDTH, res.getScaledHeight(), 1f, color);
            }
            if (snapToCenter3) {
                RenderUtils.drawRoundedRect(ms,0, centerY3 - CENTER_LINE_WIDTH / 3.0f, res.getScaledWidth(), CENTER_LINE_WIDTH, 1f, color);
            }
            if (snapToCenter4x) {
                RenderUtils.drawRoundedRect(ms,centerX4 - CENTER_LINE_WIDTH / 3.0f, 0, CENTER_LINE_WIDTH, res.getScaledHeight(), 1f, color);
            }
            if (snapToCenter4) {
                RenderUtils.drawRoundedRect(ms,0, centerY4 - CENTER_LINE_WIDTH / 3.0f, res.getScaledWidth(), CENTER_LINE_WIDTH, 1f, color);
            }
            if (snapToCenter5x) {
                RenderUtils.drawRoundedRect(ms,centerX5 - CENTER_LINE_WIDTH / 3.0f, 0, CENTER_LINE_WIDTH, res.getScaledHeight(), 1f, color);
            }
            if (snapToCenter5) {
                RenderUtils.drawRoundedRect(ms,0, centerY5 - CENTER_LINE_WIDTH / 3.0f, res.getScaledWidth(), CENTER_LINE_WIDTH, 1f, color);
            }
        }
    }
    public final boolean onClick(double mouseX, double mouseY, int button) {
        if (button == 0 && HoveringUtils.isInRegion(mouseX, mouseY, this.xPos, this.yPos, this.width, this.height)) {
            this.dragging = true;
            this.targetScale = DRAG_SCALE_MULTIPLIER;
            this.startX = (float)((int)(mouseX - (double)this.xPos));
            this.startY = (float)((int)(mouseY - (double)this.yPos));
            this.smoothedMouseDeltaX = 0.0f;
            this.hasLastDragMouseX = false;
            lastUpdateTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public final void onRelease(int button) {
        if (button == 0) {
            this.dragging = false;
            this.targetScale = 1.0f;
            this.targetTiltDegrees = 0.0f;
            this.smoothedMouseDeltaX = 0.0f;
            this.hasLastDragMouseX = false;
        }

    }
}

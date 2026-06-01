package fun.lumis.client.modules.impl.movement;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventMoveInput;
import fun.lumis.api.events.implement.EventMove;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.client.modules.Module;

public class FreeCam extends Module {

    public static FreeCam INSTANCE = new FreeCam();

    public Vec3d pos;

    public FreeCam() {
        super("FreeCam", "Обзор местности за фейк игрока", ModuleCategory.MOVEMENT);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.player != null) {
            this.pos = mc.player.getPos();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null && this.pos != null) {
            mc.player.setPosition(this.pos);
        }
    }

    @EventLink
    public void onEvent(final EventPacket event) {
        Packet<?> packet = event.getPacket();

        if (packet instanceof PlayerMoveC2SPacket) {
            event.cancel();
        } else if (packet instanceof PlayerRespawnS2CPacket || packet instanceof GameJoinS2CPacket) {
            this.toggle();
        }
    }

    @EventLink
    public void onEvent(final Event3DRender event) {
        if (this.pos == null || mc.player == null) return;

        float width = mc.player.getWidth() / 2.0F;
        float height = mc.player.getHeight();

        Box box = new Box(
                this.pos.x - width,
                this.pos.y,
                this.pos.z - width,
                this.pos.x + width,
                this.pos.y + height,
                this.pos.z + width
        );

        drawHitbox(event.getMatrices(), box, event.getCamera().getPos());
    }

    private void drawHitbox(MatrixStack matrices, Box box, Vec3d camera) {
        double x1 = box.minX - camera.x;
        double y1 = box.minY - camera.y;
        double z1 = box.minZ - camera.z;
        double x2 = box.maxX - camera.x;
        double y2 = box.maxY - camera.y;
        double z2 = box.maxZ - camera.z;

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        Tessellator tessellator = Tessellator.getInstance();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(1.5f);

        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float r = 1.0F;
        float g = 1.0F;
        float b = 1.0F;
        float a = 1.0F;

        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y1, (float) z1).color(r, g, b, a);

        buffer.vertex(matrix, (float) x2, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y1, (float) z2).color(r, g, b, a);

        buffer.vertex(matrix, (float) x2, (float) y1, (float) z2).color(r, g, b, a);
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z2).color(r, g, b, a);

        buffer.vertex(matrix, (float) x1, (float) y1, (float) z2).color(r, g, b, a);
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);

        buffer.vertex(matrix, (float) x1, (float) y2, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z1).color(r, g, b, a);

        buffer.vertex(matrix, (float) x2, (float) y2, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);

        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
        buffer.vertex(matrix, (float) x1, (float) y2, (float) z2).color(r, g, b, a);

        buffer.vertex(matrix, (float) x1, (float) y2, (float) z2).color(r, g, b, a);
        buffer.vertex(matrix, (float) x1, (float) y2, (float) z1).color(r, g, b, a);

        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x1, (float) y2, (float) z1).color(r, g, b, a);

        buffer.vertex(matrix, (float) x2, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z1).color(r, g, b, a);

        buffer.vertex(matrix, (float) x2, (float) y1, (float) z2).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);

        buffer.vertex(matrix, (float) x1, (float) y1, (float) z2).color(r, g, b, a);
        buffer.vertex(matrix, (float) x1, (float) y2, (float) z2).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    @EventLink
    public void onEvent(final EventMove event) {
        if (mc.player == null) return;

        mc.player.noClip = true;

        double speed = 1.0;
        double forward = mc.player.input.movementForward;
        double strafe = mc.player.input.movementSideways;

        double yaw = Math.toRadians(mc.player.getYaw());

        double motionX = 0;
        double motionZ = 0;

        if (forward != 0 || strafe != 0) {
            double angle = yaw + Math.atan2(-strafe, forward);
            motionX = -Math.sin(angle) * speed;
            motionZ = Math.cos(angle) * speed;
        }

        double motionY = 0;
        if (mc.options.jumpKey.isPressed()) {
            motionY = speed;
        } else if (mc.options.sneakKey.isPressed()) {
            motionY = -speed;
        }

        event.setMovePos(new Vec3d(motionX, motionY, motionZ));
    }

    @EventLink
    public void onEvent(final EventMoveInput event) {
        if (mc.player == null) return;

        if (mc.player.getPose() == EntityPose.CROUCHING || mc.player.getPose() == EntityPose.SWIMMING) {
            event.setStrafe(event.getStrafe() * 5.0F);
        }
    }
}

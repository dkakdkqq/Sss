package fun.lumis.client.modules.impl.movement;

import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.*;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.player.MoveUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.Iterator;

public class NoWeb extends Module {

    public static NoWeb INSTANCE = new NoWeb();
    public ModeSetting web = new ModeSetting("Мод", "Коллизия", "Коллизия", "Обычный", "Тест");

    public NoWeb() {
        super("NoWeb", "Убирает замедление от паутины", ModuleCategory.MOVEMENT);
        addSettings(web);
    }

    @EventLink
    public void onUpdate(final EventUpdate eventUpdate) {
        if (mc.player == null || mc.world == null) return;

        if (web.is("Коллизия")) {
            BlockPos playerPos = mc.player.getBlockPos();

            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 2; y++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos pos = playerPos.add(x, y, z);

                        if (mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB) {
                            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
                        }
                    }
                }
            }
        }

        if (web.is("Обычный")) {
            if (!mc.player.isSneaking() || !mc.player.isOnGround()) {
                BlockPos aboveHeadPos;
                double z;
                double x;
                boolean headInWeb = false;
                boolean feetInWeb = false;

                for (x = -0.295; x <= 0.295; x += 0.05) {
                    block1:
                    for (z = -0.295; z <= 0.295; z += 0.05) {
                        for (double y = mc.player.getStandingEyeHeight(); y >= 0.0; y -= 0.1) {
                            BlockPos headPos = BlockPos.ofFloored(mc.player.getX() + x, mc.player.getY() + y, mc.player.getZ() + z);
                            if (mc.world.getBlockState(headPos).getBlock() != Blocks.COBWEB) continue;
                            headInWeb = true;
                            continue block1;
                        }
                    }
                }

                if (!headInWeb) {
                    block3:
                    for (x = -0.295; x <= 0.295; x += 0.05) {
                        for (z = -0.295; z <= 0.295; z += 0.05) {
                            BlockPos pos = BlockPos.ofFloored(mc.player.getX() + x, mc.player.getY(), mc.player.getZ() + z);
                            if (mc.world.getBlockState(pos).getBlock() != Blocks.COBWEB) continue;
                            feetInWeb = true;
                            continue block3;
                        }
                    }
                }

                aboveHeadPos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + mc.player.getStandingEyeHeight() + 0.2f, mc.player.getZ());
                if (!headInWeb && !feetInWeb && mc.world.getBlockState(aboveHeadPos).getBlock() == Blocks.COBWEB) {
                    headInWeb = true;
                }

                if (headInWeb || feetInWeb) {
                    if (mc.options.jumpKey.isPressed()) {
                        mc.player.setVelocity(0.0, 0.8, 0.0);
                    } else if (mc.options.sneakKey.isPressed()) {
                        mc.player.setVelocity(0.0, -0.8, 0.0);
                    } else {
                        mc.player.setVelocity(0.0, 0.0, 0.0);
                    }
                    MoveUtils.setMotion(0.21);
                }
            }
        }

        if (web.is("Тест")) {
            if (mc.player != null) {
                boolean cobweb = false;
                Box box = mc.player.getBoundingBox();
                Iterator it = BlockPos.iterate(MathHelper.floor(box.minX), MathHelper.floor(box.minY), MathHelper.floor(box.minZ), MathHelper.floor(box.maxX), MathHelper.floor(box.maxY), MathHelper.floor(box.maxZ)).iterator();

                while(it.hasNext()) {
                    BlockPos pos = (BlockPos)it.next();
                    if (mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) {
                        cobweb = true;
                    }
                }

                if (cobweb) {
                    Vec3d velocity = mc.player.getVelocity();
                    float yaw = mc.player.getYaw();
                    double forward = 0.0D;
                    double strafe = 0.0D;
                    if (mc.player.input.playerInput.forward()) {
                        ++forward;
                    }

                    if (mc.player.input.playerInput.backward()) {
                        --forward;
                    }

                    if (mc.player.input.playerInput.left()) {
                        ++strafe;
                    }

                    if (mc.player.input.playerInput.right()) {
                        --strafe;
                    }

                    if (forward != 0.0D || strafe != 0.0D) {
                        if (forward != 0.0D) {
                            if (strafe > 0.0D) {
                                yaw += (float)(forward > 0.0D ? -45 : 45);
                            } else if (strafe < 0.0D) {
                                yaw += (float)(forward > 0.0D ? 45 : -45);
                            }

                            strafe = 0.0D;
                            if (forward > 0.0D) {
                                forward = 1.0D;
                            } else {
                                forward = -1.0D;
                            }
                        }

                        double movementYaw = Math.toDegrees(Math.atan2(strafe, forward)) + (double)yaw;
                        yaw = (float)((movementYaw % 360.0D + 360.0D) % 360.0D);
                    }

                    float result = 0.63F;
                    if ((!(yaw >= 313.0F) || !(yaw <= 317.0F)) && (!(yaw >= 223.0F) || !(yaw <= 227.0F)) && (!(yaw >= 133.0F) || !(yaw <= 137.0F)) && (!(yaw >= 43.0F) || !(yaw <= 47.0F))) {
                        if ((!(yaw >= 311.0F) || !(yaw <= 319.0F)) && (!(yaw >= 221.0F) || !(yaw <= 229.0F)) && (!(yaw >= 131.0F) || !(yaw <= 139.0F)) && (!(yaw >= 41.0F) || !(yaw <= 49.0F))) {
                            if ((!(yaw >= 310.8F) || !(yaw <= 320.8F)) && (!(yaw >= 220.8F) || !(yaw <= 230.8F)) && (!(yaw >= 130.8F) || !(yaw <= 140.8F)) && (!(yaw >= 40.8F) || !(yaw <= 50.8F))) {
                                if ((!(yaw >= 308.7F) || !(yaw <= 322.7F)) && (!(yaw >= 218.7F) || !(yaw <= 232.7F)) && (!(yaw >= 128.7F) || !(yaw <= 142.7F)) && (!(yaw >= 38.7F) || !(yaw <= 52.7F))) {
                                    if ((!(yaw >= 306.5F) || !(yaw <= 324.5F)) && (!(yaw >= 216.5F) || !(yaw <= 234.5F)) && (!(yaw >= 126.5F) || !(yaw <= 144.5F)) && (!(yaw >= 36.5F) || !(yaw <= 54.5F))) {
                                        if (yaw >= 304.0F && yaw <= 327.0F || yaw >= 214.0F && yaw <= 237.0F || yaw >= 124.0F && yaw <= 147.0F || yaw >= 34.0F && yaw <= 57.0F) {
                                            result = 0.75F;
                                        }
                                    } else {
                                        result = 0.79F;
                                    }
                                } else {
                                    result = 0.81F;
                                }
                            } else {
                                result = 0.83F;
                            }
                        } else {
                            result = 0.85F;
                        }
                    } else {
                        result = 0.88F;
                    }

                    if (!mc.options.jumpKey.isPressed()) {
                        if (mc.options.sneakKey.isPressed()) {
                            mc.player.setVelocity(velocity.x, -3.6D, velocity.z);
                        } else {
                            mc.player.setVelocity(velocity.x, 0.0D, velocity.z);
                        }
                    } else {
                        mc.player.setVelocity(velocity.x, forward == 0.0D && strafe == 0.0D ? 1.4D : 1.2D, velocity.z);
                    }

                    MoveUtils.setVelocity((double)result);
                }

            }
        }
    }
}

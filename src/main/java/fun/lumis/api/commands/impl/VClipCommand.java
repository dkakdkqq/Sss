package fun.lumis.api.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

import fun.lumis.api.commands.Command;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

public class VClipCommand extends Command {

    public VClipCommand() {
        super("vclip");
    }

    
    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(arg("Y", integer())
                .executes(context -> {
                    int y = context.getArgument("Y", Integer.class);
                    mc.player.setPosition(mc.player.getX(), mc.player.getY() + y, mc.player.getZ());
                    return SINGLE_SUCCESS;
                })
        );

        builder.then(literal("up")
                .executes(context -> {
                    clipToSafeBlock(true);
                    return SINGLE_SUCCESS;
                })
        );

        builder.then(literal("down")
                .executes(context -> {
                    clipToSafeBlock(false);
                    return SINGLE_SUCCESS;
                })
        );
    }

    
    private void clipToSafeBlock(boolean up) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        int startY = mc.player.getBlockY();
        int minY = mc.world.getBottomY();
        int maxY = mc.world.getTopYInclusive() - 2;
        int step = up ? 1 : -1;
        int from = up ? startY + 1 : startY - 1;
        int to = up ? maxY : minY;

        for (int y = from; up ? y <= to : y >= to; y += step) {
            if (!isSafeStandPosition(y)) {
                continue;
            }

            VoxelShape shape = mc.world.getBlockState(new BlockPos(mc.player.getBlockX(), y - 1, mc.player.getBlockZ())).getCollisionShape(mc.world, new BlockPos(mc.player.getBlockX(), y - 1, mc.player.getBlockZ()));
            double offsetY = shape.isEmpty() ? 0.0 : shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
            mc.player.setPosition(mc.player.getX(), y + offsetY, mc.player.getZ());
            return;
        }
    }

    
    private boolean isSafeStandPosition(int y) {
        BlockPos floorPos = new BlockPos(mc.player.getBlockX(), y - 1, mc.player.getBlockZ());
        BlockPos feetPos = floorPos.up();
        BlockPos headPos = feetPos.up();

        BlockState floorState = mc.world.getBlockState(floorPos);
        if (floorState.getCollisionShape(mc.world, floorPos).isEmpty()) {
            return false;
        }

        return mc.world.getBlockState(feetPos).getCollisionShape(mc.world, feetPos).isEmpty()
                && mc.world.getBlockState(headPos).getCollisionShape(mc.world, headPos).isEmpty();
    }
}

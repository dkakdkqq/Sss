package fun.lumis.client.modules.impl.player;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class Nuker extends Module {
    public static Nuker INSTANCE = new Nuker();

    private final FloatSetting radius = new FloatSetting("Дистанция", 3.0f, 1.0f, 5.0f, 1.0f);
    private final BooleanSetting breakAll = new BooleanSetting("Ломать все блоки", false);
    private final BooleanSetting swing = new BooleanSetting("Анимация руки", true);

    private final Set<String> targetBlocks = new HashSet<>();
    private BlockPos currentTargetBlock;

    public Nuker() {
        super("Nuker", "Автоматически ломает блоки в радиусе", ModuleCategory.PLAYER);
        addSettings(radius, breakAll, swing);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            resetBreaking();
            return;
        }

        if (!breakAll.isState() && targetBlocks.isEmpty()) {
            resetBreaking();
            return;
        }

        if (!isCurrentTargetValid()) {
            currentTargetBlock = findNewTarget();
        }

        if (currentTargetBlock != null) {
            breakCurrentTarget();
        }
    }

    private boolean isCurrentTargetValid() {
        return currentTargetBlock != null
                && isInRange(currentTargetBlock)
                && shouldBreak(currentTargetBlock);
    }

    private BlockPos findNewTarget() {
        int range = Math.round(radius.get());
        BlockPos playerPos = mc.player.getBlockPos();

        return BlockPos.stream(
                        playerPos.add(-range, 0, -range),
                        playerPos.add(range, range, range)
                )
                .map(BlockPos::toImmutable)
                .filter(this::isInRange)
                .filter(this::shouldBreak)
                .min(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(Vec3d.ofCenter(pos))))
                .orElse(null);
    }

    private boolean isInRange(BlockPos pos) {
        double maxDistance = radius.get();
        return mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= maxDistance * maxDistance;
    }

    private boolean shouldBreak(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state == null || state.isAir() || state.getHardness(mc.world, pos) < 0.0f) {
            return false;
        }

        if (breakAll.isState()) {
            return true;
        }

        String blockName = Registries.BLOCK.getId(state.getBlock()).getPath().toLowerCase();
        return targetBlocks.contains(blockName);
    }

    private void breakCurrentTarget() {
        if (currentTargetBlock == null || mc.player == null || mc.interactionManager == null) {
            return;
        }

        mc.interactionManager.attackBlock(currentTargetBlock, Direction.UP);
        mc.interactionManager.updateBlockBreakingProgress(currentTargetBlock, Direction.UP);

        if (swing.isState()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        if (mc.world.getBlockState(currentTargetBlock).isAir()) {
            resetBreaking();
        }
    }

    private void resetBreaking() {
        currentTargetBlock = null;
        if (mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    public void addBlock(String blockName) {
        targetBlocks.add(normalizeBlockName(blockName));
    }

    public void removeBlock(String blockName) {
        targetBlocks.remove(normalizeBlockName(blockName));
    }

    public void clearBlocks() {
        targetBlocks.clear();
        resetBreaking();
    }

    public boolean isTargetBlock(String blockName) {
        return targetBlocks.contains(normalizeBlockName(blockName));
    }

    public Set<String> getTargetBlocks() {
        return new HashSet<>(targetBlocks);
    }

    public static String normalizeBlockName(String blockName) {
        if (blockName == null) {
            return "";
        }
        String normalized = blockName.toLowerCase().trim();
        int namespaceSeparator = normalized.indexOf(':');
        return namespaceSeparator >= 0 ? normalized.substring(namespaceSeparator + 1) : normalized;
    }

    @Override
    public void onDisable() {
        resetBreaking();
        super.onDisable();
    }
}

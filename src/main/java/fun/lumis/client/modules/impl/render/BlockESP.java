package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.Priority;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockESP extends Module {

    public static BlockESP INSTANCE = new BlockESP();

    private static final float BOX_LINE_WIDTH = 2.0f;
    private static final float FILL_ALPHA = 0.18f;
    private static final float GREEN_R = 0.10f;
    private static final float GREEN_G = 1.00f;
    private static final float GREEN_B = 0.15f;
    private static final long SCAN_INTERVAL_MS = 50L;
    private static final int MAX_CHUNKS_PER_PASS = 2;

    private final FloatSetting distance = new FloatSetting("Дистанция", 60.0f, 10.0f, 120.0f, 1.0f);

    private final Set<String> trackedBlocks = ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, String> foundBlocks = new ConcurrentHashMap<>();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();

    private ChunkPos lastPlayerChunk;
    private int lastScanRadius = -1;
    private long lastScanTime;

    public BlockESP() {
        super("BlockESP", "Показывает выбранные блоки через стену", ModuleCategory.RENDER);
        addSettings(distance);
    }

    @Override
    public void onEnable() {
        resetScanState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        resetScanState();
        super.onDisable();
    }

    @EventLink(priority = Priority.HIGH)
    public void onRender3D(Event3DRender event) {
        if (mc.world == null || mc.player == null || trackedBlocks.isEmpty()) {
            return;
        }

        int scanRadius = getDistance();
        ChunkPos currentChunk = new ChunkPos(mc.player.getBlockPos());

        if (scanRadius != lastScanRadius) {
            resetScanState();
            lastScanRadius = scanRadius;
        }

        if (lastPlayerChunk == null || !lastPlayerChunk.equals(currentChunk)) {
            scannedChunks.clear();
            lastPlayerChunk = currentChunk;
        }

        long now = System.currentTimeMillis();
        if (now - lastScanTime >= SCAN_INTERVAL_MS) {
            scanNearbyBlocks(scanRadius);
            lastScanTime = now;
        }

        cleanupInvalidAndDistantBlocks(mc.player.getPos(), scanRadius);
        renderFoundBlocks(event.getMatrices());
    }

    private void scanNearbyBlocks(int scanRadius) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        BlockPos playerPos = mc.player.getBlockPos();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        int chunkRange = (scanRadius >> 4) + 2;

        List<ChunkPos> candidates = new ArrayList<>();
        for (int cx = -chunkRange; cx <= chunkRange; cx++) {
            for (int cz = -chunkRange; cz <= chunkRange; cz++) {
                ChunkPos chunkPos = new ChunkPos(playerChunkX + cx, playerChunkZ + cz);
                if (!scannedChunks.contains(chunkPos)) {
                    candidates.add(chunkPos);
                }
            }
        }

        candidates.sort((a, b) -> {
            long da = chunkDistanceSq(a, playerChunkX, playerChunkZ);
            long db = chunkDistanceSq(b, playerChunkX, playerChunkZ);
            return Long.compare(da, db);
        });

        int scannedThisPass = 0;
        for (ChunkPos chunkPos : candidates) {
            if (scannedThisPass >= MAX_CHUNKS_PER_PASS) {
                break;
            }

            WorldChunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                continue;
            }

            scanChunk(chunk, playerPos, scanRadius);
            scannedChunks.add(chunkPos);
            scannedThisPass++;
        }
    }

    private void scanChunk(WorldChunk chunk, BlockPos playerPos, int scanRadius) {
        int minX = chunk.getPos().getStartX();
        int minZ = chunk.getPos().getStartZ();
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        int minY = Math.max(mc.world.getBottomY(), playerPos.getY() - scanRadius);
        int maxY = Math.min(mc.world.getTopYInclusive(), playerPos.getY() + scanRadius);
        int radiusSq = scanRadius * scanRadius;

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutable.set(x, y, z);

                    if (mutable.getSquaredDistance(playerPos) > radiusSq) {
                        continue;
                    }

                    BlockState state = chunk.getBlockState(mutable);
                    if (state.isAir()) {
                        continue;
                    }

                    String blockName = Registries.BLOCK.getId(state.getBlock()).getPath().toLowerCase();
                    if (trackedBlocks.contains(blockName)) {
                        foundBlocks.put(mutable.toImmutable(), blockName);
                    }
                }
            }
        }
    }

    private void cleanupInvalidAndDistantBlocks(Vec3d playerPos, int renderDistance) {
        if (mc.world == null) {
            foundBlocks.clear();
            return;
        }

        int renderDistanceSq = renderDistance * renderDistance;
        foundBlocks.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            BlockState currentState = mc.world.getBlockState(pos);
            if (currentState.isAir()) {
                return true;
            }

            String currentBlockName = Registries.BLOCK.getId(currentState.getBlock()).getPath().toLowerCase();
            if (!trackedBlocks.contains(currentBlockName)) {
                return true;
            }

            return pos.getSquaredDistance(playerPos) > renderDistanceSq;
        });
    }

    private void renderFoundBlocks(MatrixStack matrices) {
        if (foundBlocks.isEmpty()) {
            return;
        }

        Vec3d camera = mc.gameRenderer.getCamera().getPos();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder fillBuffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (BlockPos pos : foundBlocks.keySet()) {
            addFilledBox(fillBuffer, matrix, pos, GREEN_R, GREEN_G, GREEN_B, FILL_ALPHA);
        }
        BufferRenderer.drawWithGlobalProgram(fillBuffer.end());

        RenderSystem.lineWidth(BOX_LINE_WIDTH);
        BufferBuilder lineBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (BlockPos pos : foundBlocks.keySet()) {
            addOutlinedBox(lineBuffer, matrix, pos, GREEN_R, GREEN_G, GREEN_B, 1.0f);
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private void addFilledBox(BufferBuilder buffer, Matrix4f matrix, BlockPos pos, float r, float g, float b, float a) {
        float minX = pos.getX();
        float minY = pos.getY();
        float minZ = pos.getZ();
        float maxX = minX + 1.0f;
        float maxY = minY + 1.0f;
        float maxZ = minZ + 1.0f;

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);

        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
    }

    private void addOutlinedBox(BufferBuilder buffer, Matrix4f matrix, BlockPos pos, float r, float g, float b, float a) {
        float minX = pos.getX();
        float minY = pos.getY();
        float minZ = pos.getZ();
        float maxX = minX + 1.0f;
        float maxY = minY + 1.0f;
        float maxZ = minZ + 1.0f;

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);

        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
    }

    public void addBlock(String blockName) {
        trackedBlocks.add(blockName.toLowerCase());
        scannedChunks.clear();
        foundBlocks.clear();
    }

    public void removeBlock(String blockName) {
        trackedBlocks.remove(blockName.toLowerCase());
        foundBlocks.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(blockName));
    }

    public void clearBlocks() {
        trackedBlocks.clear();
        resetScanState();
    }

    public Set<String> getTrackedBlocks() {
        return new HashSet<>(trackedBlocks);
    }

    public boolean isTracking(String blockName) {
        return trackedBlocks.contains(blockName.toLowerCase());
    }

    private int getDistance() {
        return Math.round(distance.get());
    }

    private long chunkDistanceSq(ChunkPos chunkPos, int playerChunkX, int playerChunkZ) {
        long dx = chunkPos.x - playerChunkX;
        long dz = chunkPos.z - playerChunkZ;
        return dx * dx + dz * dz;
    }

    private void resetScanState() {
        foundBlocks.clear();
        scannedChunks.clear();
        lastPlayerChunk = null;
        lastScanTime = 0L;
        lastScanRadius = -1;
    }
}

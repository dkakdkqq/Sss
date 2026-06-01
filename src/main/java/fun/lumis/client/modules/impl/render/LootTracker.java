package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.joml.Vector3f;
import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.Priority;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;

import java.util.ArrayList;
import java.util.List;

public class LootTracker extends Module {
    public static final LootTracker INSTANCE = new LootTracker();

    private static final float TAG_BOX_HEIGHT = 12.5f;
    private static final float TAG_PADDING = 5.0f;
    private static final float TAG_HUD_RADIUS = 1.1f;
    private static final int TAG_HUD_ALPHA = 204;

    private final BooleanSetting showSpawners = new BooleanSetting("Спавнеры", true);
    private final BooleanSetting showMinecarts = new BooleanSetting("Вагонетки", true);
    private final FloatSetting maxDistance = new FloatSetting("Макс. дистанция", 64f, 16f, 128f, 1f);

    private final Matrix4f lastProjectionMatrix = new Matrix4f();
    private final Quaternionf lastCameraRotation = new Quaternionf();
    private Vec3d lastCameraPos = Vec3d.ZERO;
    private float lastTickDelta;
    private boolean hasProjection;

    private final List<LootSource> cachedSources = new ArrayList<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_UPDATE_INTERVAL = 500;

    public LootTracker() {
        super("LootTracker", "Показывает залутанные спавнеры и вагонетки", ModuleCategory.RENDER);
        addSettings(showSpawners, showMinecarts, maxDistance);
    }

    @Override
    public void onDisable() {
        hasProjection = false;
        cachedSources.clear();
        super.onDisable();
    }

    @EventLink(priority = Priority.HIGH)
    public void onRender3D(Event3DRender event) {
        if (mc.player == null || mc.world == null) return;

        hasProjection = true;
        lastProjectionMatrix.set(event.getProjectionMatrix());
        lastCameraPos = event.getCamera().getPos();
        lastCameraRotation.set(event.getCamera().getRotation());
        lastTickDelta = event.getTickDelta();

        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate > CACHE_UPDATE_INTERVAL) {
            updateCache();
            lastCacheUpdate = now;
        }
    }

    @EventLink(priority = Priority.HIGH)
    public void onRender2D(EventRender.Default event) {
        if (!hasProjection || mc.world == null || mc.player == null) return;

        MatrixStack matrices = event.getContext().getMatrices();
        Font font = Fonts.getFont("sf_regular", 14);
        if (font == null) return;

        for (LootSource source : cachedSources) {
            if (mc.player.squaredDistanceTo(source.pos.getX(), source.pos.getY(), source.pos.getZ()) > maxDistance.getValue().floatValue() * maxDistance.getValue().floatValue()) {
                continue;
            }

            Vec3d screenPos = worldToScreen(new Vec3d(
                    source.pos.getX() + 0.5,
                    source.pos.getY() + 1.5,
                    source.pos.getZ() + 0.5
            ));

            if (screenPos == null) continue;

            drawLootTag(matrices, font, (float) screenPos.x, (float) screenPos.y, source);
        }
    }

    private void updateCache() {
        cachedSources.clear();

        if (showSpawners.isState()) {
            int renderDistance = mc.options.getViewDistance().getValue();
            ChunkPos playerChunk = mc.player.getChunkPos();

            for (int cx = -renderDistance; cx <= renderDistance; cx++) {
                for (int cz = -renderDistance; cz <= renderDistance; cz++) {
                    WorldChunk chunk = mc.world.getChunk(playerChunk.x + cx, playerChunk.z + cz);
                    if (chunk == null) continue;

                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        if (blockEntity instanceof MobSpawnerBlockEntity spawner) {
                            if (!hasSingleChestNearby(spawner.getPos())) continue;

                            int delay = getSpawnerDelay(spawner);
                            boolean isLooted = (delay > 0 && delay != 20) || isAreaExplored(spawner.getPos());

                            cachedSources.add(new LootSource(
                                    spawner.getPos(),
                                    LootType.SPAWNER,
                                    isLooted
                            ));
                        }
                    }
                }
            }
        }

        if (showMinecarts.isState()) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof ChestMinecartEntity minecart) {
                    boolean isLooted = isAreaExplored(minecart.getBlockPos());

                    cachedSources.add(new LootSource(
                            minecart.getBlockPos(),
                            LootType.MINECART,
                            isLooted
                    ));
                }
            }
        }
    }

    private void drawLootTag(MatrixStack matrices, Font font, float x, float y, LootSource source) {
        String typeText = source.type == LootType.SPAWNER ? "Спавнер" : "Вагонетка";
        String statusText = source.isLooted ? " [Залутано]" : " [Не залутано]";

        float typeWidth = font.getStringWidth(typeText);
        float statusWidth = font.getStringWidth(statusText);
        float totalWidth = typeWidth + statusWidth;

        float boxWidth = totalWidth + TAG_PADDING * 2;
        float boxHeight = TAG_BOX_HEIGHT;
        float tagX = x - boxWidth / 2.0f;
        float tagY = y - boxHeight / 2.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        drawDefaultTagPanel(matrices, tagX, tagY, boxWidth, boxHeight);

        float textX = tagX + TAG_PADDING;
        float textY = tagY + boxHeight / 2.0f - font.getHeight() * 0.10f;

        font.drawString(matrices, typeText, textX, textY, 0xFFFFFFFF);
        textX += typeWidth;

        int statusColor = source.isLooted ? 0xFFFF5555 : 0xFF55FF55;
        font.drawString(matrices, statusText, textX, textY, statusColor);

        RenderSystem.disableBlend();
    }

    private void drawDefaultTagPanel(MatrixStack matrices, float x, float y, float width, float height) {
        int themeColor = getStableThemeColor();
        RenderUtils.drawDefaultHudPanel(
                matrices, x, y, width, height,
                TAG_HUD_RADIUS, TAG_HUD_RADIUS,
                ColorUtils.rgba(50, 50, 50, TAG_HUD_ALPHA),
                ColorUtils.setAlphaColor(ColorUtils.darken(themeColor, 0.15f), TAG_HUD_ALPHA),
                ColorUtils.setAlphaColor(ColorUtils.darken(themeColor, 0.05f), TAG_HUD_ALPHA)
        );
    }

    private int getStableThemeColor() {
        if (Lumis.INSTANCE == null || Lumis.INSTANCE.themeStorage == null || Lumis.INSTANCE.themeStorage.getThemes() == null) {
            return ColorUtils.getThemeColor(0);
        }
        var theme = Lumis.INSTANCE.themeStorage.getThemes().getTheme();
        if (theme == null || theme.color == null || theme.color.length == 0) {
            return ColorUtils.getThemeColor(0);
        }
        return theme.color[0];
    }

    private boolean isAreaExplored(BlockPos pos) {
        int radius = 20;
        int airCount = 0;
        int checkCount = 0;

        for (int x = -radius; x <= radius; x += 4) {
            for (int y = -radius; y <= radius; y += 4) {
                for (int z = -radius; z <= radius; z += 4) {
                    BlockPos checkPos = pos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(checkPos);

                    checkCount++;
                    if (state.isOf(Blocks.AIR) || state.isOf(Blocks.CAVE_AIR)) {
                        airCount++;
                    }
                }
            }
        }

        return airCount > checkCount * 0.3;
    }

    private int getSpawnerDelay(MobSpawnerBlockEntity spawner) {
        try {
            NbtCompound tag = spawner.createNbtWithIdentifyingData(mc.world.getRegistryManager());
            return tag.contains("Delay") ? tag.getShort("Delay") : 20;
        } catch (Exception e) {
            return 20;
        }
    }

    private boolean hasSingleChestNearby(BlockPos spawnerPos) {
        int radius = 3;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = spawnerPos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(checkPos);
                    if (state.getBlock() instanceof ChestBlock) {
                        if (state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private Vec3d worldToScreen(Vec3d worldPos) {
        Vector3f relative = new Vector3f(
                (float) (worldPos.x - lastCameraPos.x),
                (float) (worldPos.y - lastCameraPos.y),
                (float) (worldPos.z - lastCameraPos.z)
        );

        Quaternionf invCameraRotation = new Quaternionf(lastCameraRotation).conjugate();
        relative.rotate(invCameraRotation);

        Vector4f clip = new Vector4f(relative.x, relative.y, relative.z, 1.0f);
        lastProjectionMatrix.transform(clip);

        float w = clip.w;
        if (w <= 0.00001f) return null;

        float ndcX = clip.x / w;
        float ndcY = clip.y / w;

        float screenX = (ndcX * 0.5f + 0.5f) * mc.getWindow().getScaledWidth();
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * mc.getWindow().getScaledHeight();

        if (Float.isNaN(screenX) || Float.isNaN(screenY)) return null;
        if (Float.isInfinite(screenX) || Float.isInfinite(screenY)) return null;

        return new Vec3d(screenX, screenY, 0);
    }

    private enum LootType {
        SPAWNER,
        MINECART
    }

    private record LootSource(BlockPos pos, LootType type, boolean isLooted) {}
}

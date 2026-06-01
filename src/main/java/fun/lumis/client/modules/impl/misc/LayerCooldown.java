package fun.lumis.client.modules.impl.misc;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.Priority;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.Module;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LayerCooldown extends Module {

    public static LayerCooldown INSTANCE = new LayerCooldown();

    private static final long DELAYED_SCAN_MS = 250L;
    private static final int SEARCH_RADIUS = 4;
    private static final int SEARCH_HEIGHT = 4;
    private static final int MAX_TIMERS = 100;
    private static final float TIMER_SECONDS = 19.5f;
    private static final float MAX_DISTANCE = 96.0f;
    private static final double TIMER_Y_OFFSET = 0.6;
    private static final ItemStack LAYER_ICON = new ItemStack(Items.DRIED_KELP);

    private final Matrix4f lastProjectionMatrix = new Matrix4f();
    private final Quaternionf lastCameraRotation = new Quaternionf();
    private Vec3d lastCameraPos = Vec3d.ZERO;
    private boolean hasProjection;

    private final List<LayerTimer> timers = new ArrayList<>();
    private final List<PendingScan> pendingScans = new ArrayList<>();

    public LayerCooldown() {
        super("LayerCooldown", "Показывает таймер возле поставленного пласта", ModuleCategory.MISC);
    }

    @Override
    public void onDisable() {
        timers.clear();
        pendingScans.clear();
        hasProjection = false;
        super.onDisable();
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (event.getType() != EventPacket.Type.RECEIVE || mc.world == null || mc.player == null) return;
        if (!(event.getPacket() instanceof PlaySoundS2CPacket packet)) return;

        String sound = getSoundPath(packet);
        if (sound == null) return;

        Vec3d soundPos = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
        BlockPos blockPos = BlockPos.ofFloored(soundPos);

        if ("block.piston.extend".equals(sound)) {
            addTimer(blockPos, soundPos);
            return;
        }

        if (isDelayedTrapSound(sound)) {
            pendingScans.add(new PendingScan(blockPos, System.currentTimeMillis() + DELAYED_SCAN_MS));
        }
    }

    @EventLink(priority = Priority.HIGH)
    public void onRender3D(Event3DRender event) {
        if (mc.world == null || mc.player == null) return;

        hasProjection = true;
        lastProjectionMatrix.set(event.getProjectionMatrix());
        lastCameraRotation.set(event.getCamera().getRotation());
        lastCameraPos = event.getCamera().getPos();

        processPendingScans();
    }

    @EventLink(priority = Priority.HIGH)
    public void onRender2D(EventRender.Default event) {
        if (!hasProjection || mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        timers.removeIf(timer -> timer.endTime <= now);
        while (timers.size() > MAX_TIMERS) {
            timers.remove(0);
        }

        if (timers.isEmpty()) return;

        MatrixStack matrices = event.getContext().getMatrices();
        Font font = Fonts.getFont("sf_regular", 13);
        if (font == null) return;

        float maxDistSq = MAX_DISTANCE * MAX_DISTANCE;
        for (int i = 0; i < timers.size(); i++) {
            LayerTimer timer = timers.get(i);
            if (mc.player.squaredDistanceTo(timer.pos) > maxDistSq) continue;

            Vec3d screen = worldToScreen(timer.pos);
            if (screen == null) continue;

            float seconds = Math.max(0.0f, (timer.endTime - now) / 1000.0f);
            drawTimer(event.getContext(), matrices, font, (float) screen.x, (float) screen.y, seconds);
        }
    }

    private void processPendingScans() {
        if (pendingScans.isEmpty() || mc.world == null) return;

        long now = System.currentTimeMillis();
        Iterator<PendingScan> iterator = pendingScans.iterator();
        while (iterator.hasNext()) {
            PendingScan scan = iterator.next();
            if (scan.runAt > now) continue;

            BlockPos found = findLayerLikeBlock(scan.center);
            Vec3d pos = found == null
                    ? Vec3d.ofCenter(scan.center)
                    : new Vec3d(found.getX() + 0.5, found.getY() + 0.65, found.getZ() + 0.5);
            addTimer(found == null ? scan.center : found, pos);
            iterator.remove();
        }
    }

    private BlockPos findLayerLikeBlock(BlockPos center) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_HEIGHT; y <= SEARCH_HEIGHT; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    if (!isLayerLikeBlock(state)) continue;

                    double distance = pos.getSquaredDistance(center);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }

        return best;
    }

    private boolean isLayerLikeBlock(BlockState state) {
        if (state == null || state.isAir()) return false;

        Block block = state.getBlock();
        return block == Blocks.PISTON
                || block == Blocks.STICKY_PISTON
                || block == Blocks.MOVING_PISTON
                || block == Blocks.DRIED_KELP_BLOCK
                || block == Blocks.ANVIL
                || block == Blocks.CHIPPED_ANVIL
                || block == Blocks.DAMAGED_ANVIL;
    }

    private void addTimer(BlockPos blockPos, Vec3d renderPos) {
        long endTime = System.currentTimeMillis() + (long) (TIMER_SECONDS * 1000.0f);

        for (int i = 0; i < timers.size(); i++) {
            LayerTimer timer = timers.get(i);
            if (timer.blockPos.getSquaredDistance(blockPos) <= 2.25) {
                timers.set(i, new LayerTimer(blockPos, renderPos.add(0.0, TIMER_Y_OFFSET, 0.0), endTime));
                return;
            }
        }

        timers.add(new LayerTimer(blockPos, renderPos.add(0.0, TIMER_Y_OFFSET, 0.0), endTime));
    }

    private boolean isDelayedTrapSound(String sound) {
        return "block.anvil.place".equals(sound)
                || "entity.zombie_horse.death".equals(sound)
                || "entity.ender_dragon.growl".equals(sound);
    }

    private String getSoundPath(PlaySoundS2CPacket packet) {
        try {
            return Registries.SOUND_EVENT.getId(packet.getSound().value()).getPath();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void drawTimer(DrawContext context, MatrixStack matrices, Font font, float x, float y, float seconds) {
        String text = formatOneDecimal(seconds) + "с";
        float textWidth = font.getStringWidth(text);
        float iconSize = 10.0f;
        float iconScale = 0.62f;
        float gap = 3.0f;
        float boxWidth = iconSize + gap + textWidth + 8.0f;
        float boxHeight = 12.5f;
        float boxX = x - boxWidth * 0.5f;
        float boxY = y - boxHeight * 0.5f;
        int themeColor = ColorUtils.getThemeColor();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderUtils.drawDefaultHudThemedPanel(matrices, boxX, boxY, boxWidth, boxHeight, 2.0f, 3.0f, themeColor);
        drawItemIcon(context, matrices, boxX + 4.0f, boxY + 1.25f, iconScale);
        font.drawString(matrices, text, boxX + 4.0f + iconSize + gap, boxY + 4.55f, 0xFFFFFFFF);
        RenderSystem.disableBlend();
    }

    private String formatOneDecimal(float value) {
        int scaled = Math.round(value * 10.0f);
        return (scaled / 10) + "." + Math.abs(scaled % 10);
    }

    private void drawItemIcon(DrawContext context, MatrixStack matrices, float x, float y, float scale) {
        if (context == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        matrices.push();
        matrices.translate(x, y, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        context.drawItem(LAYER_ICON, 0, 0);
        matrices.pop();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private Vec3d worldToScreen(Vec3d worldPos) {
        if (mc == null || mc.getWindow() == null) return null;

        Vector3f relative = new Vector3f(
                (float) (worldPos.x - lastCameraPos.x),
                (float) (worldPos.y - lastCameraPos.y),
                (float) (worldPos.z - lastCameraPos.z)
        );

        Quaternionf invCameraRot = new Quaternionf(lastCameraRotation).conjugate();
        relative.rotate(invCameraRot);

        Vector4f clip = new Vector4f(relative.x, relative.y, relative.z, 1.0f);
        lastProjectionMatrix.transform(clip);

        float w = clip.w;
        if (w <= 0.00001f) return null;

        float ndcX = clip.x / w;
        float ndcY = clip.y / w;
        float ndcZ = clip.z / w;

        float screenX = (ndcX * 0.5f + 0.5f) * mc.getWindow().getScaledWidth();
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * mc.getWindow().getScaledHeight();

        if (Float.isNaN(screenX) || Float.isNaN(screenY) || Float.isInfinite(screenX) || Float.isInfinite(screenY)) {
            return null;
        }
        if (screenX < -400 || screenY < -400
                || screenX > mc.getWindow().getScaledWidth() + 400
                || screenY > mc.getWindow().getScaledHeight() + 400) {
            return null;
        }

        return new Vec3d(screenX, screenY, ndcZ);
    }

    private record LayerTimer(BlockPos blockPos, Vec3d pos, long endTime) {
    }

    private record PendingScan(BlockPos center, long runAt) {
    }
}

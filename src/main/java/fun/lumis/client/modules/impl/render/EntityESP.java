package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.WaterAnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.Priority;
import fun.lumis.api.events.implement.Event3DRender;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.ShaderUtils;
import fun.lumis.api.utils.render.font.ReplaceSymbols;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.misc.NameProtect;
import fun.lumis.client.modules.impl.misc.ScoreboardHP;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class EntityESP extends Module {

    public static EntityESP INSTANCE = new EntityESP();
    private static final float TAG_FROM_ENTITY_GAP = 0.0f;
    private static final int TAG_FONT_SIZE = 13;
    private static final int TAG_TEXT_COLOR = 0xFFFFFFFF;
    private static final int TAG_HEALTH_COLOR = 0xFFFF5555;
    private static final int TAG_FRIEND_COLOR = 0xFF55FF55;
    private static final float TAG_HUD_RADIUS = 1.1f;
    private static final int TAG_HUD_ALPHA = 204;
    private static final float ARMOR_CELL_SIZE = 8.4f;
    private static final float ARMOR_ITEM_SCALE = 0.46f;
    private static final float ARMOR_CELL_GAP = 1.0f;
    private static final float PLAYER_HEAD_SIZE = 7.5f;
    private static final float PLAYER_HEAD_GAP = 3.0f;
    private static final float BOX_LINE_WIDTH = 1.5f;
    private static final float FILL_ALPHA = 0.23f;
    private static final float EPSILON = 0.001f;
    private static final long DONATE_CACHE_TTL_MS = 1000L;
    private static final long DONATE_CACHE_CLEANUP_MS = 2000L;
    private static final int MAX_ITEM_TAGS_PER_FRAME = 48;

    private final ListSetting elements = new ListSetting("Элементы",
            new BooleanSetting("Теги", true),
            new BooleanSetting("Броня", true)
    );
    private final BooleanSetting show3DBox = new BooleanSetting("Боксы", true);
    private final BooleanSetting boxFilled = new BooleanSetting("Заполнить бокс", true);
    private final ModeSetting boxFillMode = new ModeSetting("Мод заливки", "Обычный", "Обычный", "Волны", "Нитки");
    private final FloatSetting waveSpeed = new FloatSetting("Скорость волн", 1.2f, 0.1f, 5.0f, 0.1f)
            .visible(() -> boxFillMode.is("Волны"));
    private final FloatSetting waveScale = new FloatSetting("Размер волн", 1.0f, 1.0f, 3.0f, 0.1f)
            .visible(() -> boxFillMode.is("Волны"));
    private final FloatSetting lineSpeed = new FloatSetting("Скорость линий", 1.4f, 0.1f, 5.0f, 0.1f)
            .visible(() -> boxFillMode.getIndex() == 2);
    private final FloatSetting lineJitter = new FloatSetting("Прыжки линий", 0.55f, 0.0f, 1.5f, 0.01f)
            .visible(() -> boxFillMode.getIndex() == 2);
    private final FloatSetting outline = new FloatSetting("Обводка", 1.1f, 0.1f, 5.0f, 0.1f)
            .visible(this::isPostBoxMode);
    private final FloatSetting glow = new FloatSetting("Свечение", 1.0f, 0.0f, 5.0f, 0.1f)
            .visible(this::isPostBoxMode);
    private final FloatSetting fill = new FloatSetting("Сила заливки", 0.6f, 0.0f, 1.0f, 0.01f)
            .visible(this::isPostBoxMode);
    private final FloatSetting alpha = new FloatSetting("Прозрачность", 1.0f, 0.0f, 4.0f, 0.01f)
            .visible(this::isPostBoxMode);

    private final BooleanSetting hurtTint = new BooleanSetting("Краснеть при ударе", true);
    private final Matrix4f lastProjectionMatrix = new Matrix4f();
    private final Quaternionf lastCameraRotation = new Quaternionf();
    private final Quaternionf lastInverseCameraRotation = new Quaternionf();
    private Vec3d lastCameraPos = Vec3d.ZERO;
    private float lastTickDelta;
    private int lastScaledWidth;
    private int lastScaledHeight;
    private boolean hasProjection;
    private Framebuffer maskBuffer;
    private final List<Framebuffer> bloomBuffers = new ArrayList<>();
    private final Map<UUID, DonateCache> donateCache = new HashMap<>();
    private final Map<Integer, Float> entityHurtTintProgress = new HashMap<>();
    private long nextDonateCacheCleanupAt;
    private int maskWidth = -1;
    private int maskHeight = -1;
    private boolean hasShaderMask;
    private final Vector3f projectionScratch = new Vector3f();
    private final Vector4f clipScratch = new Vector4f();
    private final ProjectedPoint projectedPoint = new ProjectedPoint();
    private final ItemStack[] armorStacksScratch = new ItemStack[6];
    private final boolean[] armorHandScratch = new boolean[6];
    private int frameThemeColor = 0xFFFFFFFF;
    private final BooleanSetting targetPlayers = new BooleanSetting("Игроки", true);
    private final BooleanSetting targetMobs = new BooleanSetting("Мобы", true);
    private final BooleanSetting targetAnimals = new BooleanSetting("Животные", true);
    private final BooleanSetting targetItems = new BooleanSetting("Предметы", true);
    private final ListSetting targets = new ListSetting("Отображать",
            targetPlayers,
            targetMobs,
            targetAnimals,
            targetItems
    );

    public EntityESP() {
        super("EntityESP", "Показывает игроков через стену", ModuleCategory.RENDER);
        addSettings(targets, elements);
        addSettings(show3DBox, boxFilled, hurtTint);
    }

    @Override
    public void onDisable() {
        hasProjection = false;
        hasShaderMask = false;
        donateCache.clear();
        entityHurtTintProgress.clear();
        nextDonateCacheCleanupAt = 0L;
        if (maskBuffer != null) {
            maskBuffer.delete();
            maskBuffer = null;
        }
        for (Framebuffer fb : bloomBuffers) {
            fb.delete();
        }
        bloomBuffers.clear();
        super.onDisable();
    }

    @EventLink(priority = Priority.HIGH)
    public void onRender3D(Event3DRender event) {
        hasProjection = true;
        lastProjectionMatrix.set(event.getProjectionMatrix());
        lastCameraPos = event.getCamera().getPos();
        lastCameraRotation.set(event.getCamera().getRotation());
        lastInverseCameraRotation.set(lastCameraRotation).conjugate();
        lastTickDelta = event.getTickDelta();
        lastScaledWidth = mc.getWindow().getScaledWidth();
        lastScaledHeight = mc.getWindow().getScaledHeight();
        frameThemeColor = getStableThemeColor();

        hasShaderMask = false;
        if (!show3DBox.isState() || mc.world == null || mc.player == null) return;
        MatrixStack matrices = event.getMatrices();
        float tickDelta = event.getTickDelta();
        boolean postMode = isPostBoxMode();
        boolean threadMode = isThreadMode();

        if (postMode) {
            ensureMaskBuffer();
            if (maskBuffer != null) {
                maskBuffer.setClearColor(0f, 0f, 0f, 0f);
                maskBuffer.clear();
                copyMainDepthToMask();
                maskBuffer.beginWrite(false);
                RenderSystem.disableBlend();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            }
        }

        for (Entity entity : mc.world.getEntities()) {
            if (!shouldProcess3DEntity(entity)) continue;
            if (postMode && maskBuffer != null) {
                drawPlayerMaskBox(matrices, entity, tickDelta);
                hasShaderMask = true;
            } else {
                render3DBox(matrices, entity, tickDelta);
            }
        }

        if (postMode && maskBuffer != null) {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            mc.getFramebuffer().beginWrite(true);
            if (show3DBox.isState()) {
                renderShaderBoxesWorldPass();
            }
        }

        if (threadMode) {
            for (Entity entity : mc.world.getEntities()) {
                if (!shouldProcess3DEntity(entity)) continue;
                renderThreadWeb(matrices, entity, tickDelta);
            }
        }
    }

    @EventLink(priority = Priority.HIGH)
    public void onRender2D(EventRender.Default event) {
        if (!hasProjection || mc.world == null || mc.player == null) return;
        frameThemeColor = getStableThemeColor();
        boolean tagsEnabled = !elements.getSettings().isEmpty() && elements.getSettings().get(0).isState();
        boolean armorEnabled = elements.getSettings().size() > 1 && elements.getSettings().get(1).isState();
        if (!tagsEnabled && !armorEnabled) return;

        Font font = tagsEnabled ? Fonts.getFont("sf_regular", TAG_FONT_SIZE) : null;
        int renderedItemTags = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player) {
                if (!shouldProcess2DPlayer(player)) continue;

                Box interpolatedBox = getInterpolatedBox(player, lastTickDelta);
                ScreenRect rect = projectBox(interpolatedBox);
                if (rect == null) continue;

                if (tagsEnabled && font != null) {
                    drawTag(event, player, rect, font);
                }
                if (armorEnabled) {
                    drawArmor(event, player, rect, tagsEnabled);
                }
                continue;
            }

            if (!tagsEnabled || font == null) {
                continue;
            }

            if (entity instanceof ItemEntity itemEntity) {
                if (!shouldProcessItem2D(itemEntity) || renderedItemTags >= MAX_ITEM_TAGS_PER_FRAME) {
                    continue;
                }

                if (projectEntityAnchor(itemEntity, itemEntity.getHeight() + 0.25, projectedPoint)) {
                    drawDroppedItemTag(event, itemEntity, projectedPoint.x, projectedPoint.y, font);
                    renderedItemTags++;
                }
                continue;
            }

            if (!(entity instanceof LivingEntity livingEntity) || !shouldProcessLiving2D(livingEntity)) {
                continue;
            }

            Box interpolatedBox = getInterpolatedBox(livingEntity, lastTickDelta);
            ScreenRect rect = projectBox(interpolatedBox);
            if (rect == null) continue;

            drawLivingTag(event, livingEntity, rect, font);
        }
    }

    private void drawTag(EventRender.Default event, PlayerEntity player, ScreenRect rect, Font font) {
        MatrixStack matrices = event.getContext().getMatrices();
        List<DonateSegment> donateSegments = getDonateSegmentsFromTab(player);
        String nameText = getProtectedName(player.getNameForScoreboard());
        float hp = ScoreboardHP.getHealthWithAbsorption(player);
        String leftBracket = "";
        String hpText = Math.round(hp) + " hp";
        String rightBracket = "";
        boolean isFriend = Lumis.INSTANCE.friendStorage != null
                && Lumis.INSTANCE.friendStorage.isFriend(player.getName().getString());
        String friendSuffix = isFriend ? " [F]" : "";

        float donateWidth = 0.0f;
        for (DonateSegment segment : donateSegments) {
            donateWidth += font.getStringWidth(segment.text());
        }
        float totalWidth = (donateWidth
                + font.getStringWidth(nameText)
                + font.getStringWidth(leftBracket)
                + font.getStringWidth(hpText)
                + font.getStringWidth(rightBracket)
                + font.getStringWidth(friendSuffix)
                + PLAYER_HEAD_SIZE
                + PLAYER_HEAD_GAP) + 2;
        float boxHeight = TAG_FONT_SIZE + 3.0f;
        float x = rect.centerX() - totalWidth * 0.5f;
        float y = getTagTopY(rect, boxHeight);

        drawDefaultTagPanel(matrices, x - 1.0f, y - 0.5f, totalWidth + 2.0f, boxHeight - 4);

        float headY = y + 1.7f;
        RenderUtils.drawPlayerHead(matrices, player.getUuid(), x + 1.0f, headY, PLAYER_HEAD_SIZE, 1.0f, 1.0f, 0.0f);

        float drawX = x + 1.5f + PLAYER_HEAD_SIZE + PLAYER_HEAD_GAP;
        for (DonateSegment segment : donateSegments) {
            font.drawString(matrices, segment.text(), drawX, y + 4, segment.color());
            drawX += font.getStringWidth(segment.text());
        }
        font.drawString(matrices, nameText, drawX, y + 4, TAG_TEXT_COLOR);
        drawX += font.getStringWidth(nameText);
        font.drawString(matrices, leftBracket, drawX, y + 4, TAG_TEXT_COLOR);
        drawX += font.getStringWidth(leftBracket);
        font.drawString(matrices, hpText, drawX, y + 4, TAG_HEALTH_COLOR);
        drawX += font.getStringWidth(hpText);
        font.drawString(matrices, rightBracket, drawX, y + 4, TAG_TEXT_COLOR);
        drawX += font.getStringWidth(rightBracket);
        if (isFriend) font.drawString(matrices, friendSuffix, drawX, y + 4, TAG_FRIEND_COLOR);
    }

    private void drawArmor(EventRender.Default event, PlayerEntity player, ScreenRect rect, boolean tagsEnabled) {
        MatrixStack matrices = event.getContext().getMatrices();
        int count = 0;
        ItemStack offHand = player.getOffHandStack();
        if (!offHand.isEmpty()) {
            armorStacksScratch[count] = offHand;
            armorHandScratch[count++] = true;
        }
        for (ItemStack stack : player.getArmorItems()) {
            if (!stack.isEmpty()) {
                armorStacksScratch[count] = stack;
                armorHandScratch[count++] = false;
            }
        }
        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isEmpty()) {
            armorStacksScratch[count] = mainHand;
            armorHandScratch[count++] = true;
        }
        if (count == 0) return;

        float step = ARMOR_CELL_SIZE + ARMOR_CELL_GAP;
        float rowWidth = count * ARMOR_CELL_SIZE + Math.max(0, count - 1) * ARMOR_CELL_GAP;
        float x = rect.centerX() - rowWidth * 0.5f;
        float y = tagsEnabled
                ? getTagTopY(rect, TAG_FONT_SIZE + 1.0f) - 13.0f
                : rect.minY() - 13.0f;

        for (int i = 0; i < count; i++) {
            float drawX = x + i * step;
            float drawY = y;
            drawDefaultTagPanel(matrices, drawX, drawY, ARMOR_CELL_SIZE, ARMOR_CELL_SIZE);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        for (int i = 0; i < count; i++) {
            float drawX = x + i * step;
            float drawY = y;
            int stackIndex = count - 1 - i;
            ItemStack stack = armorStacksScratch[stackIndex];
            boolean handStack = armorHandScratch[stackIndex];

            matrices.push();
            float itemSize = 16.0f * ARMOR_ITEM_SCALE;
            float itemX = drawX + (ARMOR_CELL_SIZE - itemSize) * 0.5f;
            float itemY = drawY + (ARMOR_CELL_SIZE - itemSize) * 0.5f;
            matrices.translate(itemX, itemY, 0.0f);
            matrices.scale(ARMOR_ITEM_SCALE, ARMOR_ITEM_SCALE, 1.0f);
            event.getContext().drawItem(stack, 0, 0);
            if (!handStack) {
                event.getContext().drawStackOverlay(mc.textRenderer, stack, 0, 0, null);
            }
            matrices.pop();
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        for (int i = 0; i < count; i++) {
            armorStacksScratch[i] = ItemStack.EMPTY;
            armorHandScratch[i] = false;
        }
    }

    private void drawLivingTag(EventRender.Default event, LivingEntity entity, ScreenRect rect, Font font) {
        MatrixStack matrices = event.getContext().getMatrices();
        String nameText = entity instanceof PlayerEntity player
                ? getProtectedName(player.getDisplayName().getString())
                : entity.getDisplayName().getString();
        String hpText = Math.round(ScoreboardHP.getHealthWithAbsorption(entity)) + " hp";
        float totalWidth = font.getStringWidth(nameText) + font.getStringWidth(" ") + font.getStringWidth(hpText);
        float boxHeight = TAG_FONT_SIZE + 1.0f;
        float x = rect.centerX() - totalWidth * 0.5f;
        float y = getTagTopY(rect, boxHeight);

        drawDefaultTagPanel(matrices, x - 1.0f, y - 0.5f, totalWidth + 2.0f, boxHeight - 4);
        font.drawString(matrices, nameText, x, y + 3, TAG_TEXT_COLOR);
        font.drawString(matrices, hpText, x + font.getStringWidth(nameText) + font.getStringWidth(" "), y + 3, TAG_HEALTH_COLOR);
    }

    private void drawDroppedItemTag(EventRender.Default event, ItemEntity itemEntity, float anchorX, float anchorY, Font font) {
        MatrixStack matrices = event.getContext().getMatrices();
        ItemStack stack = itemEntity.getStack();
        String countText = stack.getCount() + "x";
        List<DonateSegment> itemSegments = getStyledTextSegments(stack.getName(), getDroppedItemTextColor(stack));
        int countColor = ColorUtils.rgba(155, 155, 155, 255);
        float itemNameWidth = 0.0f;
        for (DonateSegment segment : itemSegments) {
            itemNameWidth += font.getStringWidth(segment.text());
        }
        float spaceWidth = font.getStringWidth(" ");
        float totalWidth = itemNameWidth + spaceWidth + font.getStringWidth(countText);
        float boxHeight = TAG_FONT_SIZE + 1.0f;
        float x = anchorX - totalWidth * 0.5f;
        float y = anchorY - boxHeight - 2.0f;

        drawDefaultTagPanel(matrices, x - 2.0f, y - 0.5f, totalWidth + 4.0f, boxHeight - 3.0f);
        float drawX = x;
        for (DonateSegment segment : itemSegments) {
            font.drawString(matrices, segment.text(), drawX, y + 3.5f, segment.color());
            drawX += font.getStringWidth(segment.text());
        }
        font.drawString(matrices, countText, drawX + spaceWidth, y + 3.5f, countColor);
    }

    private int getMinecraftItemNameColor(ItemStack stack) {
        Text name = stack.getName();
        if (name != null) {
            int[] discoveredColor = {0};
            boolean[] found = {false};
            name.visit((style, string) -> {
                if (!found[0] && style != null && style.getColor() != null) {
                    discoveredColor[0] = 0xFF000000 | style.getColor().getRgb();
                    found[0] = true;
                }
                return found[0] ? Optional.of(string) : Optional.empty();
            }, Style.EMPTY);
            if (found[0]) return discoveredColor[0];
        }

        return switch (stack.getRarity()) {
            case UNCOMMON -> ColorUtils.rgba(255, 255, 85, 255);
            case RARE -> ColorUtils.rgba(85, 255, 255, 255);
            case EPIC -> ColorUtils.rgba(255, 85, 255, 255);
            case COMMON -> TAG_TEXT_COLOR;
        };
    }

    private int getDroppedItemTextColor(ItemStack stack) {
        return getMinecraftItemNameColor(stack);
    }

    private boolean isNetheriteItem(Item item) {
        return Registries.ITEM.getId(item).getPath().contains("netherite");
    }

    private void drawDefaultTagPanel(MatrixStack matrices, float x, float y, float width, float height) {
        int themeColor = frameThemeColor;
        RenderUtils.drawDefaultHudPanel(
                matrices, x, y, width, height,
                TAG_HUD_RADIUS, TAG_HUD_RADIUS,
                ColorUtils.rgba(50, 50, 50, TAG_HUD_ALPHA),
                ColorUtils.setAlphaColor(ColorUtils.darken(themeColor, 0.15f), TAG_HUD_ALPHA),
                ColorUtils.setAlphaColor(ColorUtils.darken(themeColor, 0.05f), TAG_HUD_ALPHA)
        );
    }

    public boolean shouldHideVanillaTags() {
        return isEnable() && !elements.getSettings().isEmpty() && elements.getSettings().get(0).isState();
    }

    private float getTagTopY(ScreenRect rect, float tagHeight) {
        return rect.minY() - tagHeight - TAG_FROM_ENTITY_GAP;
    }

    private String[] getNameVariants(PlayerEntity player) {
        String profileName = player.getGameProfile() != null ? player.getGameProfile().getName() : "";
        String scoreboardName = player.getNameForScoreboard();
        String protectedScoreboardName = getProtectedName(scoreboardName);
        String protectedProfileName = getProtectedName(profileName);
        String protectedPlainName = getProtectedName(player.getName().getString());
        return new String[]{
                player.getName().getString(),
                protectedPlainName,
                scoreboardName,
                protectedScoreboardName,
                profileName,
                protectedProfileName
        };
    }

    private String getProtectedName(String input) {
        NameProtect nameProtect = ModuleClass.INSTANCE != null ? ModuleClass.INSTANCE.nameProtect : null;
        if (nameProtect == null || !nameProtect.isEnable()) {
            return input;
        }
        return nameProtect.patch(input);
    }

    private int findAnyNameIndex(String text, String[] names) {
        if (text == null || text.isEmpty() || names == null) return -1;
        int best = -1;
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            int idx = indexOfIgnoreCase(text, name);
            if (idx >= 0 && (best == -1 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private int indexOfIgnoreCase(String text, String search) {
        if (text == null || search == null || search.isEmpty()) return -1;
        int limit = text.length() - search.length();
        for (int i = 0; i <= limit; i++) {
            if (text.regionMatches(true, i, search, 0, search.length())) {
                return i;
            }
        }
        return -1;
    }

    private void trimSegmentsToLength(List<DonateSegment> segments, int maxLength) {
        int remaining = Math.max(0, maxLength);
        List<DonateSegment> trimmed = new ArrayList<>();
        for (DonateSegment seg : segments) {
            if (remaining <= 0) break;
            String text = seg.text();
            if (text.length() <= remaining) {
                trimmed.add(seg);
                remaining -= text.length();
            } else {
                trimmed.add(new DonateSegment(text.substring(0, remaining), seg.color()));
                remaining = 0;
            }
        }
        segments.clear();
        segments.addAll(trimmed);
    }

    private List<DonateSegment> getDonateSegmentsFromTab(PlayerEntity player) {
        long now = System.currentTimeMillis();
        DonateCache cache = donateCache.computeIfAbsent(player.getUuid(), uuid -> new DonateCache());
        if (now < cache.nextUpdateAt) {
            return cache.segments;
        }

        List<DonateSegment> segments = new ArrayList<>();
        if (mc.getNetworkHandler() == null) {
            cache.segments = Collections.emptyList();
            cache.nextUpdateAt = now + DONATE_CACHE_TTL_MS;
            return cache.segments;
        }

        var entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        if (entry == null) {
            cache.segments = Collections.emptyList();
            cache.nextUpdateAt = now + DONATE_CACHE_TTL_MS;
            return cache.segments;
        }

        Text displayName = entry.getDisplayName();
        if (displayName == null) displayName = player.getDisplayName();
        if (displayName == null) {
            cache.segments = Collections.emptyList();
            cache.nextUpdateAt = now + DONATE_CACHE_TTL_MS;
            return cache.segments;
        }

        String[] nameVariants = getNameVariants(player);
        boolean[] foundName = {false};

        displayName.visit((style, string) -> {
            if (foundName[0] || string == null || string.isEmpty()) {
                return Optional.empty();
            }

            String part = string.replace('\n', ' ').replace('\r', ' ');
            int nameIndex = findAnyNameIndex(part, nameVariants);
            String donatePart = nameIndex >= 0 ? part.substring(0, nameIndex) : part;

            if (!donatePart.isEmpty()) {
                int baseColor = style.getColor() != null ? style.getColor().getRgb() : 0xFFFFFF;
                appendColoredSegments(segments, donatePart, baseColor);
            }

            if (nameIndex >= 0) {
                foundName[0] = true;
            }
            return Optional.empty();
        }, Style.EMPTY);

        if (!foundName[0]) {
            segments.clear();
            Team team = player.getScoreboardTeam();
            if (team != null && team.getPrefix() != null) {
                appendTextSegments(segments, team.getPrefix());
            }
        }

        if (segments.isEmpty()) {
            cache.segments = Collections.emptyList();
            cache.nextUpdateAt = now + DONATE_CACHE_TTL_MS;
            cleanupDonateCache(now);
            return cache.segments;
        }

        StringBuilder combined = new StringBuilder();
        for (DonateSegment seg : segments) {
            combined.append(seg.text());
        }
        int donateNameIndex = findAnyNameIndex(combined.toString(), nameVariants);
        if (donateNameIndex >= 0) {
            if (donateNameIndex == 0) {
                cache.segments = Collections.emptyList();
                cache.nextUpdateAt = now + DONATE_CACHE_TTL_MS;
                cleanupDonateCache(now);
                return cache.segments;
            }
            trimSegmentsToLength(segments, donateNameIndex);
        }

        if (segments.isEmpty()) {
            cache.segments = Collections.emptyList();
            cache.nextUpdateAt = now + DONATE_CACHE_TTL_MS;
            cleanupDonateCache(now);
            return cache.segments;
        }

        StringBuilder textCheck = new StringBuilder();
        for (DonateSegment seg : segments) {
            textCheck.append(seg.text());
        }
        if (textCheck.toString().trim().isEmpty()) {
            cache.segments = Collections.emptyList();
            cache.nextUpdateAt = now + DONATE_CACHE_TTL_MS;
            cleanupDonateCache(now);
            return cache.segments;
        }

        DonateSegment last = segments.get(segments.size() - 1);
        if (!last.text().endsWith(" ")) {
            segments.set(segments.size() - 1, new DonateSegment(last.text() + " ", last.color()));
        }
        cache.segments = List.copyOf(segments);
        cache.nextUpdateAt = now + DONATE_CACHE_TTL_MS;
        cleanupDonateCache(now);
        return cache.segments;
    }

    private void appendTextSegments(List<DonateSegment> out, Text text) {
        text.visit((style, string) -> {
            if (string == null || string.isEmpty()) {
                return Optional.empty();
            }

            int baseColor = style.getColor() != null ? style.getColor().getRgb() : 0xFFFFFF;
            appendColoredSegments(out, string.replace('\n', ' ').replace('\r', ' '), baseColor);
            return Optional.empty();
        }, Style.EMPTY);
    }

    private List<DonateSegment> getStyledTextSegments(Text text, int fallbackColor) {
        List<DonateSegment> segments = new ArrayList<>();
        if (text != null) {
            appendTextSegments(segments, text);
        }
        if (segments.isEmpty() && text != null && !text.getString().isEmpty()) {
            segments.add(new DonateSegment(text.getString(), fallbackColor));
        }
        return segments;
    }

    private void appendColoredSegments(List<DonateSegment> out, String text, int baseColor) {
        if (text == null || text.isEmpty()) return;
        int currentColor = baseColor;
        StringBuilder chunk = new StringBuilder();

        int chunkColor = currentColor;

        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            int charCount = Character.charCount(codePoint);

            if (codePoint == '\u00A7' && offset + charCount < text.length()) {
                flushSegment(out, chunk, chunkColor);
                char code = Character.toLowerCase(text.charAt(offset + charCount));
                Integer mappedColor = sectionColorToRgb(code);
                if (mappedColor != null) {
                    currentColor = mappedColor;
                } else if (code == 'r') {
                    currentColor = baseColor;
                }
                chunkColor = currentColor;
                offset += charCount + 1;
                continue;
            }

            String replacement = ReplaceSymbols.replaceCodePoint(codePoint);
            if (replacement != null) {
                flushSegment(out, chunk, chunkColor);
                int totalChars = Math.max(1, replacement.length());
                for (int i = 0; i < replacement.length(); i++) {
                    int gradientColor = ReplaceSymbols.getGradientColorForReplacement(codePoint, i, totalChars, 1.0f, currentColor);
                    if (chunk.length() > 0 && chunkColor != gradientColor) {
                        flushSegment(out, chunk, chunkColor);
                    }
                    chunkColor = gradientColor;
                    chunk.append(replacement.charAt(i));
                }
                offset += charCount;
                continue;
            }

            if (chunk.length() > 0 && chunkColor != currentColor) {
                flushSegment(out, chunk, chunkColor);
            }
            chunkColor = currentColor;
            chunk.appendCodePoint(codePoint);
            offset += charCount;
        }

        flushSegment(out, chunk, chunkColor);
    }

    private void flushSegment(List<DonateSegment> out, StringBuilder chunk, int color) {
        if (chunk.isEmpty()) return;
        out.add(new DonateSegment(chunk.toString(), color));
        chunk.setLength(0);
    }

    private Integer sectionColorToRgb(char code) {
        return switch (code) {
            case '0' -> 0x000000;
            case '1' -> 0x0000AA;
            case '2' -> 0x00AA00;
            case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000;
            case '5' -> 0xAA00AA;
            case '6' -> 0xFFAA00;
            case '7' -> 0xAAAAAA;
            case '8' -> 0x555555;
            case '9' -> 0x5555FF;
            case 'a' -> 0x55FF55;
            case 'b' -> 0x55FFFF;
            case 'c' -> 0xFF5555;
            case 'd' -> 0xFF55FF;
            case 'e' -> 0xFFFF55;
            case 'f' -> 0xFFFFFF;
            default -> null;
        };
    }

    private void cleanupDonateCache(long now) {
        if (now < nextDonateCacheCleanupAt || mc.world == null) {
            return;
        }
        nextDonateCacheCleanupAt = now + DONATE_CACHE_CLEANUP_MS;
        donateCache.entrySet().removeIf(entry -> mc.world.getPlayerByUuid(entry.getKey()) == null);
    }

    private Box getInterpolatedBox(Entity entity, float tickDelta) {
        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

        double ox = x - entity.getX();
        double oy = y - entity.getY();
        double oz = z - entity.getZ();

        return entity.getBoundingBox().offset(ox, oy, oz).expand(0.05);
    }

    private ScreenRect projectBox(Box box) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        boolean projectedAny = false;

        for (int xi = 0; xi < 2; xi++) {
            for (int yi = 0; yi < 2; yi++) {
                for (int zi = 0; zi < 2; zi++) {
                    if (!projectToScreen(
                            xi == 0 ? box.minX : box.maxX,
                            yi == 0 ? box.minY : box.maxY,
                            zi == 0 ? box.minZ : box.maxZ,
                            projectedPoint
                    )) continue;
                    projectedAny = true;
                    minX = Math.min(minX, projectedPoint.x);
                    minY = Math.min(minY, projectedPoint.y);
                    maxX = Math.max(maxX, projectedPoint.x);
                    maxY = Math.max(maxY, projectedPoint.y);
                }
            }
        }

        if (!projectedAny) return null;
        if (minX > mc.getWindow().getScaledWidth() + 300 || maxX < -300) return null;
        if (minY > mc.getWindow().getScaledHeight() + 300 || maxY < -300) return null;
        if ((maxX - minX) < 2.0 || (maxY - minY) < 2.0) return null;

        return new ScreenRect((float) minX, (float) minY, (float) maxX, (float) maxY);
    }

    private boolean projectToScreen(double worldX, double worldY, double worldZ, ProjectedPoint out) {
        projectionScratch.set(
                (float) (worldX - lastCameraPos.x),
                (float) (worldY - lastCameraPos.y),
                (float) (worldZ - lastCameraPos.z)
        );
        projectionScratch.rotate(lastInverseCameraRotation);

        clipScratch.set(projectionScratch.x, projectionScratch.y, projectionScratch.z, 1.0f);
        lastProjectionMatrix.transform(clipScratch);

        float w = clipScratch.w;
        if (w <= 0.00001f) return false;

        float ndcX = clipScratch.x / w;
        float ndcY = clipScratch.y / w;
        float ndcZ = clipScratch.z / w;

        float screenX = (ndcX * 0.5f + 0.5f) * lastScaledWidth;
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * lastScaledHeight;

        if (Float.isNaN(screenX) || Float.isNaN(screenY)) return false;
        if (Float.isInfinite(screenX) || Float.isInfinite(screenY)) return false;

        out.x = screenX;
        out.y = screenY;
        out.z = ndcZ;
        return true;
    }

    private boolean projectEntityAnchor(Entity entity, double yOffset, ProjectedPoint out) {
        double x = MathHelper.lerp(lastTickDelta, entity.lastRenderX, entity.getX());
        double y = MathHelper.lerp(lastTickDelta, entity.lastRenderY, entity.getY()) + yOffset;
        double z = MathHelper.lerp(lastTickDelta, entity.lastRenderZ, entity.getZ());
        return projectToScreen(x, y, z, out);
    }

    private boolean isInFirstPerson() {
        return mc != null && mc.gameRenderer != null && !mc.gameRenderer.getCamera().isThirdPerson();
    }

    private boolean shouldProcess3DEntity(Entity entity) {
        if (entity == null || entity.isRemoved() || entity instanceof ArmorStandEntity) {
            return false;
        }
        if (entity instanceof PlayerEntity player) {
            return shouldProcessPlayer(player, false);
        }
        if (entity instanceof ItemEntity itemEntity) {
            return targetItems.isState() && itemEntity.isAlive();
        }
        if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.isAlive()) {
            return false;
        }
        if (isAnimalEntity(entity)) {
            return targetAnimals.isState();
        }
        if (isMobEntity(entity)) {
            return targetMobs.isState();
        }
        return false;
    }
    private boolean shouldProcess2DPlayer(PlayerEntity player) {
        return shouldProcessPlayer(player, true);
    }

    private boolean shouldProcessLiving2D(LivingEntity entity) {
        return shouldProcess3DEntity(entity);
    }

    private boolean shouldProcessItem2D(ItemEntity itemEntity) {
        return targetItems.isState() && itemEntity.isAlive();
    }

    private boolean shouldProcessPlayer(PlayerEntity player, boolean skipInvisible) {
        if (!targetPlayers.isState()) {
            return false;
        }
        if (player == null || !player.isAlive()) {
            return false;
        }
        if (player == mc.player && isInFirstPerson()) {
            return false;
        }
        if (skipInvisible && player.isInvisible() && !canRenderInvisiblePlayer(player)) {
            return false;
        }
        return true;
    }

    private boolean isTargetEnabled(int index) {
        return targets.getSettings().size() > index && targets.getSettings().get(index).isState();
    }

    private boolean isAnimalEntity(Entity entity) {
        return entity instanceof AnimalEntity
                || entity instanceof WaterAnimalEntity
                || entity instanceof AmbientEntity;
    }

    private boolean isMobEntity(Entity entity) {
        return entity instanceof MobEntity && !isAnimalEntity(entity) && !(entity instanceof PlayerEntity);
    }

    private boolean canRenderInvisiblePlayer(PlayerEntity player) {
        SeeInvisibles seeInvisibles = ModuleClass.seeInvisibles;
        return seeInvisibles != null && seeInvisibles.shouldRenderInvisible(player);
    }

    private boolean isOutsideRenderDistance(Entity entity) {
        int viewDistanceChunks = mc.options.getViewDistance().getValue();
        double maxDistance = Math.max(48.0, viewDistanceChunks * 16.0 + 16.0);
        return entity.squaredDistanceTo(lastCameraPos) > maxDistance * maxDistance;
    }


    private record ScreenRect(float minX, float minY, float maxX, float maxY) {
        float centerX() {
            return (minX + maxX) * 0.5f;
        }

        float centerY() {
            return (minY + maxY) * 0.5f;
        }
    }

    private record DonateSegment(String text, int color) {
    }

    private static class DonateCache {
        private List<DonateSegment> segments = Collections.emptyList();
        private long nextUpdateAt;
    }

    private static class ProjectedPoint {
        private float x;
        private float y;
        private float z;
    }

    private void render3DBox(MatrixStack matrices, Entity entity, float tickDelta) {
        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - camera.z;

        Box box = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());

        matrices.push();
        matrices.translate(x, y, z);

        boolean isFriend = entity instanceof PlayerEntity player
                && Lumis.INSTANCE.friendStorage != null
                && Lumis.INSTANCE.friendStorage.isFriend(player.getName().getString());
        int boxColor;
        if (isFriend) {
            boxColor = ColorUtils.rgba(84, 255, 84, 255);
        } else {
            boxColor = getStableThemeColor();
        }
        boxColor = applyEntityHurtTint(entity, boxColor);
        float r = ColorUtils.redf(boxColor);
        float g = ColorUtils.greenf(boxColor);
        float b = ColorUtils.bluef(boxColor);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(BOX_LINE_WIDTH);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();

        if (boxFilled.isState()) {
            drawFilledBox(tessellator, matrix, box, r, g, b, FILL_ALPHA);
        }

        drawBoxOutline(tessellator, matrix, box, r, g, b, 1.0f);

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private int applyEntityHurtTint(Entity entity, int baseColor) {
        if (!(entity instanceof LivingEntity livingEntity) || !hurtTint.isState()) {
            entityHurtTintProgress.remove(entity.getId());
            return baseColor;
        }

        float target = MathHelper.clamp(livingEntity.hurtTime / 10.0f, 0.0f, 1.0f);
        float current = entityHurtTintProgress.getOrDefault(entity.getId(), 0.0f);
        float speed = target > current ? 0.38f : 0.16f;
        current += (target - current) * speed;

        if (current <= 0.003f && target <= 0.0f) {
            entityHurtTintProgress.remove(entity.getId());
            return baseColor;
        }

        entityHurtTintProgress.put(entity.getId(), current);
        int hitColor = ColorUtils.rgba(255, 70, 70, 255);
        return ColorUtils.interpolateColor(baseColor, hitColor, current);
    }

    private void drawPlayerMaskBox(MatrixStack matrices, Entity entity, float tickDelta) {
        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - camera.z;
        Box box = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());

        matrices.push();
        matrices.translate(x, y, z);
        drawMaskBox(Tessellator.getInstance(), matrices.peek().getPositionMatrix(), box);
        matrices.pop();
    }

    private void drawMaskBox(Tessellator tessellator, Matrix4f matrix, Box box) {
        BufferBuilder b = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        int white = 0xFFFFFFFF;

        b.vertex(matrix, minX, minY, minZ).color(white);
        b.vertex(matrix, maxX, minY, minZ).color(white);
        b.vertex(matrix, maxX, minY, maxZ).color(white);
        b.vertex(matrix, minX, minY, maxZ).color(white);

        b.vertex(matrix, minX, maxY, minZ).color(white);
        b.vertex(matrix, minX, maxY, maxZ).color(white);
        b.vertex(matrix, maxX, maxY, maxZ).color(white);
        b.vertex(matrix, maxX, maxY, minZ).color(white);

        b.vertex(matrix, minX, minY, minZ).color(white);
        b.vertex(matrix, minX, maxY, minZ).color(white);
        b.vertex(matrix, maxX, maxY, minZ).color(white);
        b.vertex(matrix, maxX, minY, minZ).color(white);

        b.vertex(matrix, minX, minY, maxZ).color(white);
        b.vertex(matrix, maxX, minY, maxZ).color(white);
        b.vertex(matrix, maxX, maxY, maxZ).color(white);
        b.vertex(matrix, minX, maxY, maxZ).color(white);

        b.vertex(matrix, minX, minY, minZ).color(white);
        b.vertex(matrix, minX, minY, maxZ).color(white);
        b.vertex(matrix, minX, maxY, maxZ).color(white);
        b.vertex(matrix, minX, maxY, minZ).color(white);

        b.vertex(matrix, maxX, minY, minZ).color(white);
        b.vertex(matrix, maxX, maxY, minZ).color(white);
        b.vertex(matrix, maxX, maxY, maxZ).color(white);
        b.vertex(matrix, maxX, minY, maxZ).color(white);

        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    private void renderShaderBoxes() {
        if (!hasShaderMask || maskBuffer == null) return;
        boolean lineMode = isThreadMode();

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.blockOverlay);
        if (shader == null) return;

        int color1 = getStableThemeColor();
        int color2 = isRainbowTheme() ? ColorUtils.getThemeColor(180) : color1;

        mc.getFramebuffer().beginWrite(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderUtils.blockOverlay);
        RenderSystem.setShaderTexture(0, maskBuffer.getColorAttachment());
        setUniform(shader, "texelSize", 1.0f / Math.max(1, mc.getWindow().getFramebufferWidth()), 1.0f / Math.max(1, mc.getWindow().getFramebufferHeight()));
        setUniform(shader, "color", ColorUtils.redf(color1), ColorUtils.greenf(color1), ColorUtils.bluef(color1));
        setUniform(shader, "color2", ColorUtils.redf(color2), ColorUtils.greenf(color2), ColorUtils.bluef(color2));
        setUniform(shader, "time", (System.currentTimeMillis() % 100000L) / 1000.0f);
        setUniform(shader, "speed", waveSpeed.get());
        setUniform(shader, "scale", waveScale.get());
        setUniform(shader, "outline", outline.get());
        setUniform(shader, "glow", lineMode ? 0.0f : glow.get());
        setUniform(shader, "fill", lineMode ? 0.0f : fill.get());
        setUniform(shader, "alpha", lineMode ? 1.0f : alpha.get());
        setUniform(shader, "outlineOnly", lineMode ? 1.0f : 0.0f);
        drawFullscreenQuad();

        if (glow.get() > EPSILON) {
            int blurredMask = runKawaseBloom(Math.max(3, Math.min(8, 4 + Math.round(outline.get() * 0.7f))));
            ShaderProgram glowShader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.shaderHandsGlow);
            if (glowShader != null) {
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SrcFactor.SRC_ALPHA,
                        GlStateManager.DstFactor.ONE,
                        GlStateManager.SrcFactor.ZERO,
                        GlStateManager.DstFactor.ONE
                );
                RenderSystem.setShader(ShaderUtils.shaderHandsGlow);
                RenderSystem.setShaderTexture(0, blurredMask);
                RenderSystem.setShaderTexture(1, maskBuffer.getColorAttachment());
                setUniform(glowShader, "color", ColorUtils.redf(color1), ColorUtils.greenf(color1), ColorUtils.bluef(color1));
                setUniform(glowShader, "color2", ColorUtils.redf(color2), ColorUtils.greenf(color2), ColorUtils.bluef(color2));
                setUniform(glowShader, "exposure", 1.0f + glow.get() * 1.8f);
                drawFullscreenQuad();
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.setShaderTexture(1, 0);
        mc.getFramebuffer().beginWrite(true);
    }

    private void renderShaderBoxesWorldPass() {
        if (!isPostBoxMode()) return;

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        float width = Math.max(mc.getWindow().getScaledWidth(), 1);
        float height = Math.max(mc.getWindow().getScaledHeight(), 1);
        Matrix4f ortho = new Matrix4f().setOrtho(0.0f, width, height, 0.0f, -1000.0f, 1000.0f);
        RenderSystem.setProjectionMatrix(ortho, ProjectionType.ORTHOGRAPHIC);

        try {
            renderShaderBoxes();
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, ProjectionType.ORTHOGRAPHIC);
        }
    }

    private int runKawaseBloom(int iterations) {
        ensureBloomBuffers(iterations);
        if (bloomBuffers.isEmpty()) return maskBuffer.getColorAttachment();

        int currentTexture = maskBuffer.getColorAttachment();
        ShaderProgram downShader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.shaderHandsKawaseDown);
        ShaderProgram upShader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.shaderHandsKawaseUp);
        if (downShader == null || upShader == null) return currentTexture;

        for (int i = 0; i < iterations; i++) {
            Framebuffer dst = bloomBuffers.get(i);
            dst.setClearColor(0f, 0f, 0f, 0f);
            dst.clear();
            dst.beginWrite(true);

            RenderSystem.setShader(ShaderUtils.shaderHandsKawaseDown);
            RenderSystem.setShaderTexture(0, currentTexture);
            setHandsKawaseUniforms(downShader, dst.textureWidth, dst.textureHeight, 1.0f + i);
            drawFullscreenQuad();

            currentTexture = dst.getColorAttachment();
        }

        for (int i = iterations - 1; i >= 1; i--) {
            Framebuffer dst = bloomBuffers.get(i - 1);
            dst.setClearColor(0f, 0f, 0f, 0f);
            dst.clear();
            dst.beginWrite(true);

            RenderSystem.setShader(ShaderUtils.shaderHandsKawaseUp);
            RenderSystem.setShaderTexture(0, currentTexture);
            setHandsKawaseUniforms(upShader, dst.textureWidth, dst.textureHeight, 1.0f + i);
            setUniform(upShader, "color", 1.0f, 1.0f, 1.0f);
            drawFullscreenQuad();

            currentTexture = dst.getColorAttachment();
        }

        mc.getFramebuffer().beginWrite(true);
        return currentTexture;
    }

    private void ensureMaskBuffer() {
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();
        if (maskBuffer == null || maskWidth != w || maskHeight != h) {
            if (maskBuffer != null) {
                maskBuffer.delete();
            }
            maskBuffer = new SimpleFramebuffer(w, h, true);
            maskWidth = w;
            maskHeight = h;
            for (Framebuffer fb : bloomBuffers) {
                fb.delete();
            }
            bloomBuffers.clear();
        }
    }

    private void ensureBloomBuffers(int iterations) {
        while (bloomBuffers.size() > iterations) {
            int last = bloomBuffers.size() - 1;
            bloomBuffers.get(last).delete();
            bloomBuffers.remove(last);
        }

        for (int i = 0; i < iterations; i++) {
            int w = Math.max(2, maskWidth >> (i + 1));
            int h = Math.max(2, maskHeight >> (i + 1));
            if (i >= bloomBuffers.size()) {
                bloomBuffers.add(new SimpleFramebuffer(w, h, false));
                continue;
            }
            Framebuffer fb = bloomBuffers.get(i);
            if (fb.textureWidth != w || fb.textureHeight != h) {
                fb.delete();
                bloomBuffers.set(i, new SimpleFramebuffer(w, h, false));
            }
        }
    }

    private void copyMainDepthToMask() {
        if (maskBuffer == null) return;
        int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getFramebuffer().fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, maskBuffer.fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
    }

    private void setUniform(ShaderProgram shader, String name, float value) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private void setUniform(ShaderProgram shader, String name, float x, float y) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y);
    }

    private void setUniform(ShaderProgram shader, String name, float x, float y, float z) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y, z);
    }

    private void setHandsKawaseUniforms(ShaderProgram shader, int texWidth, int texHeight, float offset) {
        setUniform(shader, "uSize", Math.max(1, texWidth), Math.max(1, texHeight));
        setUniform(shader, "uOffset", offset, offset);
        setUniform(shader, "uHalfPixel", 0.5f / Math.max(1, texWidth), 0.5f / Math.max(1, texHeight));
    }

    private void drawFullscreenQuad() {
        float width = Math.max(mc.getWindow().getScaledWidth(), 1);
        float height = Math.max(mc.getWindow().getScaledHeight(), 1);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        b.vertex(0, 0, 0).texture(0, 1).color(1f, 1f, 1f, 1f);
        b.vertex(0, height, 0).texture(0, 0).color(1f, 1f, 1f, 1f);
        b.vertex(width, height, 0).texture(1, 0).color(1f, 1f, 1f, 1f);
        b.vertex(width, 0, 0).texture(1, 1).color(1f, 1f, 1f, 1f);
        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    private boolean isPostBoxMode() {
        return false;
    }

    private boolean isThreadMode() {
        return false;
    }

    private boolean isRainbowTheme() {
        if (Lumis.INSTANCE == null || Lumis.INSTANCE.themeStorage == null || Lumis.INSTANCE.themeStorage.getThemes() == null) {
            return false;
        }
        var theme = Lumis.INSTANCE.themeStorage.getThemes().getTheme();
        return theme != null && "Rainbow".equals(theme.getName());
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

    private void renderThreadWeb(MatrixStack matrices, Entity entity, float tickDelta) {
        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - camera.z;
        Box box = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());

        matrices.push();
        matrices.translate(x, y, z);
        drawAnimatedWeb(matrices.peek().getPositionMatrix(), box, entity.getId());
        matrices.pop();
    }

    private void drawAnimatedWeb(Matrix4f matrix, Box box, long seedBase) {
        int strandsPerFace = 5;
        int samples = 18;
        float t = (System.currentTimeMillis() % 100000L) / 1000.0f * lineSpeed.get();
        float lineWidth = 0.0025f;
        float bendBase = 0.06f + lineJitter.get() * 0.20f;
        int baseAlpha = Math.max(20, Math.min(255, (int) (alpha.get() * 210.0f)));
        int themeColor = getStableThemeColor();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        drawFilledBoxInt(matrix, box, ColorUtils.setAlphaColor(themeColor, (int) (alpha.get() * fill.get() * 170.0f)));

        for (int face = 0; face < 6; face++) {
            int[] neighbors = faceNeighbors(face);
            for (int strand = 0; strand < strandsPerFace; strand++) {
                int key = face * 1000 + strand * 53;
                int adj = neighbors[strand % neighbors.length];
                double phase = t * (0.95 + rand01(seedBase, key + 1) * 0.55) + strand * 0.83 + face * 1.11;
                double edgeT = clamp01(0.5 + Math.sin(phase * 1.37 + rand01(seedBase, key + 2) * 6.2831853) * 0.38);

                Vec3d pivot = edgePoint(box, face, adj, edgeT, 0.0015);
                Vec3d start = facePoint(box, face,
                        clamp01(0.5 + (rand01(seedBase, key + 3) - 0.5) * 0.46),
                        clamp01(0.5 + (rand01(seedBase, key + 4) - 0.5) * 0.46),
                        0.0015);
                Vec3d end = facePoint(box, adj,
                        clamp01(0.5 + (rand01(seedBase, key + 5) - 0.5) * 0.46),
                        clamp01(0.5 + (rand01(seedBase, key + 6) - 0.5) * 0.46),
                        0.0015);

                Vec3d[] basisA = faceBasis(face);
                Vec3d[] basisB = faceBasis(adj);
                Vec3d normalA = faceNormal(face);
                Vec3d normalB = faceNormal(adj);
                double bendA = bendBase * (0.7 + rand01(seedBase, key + 7))
                        * Math.sin(phase * 1.9 + rand01(seedBase, key + 8) * 6.2831853);
                double bendB = bendBase * (0.7 + rand01(seedBase, key + 9))
                        * Math.cos(phase * 1.7 + rand01(seedBase, key + 10) * 6.2831853);

                Vec3d dirA = pivot.subtract(start);
                Vec3d c1a = start.add(dirA.multiply(0.38)).add(basisA[0].multiply(bendA)).add(basisA[1].multiply(-bendA * 0.55));
                Vec3d c2a = start.add(dirA.multiply(0.76)).add(basisA[0].multiply(-bendA * 0.65)).add(basisA[1].multiply(bendA * 0.4));

                Vec3d dirB = end.subtract(pivot);
                Vec3d c1b = pivot.add(dirB.multiply(0.24)).add(basisB[0].multiply(bendB)).add(basisB[1].multiply(bendB * 0.45));
                Vec3d c2b = pivot.add(dirB.multiply(0.62)).add(basisB[0].multiply(-bendB * 0.7)).add(basisB[1].multiply(-bendB * 0.35));

                int alphaLine = Math.max(18, Math.min(255, (int) (baseAlpha * (0.74 + 0.26 * Math.sin(phase * 2.6)))));
                int color = ColorUtils.setAlphaColor(themeColor, alphaLine);
                drawBezierRibbon(matrix, start, c1a, c2a, pivot, normalA, samples, color, lineWidth);
                drawBezierRibbon(matrix, pivot, c1b, c2b, end, normalB, samples, color, lineWidth);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private Vec3d cubicBezier(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float t) {
        double it = 1.0 - t;
        double it2 = it * it;
        double t2 = t * t;
        return p0.multiply(it2 * it)
                .add(p1.multiply(3.0 * it2 * t))
                .add(p2.multiply(3.0 * it * t2))
                .add(p3.multiply(t2 * t));
    }

    private void drawBezierRibbon(Matrix4f matrix, Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d faceNormal, int samples, int color, float halfWidth) {
        Vec3d[] points = new Vec3d[samples + 1];
        for (int s = 0; s <= samples; s++) {
            float u = (float) s / (float) samples;
            points[s] = cubicBezier(p0, p1, p2, p3, u);
        }

        BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < samples; i++) {
            Vec3d a = points[i];
            Vec3d b = points[i + 1];
            Vec3d dir = b.subtract(a);
            if (dir.lengthSquared() < 1.0E-6) continue;

            Vec3d perp = faceNormal.crossProduct(dir).normalize().multiply(halfWidth);
            Vec3d aL = a.add(perp);
            Vec3d aR = a.subtract(perp);
            Vec3d bL = b.add(perp);
            Vec3d bR = b.subtract(perp);

            quads.vertex(matrix, (float) aL.x, (float) aL.y, (float) aL.z).color(color);
            quads.vertex(matrix, (float) aR.x, (float) aR.y, (float) aR.z).color(color);
            quads.vertex(matrix, (float) bR.x, (float) bR.y, (float) bR.z).color(color);
            quads.vertex(matrix, (float) bL.x, (float) bL.y, (float) bL.z).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(quads.end());
    }

    private int[] faceNeighbors(int face) {
        return switch (face) {
            case 0, 1 -> new int[]{2, 3, 4, 5};
            case 2, 3 -> new int[]{0, 1, 4, 5};
            default -> new int[]{0, 1, 2, 3};
        };
    }

    private Vec3d[] faceBasis(int face) {
        return switch (face) {
            case 0, 1 -> new Vec3d[]{new Vec3d(1, 0, 0), new Vec3d(0, 0, 1)};
            case 2, 3 -> new Vec3d[]{new Vec3d(1, 0, 0), new Vec3d(0, 1, 0)};
            default -> new Vec3d[]{new Vec3d(0, 0, 1), new Vec3d(0, 1, 0)};
        };
    }

    private Vec3d faceNormal(int face) {
        return switch (face) {
            case 0 -> new Vec3d(0, 1, 0);
            case 1 -> new Vec3d(0, -1, 0);
            case 2 -> new Vec3d(0, 0, -1);
            case 3 -> new Vec3d(0, 0, 1);
            case 4 -> new Vec3d(-1, 0, 0);
            default -> new Vec3d(1, 0, 0);
        };
    }

    private Vec3d edgePoint(Box box, int faceA, int faceB, double t, double inset) {
        double x = Double.NaN;
        double y = Double.NaN;
        double z = Double.NaN;

        double[] fixedA = faceFixedCoords(box, faceA, inset);
        if (!Double.isNaN(fixedA[0])) x = fixedA[0];
        if (!Double.isNaN(fixedA[1])) y = fixedA[1];
        if (!Double.isNaN(fixedA[2])) z = fixedA[2];

        double[] fixedB = faceFixedCoords(box, faceB, inset);
        if (!Double.isNaN(fixedB[0])) x = fixedB[0];
        if (!Double.isNaN(fixedB[1])) y = fixedB[1];
        if (!Double.isNaN(fixedB[2])) z = fixedB[2];

        double tt = clamp01(t);
        if (Double.isNaN(x)) x = lerp(box.minX, box.maxX, tt);
        if (Double.isNaN(y)) y = lerp(box.minY, box.maxY, tt);
        if (Double.isNaN(z)) z = lerp(box.minZ, box.maxZ, tt);
        return new Vec3d(x, y, z);
    }

    private double[] faceFixedCoords(Box box, int face, double inset) {
        return switch (face) {
            case 0 -> new double[]{Double.NaN, box.maxY - inset, Double.NaN};
            case 1 -> new double[]{Double.NaN, box.minY + inset, Double.NaN};
            case 2 -> new double[]{Double.NaN, Double.NaN, box.minZ + inset};
            case 3 -> new double[]{Double.NaN, Double.NaN, box.maxZ - inset};
            case 4 -> new double[]{box.minX + inset, Double.NaN, Double.NaN};
            default -> new double[]{box.maxX - inset, Double.NaN, Double.NaN};
        };
    }

    private Vec3d facePoint(Box box, int face, double u, double v, double inset) {
        u = clamp01(u);
        v = clamp01(v);
        return switch (face) {
            case 0 -> new Vec3d(lerp(box.minX, box.maxX, u), box.maxY - inset, lerp(box.minZ, box.maxZ, v));
            case 1 -> new Vec3d(lerp(box.minX, box.maxX, u), box.minY + inset, lerp(box.minZ, box.maxZ, v));
            case 2 -> new Vec3d(lerp(box.minX, box.maxX, u), lerp(box.minY, box.maxY, v), box.minZ + inset);
            case 3 -> new Vec3d(lerp(box.minX, box.maxX, u), lerp(box.minY, box.maxY, v), box.maxZ - inset);
            case 4 -> new Vec3d(box.minX + inset, lerp(box.minY, box.maxY, v), lerp(box.minZ, box.maxZ, u));
            default -> new Vec3d(box.maxX - inset, lerp(box.minY, box.maxY, v), lerp(box.minZ, box.maxZ, u));
        };
    }

    private double rand01(long seed, int salt) {
        long x = seed + 0x9E3779B97F4A7C15L * (salt + 1L);
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return (double) (x & 0xFFFFFF) / (double) 0x1000000;
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private void drawFilledBoxInt(Matrix4f matrix, Box box, int color) {
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(color);

        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(color);

        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(color);

        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(color);

        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(color);

        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(color);
        b.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(color);

        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    private void drawFilledBox(Tessellator tessellator, Matrix4f matrix, Box box, float r, float g, float b, float a) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

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

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void drawBoxOutline(Tessellator tessellator, Matrix4f matrix, Box box, float r, float g, float b, float a) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(BOX_LINE_WIDTH);
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

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

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}

package fun.lumis.client.modules.impl.render.base.implement;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import fun.lumis.Lumis;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.animation.AnimationUtils;
import fun.lumis.api.utils.animation.Easings;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.draggable.Draggable;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.font.ReplaceSymbols;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.api.utils.scissor.ScissorUtils;
import fun.lumis.client.modules.impl.render.base.InterfaceProcessing;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class StaffList extends InterfaceProcessing {
    private static final int STATUS_VANISH_COLOR = 0xFFFF465A;
    private static final int STATUS_GM3_COLOR = 0xFFFFDC46;
    private static final int STATUS_ONLINE_COLOR = 0xFF64FF78;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Map<String, StaffData> staffDataCache = new LinkedHashMap<>();
    private final Map<String, Float> staffAnimations = new HashMap<>();
    private final Set<String> activeStaff = new HashSet<>();

    private final Pattern namePattern = Pattern.compile("^\\w{3,16}$");
    private final Set<String> validStaffPrefixes = new HashSet<>();

    private final AnimationUtils widthAnimation = new AnimationUtils(60, 10.5f, Easings.QUAD_OUT);
    private float staffAnimatedHeight = 18f;

    private long lastStaffUpdate = 0;

    private final List<String> visiblePlayers = new ArrayList<>();
    private final Set<String> animationScratch = new HashSet<>();

    private Font font10;
    private Font font12;
    private Font font14;
    private Font font16;
    private Font iconFont;

    public StaffList(Draggable draggable) {
        super(draggable);
        validStaffPrefixes.addAll(Arrays.asList(
                "supp", "ꜱupp", "mod", "der", "adm", "wne", "мод", "помо", "адм",
                "владе", "отри", "таф", "taf", "curat", "курато", "dev", "раз",
                "сапп", "yt", "ютуб", "стажер", "сотрудник"
        ));
    }

    private static class PrefixSegment {
        final String text;
        final int color;
        float width12;
        float width14;

        PrefixSegment(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private static class StaffData {
        String status;
        List<PrefixSegment> segments;
        float prefixWidth12;
        float prefixWidth14;
        float nameWidth12;
        float nameWidth14;

        StaffData(String status) {
            this.status = status;
            this.segments = new ArrayList<>();
        }
    }

    private void initFonts() {
        if (font10 == null) {
            font10 = Fonts.getFont("suisse", 10);
            font12 = Fonts.getFont("suisse", 12);
            font14 = Fonts.getFont("suisse", 14);
            font16 = Fonts.getFont("suisse", 16);
            iconFont = Fonts.getFont("icon", 13);
        }
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        if (mc.player == null || mc.world == null) return;

        initFonts();

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStaffUpdate > 500) {
            updateStaffCache();
            lastStaffUpdate = currentTime;
        }

        updateAnimations();

        if (ModuleClass.interfaceModule.style.is("Обычный")) {
            renderDefaultStyle(eventRender);
        } else {
            renderWaveStyle(eventRender);
        }

        super.onRender(eventRender);
    }

    private boolean matchesStaffPrefix(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String p : validStaffPrefixes) {
            if (lower.contains(p)) return true;
        }
        return false;
    }

    private List<PrefixSegment> parsePrefix(Text prefix) {
        List<PrefixSegment> segments = new ArrayList<>();

        prefix.visit((style, string) -> {
            if (string == null || string.isEmpty()) return Optional.empty();

            appendPrefixSegments(segments, string, style.getColor() != null ? style.getColor().getRgb() : 0xFFFFFF);

            return Optional.empty();
        }, Style.EMPTY);

        return segments;
    }

    private void appendPrefixSegments(List<PrefixSegment> segments, String text, int baseColor) {
        int currentColor = baseColor;
        StringBuilder chunk = new StringBuilder();
        int chunkColor = currentColor;

        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            int charCount = Character.charCount(codePoint);

            if (codePoint == '\u00A7' && offset + charCount < text.length()) {
                flushPrefixSegment(segments, chunk, chunkColor);
                char code = Character.toLowerCase(text.charAt(offset + charCount));
                Integer mappedColor = sectionColorToRgb(code);
                currentColor = mappedColor != null ? mappedColor : code == 'r' ? baseColor : currentColor;
                chunkColor = currentColor;
                offset += charCount + 1;
                continue;
            }

            String replacement = ReplaceSymbols.replaceCodePoint(codePoint);
            if (replacement != null) {
                flushPrefixSegment(segments, chunk, chunkColor);
                int totalChars = Math.max(1, replacement.length());
                for (int i = 0; i < replacement.length(); i++) {
                    int replacementColor = ReplaceSymbols.getGradientColorForReplacement(codePoint, i, totalChars, 1.0f, currentColor);
                    if (chunk.length() > 0 && chunkColor != replacementColor) {
                        flushPrefixSegment(segments, chunk, chunkColor);
                    }
                    chunkColor = replacementColor;
                    chunk.append(replacement.charAt(i));
                }
                offset += charCount;
                continue;
            }

            if (chunk.length() > 0 && chunkColor != currentColor) {
                flushPrefixSegment(segments, chunk, chunkColor);
            }
            chunkColor = currentColor;
            chunk.appendCodePoint(codePoint);
            offset += charCount;
        }

        flushPrefixSegment(segments, chunk, chunkColor);
    }

    private void flushPrefixSegment(List<PrefixSegment> segments, StringBuilder chunk, int color) {
        if (chunk.isEmpty()) return;
        String text = chunk.toString();
        PrefixSegment seg = new PrefixSegment(text, color);
        seg.width12 = font12.getWidth(text);
        seg.width14 = font14.getWidth(text);
        segments.add(seg);
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

    private void updateStaffCache() {
        activeStaff.clear();
        String selfName = mc.player.getName().getString();

        for (Team team : mc.world.getScoreboard().getTeams()) {
            Collection<String> players = team.getPlayerList();
            if (players.size() != 1) continue;

            String name = players.iterator().next();
            if (!namePattern.matcher(name).matches()) continue;
            if (name.equals(selfName)) continue;

            PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(name);
            boolean vanish = info == null;
            boolean isGM3 = info != null && info.getGameMode() == GameMode.SPECTATOR;

            Text prefixText = team.getPrefix();
            String prefixStr = prefixText.getString();
            boolean matchesPrefix = matchesStaffPrefix(prefixStr);
            boolean isInStaffList = Lumis.INSTANCE.staffStorage.isStaff(name);

            if (matchesPrefix || vanish || isGM3 || isInStaffList) {
                activeStaff.add(name);

                String status;
                if (vanish) {
                    status = "VANISH";
                } else if (isGM3) {
                    status = "GM3";
                } else {
                    status = "ONLINE";
                }

                StaffData existing = staffDataCache.get(name);
                if (existing == null) {
                    existing = new StaffData(status);
                    staffDataCache.put(name, existing);
                }
                existing.status = status;
                existing.segments = parsePrefix(prefixText);
                calculateWidths(existing, name);
            }
        }

        for (String staffName : Lumis.INSTANCE.staffStorage.getStaffs()) {
            if (staffName.equals(selfName)) continue;
            if (!namePattern.matcher(staffName).matches()) continue;
            if (activeStaff.contains(staffName)) continue;

            activeStaff.add(staffName);

            PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(staffName);
            String status;
            if (info == null) {
                status = "VANISH";
            } else if (info.getGameMode() == GameMode.SPECTATOR) {
                status = "GM3";
            } else {
                status = "ONLINE";
            }

            StaffData existing = staffDataCache.get(staffName);
            if (existing == null) {
                existing = new StaffData(status);
                existing.segments = new ArrayList<>();
                calculateWidths(existing, staffName);
                staffDataCache.put(staffName, existing);
            } else {
                existing.status = status;
            }
        }
    }

    private void calculateWidths(StaffData data, String name) {
        data.prefixWidth12 = 0;
        data.prefixWidth14 = 0;
        for (PrefixSegment seg : data.segments) {
            data.prefixWidth12 += seg.width12;
            data.prefixWidth14 += seg.width14;
        }
        data.nameWidth12 = font12.getWidth(name);
        data.nameWidth14 = font14.getWidth(name + " >> ");
    }

    private void updateAnimations() {
        float lerpSpeed = 0.1f;

        animationScratch.clear();
        animationScratch.addAll(staffAnimations.keySet());
        animationScratch.addAll(activeStaff);

        for (String playerName : animationScratch) {
            boolean isActive = activeStaff.contains(playerName);
            float targetAnim = isActive ? 1f : 0f;
            float currentAnim = staffAnimations.getOrDefault(playerName, 0f);
            currentAnim += (targetAnim - currentAnim) * lerpSpeed;
            staffAnimations.put(playerName, currentAnim);
        }

        Iterator<Map.Entry<String, Float>> animIt = staffAnimations.entrySet().iterator();
        while (animIt.hasNext()) {
            Map.Entry<String, Float> entry = animIt.next();
            if (entry.getValue() < 0.01f && !activeStaff.contains(entry.getKey())) {
                animIt.remove();
                staffDataCache.remove(entry.getKey());
            }
        }
    }

    private List<String> getVisiblePlayers() {
        visiblePlayers.clear();
        for (Map.Entry<String, Float> entry : staffAnimations.entrySet()) {
            if (entry.getValue() > 0.01f) {
                visiblePlayers.add(entry.getKey());
            }
        }
        Collections.sort(visiblePlayers);
        return visiblePlayers;
    }

    private int getStatusColor(String status) {
        return switch (status) {
            case "VANISH" -> STATUS_VANISH_COLOR;
            case "GM3" -> STATUS_GM3_COLOR;
            default -> STATUS_ONLINE_COLOR;
        };
    }

    private float getStatusBoxWidth(String status) {
        return 12f;
    }

    private void renderDefaultStyle(EventRender.Default eventRender) {
        float x = draggable.getX();
        float y = draggable.getY();
        MatrixStack matrices = eventRender.getContext().getMatrices();

        int colorTheme;
        if (!Lumis.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            colorTheme = Lumis.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            colorTheme = ColorUtils.getThemeColor();
        }

        List<String> visiblePlayers = getVisiblePlayers();

        float maxWidth = 60f;
        float headerHeight = 16f;
        float itemHeight = 12f;
        float padding = 5f;
        float statusPadding = 4f;

        for (String playerName : visiblePlayers) {
            StaffData data = staffDataCache.get(playerName);
            if (data != null) {
                float statusBoxW = getStatusBoxWidth(data.status);
                float totalW = padding + data.prefixWidth12 + data.nameWidth12 + statusPadding + statusBoxW + padding;
                if (totalW > maxWidth) {
                    maxWidth = totalW;
                }
            }
        }

        widthAnimation.update(maxWidth);
        float width = widthAnimation.getValue();

        float contentHeight = 0f;
        for (String playerName : visiblePlayers) {
            contentHeight += itemHeight * staffAnimations.getOrDefault(playerName, 0f);
        }

        float targetHeight = visiblePlayers.isEmpty() ? headerHeight : headerHeight + contentHeight + 2;
        staffAnimatedHeight += (targetHeight - staffAnimatedHeight) * 0.12f;
        float height = staffAnimatedHeight;

        RenderUtils.drawDefaultHudElementRects(matrices, x, y, width, height, colorTheme, isUnusualRectType());

        font14.draw(matrices, "Staff", x + 5, y + 6f, -1);
        iconFont.draw(matrices, "y", x + width - 13f, y + 7.5f, colorTheme);

        float offsetY = 18;
        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(x, y, width, height);
        for (String playerName : visiblePlayers) {
            float anim = staffAnimations.getOrDefault(playerName, 0f);
            if (anim <= 0.01f) continue;

            StaffData data = staffDataCache.get(playerName);
            if (data == null) continue;

            int alpha = (int) (255 * anim);
            float yOffset = -5 * (1 - anim);

            float currentX = x + padding;
            for (int i = 0; i < data.segments.size(); i++) {
                PrefixSegment seg = data.segments.get(i);
                int color = ColorUtils.setAlphaColor(seg.color, alpha);
                font12.draw(matrices, seg.text, currentX, y + offsetY + 2 + yOffset, color);
                currentX += seg.width12;
            }

            font12.draw(matrices, playerName, currentX, y + offsetY + 2 + yOffset, ColorUtils.rgba(255, 255, 255, alpha));

            float statusBoxWidth = getStatusBoxWidth(data.status);
            float statusBoxX = x + width - statusBoxWidth - padding;
            float statusBoxY = y + offsetY + 1.0f + yOffset;
            int statusRectColor = ColorUtils.setAlphaColor(getStatusColor(data.status), alpha);
            RenderUtils.drawRoundedRect(matrices, statusBoxX + 4, statusBoxY + 1.5f, statusBoxWidth - 4.5f, 3.45f, 0.55f, statusRectColor);

            offsetY += itemHeight * anim;
        }
        ScissorUtils.pop();
        ScissorUtils.unset();

        draggable.setWidth(width);
        draggable.setHeight(height);
    }

    private void renderWaveStyle(EventRender.Default eventRender) {
        float x = draggable.getX();
        float y = draggable.getY();
        MatrixStack matrices = eventRender.getContext().getMatrices();

        int time = (int) ((System.currentTimeMillis() % 2000) / 2000f * 360f);

        int leftTop = ColorUtils.getThemeColor(time);
        int leftBottom = ColorUtils.getThemeColor(time + 30);
        int centerTop = ColorUtils.getThemeColor(time + 90);
        int centerBottom = ColorUtils.getThemeColor(time + 120);
        int rightTop = ColorUtils.getThemeColor(time + 180);
        int rightBottom = ColorUtils.getThemeColor(time + 210);

        List<String> visiblePlayers = getVisiblePlayers();

        float maxWidth = 80f;
        float headerHeight = 18f;
        float itemHeight = 10f;
        float padding = 5f;

        for (String playerName : visiblePlayers) {
            StaffData data = staffDataCache.get(playerName);
            if (data != null) {
                float statusW = font12.getWidth(data.status);
                float totalW = padding + data.prefixWidth14 + data.nameWidth14 + statusW + padding;
                if (totalW > maxWidth) {
                    maxWidth = totalW;
                }
            }
        }

        float width = maxWidth;

        float contentHeight = 0f;
        for (String playerName : visiblePlayers) {
            contentHeight += itemHeight * staffAnimations.getOrDefault(playerName, 0f);
        }

        float height = visiblePlayers.isEmpty() ? headerHeight : headerHeight + contentHeight + 4;

        if (visiblePlayers.isEmpty()) {
            RenderUtils.drawWaveHudHeader(matrices, x, y, width, 15, 0, 10, 10,
                    leftTop, leftBottom, centerTop, centerBottom, rightTop, rightBottom);
            float titleX = x + (width - font16.getWidth("stafflist")) / 2.0f;
            font16.drawStringWithShadow(matrices, "stafflist", titleX, y + 5, -1);
            draggable.setWidth(width);
            draggable.setHeight(headerHeight);
            return;
        }

        RenderUtils.drawWaveHudPanel(matrices, x, y, width, height, ColorUtils.rgba(25, 25, 25, 150),
                15, 0, 10, 10,
                leftTop, leftBottom, centerTop, centerBottom, rightTop, rightBottom);

        float titleX = x + (width - font16.getWidth("stafflist")) / 1.9f;
        font16.drawStringWithShadow(matrices, "stafflist", titleX, y + 5, -1);

        float yOffsetPos = 20f;
        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(x, y, width, height);
        for (String playerName : visiblePlayers) {
            float anim = staffAnimations.getOrDefault(playerName, 0f);
            if (anim <= 0.01f) continue;

            StaffData data = staffDataCache.get(playerName);
            if (data == null) continue;

            int alpha = (int) (255 * anim);
            float yOffset = -5 * (1 - anim);

            float textX = x + padding;
            for (int i = 0; i < data.segments.size(); i++) {
                PrefixSegment seg = data.segments.get(i);
                int color = ColorUtils.setAlphaColor(seg.color, alpha);
                font14.draw(matrices, seg.text, textX, y + yOffsetPos + 1.5f + yOffset, color);
                textX += seg.width14;
            }

            font14.draw(matrices, playerName + " >> ", textX, y + yOffsetPos + 1.5f + yOffset, ColorUtils.rgba(255, 255, 255, alpha));
            float nameArrowWidth = font14.getWidth(playerName + " >> ");
            font12.draw(matrices, data.status, textX + nameArrowWidth, y + yOffsetPos + 2.5f + yOffset, ColorUtils.setAlphaColor(getStatusColor(data.status), alpha));

            yOffsetPos += itemHeight * anim;
        }
        ScissorUtils.pop();
        ScissorUtils.unset();

        draggable.setWidth(width);
        draggable.setHeight(height);
    }
}

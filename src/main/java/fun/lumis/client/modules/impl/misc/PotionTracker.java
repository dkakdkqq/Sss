package fun.lumis.client.modules.impl.misc;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.Box;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.client.modules.Module;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PotionTracker extends Module {

    public static PotionTracker INSTANCE = new PotionTracker();

    private static final double TRACK_RADIUS = 50.0;
    private static final double SPLASH_RADIUS = 4.0;
    private static final double SPLASH_HEIGHT = 2.0;
    private static final int MAX_MESSAGES = 4;
    private static final int GRAY = new Color(200, 200, 200).getRGB();
    private static final int PLAYER = new Color(235, 235, 235).getRGB();

    private final Map<Integer, PotionData> trackedPotions = new HashMap<>();
    private ClientWorld lastWorld;

    public PotionTracker() {
        super("PotionTracker", "Показывает попадание выкинутых зелий по игрокам", ModuleCategory.MISC);
    }

    @Override
    public void onDisable() {
        trackedPotions.clear();
        lastWorld = null;
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            trackedPotions.clear();
            lastWorld = null;
            return;
        }

        if (lastWorld != mc.world) {
            trackedPotions.clear();
            lastWorld = mc.world;
        }

        Set<Integer> currentPotions = new HashSet<>();

        double trackRadiusSq = TRACK_RADIUS * TRACK_RADIUS;
        Box searchBox = mc.player.getBoundingBox().expand(TRACK_RADIUS);
        for (PotionEntity potionEntity : mc.world.getEntitiesByClass(PotionEntity.class, searchBox, Entity::isAlive)) {
            if (mc.player.squaredDistanceTo(potionEntity) > trackRadiusSq) continue;

            PotionInfo potionInfo = getPotionInfo(potionEntity);
            if (potionInfo == null) continue;

            int entityId = potionEntity.getId();
            currentPotions.add(entityId);

            PotionData data = trackedPotions.get(entityId);
            if (data == null) {
                trackedPotions.put(entityId, new PotionData(
                        potionInfo,
                        potionEntity.getX(),
                        potionEntity.getY(),
                        potionEntity.getZ()
                ));
                continue;
            }

            data.lastX = potionEntity.getX();
            data.lastY = potionEntity.getY();
            data.lastZ = potionEntity.getZ();
            data.potionInfo = potionInfo;
        }

        Set<Integer> removedPotions = new HashSet<>(trackedPotions.keySet());
        removedPotions.removeAll(currentPotions);

        for (int entityId : removedPotions) {
            PotionData data = trackedPotions.remove(entityId);
            if (data != null) {
                printSplash(data);
            }
        }
    }

    private void printSplash(PotionData data) {
        Box potionBox = new Box(
                data.lastX - SPLASH_RADIUS,
                data.lastY - SPLASH_HEIGHT,
                data.lastZ - SPLASH_RADIUS,
                data.lastX + SPLASH_RADIUS,
                data.lastY + SPLASH_HEIGHT,
                data.lastZ + SPLASH_RADIUS
        );

        List<PlayerHit> hits = new ArrayList<>();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || !player.isAlive()) continue;
            if (!potionBox.contains(player.getPos())) continue;

            double dx = player.getX() - data.lastX;
            double dz = player.getZ() - data.lastZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > SPLASH_RADIUS) continue;

            double proximity = Math.max(0.0, 1.0 - distance / SPLASH_RADIUS);
            int percent = Math.max(1, Math.min(100, (int) Math.round(proximity * 100.0)));

            hits.add(new PlayerHit(player.getName().getString(), percent, distance));
        }

        hits.sort(Comparator.comparingDouble(PlayerHit::distance));

        for (int i = 0; i < Math.min(MAX_MESSAGES, hits.size()); i++) {
            PlayerHit hit = hits.get(i);
            sendPotionMessage(hit.playerName(), data.potionInfo, hit.percent());
        }
    }

    private PotionInfo getPotionInfo(PotionEntity potionEntity) {
        PotionContentsComponent contents = potionEntity.getStack().get(DataComponentTypes.POTION_CONTENTS);

        PotionInfo byEffects = getPotionInfo(contents);
        if (byEffects != null) {
            return byEffects;
        }

        return getPotionInfo(potionEntity.getStack().getName().getString());
    }

    private PotionInfo getPotionInfo(PotionContentsComponent contents) {
        if (contents == null || !contents.hasEffects()) return null;

        boolean regenerationTwo = hasEffect(contents, StatusEffects.REGENERATION, 1);
        boolean strengthFive = hasEffect(contents, StatusEffects.STRENGTH, 4);
        boolean healthBoostThree = hasEffect(contents, StatusEffects.HEALTH_BOOST, 2);
        boolean strengthFour = hasEffect(contents, StatusEffects.STRENGTH, 3);
        boolean speedThree = hasEffect(contents, StatusEffects.SPEED, 2);

        if (regenerationTwo) {
            return PotionInfo.HOLY_WATER;
        }
        if (strengthFive) {
            return PotionInfo.WRATH;
        }
        if (healthBoostThree) {
            return PotionInfo.PALADIN;
        }
        if (strengthFour && speedThree) {
            return PotionInfo.ASSASSIN;
        }
        if (strengthFour) {
            return PotionInfo.ASSASSIN;
        }

        return null;
    }

    private boolean hasEffect(PotionContentsComponent contents, RegistryEntry<StatusEffect> effect, int amplifier) {
        for (StatusEffectInstance instance : contents.getEffects()) {
            if (instance.getEffectType().equals(effect) && instance.getAmplifier() == amplifier) {
                return true;
            }
        }
        return false;
    }

    private PotionInfo getPotionInfo(String itemName) {
        String normalizedName = normalize(itemName);

        for (PotionInfo potionInfo : PotionInfo.values()) {
            if (normalizedName.contains(normalize(potionInfo.plainName()))) {
                return potionInfo;
            }
        }

        return null;
    }

    private String normalize(String text) {
        return text
                .replaceAll("§.", "")
                .replace("[", "")
                .replace("]", "")
                .replace("✦", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private void sendPotionMessage(String playerName, PotionInfo potionInfo, int percent) {
        if (mc.player == null) return;

        MutableText text = Text.literal("");
        text.append(gradientText("Lumis", ColorUtils.getThemeColor(0), ColorUtils.getThemeColor(90), true));
        text.append(Text.literal(" ⇒ ").setStyle(grayStyle()));
        text.append(Text.literal(playerName).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(PLAYER))));
        text.append(Text.literal(" получил ").setStyle(grayStyle()));
        text.append(gradientText(potionInfo.displayName, potionInfo.startColor, potionInfo.endColor, true));
        text.append(Text.literal(" " + percent + "%").setStyle(grayStyle()));

        mc.player.sendMessage(text, false);
    }

    private MutableText gradientText(String text, int startColor, int endColor, boolean bold) {
        MutableText result = Text.literal("");

        for (int i = 0; i < text.length(); i++) {
            float progress = text.length() <= 1 ? 0.0f : (float) i / (text.length() - 1);
            int color = ColorUtils.gradient(startColor, endColor, progress);
            result.append(Text.literal(String.valueOf(text.charAt(i)))
                    .setStyle(Style.EMPTY
                            .withBold(bold)
                            .withColor(TextColor.fromRgb(color))));
        }

        return result;
    }

    private Style grayStyle() {
        return Style.EMPTY.withColor(TextColor.fromRgb(GRAY));
    }

    private enum PotionInfo {
        HOLY_WATER("[✦] Святая вода", 0xFFF56B, 0xB8FF42),
        WRATH("[✦] Зелье Гнева", 0xC41212, 0xFFB13B),
        PALADIN("[✦] Зелье Паладина", 0xB8FF42, 0xFFF0A0),
        ASSASSIN("[✦] Зелье Ассасина", 0x555555, 0xB02A2A);

        private final String displayName;
        private final int startColor;
        private final int endColor;

        PotionInfo(String displayName, int startColor, int endColor) {
            this.displayName = displayName;
            this.startColor = startColor;
            this.endColor = endColor;
        }

        private String plainName() {
            int index = displayName.indexOf("] ");
            return index >= 0 ? displayName.substring(index + 2) : displayName;
        }
    }

    private static class PotionData {
        private PotionInfo potionInfo;
        private double lastX;
        private double lastY;
        private double lastZ;

        private PotionData(PotionInfo potionInfo, double lastX, double lastY, double lastZ) {
            this.potionInfo = potionInfo;
            this.lastX = lastX;
            this.lastY = lastY;
            this.lastZ = lastZ;
        }
    }

    private record PlayerHit(String playerName, int percent, double distance) {}
}

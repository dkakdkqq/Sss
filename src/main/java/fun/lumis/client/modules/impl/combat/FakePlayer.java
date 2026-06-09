package fun.lumis.client.modules.impl.combat;

import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import fun.lumis.api.QClient;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

/**
 * FakePlayer — модуль для спавна фейкового игрока, по которому можно тренироваться.
 * Полезно для тестирования ротаций, ауры и других combat-модулей.
 */
public class FakePlayer extends Module implements QClient {

    public static FakePlayer INSTANCE = new FakePlayer();

    private final ModeSetting mode = new ModeSetting("Режим", "Стоит", "Стоит", "Круг", "Хаос", "Следует");
    private final FloatSetting distance = new FloatSetting("Дистанция", 3.0f, 1.0f, 10.0f, 0.1f);
    private final FloatSetting speed = new FloatSetting("Скорость", 1.0f, 0.1f, 5.0f, 0.1f);
    private final BooleanSetting copyInventory = new BooleanSetting("Копировать инвентарь", true);
    private final BooleanSetting showInTab = new BooleanSetting("Показывать в табе", false);

    private OtherClientPlayerEntity fakePlayer;
    private double angle = 0.0;
    private Vec3d centerPos = Vec3d.ZERO;

    public FakePlayer() {
        super("FakePlayer", "Спавнит фейкового игрока для тренировки", ModuleCategory.COMBAT);
        addSettings(mode, distance, speed, copyInventory, showInTab);
    }

    @EventLink
    private void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        if (fakePlayer == null || !fakePlayer.isAlive()) {
            spawnFakePlayer();
        }

        updateFakePlayerPosition();
    }

    private void spawnFakePlayer() {
        if (mc.player == null || mc.world == null) return;

        Vec3d spawnPos = mc.player.getPos().add(0, 0, distance.getValue());
        
        fakePlayer = new OtherClientPlayerEntity(
                mc.world,
                mc.player.getGameProfile()
        ) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };

        fakePlayer.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        fakePlayer.setYaw(0);
        fakePlayer.setPitch(0);
        fakePlayer.setHealth(20.0f);
        fakePlayer.setGameMode(GameMode.SURVIVAL);

        if (copyInventory.isState() && mc.player != null) {
            fakePlayer.getInventory().clone(mc.player.getInventory());
        }

        mc.world.addEntity(fakePlayer.getId(), fakePlayer);
        centerPos = mc.player.getPos();
    }

    private void updateFakePlayerPosition() {
        if (fakePlayer == null || mc.player == null) return;

        Vec3d targetPos;
        double dist = distance.getValue();
        double spd = speed.getValue() * 0.05;

        switch (mode.getCurrent()) {
            case "Стоит" -> {
                if (centerPos.equals(Vec3d.ZERO)) {
                    centerPos = mc.player.getPos();
                }
                targetPos = centerPos.add(0, 0, dist);
            }
            case "Круг" -> {
                angle += spd;
                if (angle > 2 * Math.PI) angle -= 2 * Math.PI;
                targetPos = mc.player.getPos().add(
                        Math.cos(angle) * dist,
                        0,
                        Math.sin(angle) * dist
                );
            }
            case "Хаос" -> {
                angle += spd * (1 + Math.random() * 2);
                double radius = dist * (0.5 + Math.random() * 0.5);
                targetPos = mc.player.getPos().add(
                        Math.cos(angle) * radius,
                        (Math.random() - 0.5) * 2,
                        Math.sin(angle) * radius
                );
            }
            case "Следует" -> {
                Vec3d playerVel = mc.player.getVelocity();
                targetPos = mc.player.getPos().add(playerVel.multiply(5)).add(0, 0, dist);
            }
            default -> targetPos = mc.player.getPos().add(0, 0, dist);
        }

        // Плавное движение к целевой позиции
        Vec3d currentPos = fakePlayer.getPos();
        Vec3d diff = targetPos.subtract(currentPos);
        Vec3d newPos = currentPos.add(diff.multiply(0.3));

        fakePlayer.setPos(newPos.x, newPos.y, newPos.z);
        fakePlayer.setYaw((float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90);
        fakePlayer.setPitch((float) -Math.toDegrees(Math.atan2(diff.y, diff.horizontalLength())));

        // Обновление хитбокса
        fakePlayer.calculateDimensions();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (fakePlayer != null && mc.world != null) {
            mc.world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
            fakePlayer = null;
        }
        angle = 0.0;
        centerPos = Vec3d.ZERO;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        angle = 0.0;
        centerPos = Vec3d.ZERO;
        if (mc.player != null) {
            centerPos = mc.player.getPos();
        }
    }

    public LivingEntity getFakePlayer() {
        return fakePlayer;
    }

    public boolean isFakePlayer(Entity entity) {
        return entity == fakePlayer;
    }
}

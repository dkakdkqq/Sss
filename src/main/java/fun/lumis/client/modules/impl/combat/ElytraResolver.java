package fun.lumis.client.modules.impl.combat;

import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.player.InventoryUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ElytraResolver extends Module {

    public static ElytraResolver INSTANCE = new ElytraResolver();

    private final FloatSetting distance = new FloatSetting("Дистанция отлета", 6.0f, 4.0f, 8.0f, 0.1f);
    private final BooleanSetting autoFirework = new BooleanSetting("Авто-Фейерверк", true);

    private static final float MIN_HEIGHT = 4.0f;

    private boolean escaping;
    private Vec3d escapePos;
    private long escapeStartTime;
    private int returnFireworkTicks = -1;
    private Vec3d lastEscapeDirection;

    public ElytraResolver() {
        super("ElytraResolver", "Отлет на элитрах", ModuleCategory.COMBAT);
        addSettings(distance, autoFirework);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        escaping = false;
        escapePos = null;
        returnFireworkTicks = -1;
        lastEscapeDirection = null;
    }

    public void onAuraAttack() {
        if (!isEnable() || mc.player == null || !mc.player.isGliding()) return;

        Vec3d bestPos = calculateSmartEscape(mc.player.getPos(), distance.get());
        if (bestPos != null) {
            escapePos = bestPos;
            escaping = true;
            escapeStartTime = System.currentTimeMillis();

            if (autoFirework.isState()) {
                useFirework();
            }
        }
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || !mc.player.isGliding()) {
            escaping = false;
            returnFireworkTicks = -1;
            return;
        }

        if (returnFireworkTicks > 0) {
            returnFireworkTicks--;
        } else if (returnFireworkTicks == 0) {
            if (autoFirework.isState()) {
                useFirework();
            }
            returnFireworkTicks = -1;
        }

        if (escaping && escapePos != null) {
            double dist = mc.player.getPos().distanceTo(escapePos);
            if (dist < 2.0 || (System.currentTimeMillis() - escapeStartTime > 1000)) {
                escaping = false;
                if (autoFirework.isState()) {
                    returnFireworkTicks = 2;
                }
            }
        }
    }

    public boolean isEscaping() {
        return isEnable() && escaping && escapePos != null && mc.player != null && mc.player.isGliding();
    }

    public Vec3d getEscapePos() {
        return escapePos;
    }

    private Vec3d calculateSmartEscape(Vec3d pPos, float d) {
        Vec3d playerLook = mc.player.getRotationVector();
        Vec3d playerVelocity = mc.player.getVelocity();

        Vec3d[] directions = generateSmartDirections(playerLook, playerVelocity);
        List<EscapePoint> validPoints = new ArrayList<>();

        for (Vec3d dir : directions) {
            Vec3d target = pPos.add(dir.multiply(d));

            if (target.y < pPos.y + MIN_HEIGHT) {
                continue;
            }

            RaycastContext context = new RaycastContext(
                    pPos, target,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            );

            BlockHitResult hit = mc.world.raycast(context);
            double actualDistance = d;
            Vec3d finalPos = target;

            if (hit.getType() != HitResult.Type.MISS) {
                double hitDist = hit.getPos().distanceTo(pPos);
                if (hitDist > 2.0) {
                    actualDistance = hitDist;
                    finalPos = hit.getPos().add(dir.multiply(-1.0));
                } else {
                    continue;
                }
            }

            double score = calculateEscapeScore(dir, playerLook, playerVelocity, actualDistance, finalPos);
            validPoints.add(new EscapePoint(finalPos, actualDistance, score));
        }

        if (validPoints.isEmpty()) return null;

        validPoints.sort(Comparator.comparingDouble(p -> -p.score));
        lastEscapeDirection = validPoints.get(0).pos.subtract(pPos).normalize();
        return validPoints.get(0).pos;
    }

    private Vec3d[] generateSmartDirections(Vec3d playerLook, Vec3d velocity) {
        Vec3d back = new Vec3d(-playerLook.x, 0, -playerLook.z).normalize();
        Vec3d right = new Vec3d(-playerLook.z, 0, playerLook.x).normalize();
        Vec3d left = right.multiply(-1);
        Vec3d up = new Vec3d(0, 1, 0);

        List<Vec3d> dirs = new ArrayList<>();

        dirs.add(back.add(up).normalize());
        dirs.add(back.add(right).add(up).normalize());
        dirs.add(back.add(left).add(up).normalize());
        dirs.add(right.add(up).normalize());
        dirs.add(left.add(up).normalize());
        dirs.add(back.add(right.multiply(0.5)).add(up.multiply(1.5)).normalize());
        dirs.add(back.add(left.multiply(0.5)).add(up.multiply(1.5)).normalize());
        dirs.add(back.add(up.multiply(2)).normalize());
        dirs.add(right.add(up.multiply(1.5)).normalize());
        dirs.add(left.add(up.multiply(1.5)).normalize());
        dirs.add(back.multiply(0.7).add(right.multiply(0.3)).add(up.multiply(1.2)).normalize());
        dirs.add(back.multiply(0.7).add(left.multiply(0.3)).add(up.multiply(1.2)).normalize());
        dirs.add(back.multiply(0.5).add(up.multiply(1.8)).normalize());
        dirs.add(right.multiply(0.8).add(up.multiply(1.3)).normalize());
        dirs.add(left.multiply(0.8).add(up.multiply(1.3)).normalize());

        if (velocity.lengthSquared() > 0.01) {
            Vec3d perpendicular = new Vec3d(-velocity.z, 0, velocity.x).normalize();
            dirs.add(perpendicular.add(up).normalize());
            dirs.add(perpendicular.multiply(-1).add(up).normalize());
            dirs.add(perpendicular.add(up.multiply(1.5)).normalize());
            dirs.add(perpendicular.multiply(-1).add(up.multiply(1.5)).normalize());
        }

        return dirs.toArray(new Vec3d[0]);
    }

    private double calculateEscapeScore(Vec3d direction, Vec3d playerLook, Vec3d velocity, double distance, Vec3d finalPos) {
        double score = 0.0;

        double backwardBonus = -direction.dotProduct(new Vec3d(playerLook.x, 0, playerLook.z).normalize());
        score += backwardBonus * 30.0;

        score += direction.y * 25.0;

        score += distance * 2.0;

        if (velocity.lengthSquared() > 0.01) {
            Vec3d velNorm = velocity.normalize();
            double perpendicular = Math.abs(direction.dotProduct(new Vec3d(-velNorm.z, 0, velNorm.x)));
            score += perpendicular * 15.0;
        }

        if (lastEscapeDirection != null) {
            double similarity = direction.dotProduct(lastEscapeDirection);
            if (similarity > 0.7) {
                score -= 20.0;
            }
        }

        double groundDistance = finalPos.y - mc.world.getBottomY();
        if (groundDistance < 10.0) {
            score -= (10.0 - groundDistance) * 5.0;
        }

        return score;
    }

    private void useFirework() {
        if (mc.player == null) return;
        int slotFirework = InventoryUtils.getItemSlot(Items.FIREWORK_ROCKET);
        if (slotFirework != -1) {
            InventoryUtils.swapAndUseHvH(Items.FIREWORK_ROCKET);
        }
    }

    private static class EscapePoint {
        Vec3d pos;
        double distance;
        double score;

        EscapePoint(Vec3d pos, double distance, double score) {
            this.pos = pos;
            this.distance = distance;
            this.score = score;
        }
    }
}
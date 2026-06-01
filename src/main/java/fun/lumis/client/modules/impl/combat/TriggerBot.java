package fun.lumis.client.modules.impl.combat;

import net.minecraft.util.Hand;

import fun.lumis.Lumis;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventMoveInput;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.combat.IdealHitUtils;
import fun.lumis.api.utils.math.TimerUtils;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.player.AutoEat;
import lombok.Getter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.CodEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.*;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ListSetting;
import fun.lumis.client.modules.impl.movement.Sprint;

public class TriggerBot extends Module {

    public static TriggerBot INSTANCE = new TriggerBot();

    private final FloatSetting range = new FloatSetting("Дистанция атаки", 3f, 0f, 6f, 0.05f);
    
    private final ListSetting options = new ListSetting("Опции",
            new BooleanSetting("Умные криты", true),
            new BooleanSetting("Сброс спринта", true),
            new BooleanSetting("Бить через стены", false),
            new BooleanSetting("Проверка на наведение", true),
            new BooleanSetting("Отжимать щит", false),
            new BooleanSetting("Ломать щит", true)
    );
    
    private final ListSetting targets = new ListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Невидимки", true),
            new BooleanSetting("Мирные", false),
            new BooleanSetting("Мобы", true)
    );

    @Getter
    private LivingEntity target;
    
    private final TimerUtils attackTimer = new TimerUtils();
    
    private boolean needSprintReset = false;
    private boolean sprintResetDone = false;
    private int sprintResetTicks = 0;

    public TriggerBot() {
        super("TriggerBot", "Автоматически атакует при наведении на цель", ModuleCategory.COMBAT);
        addSettings(range, options, targets);
    }

    @EventLink
    public void onMoveInput(EventMoveInput event) {
        if (needSprintReset) {
            event.setForward(0);
            event.setStrafe(0);
            needSprintReset = false;
            sprintResetDone = true;
            sprintResetTicks = 0;
        }
    }

    
    @EventLink
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) return;
        if (AutoEat.shouldSuppressCombat()) {
            target = null;
            resetSprintState();
            return;
        }

        if (sprintResetDone) {
            sprintResetTicks++;
        }

        target = getTargetUnderCrosshair();
        
        if (target != null) {
            processAttack();
        } else {
            resetSprintState();
        }
    }

    private void processAttack() {
        if (!shouldAttack()) return;

        if (options.is("Сброс спринта") && mc.player.isSprinting() && !sprintResetDone && !shouldSkipSprintResetInWater()) {
            needSprintReset = true;
            return;
        }

        if (options.is("Сброс спринта") && sprintResetDone && sprintResetTicks < 1) {
            return;
        }

        attack();
        sprintResetDone = false;
        sprintResetTicks = 0;
    }

    
    private LivingEntity getTargetUnderCrosshair() {
        Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
        Vec3d lookVec = mc.player.getRotationVec(1.0F);
        float rangeValue = range.getValue().floatValue();
        Vec3d reachVec = eyePos.add(lookVec.multiply(rangeValue));

        EntityHitResult result = ProjectileUtil.raycast(
                mc.player,
                eyePos,
                reachVec,
                mc.player.getBoundingBox().expand(rangeValue),
                entity -> entity != mc.player && entity.isAlive() && entity instanceof LivingEntity,
                rangeValue * rangeValue
        );

        if (result != null && result.getEntity() instanceof LivingEntity living) {
            if (isValidTarget(living)) {
                return living;
            }
        }
        
        return null;
    }

    
    private void attack() {
        if (options.is("Отжимать щит") && mc.player.isBlocking()) {
            mc.interactionManager.stopUsingItem(mc.player);
        }

        if (target instanceof PlayerEntity player && player.isBlocking() && options.is("Ломать щит")) {
            shieldBreak(player);
        } else {
            mc.interactionManager.attackEntity(mc.player, target);
        }
        
        mc.player.swingHand(Hand.MAIN_HAND);
        attackTimer.reset();
    }

    
    private void shieldBreak(PlayerEntity entity) {
        int axeSlot = findAxeSlot();
        
        if (axeSlot != -1) {
            int prevSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = axeSlot;
            mc.interactionManager.attackEntity(mc.player, entity);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.getInventory().selectedSlot = prevSlot;
        } else {
            mc.interactionManager.attackEntity(mc.player, entity);
        }
    }

    private int findAxeSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null || entity == mc.player) return false;
        if (!entity.isAlive() || entity.getHealth() <= 0) return false;
        if (entity instanceof ArmorStandEntity) return false;

        if (entity instanceof PlayerEntity player) {
            if (!targets.is("Игроки")) return false;
            if (player.hasStatusEffect(StatusEffects.INVISIBILITY) && !targets.is("Невидимки")) return false;
            if (Lumis.INSTANCE.friendStorage.isFriend(entity.getName().getString())) return false;
        } else if (entity instanceof PassiveEntity || entity instanceof CodEntity) {
            if (!targets.is("Мирные")) return false;
        } else if (entity instanceof HostileEntity) {
            if (!targets.is("Мобы")) return false;
        }

        if (mc.player.getEyePos().distanceTo(entity.getBoundingBox().getCenter()) > range.getValue().floatValue()) {
            return false;
        }
        
        if (!options.is("Бить через стены") && !mc.player.canSee(entity)) {
            return false;
        }

        return true;
    }

    private boolean shouldAttack() {
        if (mc.player.getAttackCooldownProgress(1.5f) < IdealHitUtils.getAICooldown()) {
            return false;
        }

        if (options.is("Проверка на наведение")) {
            Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
            Vec3d lookVec = mc.player.getRotationVec(1.0F);
            float rangeValue = range.getValue().floatValue();
            Vec3d reachVec = eyePos.add(lookVec.multiply(rangeValue));

            EntityHitResult result = ProjectileUtil.raycast(
                    mc.player,
                    eyePos,
                    reachVec,
                    mc.player.getBoundingBox().expand(rangeValue),
                    ex -> ex != mc.player && ex.isAlive(),
                    rangeValue * rangeValue
            );

            if (result == null || result.getEntity() != target) {
                return false;
            }
        }

        if (options.is("Умные криты") && !IdealHitUtils.canCritical(target)) {
            return false;
        }
        
        return true;
    }

    private void resetSprintState() {
        sprintResetDone = false;
        sprintResetTicks = 0;
    }

    private boolean shouldSkipSprintResetInWater() {
        return mc.player != null
                && (mc.player.isTouchingWater() || mc.player.isSubmergedInWater())
                && Sprint.INSTANCE != null
                && Sprint.INSTANCE.shouldKeepSprintInWater();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        target = null;
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
    }
}

package fun.lumis.client.modules.impl.player;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import fun.lumis.api.events.EventInvoker;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.api.utils.bot.BotSessionManager;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AutoForest extends Module {

    public static AutoForest INSTANCE = new AutoForest();

    private static final double MAX_RANGE = 4.0D;
    private static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;
    private static final long DEFAULT_BREAK_DELAY_MS = 3L;
    private static final float AUTO_FAST_BREAK_SPEED = 1.0f;
    private static final float DEFAULT_PACKETS_PER_SECOND = 100.0f;
    private static final long VISUAL_TTL_MS = 300_000L;
    private static final long NICK_REMINDER_DELAY_MS = 5_000L;
    private static final String MODE_NORMAL_ALIAS = "normal";
    private static final String MODE_FAST_ALIAS = "fast";

    private final ModeSetting breakMode = new ModeSetting("Режим ломания", "Обычный", "Обычный", "Быстрый");
    private final FloatSetting packetsPerSecond = new FloatSetting("Пакетов в секунду", DEFAULT_PACKETS_PER_SECOND, 1.0f, 100.0f, 1.0f)
            .visible(() -> breakMode.is("Быстрый"));
    private final FloatSetting breakRadius = new FloatSetting("Радиус", 4.0f, 1.0f, 6.0f, 0.5f);
    private final BooleanSetting swing = new BooleanSetting("Махать рукой", true);
    private final BooleanSetting autoSell = new BooleanSetting("Авто продажа дерева", true);
    private final BooleanSetting autoPay = new BooleanSetting("AutoPay", false);
    private final BooleanSetting preserveVisuals = new BooleanSetting("Сохранять визуализацию", true);
    private final FloatSetting payAmount = new FloatSetting("Сумма перевода", 1000.0f, 500.0f, 25000.0f, 500.0f)
            .visible(autoPay::isState);
    private final FloatSetting intervalSeconds = new FloatSetting("Задержка", 20.0f, 1.0f, 60.0f, 1.0f);

    private final Map<BlockPos, BlockState> preservedBlocks = new HashMap<>();
    private final Map<BlockPos, Long> lastUpdateTime = new HashMap<>();
    private final Set<BlockPos> managedBlocks = new HashSet<>();

    private boolean currentSessionEnabled;
    private BlockPos targetPos;
    private String payTarget = "";
    private long lastBreakTime;
    private long lastPacketTime;
    private long lastSellTime;
    private long lastPayTime;
    private long lastNickReminderTime;

    public AutoForest() {
        super("AutoForest", "Автоматически ломает бревна и переводит деньги", ModuleCategory.PLAYER);
        addSettings(breakMode, packetsPerSecond, breakRadius, swing, autoSell, autoPay, preserveVisuals, payAmount, intervalSeconds);
        EventInvoker.register(this);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (currentSessionEnabled && mc.player != null && mc.world != null) {
            tickCurrentSession();
        }

        tickFrozenBots();
    }

    private void tickCurrentSession() {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            targetPos = null;
            return;
        }

        long now = System.currentTimeMillis();
        long scheduleDelay = Math.max(1000L, (long) (intervalSeconds.get() * 500.0f));

        if (autoSell.isState() && now - lastSellTime >= scheduleDelay) {
            mc.getNetworkHandler().sendChatCommand("sellwood");
            lastSellTime = now;
        }

        if (autoPay.isState()) {
            if (payTarget.isBlank()) {
                if (now - lastNickReminderTime >= NICK_REMINDER_DELAY_MS) {
                    lastNickReminderTime = now;
                    ChatUtils.sendMessage("Укажите ник для перевода через .autoles pay <nick>");
                }
            } else if (now - lastPayTime >= scheduleDelay + 200L) {
                mc.getNetworkHandler().sendChatCommand("pay " + payTarget + " " + (int) payAmount.get());
                lastPayTime = now;
            }
        }

        if (targetPos != null && (!isLog(targetPos) || !isInRange(targetPos) || !isVisible(targetPos))) {
            targetPos = null;
        }

        if (targetPos == null) {
            targetPos = findNearestLog();
        }

        if (targetPos != null) {
            breakTarget(now);
        }

        if (preserveVisuals.isState()) {
            updateVisualization(now);
        }
    }

    private void tickFrozenBots() {
        for (BotSessionManager.BotConnection bot : BotSessionManager.getConnections()) {
            SessionState state = bot.autoForestState();
            if (state == null || !state.enabled() || bot.player() == null || bot.world() == null || bot.handler() == null) {
                continue;
            }

            try {
                tickBotSession(bot, state);
            } catch (Exception ignored) {
                state.enabled(false);
                state.targetPos(null);
            }
        }
    }

    private void tickBotSession(BotSessionManager.BotConnection bot, SessionState state) {
        if (bot.player().isRemoved() || !bot.player().isAlive()) {
            state.enabled(false);
            state.targetPos(null);
            return;
        }

        long now = System.currentTimeMillis();
        long scheduleDelay = Math.max(1000L, (long) (Math.max(1.0f, state.intervalSeconds()) * 500.0f));

        if (state.autoSell() && now - state.lastSellTime() >= scheduleDelay) {
            bot.handler().sendChatCommand("sellwood");
            state.lastSellTime(now);
        }

        if (state.autoPay()) {
            if (state.payTarget().isBlank()) {
                if (now - state.lastNickReminderTime() >= NICK_REMINDER_DELAY_MS) {
                    state.lastNickReminderTime(now);
                }
            } else if (now - state.lastPayTime() >= scheduleDelay + 200L) {
                bot.handler().sendChatCommand("pay " + state.payTarget() + " " + (int) state.payAmount());
                state.lastPayTime(now);
            }
        }

        if (state.targetPos() != null && (!isLog(bot.world(), state.targetPos()) || !isInRange(bot.player(), state.targetPos()) || !isVisible(bot.world(), bot.player(), state.targetPos()))) {
            state.targetPos(null);
        }

        if (state.targetPos() == null) {
            state.targetPos(findNearestLog(bot.world(), bot.player(), state.breakRadius()));
        }

        if (state.targetPos() == null) {
            return;
        }

        if (MODE_FAST_ALIAS.equals(state.modeAlias())) {
            long interval = Math.max(1L, (long) (1000.0f / Math.max(1.0f, state.packetsPerSecond())));
            if (now - state.lastPacketTime() < interval) {
                return;
            }

            performFastBreak(bot.handler(), bot.interactionManager(), bot.player(), bot.world(), state.targetPos(), state.swing());
            state.lastPacketTime(now);
            return;
        }

        if (now - state.lastBreakTime() < DEFAULT_BREAK_DELAY_MS) {
            return;
        }

        if (bot.interactionManager() != null) {
            bot.interactionManager().attackBlock(state.targetPos(), Direction.UP);
            bot.interactionManager().updateBlockBreakingProgress(state.targetPos(), Direction.UP);
        } else {
            performFastBreak(bot.handler(), bot.interactionManager(), bot.player(), bot.world(), state.targetPos(), state.swing());
        }

        if (state.swing()) {
            bot.handler().sendPacket(new net.minecraft.network.packet.c2s.play.HandSwingC2SPacket(Hand.MAIN_HAND));
        }
        state.lastBreakTime(now);
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (!currentSessionEnabled || !preserveVisuals.isState() || mc.player == null || mc.world == null) {
            return;
        }

        if (event.getType() == EventPacket.Type.SEND && event.getPacket() instanceof PlayerActionC2SPacket packet) {
            handleDigPacket(packet);
            return;
        }

        if (event.getType() == EventPacket.Type.RECEIVE && event.getPacket() instanceof BlockUpdateS2CPacket packet) {
            BlockPos pos = packet.getPos();
            BlockState savedState = preservedBlocks.get(pos);
            if (savedState == null) {
                return;
            }

            BlockState serverState = packet.getState();
            if (serverState.isAir() || !serverState.equals(savedState)) {
                event.cancel();
                setClientBlock(pos, savedState);
                lastUpdateTime.put(pos, System.currentTimeMillis());
            }
        }
    }

    private void breakTarget(long now) {
        if (targetPos == null || mc.player == null || mc.player.networkHandler == null || mc.interactionManager == null) {
            return;
        }

        if (breakMode.is("Быстрый")) {
            long interval = Math.max(1L, (long) (1000.0f / Math.max(1.0f, packetsPerSecond.get())));
            if (now - lastPacketTime < interval) {
                return;
            }

            performFastBreak(targetPos);
            lastPacketTime = now;
            return;
        }

        if (now - lastBreakTime < DEFAULT_BREAK_DELAY_MS) {
            return;
        }

        mc.interactionManager.attackBlock(targetPos, Direction.UP);
        mc.interactionManager.updateBlockBreakingProgress(targetPos, Direction.UP);
        if (swing.isState()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        lastBreakTime = now;
    }

    private void performFastBreak(BlockPos pos) {
        if (mc.player == null || mc.world == null || mc.player.networkHandler == null) {
            return;
        }

        performFastBreak(mc.player.networkHandler, mc.interactionManager, mc.player, mc.world, pos, swing.isState());
    }

    private void performFastBreak(net.minecraft.client.network.ClientPlayNetworkHandler handler,
                                  net.minecraft.client.network.ClientPlayerInteractionManager interactionManager,
                                  net.minecraft.client.network.ClientPlayerEntity player,
                                  ClientWorld world,
                                  BlockPos pos,
                                  boolean shouldSwing) {
        if (handler == null || player == null || pos == null) {
            return;
        }

        boolean accelerated = false;
        if (interactionManager != null && world != null) {
            interactionManager.attackBlock(pos, Direction.UP);
            accelerated = FastBreak.accelerateClientBreak(
                    interactionManager,
                    player,
                    world,
                    pos,
                    Direction.UP,
                    AUTO_FAST_BREAK_SPEED,
                    shouldSwing
            );
        }

        if (!accelerated) {
            FastBreak.packetBreak(handler, player, pos, Direction.UP, shouldSwing);
        }
    }

    private BlockPos findNearestLog() {
        return findNearestLog(mc.world, mc.player, breakRadius.get());
    }

    private BlockPos findNearestLog(net.minecraft.client.world.ClientWorld world, net.minecraft.client.network.ClientPlayerEntity player, float radiusValue) {
        if (player == null || world == null) {
            return null;
        }

        BlockPos playerPos = player.getBlockPos();
        int radius = Math.round(radiusValue);

        return BlockPos.stream(
                        playerPos.add(-radius, -radius, -radius),
                        playerPos.add(radius, radius, radius)
                )
                .map(BlockPos::toImmutable)
                .filter(pos -> isLog(world, pos))
                .filter(pos -> isInRange(player, pos))
                .filter(pos -> isVisible(world, player, pos))
                .min(Comparator.comparingDouble(pos -> player.squaredDistanceTo(Vec3d.ofCenter(pos))))
                .orElse(null);
    }

    private boolean isInRange(BlockPos pos) {
        return isInRange(mc.player, pos);
    }

    private boolean isInRange(net.minecraft.client.network.ClientPlayerEntity player, BlockPos pos) {
        return player != null && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= MAX_RANGE_SQ;
    }

    private boolean isVisible(BlockPos pos) {
        return isVisible(mc.world, mc.player, pos);
    }

    private boolean isVisible(net.minecraft.client.world.ClientWorld world, net.minecraft.client.network.ClientPlayerEntity player, BlockPos pos) {
        if (player == null || world == null) {
            return false;
        }

        Vec3d eyePos = player.getEyePos();
        Vec3d targetCenter = Vec3d.ofCenter(pos);
        BlockHitResult hit = world.raycast(new RaycastContext(
                eyePos,
                targetCenter,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        return hit == null || hit.getType() == HitResult.Type.MISS || pos.equals(hit.getBlockPos());
    }

    private boolean isLog(BlockPos pos) {
        return isLog(mc.world, pos);
    }

    private boolean isLog(net.minecraft.client.world.ClientWorld world, BlockPos pos) {
        return world != null && world.getBlockState(pos).isIn(BlockTags.LOGS);
    }

    private void handleDigPacket(PlayerActionC2SPacket packet) {
        PlayerActionC2SPacket.Action action = packet.getAction();
        if (action != PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                && action != PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
            return;
        }

        BlockPos pos = packet.getPos();
        if (!isLog(pos)) {
            return;
        }

        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        preservedBlocks.put(pos, state);
        managedBlocks.add(pos);
        lastUpdateTime.put(pos, System.currentTimeMillis());
        setClientBlock(pos, state);
    }

    private void updateVisualization(long now) {
        if (!(mc.world instanceof ClientWorld clientWorld)) {
            return;
        }

        Set<BlockPos> toRemove = new HashSet<>();

        for (Map.Entry<BlockPos, BlockState> entry : preservedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState savedState = entry.getValue();
            BlockState currentState = clientWorld.getBlockState(pos);

            if (currentState == null || !currentState.equals(savedState)) {
                clientWorld.setBlockState(pos, savedState, 0);
                lastUpdateTime.put(pos, now);
            }

            Long lastSeen = lastUpdateTime.get(pos);
            if (lastSeen != null && now - lastSeen > VISUAL_TTL_MS) {
                toRemove.add(pos);
            }
        }

        for (BlockPos pos : toRemove) {
            preservedBlocks.remove(pos);
            lastUpdateTime.remove(pos);
            managedBlocks.remove(pos);
        }
    }

    private void restoreVisualState() {
        if (!(mc.world instanceof ClientWorld clientWorld)) {
            preservedBlocks.clear();
            lastUpdateTime.clear();
            managedBlocks.clear();
            return;
        }

        for (BlockPos pos : managedBlocks) {
            clientWorld.setBlockState(pos, mc.world.getBlockState(pos), 0);
        }

        preservedBlocks.clear();
        lastUpdateTime.clear();
        managedBlocks.clear();
    }

    private void setClientBlock(BlockPos pos, BlockState state) {
        if (mc.world instanceof ClientWorld clientWorld) {
            clientWorld.setBlockState(pos, state, 0);
        }
    }

    public List<String> getModeSuggestions() {
        return List.of(MODE_NORMAL_ALIAS, MODE_FAST_ALIAS);
    }

    public boolean setModeAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return false;
        }

        return switch (alias.trim().toLowerCase(Locale.ROOT)) {
            case MODE_NORMAL_ALIAS, "default", "обычный" -> {
                breakMode.set(breakMode.getMods().get(0));
                yield true;
            }
            case MODE_FAST_ALIAS, "quick", "быстрый" -> {
                if (breakMode.getMods().size() < 2) {
                    yield false;
                }
                breakMode.set(breakMode.getMods().get(1));
                yield true;
            }
            default -> false;
        };
    }

    public String getModeAlias() {
        if (breakMode.getMods().size() > 1 && breakMode.is(breakMode.getMods().get(1))) {
            return MODE_FAST_ALIAS;
        }
        return MODE_NORMAL_ALIAS;
    }

    public void enableForCurrentSession() {
        currentSessionEnabled = true;
        resetRuntimeState();
        restoreVisualState();
    }

    public void disableForCurrentSession() {
        currentSessionEnabled = false;
        restoreVisualState();
        resetRuntimeState();
    }

    public boolean isCurrentSessionEnabled() {
        return currentSessionEnabled;
    }

    public void setSwingEnabled(boolean value) {
        swing.setState(value);
    }

    public boolean isSwingEnabled() {
        return swing.isState();
    }

    public void setAutoSellEnabled(boolean value) {
        autoSell.setState(value);
    }

    public boolean isAutoSellEnabled() {
        return autoSell.isState();
    }

    public void setAutoPayEnabled(boolean value) {
        autoPay.setState(value);
        if (!value) {
            lastNickReminderTime = 0L;
        }
    }

    public boolean isAutoPayEnabled() {
        return autoPay.isState();
    }

    public void setPreserveVisualsEnabled(boolean value) {
        preserveVisuals.setState(value);
    }

    public boolean isPreserveVisualsEnabled() {
        return preserveVisuals.isState();
    }

    public void setPacketsPerSecond(float value) {
        packetsPerSecond.setValue(value);
    }

    public float getPacketsPerSecond() {
        return packetsPerSecond.get();
    }

    public void setBreakRadius(float value) {
        breakRadius.setValue(value);
    }

    public float getBreakRadius() {
        return breakRadius.get();
    }

    public void setPayAmount(float value) {
        payAmount.setValue(value);
    }

    public float getPayAmount() {
        return payAmount.get();
    }

    public void setIntervalSeconds(float value) {
        intervalSeconds.setValue(value);
    }

    public float getIntervalSeconds() {
        return intervalSeconds.get();
    }

    public boolean setPayTarget(String target) {
        String trimmed = target == null ? "" : target.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        payTarget = trimmed;
        lastNickReminderTime = 0L;
        return true;
    }

    public String getPayTarget() {
        return payTarget;
    }

    public boolean capturePayTargetFromChat(String message) {
        return false;
    }

    public void clearPayTarget() {
        payTarget = "";
        lastNickReminderTime = 0L;
    }

    public SessionState captureState() {
        SessionState state = new SessionState();
        state.enabled(currentSessionEnabled);
        state.modeAlias(getModeAlias());
        state.packetsPerSecond(packetsPerSecond.get());
        state.breakRadius(breakRadius.get());
        state.swing(swing.isState());
        state.autoSell(autoSell.isState());
        state.autoPay(autoPay.isState());
        state.preserveVisuals(preserveVisuals.isState());
        state.payAmount(payAmount.get());
        state.intervalSeconds(intervalSeconds.get());
        state.payTarget(payTarget);
        state.targetPos(targetPos);
        state.lastBreakTime(lastBreakTime);
        state.lastPacketTime(lastPacketTime);
        state.lastSellTime(lastSellTime);
        state.lastPayTime(lastPayTime);
        state.lastNickReminderTime(lastNickReminderTime);
        state.preservedBlocks(new HashMap<>(preservedBlocks));
        state.lastUpdateTime(new HashMap<>(lastUpdateTime));
        state.managedBlocks(new HashSet<>(managedBlocks));
        return state;
    }

    public void applyState(SessionState state) {
        if (state == null) {
            resetToDefaults();
            return;
        }

        currentSessionEnabled = state.enabled();
        setModeAlias(state.modeAlias());
        packetsPerSecond.setValue(state.packetsPerSecond());
        breakRadius.setValue(state.breakRadius());
        swing.setState(state.swing());
        autoSell.setState(state.autoSell());
        autoPay.setState(state.autoPay());
        preserveVisuals.setState(state.preserveVisuals());
        payAmount.setValue(state.payAmount());
        intervalSeconds.setValue(state.intervalSeconds());
        payTarget = state.payTarget();
        targetPos = state.targetPos();
        lastBreakTime = state.lastBreakTime();
        lastPacketTime = state.lastPacketTime();
        lastSellTime = state.lastSellTime();
        lastPayTime = state.lastPayTime();
        lastNickReminderTime = state.lastNickReminderTime();
        preservedBlocks.clear();
        preservedBlocks.putAll(state.preservedBlocks());
        lastUpdateTime.clear();
        lastUpdateTime.putAll(state.lastUpdateTime());
        managedBlocks.clear();
        managedBlocks.addAll(state.managedBlocks());
    }

    public void resetToDefaults() {
        currentSessionEnabled = false;
        setModeAlias(MODE_NORMAL_ALIAS);
        packetsPerSecond.setValue(DEFAULT_PACKETS_PER_SECOND);
        breakRadius.setValue(4.0f);
        swing.setState(true);
        autoSell.setState(true);
        autoPay.setState(false);
        preserveVisuals.setState(true);
        payAmount.setValue(1000.0f);
        intervalSeconds.setValue(20.0f);
        payTarget = "";
        restoreVisualState();
        resetRuntimeState();
    }

    private void resetRuntimeState() {
        targetPos = null;
        lastBreakTime = 0L;
        lastPacketTime = 0L;
        lastSellTime = 0L;
        lastPayTime = 0L;
        lastNickReminderTime = 0L;
        preservedBlocks.clear();
        lastUpdateTime.clear();
        managedBlocks.clear();
    }

    public static final class SessionState {
        private boolean enabled;
        private String modeAlias = MODE_NORMAL_ALIAS;
        private float packetsPerSecond = DEFAULT_PACKETS_PER_SECOND;
        private float breakRadius = 4.0f;
        private boolean swing = true;
        private boolean autoSell = true;
        private boolean autoPay;
        private boolean preserveVisuals = true;
        private float payAmount = 1000.0f;
        private float intervalSeconds = 20.0f;
        private String payTarget = "";
        private BlockPos targetPos;
        private long lastBreakTime;
        private long lastPacketTime;
        private long lastSellTime;
        private long lastPayTime;
        private long lastNickReminderTime;
        private Map<BlockPos, BlockState> preservedBlocks = new HashMap<>();
        private Map<BlockPos, Long> lastUpdateTime = new HashMap<>();
        private Set<BlockPos> managedBlocks = new HashSet<>();

        public boolean enabled() { return enabled; }
        public void enabled(boolean value) { this.enabled = value; }
        public String modeAlias() { return modeAlias; }
        public void modeAlias(String value) { this.modeAlias = value == null ? MODE_NORMAL_ALIAS : value; }
        public float packetsPerSecond() { return packetsPerSecond; }
        public void packetsPerSecond(float value) { this.packetsPerSecond = value; }
        public float breakRadius() { return breakRadius; }
        public void breakRadius(float value) { this.breakRadius = value; }
        public boolean swing() { return swing; }
        public void swing(boolean value) { this.swing = value; }
        public boolean autoSell() { return autoSell; }
        public void autoSell(boolean value) { this.autoSell = value; }
        public boolean autoPay() { return autoPay; }
        public void autoPay(boolean value) { this.autoPay = value; }
        public boolean preserveVisuals() { return preserveVisuals; }
        public void preserveVisuals(boolean value) { this.preserveVisuals = value; }
        public float payAmount() { return payAmount; }
        public void payAmount(float value) { this.payAmount = value; }
        public float intervalSeconds() { return intervalSeconds; }
        public void intervalSeconds(float value) { this.intervalSeconds = value; }
        public String payTarget() { return payTarget == null ? "" : payTarget; }
        public void payTarget(String value) { this.payTarget = value == null ? "" : value; }
        public BlockPos targetPos() { return targetPos; }
        public void targetPos(BlockPos value) { this.targetPos = value; }
        public long lastBreakTime() { return lastBreakTime; }
        public void lastBreakTime(long value) { this.lastBreakTime = value; }
        public long lastPacketTime() { return lastPacketTime; }
        public void lastPacketTime(long value) { this.lastPacketTime = value; }
        public long lastSellTime() { return lastSellTime; }
        public void lastSellTime(long value) { this.lastSellTime = value; }
        public long lastPayTime() { return lastPayTime; }
        public void lastPayTime(long value) { this.lastPayTime = value; }
        public long lastNickReminderTime() { return lastNickReminderTime; }
        public void lastNickReminderTime(long value) { this.lastNickReminderTime = value; }
        public Map<BlockPos, BlockState> preservedBlocks() { return preservedBlocks; }
        public void preservedBlocks(Map<BlockPos, BlockState> value) { this.preservedBlocks = value == null ? new HashMap<>() : value; }
        public Map<BlockPos, Long> lastUpdateTime() { return lastUpdateTime; }
        public void lastUpdateTime(Map<BlockPos, Long> value) { this.lastUpdateTime = value == null ? new HashMap<>() : value; }
        public Set<BlockPos> managedBlocks() { return managedBlocks; }
        public void managedBlocks(Set<BlockPos> value) { this.managedBlocks = value == null ? new HashSet<>() : value; }
    }
}

package fun.lumis.api.utils.bot;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.session.Session;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.client.modules.impl.player.AutoForest;
import fun.lumis.mixin.IMinecraftClientAccessor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BotSessionManager {
    private static final List<BotConnection> connections = new CopyOnWriteArrayList<>();
    private static volatile boolean ignoreBotMessages;
    private static volatile boolean bypassResourcePacksDuringBotConnect;

    public static List<BotConnection> getConnections() {
        pruneDeadConnections();
        return new ArrayList<>(connections);
    }

    public static boolean shouldBypassResourcePacks() {
        return bypassResourcePacksDuringBotConnect;
    }

    public static void finishBotConnectStage() {
        bypassResourcePacksDuringBotConnect = false;
    }

    public static String getCurrentSessionName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.getSession() == null ? "" : mc.getSession().getUsername();
    }

    public static List<String> getSessionNames(boolean includeCurrent) {
        pruneDeadConnections();
        Set<String> names = new LinkedHashSet<>();
        if (includeCurrent) {
            String currentName = getCurrentSessionName();
            if (!currentName.isBlank()) {
                names.add(currentName);
            }
        }

        for (BotConnection bot : connections) {
            if (bot.name() != null && !bot.name().isBlank()) {
                names.add(bot.name());
            }
        }
        return new ArrayList<>(names);
    }

    public static boolean toggleIgnoreBotMessages() {
        ignoreBotMessages = !ignoreBotMessages;
        return ignoreBotMessages;
    }

    public static boolean isIgnoreBotMessages() {
        return ignoreBotMessages;
    }

    public static void connect(String name, String address) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null || name == null || name.isBlank() || address == null || address.isBlank()) {
            return;
        }

        Session originalSession = mc.getSession();
        ServerInfo originalServerInfo = mc.getCurrentServerEntry();
        pruneDeadConnections();
        disconnectSessionsByName(name, Text.literal("Replaced"));
        BotConnection previous = freezeCurrentSession();
        ModuleClass.autoForest.resetToDefaults();
        ((IMinecraftClientAccessor) mc).setSession(createSessionWithName(mc.getSession(), name));
        bypassResourcePacksDuringBotConnect = true;
        mc.execute(() -> {
            try {
                ConnectScreen.connect(
                        new MultiplayerScreen(new TitleScreen()),
                        mc,
                        ServerAddress.parse(address),
                        new ServerInfo(address, address, ServerInfo.ServerType.OTHER),
                        false,
                        null
                );
            } catch (Exception ignored) {
                bypassResourcePacksDuringBotConnect = false;
                restoreAfterConnectFailure(mc, previous, originalSession, originalServerInfo);
            }
        });
    }

    public static void pulseBots(boolean rightClick) {
        for (BotConnection bot : connections) {
            if (!isConnectionUsable(bot)) continue;
            if (rightClick) {
                bot.handler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, bot.player().getYaw(), bot.player().getPitch()));
            } else {
                bot.handler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        }
    }

    public static void sayAll(String message) {
        for (BotConnection bot : connections) {
            if (!isConnectionUsable(bot)) continue;
            if (message.startsWith("/")) {
                bot.handler().sendChatCommand(message.substring(1));
            } else {
                bot.handler().sendChatMessage(message);
            }
        }
    }

    public static boolean control(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        pruneDeadConnections();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.world != null && name.equalsIgnoreCase(getCurrentSessionName())) {
            return true;
        }

        return connections.stream()
                .filter(bot -> matchesName(bot.name(), name))
                .findFirst()
                .map(bot -> {
                    if (!isConnectionUsable(bot)) {
                        connections.remove(bot);
                        return false;
                    }
                    BotConnection previous = freezeCurrentSession();
                    if (!activateSession(bot)) {
                        if (previous != null && activateSession(previous)) {
                            connections.remove(previous);
                        }
                        return false;
                    }
                    connections.remove(bot);
                    return true;
                })
                .orElse(false);
    }

    public static boolean say(String name, String message) {
        pruneDeadConnections();
        return connections.stream()
                .filter(bot -> matchesName(bot.name(), name))
                .findFirst()
                .map(bot -> {
                    if (!isConnectionUsable(bot)) {
                        connections.remove(bot);
                        return false;
                    }
                    if (message.startsWith("/")) {
                        bot.handler().sendChatCommand(message.substring(1));
                    } else {
                        bot.handler().sendChatMessage(message);
                    }
                    return true;
                })
                .orElse(false);
    }

    public static boolean remove(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        return disconnectSessionsByName(name, Text.literal("Removed")) > 0;
    }

    public static boolean restore() {
        return restore(null);
    }

    public static boolean restore(String name) {
        pruneDeadConnections();
        String targetName = name == null || name.isBlank()
                ? (connections.isEmpty() ? "" : connections.get(connections.size() - 1).name())
                : name;
        return !targetName.isBlank() && control(targetName);
    }

    private static BotConnection freezeCurrentSession() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null || mc.world == null || mc.player == null) {
            return null;
        }

        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        makeNettyBot(handler, mc.getSession().getUsername(), mc.player);
        BotConnection connection = new BotConnection(
                mc.getSession().getUsername(),
                mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "",
                handler.getConnection(),
                handler,
                mc.world,
                mc.player,
                mc.interactionManager,
                mc.getSession(),
                mc.getCurrentServerEntry(),
                ModuleClass.autoForest.captureState()
        );
        replaceConnection(connection);
        clearActiveSession(mc);
        return connection;
    }

    private static boolean activateSession(BotConnection bot) {
        if (!isConnectionUsable(bot)) {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        IMinecraftClientAccessor accessor = (IMinecraftClientAccessor) mc;

        Channel channel = getChannel(bot.connection());
        if (channel != null && channel.pipeline().get("bot_filter") != null) {
            channel.pipeline().remove("bot_filter");
        }

        try {
            setMinecraftClientField(mc, ClientPlayNetworkHandler.class, bot.handler());
            accessor.setSession(bot.session() != null ? bot.session() : createSessionWithName(mc.getSession(), bot.name()));
            setMinecraftClientField(mc, ServerInfo.class, bot.serverInfo() != null ? bot.serverInfo() : createServerInfo(bot.name(), bot.address()));
            accessor.setItemUseCooldown(0);

            mc.world = bot.world();
            mc.player = bot.player();
            mc.cameraEntity = bot.player();
            mc.interactionManager = bot.interactionManager();

            if (mc.worldRenderer != null) {
                mc.worldRenderer.setWorld(bot.world());
            }

            ModuleClass.autoForest.applyState(bot.autoForestState());
            bot.handler().sendPacket(new PlayerMoveC2SPacket.Full(
                    bot.player().getX(),
                    bot.player().getY(),
                    bot.player().getZ(),
                    bot.player().getYaw(),
                    bot.player().getPitch(),
                    bot.player().isOnGround(),
                    bot.player().horizontalCollision
            ));
            mc.setScreen(null);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void clearActiveSession(MinecraftClient mc) {
        IMinecraftClientAccessor accessor = (IMinecraftClientAccessor) mc;
        setMinecraftClientField(mc, ClientPlayNetworkHandler.class, null);
        accessor.setItemUseCooldown(0);
        mc.world = null;
        mc.player = null;
        mc.cameraEntity = null;
        mc.interactionManager = null;
        if (mc.worldRenderer != null) {
            mc.worldRenderer.setWorld(null);
        }
    }

    private static void replaceConnection(BotConnection connection) {
        disconnectSessionsByName(connection.name(), Text.literal("Replaced"));
        connections.add(connection);
    }

    private static void makeNettyBot(ClientPlayNetworkHandler handler, String name, ClientPlayerEntity botPlayer) {
        Channel channel = getChannel(handler.getConnection());
        if (channel == null) return;
        if (channel.pipeline().get("bot_filter") != null) {
            channel.pipeline().remove("bot_filter");
        }
        if (channel.pipeline().get("packet_handler") == null) {
            return;
        }

        channel.pipeline().addBefore("packet_handler", "bot_filter", new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof KeepAliveS2CPacket packet) {
                    handler.getConnection().send(new KeepAliveC2SPacket(packet.getId()));
                    if (botPlayer != null) {
                        handler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(botPlayer.isOnGround(), botPlayer.horizontalCollision));
                    }
                    ReferenceCountUtil.release(msg);
                    return;
                }

                if (msg instanceof CommonPingS2CPacket packet) {
                    handler.getConnection().send(new CommonPongC2SPacket(packet.getParameter()));
                    ReferenceCountUtil.release(msg);
                    return;
                }

                if (msg instanceof ResourcePackSendS2CPacket packet) {
                    handler.sendPacket(new ResourcePackStatusC2SPacket(packet.id(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
                    handler.sendPacket(new ResourcePackStatusC2SPacket(packet.id(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
                    ReferenceCountUtil.release(msg);
                    return;
                }

                if (msg instanceof PlayerPositionLookS2CPacket packet) {
                    applyFrozenPositionLook(botPlayer, packet);
                    handler.sendPacket(new TeleportConfirmC2SPacket(packet.teleportId()));
                    if (botPlayer != null) {
                        handler.sendPacket(new PlayerMoveC2SPacket.Full(
                                botPlayer.getX(),
                                botPlayer.getY(),
                                botPlayer.getZ(),
                                botPlayer.getYaw(),
                                botPlayer.getPitch(),
                                botPlayer.isOnGround(),
                                botPlayer.horizontalCollision
                        ));
                    }
                    ReferenceCountUtil.release(msg);
                    return;
                }

                if (msg instanceof EntityPositionSyncS2CPacket packet) {
                    applyFrozenEntityPositionSync(botPlayer, packet);
                    ReferenceCountUtil.release(msg);
                    return;
                }

                if (msg instanceof HealthUpdateS2CPacket packet) {
                    if (botPlayer != null) {
                        botPlayer.setHealth(packet.getHealth());
                    }
                    ReferenceCountUtil.release(msg);
                    return;
                }

                if (msg instanceof DisconnectS2CPacket) {
                    connections.removeIf(bot -> matchesName(bot.name(), name));
                    ctx.close();
                    ReferenceCountUtil.release(msg);
                    return;
                }

                String packetName = msg.getClass().getSimpleName();
                if (packetName.contains("Sound")
                        || packetName.contains("Particle")
                        || packetName.contains("Screen")
                        || ignoreBotMessages && isBotMessagePacket(packetName)
                        || packetName.contains("Explosion")
                        || packetName.contains("BossBar")
                        || packetName.contains("Scoreboard")
                        || packetName.contains("OverlayMessage")) {
                    ReferenceCountUtil.release(msg);
                    return;
                }
                super.channelRead(ctx, msg);
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                connections.removeIf(bot -> matchesName(bot.name(), name));
                super.channelInactive(ctx);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                connections.removeIf(bot -> matchesName(bot.name(), name));
                ctx.close();
            }
        });
    }

    private static boolean isBotMessagePacket(String packetName) {
        return packetName.contains("Chat")
                || packetName.contains("Message")
                || packetName.contains("Title")
                || packetName.contains("Overlay");
    }

    private static Channel getChannel(ClientConnection connection) {
        try {
            for (Field field : ClientConnection.class.getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (Channel) field.get(connection);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void applyFrozenPositionLook(ClientPlayerEntity botPlayer, PlayerPositionLookS2CPacket packet) {
        if (botPlayer == null || packet == null) {
            return;
        }

        double x = readPacketDouble(packet, "x", botPlayer.getX());
        double y = readPacketDouble(packet, "y", botPlayer.getY());
        double z = readPacketDouble(packet, "z", botPlayer.getZ());
        float yaw = (float) readPacketDouble(packet, "yaw", botPlayer.getYaw());
        float pitch = (float) readPacketDouble(packet, "pitch", botPlayer.getPitch());
        Object change = readPacketComponent(packet, "change");
        if (change == null) {
            change = readPacketComponent(packet, "flags");
        }

        if (hasRelativeFlag(change, "X")) {
            x += botPlayer.getX();
        }
        if (hasRelativeFlag(change, "Y")) {
            y += botPlayer.getY();
        }
        if (hasRelativeFlag(change, "Z")) {
            z += botPlayer.getZ();
        }
        if (hasRelativeFlag(change, "Y_ROT")) {
            yaw += botPlayer.getYaw();
        }
        if (hasRelativeFlag(change, "X_ROT")) {
            pitch += botPlayer.getPitch();
        }

        pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        botPlayer.refreshPositionAndAngles(x, y, z, yaw, pitch);
        botPlayer.setYaw(yaw);
        botPlayer.setPitch(pitch);
    }

    private static void applyFrozenEntityPositionSync(ClientPlayerEntity botPlayer, EntityPositionSyncS2CPacket packet) {
        if (botPlayer == null || packet == null || packet.id() != botPlayer.getId() || packet.values() == null) {
            return;
        }

        var position = packet.values().position();
        if (position == null) {
            return;
        }

        float yaw = packet.values().yaw();
        float pitch = MathHelper.clamp(packet.values().pitch(), -90.0F, 90.0F);
        botPlayer.refreshPositionAndAngles(position.x, position.y, position.z, yaw, pitch);
        botPlayer.setYaw(yaw);
        botPlayer.setPitch(pitch);
        if (packet.values().deltaMovement() != null) {
            botPlayer.setVelocity(packet.values().deltaMovement());
        }
        botPlayer.setOnGround(packet.onGround());
    }

    private static double readPacketDouble(Object packet, String name, double fallback) {
        Object value = readPacketComponent(packet, name);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static Object readPacketComponent(Object packet, String name) {
        if (packet == null || name == null || name.isBlank()) {
            return null;
        }

        try {
            Method method = packet.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(packet);
        } catch (Exception ignored) {
        }

        try {
            Method method = packet.getClass().getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
            method.setAccessible(true);
            return method.invoke(packet);
        } catch (Exception ignored) {
        }

        try {
            RecordComponent[] components = packet.getClass().getRecordComponents();
            if (components != null) {
                for (RecordComponent component : components) {
                    if (name.equals(component.getName())) {
                        return component.getAccessor().invoke(packet);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            for (Field field : packet.getClass().getDeclaredFields()) {
                if (name.equalsIgnoreCase(field.getName())) {
                    field.setAccessible(true);
                    return field.get(packet);
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static boolean hasRelativeFlag(Object flags, String flagName) {
        if (!(flags instanceof Iterable<?> iterable) || flagName == null) {
            return false;
        }

        for (Object flag : iterable) {
            if (flag instanceof Enum<?> enumFlag && flagName.equals(enumFlag.name())) {
                return true;
            }
        }
        return false;
    }

    private static Session createSessionWithName(Session current, String name) {
        try {
            Constructor<Session> constructor = Session.class.getDeclaredConstructor(
                    String.class,
                    UUID.class,
                    String.class,
                    Optional.class,
                    Optional.class,
                    Session.AccountType.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    name,
                    UUID.randomUUID(),
                    current == null ? "" : current.getAccessToken(),
                    Optional.empty(),
                    Optional.empty(),
                    Session.AccountType.MOJANG
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setMinecraftClientField(MinecraftClient mc, Class<?> fieldType, Object value) {
        try {
            for (Field field : MinecraftClient.class.getDeclaredFields()) {
                if (field.getType() == fieldType) {
                    field.setAccessible(true);
                    field.set(mc, value);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static ServerInfo createServerInfo(String name, String address) {
        String safeAddress = address == null ? "" : address;
        String safeName = name == null || name.isBlank() ? safeAddress : name;
        return new ServerInfo(safeName, safeAddress, ServerInfo.ServerType.OTHER);
    }

    private static void restoreAfterConnectFailure(MinecraftClient mc, BotConnection previous, Session originalSession, ServerInfo originalServerInfo) {
        try {
            bypassResourcePacksDuringBotConnect = false;
            if (previous != null && activateSession(previous)) {
                connections.remove(previous);
                return;
            }

            IMinecraftClientAccessor accessor = (IMinecraftClientAccessor) mc;
            accessor.setSession(originalSession);
            setMinecraftClientField(mc, ServerInfo.class, originalServerInfo);
        } catch (Exception ignored) {
        }
    }

    private static int disconnectSessionsByName(String name, Text reason) {
        if (name == null || name.isBlank()) {
            return 0;
        }

        int removed = 0;
        for (BotConnection bot : new ArrayList<>(connections)) {
            if (!matchesName(bot.name(), name)) {
                continue;
            }

            connections.remove(bot);
            removed++;
            try {
                if (bot.connection() != null) {
                    bot.connection().disconnect(reason);
                }
            } catch (Exception ignored) {
            }
        }
        return removed;
    }

    private static void pruneDeadConnections() {
        connections.removeIf(bot -> !isConnectionUsable(bot));
    }

    private static boolean isConnectionUsable(BotConnection bot) {
        if (bot == null || bot.name() == null || bot.name().isBlank()) {
            return false;
        }
        if (bot.connection() == null || bot.handler() == null || bot.world() == null || bot.player() == null) {
            return false;
        }
        if (bot.player().networkHandler != bot.handler()) {
            return false;
        }

        Channel channel = getChannel(bot.connection());
        return channel == null || channel.isOpen();
    }

    private static boolean matchesName(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    public static final class BotConnection {
        private final String name;
        private final String address;
        private final ClientConnection connection;
        private final ClientPlayNetworkHandler handler;
        private final ClientWorld world;
        private final ClientPlayerEntity player;
        private final ClientPlayerInteractionManager interactionManager;
        private final Session session;
        private final ServerInfo serverInfo;
        private final AutoForest.SessionState autoForestState;

        public BotConnection(String name, String address, ClientConnection connection, ClientPlayNetworkHandler handler, ClientWorld world, ClientPlayerEntity player, ClientPlayerInteractionManager interactionManager, Session session, ServerInfo serverInfo, AutoForest.SessionState autoForestState) {
            this.name = name;
            this.address = address;
            this.connection = connection;
            this.handler = handler;
            this.world = world;
            this.player = player;
            this.interactionManager = interactionManager;
            this.session = session;
            this.serverInfo = serverInfo;
            this.autoForestState = autoForestState;
        }

        public String name() { return name; }
        public String address() { return address; }
        public ClientConnection connection() { return connection; }
        public ClientPlayNetworkHandler handler() { return handler; }
        public ClientWorld world() { return world; }
        public ClientPlayerEntity player() { return player; }
        public ClientPlayerInteractionManager interactionManager() { return interactionManager; }
        public Session session() { return session; }
        public ServerInfo serverInfo() { return serverInfo; }
        public AutoForest.SessionState autoForestState() { return autoForestState; }
    }
}

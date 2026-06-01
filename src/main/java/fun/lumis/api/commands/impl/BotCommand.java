package fun.lumis.api.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import fun.lumis.api.commands.Command;
import fun.lumis.api.utils.bot.BotSessionManager;
import fun.lumis.api.utils.chat.ChatUtils;

import java.util.List;

public class BotCommand extends Command {

    public BotCommand() {
        super("bot");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("connect")
                        .then(arg("name", StringArgumentType.string())
                                .then(arg("ip", StringArgumentType.string())
                                        .executes(context -> {
                                            MinecraftClient mc = MinecraftClient.getInstance();
                                            if (mc.player == null) return 0;

                                            String name = StringArgumentType.getString(context, "name");
                                            String ip = StringArgumentType.getString(context, "ip");
                                            BotSessionManager.connect(name, ip);
                                            ChatUtils.sendMessage("§7[Bot] §fПодключение выполнено: " + name + " -> " + ip);
                                            return 1;
                                        }))))
                .then(literal("remove")
                        .then(arg("name", StringArgumentType.string())
                                .suggests((context, suggestions) -> {
                                    BotSessionManager.getSessionNames(false).forEach(suggestions::suggest);
                                    return suggestions.buildFuture();
                                })
                                .executes(context -> {
                                    MinecraftClient mc = MinecraftClient.getInstance();
                                    if (mc.player == null) return 0;

                                    String name = StringArgumentType.getString(context, "name");
                                    if (BotSessionManager.remove(name)) {
                                        ChatUtils.sendMessage("§7[Bot] §fСессия отключена и удалена: " + name);
                                    } else {
                                        ChatUtils.sendMessage("§7[Bot] §fСессия не найдена: " + name);
                                    }
                                    return 1;
                                })))
                .then(literal("control")
                        .then(arg("name", StringArgumentType.string())
                                .suggests((context, suggestions) -> {
                                    BotSessionManager.getSessionNames(false).forEach(suggestions::suggest);
                                    return suggestions.buildFuture();
                                })
                                .executes(context -> {
                                    MinecraftClient mc = MinecraftClient.getInstance();
                                    if (mc.player == null) return 0;

                                    String name = StringArgumentType.getString(context, "name");
                                    if (name.equalsIgnoreCase(BotSessionManager.getCurrentSessionName())) {
                                        ChatUtils.sendMessage("§7[Bot] §fТы уже управляешь этой сессией: " + name);
                                        return 1;
                                    }

                                    if (BotSessionManager.control(name)) {
                                        ChatUtils.sendMessage("§7[Bot] §fПереключаю на сессию: " + name);
                                    } else {
                                        ChatUtils.sendMessage("§7[Bot] §fСессия не найдена: " + name);
                                    }
                                    return 1;
                                })))
                .then(literal("say")
                        .then(arg("name", StringArgumentType.string())
                                .suggests((context, suggestions) -> {
                                    BotSessionManager.getSessionNames(false).forEach(suggestions::suggest);
                                    return suggestions.buildFuture();
                                })
                                .then(arg("message", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            MinecraftClient mc = MinecraftClient.getInstance();
                                            if (mc.player == null) return 0;

                                            String name = StringArgumentType.getString(context, "name");
                                            String message = StringArgumentType.getString(context, "message");
                                            if (BotSessionManager.say(name, message)) {
                                                ChatUtils.sendMessage("§7[Bot] §fСообщение отправлено от сессии " + name);
                                            } else {
                                                ChatUtils.sendMessage("§7[Bot] §fСессия не найдена: " + name);
                                            }
                                            return 1;
                                        }))))
                .then(literal("sayall")
                        .then(arg("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    MinecraftClient mc = MinecraftClient.getInstance();
                                    if (mc.player == null) return 0;

                                    String message = StringArgumentType.getString(context, "message");
                                    BotSessionManager.sayAll(message);
                                    ChatUtils.sendMessage("§7[Bot] §fСообщение отправлено от всех ботов.");
                                    return 1;
                                })))
                .then(literal("return")
                        .executes(context -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (mc.player == null) return 0;

                            if (BotSessionManager.restore()) {
                                ChatUtils.sendMessage("§7[Bot] §fВозвращаю предыдущую сессию");
                            } else {
                                ChatUtils.sendMessage("§7[Bot] §fНет сохранённой сессии для возврата");
                            }
                            return 1;
                        })
                        .then(arg("name", StringArgumentType.string())
                                .suggests((context, suggestions) -> {
                                    BotSessionManager.getSessionNames(true).forEach(suggestions::suggest);
                                    return suggestions.buildFuture();
                                })
                                .executes(context -> {
                                    MinecraftClient mc = MinecraftClient.getInstance();
                                    if (mc.player == null) return 0;

                                    String name = StringArgumentType.getString(context, "name");
                                    if (name.equalsIgnoreCase(BotSessionManager.getCurrentSessionName())) {
                                        ChatUtils.sendMessage("§7[Bot] §fТы уже управляешь этой сессией: " + name);
                                        return 1;
                                    }

                                    if (BotSessionManager.restore(name)) {
                                        ChatUtils.sendMessage("§7[Bot] §fПереключаю на сессию: " + name);
                                    } else {
                                        ChatUtils.sendMessage("§7[Bot] §fСессия не найдена: " + name);
                                    }
                                    return 1;
                                })))
                .then(literal("ignore")
                        .executes(context -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (mc.player == null) return 0;

                            boolean enabled = BotSessionManager.toggleIgnoreBotMessages();
                            ChatUtils.sendMessage("§7[Bot] §fИгнор сообщений ботов: " + (enabled ? "§aвключен" : "§cвыключен"));
                            return 1;
                        }))
                .then(literal("list")
                        .executes(context -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (mc.player == null) return 0;

                            List<BotSessionManager.BotConnection> connections = BotSessionManager.getConnections();
                            ChatUtils.sendMessage("§7[Bot] §fТекущая сессия: " + BotSessionManager.getCurrentSessionName());
                            if (connections.isEmpty()) {
                                ChatUtils.sendMessage("§7[Bot] §fСписок сохранённых сессий пуст");
                            } else {
                                ChatUtils.sendMessage("§7[Bot] §fСохранённые сессии:");
                                for (BotSessionManager.BotConnection bot : connections) {
                                    ChatUtils.sendMessage("§7- §f" + bot.name() + " (§7" + bot.address() + "§f)");
                                }
                            }
                            return 1;
                        }));
    }
}

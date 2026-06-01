package fun.lumis.api.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import org.lwjgl.glfw.GLFW;
import fun.lumis.Lumis;
import fun.lumis.api.commands.Command;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.api.utils.cmd.macro.Macro;
import fun.lumis.client.modules.settings.implement.BindSetting;

import java.lang.reflect.Field;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class MacroCommand extends Command {

    public MacroCommand() {
        super("macro");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .then(literal("add")
                        .then(arg("name", word())
                                .then(arg("bind", word())
                                        .suggests((context, builder1) -> {
                                            for (Field field : GLFW.class.getDeclaredFields()) {
                                                String name = field.getName();
                                                if (!name.startsWith("GLFW_KEY_")) {
                                                    continue;
                                                }

                                                String bind = name.replace("GLFW_KEY_", "");
                                                if (bind.startsWith(builder1.getRemaining())) {
                                                    builder1.suggest(bind);
                                                }
                                            }

                                            if ("NONE".startsWith(builder1.getRemaining().toUpperCase())) {
                                                builder1.suggest("NONE");
                                            }
                                            return builder1.buildFuture();
                                        })
                                        .then(arg("command", greedyString())
                                                .executes(context -> {
                                                    String name = context.getArgument("name", String.class);
                                                    String bind = context.getArgument("bind", String.class).toUpperCase();
                                                    String command = context.getArgument("command", String.class);

                                                    if (Lumis.INSTANCE.macroStorage.getMacro(name) != null) {
                                                        ChatUtils.sendMessage("Макрос " + name + " уже существует!");
                                                        return SINGLE_SUCCESS;
                                                    }

                                                    try {
                                                        int key = "NONE".equals(bind)
                                                                ? -1
                                                                : GLFW.class.getField("GLFW_KEY_" + bind).getInt(null);

                                                        Lumis.INSTANCE.macroStorage.add(new Macro(
                                                                name,
                                                                command,
                                                                new BindSetting("bind", key)
                                                        ));
                                                        ChatUtils.sendMessage("Макрос " + name + " был добавлен!");
                                                    } catch (Exception ignored) {
                                                        ChatUtils.sendMessage("Неверный бинд: " + bind);
                                                    }
                                                    return SINGLE_SUCCESS;
                                                })))))
                .then(literal("remove")
                        .then(arg("name", word())
                                .suggests((context, builder1) -> {
                                    Lumis.INSTANCE.macroStorage.getNames().stream()
                                            .filter(name -> name.startsWith(builder1.getRemaining()))
                                            .forEach(builder1::suggest);
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String name = context.getArgument("name", String.class);
                                    if (Lumis.INSTANCE.macroStorage.isEmpty()) {
                                        ChatUtils.sendMessage("Список макросов пуст!");
                                        return SINGLE_SUCCESS;
                                    }

                                    Macro macro = Lumis.INSTANCE.macroStorage.getMacro(name);
                                    if (macro == null) {
                                        ChatUtils.sendMessage("Макрос " + name + " не найден!");
                                        return SINGLE_SUCCESS;
                                    }

                                    Lumis.INSTANCE.macroStorage.remove(macro);
                                    ChatUtils.sendMessage("Макрос " + name + " был удалён!");
                                    return SINGLE_SUCCESS;
                                })))
                .then(literal("list")
                        .executes(context -> {
                            StringBuilder builder1 = new StringBuilder();

                            if (Lumis.INSTANCE.macroStorage.getNames().isEmpty()) {
                                ChatUtils.sendMessage("Список макросов пуст!");
                            } else {
                                for (int i = 0; i < Lumis.INSTANCE.macroStorage.getNames().size(); i++) {
                                    builder1.append(Lumis.INSTANCE.macroStorage.getNames().get(i));
                                    if (i < Lumis.INSTANCE.macroStorage.getNames().size() - 1) {
                                        builder1.append(", ");
                                    }
                                }
                                builder1.append(".");
                                ChatUtils.sendMessage("Макросы: " + builder1);
                            }

                            return SINGLE_SUCCESS;
                        }))
                .then(literal("clear")
                        .executes(context -> {
                            if (!Lumis.INSTANCE.macroStorage.isEmpty()) {
                                Lumis.INSTANCE.macroStorage.clear();
                                ChatUtils.sendMessage("Все макросы были удалены!");
                            } else {
                                ChatUtils.sendMessage("Список макросов пуст!");
                            }
                            return SINGLE_SUCCESS;
                        }));
    }
}

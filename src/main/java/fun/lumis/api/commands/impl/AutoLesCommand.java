package fun.lumis.api.commands.impl;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import fun.lumis.api.commands.Command;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.client.modules.impl.player.AutoForest;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class AutoLesCommand extends Command {
    public AutoLesCommand() {
        super("autoles");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            sendStatus();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("enable").executes(ctx -> {
            if (!module().isCurrentSessionEnabled()) {
                module().enableForCurrentSession();
            }
            ChatUtils.sendMessage("АвтоЛес включён");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("disable").executes(ctx -> {
            if (module().isCurrentSessionEnabled()) {
                module().disableForCurrentSession();
            }
            ChatUtils.sendMessage("АвтоЛес выключен");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("mode")
                .then(arg("value", word())
                        .suggests((ctx, suggestions) -> {
                            module().getModeSuggestions().forEach(suggestions::suggest);
                            return suggestions.buildFuture();
                        })
                        .executes(ctx -> {
                            String value = ctx.getArgument("value", String.class);
                            if (!module().setModeAlias(value)) {
                                ChatUtils.sendMessage("Неизвестный режим. Доступно: normal, fast");
                                return SINGLE_SUCCESS;
                            }
                            ChatUtils.sendMessage("Режим: " + module().getModeAlias());
                            return SINGLE_SUCCESS;
                        })));

        builder.then(booleanSetting("swing", value -> module().setSwingEnabled(value), () -> module().isSwingEnabled()));
        builder.then(booleanSetting("autosell", value -> module().setAutoSellEnabled(value), () -> module().isAutoSellEnabled()));
        builder.then(booleanSetting("autopay", value -> module().setAutoPayEnabled(value), () -> module().isAutoPayEnabled()));
        builder.then(booleanSetting("visuals", value -> module().setPreserveVisualsEnabled(value), () -> module().isPreserveVisualsEnabled()));

        builder.then(floatSetting("pps", value -> module().setPacketsPerSecond(value), () -> module().getPacketsPerSecond()));
        builder.then(floatSetting("radius", value -> module().setBreakRadius(value), () -> module().getBreakRadius()));
        builder.then(floatSetting("payamount", value -> module().setPayAmount(value), () -> module().getPayAmount()));
        builder.then(floatSetting("interval", value -> module().setIntervalSeconds(value), () -> module().getIntervalSeconds()));

        builder.then(literal("pay")
                .then(literal("clear").executes(ctx -> {
                    module().clearPayTarget();
                    ChatUtils.sendMessage("Ник для перевода очищен");
                    return SINGLE_SUCCESS;
                }))
                .then(arg("nick", word()).executes(ctx -> {
                    String nick = ctx.getArgument("nick", String.class);
                    if (!module().setPayTarget(nick)) {
                        ChatUtils.sendMessage("Ник не может быть пустым");
                        return SINGLE_SUCCESS;
                    }
                    ChatUtils.sendMessage("Ник для перевода: " + module().getPayTarget());
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("status").executes(ctx -> {
            sendStatus();
            return SINGLE_SUCCESS;
        }));
    }

    private LiteralArgumentBuilder<CommandSource> booleanSetting(String name, java.util.function.Consumer<Boolean> setter, java.util.function.Supplier<Boolean> getter) {
        return literal(name).then(arg("value", BoolArgumentType.bool())
                .suggests((ctx, suggestions) -> {
                    suggestions.suggest("true");
                    suggestions.suggest("false");
                    return suggestions.buildFuture();
                })
                .executes(ctx -> {
                    boolean value = BoolArgumentType.getBool(ctx, "value");
                    setter.accept(value);
                    ChatUtils.sendMessage(settingLabel(name) + ": " + (getter.get() ? "включено" : "выключено"));
                    return SINGLE_SUCCESS;
                }));
    }

    private LiteralArgumentBuilder<CommandSource> floatSetting(String name, java.util.function.Consumer<Float> setter, java.util.function.Supplier<Float> getter) {
        return literal(name).then(arg("value", FloatArgumentType.floatArg())
                .executes(ctx -> {
                    float value = FloatArgumentType.getFloat(ctx, "value");
                    setter.accept(value);
                    ChatUtils.sendMessage(settingLabel(name) + ": " + getter.get());
                    return SINGLE_SUCCESS;
                }));
    }

    private void sendStatus() {
        AutoForest module = module();
        ChatUtils.sendMessage("АвтоЛес: " + (module.isCurrentSessionEnabled() ? "включён" : "выключен"));
        ChatUtils.sendMessage("Режим=" + module.getModeAlias()
                + ", Мах рукой=" + booleanText(module.isSwingEnabled())
                + ", Автопродажа=" + booleanText(module.isAutoSellEnabled())
                + ", AutoPay=" + booleanText(module.isAutoPayEnabled())
                + ", Визуализация=" + booleanText(module.isPreserveVisualsEnabled()));
        ChatUtils.sendMessage("Пакетов в секунду=" + module.getPacketsPerSecond()
                + ", Радиус=" + module.getBreakRadius()
                + ", Сумма перевода=" + module.getPayAmount()
                + ", Задержка=" + module.getIntervalSeconds()
                + ", Ник перевода=" + (module.getPayTarget().isBlank() ? "<пусто>" : module.getPayTarget()));
    }

    private AutoForest module() {
        return ModuleClass.autoForest;
    }

    private String booleanText(boolean value) {
        return value ? "включено" : "выключено";
    }

    private String settingLabel(String name) {
        return switch (name) {
            case "swing" -> "Мах рукой";
            case "autosell" -> "Автопродажа";
            case "autopay" -> "AutoPay";
            case "visuals" -> "Визуализация";
            case "pps" -> "Пакетов в секунду";
            case "radius" -> "Радиус";
            case "payamount" -> "Сумма перевода";
            case "interval" -> "Задержка";
            default -> name;
        };
    }
}

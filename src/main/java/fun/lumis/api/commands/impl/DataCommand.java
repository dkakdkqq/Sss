package fun.lumis.api.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;

import fun.lumis.Lumis;
import fun.lumis.api.commands.Command;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.client.modules.impl.combat.Aura;

import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class DataCommand extends Command {

    public DataCommand() {
        super("data");
    }

    
    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .executes(context -> {
                    sendStatus();
                    return SINGLE_SUCCESS;
                })
                .then(literal("record")
                        .executes(context -> {
                            Aura aura = getAura();
                            if (aura == null) {
                                return SINGLE_SUCCESS;
                            }

                            aura.getDataSystem().startRecording();
                            ChatUtils.sendMessage("Data: запись начата, старые паттерны в памяти очищены");
                            return SINGLE_SUCCESS;
                        }))
                .then(literal("stop")
                        .executes(context -> {
                            stopRecording("data_" + System.currentTimeMillis());
                            return SINGLE_SUCCESS;
                        })
                        .then(arg("name", greedyString())
                                .executes(context -> {
                                    stopRecording(context.getArgument("name", String.class));
                                    return SINGLE_SUCCESS;
                                })))
                .then(literal("play")
                        .then(arg("name", word())
                                .suggests((context, suggestions) -> {
                                    getAuraPatterns().stream()
                                            .filter(name -> name.toLowerCase().startsWith(suggestions.getRemaining().toLowerCase()))
                                            .forEach(suggestions::suggest);
                                    return suggestions.buildFuture();
                                })
                                .executes(context -> {
                                    playProfile(context.getArgument("name", String.class));
                                    return SINGLE_SUCCESS;
                                })))
                .then(literal("delete")
                        .then(arg("name", word())
                                .suggests((context, suggestions) -> {
                                    getAuraPatterns().stream()
                                            .filter(name -> name.toLowerCase().startsWith(suggestions.getRemaining().toLowerCase()))
                                            .forEach(suggestions::suggest);
                                    return suggestions.buildFuture();
                                })
                                .executes(context -> {
                                    deleteProfile(context.getArgument("name", String.class));
                                    return SINGLE_SUCCESS;
                                })))
                .then(literal("list")
                        .executes(context -> {
                            listProfiles();
                            return SINGLE_SUCCESS;
                        }))
                .then(literal("clear")
                        .executes(context -> {
                            Aura aura = getAura();
                            if (aura == null) {
                                return SINGLE_SUCCESS;
                            }

                            aura.getDataSystem().clearPatterns();
                            ChatUtils.sendMessage("Data: паттерны очищены");
                            return SINGLE_SUCCESS;
                        }))
                .then(literal("status")
                        .executes(context -> {
                            sendStatus();
                            return SINGLE_SUCCESS;
                        }));
    }

    private void stopRecording(String name) {
        Aura aura = getAura();
        if (aura == null) {
            return;
        }

        if (!aura.getDataSystem().isRecording()) {
            ChatUtils.sendMessage("Data: запись не запущена");
            return;
        }

        if (!aura.getDataSystem().savePatterns(name)) {
            ChatUtils.sendMessage("Data: нечего сохранять");
            return;
        }

        aura.getDataSystem().stopRecording();
        ChatUtils.sendMessage("Data: запись остановлена и сохранена как " + name);
    }

    private void playProfile(String name) {
        Aura aura = getAura();
        if (aura == null) {
            return;
        }

        if (!aura.getDataSystem().loadPatterns(name)) {
            ChatUtils.sendMessage("Data: профиль " + name + " не найден или поврежден");
            return;
        }

        aura.getDataSystem().setRecording(false);
        aura.getDataSystem().setUsingNeuro(true);
        aura.getDataSystem().resetState();
        ChatUtils.sendMessage("Data: загружен профиль " + name + " (" + aura.getDataSystem().getPatternCount() + " паттернов)");
        ChatUtils.sendMessage("Data: выбери режим ротации Data в Aura");
    }

    private void deleteProfile(String name) {
        Aura aura = getAura();
        if (aura == null) {
            return;
        }

        if (aura.getDataSystem().deletePatterns(name)) {
            ChatUtils.sendMessage("Data: профиль " + name + " удален");
        } else {
            ChatUtils.sendMessage("Data: профиль " + name + " не найден");
        }
    }

    private void listProfiles() {
        List<String> patterns = getAuraPatterns();
        if (patterns.isEmpty()) {
            ChatUtils.sendMessage("Data: нет сохраненных профилей");
            return;
        }
        ChatUtils.sendMessage("Data: сохраненные профили (" + patterns.size() + "):");
        for (String name : patterns) {
            ChatUtils.sendMessage("  - " + name);
        }
    }

    private void sendStatus() {
        Aura aura = getAura();
        if (aura == null) {
            return;
        }

        ChatUtils.sendMessage(aura.getDataSystem().getStatusString());
        if (aura.getDataSystem().getPatternCount() > 0) {
            ChatUtils.sendMessage("Новых в сессии: " + aura.getDataSystem().getRecordedThisSession());
        }
    }

    private Aura getAura() {
        Aura aura = ModuleClass.INSTANCE == null ? null : ModuleClass.INSTANCE.aura;
        if (aura == null) {
            ChatUtils.sendMessage("Data: модуль Aura не найден");
        }
        return aura;
    }

    private List<String> getAuraPatterns() {
        Aura aura = ModuleClass.INSTANCE == null ? null : ModuleClass.INSTANCE.aura;
        return aura == null ? List.of() : aura.getDataSystem().getPatternNames();
    }
}

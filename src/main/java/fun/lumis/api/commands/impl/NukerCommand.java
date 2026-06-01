package fun.lumis.api.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import fun.lumis.api.commands.Command;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.client.modules.impl.player.Nuker;

import java.util.Set;
import java.util.stream.Collectors;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class NukerCommand extends Command {

    public NukerCommand() {
        super("nuker");
    }

    public NukerCommand(String command) {
        super(command);
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .then(literal("add")
                        .then(arg("block", word())
                                .suggests((context, builder1) -> {
                                    String input = Nuker.normalizeBlockName(builder1.getRemaining());
                                    Registries.BLOCK.stream()
                                            .map(Registries.BLOCK::getId)
                                            .map(Identifier::getPath)
                                            .filter(name -> name.startsWith(input))
                                            .limit(20)
                                            .forEach(builder1::suggest);
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String blockName = Nuker.normalizeBlockName(context.getArgument("block", String.class));

                                    if (Nuker.INSTANCE.isTargetBlock(blockName)) {
                                        ChatUtils.sendMessage("§cБлок §e" + blockName + "§c уже в списке Nuker!");
                                        return SINGLE_SUCCESS;
                                    }

                                    if (!blockExists(blockName)) {
                                        ChatUtils.sendMessage("§cБлок §e" + blockName + "§c не найден!");
                                        return SINGLE_SUCCESS;
                                    }

                                    Nuker.INSTANCE.addBlock(blockName);
                                    ChatUtils.sendMessage("§aБлок §e" + blockName + "§a добавлен в Nuker!");
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("remove")
                        .then(arg("block", word())
                                .suggests((context, builder1) -> {
                                    String input = Nuker.normalizeBlockName(builder1.getRemaining());
                                    Nuker.INSTANCE.getTargetBlocks().stream()
                                            .sorted(String::compareTo)
                                            .filter(name -> name.startsWith(input))
                                            .forEach(builder1::suggest);
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String blockName = Nuker.normalizeBlockName(context.getArgument("block", String.class));

                                    if (!Nuker.INSTANCE.isTargetBlock(blockName)) {
                                        ChatUtils.sendMessage("§cБлока §e" + blockName + "§c нет в списке Nuker!");
                                        return SINGLE_SUCCESS;
                                    }

                                    Nuker.INSTANCE.removeBlock(blockName);
                                    ChatUtils.sendMessage("§aБлок §e" + blockName + "§a удален из Nuker!");
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("list")
                        .executes(context -> {
                            Set<String> blocks = Nuker.INSTANCE.getTargetBlocks();

                            if (blocks.isEmpty()) {
                                ChatUtils.sendMessage("§cСписок Nuker пуст!");
                                return SINGLE_SUCCESS;
                            }

                            String blockList = blocks.stream()
                                    .sorted()
                                    .collect(Collectors.joining("§7, §e"));
                            ChatUtils.sendMessage("§aБлоки Nuker §7(§e" + blocks.size() + "§7)§a: §e" + blockList);
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("clear")
                        .executes(context -> {
                            if (Nuker.INSTANCE.getTargetBlocks().isEmpty()) {
                                ChatUtils.sendMessage("§cСписок Nuker уже пуст!");
                                return SINGLE_SUCCESS;
                            }

                            Nuker.INSTANCE.clearBlocks();
                            ChatUtils.sendMessage("§aСписок Nuker очищен!");
                            return SINGLE_SUCCESS;
                        })
                );
    }

    private boolean blockExists(String blockName) {
        return Registries.BLOCK.stream()
                .anyMatch(block -> Registries.BLOCK.getId(block).getPath().equalsIgnoreCase(blockName));
    }
}

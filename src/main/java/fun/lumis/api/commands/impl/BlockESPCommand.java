package fun.lumis.api.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import fun.lumis.api.commands.Command;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.client.modules.impl.render.BlockESP;

import java.util.Set;
import java.util.stream.Collectors;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class BlockESPCommand extends Command {

    public BlockESPCommand() {
        super("blockesp");
    }

    
    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .then(literal("add")
                        .then(arg("block", word())
                                .suggests((context, builder1) -> {
                                    String input = builder1.getRemaining().toLowerCase();
                                    Registries.BLOCK.stream()
                                            .map(Registries.BLOCK::getId)
                                            .map(Identifier::getPath)
                                            .filter(name -> name.startsWith(input))
                                            .limit(20)
                                            .forEach(builder1::suggest);
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String blockName = context.getArgument("block", String.class);
                                    
                                    if (BlockESP.INSTANCE.isTracking(blockName)) {
                                        ChatUtils.sendMessage("§cБлок §e" + blockName + "§c уже отслеживается!");
                                        return SINGLE_SUCCESS;
                                    }

                                    boolean exists = Registries.BLOCK.stream()
                                            .anyMatch(block -> {
                                                String name = Registries.BLOCK.getId(block).getPath();
                                                return name.equalsIgnoreCase(blockName);
                                            });

                                    if (!exists) {
                                        ChatUtils.sendMessage("§cБлок §e" + blockName + "§c не найден!");
                                        return SINGLE_SUCCESS;
                                    }

                                    BlockESP.INSTANCE.addBlock(blockName);
                                    ChatUtils.sendMessage("§aБлок §e" + blockName + "§a добавлен в отслеживание!");
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("remove")
                        .then(arg("block", word())
                                .suggests((context, builder1) -> {
                                    BlockESP.INSTANCE.getTrackedBlocks().stream()
                                            .sorted(String::compareTo)
                                            .filter(name -> name.startsWith(builder1.getRemaining().toLowerCase()))
                                            .forEach(builder1::suggest);
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String blockName = context.getArgument("block", String.class);
                                    
                                    if (!BlockESP.INSTANCE.isTracking(blockName)) {
                                        ChatUtils.sendMessage("§cБлок §e" + blockName + "§c не отслеживается!");
                                        return SINGLE_SUCCESS;
                                    }

                                    BlockESP.INSTANCE.removeBlock(blockName);
                                    ChatUtils.sendMessage("§aБлок §e" + blockName + "§a удалён из отслеживания!");
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("list")
                        .executes(context -> {
                            Set<String> blocks = BlockESP.INSTANCE.getTrackedBlocks();
                            
                            if (blocks.isEmpty()) {
                                ChatUtils.sendMessage("§cСписок отслеживаемых блоков пуст!");
                                return SINGLE_SUCCESS;
                            }

                            String blockList = blocks.stream()
                                    .sorted()
                                    .collect(Collectors.joining("§7, §e"));
                            
                            ChatUtils.sendMessage("§aОтслеживаемые блоки §7(§e" + blocks.size() + "§7)§a: §e" + blockList);
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("clear")
                        .executes(context -> {
                            if (BlockESP.INSTANCE.getTrackedBlocks().isEmpty()) {
                                ChatUtils.sendMessage("§cСписок отслеживаемых блоков уже пуст!");
                                return SINGLE_SUCCESS;
                            }

                            BlockESP.INSTANCE.clearBlocks();
                            ChatUtils.sendMessage("§aСписок отслеживаемых блоков очищен!");
                            return SINGLE_SUCCESS;
                        })
                );
    }
}
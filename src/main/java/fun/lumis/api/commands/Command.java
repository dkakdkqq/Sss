package fun.lumis.api.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import lombok.Getter;
import net.minecraft.command.CommandSource;
import fun.lumis.api.QClient;

@Getter
public abstract class Command implements QClient {
    private final String command;

    public Command(String command) {
        this.command = command;
    }

    public abstract void execute(LiteralArgumentBuilder<CommandSource> builder);

    public void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> builder = LiteralArgumentBuilder.literal(command);
        execute(builder);
        dispatcher.register(builder);
    }

    protected <T> RequiredArgumentBuilder<CommandSource, T> arg(String name, final ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    protected LiteralArgumentBuilder<CommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }
}
package fun.lumis.api.storages.implement;

import com.mojang.brigadier.CommandDispatcher;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.command.CommandSource;

import fun.lumis.api.commands.Command;
import fun.lumis.api.commands.impl.*;

import java.util.ArrayList;
import java.util.List;

@Getter
public class CommandStorage {

    private final CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
    private final List<Command> commands = new ArrayList<>();
    @Setter private String prefix = ".";

    public CommandStorage() {
        registry();
    }
    
    private void registry() {
        addCommands(
                new AutoLesCommand(),
                new FriendCommand(),
                new ConfigCommand(),
                new MacroCommand(),
                new BotCommand(),
                new BlockESPCommand(),
                new NukerCommand(),
                new NukerCommand("nuk"),
                new GPSCommand(),
                new BindCommand(),
                new StaffCommand(),
                new VClipCommand(),
                new DataCommand()
        );
    }

    public CommandSource getSource() {
        return new ClientCommandSource(null, MinecraftClient.getInstance());
    }

    private void addCommands(Command... command) {
        for (Command cmd : command) {
            cmd.register(dispatcher);
            commands.add(cmd);
        }
    }
}

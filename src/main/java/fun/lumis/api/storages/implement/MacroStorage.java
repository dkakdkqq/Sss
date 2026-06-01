package fun.lumis.api.storages.implement;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import fun.lumis.Lumis;
import fun.lumis.api.QClient;
import fun.lumis.api.events.EventInvoker;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventBinding;
import fun.lumis.api.utils.chat.ChatUtils;
import fun.lumis.api.utils.cmd.macro.Macro;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class MacroStorage implements QClient {

    public MacroStorage() {
        EventInvoker.register(this);
    }

    @Getter private final List<Macro> macros = new ArrayList<>();
    @Getter private final List<String> names = new ArrayList<>();

    public void add(Macro macro) {
        if (macro == null || macro.getName() == null || macro.getName().isBlank() || getMacro(macro.getName()) != null) {
            return;
        }
        macros.add(macro);
        names.add(macro.getName());
    }

    public void remove(Macro macro) {
        if (macro == null) {
            return;
        }
        macros.remove(macro);
        names.remove(macro.getName());
    }

    public void clear() {
        if (!macros.isEmpty()) macros.clear();
        if (!names.isEmpty()) names.clear();
    }

    public boolean isEmpty() {
        return macros.isEmpty();
    }

    public Macro getMacro(String name) {
        for (Macro macro : macros) {
            if (!macro.getName().equalsIgnoreCase(name)) continue;
            return macro;
        }
        
        return null;
    }

    @EventLink
    public void onKey(EventBinding e) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null || mc.player.networkHandler == null || macros.isEmpty()) return;

        for (Macro macro : macros) {
            if (macro == null || macro.getBind() == null || macro.getBind().getKey() != e.getKey()) {
                continue;
            }
            executeMacro(macro);
        }
    }

    private void executeMacro(Macro macro) {
        String command = macro.getCommand();
        if (command == null || command.isBlank()) {
            return;
        }

        if (command.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(command.substring(1));
            return;
        }

        String prefix = Lumis.INSTANCE.commandStorage.getPrefix();
        if (prefix != null && !prefix.isEmpty() && command.startsWith(prefix)) {
            try {
                Lumis.INSTANCE.commandStorage.getDispatcher().execute(
                        command.substring(prefix.length()),
                        Lumis.INSTANCE.commandStorage.getSource()
                );
            } catch (CommandSyntaxException ignored) {
                ChatUtils.sendMessage(Formatting.RED + "Ошибка в использовании макроса " + macro.getName() + "!");
            }
            return;
        }

        mc.player.networkHandler.sendChatMessage(command);
    }
}

package fun.lumis.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;


public interface QClient {
    MinecraftClient mc = MinecraftClient.getInstance();
    Window mw = mc.getWindow();
}

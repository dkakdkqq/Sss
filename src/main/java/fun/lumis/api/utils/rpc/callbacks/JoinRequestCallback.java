package fun.lumis.api.utils.rpc.callbacks;

import com.sun.jna.Callback;
import fun.lumis.api.utils.rpc.utils.DiscordUser;

public interface JoinRequestCallback extends Callback {
    void apply(DiscordUser var1);
}

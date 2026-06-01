package fun.lumis.mixin;

import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerInputC2SPacket.class)
public interface IPlayerInputC2SPacketAccessor {
    @Mutable
    @Accessor("input")
    void setInput(PlayerInput input);
}

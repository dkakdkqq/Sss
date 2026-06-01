package fun.lumis.mixin;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import fun.lumis.client.modules.impl.render.SeeInvisiblesRenderState;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements SeeInvisiblesRenderState {

    @Unique
    private boolean lumis$seeInvisiblesTarget;

    @Override
    public boolean lumis$isSeeInvisiblesTarget() {
        return lumis$seeInvisiblesTarget;
    }

    @Override
    public void lumis$setSeeInvisiblesTarget(boolean value) {
        lumis$seeInvisiblesTarget = value;
    }
}

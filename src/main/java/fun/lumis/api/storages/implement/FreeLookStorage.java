package fun.lumis.api.storages.implement;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.MathHelper;
import fun.lumis.api.QClient;
import fun.lumis.api.events.EventInvoker;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventLook;
import fun.lumis.api.events.implement.EventRotation;

public class FreeLookStorage implements QClient {

    public FreeLookStorage() {
        EventInvoker.register(this);
    }

    @Setter private static boolean active;
    @Getter @Setter private static float freeYaw, freePitch;
    public static boolean isActive() {
        return active;
    }

    @EventLink
    public void onLook(EventLook event) {
        if (active) {
            rotateTowards(event.getYaw(), event.getPitch());
            event.cancel();
        }
    }

    @EventLink
    public void onRotation(EventRotation event) {
        if (active) {
            event.setYaw(freeYaw);
            event.setPitch(freePitch);
        } else {
            freeYaw = event.getYaw();
            freePitch = event.getPitch();
        }
    }

    private void rotateTowards(double targetYaw, double targetPitch) {
        freePitch = MathHelper.clamp((float) (freePitch + targetPitch * 0.15D), -90.0F, 90.0F);
        freeYaw = (float) (freeYaw + targetYaw * 0.15D);
    }
}

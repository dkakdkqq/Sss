package fun.lumis.api.events.implement;

import fun.lumis.api.events.Event;

public class EventSlowWalking extends Event {

    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
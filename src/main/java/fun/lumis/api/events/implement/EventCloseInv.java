package fun.lumis.api.events.implement;

import lombok.AllArgsConstructor;
import fun.lumis.api.events.Event;

@AllArgsConstructor
public class EventCloseInv extends Event {
    public int windowId;
}


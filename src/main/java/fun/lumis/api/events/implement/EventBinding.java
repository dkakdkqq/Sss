package fun.lumis.api.events.implement;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import fun.lumis.api.events.Event;

@RequiredArgsConstructor
@Getter
public class EventBinding extends Event {

    private final int key;
    private final BindType bindType;

    public boolean isKeyDown(int button) {
        return this.key == button;
    }
    public enum BindType {
        KEYBOARD,
        MOUSE
    }

}

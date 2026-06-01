package fun.lumis.client.modules.impl.render.base;

import lombok.RequiredArgsConstructor;
import fun.lumis.api.QClient;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.utils.draggable.Draggable;

@RequiredArgsConstructor
public class InterfaceProcessing implements QClient {

    public final Draggable draggable;
    private boolean unusualRectType = true;

    public boolean isUnusualRectType() {
        return unusualRectType;
    }

    public void setUnusualRectType(boolean unusualRectType) {
        this.unusualRectType = unusualRectType;
    }

    public void onUpdate(EventUpdate eventUpdate) {
    }

    public void onRender(EventRender.Default eventRender) {

    }
}

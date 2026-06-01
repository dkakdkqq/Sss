package fun.lumis.api.events;

import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.InvocationTargetException;

@Setter
@Getter
public class Event
{
    private boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public void call()
    {
        try
        {
            EventInvoker.invoke(this);
        }
        catch (IllegalAccessException | InvocationTargetException | InstantiationException e)
        {
            throw new RuntimeException("Failed to Invoke Method", e);
        }
    }
}
package fun.lumis.api.utils.math;

import lombok.Generated;

public class TimerUtils {
    private long millis;

    public TimerUtils() {
        this.reset();
    }

    public boolean finished(float delay) {
        return (float)System.currentTimeMillis() - delay >= (float)this.millis;
    }

    public boolean finished(long delay) {
        return System.currentTimeMillis() - this.millis >= delay;
    }

    public void reset() {
        this.millis = System.currentTimeMillis();
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - this.millis;
    }

    @Generated
    public long getMillis() {
        return this.millis;
    }

    @Generated
    public void setMillis(long millis) {
        this.millis = millis;
    }
}

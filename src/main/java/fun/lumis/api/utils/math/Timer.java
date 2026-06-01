package fun.lumis.api.utils.math;

import lombok.Getter;
import lombok.Setter;
import fun.lumis.api.QClient;

@Getter
@Setter
public class Timer implements QClient {
    private long startTime = System.currentTimeMillis();

    private long millis;

    public Timer() {
        reset();
    }

    public static Timer create() {
        return new Timer();
    }

    public boolean finished(long delay) {
        return System.currentTimeMillis() - delay >= millis;
    }

    public void reset() {
        this.millis = System.currentTimeMillis();
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - this.millis;
    }

    public double deltaTime() {
        return mc.getCurrentFps() > 0 ? (1.0000 / mc.getCurrentFps()) : 1;
    }
    public boolean every(long ms) {
        boolean passed = getMillis(System.nanoTime() - millis) >= ms;
        if (passed)
            reset();
        return passed;
    }
    public boolean passed(long time) {
        return System.currentTimeMillis() - startTime > time;
    }
    public long getMillis(long time) {
        return time / 1000000L;
    }

    public long getTime() {
        return System.currentTimeMillis() - startTime;
    }
    public void setTime(long time) {
        startTime = time;
    }
}
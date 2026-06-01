package fun.lumis.api.utils.tps;

import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.math.MathHelper;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventPacket;

@Getter
public class TPSCalc {

    private float TPS = 20;
    private float adjustTicks = 0;

    private long timestamp;
    private long lastPacketTime;

    public TPSCalc() {
    }

    @EventLink
    public void onPacket(EventPacket e) {
        if (e.getType() == EventPacket.Type.RECEIVE && e.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            updateTPS();
        }
    }

    public float getTPS() {
        if (lastPacketTime == 0L) {
            return TPS;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getNetworkHandler() == null || System.currentTimeMillis() - lastPacketTime > 3500L) {
            return 20.0f;
        }

        return TPS;
    }

    private static final int SAMPLE_SIZE = 20;
    private final float[] tpsSamples = new float[SAMPLE_SIZE];
    private int sampleIndex = 0;

    private void updateTPS() {
        long now = System.nanoTime();
        lastPacketTime = System.currentTimeMillis();
        if (timestamp == 0L) {
            timestamp = now;
            return;
        }

        long delay = now - timestamp;
        timestamp = now;
        if (delay <= 0L) {
            return;
        }

        float maxTPS = 20f;
        float rawTPS = maxTPS * (1e9f / delay);
        float boundedTPS = MathHelper.clamp(rawTPS, 0, maxTPS);

        tpsSamples[sampleIndex % SAMPLE_SIZE] = boundedTPS;
        sampleIndex++;

        int sampleCount = Math.min(sampleIndex, SAMPLE_SIZE);
        float sum = 0;
        for (int i = 0; i < sampleCount; i++) {
            float sample = tpsSamples[i];
            sum += sample;
        }

        TPS = (float) round(sum / sampleCount);
        adjustTicks = TPS - maxTPS;
    }

    public double round(final double input) {
        return Math.round(input * 10.0) / 10.0;
    }
}

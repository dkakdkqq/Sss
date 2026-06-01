package fun.lumis.client.modules.impl.combat.components.gcd;


import fun.lumis.api.QClient;

public class GCDUtil implements QClient {
    public static float getFixedRotation(float rot) {
        return getDeltaMouse(rot) * getGCDValue();
    }
    public static float getGCDValue() {
        return (float) (getGCD() * 0.15D);
    }

    public static float getGCD() {
        double f = 0.5 * 0.6000000238418579D + 0.20000000298023224D;
        return (float)(f * f * f * 8.0D);
    }

    public static float getDeltaMouse(float delta) {
        return Math.round(delta / getGCDValue());
    }
}
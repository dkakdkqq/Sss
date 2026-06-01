package fun.lumis.client.modules.impl.combat.components.interpolation;

public abstract class Interpolator {
    private static final double EPSILON = 1.0E-12;
    public static final Interpolator DISCRETE = new Interpolator() {
        protected double curve(double var1) {
            return Math.abs(var1 - 1.0) < 1.0E-12 ? 1.0 : 0.0;
        }

        public String toString() {
            return "Interpolator.DISCRETE";
        }
    };
    public static final Interpolator LINEAR = new Interpolator() {
        protected double curve(double var1) {
            return var1;
        }

        public String toString() {
            return "Interpolator.LINEAR";
        }
    };
    public static final Interpolator EASE_BOTH = new Interpolator() {
        protected double curve(double var1) {
            return Interpolator.clamp(var1 < 0.2 ? 3.125 * var1 * var1 : (var1 > 0.8 ? -3.125 * var1 * var1 + 6.25 * var1 - 2.125 : 1.25 * var1 - 0.125));
        }

        public String toString() {
            return "Interpolator.EASE_BOTH";
        }
    };
    public static final Interpolator EASE_IN = new Interpolator() {
        private static final double S1 = 2.7777777777777777;
        private static final double S3 = 1.1111111111111112;
        private static final double S4 = 0.1111111111111111;

        protected double curve(double var1) {
            return Interpolator.clamp(var1 < 0.2 ? 2.7777777777777777 * var1 * var1 : 1.1111111111111112 * var1 - 0.1111111111111111);
        }

        public String toString() {
            return "Interpolator.EASE_IN";
        }
    };
    public static final Interpolator EASE_OUT = new Interpolator() {
        private static final double S1 = -2.7777777777777777;
        private static final double S2 = 5.555555555555555;
        private static final double S3 = -1.7777777777777777;
        private static final double S4 = 1.1111111111111112;

        protected double curve(double var1) {
            return Interpolator.clamp(var1 > 0.8 ? -2.7777777777777777 * var1 * var1 + 5.555555555555555 * var1 + -1.7777777777777777 : 1.1111111111111112 * var1);
        }

        public String toString() {
            return "Interpolator.EASE_OUT";
        }
    };

    protected Interpolator() {
    }






    public boolean interpolate(boolean var1, boolean var2, double var3) {
        return Math.abs(this.curve(var3) - 1.0) < 1.0E-12 ? var2 : var1;
    }

    public double interpolate(double var1, double var3, double var5) {
        return var1 + (var3 - var1) * this.curve(var5);
    }

    public int interpolate(int var1, int var2, double var3) {
        return var1 + (int)Math.round((double)(var2 - var1) * this.curve(var3));
    }

    public long interpolate(long var1, long var3, double var5) {
        return var1 + Math.round((double)(var3 - var1) * this.curve(var5));
    }

    private static double clamp(double var0) {
        return var0 < 0.0 ? 0.0 : (var0 > 1.0 ? 1.0 : var0);
    }

    protected abstract double curve(double var1);
}
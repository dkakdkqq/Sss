package fun.lumis.api.utils.animation;

@FunctionalInterface
public interface Easing {
    double ease(double value);
}
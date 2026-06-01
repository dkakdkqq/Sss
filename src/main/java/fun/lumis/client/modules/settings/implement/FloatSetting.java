package fun.lumis.client.modules.settings.implement;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.MathHelper;
import fun.lumis.client.modules.settings.Setting;

import java.util.function.Supplier;

@Getter
@Setter
public class FloatSetting extends Setting {

    private float value;
    private final float min;
    private final float max;
    private final float increment;
    private boolean active;

    public FloatSetting(String name, float value, float min, float max, float increment) {
        super(name);
        this.value = value;
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    public Number getValue() {
        return MathHelper.clamp(value, getMin(), getMax());
    }

    public void setValue(float value) {
        this.value = MathHelper .clamp(value, getMin(), getMax());
    }

    public float get() {
        return getValue().floatValue();
    }

    public FloatSetting visible(Supplier<Boolean> state) {
        this.visible = state;
        return this;
    }}

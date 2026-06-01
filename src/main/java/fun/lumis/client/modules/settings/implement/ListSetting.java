package fun.lumis.client.modules.settings.implement;

import lombok.Getter;
import lombok.Setter;
import fun.lumis.client.modules.settings.Setting;

import java.util.List;
import java.util.function.Supplier;

@Getter
@Setter
public class ListSetting extends Setting {

    public List<BooleanSetting> settings;

    public ListSetting(String name, BooleanSetting... settings) {
        super(name);
        this.settings = List.of(settings);
    }

    public ListSetting of(String name, BooleanSetting... settings) {
        return new ListSetting(name, settings);
    }

    public boolean is(String name) {
        return requireSetting(name).isState();
    }

    public void set(String name, boolean value) {
        requireSetting(name).setState(value);
    }

    public ListSetting visible(Supplier<Boolean> state) {
        this.visible = state;
        return this;
    }

    private BooleanSetting requireSetting(String name) {
        for (BooleanSetting option : this.settings) {
            if (option.name().equalsIgnoreCase(name)) {
                return option;
            }
        }
        throw new NullPointerException("Unknown list setting entry: " + name);
    }
}

package fun.lumis.client.modules.settings.implement;

import lombok.Getter;
import lombok.Setter;
import fun.lumis.client.modules.settings.Setting;

import java.util.function.Supplier;

@Getter
@Setter
public class TextSetting extends Setting {

    private String text;
    private final int maxLength;

    public TextSetting(String name, String text) {
        this(name, text, 32);
    }

    public TextSetting(String name, String text, int maxLength) {
        super(name);
        this.maxLength = Math.max(1, maxLength);
        setText(text);
    }

    public void setText(String text) {
        if (text == null) {
            this.text = "";
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length() && builder.length() < maxLength; i++) {
            char chr = text.charAt(i);
            if (!Character.isISOControl(chr)) {
                builder.append(chr);
            }
        }
        this.text = builder.toString();
    }

    public String get() {
        return text;
    }

    public TextSetting visible(Supplier<Boolean> state) {
        this.visible = state;
        return this;
    }
}

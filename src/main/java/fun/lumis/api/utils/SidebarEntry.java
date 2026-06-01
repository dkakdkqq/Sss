package fun.lumis.api.utils;

import net.minecraft.text.Text;

public class SidebarEntry {

    public final Text name;
    public final Text score;
    public final int scoreWidth;

    public SidebarEntry(Text name, Text score, int scoreWidth) {
        this.name = name;
        this.score = score;
        this.scoreWidth = scoreWidth;
    }

    public Text name() {
        return this.name;
    }

    public Text score() {
        return this.score;
    }

    public int scoreWidth() {
        return this.scoreWidth;
    }
}

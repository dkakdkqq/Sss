package fun.lumis.client.ui.autobuy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class AutoBuyHelper {
    private Group group;

    public AutoBuyHelper(Group group) {
        this.group = group;
    }

    @RequiredArgsConstructor @Getter
    public enum Group {
        RW("RW"),
        HW("HW"),
        FT("FT"),
        SP("SP");
        private final String server;
    }
}

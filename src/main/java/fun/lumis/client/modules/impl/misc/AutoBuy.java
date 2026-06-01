package fun.lumis.client.modules.impl.misc;

import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BindSetting;

public class AutoBuy extends Module {

    public static AutoBuy INSTANCE = new AutoBuy();

    public BindSetting openKey = new BindSetting("Бинд гуи", -1);

    public AutoBuy() {
        super("AutoBuy", ModuleCategory.MISC);
        addSettings(openKey);
    }
}


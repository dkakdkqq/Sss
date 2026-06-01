package fun.lumis.api.storages.implement;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.Setter;
import fun.lumis.api.QClient;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.impl.combat.*;
import fun.lumis.client.modules.impl.misc.*;
import fun.lumis.client.modules.impl.movement.*;
import fun.lumis.client.modules.impl.player.*;
import fun.lumis.client.modules.impl.render.*;
import java.util.Arrays;

@Getter
@Setter
public class ModuleStorage implements QClient {

    public ModuleStorage() {
        this.initModules();
    }

    private void initModules() {
        ModuleClass.INSTANCE.initialize();
    }
}

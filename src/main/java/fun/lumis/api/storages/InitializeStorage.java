package fun.lumis.api.storages;


import fun.lumis.Lumis;
import fun.lumis.api.QClient;
import fun.lumis.api.events.EventInvoker;
import fun.lumis.api.storages.implement.*;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.tps.TPSCalc;
import fun.lumis.client.modules.impl.render.TotemAngel;

public class InitializeStorage implements QClient {

    public void onInitialize() {
        EventInvoker.register(this);
        this.initStorages();
    }

    
    public void initStorages() {
        Lumis.INSTANCE.moduleStorage = new ModuleStorage();
        Lumis.INSTANCE.themeStorage = new ThemeStorage();
        Lumis.INSTANCE.tpsCalc = new TPSCalc();
        EventInvoker.register(Lumis.INSTANCE.tpsCalc);
        Lumis.INSTANCE.localizationStorage = new LocalizationStorage();
        Lumis.INSTANCE.freeLookStorage = new FreeLookStorage();
        Lumis.INSTANCE.rotationStorage = new RotationStorage();
        // Lumis.INSTANCE.serverStorage = new ServerStorage();
        Lumis.INSTANCE.friendStorage = new FriendStorage();
        Lumis.INSTANCE.macroStorage = new MacroStorage();
        Lumis.INSTANCE.staffStorage = new StaffStorage();
        Lumis.INSTANCE.waypointStorage = new WaypointStorage();
        Lumis.INSTANCE.commandStorage = new CommandStorage();
        Lumis.INSTANCE.configStorage = new ConfigStorage();
    }
}

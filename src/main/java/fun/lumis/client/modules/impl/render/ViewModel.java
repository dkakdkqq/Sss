package fun.lumis.client.modules.impl.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;

public class ViewModel extends Module {

    public static ViewModel INSTANCE = new ViewModel();

    public final FloatSetting mainHandX = new FloatSetting("Правая рука X", 0f, -2f, 2f, 0.01f);
    public final FloatSetting mainHandY = new FloatSetting("Правая рука Y", 0f, -2f, 2f, 0.01f);
    public final FloatSetting mainHandZ = new FloatSetting("Правая рука Z", 0f, -2f, 2f, 0.01f);

    public final FloatSetting offHandX = new FloatSetting("Левая рука X", 0f, -2f, 2f, 0.01f);
    public final FloatSetting offHandY = new FloatSetting("Левая рука Y", 0f, -2f, 2f, 0.01f);
    public final FloatSetting offHandZ = new FloatSetting("Левая рука Z", 0f, -2f, 2f, 0.01f);

    public final BooleanSetting onlyAura = new BooleanSetting("Только с аурой", false);

    public ViewModel() {
        super("ViewModel", "Оффсеты рук от первого лица", ModuleCategory.RENDER);
        addSettings(mainHandX, mainHandY, mainHandZ, offHandX, offHandY, offHandZ, onlyAura);
    }

    public void applyHandPosition(MatrixStack matrices, Arm arm) {
        if (arm == Arm.RIGHT) {
            matrices.translate(mainHandX.get(), mainHandY.get(), mainHandZ.get());
        } else {
            matrices.translate(offHandX.get(), offHandY.get(), offHandZ.get());
        }
    }
}

package fun.lumis.client.modules.impl.render;

import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.Priority;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.utils.render.hands.ShaderHandsRenderer;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

public class ShaderHands extends Module {

    public static ShaderHands INSTANCE = new ShaderHands();
    private static final ShaderHandsRenderer RENDERER = ShaderHandsRenderer.getInstance();
    public final ModeSetting mode = new ModeSetting("Режим", "Свечение", "Свечение", "Красивый");

    public final FloatSetting waveSpeed = new FloatSetting("Скорость волн", 1.2f, 0.1f, 5.0f, 0.1f)
            .visible(() -> mode.is("Красивый"));
    public final FloatSetting waveScale = new FloatSetting("Частота волн", 1.0f, 1.0f, 3.0f, 0.1f)
            .visible(() -> mode.is("Красивый"));

    public final FloatSetting outline = new FloatSetting("Ширина обводки", 1.2f, 0.1f, 5.0f, 0.1f);
    public final FloatSetting glow = new FloatSetting("Сила свечения", 1.0f, 0.0f, 5.0f, 0.1f);
    public final FloatSetting fill = new FloatSetting("Заливка", 0.6f, 0.0f, 1.0f, 0.01f);
    public final FloatSetting alpha = new FloatSetting("Прозрачность", 1.0f, 0.0f, 1.0f, 0.05f);

    public ShaderHands() {
        super("ShaderHands", "Красивый Шейдер на руки и предметы", ModuleCategory.RENDER);
        addSettings(mode, waveSpeed, waveScale, outline, glow, fill, alpha);
    }

    @Override
    public void onDisable() {
        RENDERER.invalidateState();
        super.onDisable();
    }

    @EventLink(priority = Priority.MEDIUM)
    public void onRender2D(EventRender.Default event) {
        if (!isEnable()) return;
        RENDERER.renderOverlayIfPending();
    }
}

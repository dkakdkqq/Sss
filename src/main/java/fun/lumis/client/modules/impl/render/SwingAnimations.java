package fun.lumis.client.modules.impl.render;

import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;
import fun.lumis.client.modules.settings.implement.FloatSetting;
import fun.lumis.client.modules.settings.implement.ModeSetting;

public class SwingAnimations extends Module {

    public static SwingAnimations INSTANCE = new SwingAnimations();

    public boolean swimmingAnimation = true;
    public boolean climbAndCrawl = true;
    public boolean mb3DCompat = false;

    public final BooleanSetting hmiEnable = new BooleanSetting("Мод на красивые руки", false);
    public final ModeSetting hmiAnimationType = new ModeSetting("Вид анимации", "Классик", "Классик", "Шарп")
            .visible(hmiEnable::isState);
    public final FloatSetting hmiSmoothness = new FloatSetting("Плавность анимации", 1.0f, 0.35f, 2.5f, 0.05f)
            .visible(hmiEnable::isState);

    public final BooleanSetting swingEnabled = new BooleanSetting("Анимация свинга", true)
            .visible(() -> !hmiEnable.isState());

    public final ModeSetting swingType = new ModeSetting(
            "Тип свинга",
            "Smooth",
            "Smooth", "Static", "Down", "DropDown", "Poke", "SelfBack",
            "Feast", "ToBack", "Block", "Akrien", "Break", "Pander", "Slant"
    ).visible(() -> !hmiEnable.isState() && swingEnabled.isState());

    public final FloatSetting swingStrength = new FloatSetting("Сила анимации", 1f, 0.1f, 3f, 0.01f)
            .visible(() -> !hmiEnable.isState() && swingEnabled.isState() && !swingType.is("Pander"));

    public final FloatSetting corner = new FloatSetting("Угол DropDown", 12f, 1f, 360f, 1f)
            .visible(() -> !hmiEnable.isState() && swingEnabled.isState() && swingType.is("DropDown"));

    public final FloatSetting slant = new FloatSetting("Наклон DropDown", 12f, 1f, 360f, 1f)
            .visible(() -> !hmiEnable.isState() && swingEnabled.isState() && swingType.is("DropDown"));

    public final BooleanSetting smoothEnabled = new BooleanSetting("Плавная анимация", false)
            .visible(() -> !hmiEnable.isState());

    public final FloatSetting slowAnimationSpeed = new FloatSetting("Скорость анимации", 12f, 1f, 50f, 1f)
            .visible(() -> !hmiEnable.isState() && smoothEnabled.isState());

    public final BooleanSetting auraTargetOnly = new BooleanSetting("Только при Aura", false)
            .visible(() -> !hmiEnable.isState());
    public final BooleanSetting swapHands = new BooleanSetting("Свап рук", false)
            .visible(() -> !hmiEnable.isState());
    public final BooleanSetting eatAnim = new BooleanSetting("Анимация еды", false)
            .visible(() -> !hmiEnable.isState());

    public SwingAnimations() {
        super("SwingAnimations", "Кастомная анимация аттаки", ModuleCategory.RENDER);
        addSettings(
                hmiEnable, hmiAnimationType, hmiSmoothness,
                swingEnabled, swingType, swingStrength, corner, slant,
                smoothEnabled, slowAnimationSpeed,
                auraTargetOnly, swapHands,
                eatAnim
        );
    }
}

package fun.lumis.client.ui.autobuy;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import fun.lumis.api.QClient;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.render.RenderUtils;

public class AutoBuy extends Screen implements QClient {

    private final float WIDTH = 170, HEIGHT = 240;
  //  private final AutoBuyHelper.Group group = AutoBuyHelper.Group.values().length;

    public AutoBuy() {
        super(Text.of("AutoBuy"));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float X = (mw.getScaledWidth() / 2f) - WIDTH;
        float Y = (mw.getScaledHeight() / 2f) - HEIGHT;

        RenderUtils.drawGradientRect(context.getMatrices(), X, Y, WIDTH, HEIGHT, 5, ColorUtils.getThemeColor(), ColorUtils.darken(ColorUtils.getThemeColor(), 0.5f), true);

       // for (AutoBuyHelper.Group)


        super.render(context, mouseX, mouseY, delta);
    }
}

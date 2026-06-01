package fun.lumis.api.utils.chat;

import java.awt.Color;
import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import fun.lumis.api.utils.color.ColorUtils;

@UtilityClass
public class ChatUtils {

    public void sendMessage(Object message) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) {
            System.out.println("[Lumis] " + message);
            return;
        }

        MutableText text = Text.literal("");
        String prefix = "Lumis";
        for (int i = 0; i < prefix.length(); i++) {
            text.append(Text.literal(String.valueOf(prefix.charAt(i)))
                    .setStyle(Style.EMPTY
                            .withBold(true)
                            .withColor(TextColor.fromRgb(ColorUtils.gradient(ColorUtils.getThemeColor(0), ColorUtils.getThemeColor(90), ((float) i / prefix.length()))))
                    ));
        }

        text.append(Text.literal(" ⇨ ")
                .setStyle(Style.EMPTY
                        .withBold(false)
                        .withColor(TextColor.fromRgb(new Color(200, 200, 200).getRGB()))
                ));

        text.append(Text.literal(String.valueOf(message))
                .setStyle(Style.EMPTY
                        .withBold(false)
                        .withColor(TextColor.fromRgb(new Color(200, 200, 200).getRGB()))
                ));

        mc.player.sendMessage(text, false);
    }
}
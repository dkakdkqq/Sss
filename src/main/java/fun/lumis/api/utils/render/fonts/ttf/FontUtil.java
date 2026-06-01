package fun.lumis.api.utils.render.fonts.ttf;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.awt.Font;
import java.io.InputStream;

public class FontUtil {
    public static Font getFontFromTTF(Identifier loc, float fontSize, int fontType) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return null;
            if (client.getResourceManager() == null) return null;

            var resource = client.getResourceManager().getResource(loc);
            if (resource.isPresent()) {
                InputStream inputStream = resource.get().getInputStream();
                Font output = Font.createFont(fontType, inputStream);
                output = output.deriveFont(fontSize);
                inputStream.close();
                return output;
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
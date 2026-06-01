package fun.lumis.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.Priority;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.modules.Module;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ShulkerPreview extends Module {

    public static ShulkerPreview INSTANCE = new ShulkerPreview();

    private static final float RECT_RADIUS = 5f;
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 7;
    private static final int ROWS = 3;
    private static final int COLS = 9;
    private static final int TITLE_HEIGHT = 14;
    private static final int SLOT_BG_COLOR = 0xFF8B8B8B;

    private Field guiLeftField;
    private Field guiTopField;

    private static ShulkerPreview instance;

    public ShulkerPreview() {
        super("ShulkerPreview", "Показывает содержимое шалкера при наведении + CTRL", ModuleCategory.RENDER);
        initReflection();
        instance = this;
    }

    public static ShulkerPreview getInstance() {
        return instance;
    }

    private void initReflection() {
        try {
            for (Field field : HandledScreen.class.getDeclaredFields()) {
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    String name = field.getName();
                    if (name.equals("x") || name.equals("field_2776") || name.contains("Left") || name.contains("guiLeft")) {
                        guiLeftField = field;
                    } else if (name.equals("y") || name.equals("field_2800") || name.contains("Top") || name.contains("guiTop")) {
                        guiTopField = field;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private int getGuiLeft(HandledScreen<?> screen) {
        try {
            if (guiLeftField != null) {
                return guiLeftField.getInt(screen);
            }
        } catch (Exception ignored) {}
        return (mc.getWindow().getScaledWidth() - 176) / 2;
    }

    private int getGuiTop(HandledScreen<?> screen) {
        try {
            if (guiTopField != null) {
                return guiTopField.getInt(screen);
            }
        } catch (Exception ignored) {}
        return (mc.getWindow().getScaledHeight() - 166) / 2;
    }

    public void renderFromMixin(DrawContext context, int mouseX, int mouseY) {
        if (!isEnable()) return;
        if (mc == null || mc.player == null || mc.currentScreen == null) return;

        if (!(mc.currentScreen instanceof HandledScreen<?> handledScreen)) return;

        long handle = mc.getWindow().getHandle();
        boolean isCtrlPressed = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;

        if (!isCtrlPressed) return;

        Slot hoveredSlot = getHoveredSlot(handledScreen);
        if (hoveredSlot == null) return;

        ItemStack stack = hoveredSlot.getStack();
        if (!isShulkerBox(stack)) return;

        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) return;

        renderShulkerPreview(context, stack, container, mouseX, mouseY);
    }

    private Slot getHoveredSlot(HandledScreen<?> screen) {
        try {
            var handler = screen.getScreenHandler();
            if (handler == null || handler.slots == null) return null;

            double mouseX = mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth();
            double mouseY = mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight();

            int guiLeft = getGuiLeft(screen);
            int guiTop = getGuiTop(screen);

            for (Slot slot : handler.slots) {
                int slotX = guiLeft + slot.x;
                int slotY = guiTop + slot.y;

                if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    return slot;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() == Items.SHULKER_BOX ||
                stack.getItem() == Items.WHITE_SHULKER_BOX ||
                stack.getItem() == Items.ORANGE_SHULKER_BOX ||
                stack.getItem() == Items.MAGENTA_SHULKER_BOX ||
                stack.getItem() == Items.LIGHT_BLUE_SHULKER_BOX ||
                stack.getItem() == Items.YELLOW_SHULKER_BOX ||
                stack.getItem() == Items.LIME_SHULKER_BOX ||
                stack.getItem() == Items.PINK_SHULKER_BOX ||
                stack.getItem() == Items.GRAY_SHULKER_BOX ||
                stack.getItem() == Items.LIGHT_GRAY_SHULKER_BOX ||
                stack.getItem() == Items.CYAN_SHULKER_BOX ||
                stack.getItem() == Items.PURPLE_SHULKER_BOX ||
                stack.getItem() == Items.BLUE_SHULKER_BOX ||
                stack.getItem() == Items.BROWN_SHULKER_BOX ||
                stack.getItem() == Items.GREEN_SHULKER_BOX ||
                stack.getItem() == Items.RED_SHULKER_BOX ||
                stack.getItem() == Items.BLACK_SHULKER_BOX;
    }

    private int getShulkerColor(ItemStack stack) {
        if (stack.getItem() == Items.SHULKER_BOX) return 0xFF9E6DBD;
        if (stack.getItem() == Items.WHITE_SHULKER_BOX) return 0xFFFFFFFF;
        if (stack.getItem() == Items.ORANGE_SHULKER_BOX) return 0xFFF9801D;
        if (stack.getItem() == Items.MAGENTA_SHULKER_BOX) return 0xFFC74EBD;
        if (stack.getItem() == Items.LIGHT_BLUE_SHULKER_BOX) return 0xFF3AB3DA;
        if (stack.getItem() == Items.YELLOW_SHULKER_BOX) return 0xFFFED83D;
        if (stack.getItem() == Items.LIME_SHULKER_BOX) return 0xFF80C71F;
        if (stack.getItem() == Items.PINK_SHULKER_BOX) return 0xFFF38BAA;
        if (stack.getItem() == Items.GRAY_SHULKER_BOX) return 0xFF474F52;
        if (stack.getItem() == Items.LIGHT_GRAY_SHULKER_BOX) return 0xFF9D9D97;
        if (stack.getItem() == Items.CYAN_SHULKER_BOX) return 0xFF169C9C;
        if (stack.getItem() == Items.PURPLE_SHULKER_BOX) return 0xFF8932B8;
        if (stack.getItem() == Items.BLUE_SHULKER_BOX) return 0xFF3C44AA;
        if (stack.getItem() == Items.BROWN_SHULKER_BOX) return 0xFF835432;
        if (stack.getItem() == Items.GREEN_SHULKER_BOX) return 0xFF5E7C16;
        if (stack.getItem() == Items.RED_SHULKER_BOX) return 0xFFB02E26;
        if (stack.getItem() == Items.BLACK_SHULKER_BOX) return 0xFF1D1D21;
        return 0xFF9E6DBD;
    }

    private void renderShulkerPreview(DrawContext context, ItemStack shulkerItem, ContainerComponent container, float mouseX, float mouseY) {
        var matrices = context.getMatrices();
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        float contentWidth = COLS * SLOT_SIZE;
        float contentHeight = ROWS * SLOT_SIZE;
        float totalWidth = contentWidth + PADDING * 2;
        float totalHeight = contentHeight + PADDING * 2 + TITLE_HEIGHT;

        float x = mouseX + 12;
        float y = mouseY - 12;

        if (x + totalWidth > screenWidth) {
            x = mouseX - totalWidth - 4;
        }
        if (y + totalHeight > screenHeight) {
            y = screenHeight - totalHeight - 4;
        }
        if (y < 4) {
            y = 4;
        }
        if (x < 4) {
            x = 4;
        }

        int shulkerColor = getShulkerColor(shulkerItem);
        int bgColor = ColorUtils.applyAlpha(shulkerColor, 0.85f);
        int darkerColor = darkenColor(shulkerColor, 0.6f);
        int lighterColor = lightenColor(shulkerColor, 1.3f);

        matrices.push();

        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        matrices.translate(0, 0, 500);

        RenderUtils.drawBlur(matrices, x - 2, y - 2, totalWidth + 4, totalHeight + 4, RECT_RADIUS + 2, 8.0f, -1);

        context.fill((int) x, (int) y, (int) (x + totalWidth), (int) (y + totalHeight), bgColor);

        context.fill((int) x, (int) y, (int) (x + totalWidth), (int) (y + 2), lighterColor);
        context.fill((int) x, (int) (y + totalHeight - 2), (int) (x + totalWidth), (int) (y + totalHeight), darkerColor);
        context.fill((int) x, (int) y, (int) (x + 2), (int) (y + totalHeight), lighterColor);
        context.fill((int) (x + totalWidth - 2), (int) y, (int) (x + totalWidth), (int) (y + totalHeight), darkerColor);

        var font = Fonts.getFont("sf_regular", 12);
        if (font != null) {
            String title = shulkerItem.getName().getString();
            float titleX = x + PADDING;
            float titleY = y + PADDING - 1;

            int textColor = isColorDark(shulkerColor) ? 0xFFFFFFFF : 0xFF1A1A1A;
            font.drawString(matrices, title, titleX, titleY, textColor);
        }

        float slotsX = x + PADDING;
        float slotsY = y + PADDING + TITLE_HEIGHT - 2;

        int slotAreaBg = darkenColor(shulkerColor, 0.5f);
        context.fill((int) (slotsX - 1), (int) (slotsY - 1),
                (int) (slotsX + contentWidth + 1), (int) (slotsY + contentHeight + 1),
                slotAreaBg);

        List<ItemStack> items = new ArrayList<>();
        container.stream().forEach(items::add);

        for (int i = 0; i < ROWS * COLS; i++) {
            int row = i / COLS;
            int col = i % COLS;

            int slotX = (int) (slotsX + col * SLOT_SIZE);
            int slotY = (int) (slotsY + row * SLOT_SIZE);

            context.fill(slotX, slotY, slotX + SLOT_SIZE - 2, slotY + SLOT_SIZE - 2, SLOT_BG_COLOR);

            context.fill(slotX, slotY, slotX + SLOT_SIZE - 2, slotY + 1, 0xFF555555);
            context.fill(slotX, slotY, slotX + 1, slotY + SLOT_SIZE - 2, 0xFF555555);
            context.fill(slotX, slotY + SLOT_SIZE - 3, slotX + SLOT_SIZE - 2, slotY + SLOT_SIZE - 2, 0xFFFFFFFF);
            context.fill(slotX + SLOT_SIZE - 3, slotY, slotX + SLOT_SIZE - 2, slotY + SLOT_SIZE - 2, 0xFFFFFFFF);

            if (i < items.size()) {
                ItemStack itemStack = items.get(i);
                if (!itemStack.isEmpty()) {
                    context.drawItem(itemStack, slotX, slotY);
                    context.drawStackOverlay(mc.textRenderer, itemStack, slotX, slotY);
                }
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private int lightenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) Math.min(255, ((color >> 16) & 0xFF) * factor);
        int g = (int) Math.min(255, ((color >> 8) & 0xFF) * factor);
        int b = (int) Math.min(255, (color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private boolean isColorDark(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return luminance < 0.5;
    }
}

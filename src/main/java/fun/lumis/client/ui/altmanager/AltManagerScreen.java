package fun.lumis.client.ui.altmanager;

import fun.lumis.api.QClient;
import fun.lumis.api.utils.animation.AnimationUtils;
import fun.lumis.api.utils.animation.Easings;
import fun.lumis.api.utils.color.ColorUtils;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Центрированное меню Alt Manager.
 *
 * <ul>
 *   <li>Список добавленных ников. ЛКМ — выбрать (переключить офлайн-сессию), ПКМ — удалить.</li>
 *   <li>Снизу: поле ввода ника + кнопка-галочка (добавить) + кнопка ChatGPT (рандомный ник).</li>
 * </ul>
 */
public class AltManagerScreen extends Screen implements QClient {

    private final Screen parent;

    // Палитра
    private static final int PANEL = 0xFF15151D;
    private static final int PANEL_BAR = 0xFF1B1B26;
    private static final int BORDER = 0x18FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUBTEXT = 0xFF9A9AA8;
    private static final int PLACEHOLDER = 0xFF66667A;
    private static final int ACCENT = 0xFF7D5CFF;
    private static final int ROW = 0xFF1F1F2B;
    private static final int ROW_HOVER = 0xFF2A2A3A;
    private static final int GPT_GREEN = 0xFF10A37F;
    private static final int DANGER = 0xFFE0556B;

    // Геометрия панели
    private final float pw = 372f;
    private final float ph = 338f;
    private final float headerH = 48f;
    private final float footerH = 46f;
    private final float rowH = 30f;
    private float panelX, panelY, listTop, footerTop;

    // Поле ввода
    private String input = "";
    private boolean inputFocused;

    // Список
    private float scroll;

    // Анимации кнопок
    private final AnimationUtils addAnim = new AnimationUtils(0f, 11f, Easings.CUBIC_OUT);
    private final AnimationUtils gptAnim = new AnimationUtils(0f, 11f, Easings.CUBIC_OUT);

    // Уведомление (флэш)
    private String flash = "";
    private long flashUntil;

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Alt Manager"));
        this.parent = parent;
    }

    private void layout() {
        panelX = (width - pw) / 2f;
        panelY = (height - ph) / 2f;
        listTop = panelY + headerH;
        footerTop = panelY + ph - footerH;
    }

    private Font font(int size) {
        return Fonts.getFont("sf_regular", size);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void drawV(Font f, MatrixStack ms, String text, float x, float centerY, int color) {
        if (f == null) return;
        f.drawString(ms, text, x, centerY - f.getHeight() / 4f, color);
    }

    private void drawCenterV(Font f, MatrixStack ms, String text, float cx, float centerY, int color) {
        if (f == null) return;
        f.drawCenteredString(ms, text, cx, centerY - f.getHeight() / 4f, color);
    }

    // --- Геометрия футера ---
    private float btnSize() {
        return 30f;
    }

    private float gptX() {
        return panelX + pw - 12f - btnSize();
    }

    private float checkX() {
        return gptX() - 8f - btnSize();
    }

    private float inputX() {
        return panelX + 12f;
    }

    private float inputW() {
        return checkX() - 8f - inputX();
    }

    private float footerWidgetY() {
        return footerTop + (footerH - btnSize()) / 2f;
    }

    @Override
    protected void init() {
        layout();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Свой фон рисуется в render(); вызов оставлен пустым, чтобы не было ванильного фона.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        layout();
        MatrixStack ms = context.getMatrices();

        // Фон (немедленная отрисовка; context.fillGradient буферизуется и перекрыл бы панель)
        RenderUtils.drawGradientRect(ms, 0, 0, width, height, 0xFF101018, 0xFF08080C);
        drawCenterV(font(40), ms, "lumis", width / 2f, height / 2f, 0x14FFFFFF);

        // Тень + панель
        RenderUtils.drawShadow(ms, panelX, panelY, pw, ph, 16f, 22f, 0x66000000);
        RenderUtils.drawRoundedRect(ms, panelX, panelY, pw, ph, 16f, PANEL);

        // Список (рисуется до баров, чтобы бары скрыли вылет за края)
        renderList(context, ms, mouseX, mouseY);

        // Хедер
        RenderUtils.drawRoundedRect(ms, panelX, panelY, pw, headerH, 16f, 16f, 0f, 0f, PANEL_BAR);
        drawV(font(22), ms, "Alt Manager", panelX + 16f, panelY + headerH / 2f - 4f, TEXT);
        String current = AltManager.currentName();
        Font small = font(13);
        drawV(small, ms, "Текущий: " + (current.isEmpty() ? "-" : current),
                panelX + 16f, panelY + headerH / 2f + 9f, SUBTEXT);
        String count = AltManager.size() + " альтов";
        if (small != null) {
            drawV(small, ms, count, panelX + pw - 16f - small.getStringWidth(count), panelY + headerH / 2f, ACCENT);
        }

        // Футер
        RenderUtils.drawRoundedRect(ms, panelX, footerTop, pw, ph - (footerTop - panelY), 0f, 0f, 16f, 16f, PANEL_BAR);
        renderFooter(context, ms, mouseX, mouseY, delta);

        // Рамка панели поверх всего
        RenderUtils.drawRoundedRectOutline(ms, panelX, panelY, pw, ph, 16f, 1.2f, BORDER, BORDER, BORDER, BORDER);

        // Флэш-сообщение
        if (!flash.isEmpty() && System.currentTimeMillis() < flashUntil) {
            Font f = font(13);
            if (f != null) {
                drawCenterV(f, ms, flash, panelX + pw / 2f, footerTop - 10f, DANGER);
            }
        }
    }

    private void renderList(DrawContext context, MatrixStack ms, int mouseX, int mouseY) {
        List<String> alts = AltManager.getAlts();
        float listBottom = footerTop;
        float listHeight = listBottom - listTop;

        clampScroll(alts.size(), listHeight);

        String current = AltManager.currentName();
        float y = listTop + 6f - scroll;

        if (alts.isEmpty()) {
            Font f = font(14);
            drawCenterV(f, ms, "Список пуст — добавьте ник снизу", panelX + pw / 2f, listTop + listHeight / 2f, SUBTEXT);
        }

        for (String name : alts) {
            float rowX = panelX + 10f;
            float rowW = pw - 20f;
            float top = y;
            float bottom = y + rowH;

            // Рисуем только то, что попадает в видимую область (бары скроют остальное)
            if (bottom >= listTop && top <= listBottom) {
                boolean hovered = inside(mouseX, mouseY, rowX, top, rowW, rowH)
                        && mouseY >= listTop && mouseY <= listBottom;
                boolean selected = name.equalsIgnoreCase(current);

                int rowColor = selected ? ColorUtils.rgba(45, 40, 70, 255) : (hovered ? ROW_HOVER : ROW);
                RenderUtils.drawRoundedRect(ms, rowX, top + 2f, rowW, rowH - 4f, 8f, rowColor);

                if (selected) {
                    RenderUtils.drawRoundedRect(ms, rowX, top + 6f, 3f, rowH - 12f, 1.5f, ACCENT);
                }

                // Голова
                RenderUtils.drawPlayerHead(ms, name, rowX + 9f, top + (rowH - 18f) / 2f, 18f, 4f);

                // Ник
                drawV(font(15), ms, name, rowX + 35f, top + rowH / 2f, TEXT);

                if (hovered) {
                    Font hint = font(11);
                    String hintText = "ЛКМ — выбрать  •  ПКМ — удалить";
                    if (hint != null) {
                        drawV(hint, ms, hintText, rowX + rowW - 12f - hint.getStringWidth(hintText), top + rowH / 2f, SUBTEXT);
                    }
                }
            }

            y += rowH;
        }

        // Скроллбар
        float contentH = alts.size() * rowH + 12f;
        if (contentH > listHeight) {
            float barTrackH = listHeight - 8f;
            float barH = Math.max(20f, barTrackH * (listHeight / contentH));
            float maxScroll = contentH - listHeight;
            float barY = listTop + 4f + (maxScroll <= 0 ? 0 : (scroll / maxScroll) * (barTrackH - barH));
            RenderUtils.drawRoundedRect(ms, panelX + pw - 6f, barY, 3f, barH, 1.5f, 0x40FFFFFF);
        }
    }

    private void renderFooter(DrawContext context, MatrixStack ms, int mouseX, int mouseY, float delta) {
        float bs = btnSize();
        float wy = footerWidgetY();
        float ix = inputX();
        float iw = inputW();

        // Поле ввода
        int inputBg = 0xFF24242F;
        RenderUtils.drawRoundedRect(ms, ix, wy, iw, bs, 8f, inputBg);
        if (inputFocused) {
            RenderUtils.drawRoundedRectOutline(ms, ix, wy, iw, bs, 8f, 1.2f, ACCENT, ACCENT, ACCENT, ACCENT);
        }

        Font inputFont = font(15);
        float textY = wy + bs / 2f;
        if (input.isEmpty() && !inputFocused) {
            drawV(inputFont, ms, "Введите ник...", ix + 9f, textY, PLACEHOLDER);
        } else {
            drawV(inputFont, ms, input, ix + 9f, textY, TEXT);
            if (inputFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                float caretX = ix + 9f + (inputFont != null ? inputFont.getStringWidth(input) : 0f) + 1f;
                RenderUtils.drawRoundedRect(ms, caretX, wy + 7f, 1.2f, bs - 14f, 0.5f, TEXT);
            }
        }

        // Кнопка-галочка (добавить)
        float cx = checkX();
        boolean checkHover = inside(mouseX, mouseY, cx, wy, bs, bs);
        addAnim.update(checkHover ? 1f : 0f);
        int checkBg = ColorUtils.interpolateColor(0xFF24242F, ACCENT, addAnim.getValue());
        RenderUtils.drawRoundedRect(ms, cx, wy, bs, bs, 8f, checkBg);
        drawCenterV(font(18), ms, "\u2713", cx + bs / 2f, wy + bs / 2f, TEXT);

        // Кнопка ChatGPT (рандомный ник)
        float gx = gptX();
        boolean gptHover = inside(mouseX, mouseY, gx, wy, bs, bs);
        gptAnim.update(gptHover ? 1f : 0f);
        int gptBg = ColorUtils.interpolateColor(GPT_GREEN, 0xFF15C79B, gptAnim.getValue());
        RenderUtils.drawRoundedRect(ms, gx, wy, bs, bs, 8f, gptBg);
        // Стилизованный логотип ChatGPT: искра + подпись
        drawCenterV(font(13), ms, "GPT", gx + bs / 2f, wy + bs / 2f - 4f, 0xFFFFFFFF);
        drawCenterV(font(11), ms, "\u2726", gx + bs / 2f, wy + bs / 2f + 7f, 0xFFE9FFF7);
    }

    private void clampScroll(int rows, float listHeight) {
        float contentH = rows * rowH + 12f;
        float maxScroll = Math.max(0f, contentH - listHeight);
        if (scroll < 0f) scroll = 0f;
        if (scroll > maxScroll) scroll = maxScroll;
    }

    private void setFlash(String text) {
        this.flash = text;
        this.flashUntil = System.currentTimeMillis() + 1800L;
    }

    private void addCurrentInput() {
        String name = input.trim();
        if (name.isEmpty()) {
            return;
        }
        if (!AltManager.isValid(name)) {
            setFlash("Недопустимый ник (1-16 символов A-Z 0-9 _)");
            return;
        }
        if (AltManager.add(name)) {
            input = "";
        } else {
            setFlash("Такой ник уже добавлен");
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layout();

        float bs = btnSize();
        float wy = footerWidgetY();

        // Поле ввода — фокус
        if (inside(mouseX, mouseY, inputX(), wy, inputW(), bs)) {
            inputFocused = true;
            return true;
        }

        // Кнопка-галочка — добавить
        if (button == 0 && inside(mouseX, mouseY, checkX(), wy, bs, bs)) {
            addCurrentInput();
            return true;
        }

        // Кнопка ChatGPT — сгенерировать рандомный ник в поле ввода
        if (button == 0 && inside(mouseX, mouseY, gptX(), wy, bs, bs)) {
            input = RandomNickGenerator.generate();
            inputFocused = true;
            return true;
        }

        // Клики по списку
        float listBottom = footerTop;
        List<String> alts = AltManager.getAlts();
        float y = listTop + 6f - scroll;
        for (String name : alts) {
            float rowX = panelX + 10f;
            float rowW = pw - 20f;
            if (mouseY >= listTop && mouseY <= listBottom
                    && inside(mouseX, mouseY, rowX, y + 2f, rowW, rowH - 4f)) {
                if (button == 0) {
                    if (AltManager.apply(name)) {
                        setFlash("Аккаунт: " + name);
                    }
                } else if (button == 1) {
                    AltManager.remove(name);
                }
                return true;
            }
            y += rowH;
        }

        // Клик вне панели — снять фокус с поля
        inputFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        layout();
        if (mouseX >= panelX && mouseX <= panelX + pw && mouseY >= listTop && mouseY <= footerTop) {
            scroll -= (float) verticalAmount * rowH;
            clampScroll(AltManager.size(), footerTop - listTop);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inputFocused) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!input.isEmpty()) {
                        input = input.substring(0, input.length() - 1);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    addCurrentInput();
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    inputFocused = false;
                    return true;
                }
                default -> {
                    return true;
                }
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inputFocused && input.length() < 16) {
            if ((chr >= 'a' && chr <= 'z') || (chr >= 'A' && chr <= 'Z')
                    || (chr >= '0' && chr <= '9') || chr == '_') {
                input += chr;
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

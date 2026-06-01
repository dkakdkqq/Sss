package fun.lumis.api.utils.input;

import lombok.experimental.UtilityClass;
import org.lwjgl.glfw.GLFW;
import fun.lumis.Lumis;
import fun.lumis.api.QClient;
import fun.lumis.api.events.implement.EventBinding;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.client.ClientSoundPlayer;
import fun.lumis.client.ui.MenuPanel;
import fun.lumis.client.ui.autobuy.AutoBuy;

@UtilityClass
public class KeyBoardUtils implements QClient {

    public static final int MOUSE_BUTTON_OFFSET = 1000;

    public void call(int key, int action) {
        if (key <= -1) {
            return;
        }
        if (action == 1) {
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                ClientSoundPlayer.playSound("opengui.wav", 0.6, 1.0f);
                mc.setScreen(new MenuPanel());
            }
            if (key == ModuleClass.INSTANCE.autoBuy.openKey.getKey()) {
                mc.setScreen(new AutoBuy());
            }

            new EventBinding(key, EventBinding.BindType.KEYBOARD).call();

            var modules = ModuleClass.INSTANCE.getObject();
            for (int i = 0, size = modules.size(); i < size; i++) {
                var module = modules.get(i);
                if (module.getKey() == key) {
                    module.toggle();
                }
            }
        }
    }

    public String getKeyName(int keyCode) {
        if (keyCode == -1) return "None";
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        if (name != null) return name.toUpperCase();
        return switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> "ESC";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            default -> "KEY" + keyCode;
        };
    }


    public void callMouse(int button, int action) {
        if (mc.currentScreen != null) {
            return;
        }

        if (button < 0) {
            return;
        }

        if (action == 1) {
            int mouseKey = MOUSE_BUTTON_OFFSET + button;

            new EventBinding(mouseKey, EventBinding.BindType.MOUSE).call();

            var modules = ModuleClass.INSTANCE.getObject();
            for (int i = 0, size = modules.size(); i < size; i++) {
                var module = modules.get(i);
                if (module.getKey() == mouseKey) {
                    module.toggle();
                }
            }
        }
    }

    public boolean isBindHeld(int key) {
        if (key == -1) return false;

        long window = mc.getWindow().getHandle();

        if (key >= MOUSE_BUTTON_OFFSET) {
            int mouseButton = key - MOUSE_BUTTON_OFFSET;
            return GLFW.glfwGetMouseButton(window, mouseButton) == GLFW.GLFW_PRESS;
        } else {
            return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        }
    }

    public boolean isBindPressed(int key) {
        return isBindHeld(key);
    }

    public String getBindName(int key) {
        if (key == -1) {
            return "n/a";
        } else if (key >= MOUSE_BUTTON_OFFSET) {
            int mouseButton = key - MOUSE_BUTTON_OFFSET;
            return switch (mouseButton) {
                case 0 -> "ЛКМ";
                case 1 -> "ПКМ";
                case 2 -> "СКМ";
                case 3 -> "MOUSE4";
                case 4 -> "MOUSE5";
                default -> "MOUSE" + (mouseButton + 1);
            };
        } else {
            if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
                return String.valueOf((char) ('A' + (key - GLFW.GLFW_KEY_A)));
            }

            if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
                return String.valueOf((char) ('0' + (key - GLFW.GLFW_KEY_0)));
            }

            String symbol = switch (key) {
                case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
                case GLFW.GLFW_KEY_MINUS -> "-";
                case GLFW.GLFW_KEY_EQUAL -> "=";
                case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
                case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
                case GLFW.GLFW_KEY_BACKSLASH -> "\\";
                case GLFW.GLFW_KEY_SEMICOLON -> ";";
                case GLFW.GLFW_KEY_APOSTROPHE -> "'";
                case GLFW.GLFW_KEY_COMMA -> ",";
                case GLFW.GLFW_KEY_PERIOD -> ".";
                case GLFW.GLFW_KEY_SLASH -> "/";
                default -> null;
            };
            if (symbol != null) {
                return symbol;
            }
            return switch (key) {
                case GLFW.GLFW_KEY_SPACE -> "SPACE";
                case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
                case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
                case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
                case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
                case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
                case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
                case GLFW.GLFW_KEY_TAB -> "TAB";
                case GLFW.GLFW_KEY_ENTER -> "ENTER";
                case GLFW.GLFW_KEY_ESCAPE -> "ESC";
                case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
                case GLFW.GLFW_KEY_DELETE -> "DELETE";
                case GLFW.GLFW_KEY_INSERT -> "INSERT";
                case GLFW.GLFW_KEY_HOME -> "HOME";
                case GLFW.GLFW_KEY_END -> "END";
                case GLFW.GLFW_KEY_PAGE_UP -> "PAGEUP";
                case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGEDOWN";
                case GLFW.GLFW_KEY_UP -> "UP";
                case GLFW.GLFW_KEY_DOWN -> "DOWN";
                case GLFW.GLFW_KEY_LEFT -> "LEFT";
                case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
                case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
                case GLFW.GLFW_KEY_F1 -> "F1";
                case GLFW.GLFW_KEY_F2 -> "F2";
                case GLFW.GLFW_KEY_F3 -> "F3";
                case GLFW.GLFW_KEY_F4 -> "F4";
                case GLFW.GLFW_KEY_F5 -> "F5";
                case GLFW.GLFW_KEY_F6 -> "F6";
                case GLFW.GLFW_KEY_F7 -> "F7";
                case GLFW.GLFW_KEY_F8 -> "F8";
                case GLFW.GLFW_KEY_F9 -> "F9";
                case GLFW.GLFW_KEY_F10 -> "F10";
                case GLFW.GLFW_KEY_F11 -> "F11";
                case GLFW.GLFW_KEY_F12 -> "F12";
                case GLFW.GLFW_KEY_KP_0 -> "NUM0";
                case GLFW.GLFW_KEY_KP_1 -> "NUM1";
                case GLFW.GLFW_KEY_KP_2 -> "NUM2";
                case GLFW.GLFW_KEY_KP_3 -> "NUM3";
                case GLFW.GLFW_KEY_KP_4 -> "NUM4";
                case GLFW.GLFW_KEY_KP_5 -> "NUM5";
                case GLFW.GLFW_KEY_KP_6 -> "NUM6";
                case GLFW.GLFW_KEY_KP_7 -> "NUM7";
                case GLFW.GLFW_KEY_KP_8 -> "NUM8";
                case GLFW.GLFW_KEY_KP_9 -> "NUM9";
                case GLFW.GLFW_KEY_KP_DECIMAL -> "NUM.";
                case GLFW.GLFW_KEY_KP_DIVIDE -> "NUM/";
                case GLFW.GLFW_KEY_KP_MULTIPLY -> "NUM*";
                case GLFW.GLFW_KEY_KP_SUBTRACT -> "NUM-";
                case GLFW.GLFW_KEY_KP_ADD -> "NUM+";
                case GLFW.GLFW_KEY_KP_ENTER -> "NUMENTER";
                default -> "KEY" + key;
            };
        }
    }

    public boolean isMouseButton(int key) {
        return key >= MOUSE_BUTTON_OFFSET;
    }

    public int getMouseButtonFromKey(int key) {
        if (isMouseButton(key)) {
            return key - MOUSE_BUTTON_OFFSET;
        }
        return -1;
    }

    public int createMouseBind(int mouseButton) {
        return MOUSE_BUTTON_OFFSET + mouseButton;
    }
}

    package fun.lumis.client.modules.impl.combat;

    import net.minecraft.item.Item;
    import net.minecraft.item.ItemStack;
    import net.minecraft.item.Items;
    import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
    import net.minecraft.screen.slot.SlotActionType;
    
    import fun.lumis.api.events.EventLink;
    import fun.lumis.api.events.implement.EventBinding;
    import fun.lumis.api.events.implement.EventMoveInput;
    import fun.lumis.api.events.implement.EventUpdate;
    import fun.lumis.client.modules.Module;
    import fun.lumis.client.modules.impl.movement.Sprint;
    import fun.lumis.client.modules.settings.implement.BindSetting;
    import fun.lumis.client.modules.settings.implement.BooleanSetting;
    import fun.lumis.client.modules.settings.implement.ModeSetting;

    public class AutoSwap extends Module {

        public static AutoSwap INSTANCE = new AutoSwap();

        private final ModeSetting firstItem = new ModeSetting("Первый предмет", "Руна", "Руна", "Тотем", "Шар", "Гепл", "Щит");
        private final ModeSetting secondItem = new ModeSetting("Второй предмет", "Тотем", "Руна", "Тотем", "Шар", "Гепл", "Щит");
        private final BindSetting swapKey = new BindSetting("Кнопка свапа", -98);
        private final BooleanSetting bypassgrim = new BooleanSetting("Обходить Grim", true);

        private int bypassTicks;
        private boolean sprintPaused;
        private int swapCooldown;
        private int targetSlot = -1;
        private boolean needSwap = false;

        public AutoSwap() {
            super("AutoSwap", "Быстрая смена предметов в офф-хенде", ModuleCategory.COMBAT);
            addSettings(firstItem, secondItem, swapKey, bypassgrim);
        }

        @Override
        public void onEnable() {
            this.needSwap = false;
            this.targetSlot = -1;
            this.bypassTicks = 0;
            this.swapCooldown = 0;
            super.onEnable();
        }

        @EventLink
        public void onBinding(final EventBinding event) {
            if (mc.currentScreen != null) return;
            if (mc.player == null || mc.world == null) return;

            if (event.getKey() == swapKey.getKey()) {
                if (swapCooldown == 0) {
                    this.needSwap = true;
                }
            }
        }

        @EventLink
        public void onInput(final EventMoveInput e) {
            if (bypassgrim.isState() && bypassTicks > 0) {
                if (mc.player == null) return;
                mc.player.setSprinting(false);
                e.setForward(0);
                e.setStrafe(0);
                e.setJump(false);
                e.setSneak(false);
            }
        }

        @EventLink
        public void onUpdate(final EventUpdate e) {
            if (mc.player == null || mc.world == null) return;

            if (swapCooldown > 0) {
                swapCooldown--;
            }

            if (bypassgrim.isState() && bypassTicks > 0) {
                mc.player.setSprinting(false);
                bypassTicks--;

                if (bypassTicks == 1) {
                    performSwap();
                }

                if (bypassTicks == 0) {
                    restoreSprint();
                }
                return;
            }

            if (needSwap && targetSlot == -1) {
                needSwap = false;

                Item offhand = mc.player.getOffHandStack().getItem();
                Item first = getItem(firstItem.getCurrent());
                Item second = getItem(secondItem.getCurrent());

                int firstSlot = findItemSlot(first);
                int secondSlot = findItemSlot(second);

                if (firstSlot == -1 && secondSlot == -1) return;

                int slot;
                if (offhand == first && secondSlot != -1) {
                    slot = secondSlot;
                } else if (firstSlot != -1) {
                    slot = firstSlot;
                } else {
                    slot = secondSlot;
                }

                if (slot == -1) return;

                targetSlot = slot;

                if (bypassgrim.isState()) {
                    disableSprint();
                    bypassTicks = 2;
                    swapCooldown = 2;
                } else {
                    performSwap();
                    swapCooldown = 2;
                }
            }
        }

        
        private void performSwap() {
            if (targetSlot == -1) return;

            doSwap(targetSlot);
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(0));

            targetSlot = -1;
        }

        private void doSwap(int slot) {
            if (slot >= 36 && slot <= 44) {
                int hotbarSlot = slot - 36;
                mc.interactionManager.clickSlot(0, 45, hotbarSlot, SlotActionType.SWAP, mc.player);
            } else {
                mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
                mc.interactionManager.clickSlot(0, 45, 0, SlotActionType.SWAP, mc.player);
                mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.SWAP, mc.player);
            }
        }

        
        private int findItemSlot(Item item) {
            for (int i = 9; i < 45; i++) {
                ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
                if (stack.getItem() == item) {
                    return i;
                }
            }
            return -1;
        }

        private Item getItem(String name) {
            return switch (name) {
                case "Руна" -> Items.FIREWORK_STAR;
                case "Тотем" -> Items.TOTEM_OF_UNDYING;
                case "Шар" -> Items.PLAYER_HEAD;
                case "Гепл" -> Items.GOLDEN_APPLE;
                case "Щит" -> Items.SHIELD;
                default -> Items.AIR;
            };
        }

        private void disableSprint() {
            if (sprintPaused) {
                return;
            }

            Sprint.pushPause(1000);
            sprintPaused = true;
        }

        private void restoreSprint() {
            if (!sprintPaused) {
                return;
            }

            sprintPaused = false;
            Sprint.popPause();
        }

        @Override
        public void onDisable() {
            bypassTicks = 0;
            swapCooldown = 0;
            needSwap = false;
            targetSlot = -1;
            restoreSprint();
            super.onDisable();
        }
    }

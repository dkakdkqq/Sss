package fun.lumis.client.modules.impl.player;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import fun.lumis.api.events.EventLink;
import fun.lumis.api.events.implement.EventPacket;
import fun.lumis.api.events.implement.EventUpdate;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.client.modules.Module;
import fun.lumis.client.modules.settings.implement.BooleanSetting;

public class AutoFish extends Module {

    public static AutoFish INSTANCE = new AutoFish();

    private final BooleanSetting takeRod = new BooleanSetting("Автоматически брать удочку", true);

    private boolean isCached = false;
    private boolean needCached = false;
    private int rodHotbarSlot = -1;
    private long lastActionTime = 0L;
    private long catchTime = 0L;

    public AutoFish() {
        super("AutoFish", "Автоматизирует процесс рыбалки", ModuleCategory.PLAYER);
        addSettings(takeRod);
    }

    @Override
    public void onDisable() {
        isCached = false;
        needCached = false;
        rodHotbarSlot = -1;
        lastActionTime = 0L;
        catchTime = 0L;
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (takeRod.isState() && rodHotbarSlot == -1) {
            findBestFishingRodInHotbar();
        }

        if (rodHotbarSlot != -1 && mc.player.getInventory().selectedSlot != rodHotbarSlot) {
            mc.player.getInventory().selectedSlot = rodHotbarSlot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(rodHotbarSlot));
        }

        long currentTime = System.currentTimeMillis();

        if (isCached && currentTime - catchTime >= 600) {
            useFishingRod();
            isCached = false;
            needCached = true;
            lastActionTime = currentTime;
        }

        if (needCached && currentTime - lastActionTime >= 300) {
            useFishingRod();
            needCached = false;
            lastActionTime = currentTime;
        }
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (event.getPacket() instanceof PlaySoundS2CPacket packet) {
            if (packet.getSound().value() == SoundEvents.ENTITY_FISHING_BOBBER_SPLASH) {
                isCached = true;
                catchTime = System.currentTimeMillis();
            }
        }
    }

    private void useFishingRod() {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        if (rodHotbarSlot != -1 && rodHotbarSlot < 9) {
            ItemStack stack = mc.player.getInventory().getStack(rodHotbarSlot);

            if (stack.getItem() instanceof FishingRodItem) {
                if (mc.player.getInventory().selectedSlot != rodHotbarSlot) {
                    mc.player.getInventory().selectedSlot = rodHotbarSlot;
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(rodHotbarSlot));
                }

                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
        }
    }

    private void findBestFishingRodInHotbar() {
        if (mc.player == null) {
            return;
        }

        int bestRodSlot = -1;
        int maxEnchantments = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.getItem() instanceof FishingRodItem) {
                int enchantmentCount = EnchantmentHelper.getEnchantments(stack).getSize();

                if (enchantmentCount > maxEnchantments) {
                    maxEnchantments = enchantmentCount;
                    bestRodSlot = i;
                }
            }
        }

        if (bestRodSlot != -1) {
            rodHotbarSlot = bestRodSlot;
        }
    }
}
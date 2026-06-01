package fun.lumis.api.utils.player;

import lombok.experimental.UtilityClass;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.NotNull;
import fun.lumis.api.QClient;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@UtilityClass
public class HotbarUtil implements QClient {
    private int cachedSlot = -1;

    public int getItemCount(Item item) {
        if (mc.player == null) return 0;

        int counter = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(item)) counter += stack.getCount();
        }
        return counter;
    }

    public SlotSearchResult getAxe() {
        return findBest(itemStack -> itemStack.getItem() instanceof AxeItem, false);
    }

    public SlotSearchResult getAxeHotBar() {
        return findBest(itemStack -> itemStack.getItem() instanceof AxeItem, true);
    }

    public SlotSearchResult getPickAxe() {
        return findBest(itemStack -> itemStack.getItem() instanceof PickaxeItem, false);
    }

    public SlotSearchResult getPickAxeHotbar() {
        return getPickAxeHotBar();
    }

    public SlotSearchResult getPickAxeHotBar() {
        return findBest(itemStack -> itemStack.getItem() instanceof PickaxeItem, true);
    }

    public SlotSearchResult getSword() {
        return findBest(itemStack -> itemStack.getItem() instanceof SwordItem, false);
    }

    public SlotSearchResult getSwordHotBar() {
        return findBest(itemStack -> itemStack.getItem() instanceof SwordItem, true);
    }

    public SlotSearchResult getSkull() {
        return findInHotBar(stack -> stack.isOf(Items.SKELETON_SKULL)
                || stack.isOf(Items.WITHER_SKELETON_SKULL)
                || stack.isOf(Items.CREEPER_HEAD)
                || stack.isOf(Items.PLAYER_HEAD)
                || stack.isOf(Items.ZOMBIE_HEAD));
    }

    public int getElytra() {
        if (mc.player == null) return -1;

        for (ItemStack stack : mc.player.getInventory().armor) {
            if (stack.isOf(Items.ELYTRA) && stack.getDamage() < stack.getMaxDamage() - 1) {
                return -2;
            }
        }

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.ELYTRA) && stack.getDamage() < stack.getMaxDamage() - 1) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    public SlotSearchResult findInHotBar(Searcher searcher) {
        if (mc.player != null) {
            if (searcher.isValid(mc.player.getOffHandStack())) {
                return SlotSearchResult.inOffhand(mc.player.getOffHandStack());
            }

            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (searcher.isValid(stack)) return new SlotSearchResult(i, true, stack);
            }
        }
        return SlotSearchResult.notFound();
    }

    public SlotSearchResult findItemInHotBar(List<Item> items) {
        return findInHotBar(stack -> items.contains(stack.getItem()));
    }

    public SlotSearchResult findItemInHotBar(Item... items) {
        return findItemInHotBar(Arrays.asList(items));
    }

    public SlotSearchResult findInInventory(Searcher searcher) {
        if (mc.player != null) {
            for (int i = 35; i >= 0; i--) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (searcher.isValid(stack)) return new SlotSearchResult(i, true, stack);
            }
        }
        return SlotSearchResult.notFound();
    }

    public SlotSearchResult findItemInInventory(List<Item> items) {
        return findInInventory(stack -> items.contains(stack.getItem()));
    }

    public SlotSearchResult findItemInInventory(Item... items) {
        return findItemInInventory(Arrays.asList(items));
    }

    public SlotSearchResult findBlockInHotBar(@NotNull List<Block> blocks) {
        return findItemInHotBar(blocks.stream().map(Block::asItem).toList());
    }

    public SlotSearchResult findBlockInHotBar(Block... blocks) {
        return findItemInHotBar(Arrays.stream(blocks).map(Block::asItem).toList());
    }

    public SlotSearchResult findBlockInInventory(@NotNull List<Block> blocks) {
        return findItemInInventory(blocks.stream().map(Block::asItem).toList());
    }

    public SlotSearchResult findBlockInInventory(Block... blocks) {
        return findItemInInventory(Arrays.stream(blocks).map(Block::asItem).toList());
    }

    public void saveSlot() {
        if (mc.player != null) cachedSlot = mc.player.getInventory().selectedSlot;
    }

    public void returnSlot() {
        if (cachedSlot != -1) switchTo(cachedSlot);
        cachedSlot = -1;
    }

    public void saveAndSwitchTo(int slot) {
        saveSlot();
        switchTo(slot);
    }

    public void switchTo(int slot) {
        if (mc.player == null || mc.getNetworkHandler() == null || slot < 0 || slot > 8) return;
        if (mc.player.getInventory().selectedSlot == slot) return;
        mc.player.getInventory().selectedSlot = slot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    public void switchToSilent(int slot) {
        if (mc.player == null || mc.getNetworkHandler() == null || slot < 0 || slot > 8) return;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    public SlotSearchResult getAntiWeaknessItem() {
        if (mc.player == null) return SlotSearchResult.notFound();

        Item mainHand = mc.player.getMainHandStack().getItem();
        if (mainHand instanceof SwordItem
                || mainHand instanceof PickaxeItem
                || mainHand instanceof AxeItem
                || mainHand instanceof ShovelItem) {
            return new SlotSearchResult(mc.player.getInventory().selectedSlot, true, mc.player.getMainHandStack());
        }

        return findInHotBar(stack -> stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof PickaxeItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof ShovelItem);
    }

    public float getHitDamage(@NotNull ItemStack weapon, PlayerEntity entity) {
        if (mc.player == null || mc.world == null) return 0.0f;

        float baseDamage = getBaseAttackDamage(weapon);

        if (mc.player.fallDistance > 0.0f) baseDamage += baseDamage / 2.0f;

        if (mc.player.hasStatusEffect(StatusEffects.STRENGTH)) {
            int strength = Objects.requireNonNull(mc.player.getStatusEffect(StatusEffects.STRENGTH)).getAmplifier() + 1;
            baseDamage += 3.0f * strength;
        }

        return DamageUtil.getDamageLeft(entity, baseDamage, mc.world.getDamageSources().generic(), entity.getArmor(),
                (float) entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));
    }

    public SlotSearchResult findBedInHotBar() {
        return findInHotBar(stack -> stack.getItem() instanceof BedItem);
    }

    public SlotSearchResult findBed() {
        return findInInventory(stack -> stack.getItem() instanceof BedItem);
    }

    public Item getItem(String name) {
        if (name == null) return Items.AIR;
        String normalized = name.toLowerCase();

        for (Block block : Registries.BLOCK) {
            if (block.getTranslationKey().replace("block.minecraft.", "").equals(normalized)) {
                return Item.fromBlock(block);
            }
        }

        for (Item item : Registries.ITEM) {
            if (item.getTranslationKey().replace("item.minecraft.", "").equals(normalized)) {
                return item;
            }
        }

        return Items.DIRT;
    }

    public int getBedsCount() {
        if (mc.player == null) return 0;

        int counter = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof BedItem) counter += stack.getCount();
        }
        return counter;
    }

    private SlotSearchResult findBest(Searcher searcher, boolean hotbarOnly) {
        if (mc.player == null) return SlotSearchResult.notFound();

        int bestSlot = -1;
        float bestDamage = 0.0f;
        int end = hotbarOnly ? 8 : 35;

        for (int i = 0; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!searcher.isValid(stack)) continue;

            float damage = getBaseAttackDamage(stack);
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = i;
            }
        }

        return bestSlot == -1
                ? SlotSearchResult.notFound()
                : new SlotSearchResult(bestSlot, true, mc.player.getInventory().getStack(bestSlot));
    }

    private float getBaseAttackDamage(ItemStack stack) {
        AttributeModifiersComponent component = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        double damage = 1.0;

        for (AttributeModifiersComponent.Entry entry : component.modifiers()) {
            if (entry.attribute().equals(EntityAttributes.ATTACK_DAMAGE)) {
                damage += entry.modifier().value();
            }
        }

        return (float) damage;
    }

    public boolean isHolding(Item item) {
        return mc.player != null && (mc.player.getMainHandStack().isOf(item) || mc.player.getOffHandStack().isOf(item));
    }

    public Hand getHand(Item item) {
        if (mc.player == null) return null;
        if (mc.player.getOffHandStack().isOf(item)) return Hand.OFF_HAND;
        if (mc.player.getMainHandStack().isOf(item)) return Hand.MAIN_HAND;
        return null;
    }

    public interface Searcher {
        boolean isValid(ItemStack stack);
    }
}

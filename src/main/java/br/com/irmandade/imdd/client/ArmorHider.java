package br.com.irmandade.imdd.client;

import br.com.irmandade.imdd.relic.RelicRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Esconde a armadura no render state sem depender dos nomes dos campos (que mudam entre versões):
 * varre os campos do tipo ItemStack e esvazia os que guardam armadura. Elytra, abóbora e cabeças ficam.
 */
public final class ArmorHider {
    private static final Map<Class<?>, List<Field>> CACHE = new ConcurrentHashMap<>();

    private static final ThreadLocal<Boolean> GUARD = ThreadLocal.withInitial(() -> false);

    private ArmorHider() {}

    /** No cliente, quem usa a Relíquia da Invisibilidade "não tem" armadura (exceto elytra, abóbora e cabeças). */
    public static ItemStack filter(LivingEntity e, EquipmentSlot slot, ItemStack original) {
        if (original == null || original.isEmpty() || GUARD.get()) return original;
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) return original;
        if (original.is(Items.ELYTRA) || original.is(Items.CARVED_PUMPKIN) || original.is(ItemTags.SKULLS)) return original;
        Level w = e.level();
        if (w == null || !w.isClientSide()) return original;
        GUARD.set(true);
        try {
            return RelicRegistry.is(e.getItemBySlot(EquipmentSlot.FEET), "invisibilidade") ? ItemStack.EMPTY : original;
        } finally {
            GUARD.set(false);
        }
    }

    private static List<Field> stackFields(Class<?> type) {
        return CACHE.computeIfAbsent(type, k -> {
            List<Field> out = new ArrayList<>();
            for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == ItemStack.class && !Modifier.isStatic(f.getModifiers())) {
                        try { f.setAccessible(true); out.add(f); } catch (Exception ignored) {}
                    }
                }
            }
            return out;
        });
    }

    public static void hide(Object state) {
        for (Field f : stackFields(state.getClass())) {
            try {
                ItemStack cur = (ItemStack) f.get(state);
                if (cur == null || cur.isEmpty()) continue;
                if (cur.is(Items.ELYTRA) || cur.is(Items.CARVED_PUMPKIN) || cur.is(ItemTags.SKULLS)) continue;
                Equippable eq = cur.get(DataComponents.EQUIPPABLE);
                if (eq == null) continue;
                EquipmentSlot s = eq.slot();
                if (s == EquipmentSlot.HEAD || s == EquipmentSlot.CHEST || s == EquipmentSlot.LEGS || s == EquipmentSlot.FEET) {
                    f.set(state, ItemStack.EMPTY);
                }
            } catch (Exception ignored) {}
        }
    }
}

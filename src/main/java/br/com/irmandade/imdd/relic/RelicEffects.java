package br.com.irmandade.imdd.relic;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/** Propriedades das relíquias (fase 1): Fazendeiro, Sorte, Nether, Invasor, Defesa, Stomper. */
public final class RelicEffects {
    /** Invasor: tempo ligado e desligado em ticks (20 ticks = 1s). Mude aqui para ajustar. */
    private static final int INVASOR_ON_TICKS = 100;   // 5s com Bad Omen
    private static final int INVASOR_OFF_TICKS = 100;  // 5s sem efeito
    private static final Map<UUID, Integer> INVASOR_NEXT = new HashMap<>();

    private RelicEffects() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTickCount();
            if (tick % 10 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) tickPlayer(p, tick);
        });

        // Stomper: na mão principal, sem dano de queda
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer p
                    && source.is(DamageTypeTags.IS_FALL)
                    && RelicRegistry.is(p.getMainHandItem(), "stomper")) {
                return false;
            }
            return true;
        });

        // Defesa: espinhos 360° com a relíquia em qualquer mão
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> thorns(entity, source));
    }

    private static void tickPlayer(ServerPlayer p, int tick) {
        sustain(p, "fazendeiro", RelicRegistry.is(p.getItemBySlot(EquipmentSlot.LEGS), "fazendeiro"), MobEffects.HERO_OF_THE_VILLAGE, 4);
        sustain(p, "nether", RelicRegistry.is(p.getItemBySlot(EquipmentSlot.HEAD), "nether"), MobEffects.FIRE_RESISTANCE, 0);
        boolean luck = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (RelicRegistry.is(p.getInventory().getItem(i), "sorte")) { luck = true; break; }
        }
        sustain(p, "sorte", luck, MobEffects.LUCK, 9);
        // Invasor (calça equipada): Bad Omen 5s ligado / 10s desligado; não ativa com Raid Omen/Trial Omen
        if (RelicRegistry.is(p.getItemBySlot(EquipmentSlot.LEGS), "invasor")) {
            int next = INVASOR_NEXT.getOrDefault(p.getUUID(), 0);
            if (tick >= next && !p.hasEffect(MobEffects.RAID_OMEN) && !p.hasEffect(MobEffects.TRIAL_OMEN)) {
                p.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, INVASOR_ON_TICKS, 0, false, true, true));
                INVASOR_NEXT.put(p.getUUID(), tick + INVASOR_ON_TICKS + INVASOR_OFF_TICKS);
            }
        } else {
            INVASOR_NEXT.remove(p.getUUID());
        }
    }

    private static final Map<String, Set<UUID>> ACTIVE = new HashMap<>();

    /** Mantém o efeito enquanto a condição valer (duração longa => sem piscar, sem visual de beacon) e remove ao desequipar. */
    private static void sustain(ServerPlayer p, String key, boolean cond, Holder<MobEffect> effect, int amp) {
        Set<UUID> set = ACTIVE.computeIfAbsent(key, k -> new HashSet<>());
        if (cond) {
            p.addEffect(new MobEffectInstance(effect, 220, amp, false, false, true));
            set.add(p.getUUID());
        } else if (set.remove(p.getUUID())) {
            p.removeEffect(effect);
        }
    }

    private static void refresh(ServerPlayer p, Holder<MobEffect> effect, int amplifier) {
        p.addEffect(new MobEffectInstance(effect, 40, amplifier, true, false, true));
    }

    private static void thorns(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer p)) return;
        if (source.is(DamageTypes.THORNS)) return;
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == p) return;
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        if (!RelicRegistry.is(main, "defesa") && !RelicRegistry.is(off, "defesa")) return;
        if (p.getRandom().nextFloat() < 0.75f) { // Thorns V = 15% x 5
            attacker.hurtServer((ServerLevel) p.level(), p.damageSources().thorns(p), 1 + p.getRandom().nextInt(4));
        }
    }
}

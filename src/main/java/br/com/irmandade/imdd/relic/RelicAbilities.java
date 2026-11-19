package br.com.irmandade.imdd.relic;

import br.com.irmandade.imdd.expurgo.Expurgo;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Relíquias com habilidades ativas: Imortalidade, Espião, Vento, Warden, Dragão, Invisibilidade, Explorador. */
public final class RelicAbilities {
    /** ids de entidades cuja armadura deve ser escondida nos pacotes de equipamento (usado pelo mixin). */
    public static final Set<Integer> MASKED_IDS = Collections.synchronizedSet(new HashSet<>());

    private static final Map<UUID, Integer> INVIS_START = new HashMap<>();
    private static final Map<UUID, Integer> INVIS_END = new HashMap<>();
    private record Key(Identifier dim, long pos) {}
    private static final Map<Key, Integer> MAGMA = new HashMap<>();

    private RelicAbilities() {}

    public static void init() {
        UseItemCallback.EVENT.register((player, world, hand) -> onUseItem(player, world, hand));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            // Ender chest bloqueado durante o Dia do Expurgo
            if (!world.isClientSide() && Expurgo.isActive() && world.getBlockState(hit.getBlockPos()).is(Blocks.ENDER_CHEST)) {
                player.sendSystemMessage(Expurgo.enderChestMessage());
                return InteractionResult.FAIL;
            }
            // Dragão: não pode ser colocado; clicar em bloco também teleporta
            if (RelicRegistry.is(player.getItemInHand(hand), "dragao")) {
                if (!world.isClientSide()) dragonTeleport((ServerPlayer) player);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        // Imortalidade: totem infinito com cooldown de 10s (não afeta outros totens)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer p)) return true;
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
            ItemStack relic = RelicRegistry.is(p.getMainHandItem(), "imortalidade") ? p.getMainHandItem()
                    : RelicRegistry.is(p.getOffhandItem(), "imortalidade") ? p.getOffhandItem() : ItemStack.EMPTY;
            if (relic.isEmpty() || p.getCooldowns().isOnCooldown(relic)) return true; // em cooldown: morre
            p.setHealth(1.0f);
            p.removeAllEffects();
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
            p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
            p.getCooldowns().addCooldown(relic, 200);
            ((ServerLevel) p.level()).broadcastEntityEvent(p, (byte) 35);
            return false;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTickCount();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) invisTick(p, tick);
            if (tick % 4 == 0) lavaWalk(server, tick);
        });
    }

    // ---------------- uso com botão direito ----------------

    private static InteractionResult onUseItem(Player p, Level w, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        String id = RelicRegistry.idOf(s);
        if (id.isEmpty()) return InteractionResult.PASS;
        boolean mine = id.equals("espiao") || id.equals("vento") || id.equals("warden") || id.equals("dragao");
        if (!mine) return InteractionResult.PASS;
        if (!id.equals("dragao") && p.getCooldowns().isOnCooldown(s)) return InteractionResult.FAIL;
        if (w.isClientSide()) return InteractionResult.SUCCESS;

        ServerPlayer sp = (ServerPlayer) p;
        ServerLevel sw = (ServerLevel) w;
        if (id.equals("espiao")) {
            // Espião nível 2 por 8 min; cooldown 20 min
            sp.addEffect(new MobEffectInstance(SpyEffect.SPY, 8 * 60 * 20, 1, false, true, true));
            sp.getCooldowns().addCooldown(s, 20 * 60 * 20);
        } else if (id.equals("vento")) {
            Vec3 eye = sp.getEyePosition();
            for (float off : new float[]{-4f, 4f}) {
                WindCharge wc = new WindCharge(sp, sw, eye.x, eye.y, eye.z);
                wc.shootFromRotation(sp, sp.getXRot(), sp.getYRot() + off, 0f, 1.5f, 0f);
                sw.addFreshEntity(wc);
            }
            sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.5f, 1.0f);
            sp.getCooldowns().addCooldown(s, 6);
        } else if (id.equals("warden")) {
            sonicBoom(sp, sw);
            sp.getCooldowns().addCooldown(s, 200);
        } else {
            dragonTeleport(sp);
        }
        return InteractionResult.SUCCESS;
    }

    private static void sonicBoom(ServerPlayer p, ServerLevel w) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(15));
        for (int i = 1; i <= 15; i++) {
            Vec3 pt = eye.add(look.scale(i));
            w.sendParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 1.0f);
        AABB area = p.getBoundingBox().expandTowards(look.scale(15)).inflate(1.0);
        for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, area, x -> x != p && x.isAlive())) {
            if (e.getBoundingBox().inflate(0.6).clip(eye, end).isPresent()) {
                e.hurtServer(w, w.damageSources().sonicBoom(p), 10.0f);
                double res = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                e.push(look.x * 2.5 * res, look.y * 0.5 * res, look.z * 2.5 * res);
                if (e instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
            }
        }
    }

    /** Mesmo teleporte aleatório da chorus fruit (raio 8), sem cooldown. */
    private static void dragonTeleport(ServerPlayer p) {
        ServerLevel w = (ServerLevel) p.level();
        double ox = p.getX(), oy = p.getY(), oz = p.getZ();
        for (int i = 0; i < 16; i++) {
            double x = ox + (p.getRandom().nextDouble() - 0.5) * 16.0;
            double y = Mth.clamp(oy + (p.getRandom().nextInt(16) - 8), w.getMinY(), w.getMinY() + w.dimensionType().logicalHeight() - 1);
            double z = oz + (p.getRandom().nextDouble() - 0.5) * 16.0;
            if (p.isPassenger()) p.stopRiding();
            if (p.randomTeleport(x, y, z, true)) {
                w.playSound(null, ox, oy, oz, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
                break;
            }
        }
    }

    // ---------------- Invisibilidade (nível 11; a armadura é escondida no cliente, ver LivingEntityRendererMixin) ----------------

    private static void invisTick(ServerPlayer p, int tick) {
        UUID id = p.getUUID();
        boolean equipped = RelicRegistry.is(p.getItemBySlot(EquipmentSlot.FEET), "invisibilidade");
        Integer start = INVIS_START.get(id);
        if (equipped) {
            INVIS_END.remove(id);
            if (start == null) { INVIS_START.put(id, tick); return; }      // armadura some já ao equipar
            if (tick - start >= 10) {                                        // 10 ticks depois: o player some
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 220, 10, false, false, false));
            }
        } else if (start != null) {
            INVIS_START.remove(id);                                          // armadura aparece ao tirar a bota
            INVIS_END.put(id, tick + 10);                                    // 10 ticks depois: acaba o efeito
        }
        Integer end = INVIS_END.get(id);
        if (end != null && tick >= end) {
            INVIS_END.remove(id);
            p.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    // ---------------- Explorador: magma que quebra em estágios sobre a lava ----------------

    private static void lavaWalk(MinecraftServer server, int tick) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!RelicRegistry.is(p.getItemBySlot(EquipmentSlot.FEET), "explorador")) continue;
            ServerLevel w = (ServerLevel) p.level();
            BlockPos base = p.blockPosition().below();
            int r = 3;
            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-r, 0, -r), base.offset(r, 0, r))) {
                if (pos.distSqr(base) > r * r + 1) continue;
                BlockState st = w.getBlockState(pos);
                if (st.is(Blocks.LAVA) && st.getFluidState().isSource() && w.getBlockState(pos.above()).isAir()) {
                    BlockPos fixed = pos.immutable();
                    w.setBlockAndUpdate(fixed, ModBlocks.FROSTED_MAGMA.defaultBlockState());
                    w.scheduleTick(fixed, ModBlocks.FROSTED_MAGMA, 20 + p.getRandom().nextInt(21));
                }
            }
        }
    }
}

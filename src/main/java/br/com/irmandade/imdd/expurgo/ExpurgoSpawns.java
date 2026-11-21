package br.com.irmandade.imdd.expurgo;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Durante o Dia do Expurgo:
 *  - +40% de mobs hostis: cada hostil que aparece no overworld tem 40% de chance de gerar um clone extra;
 *  - fantasmas sempre nascem (sem precisar ficar sem dormir) perto de jogadores que veem o céu.
 */
public final class ExpurgoSpawns {
    private static final String TAG = "imdd_expurgo_seen";
    private static final float EXTRA_CHANCE = 0.40f;
    private static final int PHANTOM_INTERVAL = 100;   // ticks entre tentativas (5s)
    private static final int PHANTOM_MAX_NEAR = 4;     // máximo de fantasmas num raio de 64 blocos

    private record Pending(ServerLevel level, EntityType<?> type, BlockPos pos) {}

    private static final List<Pending> QUEUE = new ArrayList<>();
    private static boolean spawning = false;

    private ExpurgoSpawns() {}

    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register(ExpurgoSpawns::onLoad);
        ServerTickEvents.END_SERVER_TICK.register(ExpurgoSpawns::tick);
    }

    // ---------- +40% de hostis ----------

    private static void onLoad(Entity entity, ServerLevel level) {
        if (spawning || !Expurgo.isActive()) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (!(entity instanceof Mob mob)) return;
        if (mob.getType().getCategory() != MobCategory.MONSTER) return;
        if (mob.getType() == EntityTypes.PHANTOM) return;           // fantasmas têm sistema próprio
        if (!mob.addTag(TAG)) return;                               // addTag devolve false se já tinha a tag: já processado (evita duplicar ao recarregar chunk)

        if (mob.isPersistenceRequired() || mob.isPassenger() || mob.isVehicle()) return; // nomeado / jockey
        if (mob.getMaxHealth() >= 80f) return;                      // chefes (wither, warden, elder guardian...)

        if (level.getRandom().nextFloat() < EXTRA_CHANCE) {
            QUEUE.add(new Pending(level, mob.getType(), mob.blockPosition()));
        }
    }

    // ---------- tick: processa fila + fantasmas ----------

    private static void tick(MinecraftServer s) {
        if (!Expurgo.isActive()) { QUEUE.clear(); return; }

        if (!QUEUE.isEmpty()) {
            List<Pending> todo = new ArrayList<>(QUEUE);
            QUEUE.clear();
            spawning = true;
            try {
                for (Pending p : todo) {
                    Entity e = p.type().spawn(p.level(), p.pos(), EntitySpawnReason.NATURAL);
                    if (e != null) e.addTag(TAG);
                }
            } finally {
                spawning = false;
            }
        }

        if (s.getTickCount() % PHANTOM_INTERVAL == 0) {
            for (ServerPlayer p : s.getPlayerList().getPlayers()) spawnPhantoms(p);
        }
    }

    private static void spawnPhantoms(ServerPlayer p) {
        if (p.isSpectator() || p.isCreative()) return;
        if (!(p.level() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        BlockPos base = p.blockPosition();
        if (!level.canSeeSky(base)) return; // fantasmas precisam de céu aberto acima do jogador

        AABB box = new AABB(base).inflate(64);
        List<Entity> near = level.getEntities((Entity) null, box, e -> e.getType() == EntityTypes.PHANTOM);
        if (near.size() >= PHANTOM_MAX_NEAR) return;

        RandomSource r = level.getRandom();
        int count = 1 + r.nextInt(3); // 1 a 3 por tentativa
        spawning = true;
        try {
            for (int i = 0; i < count; i++) {
                for (int attempt = 0; attempt < 8; attempt++) {
                    BlockPos pos = base.offset(r.nextInt(21) - 10, 20 + r.nextInt(15), r.nextInt(21) - 10);
                    if (!level.getBlockState(pos).isAir()) continue;
                    Entity e = EntityTypes.PHANTOM.spawn(level, pos, EntitySpawnReason.NATURAL);
                    if (e != null) { e.addTag(TAG); break; }
                }
            }
        } finally {
            spawning = false;
        }
    }
}

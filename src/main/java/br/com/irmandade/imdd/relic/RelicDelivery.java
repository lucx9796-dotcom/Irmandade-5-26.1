package br.com.irmandade.imdd.relic;

import br.com.irmandade.imdd.Imdd;
import br.com.irmandade.imdd.expurgo.Expurgo;
import br.com.irmandade.imdd.tab.TabText;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/**
 * Entrega de relíquias:
 *  - jogador novo no mapa recebe 1 relíquia aleatória, ainda não entregue, no baú do fim;
 *  - cada relíquia só é entregue uma vez (registro em config/imdd/relics_delivery.json);
 *  - Dragão, machado do Bizonho, Stomper e Warden NÃO entram no sorteio (têm outra forma de conquista);
 *  - Dragão: jogar o ovo de dragão no void do End dá a relíquia ao jogador que jogou.
 */
public final class RelicDelivery {
    /** Relíquias que não são entregues a jogadores novos. */
    public static final Set<String> EXCLUDED = Set.of("dragao", "bizonho_machado", "stomper", "warden");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, String> DELIVERED = new LinkedHashMap<>(); // id da relíquia -> uuid do dono
    private static final Set<String> KNOWN = new HashSet<>();                    // jogadores já avaliados
    private static final Set<String> RESET = new HashSet<>();                    // jogadores liberados por admin para novo sorteio
    private static final Map<UUID, UUID> EGG_OWNER = new HashMap<>();            // ovo (item) -> quem jogou
    private static final Random RANDOM = new Random();
    private static long lastModified = 0;
    private static final Predicate<CommandSourceStack> OP = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    private RelicDelivery() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> load());
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        ServerEntityEvents.ENTITY_LOAD.register(RelicDelivery::onEntityLoad);
        ServerTickEvents.END_SERVER_TICK.register(RelicDelivery::tick);
    }

    // ---------- jogador novo ----------

    /** Jogadores com OP não recebem relíquia no baú do fim. */
    private static boolean isOp(ServerPlayer p) {
        try { return OP.test(p.createCommandSourceStack()); } catch (Exception e) { return false; }
    }

    private static void onJoin(ServerPlayer p) {
        if (isOp(p)) return;
        String uuid = p.getUUID().toString();
        if (KNOWN.contains(uuid)) return;
        KNOWN.add(uuid);

        // Quem já tinha tempo de jogo antes do mod não é "novo no mapa"
        // (exceto se um admin resetou o jogador: aí ele entra no sorteio de novo)
        int played = p.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
        if (played > 0 && !RESET.remove(uuid)) { save(); return; }

        giveRandom(p);
    }

    /** Sorteia uma relíquia livre e coloca no baú do fim do jogador (OPs não entram). */
    private static void giveRandom(ServerPlayer p) {
        String uuid = p.getUUID().toString();
        String id = pickFree();
        if (id == null) {
            Imdd.LOGGER.info("Sem relíquias livres para entregar a {}", p.getName().getString());
            save();
            return;
        }
        ItemStack relic = RelicRegistry.copyOf(id);
        ItemStack rest = p.getEnderChestInventory().addItem(relic);
        if (!rest.isEmpty()) {            // baú cheio (não deveria acontecer com jogador novo)
            p.getInventory().add(rest);
            if (!rest.isEmpty()) p.drop(rest, false);
        }
        DELIVERED.put(id, uuid);
        save();
        p.sendSystemMessage(TabText.parse("&dIMDD &7» &fVocê recebeu uma relíquia no seu baú do fim!"));
    }

    private static String pickFree() {
        List<String> free = new ArrayList<>();
        for (String id : RelicRegistry.ids()) {
            if (EXCLUDED.contains(id) || DELIVERED.containsKey(id)) continue;
            if (RelicRegistry.copyOf(id).isEmpty()) continue;
            free.add(id);
        }
        return free.isEmpty() ? null : free.get(RANDOM.nextInt(free.size()));
    }

    // ---------- Dragão: ovo jogado no void do End ----------

    private static void onEntityLoad(Entity entity, ServerLevel world) {
        if (world.dimension() != Level.END) return;
        if (!(entity instanceof ItemEntity item) || !item.getItem().is(Items.DRAGON_EGG)) return;
        if (EGG_OWNER.containsKey(item.getUUID())) return;
        Player near = world.getNearestPlayer(entity, 6.0);
        EGG_OWNER.put(item.getUUID(), near == null ? null : near.getUUID());
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 == 0) syncFromFile(server);
        if (EGG_OWNER.isEmpty() || server.getTickCount() % 5 != 0) return;
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) return;
        Iterator<Map.Entry<UUID, UUID>> it = EGG_OWNER.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> e = it.next();
            Entity ent = end.getEntity(e.getKey());
            if (!(ent instanceof ItemEntity egg) || egg.isRemoved()) { it.remove(); continue; }
            if (egg.getY() >= end.getMinY() - 8) continue;    // ainda não caiu no void

            ServerPlayer p = e.getValue() == null ? null : server.getPlayerList().getPlayer(e.getValue());
            if (p == null) {
                Player near = end.getNearestPlayer(egg.getX(), 0, egg.getZ(), 256, false);
                if (near instanceof ServerPlayer sp) p = sp;
            }
            it.remove();
            egg.discard();
            if (p != null) grantDragon(p);
        }
    }

    private static void grantDragon(ServerPlayer p) {
        if (DELIVERED.containsKey("dragao")) {
            p.sendSystemMessage(TabText.parse("&dIMDD &7» &cA Relíquia do Dragão já foi conquistada."));
            return;
        }
        ItemStack relic = RelicRegistry.copyOf("dragao");
        if (relic.isEmpty()) return;
        p.getInventory().add(relic);
        if (!relic.isEmpty()) p.drop(relic, false);
        DELIVERED.put("dragao", p.getUUID().toString());
        save();
        p.sendSystemMessage(TabText.parse("&dIMDD &7» &fVocê conquistou uma relíquia!"));
    }

    // ---------- administração (/relics entregues, /relics liberar <id>) ----------

    public static Map<String, String> delivered() { return DELIVERED; }

    /** Libera uma relíquia (ex.: perdida no void) para poder ser entregue/conquistada de novo. */
    public static boolean release(String id) {
        boolean had = DELIVERED.remove(id) != null;
        if (had) save();
        return had;
    }

    /**
     * Relê o arquivo (admin editou à mão). Quem era dono de uma relíquia e sumiu do arquivo
     * é tratado como resetado e sorteado de novo (na hora se estiver online).
     */
    private static void syncFromFile(MinecraftServer server) {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            long mod = Files.getLastModifiedTime(f).toMillis();
            if (mod == lastModified) return;
            lastModified = mod;

            JsonObject o = GSON.fromJson(Files.readString(f), JsonObject.class);
            Map<String, String> fileDelivered = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("delivered").entrySet()) fileDelivered.put(e.getKey(), e.getValue().getAsString());
            Set<String> fileKnown = new HashSet<>();
            for (JsonElement e : o.getAsJsonArray("known")) fileKnown.add(e.getAsString());

            // donos que estavam na memória e não estão mais no arquivo
            Set<String> before = new HashSet<>(DELIVERED.values());
            Set<String> after = new HashSet<>(fileDelivered.values());
            before.removeAll(after);

            DELIVERED.clear(); DELIVERED.putAll(fileDelivered);
            KNOWN.clear(); KNOWN.addAll(fileKnown);

            for (String u : before) {
                KNOWN.remove(u);
                RESET.add(u);
                ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(u));
                if (online != null) onJoin(online);
            }
            if (!before.isEmpty()) save();
            lastModified = Files.getLastModifiedTime(f).toMillis();
        } catch (Exception e) {
            Imdd.LOGGER.error("Falha ao sincronizar relics_delivery.json", e);
        }
    }

    // ---------- persistência ----------

    private static Path file() { return Expurgo.dir().resolve("relics_delivery.json"); }

    private static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            lastModified = Files.getLastModifiedTime(f).toMillis();
            JsonObject o = GSON.fromJson(Files.readString(f), JsonObject.class);
            DELIVERED.clear();
            KNOWN.clear();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("delivered").entrySet()) DELIVERED.put(e.getKey(), e.getValue().getAsString());
            for (JsonElement e : o.getAsJsonArray("known")) KNOWN.add(e.getAsString());
        } catch (Exception e) {
            Imdd.LOGGER.error("Falha ao ler relics_delivery.json", e);
        }
    }

    private static void save() {
        try {
            JsonObject o = new JsonObject(), d = new JsonObject();
            JsonArray k = new JsonArray();
            DELIVERED.forEach(d::addProperty);
            KNOWN.forEach(k::add);
            o.add("delivered", d);
            o.add("known", k);
            Files.writeString(file(), GSON.toJson(o));
            lastModified = Files.getLastModifiedTime(file()).toMillis();
        } catch (Exception e) {
            Imdd.LOGGER.error("Falha ao salvar relics_delivery.json", e);
        }
    }
}

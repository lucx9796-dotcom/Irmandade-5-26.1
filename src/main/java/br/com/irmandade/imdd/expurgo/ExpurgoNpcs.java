package br.com.irmandade.imdd.expurgo;

import br.com.irmandade.imdd.Imdd;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * NPC do Dia do Expurgo: quando um jogador sai durante o Expurgo, um boneco com a skin dele, a armadura, os itens
 * de mão e o nick visível acima da cabeça fica no lugar, guardando o inventário. Se o NPC morrer, os itens caem no chão; se o jogador voltar antes, recebe tudo de volta.
 */
public final class ExpurgoNpcs {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, Rec> RECS = new HashMap<>();
    private static final Set<UUID> TO_REMOVE = new HashSet<>();

    private static final class Rec {
        UUID npc; String name; String dim; double x, y, z;
        Map<Integer, JsonElement> items = new LinkedHashMap<>();
    }

    private ExpurgoNpcs() {}

    // ---------- eventos ----------

    public static void onDisconnect(ServerPlayer p, MinecraftServer server) {
        if (!Expurgo.isActive() || server.isStopped() || p.isDeadOrDying() || p.isSpectator()) return;
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        String equipment = equipmentSnbt(p, server); // antes de esvaziar o inventário
        Rec rec = new Rec();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            try {
                rec.items.put(i, ItemStack.CODEC.encodeStart(ops, s).getOrThrow());
                inv.setItem(i, ItemStack.EMPTY);
            } catch (Exception e) { Imdd.LOGGER.error("Falha ao guardar item do NPC", e); }
        }
        ServerLevel w = (ServerLevel) p.level();
        rec.npc = UUID.randomUUID();
        rec.name = p.getName().getString();
        rec.dim = w.dimension().identifier().toString();
        rec.x = p.getX(); rec.y = p.getY(); rec.z = p.getZ();
        String nick = rec.name.replace("\\", "\\\\").replace("\"", "\\\"");
        Expurgo.run(server, String.format(Locale.ROOT,
                "execute in %s run summon minecraft:mannequin %.3f %.3f %.3f {UUID:%s,profile:\"%s\",CustomName:\"%s\",CustomNameVisible:1b,"
                        + "hide_description:1b,immovable:1b,Invulnerable:0b,Silent:1b,Tags:[\"imdd_npc\"]%s}",
                rec.dim, rec.x, rec.y, rec.z, uuidInts(rec.npc), nick, nick, equipment));
        RECS.put(p.getUUID(), rec);
        save();
    }

    /** Monta ",equipment:{head:{...},...}" com a armadura e os itens de mão do jogador (só para exibição no NPC). */
    private static String equipmentSnbt(ServerPlayer p, MinecraftServer server) {
        RegistryOps<Tag> nbt = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        StringBuilder sb = new StringBuilder();
        append(sb, nbt, "head", p.getItemBySlot(EquipmentSlot.HEAD));
        append(sb, nbt, "chest", p.getItemBySlot(EquipmentSlot.CHEST));
        append(sb, nbt, "legs", p.getItemBySlot(EquipmentSlot.LEGS));
        append(sb, nbt, "feet", p.getItemBySlot(EquipmentSlot.FEET));
        append(sb, nbt, "mainhand", p.getMainHandItem());
        append(sb, nbt, "offhand", p.getOffhandItem());
        return sb.length() == 0 ? "" : ",equipment:{" + sb + "}";
    }

    private static void append(StringBuilder sb, RegistryOps<Tag> nbt, String slot, ItemStack s) {
        if (s.isEmpty()) return;
        try {
            String snbt = ItemStack.CODEC.encodeStart(nbt, s).getOrThrow().toString();
            if (sb.length() > 0) sb.append(',');
            sb.append(slot).append(':').append(snbt);
        } catch (Exception e) { Imdd.LOGGER.error("Falha ao montar equipamento do NPC", e); }
    }

    public static void onJoin(ServerPlayer p, MinecraftServer server) {
        Rec rec = RECS.remove(p.getUUID());
        if (rec == null) return;
        removeEntity(server, rec.npc);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        for (Map.Entry<Integer, JsonElement> e : rec.items.entrySet()) {
            ItemStack s = ItemStack.CODEC.parse(ops, e.getValue()).resultOrPartial(err -> Imdd.LOGGER.error("Item inválido: {}", err)).orElse(ItemStack.EMPTY);
            if (s.isEmpty()) continue;
            if (p.getInventory().getItem(e.getKey()).isEmpty()) p.getInventory().setItem(e.getKey(), s);
            else { p.getInventory().add(s); if (!s.isEmpty()) p.drop(s, false); }
        }
        save();
    }

    public static void onDeath(LivingEntity entity, DamageSource source) {
        Iterator<Map.Entry<UUID, Rec>> it = RECS.entrySet().iterator();
        while (it.hasNext()) {
            Rec rec = it.next().getValue();
            if (!rec.npc.equals(entity.getUUID())) continue;
            ServerLevel w = (ServerLevel) entity.level();
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, w.getServer().registryAccess());
            for (JsonElement je : rec.items.values()) {
                ItemStack s = ItemStack.CODEC.parse(ops, je).resultOrPartial(err -> {}).orElse(ItemStack.EMPTY);
                if (!s.isEmpty()) Containers.dropItemStack(w, entity.getX(), entity.getY(), entity.getZ(), s);
            }
            w.getServer().getPlayerList().broadcastSystemMessage(Component.literal("☠ O NPC de " + rec.name + " foi morto e dropou tudo!")
                    .withStyle(st -> st.withColor(0xb30000)), false);
            it.remove();
            save();
            return;
        }
    }

    public static void onEntityLoad(Entity entity, ServerLevel world) {
        if (TO_REMOVE.remove(entity.getUUID())) {
            world.getServer().execute(entity::discard);
            save();
        }
    }

    /** Fim do Expurgo: remove os NPCs (os itens ficam guardados para o dono ao entrar). */
    public static void discardAll(MinecraftServer server) {
        for (Rec rec : RECS.values()) removeEntity(server, rec.npc);
        save();
    }

    private static void removeEntity(MinecraftServer server, UUID npc) {
        for (ServerLevel w : server.getAllLevels()) {
            Entity e = w.getEntity(npc);
            if (e != null) { e.discard(); return; }
        }
        TO_REMOVE.add(npc); // chunk descarregado: remove quando carregar
    }

    private static String uuidInts(UUID u) {
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return "[I;" + (int) (m >> 32) + "," + (int) m + "," + (int) (l >> 32) + "," + (int) l + "]";
    }

    // ---------- persistência (config/imdd/npcs.json) ----------

    public static void load() {
        try {
            Path f = Expurgo.dir().resolve("npcs.json");
            if (!Files.exists(f)) return;
            JsonObject root = GSON.fromJson(Files.readString(f), JsonObject.class);
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("npcs").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Rec r = new Rec();
                r.npc = UUID.fromString(o.get("npc").getAsString());
                r.name = o.get("name").getAsString();
                r.dim = o.get("dim").getAsString();
                r.x = o.get("x").getAsDouble(); r.y = o.get("y").getAsDouble(); r.z = o.get("z").getAsDouble();
                for (Map.Entry<String, JsonElement> it : o.getAsJsonObject("items").entrySet()) r.items.put(Integer.parseInt(it.getKey()), it.getValue());
                RECS.put(UUID.fromString(e.getKey()), r);
            }
            for (JsonElement u : root.getAsJsonArray("remove")) TO_REMOVE.add(UUID.fromString(u.getAsString()));
        } catch (Exception e) { Imdd.LOGGER.error("Falha ao ler npcs.json", e); }
    }

    private static void save() {
        try {
            JsonObject root = new JsonObject(), npcs = new JsonObject();
            for (Map.Entry<UUID, Rec> e : RECS.entrySet()) {
                Rec r = e.getValue();
                JsonObject o = new JsonObject(), items = new JsonObject();
                o.addProperty("npc", r.npc.toString()); o.addProperty("name", r.name); o.addProperty("dim", r.dim);
                o.addProperty("x", r.x); o.addProperty("y", r.y); o.addProperty("z", r.z);
                for (Map.Entry<Integer, JsonElement> it : r.items.entrySet()) items.add(String.valueOf(it.getKey()), it.getValue());
                o.add("items", items);
                npcs.add(e.getKey().toString(), o);
            }
            root.add("npcs", npcs);
            JsonArray rem = new JsonArray();
            for (UUID u : TO_REMOVE) rem.add(u.toString());
            root.add("remove", rem);
            Files.writeString(Expurgo.dir().resolve("npcs.json"), GSON.toJson(root));
        } catch (Exception e) { Imdd.LOGGER.error("Falha ao salvar npcs.json", e); }
    }
}

package br.com.irmandade.imdd.expurgo;

import br.com.irmandade.imdd.Imdd;
import br.com.irmandade.imdd.net.ExpurgoPayload;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dia do Expurgo: queimar uma nether star ativa 24h (cada nova estrela soma +24h). */
public final class Expurgo {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final Gson GSON = new Gson();
    private static long endMillis = 0;
    private static MinecraftServer server;

    private Expurgo() {}

    public static boolean isActive() { return endMillis > System.currentTimeMillis(); }

    public static long remainingMillis() { return Math.max(0, endMillis - System.currentTimeMillis()); }

    public static Path dir() {
        Path d = FabricLoader.getInstance().getConfigDir().resolve("imdd");
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return d;
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; load(); ExpurgoNpcs.load(); br.com.irmandade.imdd.tab.TabConfig.load(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
        ServerTickEvents.END_SERVER_TICK.register(Expurgo::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            ServerPlayer p = handler.getPlayer();
            ExpurgoNpcs.onJoin(p, s);
            sync(p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> ExpurgoNpcs.onDisconnect(handler.getPlayer(), s));
        ServerLivingEntityEvents.AFTER_DEATH.register(ExpurgoNpcs::onDeath);
        ServerEntityEvents.ENTITY_LOAD.register(ExpurgoNpcs::onEntityLoad);
        ExpurgoSpawns.init();
    }

    // ---------- ativação ----------

    public static void onNetherStarBurned(ServerLevel world, Vec3 pos, int count) {
        MinecraftServer s = world.getServer();
        long now = System.currentTimeMillis();
        boolean was = isActive();
        endMillis = (was ? endMillis : now) + DAY * Math.max(1, count);
        save();

        if (!was) run(s, "execute in minecraft:overworld run time set 18000"); // meia-noite de lua cheia
        showTitle(s, was ? "O Tempo foi Extendido!" : "24 Horas Sem Regras ativado!");

        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(world, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setPos(pos.x, pos.y, pos.z);
            bolt.setVisualOnly(true);
            world.addFreshEntity(bolt);
        }
        for (ServerPlayer p : s.getPlayerList().getPlayers()) sync(p);
    }

    public static void end(MinecraftServer s) {
        endMillis = 0;
        save();
        ExpurgoNpcs.discardAll(s);
        run(s, "execute in minecraft:overworld run time set 0");
        s.getPlayerList().broadcastSystemMessage(Component.literal("☠ O Dia do Expurgo terminou.").withStyle(st -> st.withColor(0xb30000)), false);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) sync(p);
    }

    private static void tick(MinecraftServer s) {
        int t = s.getTickCount();
        if (endMillis != 0 && !isActive()) end(s);
        if (isActive() && t % 100 == 0) run(s, "execute in minecraft:overworld run time set 18000"); // mantém a noite de lua cheia
        if (t % 20 == 0) for (ServerPlayer p : s.getPlayerList().getPlayers()) TabList.update(s, p);
    }

    // ---------- mensagens ----------

    private static void showTitle(MinecraftServer s, String subtitle) {
        MutableComponent title = Component.empty()
                .append(Component.literal("☠").withStyle(st -> st.withColor(0xb30000)))
                .append(Component.literal(" "))
                .append(Component.literal("Dia do Expurgo").withStyle(st -> st.withColor(0xb30000).withBold(true)))
                .append(Component.literal(" "))
                .append(Component.literal("☠").withStyle(st -> st.withColor(0xb30000)));
        Component sub = Component.literal(subtitle).withStyle(st -> st.withColor(0x757575));
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    public static Component enderChestMessage() {
        return Component.empty()
                .append(Component.literal("IMDD").withStyle(st -> st.withColor(0xFF55FF)))
                .append(Component.literal(" "))
                .append(Component.literal("»").withStyle(st -> st.withColor(0xa096b2)))
                .append(Component.literal(" "))
                .append(Component.literal("Você não pode abrir o baú do fim durante o").withStyle(st -> st.withColor(0xdc5663)))
                .append(Component.literal(" "))
                .append(Component.literal("Dia do Expurgo").withStyle(st -> st.withColor(0x9f0206).withBold(true)));
    }

    // ---------- util ----------

    public static void sync(ServerPlayer p) {
        if (ServerPlayNetworking.canSend(p, ExpurgoPayload.TYPE)) ServerPlayNetworking.send(p, new ExpurgoPayload(remainingMillis()));
    }

    static void run(MinecraftServer s, String command) {
        try {
            s.getCommands().getDispatcher().execute(command, s.createCommandSourceStack().withSuppressedOutput());
        } catch (Exception e) {
            Imdd.LOGGER.error("Falha ao executar comando interno '{}': {}", command, e.getMessage());
        }
    }

    private static void load() {
        try {
            Path f = dir().resolve("expurgo.json");
            if (Files.exists(f)) endMillis = GSON.fromJson(Files.readString(f), JsonObject.class).get("endMillis").getAsLong();
        } catch (Exception e) { Imdd.LOGGER.error("Falha ao ler expurgo.json", e); }
    }

    private static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("endMillis", endMillis);
            Files.writeString(dir().resolve("expurgo.json"), GSON.toJson(o));
        } catch (Exception e) { Imdd.LOGGER.error("Falha ao salvar expurgo.json", e); }
    }
}

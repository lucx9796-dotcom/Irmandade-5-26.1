package br.com.irmandade.imdd.client;

import br.com.irmandade.imdd.Imdd;
import br.com.irmandade.imdd.net.ExpurgoPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Cliente: marca d'água no F3 (direita, embaixo). */
public class ImddClient implements ClientModInitializer {
    private static final String LINE1 = "Irmandade By LucxMC";
    private static final String LINE2 = "-. .. -. --. ..- . -- / . ... - .- / ... . --. ..- .-. --- /";
    private static volatile long endMs = 0;

    /** true enquanto o Dia do Expurgo está ativo (o servidor envia o tempo restante). */
    public static boolean expurgoActive() {
        return endMs > System.currentTimeMillis();
    }

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ExpurgoPayload.TYPE, (payload, context) ->
                endMs = payload.remainingMillis() > 0 ? System.currentTimeMillis() + payload.remainingMillis() : 0);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> endMs = 0);
        TranslucentBlocks.register();
        VanishClient.init();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("imdd", "overlay"), ImddClient::render);
    }

    private static void render(GuiGraphicsExtractor ctx, DeltaTracker tick) {
        Minecraft mc = Minecraft.getInstance();
        int w = ctx.guiWidth(), h = ctx.guiHeight();

        if (overlayVisible(mc)) {
            int y = Math.min(Math.max(h / 2 + 25, 372), h - 30);   // direita, logo abaixo das linhas do F3 (que terminam por volta de y=360)
            drawLine(ctx, mc, LINE1, w, y, 0xFFFF55FF);       // Light Purple
            drawLine(ctx, mc, LINE2, w, y + 9, 0xFFFF5555);   // Light Red
        }
    }

    private static void drawLine(GuiGraphicsExtractor ctx, Minecraft mc, String text, int screenW, int y, int color) {
        int width = mc.font.width(text);
        int x = screenW - 2 - width;
        ctx.fill(x - 1, y - 1, x + width + 1, y + 8, 0x90505050);
        ctx.text(mc.font, text, x, y, color, false);
    }

    // ---------- só mostra com a tela F3 de verdade ----------

    private static boolean f3Resolved;
    private static Field f3ListField;
    private static Method f3Method;

    /**
     * O overlay de debug também fica visível quando só uma opção de debug está ligada (F3+B = hitboxes, F3+G = bordas de chunk...),
     * mesmo sem a tela F3. Por isso confirmo com a lista de entradas do F3 ("isF3...") por reflexão. A partir do 26.1 o jogo não é
     * mais ofuscado, então basta procurar pelos nomes oficiais. Se não achar, cai no comportamento antigo (mostra sempre que o overlay aparece).
     */
    private static boolean overlayVisible(Minecraft mc) {
        if (!mc.gui.hud.getDebugOverlay().showDebugScreen()) return false;
        Boolean f3 = f3Enabled(mc);
        return f3 == null || f3;
    }

    private static Boolean f3Enabled(Minecraft mc) {
        try {
            if (!f3Resolved) {
                f3Resolved = true;
                for (Field f : Minecraft.class.getDeclaredFields()) {
                    if (!f.getType().getSimpleName().equals("DebugScreenEntryList")) continue;
                    for (Method m : f.getType().getDeclaredMethods()) {
                        if (m.getParameterCount() == 0 && m.getReturnType() == boolean.class
                                && (m.getName().equals("isF3Visible") || m.getName().equals("isF3Enabled"))) {
                            f.setAccessible(true);
                            m.setAccessible(true);
                            f3ListField = f;
                            f3Method = m;
                            break;
                        }
                    }
                    if (f3Method != null) break;
                }
            }
            if (f3ListField == null || f3Method == null) return null;
            Object list = f3ListField.get(mc);
            return list == null ? null : (Boolean) f3Method.invoke(list);
        } catch (Throwable t) {
            f3ListField = null;
            f3Method = null;
            Imdd.LOGGER.warn("Marca d'água do F3: não consegui checar a tela F3 ({})", t.toString());
            return null;
        }
    }
}

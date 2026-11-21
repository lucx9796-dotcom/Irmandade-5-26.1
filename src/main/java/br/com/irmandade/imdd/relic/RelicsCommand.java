package br.com.irmandade.imdd.relic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class RelicsCommand {
    private RelicsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("relics")
                // Só operadores (nível 2). Para liberar a todos, remova a linha abaixo.
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    RelicsMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("entregues").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    for (String id : RelicRegistry.ids()) {
                        String owner = RelicDelivery.delivered().get(id);
                        String status;
                        if (owner != null) {
                            ServerPlayer online = src.getServer().getPlayerList().getPlayer(UUID.fromString(owner));
                            status = "entregue a " + (online != null ? online.getName().getString() : owner);
                        } else if (RelicDelivery.EXCLUDED.contains(id)) {
                            status = "conquista especial (não é entregue)";
                        } else {
                            status = "livre";
                        }
                        String line = id + ": " + status;
                        src.sendSuccess(() -> Component.literal(line), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("liberar")
                        .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            if (RelicDelivery.release(id)) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Relíquia '" + id + "' liberada."), true);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal("A relíquia '" + id + "' não estava registrada como entregue."));
                            return 0;
                        }))));
    }
}
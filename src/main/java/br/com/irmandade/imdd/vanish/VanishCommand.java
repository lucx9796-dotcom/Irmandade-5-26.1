package br.com.irmandade.imdd.vanish;

import br.com.irmandade.imdd.tab.TabText;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/** /desaparecer [jogador]  |  /aparecer <jogador>  (só operadores) */
public final class VanishCommand {
    private VanishCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("desaparecer")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    if (VanishState.vanished().isEmpty()) {
                        src.sendSuccess(() -> TabText.parse("&dIMDD &7» &fNinguém está desaparecido."), false);
                        return 1;
                    }
                    src.sendSuccess(() -> TabText.parse("&dIMDD &7» &fDesaparecidos:"), false);
                    for (Map.Entry<UUID, String> e : VanishState.vanished().entrySet()) {
                        String name = e.getValue();
                        src.sendSuccess(() -> TabText.parse("&7- &b" + name), false);
                    }
                    return 1;
                })
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
                    VanishState.vanish(ctx.getSource().getServer(), target);
                    String name = target.getName().getString();
                    ctx.getSource().sendSuccess(() -> TabText.parse("&dIMDD &7» &b" + name + " &fagora está &bdesaparecido&f."), false);
                    return 1;
                })));

        dispatcher.register(Commands.literal("aparecer")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
                    VanishState.reveal(ctx.getSource().getServer(), target);
                    String name = target.getName().getString();
                    ctx.getSource().sendSuccess(() -> TabText.parse("&dIMDD &7» &b" + name + " &fvoltou a &aaparecer&f."), false);
                    return 1;
                })));
    }
}

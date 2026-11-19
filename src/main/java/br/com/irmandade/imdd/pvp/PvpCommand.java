package br.com.irmandade.imdd.pvp;

import br.com.irmandade.imdd.tab.TabText;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /pvp  |  /pvp on  |  /pvp off  |  /pvp off <tempo>  (ex.: 15m, 1h, 1h30m5s) */
public final class PvpCommand {
    private PvpCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pvp")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    String msg;
                    if (PvpState.isEnabled()) msg = "&dIMDD &7» &fPvP está &aligado&f.";
                    else if (PvpState.hasTimer()) msg = "&dIMDD &7» &fPvP está &cdesligado &f(volta em &b" + PvpState.human(PvpState.remainingMillis()) + "&f).";
                    else msg = "&dIMDD &7» &fPvP está &cdesligado&f.";
                    ctx.getSource().sendSuccess(() -> TabText.parse(msg), false);
                    return 1;
                })
                .then(Commands.literal("on").executes(ctx -> {
                    if (PvpState.isEnabled()) {
                        ctx.getSource().sendFailure(Component.literal("O PvP já está ligado."));
                        return 0;
                    }
                    PvpState.on(ctx.getSource().getServer());
                    return 1;
                }))
                .then(Commands.literal("off")
                        .executes(ctx -> {
                            PvpState.off(ctx.getSource().getServer(), 0);
                            return 1;
                        })
                        .then(Commands.argument("tempo", StringArgumentType.word()).executes(ctx -> {
                            long ms = PvpState.parseDuration(StringArgumentType.getString(ctx, "tempo"));
                            if (ms <= 0) {
                                ctx.getSource().sendFailure(Component.literal("Tempo inválido. Use h, m e s, por exemplo: 15m, 1h ou 1h30m5s."));
                                return 0;
                            }
                            PvpState.off(ctx.getSource().getServer(), ms);
                            return 1;
                        }))));
    }
}

package br.com.irmandade.imdd.expurgo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Command;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /expurgo parar  (ou /expurgo encerrar): termina o Dia do Expurgo na hora. */
public final class ExpurgoCommand {
    private ExpurgoCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        Command<CommandSourceStack> stop = ctx -> {
            CommandSourceStack src = ctx.getSource();
            if (!Expurgo.isActive()) {
                src.sendFailure(Component.literal("O Dia do Expurgo não está ativo."));
                return 0;
            }
            Expurgo.end(src.getServer());
            src.sendSuccess(() -> Component.literal("Dia do Expurgo encerrado."), true);
            return 1;
        };
        dispatcher.register(Commands.literal("expurgo")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("parar").executes(stop))
                .then(Commands.literal("encerrar").executes(stop)));
    }
}

package br.com.irmandade.imdd.mixin;

import br.com.irmandade.imdd.vanish.VanishState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Seletores (/tell nome, @a ...): quem não é op não encontra jogadores desaparecidos.
 * Alvos: EntitySelector.findPlayers (jogadores) e EntitySelector.findEntities (entidades).
 */
@Pseudo
@Mixin(targets = "net.minecraft.commands.arguments.selector.EntitySelector")
public abstract class EntitySelectorMixin {

    @Inject(method = "findPlayers", at = @At("RETURN"), cancellable = true, require = 0)
    private void imdd$jogadores(CommandSourceStack source, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        cir.setReturnValue(VanishState.filterPlayers(source, cir.getReturnValue()));
    }

    @Inject(method = "findEntities", at = @At("RETURN"), cancellable = true, require = 0)
    private void imdd$entidades(CommandSourceStack source, CallbackInfoReturnable<List<? extends Entity>> cir) {
        cir.setReturnValue(VanishState.filterPlayers(source, cir.getReturnValue()));
    }
}

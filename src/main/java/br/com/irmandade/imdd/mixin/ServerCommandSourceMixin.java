package br.com.irmandade.imdd.mixin;

import br.com.irmandade.imdd.vanish.VanishState;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Sugestões de nomes de jogadores (CommandSourceStack.getOnlinePlayerNames): esconde os desaparecidos de quem não é op. */
@Pseudo
@Mixin(targets = "net.minecraft.commands.CommandSourceStack")
public abstract class ServerCommandSourceMixin {

    @Inject(method = "getOnlinePlayerNames", at = @At("RETURN"), cancellable = true, require = 0)
    private void imdd$esconderNomes(CallbackInfoReturnable<Collection<String>> cir) {
        cir.setReturnValue(VanishState.filterNames((CommandSourceStack) (Object) this, cir.getReturnValue()));
    }
}

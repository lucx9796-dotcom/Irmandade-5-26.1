package br.com.irmandade.imdd.mixin.client;

import br.com.irmandade.imdd.client.ArmorHider;
import br.com.irmandade.imdd.relic.RelicRegistry;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Relíquia da Invisibilidade: esconde a armadura do jogador (só elytra, cabeças de mob/player e abóbora continuam). */
@Mixin(AvatarRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void imdd$esconderArmadura(Avatar player, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (RelicRegistry.is(player.getItemBySlot(EquipmentSlot.FEET), "invisibilidade")) {
            ArmorHider.hide(state);
        }
    }
}

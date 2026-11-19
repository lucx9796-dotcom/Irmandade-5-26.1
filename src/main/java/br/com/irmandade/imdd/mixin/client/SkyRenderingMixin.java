package br.com.irmandade.imdd.mixin.client;

import br.com.irmandade.imdd.client.ImddClient;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lua do Dia do Expurgo: 3x maior (antes 10x, reduzido em 70%) e vermelha. Para mudar o tamanho, troque o 3.0F abaixo.
 * Alvo: SkyRenderer, método que desenha a lua (renderMoon). No 1.21.11 era class_9975.method_62304.
 * Esse método faz translate(0,100,0) e scale(20,1,20); mudar só X/Z cresce a lua sem afastá-la.
 * ATENÇÃO (26.1): o nome do método foi assumido (renderMoon). Com require = 0 o mixin é ignorado em silêncio
 * se o nome/estrutura mudou — nesse caso a lua só não fica grande/vermelha. Ver "ATENÇÃO" no README do port.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.SkyRenderer")
public abstract class SkyRenderingMixin {

    @ModifyConstant(method = "renderMoon", constant = @Constant(floatValue = 20.0F), require = 0)
    private float imdd$luaGigante(float original) {
        return ImddClient.expurgoActive() ? original * 3.0F : original;
    }

    /** O "color modulator" da lua é (1,1,1,alpha): no Expurgo vira vermelho sangue. */
    @Redirect(method = "renderMoon", at = @At(value = "NEW", target = "org/joml/Vector4f"), require = 0)
    private Vector4f imdd$luaVermelha(float r, float g, float b, float a) {
        return ImddClient.expurgoActive() ? new Vector4f(1.0F, 0.1F, 0.08F, a) : new Vector4f(r, g, b, a);
    }
}

package br.com.irmandade.imdd.relic;

import br.com.irmandade.imdd.Imdd;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Efeito "Espião": -40% de escala por nível (nível 2 => escala 0.2). Textura: textures/mob_effect/spy.png */
public class SpyEffect extends MobEffect {
    public static Holder<MobEffect> SPY;

    public SpyEffect() {
        super(MobEffectCategory.NEUTRAL, 0xE09451);
        addAttributeModifier(Attributes.SCALE, Identifier.fromNamespaceAndPath(Imdd.MOD_ID, "effect.spy"),
                -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    public static void register() {
        SPY = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Identifier.fromNamespaceAndPath(Imdd.MOD_ID, "spy"), new SpyEffect());
    }
}

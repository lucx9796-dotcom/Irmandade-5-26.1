package br.com.irmandade.imdd.relic;

import br.com.irmandade.imdd.Imdd;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class ModRecipes {
    public static RecipeSerializer<BizonhoAxeRecipe> BIZONHO_AXE;

    private ModRecipes() {}

    public static void register() {
        // 26.1: RecipeSerializer virou um record (MapCodec + StreamCodec)
        BIZONHO_AXE = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(Imdd.MOD_ID, "bizonho_axe"),
                new RecipeSerializer<>(MapCodec.unit(BizonhoAxeRecipe.INSTANCE), StreamCodec.unit(BizonhoAxeRecipe.INSTANCE)));
    }
}

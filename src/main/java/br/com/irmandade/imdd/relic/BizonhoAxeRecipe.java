package br.com.irmandade.imdd.relic;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Machado do Bizonho: receita do machado de diamante (3 diamantes + 2 gravetos), mas um dos gravetos
 * é o graveto da Relíquia do Bizonho e o outro é um graveto normal.
 */
public class BizonhoAxeRecipe extends CustomRecipe {
    public static final BizonhoAxeRecipe INSTANCE = new BizonhoAxeRecipe();

    public BizonhoAxeRecipe() {
        super();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return match(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return match(input) ? RelicRegistry.copyOf("bizonho_machado") : ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipes.BIZONHO_AXE;
    }

    private static boolean match(CraftingInput in) {
        if (in.width() != 2 || in.height() != 3) return false;
        ItemStack[] s = new ItemStack[6];
        for (int i = 0; i < 6; i++) s[i] = in.getItem(i);
        // Formato do machado:  D D / D S / _ S   ou espelhado:  D D / S D / S _
        boolean normal = diamonds(s[0], s[1], s[2]) && s[4].isEmpty() && sticks(s[3], s[5]);
        boolean mirrored = diamonds(s[0], s[1], s[3]) && s[5].isEmpty() && sticks(s[2], s[4]);
        return normal || mirrored;
    }

    private static boolean diamonds(ItemStack a, ItemStack b, ItemStack c) {
        return a.is(Items.DIAMOND) && b.is(Items.DIAMOND) && c.is(Items.DIAMOND);
    }

    /** Um graveto da relíquia do Bizonho + um graveto normal. */
    private static boolean sticks(ItemStack a, ItemStack b) {
        if (!a.is(Items.STICK) || !b.is(Items.STICK)) return false;
        boolean aRelic = RelicRegistry.is(a, "bizonho"), bRelic = RelicRegistry.is(b, "bizonho");
        boolean aPlain = !RelicRegistry.isRelic(a), bPlain = !RelicRegistry.isRelic(b);
        return (aRelic && bPlain) || (bRelic && aPlain);
    }
}

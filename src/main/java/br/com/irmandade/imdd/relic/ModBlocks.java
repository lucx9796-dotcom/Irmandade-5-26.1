package br.com.irmandade.imdd.relic;

import br.com.irmandade.imdd.Imdd;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static Block FROSTED_MAGMA;

    private ModBlocks() {}

    public static void register() {
        Identifier id = Identifier.fromNamespaceAndPath(Imdd.MOD_ID, "frosted_magma");
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        FROSTED_MAGMA = Registry.register(BuiltInRegistries.BLOCK, id,
                new FrostedMagmaBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.MAGMA_BLOCK).setId(key).noOcclusion()));
    }
}

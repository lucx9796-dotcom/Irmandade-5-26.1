package br.com.irmandade.imdd.relic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Magma que "quebra" em 4 estágios (como o gelo do Frost Walker) e volta a ser lava. */
public class FrostedMagmaBlock extends MagmaBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    public FrostedMagmaBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AGE);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Igual ao frosted ice: a cada 20-40 ticks há 1/3 de chance de avançar um estágio
        if (random.nextInt(3) == 0) {
            int age = state.getValue(AGE);
            if (age < 3) {
                level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
            } else {
                level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
                return;
            }
        }
        level.scheduleTick(pos, this, 20 + random.nextInt(21));
    }

    /** Não desenha a face entre dois blocos iguais (igual ao gelo/vidro). */
    protected boolean skipRendering(BlockState state, BlockState neighbor, Direction direction) {
        return neighbor.is(this) || super.skipRendering(state, neighbor, direction);
    }
}

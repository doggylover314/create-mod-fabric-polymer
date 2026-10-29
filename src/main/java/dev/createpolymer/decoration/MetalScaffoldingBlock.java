package dev.createpolymer.decoration;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.util.ShapeUtil;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Metal scaffolding. Players see it on Polymer's scaffolding states, which carry Create's cube model (one for a
 * scaffold standing on something, one for the bottom piece); where Polymer has none left the vanilla scaffolding is shown.
 */
public class MetalScaffoldingBlock extends ScaffoldingBlock implements IWrenchable, PolymerTexturedBlock {

	public static final VoxelShape SCAFFOLD_HALF = ShapeUtil.box(0, 8, 0, 16, 16, 16);
	public static final VoxelShape SCAFFOLD_FULL = Shapes.or(SCAFFOLD_HALF, ShapeUtil.box(0, 0, 0, 2, 16, 2),
		ShapeUtil.box(0, 0, 14, 2, 16, 16), ShapeUtil.box(14, 0, 0, 16, 16, 2), ShapeUtil.box(14, 0, 14, 16, 16, 16));

	// [bottom][waterlogged]
	private final BlockState[][] clientStates = new BlockState[2][2];

	/** {@code model} is the name of Create's model for a standing scaffold, with "_horizontal" for the bottom piece. */
	public MetalScaffoldingBlock(Properties pProperties, String model) {
		super(pProperties);
		for (int bottom = 0; bottom < 2; bottom++)
			for (int waterlogged = 0; waterlogged < 2; waterlogged++)
				clientStates[bottom][waterlogged] = DecorationStates.request(
					BlockModelType.getScaffolding(bottom == 1, waterlogged == 1),
					DecorationStates.model(model + (bottom == 1 ? "_horizontal" : "")));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		BlockState client = clientStates[state.getValue(BOTTOM) ? 1 : 0][state.getValue(WATERLOGGED) ? 1 : 0];
		return client != null ? client : Blocks.SCAFFOLDING.withPropertiesOf(state);
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.SCAFFOLDING.defaultBlockState();
	}

	/**
	 * Polymer asks for the shape of every state while the block is registered, which is before its item exists. The
	 * shape asks for the item, so vanilla's cached answer would stay "air" for good: look it up every time.
	 */
	@Override
	public Item asItem() {
		return Item.byBlock(this);
	}

	@Override
	protected void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRand) {}

	@Override
	protected boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		return true;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		if (pState.getValue(BOTTOM))
			return SCAFFOLD_HALF;
		return super.getCollisionShape(pState, pLevel, pPos, pContext);
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		if (pState.getValue(BOTTOM))
			return SCAFFOLD_HALF;
		if (!pContext.isHoldingItem(pState.getBlock()
			.asItem()))
			return SCAFFOLD_FULL;
		return Shapes.block();
	}

	@Override
	protected VoxelShape getInteractionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
		return Shapes.block();
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader pLevel, ScheduledTickAccess ticks, BlockPos pCurrentPos,
		Direction pFacing, BlockPos pFacingPos, BlockState pFacingState, RandomSource random) {
		super.updateShape(pState, pLevel, ticks, pCurrentPos, pFacing, pFacingPos, pFacingState, random);
		BlockState stateBelow = pLevel.getBlockState(pCurrentPos.below());
		return pFacing == Direction.DOWN ? pState.setValue(BOTTOM,
			!stateBelow.is(this) && !stateBelow.isFaceSturdy(pLevel, pCurrentPos.below(), Direction.UP)) : pState;
	}

}

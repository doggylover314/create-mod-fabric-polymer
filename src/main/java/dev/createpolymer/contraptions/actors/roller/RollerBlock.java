package dev.createpolymer.contraptions.actors.roller;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.actors.AttachedActorBlock;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's RollerBlock (MIT, The Create Team). The pole placement helper, which extends a row of
 * rollers, needs the client's placement preview and is not ported.
 */
public class RollerBlock extends AttachedActorBlock implements IBE<RollerBlockEntity> {

	public RollerBlock(Properties p_i48377_1_) {
		super(p_i48377_1_);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withWater(defaultBlockState().setValue(FACING, context.getHorizontalDirection()
			.getOpposite()), context);
	}

	@Override
	public Class<RollerBlockEntity> getBlockEntityClass() {
		return RollerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends RollerBlockEntity> getBlockEntityType() {
		return ActorsRegistry.MECHANICAL_ROLLER_BLOCK_ENTITY;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return Shapes.block();
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
		return true;
	}

	@Override
	public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
		super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
		withBlockEntityDo(pLevel, pPos, RollerBlockEntity::searchForSharedValues);
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		return List.of(new Piece(ModelItems.block("mechanical_roller/assembled"), Orientation.forHorizontal(state.getValue(FACING))));
	}

	/** The casing, frame and wheel as one model (the wheel stands still: it only turns with a moving contraption) */
	public static class Model extends BaseModel {
		private final Part part;

		public Model(BlockState state) {
			super(state);
			part = addPart(ModelItems.block("mechanical_roller/assembled"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			part.orientation(Orientation.forHorizontal(state.getValue(FACING))).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}

}

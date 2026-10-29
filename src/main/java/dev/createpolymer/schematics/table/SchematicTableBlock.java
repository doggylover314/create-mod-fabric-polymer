package dev.createpolymer.schematics.table;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.registry.feature.SchematicsRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SchematicTableBlock extends HorizontalDirectionalBlock implements IBE<SchematicTableBlockEntity>, PolymerModelBlock {

	/** Create's AllShapes.TABLE_POLE_SHAPE */
	public static final VoxelShape TABLE_POLE_SHAPE = Shapes.or(ShapeUtil.box(4, 0, 4, 12, 2, 12),
		ShapeUtil.box(5, 2, 5, 11, 14, 11));
	/** Create's AllShapes.SCHEMATICS_TABLE */
	public static final VoxelShaper SHAPE = VoxelShaper.forHorizontal(
		Shapes.or(ShapeUtil.box(4, 0, 4, 12, 12, 12), ShapeUtil.box(0, 11, 2, 16, 14, 14)), Direction.SOUTH);

	public SchematicTableBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState()
			.setValue(FACING, context.getHorizontalDirection()
				.getOpposite());
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return TABLE_POLE_SHAPE;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPE.get(state.getValue(FACING));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.SUCCESS;
		withBlockEntityDo(level, pos, be -> be.openGui(serverPlayer));
		return InteractionResult.SUCCESS;
	}

	@Override
	public Class<SchematicTableBlockEntity> getBlockEntityClass() {
		return SchematicTableBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SchematicTableBlockEntity> getBlockEntityType() {
		return SchematicsRegistry.SCHEMATIC_TABLE_BLOCK_ENTITY;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.SPRUCE_PLANKS.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final Part table;

		public Model(BlockState state) {
			super(state);
			table = addPart(dev.createpolymer.visual.ModelItems.block("schematic_table"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(table, "schematic_table", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {}
	}
}

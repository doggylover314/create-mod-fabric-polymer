package dev.createpolymer.contraptions.actors.psi;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.piston.WrenchableDirectionalBlock;
import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's PortableStorageInterfaceBlock (MIT, The Create Team). */
public class PortableStorageInterfaceBlock extends WrenchableDirectionalBlock
	implements IBE<PortableStorageInterfaceBlockEntity>, PolymerModelBlock, ContraptionDisplay {

	/** Create's AllShapes.PORTABLE_STORAGE_INTERFACE */
	public static final Map<Direction, VoxelShape> SHAPES =
		ShapeUtil.forDirectional(ShapeUtil.box(0, 0, 0, 16, 14, 16));

	boolean fluids;

	public static PortableStorageInterfaceBlock forItems(Properties p_i48415_1_) {
		return new PortableStorageInterfaceBlock(p_i48415_1_, false);
	}

	public static PortableStorageInterfaceBlock forFluids(Properties p_i48415_1_) {
		return new PortableStorageInterfaceBlock(p_i48415_1_, true);
	}

	private PortableStorageInterfaceBlock(Properties p_i48415_1_, boolean fluids) {
		super(p_i48415_1_);
		this.fluids = fluids;
	}

	public boolean isFluids() {
		return fluids;
	}

	@Override
	protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block p_220069_4_,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean p_220069_6_) {
		withBlockEntityDo(world, pos, PortableStorageInterfaceBlockEntity::neighbourChanged);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction direction = context.getNearestLookingDirection();
		if (context.getPlayer() != null && context.getPlayer()
			.isShiftKeyDown())
			direction = direction.getOpposite();
		return defaultBlockState().setValue(FACING, direction.getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level worldIn, BlockPos pos, Direction direction) {
		return getBlockEntityOptional(worldIn, pos).map(be -> be.isConnected() ? 15 : 0)
			.orElse(0);
	}

	@Override
	public Class<PortableStorageInterfaceBlockEntity> getBlockEntityClass() {
		return PortableStorageInterfaceBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PortableStorageInterfaceBlockEntity> getBlockEntityType() {
		return fluids ? ActorsRegistry.PORTABLE_FLUID_INTERFACE_BLOCK_ENTITY
			: ActorsRegistry.PORTABLE_STORAGE_INTERFACE_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, fluids);
	}

	private String modelFolder() {
		return fluids ? "portable_fluid_interface" : "portable_storage_interface";
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		// the extension rests: the middle piece sits 6 pixels up (baked into the model), the head on top of it
		return List.of(new Piece(ModelItems.block(modelFolder() + "/assembled"),
			Orientation.forFacing(state.getValue(FACING))));
	}

	/** The base, the extending middle piece and the head. They glow while a contraption is connected. */
	public static class Model extends BaseModel {
		private final String folder;
		private final Part base;
		private final Part middle;
		private final Part top;
		private boolean lit;
		private float shownProgress = -1;

		public Model(BlockState state, boolean fluids) {
			super(state);
			folder = fluids ? "portable_fluid_interface" : "portable_storage_interface";
			base = addPart(ModelItems.block(folder + "/block"));
			middle = addPart(ModelItems.block(folder + "/block_middle"));
			top = addPart(ModelItems.block(folder + "/block_top"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(FACING);
			base.orientation(Orientation.forFacing(facing)).apply(0);
			middle.orientation(Orientation.forFacing(facing));
			top.orientation(Orientation.forFacing(facing));
			shownProgress = -1;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof PortableStorageInterfaceBlockEntity psi))
				return;
			boolean connected = psi.isConnected();
			float progress = psi.getExtensionDistance(1);
			if (connected == lit && progress == shownProgress)
				return;
			if (connected != lit) {
				lit = connected;
				middle.setItem(ModelItems.block(folder + (lit ? "/block_middle_powered" : "/block_middle")));
			}
			shownProgress = progress;
			Direction facing = state.getValue(FACING);
			float middleOffset = progress * 0.5f + 0.375f;
			middle.translation(facing.getStepX() * middleOffset, facing.getStepY() * middleOffset,
				facing.getStepZ() * middleOffset);
			top.translation(facing.getStepX() * progress, facing.getStepY() * progress, facing.getStepZ() * progress);
			middle.apply(1);
			top.apply(1);
		}
	}

}

package dev.createpolymer.contraptions.piston;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MechanicalPistonBlock extends DirectionalAxisKineticBlock implements IBE<MechanicalPistonBlockEntity>, IWrenchable, ContraptionDisplay {

	public static final EnumProperty<PistonState> STATE = EnumProperty.create("state", PistonState.class);

	/** Create's AllShapes.CASING_12PX, MECHANICAL_PISTON_EXTENDED, MECHANICAL_PISTON_HEAD and FOUR_VOXEL_POLE */
	public static final Map<Direction, VoxelShape> CASING_12PX = ShapeUtil.forDirectional(ShapeUtil.box(0, 0, 0, 16, 12, 16));
	public static final Map<Axis, VoxelShape> FOUR_VOXEL_POLE = ShapeUtil.forAxis(ShapeUtil.box(6, 0, 6, 10, 16, 10));
	public static final Map<Direction, VoxelShape> PISTON_HEAD_SHAPE = ShapeUtil.forDirectional(Blocks.PISTON_HEAD.defaultBlockState()
		.setValue(PistonHeadBlock.FACING, Direction.UP)
		.setValue(PistonHeadBlock.SHORT, true)
		.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
	public static final Map<Direction, VoxelShape> EXTENDED_SHAPE = ShapeUtil.forDirectional(Shapes.or(
		ShapeUtil.box(0, 0, 0, 16, 12, 16), ShapeUtil.box(6, 0, 6, 10, 16, 10)));

	protected boolean isSticky;

	public static MechanicalPistonBlock normal(Properties properties) {
		return new MechanicalPistonBlock(properties, false);
	}

	public static MechanicalPistonBlock sticky(Properties properties) {
		return new MechanicalPistonBlock(properties, true);
	}

	protected MechanicalPistonBlock(Properties properties, boolean sticky) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
			.setValue(STATE, PistonState.RETRACTED));
		isSticky = sticky;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(STATE);
		super.createBlockStateDefinition(builder);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (!player.mayBuild())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (player.isShiftKeyDown())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!stack.is(ConventionalItemTags.SLIME_BALLS) && !stack.is(net.minecraft.world.item.Items.SLIME_BALL)) {
			if (stack.isEmpty()) {
				withBlockEntityDo(level, pos, be -> be.assembleNextTick = true);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (state.getValue(STATE) != PistonState.RETRACTED)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		Direction direction = state.getValue(FACING);
		if (hitResult.getDirection() != direction)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (((MechanicalPistonBlock) state.getBlock()).isSticky)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level instanceof ServerLevel serverLevel) {
			net.minecraft.world.phys.Vec3 vec = hitResult.getLocation();
			serverLevel.sendParticles(ParticleTypes.ITEM_SLIME, vec.x, vec.y, vec.z, 3, 0.1, 0.1, 0.1, 0);
		}
		level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, .5f, 1);
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		if (!player.isCreative())
			stack.shrink(1);
		level.setBlockAndUpdate(pos, ContraptionRegistry.STICKY_MECHANICAL_PISTON.defaultBlockState()
			.setValue(FACING, direction)
			.setValue(AXIS_ALONG_FIRST_COORDINATE, state.getValue(AXIS_ALONG_FIRST_COORDINATE)));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		// Create only reacts to the block behind the piston; the orientation does not tell which neighbour changed,
		// so any change schedules the (cheap) tick that clears the assembly error
		if (!level.isClientSide() && !level.getBlockTicks()
			.willTickThisTick(pos, this))
			level.scheduleTick(pos, this, 1);
	}

	@Override
	protected void tick(BlockState state, ServerLevel worldIn, BlockPos pos, RandomSource r) {
		Direction direction = state.getValue(FACING);
		BlockState pole = worldIn.getBlockState(pos.relative(direction.getOpposite()));
		if (!isExtensionPole(pole))
			return;
		if (pole.getValue(PistonExtensionPoleBlock.FACING)
			.getAxis() != direction.getAxis())
			return;
		withBlockEntityDo(worldIn, pos, be -> {
			if (be.lastException == null)
				return;
			be.lastException = null;
			be.sendData();
		});
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (state.getValue(STATE) != PistonState.RETRACTED)
			return InteractionResult.PASS;
		return null;
	}

	public enum PistonState implements StringRepresentable {
		RETRACTED, MOVING, EXTENDED;

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}
	}

	@Override
	public BlockState playerWillDestroy(Level worldIn, BlockPos pos, BlockState state, @Nullable Player player) {
		Direction direction = state.getValue(FACING);
		BlockPos pistonHead = null;
		BlockPos pistonBase = pos;
		boolean dropBlocks = player == null || !player.isCreative();

		Integer maxPoles = maxAllowedPistonPoles();
		for (int offset = 1; offset < maxPoles; offset++) {
			BlockPos currentPos = pos.relative(direction, offset);
			BlockState block = worldIn.getBlockState(currentPos);

			if (isExtensionPole(block) && direction.getAxis() == block.getValue(BlockStateProperties.FACING)
				.getAxis())
				continue;

			if (isPistonHead(block) && block.getValue(BlockStateProperties.FACING) == direction) {
				pistonHead = currentPos;
			}

			break;
		}

		if (pistonHead != null && pistonBase != null) {
			BlockPos.betweenClosedStream(pistonBase, pistonHead)
				.filter(p -> !p.equals(pos))
				.forEach(p -> worldIn.destroyBlock(p, dropBlocks));
		}

		for (int offset = 1; offset < maxPoles; offset++) {
			BlockPos currentPos = pos.relative(direction.getOpposite(), offset);
			BlockState block = worldIn.getBlockState(currentPos);

			if (isExtensionPole(block) && direction.getAxis() == block.getValue(BlockStateProperties.FACING)
				.getAxis()) {
				worldIn.destroyBlock(currentPos, dropBlocks);
				continue;
			}

			break;
		}

		return player == null ? state : super.playerWillDestroy(worldIn, pos, state, player);
	}

	public static int maxAllowedPistonPoles() {
		return ContraptionRegistry.maxPistonPoles;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {

		if (state.getValue(STATE) == PistonState.EXTENDED)
			return EXTENDED_SHAPE.get(state.getValue(FACING));

		if (state.getValue(STATE) == PistonState.MOVING)
			return CASING_12PX.get(state.getValue(FACING));

		return Shapes.block();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	public Class<MechanicalPistonBlockEntity> getBlockEntityClass() {
		return MechanicalPistonBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends MechanicalPistonBlockEntity> getBlockEntityType() {
		return ContraptionRegistry.MECHANICAL_PISTON_BLOCK_ENTITY;
	}

	public static boolean isPiston(BlockState state) {
		return state.getBlock() instanceof MechanicalPistonBlock;
	}

	public static boolean isStickyPiston(BlockState state) {
		return state.getBlock() instanceof MechanicalPistonBlock piston && piston.isSticky;
	}

	public static boolean isExtensionPole(BlockState state) {
		return state.getBlock() instanceof PistonExtensionPoleBlock;
	}

	public static boolean isPistonHead(BlockState state) {
		return state.getBlock() instanceof MechanicalPistonHeadBlock;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.OAK_PLANKS.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, isSticky);
	}

	@Override
	public java.util.List<Piece> getContraptionPieces(BlockState state) {
		java.util.List<Piece> pieces = new java.util.ArrayList<>();
		var variant = dev.createpolymer.visual.BlockstateModels.get(isSticky ? "sticky_mechanical_piston" : "mechanical_piston", state);
		if (variant != null)
			pieces.add(new Piece(variant.stack(), variant.rotation()));
		pieces.add(new Piece(ModelItems.block("shaft"), Orientation.forAxis(getRotationAxis(state))));
		return pieces;
	}

	public static class Model extends BaseModel {
		private final Part body;
		private final Part shaft;
		private final String stateFile;

		public Model(BlockState state, boolean sticky) {
			super(state);
			stateFile = sticky ? "sticky_mechanical_piston" : "mechanical_piston";
			body = addPart(ModelItems.block("mechanical_piston/normal/block"));
			shaft = addPart(ModelItems.block("shaft"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(body, stateFile, state);
			Axis axis = ((IRotate) state.getBlock()).getRotationAxis(state);
			shaft.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof MechanicalPistonBlockEntity piston))
				return;
			Axis axis = ((IRotate) state.getBlock()).getRotationAxis(state);
			shaft.spin(time, piston.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
		}
	}
}

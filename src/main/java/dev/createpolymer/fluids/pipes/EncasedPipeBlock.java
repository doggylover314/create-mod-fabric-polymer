package dev.createpolymer.fluids.pipes;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.DOWN;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.UP;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.schematic.requirement.SpecialBlockItemRequirement;
import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.fluids.FluidTransportBehaviour;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.feature.PipesRegistry;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.ticks.TickPriority;

/**
 * Create's EncasedPipeBlock. The casing is Create's copper casing; the block is looked up by name because that
 * block belongs to another feature.
 */
public class EncasedPipeBlock extends Block implements SpecialBlockItemRequirement, IWrenchable, IBE<FluidPipeBlockEntity>, PolymerModelBlock {
	public static final Map<Direction, BooleanProperty> FACING_TO_PROPERTY_MAP = PipeBlock.PROPERTY_BY_DIRECTION;

	private final Supplier<@Nullable Block> casing;

	public EncasedPipeBlock(Properties properties, Supplier<@Nullable Block> casing) {
		super(properties);
		this.casing = casing;
		registerDefaultState(defaultBlockState().setValue(NORTH, false)
			.setValue(SOUTH, false)
			.setValue(DOWN, false)
			.setValue(UP, false)
			.setValue(WEST, false)
			.setValue(EAST, false));
	}

	/** Create's copper casing, once that block exists. */
	public static @Nullable Block copperCasing() {
		return BuiltInRegistries.BLOCK.getOptional(CreatePolymer.id("copper_casing"))
			.orElse(null);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
		super.createBlockStateDefinition(builder);
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		if (!world.isClientSide() && state != oldState)
			world.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		Direction changed = FluidPropagator.validateNeighbourChange(state, direction, neighbourState);
		if (changed != null && state.getValue(FACING_TO_PROPERTY_MAP.get(changed)))
			ticks.scheduleTick(pos, this, 1, TickPriority.HIGH);
		return state;
	}

	@Override
	protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource r) {
		FluidPropagator.propagateChangedPipe(world, pos, state);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();

		if (world.isClientSide())
			return InteractionResult.SUCCESS;

		context.getLevel()
			.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, context.getClickedPos(), Block.getId(state));
		BlockState equivalentPipe = transferSixWayProperties(state, PipesRegistry.FLUID_PIPE.defaultBlockState());

		Direction firstFound = Direction.UP;
		for (Direction d : Iterate.directions)
			if (state.getValue(FACING_TO_PROPERTY_MAP.get(d))) {
				firstFound = d;
				break;
			}

		FluidTransportBehaviour.cacheFlows(world, pos);
		world.setBlockAndUpdate(pos, PipesRegistry.FLUID_PIPE
			.updateBlockState(equivalentPipe, firstFound, null, world, pos));
		FluidTransportBehaviour.loadFlows(world, pos);
		return InteractionResult.SUCCESS;
	}

	public static BlockState transferSixWayProperties(BlockState from, BlockState to) {
		for (Direction d : Iterate.directions) {
			BooleanProperty property = FACING_TO_PROPERTY_MAP.get(d);
			to = to.setValue(property, from.getValue(property));
		}
		return to;
	}

	@Override
	public Class<FluidPipeBlockEntity> getBlockEntityClass() {
		return FluidPipeBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FluidPipeBlockEntity> getBlockEntityType() {
		return PipesRegistry.ENCASED_FLUID_PIPE_BLOCK_ENTITY;
	}

	public @Nullable Block getCasing() {
		return casing.get();
	}

	public boolean isCasing(ItemStack stack) {
		Block block = getCasing();
		return block != null && !stack.isEmpty() && stack.getItem() == block.asItem();
	}

	/** Create's EncasedBlock#handleEncasing, used when the casing is applied to a plain pipe. */
	public void handleEncasing(BlockState state, Level level, BlockPos pos) {
		FluidTransportBehaviour.cacheFlows(level, pos);
		level.setBlockAndUpdate(pos, EncasedPipeBlock.transferSixWayProperties(state, defaultBlockState()));
		FluidTransportBehaviour.loadFlows(level, pos);
	}

	/** Create's EncasableBlock#playEncaseSound */
	public static void playEncaseSound(Level level, BlockPos pos) {
		BlockState newState = level.getBlockState(pos);
		SoundType soundType = newState.getSoundType();
		level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F,
			soundType.getPitch() * 0.8F);
	}

	@Override
	protected BlockState rotate(BlockState pState, Rotation pRotation) {
		return FluidPipeBlockRotation.rotate(pState, pRotation);
	}

	@Override
	protected BlockState mirror(BlockState pState, Mirror pMirror) {
		return FluidPipeBlockRotation.mirror(pState, pMirror);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(PipesRegistry.FLUID_PIPE_ITEM);
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * Create's encased_fluid_pipe blockstate: every side is either the open model (the pipe ring) or the flat
	 * casing, rotated to the side. The drain rims of PipeAttachmentModel are added on top.
	 */
	public static class Model extends BaseModel {
		private final Map<Direction, Part> sides = new EnumMap<>(Direction.class);
		private final PipeAttachments attachments;
		private boolean dirty = true;

		public Model(BlockState state) {
			super(state);
			for (Direction d : Iterate.directions)
				sides.put(d, addPart(ItemStack.EMPTY));
			attachments = new PipeAttachments(this);
			onStateChanged(state);
		}

		/** Blockstate rotations of the encased pipe: {x, y} for DOWN, UP, NORTH, SOUTH, WEST, EAST. */
		private static int[] rotation(Direction d) {
			return switch (d) {
				case DOWN -> new int[] { -90, 0 };
				case UP -> new int[] { 90, 0 };
				case NORTH -> new int[] { 0, 180 };
				case SOUTH -> new int[] { 0, 0 };
				case WEST -> new int[] { 0, 90 };
				case EAST -> new int[] { 0, 270 };
			};
		}

		@Override
		protected void onStateChanged(BlockState state) {
			for (Direction d : Iterate.directions) {
				if (!(state.getBlock() instanceof EncasedPipeBlock))
					continue;
				boolean open = state.getValue(FACING_TO_PROPERTY_MAP.get(d));
				int[] rot = rotation(d);
				Part part = sides.get(d);
				part.setItem(ModelItems.block("encased_fluid_pipe/" + (open ? "block_open" : "block_flat")));
				part.orientation(Orientation.variant(rot[0], rot[1]))
					.apply(0);
			}
			dirty = true;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!dirty && time % 5 != 0)
				return;
			dirty = false;
			attachments.update(level, pos, state, BlockEntityBehaviour.get(be, FluidTransportBehaviour.TYPE));
		}
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity be) {
		return ItemRequirement.of(PipesRegistry.FLUID_PIPE.defaultBlockState(), be);
	}
}

package dev.createpolymer.kinetics.belt;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.fluid.FluidItemHandlers;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.belt.BeltBlockEntity.CasingType;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import dev.createpolymer.kinetics.belt.transport.BeltMovementHandler.TransportedEntityInfo;
import dev.createpolymer.kinetics.belt.transport.BeltTunnelInteractionHandler;
import dev.createpolymer.logistics.funnel.FunnelBlock;
import dev.createpolymer.logistics.tunnel.BeltTunnelBlock;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.BeltRegistry;
import dev.createpolymer.registry.feature.CrushingRegistry;
import dev.createpolymer.util.Iterate;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BeltBlock extends HorizontalKineticBlock
	implements IBE<BeltBlockEntity>, IWrenchable, SimpleWaterloggedBlock, PolymerTexturedBlock {

	public static final Property<BeltSlope> SLOPE = EnumProperty.create("slope", BeltSlope.class);
	public static final Property<BeltPart> PART = EnumProperty.create("part", BeltPart.class);
	public static final BooleanProperty CASING = BooleanProperty.create("casing");
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	public BeltBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(SLOPE, BeltSlope.HORIZONTAL)
			.setValue(PART, BeltPart.START)
			.setValue(CASING, false)
			.setValue(WATERLOGGED, false));
	}

	@Override
	protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
		return super.areStatesKineticallyEquivalent(oldState, newState)
			&& oldState.getValue(PART) == newState.getValue(PART);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		if (face.getAxis() != getRotationAxis(state))
			return false;
		return getBlockEntityOptional(world, pos).map(BeltBlockEntity::hasPulley)
			.orElse(false);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		if (state.getValue(SLOPE) == BeltSlope.SIDEWAYS)
			return Axis.Y;
		return state.getValue(HORIZONTAL_FACING)
			.getClockWise()
			.getAxis();
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(BeltRegistry.BELT_CONNECTOR);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		List<ItemStack> drops = new ArrayList<>(super.getDrops(state, builder));
		BlockEntity blockEntity = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
		if (blockEntity instanceof BeltBlockEntity && ((BeltBlockEntity) blockEntity).hasPulley())
			drops.addAll(ModBlocks.SHAFT.defaultBlockState()
				.getDrops(builder));
		return drops;
	}

	@Override
	protected void spawnAfterBreak(BlockState state, ServerLevel worldIn, BlockPos pos, ItemStack tool, boolean dropExperience) {
		BeltBlockEntity controllerBE = BeltHelper.getControllerBE(worldIn, pos);
		if (controllerBE != null)
			controllerBE.getInventory()
				.ejectAll();
	}

	@Override
	protected void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn,
								InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (!canTransportObjects(state))
			return;
		if (entityIn instanceof Player player) {
			if (player.isShiftKeyDown())
				return;
			if (player.getAbilities().flying)
				return;
		}

		BeltBlockEntity belt = BeltHelper.getSegmentBE(worldIn, pos);
		if (belt == null)
			return;
		ItemStack asItem = ItemHelper.fromItemEntity(entityIn);
		if (!asItem.isEmpty()) {
			if (worldIn.isClientSide())
				return;
			if (entityIn.getDeltaMovement().y > 0)
				return;
			if (BeltTunnelInteractionHandler.getTunnelOnPosition(worldIn, pos) != null)
				return;
			withBlockEntityDo(worldIn, pos, be -> {
				IItemHandler handler = ItemHandlerLookup.get(worldIn, pos, null);
				if (handler == null)
					return;
				ItemStack remainder = handler.insertItem(0, asItem, false);
				if (remainder.isEmpty())
					entityIn.discard();
				else if (entityIn instanceof ItemEntity itemEntity && remainder.getCount() != itemEntity.getItem().getCount())
					itemEntity.setItem(remainder);
			});
			return;
		}

		BeltBlockEntity controller = BeltHelper.getControllerBE(worldIn, pos);
		if (controller == null || controller.passengers == null)
			return;
		if (controller.passengers.containsKey(entityIn)) {
			TransportedEntityInfo info = controller.passengers.get(entityIn);
			if (info.getTicksSinceLastCollision() != 0 || pos.equals(entityIn.blockPosition()))
				info.refresh(pos, state);
		} else {
			controller.passengers.put(entityIn, new TransportedEntityInfo(pos, state));
			entityIn.setOnGround(true);
		}
	}

	public static boolean canTransportObjects(BlockState state) {
		if (!(state.getBlock() instanceof BeltBlock))
			return false;
		BeltSlope slope = state.getValue(SLOPE);
		return slope != BeltSlope.VERTICAL && slope != BeltSlope.SIDEWAYS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hitResult) {
		if (player.isShiftKeyDown() || !player.mayBuild())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		boolean isWrench = stack.is(ModItems.WRENCH);
		boolean isConnector = stack.is(BeltRegistry.BELT_CONNECTOR);
		boolean isShaft = stack.is(ModItems.SHAFT);
		boolean isDye = stack.is(ItemTags.DYES);
		boolean hasWater = holdsWater(stack);
		boolean isHand = stack.isEmpty() && hand == InteractionHand.MAIN_HAND;

		if (isDye || hasWater)
			return onBlockEntityUseItemOn(level, pos,
				be -> be.applyColor(stack.get(DataComponents.DYE)) ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND);

		if (isConnector)
			return BeltSlicer.useConnector(state, level, pos, player, hand, hitResult);
		if (isWrench)
			return BeltSlicer.useWrench(state, level, pos, player, hand, hitResult);

		BeltBlockEntity belt = BeltHelper.getSegmentBE(level, pos);
		if (belt == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		if (isHand) {
			BeltBlockEntity controllerBelt = belt.getControllerBE();
			if (controllerBelt == null)
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			MutableBoolean success = new MutableBoolean(false);
			controllerBelt.getInventory()
				.applyToEachWithin(belt.index + .5f, .55f, (transportedItemStack) -> {
					player.getInventory()
						.placeItemBackInInventory(transportedItemStack.stack, Prediction.SERVER_ONLY);
					success.setTrue();
					return TransportedResult.removeItem();
				});
			if (success.isTrue())
				level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
					1f + level.getRandom().nextFloat());
		}

		if (isShaft) {
			if (state.getValue(PART) != BeltPart.MIDDLE)
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			if (!player.isCreative())
				stack.shrink(1);
			KineticBlockEntity.switchToBlockState(level, pos, state.setValue(PART, BeltPart.PULLEY));
			return InteractionResult.SUCCESS;
		}

		if (isBrassCasing(stack)) {
			withBlockEntityDo(level, pos, be -> be.setCasingType(CasingType.BRASS));
			updateCoverProperty(level, pos, level.getBlockState(pos));

			SoundType soundType = brassCasing().defaultBlockState()
				.getSoundType();
			level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
				(soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

			return InteractionResult.SUCCESS;
		}

		if (stack.is(ModItems.ANDESITE_CASING)) {
			withBlockEntityDo(level, pos, be -> be.setCasingType(CasingType.ANDESITE));
			updateCoverProperty(level, pos, level.getBlockState(pos));

			SoundType soundType = ModBlocks.ANDESITE_CASING.defaultBlockState()
				.getSoundType();
			level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
				(soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

			return InteractionResult.SUCCESS;
		}

		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** Create asks GenericItemEmptying whether the item holds water: buckets, bottles and fluid containers of other mods. */
	private static boolean holdsWater(ItemStack stack) {
		if (stack.isEmpty())
			return false;
		ItemStack one = stack.copyWithCount(1);
		if (one.is(Items.POTION)) {
			PotionContents contents = one.get(DataComponents.POTION_CONTENTS);
			return contents != null && contents.is(Potions.WATER);
		}
		return FluidItemHandlers.get(one)
			.map(handler -> handler.drain(Integer.MAX_VALUE, FluidAction.SIMULATE)
				.getFluid()
				.isSame(Fluids.WATER))
			.orElse(false);
	}

	/** Brass casing is not ported yet; this finds it by id when it exists. */
	@Nullable
	private static Item brassCasingItem() {
		return BuiltInRegistries.ITEM.getOptional(CreatePolymer.id("brass_casing"))
			.orElse(null);
	}

	private static boolean isBrassCasing(ItemStack stack) {
		Item item = brassCasingItem();
		return item != null && stack.is(item);
	}

	/** The brass casing block, or a gold block until it is ported. */
	public static Block brassCasing() {
		return BuiltInRegistries.BLOCK.getOptional(CreatePolymer.id("brass_casing"))
			.orElse(Blocks.GOLD_BLOCK);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		Player player = context.getPlayer();
		BlockPos pos = context.getClickedPos();

		if (state.getValue(CASING)) {
			if (world.isClientSide())
				return InteractionResult.SUCCESS;
			withBlockEntityDo(world, pos, be -> be.setCasingType(CasingType.NONE));
			return InteractionResult.SUCCESS;
		}

		if (state.getValue(PART) == BeltPart.PULLEY) {
			if (world.isClientSide())
				return InteractionResult.SUCCESS;
			KineticBlockEntity.switchToBlockState(world, pos, state.setValue(PART, BeltPart.MIDDLE));
			if (player != null && !player.isCreative())
				player.getInventory()
					.placeItemBackInInventory(new ItemStack(ModItems.SHAFT), Prediction.SERVER_ONLY);
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(SLOPE, PART, CASING, WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return BeltShapes.getShape(state);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		if (state.getBlock() != this)
			return Shapes.empty();

		// Sloped belts: the stair-shaped collision that players are shown (see BeltShapes)
		if (state.getValue(SLOPE).isDiagonal())
			return BeltShapes.getSlopeCollisionShape(state);

		VoxelShape shape = getShape(state, worldIn, pos, context);
		if (!(context instanceof EntityCollisionContext))
			return shape;

		return getBlockEntityOptional(worldIn, pos).map(be -> {
				Entity entity = ((EntityCollisionContext) context).getEntity();
				if (entity == null)
					return shape;

				BeltBlockEntity controller = be.getControllerBE();
				if (controller == null)
					return shape;
				if (controller.passengers == null || !controller.passengers.containsKey(entity))
					return BeltShapes.getCollisionShape(state);
				return shape;

			})
			.orElse(shape);
	}

	public static void initBelt(Level world, BlockPos pos) {
		if (world.isClientSide())
			return;
		if (world instanceof ServerLevel && ((ServerLevel) world).getChunkSource()
			.getGenerator() instanceof DebugLevelSource)
			return;

		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof BeltBlock))
			return;
		// Find controller
		int limit = 1000;
		BlockPos currentPos = pos;
		while (limit-- > 0) {
			BlockState currentState = world.getBlockState(currentPos);
			if (!(currentState.getBlock() instanceof BeltBlock)) {
				world.destroyBlock(pos, true);
				return;
			}
			BlockPos nextSegmentPosition = nextSegmentPosition(currentState, currentPos, false);
			if (nextSegmentPosition == null)
				break;
			if (!world.isLoaded(nextSegmentPosition))
				return;
			currentPos = nextSegmentPosition;
		}

		// Init belts
		int index = 0;
		List<BlockPos> beltChain = getBeltChain(world, currentPos);
		if (beltChain.size() < 2) {
			world.destroyBlock(currentPos, true);
			return;
		}

		for (BlockPos beltPos : beltChain) {
			BlockEntity blockEntity = world.getBlockEntity(beltPos);
			BlockState currentState = world.getBlockState(beltPos);

			if (blockEntity instanceof BeltBlockEntity be && currentState.getBlock() instanceof BeltBlock) {
				be.setController(currentPos);
				be.beltLength = beltChain.size();
				be.index = index;
				be.attachKinetics();
				be.setChanged();
				be.sendData();

				if (be.isController() && !canTransportObjects(currentState))
					be.getInventory()
						.ejectAll();
			} else {
				world.destroyBlock(currentPos, true);
				return;
			}
			index++;
		}

	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean isMoving) {
		super.affectNeighborsAfterRemoval(state, world, pos, isMoving);

		if (isMoving)
			return;

		// Destroy chain
		for (boolean forward : Iterate.trueAndFalse) {
			BlockPos currentPos = nextSegmentPosition(state, pos, forward);
			if (currentPos == null)
				continue;
			BlockState currentState = world.getBlockState(currentPos);
			if (!(currentState.getBlock() instanceof BeltBlock))
				continue;

			boolean hasPulley = false;
			BlockEntity blockEntity = world.getBlockEntity(currentPos);
			if (blockEntity instanceof BeltBlockEntity belt) {
				if (belt.isController())
					belt.getInventory()
						.ejectAll();

				hasPulley = belt.hasPulley();
			}

			world.removeBlockEntity(currentPos);
			BlockState shaftState = ModBlocks.SHAFT.defaultBlockState()
				.setValue(BlockStateProperties.AXIS, getRotationAxis(currentState));
			world.setBlock(currentPos, withWater(world,
				hasPulley ? shaftState : Blocks.AIR.defaultBlockState(), currentPos), Block.UPDATE_ALL);
			world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, currentPos, Block.getId(currentState));
		}
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
									 Direction side, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		updateWater(level, ticks, state, pos);
		if (level instanceof LevelAccessor world) {
			if (side.getAxis()
				.isHorizontal())
				updateTunnelConnections(world, pos.above());
			if (side == Direction.UP)
				updateCoverProperty(world, pos, state);
		}
		return state;
	}

	public void updateCoverProperty(LevelAccessor world, BlockPos pos, BlockState state) {
		if (world.isClientSide())
			return;
		if (state.getValue(CASING) && state.getValue(SLOPE) == BeltSlope.HORIZONTAL)
			withBlockEntityDo(world, pos, bbe -> bbe.setCovered(isBlockCoveringBelt(world, pos.above())));
	}

	public static boolean isBlockCoveringBelt(LevelAccessor world, BlockPos pos) {
		BlockState blockState = world.getBlockState(pos);
		VoxelShape collisionShape = blockState.getCollisionShape(world, pos);
		if (collisionShape.isEmpty())
			return false;
		AABB bounds = collisionShape.bounds();
		if (bounds.getXsize() < .5f || bounds.getZsize() < .5f)
			return false;
		if (bounds.minY > 0)
			return false;
		if (blockState.is(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER))
			return false;
		if (FunnelBlock.isFunnel(blockState) && FunnelBlock.getFunnelFacing(blockState) != Direction.UP)
			return false;
		if (blockState.getBlock() instanceof BeltTunnelBlock)
			return false;
		return true;
	}

	private void updateTunnelConnections(LevelAccessor world, BlockPos pos) {
		Block tunnelBlock = world.getBlockState(pos)
			.getBlock();
		if (tunnelBlock instanceof BeltTunnelBlock)
			((BeltTunnelBlock) tunnelBlock).updateTunnel(world, pos);
	}

	public static List<BlockPos> getBeltChain(LevelAccessor world, BlockPos controllerPos) {
		List<BlockPos> positions = new LinkedList<>();

		BlockState blockState = world.getBlockState(controllerPos);
		if (!(blockState.getBlock() instanceof BeltBlock))
			return positions;

		int limit = 1000;
		BlockPos current = controllerPos;
		while (limit-- > 0 && current != null) {
			BlockState state = world.getBlockState(current);
			if (!(state.getBlock() instanceof BeltBlock))
				break;
			positions.add(current);
			current = nextSegmentPosition(state, current, true);
		}

		return positions;
	}

	@Nullable
	public static BlockPos nextSegmentPosition(BlockState state, BlockPos pos, boolean forward) {
		Direction direction = state.getValue(HORIZONTAL_FACING);
		BeltSlope slope = state.getValue(SLOPE);
		BeltPart part = state.getValue(PART);

		int offset = forward ? 1 : -1;

		if (part == BeltPart.END && forward || part == BeltPart.START && !forward)
			return null;
		if (slope == BeltSlope.VERTICAL)
			return pos.above(direction.getAxisDirection() == AxisDirection.POSITIVE ? offset : -offset);
		pos = pos.relative(direction, offset);
		if (slope != BeltSlope.HORIZONTAL && slope != BeltSlope.SIDEWAYS)
			return pos.above(slope == BeltSlope.UPWARD ? offset : -offset);
		return pos;
	}

	@Override
	public Class<BeltBlockEntity> getBlockEntityClass() {
		return BeltBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends BeltBlockEntity> getBlockEntityType() {
		return BeltRegistry.BELT_BLOCK_ENTITY;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		BlockState rotate = super.rotate(state, rot);

		if (state.getValue(SLOPE) != BeltSlope.VERTICAL)
			return rotate;
		if (state.getValue(HORIZONTAL_FACING)
			.getAxisDirection() != rotate.getValue(HORIZONTAL_FACING)
			.getAxisDirection()) {
			if (state.getValue(PART) == BeltPart.START)
				return rotate.setValue(PART, BeltPart.END);
			if (state.getValue(PART) == BeltPart.END)
				return rotate.setValue(PART, BeltPart.START);
		}

		return rotate;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	// Create's ProperWaterloggedBlock

	public FluidState fluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
	}

	public void updateWater(LevelReader level, ScheduledTickAccess ticks, BlockState state, BlockPos pos) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
	}

	@Nullable
	public static BlockState withWater(LevelAccessor level, BlockState placementState, BlockPos pos) {
		if (placementState == null)
			return null;
		FluidState ifluidstate = level.getFluidState(pos);
		if (placementState.isAir())
			return ifluidstate.getType() == Fluids.WATER ? ifluidstate.createLegacyBlock() : placementState;
		if (!(placementState.getBlock() instanceof SimpleWaterloggedBlock))
			return placementState;
		return placementState.setValue(BlockStateProperties.WATERLOGGED, ifluidstate.getType() == Fluids.WATER);
	}

	// ---- Polymer ----

	// Invisible client blocks with a collision close to the belt's: a farmland (15 px tall) for flat belts, a
	// stair (8 px, stepping up to 16 px) for sloped ones.
	private static @Nullable BlockState CLIENT_FLAT;
	private static final Map<Direction, BlockState> CLIENT_STAIRS = new EnumMap<>(Direction.class);
	private static final Map<Direction, BlockState> CLIENT_STAIRS_WET = new EnumMap<>(Direction.class);

	public static void requestClientStates() {
		CLIENT_FLAT = PolymerBlockResourceUtils.requestEmpty(BlockModelType.FARMLAND);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			CLIENT_STAIRS.put(d, PolymerBlockResourceUtils.requestEmpty(
				BlockModelType.valueOf("STAIRS_" + d.name() + "_BOTTOM_STRAIGHT")));
			CLIENT_STAIRS_WET.put(d, PolymerBlockResourceUtils.requestEmpty(
				BlockModelType.valueOf("STAIRS_" + d.name() + "_BOTTOM_STRAIGHT_WATERLOGGED")));
		}
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		boolean water = state.getValue(WATERLOGGED);
		BeltSlope slope = state.getValue(SLOPE);
		if (slope.isDiagonal()) {
			Direction facing = state.getValue(HORIZONTAL_FACING);
			// a stair "faces" down its steps, a belt rising towards its facing has stairs that face away
			Direction stairFacing = slope == BeltSlope.UPWARD ? facing.getOpposite() : facing;
			BlockState client = (water ? CLIENT_STAIRS_WET : CLIENT_STAIRS).get(stairFacing);
			if (client != null)
				return client;
		} else if (slope == BeltSlope.HORIZONTAL && !water && CLIENT_FLAT != null)
			return CLIENT_FLAT;
		return super.getPolymerBlockState(state, context);
	}

	@Override
	protected BlockState particleState() {
		return Blocks.WOOL.pick(DyeColor.GRAY).defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new BeltVisual(state);
	}

}

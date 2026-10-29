package dev.createpolymer.contraptions.actors.seat;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's SeatBlock (MIT, The Create Team). Players and mobs sit on a {@link SeatEntity} above the block.
 * <p>
 * Players see an invisible half block (its box is the seat's box) with the seat model on display entities. Create's
 * landing check, which seats mobs that fall onto it and bounces everything else, runs through the block's bounce
 * (restitution 0.66, like a bed) in 26.3.
 */
public class SeatBlock extends Block implements ProperWaterloggedBlock, PolymerModelBlock, ContraptionDisplay {
	/** Create's AllShapes.SEAT, SEAT_COLLISION and SEAT_COLLISION_PLAYERS */
	public static final VoxelShape SEAT = ShapeUtil.box(0, 0, 0, 16, 8, 16);
	public static final VoxelShape SEAT_COLLISION = ShapeUtil.box(0, 0, 0, 16, 6, 16);
	public static final VoxelShape SEAT_COLLISION_PLAYERS = ShapeUtil.box(0, 0, 0, 16, 3, 16);

	protected final DyeColor color;

	public SeatBlock(Properties properties, DyeColor color) {
		super(properties);
		this.color = color;
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(WATERLOGGED));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		return withWater(super.getStateForPlacement(pContext), pContext);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pCurrentPos,
		Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource random) {
		updateWater(level, ticks, pState, pCurrentPos);
		return pState;
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	/**
	 * Create's updateEntityAfterFallOn: an entity that lands on the seat sits down if it can be picked up. Entities
	 * that cannot (players, items, ...) bounce, which the block's restitution does.
	 */
	@Override
	public void bounceOn(Level level, BlockState state, BlockPos belowPos, Entity entity, double fallDistance) {
		// the position given is half a block under the entity: the seat is where the entity stands
		BlockPos pos = entity.blockPosition();
		if (entity instanceof Player || !(entity instanceof LivingEntity) || !canBePickedUp(entity)
			|| isSeatOccupied(level, pos))
			return;
		if (level.getBlockState(pos)
			.getBlock() != this)
			return;
		sitDown(level, pos, entity);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected VoxelShape getShape(BlockState p_220053_1_, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		return SEAT;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState p_220071_1_, BlockGetter p_220071_2_, BlockPos p_220071_3_,
		CollisionContext ctx) {
		if (ctx instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof Player)
			return SEAT_COLLISION_PLAYERS;
		return SEAT_COLLISION;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (player.isShiftKeyDown() || player instanceof FakePlayer)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		DyeColor color = stack.get(DataComponents.DYE);
		if (color != null && color != this.color) {
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			BlockState newState = ActorsRegistry.SEATS.get(color)
				.defaultBlockState()
				.setValue(WATERLOGGED, state.getValue(WATERLOGGED));
			level.setBlockAndUpdate(pos, newState);
			return InteractionResult.SUCCESS;
		}

		List<SeatEntity> seats = level.getEntitiesOfClass(SeatEntity.class, new AABB(pos));
		if (!seats.isEmpty()) {
			SeatEntity seatEntity = seats.get(0);
			List<Entity> passengers = seatEntity.getPassengers();
			if (!passengers.isEmpty() && passengers.get(0) instanceof Player)
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			if (!level.isClientSide()) {
				seatEntity.ejectPassengers();
				player.startRiding(seatEntity);
			}
			return InteractionResult.SUCCESS;
		}

		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		sitDown(level, pos, getLeashed(level, player).orElse(player));
		return InteractionResult.SUCCESS;
	}

	public static boolean isSeatOccupied(Level world, BlockPos pos) {
		return !world.getEntitiesOfClass(SeatEntity.class, new AABB(pos))
			.isEmpty();
	}

	public static Optional<Entity> getLeashed(Level level, Player player) {
		List<Entity> entities = player.level()
			.getEntities((Entity) null, player.getBoundingBox()
				.inflate(10), e -> true);
		for (Entity e : entities)
			if (e instanceof Mob mob && mob.getLeashHolder() == player && SeatBlock.canBePickedUp(e))
				return Optional.of(mob);
		return Optional.empty();
	}

	public static boolean canBePickedUp(Entity passenger) {
		if (passenger instanceof Shulker)
			return false;
		if (passenger instanceof Player)
			return false;
		if (passenger.is(ActorsRegistry.IGNORE_SEAT))
			return false;
		if (!ActorsRegistry.seatHostileMobs && !passenger.getType()
			.getCategory()
			.isFriendly())
			return false;
		return passenger instanceof LivingEntity;
	}

	public static void sitDown(Level level, BlockPos pos, Entity entity) {
		if (level.isClientSide())
			return;
		SeatEntity seat = new SeatEntity(level);
		seat.setPosForBlock(pos);
		level.addFreshEntity(seat);
		entity.startRiding(seat, true, true);
		if (entity instanceof TamableAnimal ta)
			ta.setInSittingPose(true);
	}

	public DyeColor getColor() {
		return color;
	}

	// ---- Polymer ----

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return RedstoneClientStates.slab(false, state.getValue(BlockStateProperties.WATERLOGGED));
	}

	@Override
	public BlockState particleState() {
		return net.minecraft.world.level.block.Blocks.WOOL.pick(color).defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, color);
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		return List.of(new Piece(ModelItems.block(color.getSerializedName() + "_seat")));
	}

	/** Create's seat model (a coloured cushion) */
	public static class Model extends BaseModel {
		public Model(BlockState state, DyeColor color) {
			super(state);
			addPart(ModelItems.block(color.getSerializedName() + "_seat"));
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.entity.@Nullable BlockEntity be,
			long time) {
		}
	}

}

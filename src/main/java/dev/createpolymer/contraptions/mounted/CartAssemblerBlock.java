package dev.createpolymer.contraptions.mounted;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.schematic.requirement.SpecialBlockItemRequirement;
import dev.createpolymer.contraptions.minecart.MinecartPassingBlock;
import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.redstone.rail.ControllerRailBlock;
import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.schematics.requirement.ItemRequirement.ItemUseType;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Create's cart assembler. Players see a vanilla rail of the matching kind, so that minecarts roll over it on their
 * client, with Create's assembler casing drawn around it by a display entity.
 */
public class CartAssemblerBlock extends BaseRailBlock
	implements SpecialBlockItemRequirement, IBE<CartAssemblerBlockEntity>, IWrenchable, MinecartPassingBlock, PolymerModelBlock {

	/** Create's AllShapes.CART_ASSEMBLER and CART_ASSEMBLER_PLAYER_COLLISION. */
	private static final VoxelShaper CART_ASSEMBLER = VoxelShaper.forHorizontalAxis(
		Shapes.or(ShapeUtil.box(0, 12, 0, 16, 16, 16), ShapeUtil.box(-2, 0, 1, 18, 14, 15)), Axis.Z);
	private static final VoxelShaper CART_ASSEMBLER_PLAYER_COLLISION =
		VoxelShaper.forHorizontalAxis(ShapeUtil.box(0, 0, 1, 16, 16, 15), Axis.Z);

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty BACKWARDS = BooleanProperty.create("backwards");
	public static final Property<RailShape> RAIL_SHAPE =
		EnumProperty.create("shape", RailShape.class, RailShape.EAST_WEST, RailShape.NORTH_SOUTH);
	public static final Property<CartAssembleRailType> RAIL_TYPE =
		EnumProperty.create("rail_type", CartAssembleRailType.class);

	public CartAssemblerBlock(Properties properties) {
		super(true, properties);
		registerDefaultState(defaultBlockState().setValue(POWERED, false)
			.setValue(BACKWARDS, false)
			.setValue(RAIL_TYPE, CartAssembleRailType.POWERED_RAIL)
			.setValue(WATERLOGGED, false));
	}

	public static BlockState createAnchor(BlockState state) {
		Axis axis = state.getValue(RAIL_SHAPE) == RailShape.NORTH_SOUTH ? Axis.Z : Axis.X;
		return MinecartRegistry.MINECART_ANCHOR.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
	}

	private static Item getRailItem(BlockState state) {
		return state.getValue(RAIL_TYPE)
			.getItem();
	}

	public static BlockState getRailBlock(BlockState state) {
		BaseRailBlock railBlock = (BaseRailBlock) state.getValue(RAIL_TYPE)
			.getBlock();

		@SuppressWarnings("deprecation")
		BlockState railState = railBlock.defaultBlockState()
			.setValue(railBlock.getShapeProperty(), state.getValue(RAIL_SHAPE));

		if (railState.hasProperty(ControllerRailBlock.BACKWARDS))
			railState = railState.setValue(ControllerRailBlock.BACKWARDS, state.getValue(BACKWARDS));
		return railState;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(RAIL_SHAPE, POWERED, RAIL_TYPE, BACKWARDS, WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	/**
	 * Create's {@code canMakeSlopes() == false}: the assembler stays flat and never takes the shape of the rails next
	 * to it (its shape property only has the two flat shapes).
	 */
	@Override
	protected BlockState updateDir(Level level, BlockPos pos, BlockState state, boolean first) {
		return state;
	}

	@Override
	public void onMinecartPass(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos,
		AbstractMinecart cart) {
		if (!canAssembleTo(cart))
			return;
		if (world.isClientSide())
			return;

		withBlockEntityDo(world, pos, be -> be.assembleNextTick(cart));
	}

	public enum CartAssemblerAction {
		ASSEMBLE, DISASSEMBLE, ASSEMBLE_ACCELERATE, DISASSEMBLE_BRAKE, ASSEMBLE_ACCELERATE_DIRECTIONAL, PASS;

		public boolean shouldAssemble() {
			return this == ASSEMBLE || this == ASSEMBLE_ACCELERATE || this == ASSEMBLE_ACCELERATE_DIRECTIONAL;
		}

		public boolean shouldDisassemble() {
			return this == DISASSEMBLE || this == DISASSEMBLE_BRAKE;
		}
	}

	public static CartAssemblerAction getActionForCart(BlockState state, AbstractMinecart cart) {
		CartAssembleRailType type = state.getValue(RAIL_TYPE);
		boolean powered = state.getValue(POWERED);
		switch (type) {
		case ACTIVATOR_RAIL:
			return powered ? CartAssemblerAction.DISASSEMBLE : CartAssemblerAction.PASS;
		case CONTROLLER_RAIL:
			return powered ? CartAssemblerAction.ASSEMBLE_ACCELERATE_DIRECTIONAL
				: CartAssemblerAction.DISASSEMBLE_BRAKE;
		case DETECTOR_RAIL:
			return cart.getPassengers()
				.isEmpty() ? CartAssemblerAction.ASSEMBLE_ACCELERATE : CartAssemblerAction.DISASSEMBLE;
		case POWERED_RAIL:
			return powered ? CartAssemblerAction.ASSEMBLE_ACCELERATE : CartAssemblerAction.DISASSEMBLE_BRAKE;
		case REGULAR:
			return powered ? CartAssemblerAction.ASSEMBLE : CartAssemblerAction.DISASSEMBLE;
		default:
			return CartAssemblerAction.PASS;
		}
	}

	public static boolean canAssembleTo(AbstractMinecart cart) {
		return cart.isRideable() || cart instanceof MinecartFurnace || cart instanceof MinecartChest;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		Item previousItem = getRailItem(state);
		Item heldItem = stack.getItem();
		if (heldItem != previousItem) {

			CartAssembleRailType newType = null;
			for (CartAssembleRailType type : CartAssembleRailType.values())
				if (heldItem == type.getItem())
					newType = type;
			if (newType == null)
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1, 1);
			level.setBlockAndUpdate(pos, state.setValue(RAIL_TYPE, newType));

			if (!player.isCreative()) {
				stack.shrink(1);
				player.getInventory()
					.placeItemBackInInventory(new ItemStack(previousItem), Prediction.SERVER_ONLY);
			}
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected void neighborChanged(@NotNull BlockState state, @NotNull Level worldIn, @NotNull BlockPos pos,
		@NotNull Block blockIn, @Nullable Orientation orientation, boolean isMoving) {
		if (worldIn.isClientSide())
			return;
		boolean previouslyPowered = state.getValue(POWERED);
		if (previouslyPowered != worldIn.hasNeighborSignal(pos))
			worldIn.setBlock(pos, state.cycle(POWERED), Block.UPDATE_CLIENTS);
		super.neighborChanged(state, worldIn, pos, blockIn, orientation, isMoving);
	}

	@Override
	@NotNull
	public Property<RailShape> getShapeProperty() {
		return RAIL_SHAPE;
	}

	@Override
	@NotNull
	protected VoxelShape getShape(BlockState state, @NotNull BlockGetter worldIn, @NotNull BlockPos pos,
		@NotNull CollisionContext context) {
		return CART_ASSEMBLER.get(getRailAxis(state));
	}

	protected Axis getRailAxis(BlockState state) {
		return state.getValue(RAIL_SHAPE) == RailShape.NORTH_SOUTH ? Direction.Axis.Z : Direction.Axis.X;
	}

	@Override
	@NotNull
	protected VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter worldIn,
		@NotNull BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext) {
			Entity entity = ((EntityCollisionContext) context).getEntity();
			if (entity instanceof AbstractMinecart)
				return Shapes.empty();
			if (entity instanceof Player)
				return CART_ASSEMBLER_PLAYER_COLLISION.get(getRailAxis(state));
		}
		return Shapes.block();
	}

	@Override
	public Class<CartAssemblerBlockEntity> getBlockEntityClass() {
		return CartAssemblerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CartAssemblerBlockEntity> getBlockEntityType() {
		return MinecartRegistry.CART_ASSEMBLER_BLOCK_ENTITY;
	}

	@Override
	protected boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader world, @NotNull BlockPos pos) {
		return false;
	}

	@Override
	@SuppressWarnings("deprecation")
	@NotNull
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		List<ItemStack> drops = new ArrayList<>(super.getDrops(state, builder));
		drops.addAll(getRailBlock(state).getDrops(builder));
		return drops;
	}

	@SuppressWarnings("deprecation")
	public List<ItemStack> getDropsNoRail(BlockState state, ServerLevel world, BlockPos pos,
		@Nullable BlockEntity p_220077_3_, @Nullable Entity p_220077_4_, ItemStack p_220077_5_) {
		return super.getDrops(state,
			(new LootParams.Builder(world)).withParameter(LootContextParams.ORIGIN, Vec3.atLowerCornerOf(pos))
				.withParameter(LootContextParams.TOOL, p_220077_5_)
				.withOptionalParameter(LootContextParams.THIS_ENTITY, p_220077_4_)
				.withOptionalParameter(LootContextParams.BLOCK_ENTITY, p_220077_3_));
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		if (world.isClientSide())
			return InteractionResult.SUCCESS;
		if (player != null && !player.isCreative())
			getDropsNoRail(state, (ServerLevel) world, pos, world.getBlockEntity(pos), player, context.getItemInHand())
				.forEach(itemStack -> player.getInventory()
					.placeItemBackInInventory(itemStack, Prediction.SERVER_ONLY));
		if (world instanceof ServerLevel)
			state.spawnAfterBreak((ServerLevel) world, pos, ItemStack.EMPTY, true);
		world.setBlockAndUpdate(pos, getRailBlock(state));
		return InteractionResult.SUCCESS;
	}

	public static class MinecartAnchorBlock extends Block
		implements eu.pb4.polymer.core.api.block.PolymerBlock, ContraptionDisplay {

		public MinecartAnchorBlock(Properties p_i48440_1_) {
			super(p_i48440_1_);
		}

		@Override
		protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
			builder.add(BlockStateProperties.HORIZONTAL_AXIS);
			super.createBlockStateDefinition(builder);
		}

		@Override
		@NotNull
		protected VoxelShape getShape(@NotNull BlockState p_220053_1_, @NotNull BlockGetter p_220053_2_,
			@NotNull BlockPos p_220053_3_, @NotNull CollisionContext p_220053_4_) {
			return Shapes.empty();
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			return Blocks.BARRIER.defaultBlockState();
		}

		@Override
		public List<Piece> getContraptionPieces(BlockState state) {
			return List.of(new Piece(ModelItems.block("cart_assembler/minecart_anchor")));
		}
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		if (world.isClientSide())
			return InteractionResult.SUCCESS;
		BlockPos pos = context.getClickedPos();
		world.setBlock(pos, rotate(state, Rotation.CLOCKWISE_90), Block.UPDATE_ALL);
		world.updateNeighborsAt(pos.below(), this);
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (rotation == Rotation.NONE)
			return state;
		BlockState base = MinecartRegistry.CONTROLLER_RAIL.defaultBlockState()
			.setValue(ControllerRailBlock.SHAPE, state.getValue(RAIL_SHAPE))
			.setValue(ControllerRailBlock.BACKWARDS, state.getValue(BACKWARDS))
			.rotate(rotation);
		return state.setValue(RAIL_SHAPE, base.getValue(ControllerRailBlock.SHAPE))
			.setValue(BACKWARDS, base.getValue(ControllerRailBlock.BACKWARDS));
	}

	@Override
	@SuppressWarnings("deprecation")
	protected BlockState mirror(BlockState state, Mirror mirror) {
		if (mirror == Mirror.NONE)
			return state;
		BlockState base = MinecartRegistry.CONTROLLER_RAIL.defaultBlockState()
			.setValue(ControllerRailBlock.SHAPE, state.getValue(RAIL_SHAPE))
			.setValue(ControllerRailBlock.BACKWARDS, state.getValue(BACKWARDS))
			.mirror(mirror);
		return state.setValue(BACKWARDS, base.getValue(ControllerRailBlock.BACKWARDS));
	}

	public static Direction getHorizontalDirection(BlockState blockState) {
		if (!(blockState.getBlock() instanceof CartAssemblerBlock))
			return Direction.SOUTH;
		Direction pointingTo = getPointingTowards(blockState);
		return blockState.getValue(BACKWARDS) ? pointingTo.getOpposite() : pointingTo;
	}

	private static Direction getPointingTowards(BlockState state) {
		switch (state.getValue(RAIL_SHAPE)) {
		case EAST_WEST:
			return Direction.WEST;
		default:
			return Direction.NORTH;
		}
	}

	// ---- Polymer ----

	/** A vanilla rail of the assembler's kind: the shape, the signal and the water are all a client needs. */
	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		RailShape shape = state.getValue(RAIL_SHAPE);
		boolean powered = state.getValue(POWERED);
		boolean water = state.getValue(WATERLOGGED);
		return switch (state.getValue(RAIL_TYPE)) {
			case REGULAR -> Blocks.RAIL.defaultBlockState()
				.setValue(BlockStateProperties.RAIL_SHAPE, shape)
				.setValue(WATERLOGGED, water);
			case DETECTOR_RAIL -> Blocks.DETECTOR_RAIL.defaultBlockState()
				.setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, shape)
				.setValue(BlockStateProperties.POWERED, powered)
				.setValue(WATERLOGGED, water);
			case ACTIVATOR_RAIL -> Blocks.ACTIVATOR_RAIL.defaultBlockState()
				.setValue(PoweredRailBlock.SHAPE, shape)
				.setValue(PoweredRailBlock.POWERED, powered)
				.setValue(WATERLOGGED, water);
			case POWERED_RAIL, CONTROLLER_RAIL -> Blocks.POWERED_RAIL.defaultBlockState()
				.setValue(PoweredRailBlock.SHAPE, shape)
				.setValue(PoweredRailBlock.POWERED, powered)
				.setValue(WATERLOGGED, water);
		};
	}

	@Override
	public BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final Part casing;

		public Model(BlockState state) {
			super(state);
			casing = showVariant(addPart(ModelItems.block("cart_assembler/block")), "cart_assembler", state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(casing, "cart_assembler", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity be) {
		ArrayList<ItemStack> requiredItems = new ArrayList<>();
		requiredItems.add(new ItemStack(getRailItem(state)));
		requiredItems.add(new ItemStack(asItem()));
		return new ItemRequirement(ItemUseType.CONSUME, requiredItems);
	}
}

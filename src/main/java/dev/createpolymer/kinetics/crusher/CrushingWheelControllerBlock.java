package dev.createpolymer.kinetics.crusher;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.registry.feature.CrushingRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/**
 * Ported from Create's CrushingWheelControllerBlock (MIT, The Create Team).
 * <p>
 * Players see nothing here (air, like Create's empty model); the block entity's item is drawn by a display entity.
 * Players always get an empty collision shape on the server: the client has no platform to stand on, and a floor
 * only the server knew about would send players back each time they walked into the gap.
 */
public class CrushingWheelControllerBlock extends DirectionalBlock implements IBE<CrushingWheelControllerBlockEntity>, PolymerModelBlock {

	public CrushingWheelControllerBlock(Properties p_i48440_1_) {
		super(p_i48440_1_);
	}

	public static final BooleanProperty VALID = BooleanProperty.create("valid");

	/** Create's AllShapes.CRUSHING_WHEEL_CONTROLLER_COLLISION: a 13px platform at the feeding side (authored facing down). */
	public static final Map<Direction, VoxelShape> CRUSHING_WHEEL_CONTROLLER_COLLISION =
		ShapeUtil.forDirectional(ShapeUtil.box(0, 3, 0, 16, 16, 16));

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
		return false;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(VALID);
		builder.add(FACING);
		super.createBlockStateDefinition(builder);
	}

	@Override
	protected void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn,
								InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (!state.getValue(VALID))
			return;

		Direction facing = state.getValue(FACING);
		Axis axis = facing.getAxis();

		checkEntityForProcessing(worldIn, pos, entityIn);

		withBlockEntityDo(worldIn, pos, be -> {
			if (be.processingEntity == entityIn)

				entityIn.makeStuckInBlock(state, new Vec3(axis == Axis.X ? (double) 0.05F : 0.25D,
					axis == Axis.Y ? (double) 0.05F : 0.25D, axis == Axis.Z ? (double) 0.05F : 0.25D));
		});
	}

	public void checkEntityForProcessing(Level worldIn, BlockPos pos, Entity entityIn) {
		CrushingWheelControllerBlockEntity be = getBlockEntity(worldIn, pos);
		if (be == null)
			return;
		if (be.crushingspeed == 0)
			return;
//		if (entityIn instanceof ItemEntity)
//			((ItemEntity) entityIn).setPickUpDelay(10);
		if (CrushingWheelControllerBlockEntity.isBypassing(entityIn, pos))
			return;
		if (be.isOccupied())
			return;
		boolean isPlayer = entityIn instanceof Player;
		if (isPlayer && ((Player) entityIn).isCreative())
			return;
		if (isPlayer && entityIn.level().getDifficulty() == Difficulty.PEACEFUL)
			return;

		be.startCrushing(entityIn);
	}

	@Override
	protected BlockState updateShape(BlockState stateIn, LevelReader level, ScheduledTickAccess ticks, BlockPos currentPos,
									 Direction facing, BlockPos facingPos, BlockState facingState, RandomSource random) {
		updateSpeed(stateIn, level, currentPos);
		return stateIn;
	}

	public void updateSpeed(BlockState state, BlockGetter world, BlockPos pos) {
		withBlockEntityDo(world, pos, be -> {
			if (!state.getValue(VALID)) {
				if (be.crushingspeed != 0) {
					be.crushingspeed = 0;
					be.sendData();
				}
				return;
			}

			for (Direction d : Iterate.directions) {
				BlockState neighbour = world.getBlockState(pos.relative(d));
				if (!neighbour.is(CrushingRegistry.CRUSHING_WHEEL))
					continue;
				if (neighbour.getValue(BlockStateProperties.AXIS) == d.getAxis())
					continue;
				BlockEntity adjBE = world.getBlockEntity(pos.relative(d));
				if (!(adjBE instanceof CrushingWheelBlockEntity cwbe))
					continue;
				be.crushingspeed = Math.abs(cwbe.getSpeed() / 50f);
				be.sendData();

				break;
			}
		});
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		VoxelShape standardShape = CRUSHING_WHEEL_CONTROLLER_COLLISION.get(state.getValue(FACING));

		if (!(context instanceof EntityCollisionContext ecc))
			return standardShape;
		Entity entity = ecc.getEntity();
		if (entity == null)
			return standardShape;
		if (entity instanceof Player)
			return Shapes.empty();

		if (!state.getValue(VALID))
			return standardShape;

		if (CrushingWheelControllerBlockEntity.isBypassing(entity, pos))
			if (state.getValue(FACING) != Direction.UP) // Allow output items to land on top of the block rather
				return Shapes.empty();					// than falling back through.

		CrushingWheelControllerBlockEntity be = getBlockEntity(worldIn, pos);
		if (be != null && be.processingEntity == entity)
			return Shapes.empty();

		return standardShape;
	}

	@Override
	public Class<CrushingWheelControllerBlockEntity> getBlockEntityClass() {
		return CrushingWheelControllerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CrushingWheelControllerBlockEntity> getBlockEntityType() {
		return CrushingRegistry.CRUSHING_WHEEL_CONTROLLER_BLOCK_ENTITY;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	// ---- Polymer ----

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return Blocks.AIR.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Shows the item that is being crushed (or its results) in the middle of the block. */
	public static class Model extends BaseModel {
		private final ItemDisplayElement item;
		private ItemStack shown = ItemStack.EMPTY;

		public Model(BlockState state) {
			super(state);
			item = new ItemDisplayElement(ItemStack.EMPTY);
			item.setItemDisplayContext(ItemDisplayContext.FIXED);
			item.setViewRange(1f);
			item.setTeleportDuration(0);
			item.setInterpolationDuration(0);
			item.setTransformation(new Matrix4f().scale(.5f));
			addElement(item);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof CrushingWheelControllerBlockEntity crusher))
				return;
			ItemStack stack = crusher.getDisplayedItem();
			if (ItemStack.isSameItemSameComponents(stack, shown) && stack.isEmpty() == shown.isEmpty())
				return;
			shown = stack.copyWithCount(1);
			item.setItem(shown);
			item.startInterpolation();
		}
	}

}

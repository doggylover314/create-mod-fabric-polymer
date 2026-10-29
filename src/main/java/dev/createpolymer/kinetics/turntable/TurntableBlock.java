package dev.createpolymer.kinetics.turntable;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.KineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.feature.KineticsExtraRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's TurntableBlock (MIT, The Create Team).
 * Players are turned by {@link TurntableHandler}; everything else standing on it is turned here.
 */
public class TurntableBlock extends KineticBlock implements IBE<TurntableBlockEntity> {
	public static final VoxelShape SHAPE = Shapes.or(ShapeUtil.box(1, 4, 1, 15, 8, 15), ShapeUtil.box(5, 0, 5, 11, 4, 11));

	// Invisible bottom slab: players see (and stand on) the same height as the real 8/16 high block.
	private static @Nullable BlockState clientState;

	public TurntableBlock(Properties properties) {
		super(properties);
	}

	public static void requestClientState() {
		clientState = PolymerBlockResourceUtils.requestEmpty(BlockModelType.SLAB_BOTTOM);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return clientState != null ? clientState : super.getPolymerBlockState(state, context);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity e, InsideBlockEffectApplier effectApplier,
								boolean isPrecise) {
		if (!e.onGround())
			return;
		if (e.getDeltaMovement().y > 0)
			return;
		if (e.getY() < pos.getY() + .5f)
			return;

		withBlockEntityDo(worldIn, pos, be -> {
			float speed = ((KineticBlockEntity) be).getSpeed() * 3 / 10;
			if (speed == 0)
				return;

			Level world = e.level();
			if ((e instanceof Player))
				return;
			if (world.isClientSide())
				return;

			if (e instanceof LivingEntity livingEntity) {
				float diff = e.getYHeadRot() - speed;
				livingEntity.setNoActionTime(20);
				e.setYBodyRot(diff);
				e.setYHeadRot(diff);
				e.setOnGround(false);
				e.needsSync = true;
			}

			e.setYRot(e.getYRot() - speed);
		});
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == Direction.DOWN;
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return Axis.Y;
	}

	@Override
	public Class<TurntableBlockEntity> getBlockEntityClass() {
		return TurntableBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends TurntableBlockEntity> getBlockEntityType() {
		return KineticsExtraRegistry.TURNTABLE_BE;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's SingleAxisRotatingVisual with the turntable model. */
	public static class Model extends BaseModel {
		private final Part head;

		public Model(BlockState state) {
			super(state);
			head = addPart(ModelItems.block("turntable"));
			head.spinAxis(Axis.Y).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof KineticBlockEntity kbe)
				head.spin(time, kbe.getSpeed(), Orientation.cogOffset(false, Axis.Y, pos.getX(), pos.getY(), pos.getZ()));
		}
	}

}

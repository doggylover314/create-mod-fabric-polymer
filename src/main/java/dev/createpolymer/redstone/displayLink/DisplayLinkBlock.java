package dev.createpolymer.redstone.displayLink;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

import dev.createpolymer.api.behaviour.display.DisplaySource;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.redstone.WrenchableDirectionalBlock;
import dev.createpolymer.redstone.displayLink.source.RedstonePowerDisplaySource;
import dev.createpolymer.registry.feature.DisplayRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DisplayLinkBlock extends WrenchableDirectionalBlock implements IBE<DisplayLinkBlockEntity>, PolymerModelBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	/** Create's AllShapes.DATA_GATHERER. */
	private static final Map<Direction, VoxelShape> SHAPES = ShapeUtil.forDirectional(
		Shapes.or(ShapeUtil.box(1, 0, 1, 15, 6, 15), ShapeUtil.box(3, 5, 3, 13, 9, 13)));

	public DisplayLinkBlock(Properties p_i48415_1_) {
		super(p_i48415_1_);
		registerDefaultState(defaultBlockState().setValue(POWERED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState placed = defaultBlockState();
		placed = placed.setValue(FACING, context.getClickedFace());
		return placed.setValue(POWERED, shouldBePowered(placed, context.getLevel(), context.getClickedPos()));
	}

	public static void notifyGatherers(LevelAccessor level, BlockPos pos) {
		forEachAttachedGatherer(level, pos, DisplayLinkBlockEntity::tickSource);
	}

	@SuppressWarnings("unchecked")
	public static <T extends DisplaySource> void sendToGatherers(LevelAccessor level, BlockPos pos,
		BiConsumer<DisplayLinkBlockEntity, T> callback, Class<T> type) {
		forEachAttachedGatherer(level, pos, dgte -> {
			if (type.isInstance(dgte.activeSource))
				callback.accept(dgte, (T) dgte.activeSource);
		});
	}

	private static void forEachAttachedGatherer(LevelAccessor level, BlockPos pos,
		Consumer<DisplayLinkBlockEntity> callback) {
		for (Direction d : Iterate.directions) {
			BlockPos offsetPos = pos.relative(d);
			BlockState blockState = level.getBlockState(offsetPos);
			if (!(blockState.getBlock() instanceof DisplayLinkBlock))
				continue;

			BlockEntity blockEntity = level.getBlockEntity(offsetPos);
			if (!(blockEntity instanceof DisplayLinkBlockEntity dlbe))
				continue;
			if (dlbe.activeSource == null)
				continue;
			if (dlbe.getDirection() != d.getOpposite())
				continue;

			callback.accept(dlbe);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
		@Nullable Orientation orientation, boolean isMoving) {
		if (worldIn.isClientSide())
			return;

		// Create only does this when the block on the attached side changed; 26.3 does not tell which neighbour it was
		sendToGatherers(worldIn, pos.relative(state.getValue(FACING)
			.getOpposite()), (dlte, p) -> dlte.tickSource(), RedstonePowerDisplaySource.class);

		boolean powered = shouldBePowered(state, worldIn, pos);
		boolean previouslyPowered = state.getValue(POWERED);
		if (previouslyPowered != powered) {
			worldIn.setBlock(pos, state.cycle(POWERED), Block.UPDATE_CLIENTS);
			if (!powered)
				withBlockEntityDo(worldIn, pos, DisplayLinkBlockEntity::onNoLongerPowered);
		}
	}

	private boolean shouldBePowered(BlockState state, Level worldIn, BlockPos pos) {
		boolean powered = false;
		for (Direction d : Iterate.directions) {
			if (d.getOpposite() == state.getValue(FACING))
				continue;
			if (worldIn.getSignal(pos.relative(d), d) == 0)
				continue;
			powered = true;
			break;
		}
		return powered;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(POWERED));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (player == null)
			return InteractionResult.PASS;
		if (player.isShiftKeyDown())
			return InteractionResult.PASS;
		if (!(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.SUCCESS;
		withBlockEntityDo(level, pos, be -> {
			if (be.targetOffset.equals(BlockPos.ZERO)) {
				player.sendOverlayMessage(Component.translatable("create.display_link.invalid"));
				return;
			}
			DisplayLinkGui.open(serverPlayer, be);
		});
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return SHAPES.get(pState.getValue(FACING));
	}

	@Override
	public Class<DisplayLinkBlockEntity> getBlockEntityClass() {
		return DisplayLinkBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends DisplayLinkBlockEntity> getBlockEntityType() {
		return DisplayRegistry.DISPLAY_LINK_BLOCK_ENTITY;
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

	/** The link and its bulb, which lights up for a moment whenever the link sends something (Create's LinkBulbRenderer). */
	public static class Model extends BaseModel {
		private final Part block;
		private final Part tube;
		private final Part glowPart;
		private int shownGlow = -1;
		private BlockState shownState;

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("display_link/block"));
			tube = addPart(ModelItems.block("display_link/tube"));
			glowPart = addPart(ModelItems.block("display_link/glow"));
			for (Part part : new Part[] { tube, glowPart })
				part.element.setBrightness(Brightness.FULL_BRIGHT);
			hide();
			showVariant(block, "display_link", state);
		}

		private void hide() {
			tube.setMatrix(new Matrix4f().scale(0), 0);
			glowPart.setMatrix(new Matrix4f().scale(0), 0);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "display_link", state);
			shownGlow = -1;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof DisplayLinkBlockEntity link))
				return;
			float glow = link.getGlow(1);
			// the bulb is only drawn while it glows; the glow part grows with the brightness, in steps of 1/8
			int step = glow < .125f ? 0 : 1 + Mth.clamp(Math.round(8 * (1 - (2 * (float) Math.pow(glow - .75f, 2)))), 0, 8);
			if (step == shownGlow && state == shownState)
				return;
			shownGlow = step;
			shownState = state;
			if (step == 0) {
				hide();
				return;
			}

			Direction face = link.getBulbFacing(state);
			Vec3 offset = link.getBulbOffset(state);
			Matrix4f base = new Matrix4f()
				.rotateY((float) Math.toRadians(AngleHelper.horizontalAngle(face) + 180))
				.rotateX((float) Math.toRadians(-AngleHelper.verticalAngle(face) - 90))
				.translate((float) offset.x, (float) offset.y, (float) offset.z);
			tube.setMatrix(new Matrix4f(base), 1);
			float size = .4f + .6f * (step - 1) / 8f;
			// the glow model is a bit larger than the tube, around the same centre
			Vec3 centre = new Vec3(0, 2.5 / 16, 0);
			glowPart.setMatrix(new Matrix4f(base)
				.translate((float) centre.x, (float) centre.y, (float) centre.z)
				.scale(size)
				.translate((float) -centre.x, (float) -centre.y, (float) -centre.z), 1);
		}
	}
}

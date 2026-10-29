package dev.createpolymer.fluids.tank;

import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;

import dev.createpolymer.api.connectivity.ConnectivityHandler;
import dev.createpolymer.fluids.FluidBox;
import dev.createpolymer.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank;
import dev.createpolymer.fluids.transfer.GenericItemEmptying;
import dev.createpolymer.fluids.transfer.GenericItemFilling;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.ComparatorUtil;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidHelper;
import dev.createpolymer.foundation.fluid.FluidHelper.FluidExchange;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.feature.TanksRegistry;
import dev.createpolymer.util.Lang;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FluidTankBlock extends Block implements IWrenchable, IBE<FluidTankBlockEntity>, PolymerModelBlock {

	public static final BooleanProperty TOP = BooleanProperty.create("top");
	public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
	public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
	/**
	 * Light emitted by the fluid inside. Create's tank asks its block entity for this; vanilla blocks only give light
	 * by their state, so the tank keeps it in a property.
	 */
	public static final IntegerProperty LUMINOSITY = IntegerProperty.create("luminosity", 0, 15);

	private boolean creative;

	public static FluidTankBlock regular(Properties p_i48440_1_) {
		return new FluidTankBlock(p_i48440_1_, false);
	}

	public static FluidTankBlock creative(Properties p_i48440_1_) {
		return new FluidTankBlock(p_i48440_1_, true);
	}

	protected FluidTankBlock(Properties p_i48440_1_, boolean creative) {
		super(p_i48440_1_);
		this.creative = creative;
		registerDefaultState(defaultBlockState().setValue(TOP, true)
			.setValue(BOTTOM, true)
			.setValue(SHAPE, Shape.WINDOW)
			.setValue(LUMINOSITY, 0));
	}

	public boolean isCreative() {
		return creative;
	}

	public static boolean isTank(BlockState state) {
		return state.getBlock() instanceof FluidTankBlock;
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean moved) {
		if (oldState.getBlock() == state.getBlock())
			return;
		if (moved)
			return;
		withBlockEntityDo(world, pos, FluidTankBlockEntity::updateConnectivity);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> p_206840_1_) {
		p_206840_1_.add(TOP, BOTTOM, SHAPE, LUMINOSITY);
	}

	/** Create's getLightEmission(state, world, pos): only tanks with windows shine, see {@link FluidTankBlockEntity}. */
	public static int getLightEmission(BlockState state) {
		return state.getValue(LUMINOSITY);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		withBlockEntityDo(context.getLevel(), context.getClickedPos(), FluidTankBlockEntity::toggleWindows);
		return InteractionResult.SUCCESS;
	}

	static final VoxelShape CAMPFIRE_SMOKE_CLIP = Block.box(0, 4, 0, 16, 16, 16);

	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
										CollisionContext pContext) {
		if (pContext == CollisionContext.empty())
			return CAMPFIRE_SMOKE_CLIP;
		return pState.getShape(pLevel, pPos);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.block();
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState pState, BlockGetter pReader, BlockPos pPos) {
		return Shapes.block();
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pCurrentPos,
									 Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource random) {
		if (pDirection == Direction.DOWN && pNeighborState.getBlock() != this)
			withBlockEntityDo(level, pCurrentPos, FluidTankBlockEntity::updateBoilerTemperature);
		return pState;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hitResult) {
		boolean onClient = level.isClientSide();

		if (stack.isEmpty())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!player.isCreative() && !creative)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		FluidExchange exchange = null;
		FluidTankBlockEntity be = ConnectivityHandler.partAt(getBlockEntityType(), level, pos);
		if (be == null)
			return InteractionResult.FAIL;

		IFluidHandler tankCapability = FluidHandlerLookup.get(level, be.getBlockPos(), null);
		if (tankCapability == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		FluidStack prevFluidInTank = tankCapability.getFluidInTank(0)
			.copy();

		if (FluidHelper.tryEmptyItemIntoBE(level, player, hand, stack, be))
			exchange = FluidExchange.ITEM_TO_TANK;
		else if (FluidHelper.tryFillItemFromBE(level, player, hand, stack, be))
			exchange = FluidExchange.TANK_TO_ITEM;

		if (exchange == null) {
			if (GenericItemEmptying.canItemBeEmptied(level, stack)
				|| GenericItemFilling.canItemBeFilled(level, stack))
				return InteractionResult.SUCCESS;
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		SoundEvent soundevent = null;
		BlockState fluidState = null;
		FluidStack fluidInTank = tankCapability.getFluidInTank(0);

		if (exchange == FluidExchange.ITEM_TO_TANK) {
			if (creative && !onClient) {
				FluidStack fluidInItem = GenericItemEmptying.emptyItem(level, stack, true)
					.getFirst();
				if (!fluidInItem.isEmpty() && tankCapability instanceof CreativeSmartFluidTank)
					((CreativeSmartFluidTank) tankCapability).setContainedFluid(fluidInItem);
			}

			Fluid fluid = fluidInTank.getFluid();
			fluidState = fluid.defaultFluidState()
				.createLegacyBlock();
			soundevent = FluidHelper.getEmptySound(fluidInTank);
		}

		if (exchange == FluidExchange.TANK_TO_ITEM) {
			if (creative && !onClient)
				if (tankCapability instanceof CreativeSmartFluidTank)
					((CreativeSmartFluidTank) tankCapability).setContainedFluid(FluidStack.EMPTY);

			Fluid fluid = prevFluidInTank.getFluid();
			fluidState = fluid.defaultFluidState()
				.createLegacyBlock();
			soundevent = FluidHelper.getFillSound(prevFluidInTank);
		}

		if (soundevent != null && !onClient) {
			float pitch = Mth
				.clamp(1 - (1f * fluidInTank.getAmount() / (FluidTankBlockEntity.getCapacityMultiplier() * 16)), 0, 1);
			pitch /= 1.5f;
			pitch += .5f;
			pitch += (level.getRandom().nextFloat() - .5f) / 4f;
			level.playSound(null, pos, soundevent, SoundSource.BLOCKS, .5f, pitch);
		}

		if (!FluidStack.isSameFluidSameComponents(fluidInTank, prevFluidInTank)) {
			FluidTankBlockEntity controllerBE = be.getControllerBE();
			if (controllerBE != null) {
				if (fluidState != null && !fluidState.isAir() && level instanceof ServerLevel serverLevel) {
					BlockParticleOption blockParticleData = new BlockParticleOption(ParticleTypes.BLOCK, fluidState);
					float fluidLevel = (float) fluidInTank.getAmount() / tankCapability.getTankCapacity(0);

					boolean reversed = FluidHelper.isLighterThanAir(fluidInTank);
					if (reversed)
						fluidLevel = 1 - fluidLevel;

					Vec3 vec = hitResult.getLocation();
					vec = new Vec3(vec.x, controllerBE.getBlockPos()
						.getY() + fluidLevel * (controllerBE.getHeight() - .5f) + .25f, vec.z);
					Vec3 motion = player.position()
						.subtract(vec)
						.scale(1 / 20f);
					vec = vec.add(motion);
					serverLevel.sendParticles(blockParticleData, vec.x, vec.y, vec.z, 0, motion.x, motion.y, motion.z, 1);
				}

				controllerBE.sendDataImmediately();
				controllerBE.setChanged();
			}
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public Class<FluidTankBlockEntity> getBlockEntityClass() {
		return FluidTankBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() {
		return creative ? TanksRegistry.CREATIVE_FLUID_TANK_BLOCK_ENTITY : TanksRegistry.FLUID_TANK_BLOCK_ENTITY;
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		if (mirror == Mirror.NONE)
			return state;
		boolean x = mirror == Mirror.FRONT_BACK;
		switch (state.getValue(SHAPE)) {
			case WINDOW_NE:
				return state.setValue(SHAPE, x ? Shape.WINDOW_NW : Shape.WINDOW_SE);
			case WINDOW_NW:
				return state.setValue(SHAPE, x ? Shape.WINDOW_NE : Shape.WINDOW_SW);
			case WINDOW_SE:
				return state.setValue(SHAPE, x ? Shape.WINDOW_SW : Shape.WINDOW_NE);
			case WINDOW_SW:
				return state.setValue(SHAPE, x ? Shape.WINDOW_SE : Shape.WINDOW_NW);
			default:
				return state;
		}
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		for (int i = 0; i < rotation.ordinal(); i++)
			state = rotateOnce(state);
		return state;
	}

	private BlockState rotateOnce(BlockState state) {
		switch (state.getValue(SHAPE)) {
			case WINDOW_NE:
				return state.setValue(SHAPE, Shape.WINDOW_SE);
			case WINDOW_NW:
				return state.setValue(SHAPE, Shape.WINDOW_NE);
			case WINDOW_SE:
				return state.setValue(SHAPE, Shape.WINDOW_SW);
			case WINDOW_SW:
				return state.setValue(SHAPE, Shape.WINDOW_NW);
			default:
				return state;
		}
	}

	public enum Shape implements StringRepresentable {
		PLAIN, WINDOW, WINDOW_NW, WINDOW_SW, WINDOW_NE, WINDOW_SE;

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}
	}

	// Tanks are less noisy when placed in batch
	public static final SoundType SILENCED_METAL =
		new SoundType(0.1F, 1.5F, SoundEvents.METAL_BREAK, SoundEvents.METAL_STEP,
			SoundEvents.METAL_PLACE, SoundEvents.METAL_HIT, SoundEvents.METAL_FALL);

	/** Set by {@link FluidTankItem} while it places the rest of a layer (Create flags the player's persistent data). */
	static boolean silenceTankSound = false;

	@Override
	protected SoundType getSoundType(BlockState state) {
		if (silenceTankSound)
			return SILENCED_METAL;
		return super.getSoundType(state);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level worldIn, BlockPos pos, Direction direction) {
		return getBlockEntityOptional(worldIn, pos).map(FluidTankBlockEntity::getControllerBE)
			.map(be -> ComparatorUtil.fractionToRedstoneLevel(be.getFillState()))
			.orElse(0);
	}

	public static void updateBoilerState(BlockState pState, Level pLevel, BlockPos tankPos) {
		BlockState tankState = pLevel.getBlockState(tankPos);
		if (!(tankState.getBlock() instanceof FluidTankBlock tank))
			return;
		FluidTankBlockEntity tankBE = tank.getBlockEntity(pLevel, tankPos);
		if (tankBE == null)
			return;
		FluidTankBlockEntity controllerBE = tankBE.getControllerBE();
		if (controllerBE == null)
			return;
		controllerBE.updateBoilerState();
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return creative ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, creative);
	}

	/**
	 * Vanilla clients cannot do Create's connected textures, so each tank block shows one model chosen by where it
	 * sits in its multiblock: {@code fluid_tank/polymer_<y><window>_<bits>} (or {@code creative_fluid_tank/...}),
	 * generated by tools/asset-scripts/make_tank_models.py. They have the connected-texture tile of every face baked
	 * in, and the walls between neighbouring tank blocks left out like Create's FluidTankModel does.
	 * The fluid is drawn by the controller as one box for the whole tank.
	 */
	public static class Model extends BaseModel {
		private static final float CAP_HEIGHT = 1 / 4f;
		private static final float TANK_HULL_WIDTH = 1 / 16f + 1 / 128f;
		private static final float MIN_PUDDLE_HEIGHT = 1 / 16f;

		private final boolean creative;
		private final Part shell;
		private final FluidBox fluid;
		private String shown = "";
		private int shownKey = -1;

		public Model(BlockState state, boolean creative) {
			super(state);
			this.creative = creative;
			shell = addPart(ModelItems.block(modelName(creative, 1, 1, 0, 0, 0, true)));
			shell.apply(0);
			fluid = new FluidBox(this);
			shown = modelName(creative, 1, 1, 0, 0, 0, true);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof FluidTankBlockEntity tank))
				return;
			FluidTankBlockEntity controller = tank.getControllerBE();
			if (controller == null)
				controller = tank;
			BlockPos origin = controller.getBlockPos();
			int width = controller.getWidth(), height = controller.getHeight();
			int dx = pos.getX() - origin.getX(), dy = pos.getY() - origin.getY(), dz = pos.getZ() - origin.getZ();
			boolean window = controller.hasWindow();
			// the name only changes when one of these does
			int key = (dx & 3) | (dz & 3) << 2 | (dy & 63) << 4 | (width & 3) << 10 | (height & 63) << 12 | (window ? 1 << 18 : 0);
			if (key != shownKey) {
				shownKey = key;
				String name = modelName(creative, width, height, dx, dy, dz, window);
				if (!name.equals(shown)) {
					shown = name;
					shell.setItem(ModelItems.block(name));
				}
			}
			if (tank == controller)
				updateFluid(controller);
			else
				fluid.hide();
		}

		/** Create's FluidTankRenderer.renderSafe: the fluid is a box inside the tank, drawn by the controller. */
		private void updateFluid(FluidTankBlockEntity tank) {
			if (!tank.hasWindow() || tank.boiler.isActive() || tank.getFluidLevel() == null) {
				fluid.hide();
				return;
			}
			float width = tank.getWidth();
			float totalHeight = tank.getHeight() - 2 * CAP_HEIGHT - MIN_PUDDLE_HEIGHT;
			float level = tank.getFluidLevel().getValue();
			FluidStack fluidStack = tank.getTankInventory().getFluid();
			if (fluidStack.isEmpty() || level < 1 / (512f * totalHeight)) {
				fluid.hide();
				return;
			}
			float clampedLevel = Mth.clamp(level * totalHeight, 0, totalHeight);
			boolean top = FluidHelper.isLighterThanAir(fluidStack);

			float yMin = CAP_HEIGHT + MIN_PUDDLE_HEIGHT;
			if (top)
				yMin += totalHeight - clampedLevel;

			// block-centred coordinates of the controller block
			fluid.show(fluidStack, TANK_HULL_WIDTH - .5f, yMin - .5f, TANK_HULL_WIDTH - .5f,
				width - TANK_HULL_WIDTH - .5f, yMin + clampedLevel - .5f, width - TANK_HULL_WIDTH - .5f);
		}

		/**
		 * Model path (under models/block) of the tank block at offset (dx, dy, dz) from the controller of a
		 * multiblock that is {@code width} wide and {@code height} tall. Same names as the generator script.
		 */
		public static String modelName(boolean creative, int width, int height, int dx, int dy, int dz, boolean window) {
			if (width < 1 || width > 3 || height < 1 || dx < 0 || dx >= width || dz < 0 || dz >= width || dy < 0
				|| dy >= height) {
				width = 1;
				height = 1;
				dx = dy = dz = 0;
			}
			boolean yLow = dy > 0, yHigh = dy < height - 1;
			String y = yLow ? (yHigh ? "middle" : "top") : (yHigh ? "bottom" : "single");

			String shape = "";
			if (window) {
				// SIZE 1: Every tank has a window
				if (width == 1)
					shape = "_window";
				// SIZE 2: Every tank has a corner window
				if (width == 2)
					shape = dx == 0 ? dz == 0 ? "_window_nw" : "_window_sw" : dz == 0 ? "_window_ne" : "_window_se";
				// SIZE 3: Tanks in the center have a window
				if (width == 3 && Math.abs(dx - dz) == 1)
					shape = "_window";
			}

			return (creative ? "creative_fluid_tank" : "fluid_tank") + "/polymer_" + y + shape + "_" + bit(dx > 0)
				+ bit(dx < width - 1) + bit(dz > 0) + bit(dz < width - 1);
		}

		private static char bit(boolean b) {
			return b ? '1' : '0';
		}
	}
}

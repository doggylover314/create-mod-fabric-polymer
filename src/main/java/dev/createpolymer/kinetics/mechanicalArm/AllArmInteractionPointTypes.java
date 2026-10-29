package dev.createpolymer.kinetics.mechanicalArm;

import java.util.List;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.registry.feature.EjectorRegistry;
import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.foundation.item.handler.StorageItemHandler;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.belt.BeltBlockEntity;
import dev.createpolymer.kinetics.belt.BeltBlock;
import dev.createpolymer.kinetics.belt.BeltHelper;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import dev.createpolymer.kinetics.crafter.MechanicalCrafterBlock;
import dev.createpolymer.kinetics.crafter.MechanicalCrafterBlockEntity;
import dev.createpolymer.kinetics.deployer.DeployerBlock;
import dev.createpolymer.kinetics.saw.SawBlock;
import dev.createpolymer.logistics.chute.AbstractChuteBlock;
import dev.createpolymer.logistics.funnel.AbstractFunnelBlock;
import dev.createpolymer.logistics.funnel.BeltFunnelBlock;
import dev.createpolymer.logistics.funnel.BeltFunnelBlock.Shape;
import dev.createpolymer.logistics.funnel.FunnelBlock;
import dev.createpolymer.logistics.funnel.FunnelBlockEntity;
import dev.createpolymer.logistics.tunnel.BeltTunnelBlock;
import dev.createpolymer.processing.basin.BasinBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.TryInsertResult;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.registry.feature.BeltRegistry;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.registry.feature.CrafterRegistry;
import dev.createpolymer.registry.feature.CrushingRegistry;
import dev.createpolymer.registry.feature.DeployerRegistry;
import dev.createpolymer.registry.feature.SawRegistry;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's AllArmInteractionPointTypes (MIT, The Create Team).
 * <p>
 * Not ported yet: the packager type and the track station that Create's depot type also accepts (neither block
 * exists here).
 */
public class AllArmInteractionPointTypes {
	private static boolean initialized;

	public static BasinType BASIN;
	public static BeltType BELT;
	public static BlazeBurnerType BLAZE_BURNER;
	public static ChuteType CHUTE;
	public static CrafterType CRAFTER;
	public static CrushingWheelsType CRUSHING_WHEELS;
	public static DeployerType DEPLOYER;
	public static DepotType DEPOT;
	public static FunnelType FUNNEL;
	public static MillstoneType MILLSTONE;
	public static SawType SAW;
	public static CampfireType CAMPFIRE;
	public static ComposterType COMPOSTER;
	public static JukeboxType JUKEBOX;
	public static RespawnAnchorType RESPAWN_ANCHOR;
	public static InventoryType INVENTORY;

	/** Registers all types; called from ArmRegistry.init(). */
	public static void init() {
		if (initialized)
			return;
		initialized = true;

		BASIN = register("basin", new BasinType());
		BELT = register("belt", new BeltType());
		BLAZE_BURNER = register("blaze_burner", new BlazeBurnerType());
		CHUTE = register("chute", new ChuteType());
		CRAFTER = register("crafter", new CrafterType());
		CRUSHING_WHEELS = register("crushing_wheels", new CrushingWheelsType());
		DEPLOYER = register("deployer", new DeployerType());
		DEPOT = register("depot", new DepotType());
		FUNNEL = register("funnel", new FunnelType());
		MILLSTONE = register("millstone", new MillstoneType());
		SAW = register("saw", new SawType());

		CAMPFIRE = register("campfire", new CampfireType());
		COMPOSTER = register("composter", new ComposterType());
		JUKEBOX = register("jukebox", new JukeboxType());
		RESPAWN_ANCHOR = register("respawn_anchor", new RespawnAnchorType());

		INVENTORY = register("inventory", new InventoryType());
	}

	private static <T extends ArmInteractionPointType> T register(String name, T type) {
		return ArmInteractionPointType.register(CreatePolymer.id(name), type);
	}

	//

	public static class BasinType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return BasinBlock.isBasin(level, pos);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new ArmInteractionPoint(this, level, pos, state);
		}
	}

	public static class BeltType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.is(BeltRegistry.BELT) && !(level.getBlockState(pos.above())
				.getBlock() instanceof BeltTunnelBlock) && BeltBlock.canTransportObjects(state);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new BeltPoint(this, level, pos, state);
		}
	}

	public static class BlazeBurnerType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.is(BurnerRegistry.BLAZE_BURNER);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new BlazeBurnerPoint(this, level, pos, state);
		}
	}

	public static class ChuteType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return AbstractChuteBlock.isChute(state);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new TopFaceArmInteractionPoint(this, level, pos, state);
		}
	}

	public static class CrafterType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.is(CrafterRegistry.MECHANICAL_CRAFTER);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new CrafterPoint(this, level, pos, state);
		}
	}

	public static class CrushingWheelsType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.is(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new CrushingWheelPoint(this, level, pos, state);
		}
	}

	public static class DeployerType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.is(DeployerRegistry.DEPLOYER);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new DeployerPoint(this, level, pos, state);
		}
	}

	public static class DepotType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			// Create also accepts the track station (trains are not ported)
			return state.is(ModBlocks.DEPOT) || state.is(EjectorRegistry.WEIGHTED_EJECTOR);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new DepotPoint(this, level, pos, state);
		}
	}

	public static class FunnelType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.getBlock() instanceof AbstractFunnelBlock
				&& !(state.hasProperty(FunnelBlock.EXTRACTING) && state.getValue(FunnelBlock.EXTRACTING))
				&& !(state.hasProperty(BeltFunnelBlock.SHAPE)
				&& state.getValue(BeltFunnelBlock.SHAPE) == Shape.PUSHING);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new FunnelPoint(this, level, pos, state);
		}
	}

	public static class MillstoneType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.is(ModBlocks.MILLSTONE);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new ArmInteractionPoint(this, level, pos, state);
		}
	}

	public static class SawType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.is(SawRegistry.MECHANICAL_SAW) && state.getValue(SawBlock.FACING) == Direction.UP
				&& level.getBlockEntity(pos) instanceof KineticBlockEntity kbe && kbe.getSpeed() != 0;
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new DepotPoint(this, level, pos, state);
		}
	}

	public static class CampfireType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.getBlock() instanceof CampfireBlock;
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new CampfirePoint(this, level, pos, state);
		}
	}

	public static class ComposterType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.is(Blocks.COMPOSTER);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new ComposterPoint(this, level, pos, state);
		}
	}

	public static class JukeboxType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.is(Blocks.JUKEBOX);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new JukeboxPoint(this, level, pos, state);
		}
	}

	public static class RespawnAnchorType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return state.is(Blocks.RESPAWN_ANCHOR);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new RespawnAnchorPoint(this, level, pos, state);
		}
	}

	/**
	 * Any other block with an item handler (chests, barrels, vaults, hoppers, other mods' storages), reached from
	 * above like Create's cached handler does. Lowest priority, so every specific type wins.
	 */
	public static class InventoryType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return ItemHandlerLookup.get(level, pos, Direction.UP) != null;
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new ArmInteractionPoint(this, level, pos, state);
		}

		@Override
		public int getPriority() {
			return -100;
		}
	}

	//

	public static class DepositOnlyArmInteractionPoint extends ArmInteractionPoint {
		public DepositOnlyArmInteractionPoint(ArmInteractionPointType type, Level level, BlockPos pos,
											  BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		public void cycleMode() {
		}

		@Override
		public ItemStack extract(ArmBlockEntity armBlockEntity, int slot, int amount, boolean simulate) {
			return ItemStack.EMPTY;
		}

		@Override
		public int getSlotCount(ArmBlockEntity armBlockEntity) {
			return 0;
		}
	}

	public static class TopFaceArmInteractionPoint extends ArmInteractionPoint {
		public TopFaceArmInteractionPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		protected Vec3 getInteractionPositionVector() {
			return Vec3.atLowerCornerOf(pos)
				.add(.5f, 1, .5f);
		}
	}

	public static class BeltPoint extends DepotPoint {
		public BeltPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		public void keepAlive() {
			super.keepAlive();
			BeltBlockEntity beltBE = BeltHelper.getSegmentBE(level, pos);
			if (beltBE == null)
				return;
			TransportedItemStackHandlerBehaviour transport =
				beltBE.getBehaviour(TransportedItemStackHandlerBehaviour.TYPE);
			if (transport == null)
				return;
			MutableBoolean found = new MutableBoolean(false);
			transport.handleProcessingOnAllItems(tis -> {
				if (found.isTrue())
					return TransportedResult.doNothing();
				tis.lockedExternally = true;
				found.setTrue();
				return TransportedResult.doNothing();
			});
		}
	}

	public static class BlazeBurnerPoint extends DepositOnlyArmInteractionPoint {
		public BlazeBurnerPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		public ItemStack insert(ArmBlockEntity armBlockEntity, ItemStack stack, boolean simulate) {
			ItemStack input = stack.copy();
			TryInsertResult res =
				BlazeBurnerBlock.tryInsert(cachedState, level, pos, input, false, false, simulate);
			ItemStack remainder = res.object();
			if (input.isEmpty()) {
				return remainder;
			} else {
				if (!simulate)
					Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
				return input;
			}
		}
	}

	public static class CrafterPoint extends ArmInteractionPoint {
		public CrafterPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		protected Direction getInteractionDirection() {
			return cachedState.getOptionalValue(MechanicalCrafterBlock.HORIZONTAL_FACING)
				.orElse(Direction.SOUTH)
				.getOpposite();
		}

		@Override
		protected Vec3 getInteractionPositionVector() {
			return super.getInteractionPositionVector().add(Vec3.atLowerCornerOf(getInteractionDirection().getUnitVec3i())
				.scale(.5f));
		}

		@Override
		public void updateCachedState() {
			BlockState oldState = cachedState;
			super.updateCachedState();
			if (oldState != cachedState)
				cachedAngles = null;
		}

		/**
		 * Create lets the crafter's SmartInventory be emptied (allowExtraction) just for this call. Our crafter
		 * inventory never gives items out through its handler, so the stack is taken from it directly. The
		 * connected crafters' handler has one slot per crafter, in the order of ConnectedInput.getInventories,
		 * and only this crafter's own slot is open.
		 */
		@Override
		public ItemStack extract(ArmBlockEntity armBlockEntity, int slot, int amount, boolean simulate) {
			BlockEntity be = level.getBlockEntity(pos);
			if (!(be instanceof MechanicalCrafterBlockEntity crafter))
				return ItemStack.EMPTY;
			List<MechanicalCrafterBlockEntity.Inventory> inventories = crafter.getInput()
				.getInventories(level, pos);
			if (slot < 0 || slot >= inventories.size() || inventories.get(slot) != crafter.getInventory())
				return ItemStack.EMPTY;
			MechanicalCrafterBlockEntity.Inventory inventory = crafter.getInventory();
			ItemStack stack = inventory.getStackInSlot(0);
			if (stack.isEmpty() || amount <= 0)
				return ItemStack.EMPTY;
			ItemStack extracted = stack.copyWithCount(Math.min(amount, stack.getCount()));
			if (!simulate) {
				ItemStack remainder = stack.copyWithCount(stack.getCount() - extracted.getCount());
				inventory.setStackInSlot(0, remainder);
			}
			return extracted;
		}
	}

	public static class DeployerPoint extends ArmInteractionPoint {
		public DeployerPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		protected Direction getInteractionDirection() {
			return cachedState.getOptionalValue(DeployerBlock.FACING)
				.orElse(Direction.UP)
				.getOpposite();
		}

		@Override
		protected Vec3 getInteractionPositionVector() {
			return super.getInteractionPositionVector().add(Vec3.atLowerCornerOf(getInteractionDirection().getUnitVec3i())
				.scale(.65f));
		}

		@Override
		public void updateCachedState() {
			BlockState oldState = cachedState;
			super.updateCachedState();
			if (oldState != cachedState)
				cachedAngles = null;
		}
	}

	public static class DepotPoint extends ArmInteractionPoint {
		public DepotPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		protected Vec3 getInteractionPositionVector() {
			return Vec3.atLowerCornerOf(pos)
				.add(.5f, 14 / 16f, .5f);
		}
	}

	public static class FunnelPoint extends DepositOnlyArmInteractionPoint {
		public FunnelPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		protected Vec3 getInteractionPositionVector() {
			Direction funnelFacing = FunnelBlock.getFunnelFacing(cachedState);
			Vec3i normal = funnelFacing != null ? funnelFacing.getUnitVec3i() : Vec3i.ZERO;
			return VecHelper.getCenterOf(pos)
				.add(Vec3.atLowerCornerOf(normal)
					.scale(-.15f));
		}

		@Override
		protected Direction getInteractionDirection() {
			Direction funnelFacing = FunnelBlock.getFunnelFacing(cachedState);
			return funnelFacing != null ? funnelFacing.getOpposite() : Direction.UP;
		}

		@Override
		public void updateCachedState() {
			BlockState oldState = cachedState;
			super.updateCachedState();
			if (oldState != cachedState)
				cachedAngles = null;
		}

		@Override
		public ItemStack insert(ArmBlockEntity armBlockEntity, ItemStack stack, boolean simulate) {
			FilteringBehaviour filtering = BlockEntityBehaviour.get(level, pos, FilteringBehaviour.TYPE);
			InvManipulationBehaviour inserter = BlockEntityBehaviour.get(level, pos, InvManipulationBehaviour.TYPE);
			if (cachedState.getOptionalValue(BlockStateProperties.POWERED)
				.orElse(false))
				return stack;
			if (inserter == null)
				return stack;
			if (filtering != null && !filtering.test(stack))
				return stack;
			if (simulate)
				inserter.simulate();
			ItemStack insert = inserter.insert(stack);
			if (!simulate && insert.getCount() != stack.getCount()) {
				BlockEntity blockEntity = level.getBlockEntity(pos);
				if (blockEntity instanceof FunnelBlockEntity funnelBlockEntity) {
					funnelBlockEntity.onTransfer(stack);
					if (funnelBlockEntity.hasFlap())
						funnelBlockEntity.flap(true);
				}
			}
			return insert;
		}
	}

	public static class CampfirePoint extends DepositOnlyArmInteractionPoint {
		public CampfirePoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		public ItemStack insert(ArmBlockEntity armBlockEntity, ItemStack stack, boolean simulate) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (!(blockEntity instanceof CampfireBlockEntity campfireBE) || !(level instanceof ServerLevel serverLevel))
				return stack;
			if (serverLevel.recipeAccess()
				.getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(stack), serverLevel)
				.isEmpty())
				return stack;
			if (simulate) {
				boolean hasSpace = false;
				for (ItemStack campfireStack : campfireBE.getItems()) {
					if (campfireStack.isEmpty()) {
						hasSpace = true;
						break;
					}
				}
				if (!hasSpace)
					return stack;
				ItemStack remainder = stack.copy();
				remainder.shrink(1);
				return remainder;
			}
			ItemStack remainder = stack.copy();
			// takes one item from the stack it is given and starts cooking it
			campfireBE.placeFood(serverLevel, null, remainder);
			return remainder;
		}
	}

	public static class ComposterPoint extends ArmInteractionPoint {
		public ComposterPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		protected Vec3 getInteractionPositionVector() {
			return Vec3.atLowerCornerOf(pos)
				.add(.5f, 13 / 16f, .5f);
		}

		@Nullable
		@Override
		protected IItemHandler getHandler(ArmBlockEntity armBlockEntity) {
			return null;
		}

		protected WorldlyContainer getContainer() {
			ComposterBlock composterBlock = (ComposterBlock) Blocks.COMPOSTER;
			return composterBlock.getContainer(cachedState, level, pos);
		}

		private IItemHandler handlerFor(Direction side) {
			return new StorageItemHandler(ContainerStorage.of(getContainer(), side));
		}

		@Override
		public ItemStack insert(ArmBlockEntity armBlockEntity, ItemStack stack, boolean simulate) {
			return ItemHandlerHelper.insertItem(handlerFor(Direction.UP), stack, simulate);
		}

		@Override
		public ItemStack extract(ArmBlockEntity armBlockEntity, int slot, int amount, boolean simulate) {
			return handlerFor(Direction.DOWN).extractItem(slot, amount, simulate);
		}

		@Override
		public int getSlotCount(ArmBlockEntity armBlockEntity) {
			return 2;
		}
	}

	public static class JukeboxPoint extends TopFaceArmInteractionPoint {
		public JukeboxPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		public int getSlotCount(ArmBlockEntity armBlockEntity) {
			return 1;
		}

		@Override
		public ItemStack insert(ArmBlockEntity armBlockEntity, ItemStack stack, boolean simulate) {
			if (stack.get(DataComponents.JUKEBOX_PLAYABLE) == null)
				return stack;
			if (cachedState.getOptionalValue(JukeboxBlock.HAS_RECORD).orElse(true))
				return stack;
			if (!(level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukeboxBE))
				return stack;
			if (!jukeboxBE.getTheItem().isEmpty())
				return stack;
			ItemStack remainder = stack.copy();
			ItemStack toInsert = remainder.split(1);
			if (!simulate)
				jukeboxBE.setTheItem(toInsert);
			return remainder;
		}

		@Override
		public ItemStack extract(ArmBlockEntity armBlockEntity, int slot, int amount, boolean simulate) {
			if (!cachedState.getOptionalValue(JukeboxBlock.HAS_RECORD).orElse(false))
				return ItemStack.EMPTY;
			if (!(level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukeboxBE))
				return ItemStack.EMPTY;
			if (!simulate)
				return jukeboxBE.removeItem(slot, amount);
			return jukeboxBE.getTheItem();
		}
	}

	public static class RespawnAnchorPoint extends DepositOnlyArmInteractionPoint {
		public RespawnAnchorPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		protected Vec3 getInteractionPositionVector() {
			return Vec3.atLowerCornerOf(pos)
				.add(.5f, 1, .5f);
		}

		@Override
		public ItemStack insert(ArmBlockEntity armBlockEntity, ItemStack stack, boolean simulate) {
			if (!stack.is(Items.GLOWSTONE))
				return stack;
			if (cachedState.getOptionalValue(RespawnAnchorBlock.CHARGE)
				.orElse(4) == 4)
				return stack;
			if (!simulate)
				RespawnAnchorBlock.charge(null, level, pos, cachedState);
			ItemStack remainder = stack.copy();
			remainder.shrink(1);
			return remainder;
		}
	}

	public static class CrushingWheelPoint extends DepositOnlyArmInteractionPoint {
		public CrushingWheelPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
			super(type, level, pos, state);
		}

		@Override
		protected Vec3 getInteractionPositionVector() {
			return Vec3.atLowerCornerOf(pos)
				.add(.5f, 1, .5f);
		}
	}
}

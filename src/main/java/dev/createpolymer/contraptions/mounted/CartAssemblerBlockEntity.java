package dev.createpolymer.contraptions.mounted;

import java.util.List;
import java.util.UUID;

import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.IDisplayAssemblyExceptions;
import dev.createpolymer.contraptions.OrientedContraptionEntity;
import dev.createpolymer.contraptions.minecart.CouplingHandler;
import dev.createpolymer.contraptions.minecart.MinecartSim2020;
import dev.createpolymer.contraptions.minecart.capability.MinecartController;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.createpolymer.redstone.rail.ControllerRailBlock;
import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class CartAssemblerBlockEntity extends SmartBlockEntity implements IDisplayAssemblyExceptions {
	private static final int assemblyCooldown = 8;

	protected ScrollOptionBehaviour<CartMovementMode> movementMode;
	private int ticksSinceMinecartUpdate;
	protected AssemblyException lastException;
	protected AbstractMinecart cartToAssemble;

	public CartAssemblerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		ticksSinceMinecartUpdate = assemblyCooldown;
	}

	@Override
	public void tick() {
		super.tick();
		if (ticksSinceMinecartUpdate < assemblyCooldown) {
			ticksSinceMinecartUpdate++;
		}

		tryAssemble(cartToAssemble);
		cartToAssemble = null;
	}

	public void tryAssemble(AbstractMinecart cart) {
		if (cart == null)
			return;

		if (!isMinecartUpdateValid())
			return;
		resetTicksSinceMinecartUpdate();

		BlockState state = level.getBlockState(worldPosition);
		if (!state.is(MinecartRegistry.CART_ASSEMBLER))
			return;
		CartAssemblerBlock block = (CartAssemblerBlock) state.getBlock();

		CartAssemblerBlock.CartAssemblerAction action = CartAssemblerBlock.getActionForCart(state, cart);
		if (action.shouldAssemble())
			assemble(level, worldPosition, cart);
		if (action.shouldDisassemble())
			disassemble(level, worldPosition, cart);
		if (action == CartAssemblerBlock.CartAssemblerAction.ASSEMBLE_ACCELERATE) {
			if (cart.getDeltaMovement()
				.length() > 1 / 128f) {
				Direction facing = cart.getMotionDirection();
				RailShape railShape = state.getValue(CartAssemblerBlock.RAIL_SHAPE);
				for (Direction d : Iterate.directionsInAxis(railShape == RailShape.EAST_WEST ? Axis.X : Axis.Z))
					if (level.getBlockState(worldPosition.relative(d))
						.isRedstoneConductor(level, worldPosition.relative(d)))
						facing = d.getOpposite();

				float speed = MinecartSim2020.RAIL_MAX_SPEED;
				cart.setDeltaMovement(facing.getStepX() * speed, facing.getStepY() * speed, facing.getStepZ() * speed);
			}
		}
		if (action == CartAssemblerBlock.CartAssemblerAction.ASSEMBLE_ACCELERATE_DIRECTIONAL) {
			Vec3i accelerationVector =
				ControllerRailBlock.getAccelerationVector(MinecartRegistry.CONTROLLER_RAIL.defaultBlockState()
					.setValue(ControllerRailBlock.SHAPE, state.getValue(CartAssemblerBlock.RAIL_SHAPE))
					.setValue(ControllerRailBlock.BACKWARDS, state.getValue(CartAssemblerBlock.BACKWARDS)));
			float speed = MinecartSim2020.RAIL_MAX_SPEED;
			cart.setDeltaMovement(Vec3.atLowerCornerOf(accelerationVector)
				.scale(speed));
		}
		if (action == CartAssemblerBlock.CartAssemblerAction.DISASSEMBLE_BRAKE) {
			Vec3 diff = VecHelper.getCenterOf(worldPosition)
				.subtract(cart.position());
			cart.setDeltaMovement(diff.x / 16f, 0, diff.z / 16f);
		}
	}

	protected void assemble(Level world, BlockPos pos, AbstractMinecart cart) {
		if (!cart.getPassengers()
			.isEmpty())
			return;

		MinecartController controller = MinecartController.get(cart);
		if (controller != MinecartController.EMPTY && controller.isCoupledThroughContraption())
			return;

		CartMovementMode mode = CartMovementMode.values()[movementMode.value];

		MountedContraption contraption = new MountedContraption(mode);
		try {
			if (!contraption.assemble(world, pos))
				return;

			lastException = null;
			sendData();
		} catch (AssemblyException e) {
			lastException = e;
			sendData();
			return;
		}

		boolean couplingFound = contraption.connectedCart != null;
		Direction initialOrientation = CartAssemblerBlock.getHorizontalDirection(getBlockState());

		if (couplingFound) {
			cart.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
			if (!CouplingHandler.tryToCoupleCarts(null, world, cart.getId(), contraption.connectedCart.getId()))
				return;
		}

		contraption.removeBlocksFromWorld(world, BlockPos.ZERO);
		contraption.startMoving(world);
		contraption.expandBoundsAroundAxis(Axis.Y);

		if (couplingFound) {
			Vec3 diff = contraption.connectedCart.position()
				.subtract(cart.position());
			initialOrientation = Direction.fromYRot(Mth.atan2(diff.z, diff.x) * 180 / Math.PI);
		}

		OrientedContraptionEntity entity = OrientedContraptionEntity.create(world, contraption, initialOrientation);
		if (couplingFound)
			entity.setCouplingId(cart.getUUID());
		entity.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
		world.addFreshEntity(entity);
		entity.startRiding(cart);

		if (cart instanceof MinecartFurnace furnace) {
			furnace.push = Vec3.ZERO;
		}
	}

	protected void disassemble(Level world, BlockPos pos, AbstractMinecart cart) {
		if (cart.getPassengers()
			.isEmpty())
			return;
		Entity entity = cart.getPassengers()
			.get(0);
		if (!(entity instanceof OrientedContraptionEntity contraption))
			return;
		UUID couplingId = contraption.getCouplingId();

		if (couplingId == null) {
			contraption.yaw = CartAssemblerBlock.getHorizontalDirection(getBlockState())
				.toYRot();
			disassembleCart(cart);
			return;
		}

		Couple<MinecartController> coupledCarts = contraption.getCoupledCartsIfPresent();
		if (coupledCarts == null)
			return;

		// Make sure connected cart is present and being disassembled
		for (boolean current : Iterate.trueAndFalse) {
			MinecartController minecartController = coupledCarts.get(current);
			if (minecartController.cart() == cart)
				continue;
			BlockPos otherPos = minecartController.cart()
				.blockPosition();
			BlockState blockState = world.getBlockState(otherPos);
			if (!blockState.is(MinecartRegistry.CART_ASSEMBLER))
				return;
			if (!CartAssemblerBlock.getActionForCart(blockState, minecartController.cart())
				.shouldDisassemble())
				return;
		}

		for (boolean current : Iterate.trueAndFalse)
			coupledCarts.get(current)
				.removeConnection(current);
		disassembleCart(cart);
	}

	protected void disassembleCart(AbstractMinecart cart) {
		cart.ejectPassengers();
		if (cart instanceof MinecartFurnace furnace) {
			Vec3 motion = cart.getDeltaMovement();
			furnace.push = new Vec3(motion.z, 0, motion.x);
		}
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		movementMode = new ScrollOptionBehaviour<>(CartMovementMode.class,
			Component.translatable("create.contraptions.cart_movement_mode"), this, getMovementModeSlot()) {
			/**
			 * The value box floats beside the casing, outside of the thin rail a vanilla client aims at, so a click
			 * on one of the assembler's open sides counts.
			 */
			@Override
			public boolean testHit(Vec3 hit) {
				return true;
			}
		};
		behaviours.add(movementMode);
	}

	@Override
	public void write(ValueOutput compound) {
		AssemblyException.write(compound, lastException);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		lastException = AssemblyException.read(compound);
		super.read(compound);
	}

	@Override
	public AssemblyException getLastAssemblyException() {
		return lastException;
	}

	protected ValueBoxTransform getMovementModeSlot() {
		return new CartAssemblerValueBoxTransform();
	}

	private class CartAssemblerValueBoxTransform extends CenteredSideValueBoxTransform {

		public CartAssemblerValueBoxTransform() {
			super((state, d) -> {
				if (d.getAxis()
					.isVertical())
					return false;
				if (!state.hasProperty(CartAssemblerBlock.RAIL_SHAPE))
					return false;
				RailShape railShape = state.getValue(CartAssemblerBlock.RAIL_SHAPE);
				return (d.getAxis() == Axis.X) == (railShape == RailShape.NORTH_SOUTH);
			});
		}

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 7, 17.5);
		}

	}

	public enum CartMovementMode implements INamedIconOptions {

		ROTATE(Items.COMPASS),
		ROTATE_PAUSED(Items.CLOCK),
		ROTATION_LOCKED(Items.TRIPWIRE_HOOK),

		;

		private String translationKey;
		private Item icon;

		CartMovementMode(Item icon) {
			this.icon = icon;
			translationKey = "create.contraptions.cart_movement_mode." + Lang.asId(name());
		}

		@Override
		public Item getIconItem() {
			return icon;
		}

		@Override
		public String getTranslationKey() {
			return translationKey;
		}
	}

	public void resetTicksSinceMinecartUpdate() {
		ticksSinceMinecartUpdate = 0;
	}

	public void assembleNextTick(AbstractMinecart cart) {
		if (cartToAssemble == null)
			cartToAssemble = cart;
	}

	public boolean isMinecartUpdateValid() {
		return ticksSinceMinecartUpdate >= assemblyCooldown;
	}

}

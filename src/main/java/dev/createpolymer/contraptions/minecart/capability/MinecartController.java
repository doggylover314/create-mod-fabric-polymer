package dev.createpolymer.contraptions.minecart.capability;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.OrientedContraptionEntity;
import dev.createpolymer.contraptions.minecart.CouplingHandler;
import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Extended code for Minecarts, this allows for handling stalled carts and
 * coupled trains. Create attaches it to carts with a NeoForge attachment; here it is a persistent Fabric data
 * attachment ({@link MinecartRegistry#MINECART_CONTROLLER}) that {@link CapabilityMinecartController} binds to its
 * cart whenever the cart enters a level.
 */
public class MinecartController {
	public static final MinecartController EMPTY = new MinecartController.Empty();

	/** The saved form: the same four optional entries Create's attachment serializer wrote. */
	public static final Codec<MinecartController> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		StallData.CODEC.optionalFieldOf("InternalStallData")
			.forGetter(c -> c.stallData.get(true)),
		StallData.CODEC.optionalFieldOf("StallData")
			.forGetter(c -> c.stallData.get(false)),
		CouplingData.CODEC.optionalFieldOf("MainCoupling")
			.forGetter(c -> c.couplings.get(true)),
		CouplingData.CODEC.optionalFieldOf("ConnectedCoupling")
			.forGetter(c -> c.couplings.get(false)))
		.apply(instance, MinecartController::new));

	private boolean needsEntryRefresh;
	private WeakReference<AbstractMinecart> weakRef;

	/*
	 * Stall information, <Internal (waiting couplings), External (stalled
	 * contraptions)>
	 */
	private Couple<Optional<StallData>> stallData;

	/*
	 * Coupling information, <Main (helmed by this cart), Connected (handled by
	 * other cart)>
	 */
	private Couple<Optional<CouplingData>> couplings;

	public MinecartController(AbstractMinecart minecart) {
		weakRef = new WeakReference<>(minecart);
		stallData = Couple.create(Optional::empty);
		couplings = Couple.create(Optional::empty);
		needsEntryRefresh = true;
	}

	private MinecartController(Optional<StallData> internalStall, Optional<StallData> externalStall,
		Optional<CouplingData> main, Optional<CouplingData> connected) {
		weakRef = new WeakReference<>(null);
		stallData = Couple.create(internalStall, externalStall);
		couplings = Couple.create(main, connected);
		needsEntryRefresh = true;
	}

	/** The controller of a cart, or {@link #EMPTY} if the entity has none (Create: {@code getData(MINECART_CONTROLLER)}). */
	@NotNull
	public static MinecartController get(Entity entity) {
		MinecartController controller = entity.getAttached(MinecartRegistry.MINECART_CONTROLLER);
		return controller == null ? EMPTY : controller;
	}

	public final boolean isEmpty() {
		return getType() == Type.EMPTY;
	}

	@NotNull
	protected Type getType() {
		return Type.NORMAL;
	}

	/** Called when a cart enters a level: a controller read from a save has no cart yet. */
	void bind(AbstractMinecart minecart) {
		weakRef = new WeakReference<>(minecart);
		needsEntryRefresh = true;
	}

	public void tick() {
		AbstractMinecart cart = cart();
		Level world = getWorld();

		if (cart == null || world == null)
			return;

		if (needsEntryRefresh) {
			CapabilityMinecartController.queuedAdditions.get(world)
				.add(cart);
			needsEntryRefresh = false;
		}

		stallData.forEach(opt -> opt.ifPresent(sd -> sd.tick(cart)));

		MutableBoolean internalStall = new MutableBoolean(false);
		couplings.forEachWithContext((opt, main) -> opt.ifPresent(cd -> {

			UUID idOfOther = cd.idOfCart(!main);
			MinecartController otherCart = CapabilityMinecartController.getIfPresent(world, idOfOther);
			internalStall.setValue(internalStall.booleanValue() || otherCart == null || !otherCart.isPresent()
				|| otherCart.isStalled(false));

		}));
		if (!world.isClientSide()) {
			setStalled(internalStall.booleanValue(), true);
			disassemble(cart);
			ejectPassengersOfContraptionCarriages(cart);
		}
	}

	private void disassemble(AbstractMinecart cart) {
		if (cart instanceof Minecart) {
			return;
		}
		List<Entity> passengers = cart.getPassengers();
		if (passengers.isEmpty() || !(passengers.getFirst() instanceof AbstractContraptionEntity)) {
			return;
		}
		Level world = cart.level();
		int i = Mth.floor(cart.getX());
		int j = Mth.floor(cart.getY());
		int k = Mth.floor(cart.getZ());
		if (world.getBlockState(new BlockPos(i, j - 1, k))
			.is(BlockTags.RAILS)) {
			--j;
		}
		BlockPos blockpos = new BlockPos(i, j, k);
		BlockState blockstate = world.getBlockState(blockpos);
		if (blockstate.is(Blocks.ACTIVATOR_RAIL)) {
			if (cart.isVehicle()) {
				cart.ejectPassengers();
			}

			if (cart.getHurtTime() == 0) {
				cart.setHurtDir(-cart.getHurtDir());
				cart.setHurtTime(10);
				cart.setDamage(50.0F);
			}
		}
	}

	/**
	 * Create cancels EntityMountEvent for entities other than contraptions on a cart that is coupled through a
	 * contraption. Fabric has no such event, so whatever boards such a cart is put off again.
	 */
	private void ejectPassengersOfContraptionCarriages(AbstractMinecart cart) {
		if (!isCoupledThroughContraption())
			return;
		for (Entity passenger : new ArrayList<>(cart.getPassengers()))
			if (!(passenger instanceof AbstractContraptionEntity))
				passenger.stopRiding();
	}

	public boolean isFullyCoupled() {
		return isLeadingCoupling() && isConnectedToCoupling();
	}

	public boolean isLeadingCoupling() {
		return couplings.get(true)
			.isPresent();
	}

	public boolean isConnectedToCoupling() {
		return couplings.get(false)
			.isPresent();
	}

	public boolean isCoupledThroughContraption() {
		for (boolean current : Iterate.trueAndFalse)
			if (hasContraptionCoupling(current))
				return true;
		return false;
	}

	public boolean hasContraptionCoupling(boolean current) {
		Optional<CouplingData> optional = couplings.get(current);
		return optional.isPresent() && optional.get().contraption;
	}

	public float getCouplingLength(boolean leading) {
		Optional<CouplingData> optional = couplings.get(leading);
		return optional.map(couplingData -> couplingData.length)
			.orElse(0F);
	}

	public void decouple() {
		couplings.forEachWithContext((opt, main) -> opt.ifPresent(cd -> {
			UUID idOfOther = cd.idOfCart(!main);
			MinecartController otherCart = CapabilityMinecartController.getIfPresent(getWorld(), idOfOther);
			if (otherCart == null)
				return;

			removeConnection(main);
			otherCart.removeConnection(!main);
		}));
	}

	public void removeConnection(boolean main) {
		if (hasContraptionCoupling(main) && getWorld() != null && !getWorld().isClientSide()) {
			List<Entity> passengers = cart().getPassengers();
			if (!passengers.isEmpty()) {
				Entity entity = passengers.getFirst();
				if (entity instanceof AbstractContraptionEntity)
					((AbstractContraptionEntity) entity).disassemble();
			}
		}

		couplings.set(main, Optional.empty());
		needsEntryRefresh |= main;
		sendData();
	}

	public void prepareForCoupling(boolean isLeading) {
		// reverse existing chain if necessary
		if (isLeading && isLeadingCoupling() || !isLeading && isConnectedToCoupling()) {

			List<MinecartController> cartsToFlip = new ArrayList<>();
			MinecartController current = this;
			boolean forward = current.isLeadingCoupling();
			int safetyCount = 1000;

			do {
				if (safetyCount-- <= 0) {
					CreatePolymer.LOGGER.warn("Infinite loop in coupling iteration");
					return;
				}
				cartsToFlip.add(current);
				current = CouplingHandler.getNextInCouplingChain(getWorld(), current, forward);
			} while (current != null && current != MinecartController.EMPTY);

			for (MinecartController minecartController : cartsToFlip) {
				minecartController.couplings.forEachWithContext((opt, leading) -> opt.ifPresent(cd -> {
					cd.flip();
					if (!cd.contraption)
						return;
					List<Entity> passengers = minecartController.cart()
						.getPassengers();
					if (passengers.isEmpty())
						return;
					Entity entity = passengers.getFirst();
					if (!(entity instanceof OrientedContraptionEntity contraption))
						return;
					UUID couplingId = contraption.getCouplingId();
					if (cd.mainCartID.equals(couplingId)) {
						contraption.setCouplingId(cd.connectedCartID);
						return;
					}
					if (cd.connectedCartID.equals(couplingId)) {
						contraption.setCouplingId(cd.mainCartID);
						return;
					}
				}));
				minecartController.couplings = minecartController.couplings.swap();
				minecartController.needsEntryRefresh = true;
				if (minecartController == this)
					continue;
				minecartController.sendData();
			}
		}
	}

	public void coupleWith(boolean isLeading, UUID coupled, float length, boolean contraption) {
		UUID mainID = isLeading ? cart().getUUID() : coupled;
		UUID connectedID = isLeading ? coupled : cart().getUUID();
		couplings.set(isLeading, Optional.of(new CouplingData(mainID, connectedID, length, contraption)));
		needsEntryRefresh |= isLeading;
		sendData();
	}

	@Nullable
	public UUID getCoupledCart(boolean asMain) {
		Optional<CouplingData> optional = couplings.get(asMain);
		if (optional.isEmpty())
			return null;
		CouplingData couplingData = optional.get();
		return asMain ? couplingData.connectedCartID : couplingData.mainCartID;
	}

	public boolean isStalled() {
		return isStalled(true) || isStalled(false);
	}

	private boolean isStalled(boolean internal) {
		return stallData.get(internal)
			.isPresent();
	}

	public void setStalledExternally(boolean stall) {
		setStalled(stall, false);
	}

	private void setStalled(boolean stall, boolean internal) {
		if (isStalled(internal) == stall)
			return;

		@Nullable
		AbstractMinecart cart = cart();
		if (cart == null)
			return;

		if (stall) {
			stallData.set(internal, Optional.of(new StallData(cart)));
			sendData();
			return;
		}

		if (!isStalled(!internal))
			stallData.get(internal)
				.ifPresent(data -> data.release(cart));
		stallData.set(internal, Optional.empty());

		sendData();
	}

	/**
	 * Create sends the controller to clients tracking the cart; there is no client side, so this only makes sure the
	 * cart is registered again.
	 */
	public void sendData() {
		sendData(null);
	}

	public void sendData(@Nullable AbstractMinecart cart) {
		if (cart != null) {
			this.weakRef = new WeakReference<>(cart);
			needsEntryRefresh = true;
		}
	}

	public boolean isPresent() {
		return weakRef.get() != null && cart().isAlive();
	}

	public AbstractMinecart cart() {
		return weakRef.get();
	}

	private @Nullable Level getWorld() {
		if (cart() == null)
			return null;
		return cart().level();
	}

	private static class CouplingData {
		static final Codec<CouplingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("Main")
				.forGetter(c -> c.mainCartID),
			UUIDUtil.CODEC.fieldOf("Connected")
				.forGetter(c -> c.connectedCartID),
			Codec.FLOAT.optionalFieldOf("Length", 0f)
				.forGetter(c -> c.length),
			Codec.BOOL.optionalFieldOf("Contraption", false)
				.forGetter(c -> c.contraption))
			.apply(instance, CouplingData::new));

		private UUID mainCartID;
		private UUID connectedCartID;
		private float length;
		private boolean contraption;

		public CouplingData(UUID mainCartID, UUID connectedCartID, float length, boolean contraption) {
			this.mainCartID = mainCartID;
			this.connectedCartID = connectedCartID;
			this.length = length;
			this.contraption = contraption;
		}

		void flip() {
			UUID swap = mainCartID;
			mainCartID = connectedCartID;
			connectedCartID = swap;
		}

		public UUID idOfCart(boolean main) {
			return main ? mainCartID : connectedCartID;
		}

	}

	private static class StallData {
		static final Codec<StallData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Vec3.CODEC.fieldOf("Pos")
				.forGetter(s -> s.position),
			Vec3.CODEC.fieldOf("Motion")
				.forGetter(s -> s.motion),
			Codec.FLOAT.optionalFieldOf("Yaw", 0f)
				.forGetter(s -> s.yaw),
			Codec.FLOAT.optionalFieldOf("Pitch", 0f)
				.forGetter(s -> s.pitch))
			.apply(instance, StallData::new));

		Vec3 position;
		Vec3 motion;
		float yaw, pitch;

		private StallData(Vec3 position, Vec3 motion, float yaw, float pitch) {
			this.position = position;
			this.motion = motion;
			this.yaw = yaw;
			this.pitch = pitch;
		}

		StallData(AbstractMinecart entity) {
			position = entity.position();
			motion = entity.getDeltaMovement();
			yaw = entity.getYRot();
			pitch = entity.getXRot();
			tick(entity);
		}

		void tick(AbstractMinecart entity) {
//			entity.setPos(position.x, position.y, position.z);
			entity.setDeltaMovement(Vec3.ZERO);
			entity.setYRot(yaw);
			entity.setXRot(pitch);
		}

		void release(AbstractMinecart entity) {
			entity.setDeltaMovement(motion);
		}
	}

	private static class Empty extends MinecartController {

		private Empty() {
			super((AbstractMinecart) null);
		}

		@Override
		@NotNull
		protected Type getType() {
			return Type.EMPTY;
		}

		private static void warn() {
			CreatePolymer.LOGGER.warn("Method called on EMPTY MinecartController", new Exception());
		}

		@Override
		public void tick() {
			warn();
		}

		@Override
		public boolean isFullyCoupled() {
			warn();
			return false;
		}

		@Override
		public boolean isLeadingCoupling() {
			warn();
			return false;
		}

		@Override
		public boolean isConnectedToCoupling() {
			warn();
			return false;
		}

		@Override
		public boolean isCoupledThroughContraption() {
			warn();
			return false;
		}

		@Override
		public boolean hasContraptionCoupling(boolean current) {
			warn();
			return false;
		}

		@Override
		public float getCouplingLength(boolean leading) {
			warn();
			return 0.0f;
		}

		@Override
		public void decouple() {
			warn();
		}

		@Override
		public void removeConnection(boolean main) {
			warn();
		}

		@Override
		public void prepareForCoupling(boolean isLeading) {
			warn();
		}

		@Override
		public void coupleWith(boolean isLeading, UUID coupled, float length, boolean contraption) {
			warn();
		}

		@Nullable
		@Override
		public UUID getCoupledCart(boolean asMain) {
			warn();
			return null;
		}

		@Override
		public boolean isStalled() {
			warn();
			return false;
		}

		@Override
		public void setStalledExternally(boolean stall) {
			warn();
		}

		@Override
		public void sendData() {
			super.sendData();
		}

		@Override
		public boolean isPresent() {
			return super.isPresent();
		}

		@Override
		public AbstractMinecart cart() {
			return super.cart();
		}
	}

	protected enum Type {
		EMPTY, NORMAL
	}

}

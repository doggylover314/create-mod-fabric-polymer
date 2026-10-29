package dev.createpolymer.kinetics.deployer;

import static dev.createpolymer.kinetics.base.DirectionalKineticBlock.FACING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.belt.behaviour.BeltProcessingBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.processing.sequenced.SequencedAssemblyRecipe;
import dev.createpolymer.registry.feature.DeployerRegistry;
import dev.createpolymer.util.NBTHelper;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Ported from Create's DeployerBlockEntity (MIT, The Create Team). */
public class DeployerBlockEntity extends KineticBlockEntity implements Clearable {
	protected State state;
	protected Mode mode;
	protected ItemStack heldItem;
	protected DeployerFakePlayer player;
	protected int timer;
	protected float reach;
	protected boolean fistBump = false;
	protected List<ItemStack> overflowItems = new ArrayList<>();
	protected FilteringBehaviour filtering;
	protected boolean redstoneLocked;
	protected UUID owner;
	private IItemHandlerModifiable invHandler;
	private List<ItemStackWithSlot> deferredInventoryList;

	public BeltProcessingBehaviour processingBehaviour;

	public enum State {
		WAITING, EXPANDING, RETRACTING, DUMPING;
	}

	public enum Mode {
		PUNCH, USE
	}

	/** Create's DEPLOYER_HAND_POINTING / HOLDING / PUNCHING partial models. */
	public enum HandPose {
		POINTING("deployer/hand_pointing", 0), HOLDING("deployer/hand_holding", 4 / 16f),
		PUNCHING("deployer/hand_punching", 3 / 16f);

		public final String model;
		public final float handLength;

		HandPose(String model, float handLength) {
			this.model = model;
			this.handLength = handLength;
		}
	}

	public DeployerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.state = State.WAITING;
		mode = Mode.USE;
		heldItem = ItemStack.EMPTY;
		redstoneLocked = false;
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(DeployerRegistry.DEPLOYER_BLOCK_ENTITY, (be, side) -> {
			if (be.invHandler == null)
				be.initHandler();
			return be.invHandler;
		});
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		filtering = new FilteringBehaviour(this, new DeployerFilterSlot());
		behaviours.add(filtering);
		processingBehaviour =
			new BeltProcessingBehaviour(this).whenItemEnters((s, i) -> BeltDeployerCallbacks.onItemReceived(s, i, this))
				.whileItemHeld((s, i) -> BeltDeployerCallbacks.whenItemHeld(s, i, this));
		behaviours.add(processingBehaviour);
	}

	@Override
	public void initialize() {
		super.initialize();
		initHandler();
	}

	private void initHandler() {
		if (invHandler != null)
			return;
		if (level instanceof ServerLevel sLevel) {
			player = new DeployerFakePlayer(sLevel, owner);
			if (deferredInventoryList != null) {
				Inventory inventory = player.getInventory();
				inventory.clearContent();
				for (ItemStackWithSlot item : deferredInventoryList)
					if (item.isValidInContainer(inventory.getContainerSize()))
						inventory.setItem(item.slot(), item.stack());
				deferredInventoryList = null;
				heldItem = player.getMainHandItem();
				sendData();
			}
			Vec3 initialPos = VecHelper.getCenterOf(worldPosition.relative(getBlockState().getValue(FACING)));
			player.setPos(initialPos.x, initialPos.y, initialPos.z);
		}
		invHandler = createHandler();
	}

	protected void onExtract(ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
		sendData();
		setChanged();
	}

	protected int getTimerSpeed() {
		return (int) (getSpeed() == 0 ? 0 : Mth.clamp(Math.abs(getSpeed() * 2), 8, 512));
	}

	@Override
	public void tick() {
		super.tick();

		if (getSpeed() == 0)
			return;
		if (!level.isClientSide() && player != null && player.blockBreakingProgress != null) {
			if (level.isEmptyBlock(player.blockBreakingProgress.getKey())) {
				level.destroyBlockProgress(player.getId(), player.blockBreakingProgress.getKey(), -1);
				player.blockBreakingProgress = null;
			}
		}
		if (timer > 0) {
			timer -= getTimerSpeed();
			return;
		}
		if (level.isClientSide())
			return;
		if (player == null)
			return;

		ItemStack stack = player.getMainHandItem();
		if (state == State.WAITING) {
			if (!overflowItems.isEmpty()) {
				timer = getTimerSpeed() * 10;
				return;
			}

			boolean changed = false;
			Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (overflowItems.size() > 10)
					break;
				ItemStack item = inventory.getItem(i);
				if (item.isEmpty())
					continue;
				if (item != stack || !filtering.test(item)) {
					overflowItems.add(item);
					inventory.setItem(i, ItemStack.EMPTY);
					changed = true;
				}
			}

			if (changed) {
				sendData();
				timer = getTimerSpeed() * 10;
				return;
			}

			Direction facing = getBlockState().getValue(FACING);
			if (mode == Mode.USE
				&& !DeployerHandler.shouldActivate(stack, level, worldPosition.relative(facing, 2), facing)) {
				timer = getTimerSpeed() * 10;
				return;
			}

			// Check for advancement conditions
			if (mode == Mode.PUNCH && !fistBump && startFistBump(facing))
				return;
			if (redstoneLocked)
				return;

			start();
			return;
		}

		if (state == State.EXPANDING) {
			if (fistBump)
				triggerFistBump();
			activate();

			state = State.RETRACTING;
			timer = 1000;
			sendData();
			return;
		}

		if (state == State.RETRACTING) {
			state = State.WAITING;
			timer = 500;
			sendData();
			return;
		}

	}

	protected void start() {
		state = State.EXPANDING;
		Vec3 movementVector = getMovementVector();
		Vec3 rayOrigin = VecHelper.getCenterOf(worldPosition)
			.add(movementVector.scale(3 / 2f));
		Vec3 rayTarget = VecHelper.getCenterOf(worldPosition)
			.add(movementVector.scale(5 / 2f));
		ClipContext rayTraceContext = new ClipContext(rayOrigin, rayTarget, Block.OUTLINE, Fluid.NONE, player);
		BlockHitResult result = level.clip(rayTraceContext);
		reach = (float) (.5f + Math.min(result.getLocation()
			.subtract(rayOrigin)
			.length(), .75f));
		timer = 1000;
		sendData();
	}

	public boolean startFistBump(Direction facing) {
		int i = 0;
		DeployerBlockEntity partner = null;

		for (i = 2; i < 5; i++) {
			BlockPos otherDeployer = worldPosition.relative(facing, i);
			if (!level.isLoaded(otherDeployer))
				return false;
			BlockEntity other = level.getBlockEntity(otherDeployer);
			if (other instanceof DeployerBlockEntity dpe) {
				partner = dpe;
				break;
			}
		}

		if (partner == null)
			return false;

		if (level.getBlockState(partner.getBlockPos())
			.getValue(FACING)
			.getOpposite() != facing || partner.mode != Mode.PUNCH)
			return false;
		if (partner.getSpeed() == 0)
			return false;

		for (DeployerBlockEntity be : Arrays.asList(this, partner)) {
			be.fistBump = true;
			be.reach = ((i - 2)) * .5f;
			be.timer = 1000;
			be.state = State.EXPANDING;
			be.sendData();
		}

		return true;
	}

	public void triggerFistBump() {
		int i = 0;
		DeployerBlockEntity deployerBlockEntity = null;
		for (i = 2; i < 5; i++) {
			BlockPos pos = worldPosition.relative(getBlockState().getValue(FACING), i);
			if (!level.isLoaded(pos))
				return;
			if (level.getBlockEntity(pos) instanceof DeployerBlockEntity dpe) {
				deployerBlockEntity = dpe;
				break;
			}
		}

		if (deployerBlockEntity == null)
			return;
		if (!deployerBlockEntity.fistBump || deployerBlockEntity.state != State.EXPANDING)
			return;
		if (deployerBlockEntity.timer > 0)
			return;

		fistBump = false;
		deployerBlockEntity.fistBump = false;
		deployerBlockEntity.state = State.RETRACTING;
		deployerBlockEntity.timer = 1000;
		deployerBlockEntity.sendData();

		BlockPos soundLocation = BlockPos.containing(Vec3.atCenterOf(worldPosition)
			.add(Vec3.atCenterOf(deployerBlockEntity.getBlockPos()))
			.scale(.5f));
		level.playSound(null, soundLocation, SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.BLOCKS, .75f, .75f);
	}

	protected void activate() {
		Vec3 movementVector = getMovementVector();
		Direction direction = getBlockState().getValue(FACING);
		Vec3 center = VecHelper.getCenterOf(worldPosition);
		BlockPos clickedPos = worldPosition.relative(direction, 2);
		player.setXRot(direction == Direction.UP ? -90 : direction == Direction.DOWN ? 90 : 0);
		player.setYRot(direction.toYRot());

		if (direction == Direction.DOWN
			&& BlockEntityBehaviour.get(level, clickedPos, TransportedItemStackHandlerBehaviour.TYPE) != null)
			return; // Belt processing handled in BeltDeployerCallbacks

		DeployerHandler.activate(player, center, clickedPos, movementVector, mode);
		spawnEffectParticles();

		if (player != null) {
			int count = heldItem.getCount();
			heldItem = player.getMainHandItem();
			if (count != heldItem.getCount())
				setChanged();
		}
	}

	/**
	 * Create ships the item the deployer worked with to the client with the next data packet, which draws the
	 * particles. Here the server sends them right away.
	 */
	protected void spawnEffectParticles() {
		if (player == null || player.spawnedItemEffects == null)
			return;
		ItemStack particleStack = player.spawnedItemEffects;
		player.spawnedItemEffects = null;
		if (particleStack.isEmpty() || !(level instanceof ServerLevel serverLevel))
			return;
		Vec3 location = VecHelper.getCenterOf(worldPosition)
			.add(getMovementVector().scale(reach + 1));
		serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, particleStack.getItem()), location.x, location.y,
			location.z, 20, .125f, .125f, .125f, .0625f);
	}

	protected Vec3 getMovementVector() {
		if (!(getBlockState().getBlock() instanceof DeployerBlock))
			return Vec3.ZERO;
		return Vec3.atLowerCornerOf(getBlockState().getValue(FACING)
			.getUnitVec3i());
	}

	@Override
	protected void read(ValueInput compound) {
		state = NBTHelper.readEnum(compound, "State", State.class);
		mode = NBTHelper.readEnum(compound, "Mode", Mode.class);
		timer = compound.getIntOr("Timer", 0);
		redstoneLocked = compound.getBooleanOr("Powered", false);
		owner = compound.read("Owner", UUIDUtil.CODEC)
			.orElse(null);

		List<ItemStackWithSlot> inventory = new ArrayList<>();
		compound.listOrEmpty("Inventory", ItemStackWithSlot.CODEC)
			.forEach(inventory::add);
		deferredInventoryList = inventory;
		overflowItems = NBTHelper.readItemList(compound, "Overflow");
		heldItem = compound.read("HeldItem", ItemStack.OPTIONAL_CODEC)
			.orElse(heldItem);
		super.read(compound);
	}

	@Override
	public void writeSafe(ValueOutput compound) {
		NBTHelper.writeEnum(compound, "Mode", mode);
		super.writeSafe(compound);
	}

	@Override
	public void write(ValueOutput compound) {
		NBTHelper.writeEnum(compound, "Mode", mode);
		NBTHelper.writeEnum(compound, "State", state);
		compound.putInt("Timer", timer);
		compound.putBoolean("Powered", redstoneLocked);
		if (owner != null)
			compound.store("Owner", UUIDUtil.CODEC, owner);

		if (player != null) {
			player.getInventory()
				.save(compound.list("Inventory", ItemStackWithSlot.CODEC));
			compound.store("HeldItem", ItemStack.OPTIONAL_CODEC, player.getMainHandItem());
			NBTHelper.writeItemList(compound, "Overflow", overflowItems);
		} else if (deferredInventoryList != null) {
			ValueOutput.TypedOutputList<ItemStackWithSlot> list = compound.list("Inventory", ItemStackWithSlot.CODEC);
			deferredInventoryList.forEach(list::add);
		}

		super.write(compound);
	}

	private IItemHandlerModifiable createHandler() {
		return new DeployerItemHandler(this);
	}

	public void redstoneUpdate() {
		if (level.isClientSide())
			return;
		boolean blockPowered = level.hasNeighborSignal(worldPosition);
		if (blockPowered == redstoneLocked)
			return;
		redstoneLocked = blockPowered;
		sendData();
	}

	public HandPose getHandPose() {
		return mode == Mode.PUNCH ? HandPose.PUNCHING : getHeldItem().isEmpty() ? HandPose.POINTING : HandPose.HOLDING;
	}

	/** The item in the hand of the deployer, as shown on its model. */
	public ItemStack getHeldItem() {
		return player != null ? player.getMainHandItem() : heldItem;
	}

	public Mode getMode() {
		return mode;
	}

	public State getState() {
		return state;
	}

	public void discardPlayer() {
		if (player == null)
			return;
		player.getInventory()
			.dropAll();
		overflowItems.forEach(itemstack -> player.drop(itemstack, true, Prediction.SERVER_ONLY));
		player.discard();
		player = null;
	}

	/** Block broken: Create calls this from DeployerBlock.onRemove. */
	@Override
	public void destroy() {
		super.destroy();
		discardPlayer();
	}

	@Override
	public void clearContent() {
		filtering.setFilter(ItemStack.EMPTY);
	}

	public void changeMode() {
		mode = mode == Mode.PUNCH ? Mode.USE : Mode.PUNCH;
		setChanged();
		sendData();
	}

	/** How far the hand sticks out of the deployer, in blocks. */
	public float getHandOffset(float partialTicks) {
		float progress = 0;
		int timerSpeed = getTimerSpeed();
		HandPose handPose = getHandPose();

		if (state == State.EXPANDING) {
			progress = 1 - (timer - partialTicks * timerSpeed) / 1000f;
			if (fistBump)
				progress *= progress;
		}
		if (state == State.RETRACTING)
			progress = (timer - partialTicks * timerSpeed) / 1000f;
		float distance = Math.min(Mth.clamp(progress, 0, 1) * (reach + handPose.handLength), 21 / 16f);

		return distance;
	}

	@Nullable
	public RecipeHolder<? extends Recipe<? extends RecipeInput>> getRecipe(ItemStack stack) {
		if (player == null || level == null)
			return null;

		ItemStack heldItemMainhand = player.getMainHandItem();
		// Create also looks for sandpaper polishing recipes here

		ItemApplicationInput inventory = new ItemApplicationInput(stack, heldItemMainhand);
		DeployerRecipeSearchEvent event = new DeployerRecipeSearchEvent(this, inventory);

		event.addRecipe(() -> SequencedAssemblyRecipe.getRecipe(level, event.getInventory(), DeployerRegistry.DEPLOYING,
			DeployerApplicationRecipe.class), 100);
		event.addRecipe(() -> checkRecipe(DeployerRegistry.DEPLOYING, event.getInventory(), level), 50);
		event.addRecipe(() -> checkRecipe(DeployerRegistry.ITEM_APPLICATION, event.getInventory(), level), 50);

		DeployerRecipeSearchEvent.EVENT.invoker()
			.onSearch(event);
		return event.getRecipe();
	}

	private <I extends RecipeInput, R extends Recipe<I>> Optional<RecipeHolder<R>> checkRecipe(RecipeType<R> type, I inv,
		Level level) {
		if (!(level instanceof ServerLevel serverLevel))
			return Optional.empty();
		return serverLevel.recipeAccess()
			.getRecipeFor(type, inv, level)
			.filter(DeployerBlockEntity::canBeAutomated);
	}

	/** Create's AllRecipeTypes.CAN_BE_AUTOMATED */
	public static boolean canBeAutomated(RecipeHolder<?> recipe) {
		return !recipe.id()
			.identifier()
			.getPath()
			.endsWith("_manual_only");
	}

	public DeployerFakePlayer getPlayer() {
		return player;
	}
}

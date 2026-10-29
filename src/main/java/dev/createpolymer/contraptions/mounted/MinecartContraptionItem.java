package dev.createpolymer.contraptions.mounted;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.OrientedContraptionEntity;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.util.NBTHelper;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class MinecartContraptionItem extends SimplePolymerItem {

	/** Create's AbstractMinecart.Type of the carts that can carry a contraption. */
	public enum Type {
		RIDEABLE, FURNACE, CHEST
	}

	private final Type minecartType;

	public static MinecartContraptionItem rideable(Properties builder) {
		return new MinecartContraptionItem(Type.RIDEABLE, builder);
	}

	public static MinecartContraptionItem furnace(Properties builder) {
		return new MinecartContraptionItem(Type.FURNACE, builder);
	}

	public static MinecartContraptionItem chest(Properties builder) {
		return new MinecartContraptionItem(Type.CHEST, builder);
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return MinecartRegistry.minecartContraptionInContainers;
	}

	private MinecartContraptionItem(Type minecartTypeIn, Properties builder) {
		super(builder, Items.TRIAL_KEY, true);
		this.minecartType = minecartTypeIn;
		DispenserBlock.registerBehavior(this, DISPENSER_BEHAVIOR);
	}

	private EntityType<? extends AbstractMinecart> getEntityType() {
		return switch (minecartType) {
			case RIDEABLE -> EntityTypes.MINECART;
			case FURNACE -> EntityTypes.FURNACE_MINECART;
			case CHEST -> EntityTypes.CHEST_MINECART;
		};
	}

	public static void init() {
		UseEntityCallback.EVENT.register(MinecartContraptionItem::wrenchCanBeUsedToPickUpMinecartContraptions);
	}

	// Taken and adjusted from MinecartDispenseItemBehavior
	private static final DispenseItemBehavior DISPENSER_BEHAVIOR = new DefaultDispenseItemBehavior() {
		private final DefaultDispenseItemBehavior behaviourDefaultDispenseItem = new DefaultDispenseItemBehavior();

		@Override
		public ItemStack execute(BlockSource source, ItemStack stack) {
			Direction direction = source.state()
				.getValue(DispenserBlock.FACING);
			ServerLevel world = source.level();
			Vec3 vec3 = source.center();
			double d0 = vec3.x() + (double) direction.getStepX() * 1.125D;
			double d1 = Math.floor(vec3.y()) + (double) direction.getStepY();
			double d2 = vec3.z() + (double) direction.getStepZ() * 1.125D;
			BlockPos blockpos = source.pos()
				.relative(direction);
			BlockState blockstate = world.getBlockState(blockpos);
			RailShape railshape = getRailShape(blockstate);
			double d3;
			if (blockstate.is(BlockTags.RAILS)) {
				if (railshape.isSlope()) {
					d3 = 0.6D;
				} else {
					d3 = 0.1D;
				}
			} else {
				if (!blockstate.isAir() || !world.getBlockState(blockpos.below())
					.is(BlockTags.RAILS)) {
					return this.behaviourDefaultDispenseItem.dispense(source, stack);
				}

				BlockState blockstate1 = world.getBlockState(blockpos.below());
				RailShape railshape1 = getRailShape(blockstate1);
				if (direction != Direction.DOWN && railshape1.isSlope()) {
					d3 = -0.4D;
				} else {
					d3 = -0.9D;
				}
			}

			AbstractMinecart abstractminecartentity = AbstractMinecart.createMinecart(world, d0, d1 + d3, d2,
				((MinecartContraptionItem) stack.getItem()).getEntityType(), EntitySpawnReason.DISPENSER, stack, null);
			if (abstractminecartentity == null)
				return stack;
			if (stack.has(DataComponents.CUSTOM_NAME))
				abstractminecartentity.setCustomName(stack.getHoverName());
			world.addFreshEntity(abstractminecartentity);
			addContraptionToMinecart(world, stack, abstractminecartentity, direction);

			stack.shrink(1);
			return stack;
		}

		@Override
		protected void playSound(BlockSource source) {
			source.level()
				.levelEvent(LevelEvent.SOUND_DISPENSER_DISPENSE, source.pos(), 0);
		}
	};

	private static RailShape getRailShape(BlockState state) {
		return state.getBlock() instanceof BaseRailBlock rail ? state.getValue(rail.getShapeProperty())
			: RailShape.NORTH_SOUTH;
	}

	// Taken and adjusted from MinecartItem
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level world = context.getLevel();
		BlockPos blockpos = context.getClickedPos();
		BlockState blockstate = world.getBlockState(blockpos);
		if (!blockstate.is(BlockTags.RAILS)) {
			return InteractionResult.FAIL;
		} else {
			ItemStack itemstack = context.getItemInHand();
			if (world instanceof ServerLevel serverlevel) {
				RailShape railshape = getRailShape(blockstate);
				double d0 = 0.0D;
				if (railshape.isSlope()) {
					d0 = 0.5D;
				}

				AbstractMinecart abstractminecartentity = AbstractMinecart.createMinecart(serverlevel,
					(double) blockpos.getX() + 0.5D, (double) blockpos.getY() + 0.0625D + d0,
					(double) blockpos.getZ() + 0.5D, getEntityType(), EntitySpawnReason.SPAWN_ITEM_USE, itemstack,
					context.getPlayer());
				if (abstractminecartentity == null)
					return InteractionResult.FAIL;
				if (itemstack.has(DataComponents.CUSTOM_NAME))
					abstractminecartentity.setCustomName(itemstack.getHoverName());
				Player player = context.getPlayer();
				world.addFreshEntity(abstractminecartentity);
				addContraptionToMinecart(world, itemstack, abstractminecartentity,
					player == null ? null : player.getDirection());
			}

			itemstack.shrink(1);
			return InteractionResult.SUCCESS;
		}
	}

	public static void addContraptionToMinecart(Level world, ItemStack itemstack, AbstractMinecart cart,
		@Nullable Direction newFacing) {
		if (itemstack.has(MinecartRegistry.MINECART_CONTRAPTION_DATA)) {
			CompoundTag contraptionTag = itemstack.get(MinecartRegistry.MINECART_CONTRAPTION_DATA);

			Direction intialOrientation = NBTHelper.readEnum(contraptionTag, "InitialOrientation", Direction.class);

			Contraption mountedContraption = Contraption.fromNBT(world, contraptionTag, false);
			OrientedContraptionEntity contraptionEntity =
				newFacing == null ? OrientedContraptionEntity.create(world, mountedContraption, intialOrientation)
					: OrientedContraptionEntity.createAtYaw(world, mountedContraption, intialOrientation,
						newFacing.toYRot());

			contraptionEntity.startRiding(cart);
			contraptionEntity.setPos(cart.getX(), cart.getY(), cart.getZ());
			world.addFreshEntity(contraptionEntity);
		}
	}

	public static InteractionResult wrenchCanBeUsedToPickUpMinecartContraptions(Player player, Level level,
		InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (player == null || entity == null || level.isClientSide() || player.isSpectator())
			return InteractionResult.PASS;
		if (!MinecartRegistry.survivalContraptionPickup && !player.isCreative())
			return InteractionResult.PASS;

		ItemStack wrench = player.getItemInHand(hand);
		if (!wrench.is(ModItems.WRENCH))
			return InteractionResult.PASS;
		if (entity instanceof AbstractContraptionEntity)
			entity = entity.getVehicle();
		if (!(entity instanceof AbstractMinecart cart))
			return InteractionResult.PASS;
		if (!entity.isAlive())
			return InteractionResult.PASS;
		Type type = getType(cart);
		if (type == null)
			return InteractionResult.PASS;
		List<Entity> passengers = cart.getPassengers();
		if (passengers.isEmpty() || !(passengers.get(0) instanceof OrientedContraptionEntity oce))
			return InteractionResult.PASS;
		Contraption contraption = oce.getContraption();

		if (contraption.forbidsPickup()) {
			player.sendOverlayMessage(Component.translatable("create.contraption.minecart_contraption_illegal_pickup")
				.withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}

		contraption.stop(level);

		// Create also resets the portable storage interfaces of the contraption here

		ItemStack generatedStack = create(type, oce);
		generatedStack.set(DataComponents.CUSTOM_NAME, entity.getCustomName());

		player.getInventory()
			.placeItemBackInInventory(generatedStack, Prediction.SERVER_ONLY);
		oce.discard();
		entity.discard();
		return InteractionResult.SUCCESS;
	}

	@Nullable
	private static Type getType(AbstractMinecart cart) {
		if (cart instanceof MinecartFurnace)
			return Type.FURNACE;
		if (cart instanceof MinecartChest)
			return Type.CHEST;
		if (cart.isRideable())
			return Type.RIDEABLE;
		return null;
	}

	public static ItemStack create(Type type, OrientedContraptionEntity entity) {
		ItemStack stack = ItemStack.EMPTY;

		switch (type) {
			case RIDEABLE:
				stack = new ItemStack(MinecartRegistry.MINECART_CONTRAPTION);
				break;
			case FURNACE:
				stack = new ItemStack(MinecartRegistry.FURNACE_MINECART_CONTRAPTION);
				break;
			case CHEST:
				stack = new ItemStack(MinecartRegistry.CHEST_MINECART_CONTRAPTION);
				break;
			default:
				break;
		}

		if (stack.isEmpty())
			return stack;

		CompoundTag tag = entity.getContraption()
			.writeNBT(entity.registryAccess(), false);
		tag.remove("UUID");
		tag.remove("Pos");
		tag.remove("Motion");

		NBTHelper.writeEnum(tag, "InitialOrientation", entity.getInitialOrientation());

		stack.set(MinecartRegistry.MINECART_CONTRAPTION_DATA, tag);
		return stack;
	}
}

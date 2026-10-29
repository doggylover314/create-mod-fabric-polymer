package dev.createpolymer.logistics.box;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.box.PackageStyles.PackageStyle;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Create's PackageItem. Players see a trial key with the package's model; address and contents are shown through the
 * tooltip Polymer builds from {@link #appendHoverText}.
 */
public class PackageItem extends Item implements PolymerItem {
	public static final int SLOTS = 9;

	public PackageStyle style;

	public PackageItem(Properties properties, PackageStyle style) {
		super(properties);
		this.style = style;
		PackageStyles.ALL_BOXES.add(this);
		(style.rare() ? PackageStyles.RARE_BOXES : PackageStyles.STANDARD_BOXES).add(this);
	}

	/** Create's getDescriptionId override: all cardboard boxes share one name, so do all rare boxes. */
	public static Properties properties(PackageStyle style) {
		return new Properties().stacksTo(1)
			.overrideDescription("item.create" + (style.rare() ? ".rare_package" : ".package"));
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		return Items.TRIAL_KEY;
	}

	public static boolean isPackage(ItemStack stack) {
		return stack.getItem() instanceof PackageItem;
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	public static ItemStack containing(List<ItemStack> stacks) {
		ItemStackHandler newInv = new ItemStackHandler(9);
		stacks.forEach(s -> ItemHandlerHelper.insertItemStacked(newInv, s, false));
		return containing(newInv);
	}

	public static ItemStack containing(ItemStackHandler stacks) {
		ItemStack box = PackageStyles.getRandomBox();
		box.set(PackagesRegistry.PACKAGE_CONTENTS, ItemHelper.containerContentsFromHandler(stacks));
		return box;
	}

	public static void clearAddress(ItemStack box) {
		box.remove(PackagesRegistry.PACKAGE_ADDRESS);
	}

	public static void addAddress(ItemStack box, String address) {
		box.set(PackagesRegistry.PACKAGE_ADDRESS, address);
	}

	public static void setOrder(ItemStack box, int orderId, int linkIndex, boolean isFinalLink, int fragmentIndex,
		boolean isFinal, @Nullable PackageOrderWithCrafts orderContext) {
		PackageOrderData order = new PackageOrderData(orderId, linkIndex, isFinalLink, fragmentIndex, isFinal, orderContext);
		box.set(PackagesRegistry.PACKAGE_ORDER_DATA, order);
	}

	public static int getOrderId(ItemStack box) {
		if (box.has(PackagesRegistry.PACKAGE_ORDER_DATA)) {
			//noinspection DataFlowIssue
			return box.get(PackagesRegistry.PACKAGE_ORDER_DATA).orderId();
		} else {
			return -1;
		}
	}

	public static boolean hasOrderData(ItemStack box) {
		return box.has(PackagesRegistry.PACKAGE_ORDER_DATA);
	}

	public static int getIndex(ItemStack box) {
		if (box.has(PackagesRegistry.PACKAGE_ORDER_DATA)) {
			//noinspection DataFlowIssue
			return box.get(PackagesRegistry.PACKAGE_ORDER_DATA).fragmentIndex();
		} else {
			return -1;
		}
	}

	public static boolean isFinal(ItemStack box) {
		//noinspection DataFlowIssue
		return box.has(PackagesRegistry.PACKAGE_ORDER_DATA) && box.get(PackagesRegistry.PACKAGE_ORDER_DATA).isFinal();
	}

	public static int getLinkIndex(ItemStack box) {
		if (box.has(PackagesRegistry.PACKAGE_ORDER_DATA)) {
			//noinspection DataFlowIssue
			return box.get(PackagesRegistry.PACKAGE_ORDER_DATA).linkIndex();
		} else {
			return -1;
		}
	}

	public static boolean isFinalLink(ItemStack box) {
		//noinspection DataFlowIssue
		return box.has(PackagesRegistry.PACKAGE_ORDER_DATA) && box.get(PackagesRegistry.PACKAGE_ORDER_DATA).isFinalLink();
	}

	/**
	 * Ordered items and their amount in the original, combined request
	 * (Present in all non-redstone packages)
	 */
	@Nullable
	public static PackageOrderWithCrafts getOrderContext(ItemStack box) {
		if (box.has(PackagesRegistry.PACKAGE_ORDER_DATA)) {
			PackageOrderData data = box.get(PackagesRegistry.PACKAGE_ORDER_DATA);
			//noinspection DataFlowIssue
			return data.orderContext();
		} else if (box.has(PackagesRegistry.PACKAGE_ORDER_CONTEXT)) {
			return box.get(PackagesRegistry.PACKAGE_ORDER_CONTEXT);
		} else {
			return null;
		}
	}

	public static void addOrderContext(ItemStack box, PackageOrderWithCrafts orderContext) {
		box.set(PackagesRegistry.PACKAGE_ORDER_CONTEXT, orderContext);
	}

	public static boolean matchAddress(ItemStack box, String address) {
		return matchAddress(getAddress(box), address);
	}

	public static boolean matchAddress(String boxAddress, String address) {
		if (address.isBlank())
			return boxAddress.isBlank();
		if (address.equals("*") || boxAddress.equals("*"))
			return true;
		if (address.equals(boxAddress))
			return true;
		return address.matches(Glob.toRegexPattern(boxAddress)) ||
			boxAddress.matches(Glob.toRegexPattern(address));
	}

	public static String getAddress(ItemStack box) {
		return box.getOrDefault(PackagesRegistry.PACKAGE_ADDRESS, "");
	}

	public static float getWidth(ItemStack box) {
		if (box.getItem() instanceof PackageItem pi)
			return pi.style.width() / 16f;
		return 1;
	}

	public static float getHeight(ItemStack box) {
		if (box.getItem() instanceof PackageItem pi)
			return pi.style.height() / 16f;
		return 1;
	}

	public static float getHookDistance(ItemStack box) {
		if (box.getItem() instanceof PackageItem pi)
			return pi.style.riggingOffset() / 16f;
		return 1;
	}

	public static ItemStackHandler getContents(ItemStack box) {
		ItemStackHandler newInv = new ItemStackHandler(9);
		ItemContainerContents contents = box.getOrDefault(PackagesRegistry.PACKAGE_CONTENTS, ItemContainerContents.EMPTY);
		ItemHelper.fillItemStackHandler(contents, newInv);
		return newInv;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext tooltipContext, TooltipDisplay display,
		Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		super.appendHoverText(stack, tooltipContext, display, tooltipComponents, tooltipFlag);

		if (stack.has(PackagesRegistry.PACKAGE_ADDRESS))
			tooltipComponents.accept(Component.literal("→ " + stack.get(PackagesRegistry.PACKAGE_ADDRESS))
				.withStyle(ChatFormatting.GOLD));

		// From stack nbt
		if (!stack.has(PackagesRegistry.PACKAGE_CONTENTS))
			return;

		int visibleNames = 0;
		int skippedNames = 0;
		ItemStackHandler contents = getContents(stack);
		for (int i = 0; i < contents.getSlots(); i++) {
			ItemStack itemstack = contents.getStackInSlot(i);
			if (itemstack.isEmpty())
				continue;
			if (itemstack.getItem() instanceof SpawnEggItem)
				continue;
			if (visibleNames > 2) {
				skippedNames++;
				continue;
			}

			visibleNames++;
			tooltipComponents.accept(itemstack.getHoverName()
				.copy()
				.append(" x")
				.append(String.valueOf(itemstack.getCount()))
				.withStyle(ChatFormatting.GRAY));
		}

		if (skippedNames > 0)
			tooltipComponents.accept(Component.translatable("container.shulkerBox.more", skippedNames)
				.withStyle(ChatFormatting.ITALIC));
	}

	// Throwing stuff

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	/** Create's open: the box pops, its contents go into the player's inventory (spawn eggs spawn their mob). */
	public InteractionResult open(Level worldIn, Player playerIn, InteractionHand handIn) {
		ItemStack box = playerIn.getItemInHand(handIn);
		ItemStackHandler contents = getContents(box);

		playerIn.setItemInHand(handIn, box.getCount() <= 1 ? ItemStack.EMPTY : box.copyWithCount(box.getCount() - 1));

		if (worldIn instanceof ServerLevel serverLevel) {
			for (int i = 0; i < contents.getSlots(); i++) {
				ItemStack itemstack = contents.getStackInSlot(i);
				if (itemstack.isEmpty())
					continue;

				EntityType<?> entitytype = SpawnEggItem.getType(itemstack);
				if (entitytype != null) {
					Entity entity = entitytype.spawn(serverLevel, itemstack, null, BlockPos.containing(playerIn.position()
						.add(playerIn.getLookAngle()
							.multiply(1, 0, 1)
							.normalize())),
						EntitySpawnReason.SPAWN_ITEM_USE, false, false);
					if (entity != null)
						itemstack.shrink(1);
				}

				playerIn.getInventory()
					.placeItemBackInInventory(itemstack.copy(), Prediction.SERVER_ONLY);
			}

			Vec3 position = playerIn.position();
			PackageSounds.packagePop(worldIn, playerIn.blockPosition());
			for (int i = 0; i < 10; i++) {
				Vec3 motion = VecHelper.offsetRandomly(Vec3.ZERO, worldIn.getRandom(), .125f);
				Vec3 pos = position.add(0, 0.5, 0)
					.add(playerIn.getLookAngle()
						.scale(.5))
					.add(motion.scale(4));
				serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.WOOL.pick(DyeColor.BROWN)), pos.x, pos.y,
					pos.z, 0, motion.x, motion.y, motion.z, 1);
			}
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null)
			return InteractionResult.FAIL;
		if (player.isShiftKeyDown()) {
			return open(context.getLevel(), player, context.getHand());
		}

		Vec3 point = context.getClickLocation();
		float h = style.height() / 16f;
		float r = style.width() / 2f / 16f;

		if (context.getClickedFace() == Direction.DOWN)
			point = point.subtract(0, h + .25f, 0);
		else if (context.getClickedFace()
			.getAxis()
			.isHorizontal())
			point = point.add(Vec3.atLowerCornerOf(context.getClickedFace()
					.getUnitVec3i())
				.scale(r));

		AABB scanBB = new AABB(point, point).inflate(r, 0, r)
			.expandTowards(0, h, 0);
		Level world = context.getLevel();
		if (!world.getEntities(PackagesRegistry.PACKAGE, scanBB, e -> true)
			.isEmpty())
			return super.useOn(context);

		if (world.isClientSide())
			return InteractionResult.SUCCESS;
		PackageEntity packageEntity = new PackageEntity(world, point.x, point.y, point.z);
		ItemStack itemInHand = context.getItemInHand();
		packageEntity.setBox(itemInHand.copy());
		world.addFreshEntity(packageEntity);
		itemInHand.shrink(1);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level world, Player player, InteractionHand hand) {
		if (player.isShiftKeyDown())
			return open(world, player, hand);
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level world, LivingEntity entity, int ticks) {
		if (!(entity instanceof Player player))
			return false;
		int i = this.getUseDuration(stack, entity) - ticks;
		if (i < 0)
			return false;

		float f = getPackageVelocity(i);
		if (f < 0.1D)
			return false;
		if (world.isClientSide())
			return false;

		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
			SoundSource.NEUTRAL, 0.5F, 0.5F);

		ItemStack copy = stack.copy();
		if (!player.getAbilities().instabuild)
			stack.shrink(1);

		Vec3 vec = new Vec3(entity.getX(), entity.getY() + entity.getBoundingBox()
			.getYsize() / 2f, entity.getZ());
		Vec3 motion = entity.getLookAngle()
			.scale(f * 2);
		vec = vec.add(motion);

		PackageEntity packageEntity = new PackageEntity(world, vec.x, vec.y, vec.z);
		packageEntity.setBox(copy);
		packageEntity.setDeltaMovement(motion);
		packageEntity.tossedBy = new WeakReference<>(player);
		world.addFreshEntity(packageEntity);
		return true;
	}

	public static float getPackageVelocity(int p_185059_0_) {
		float f = (float) p_185059_0_ / 20.0F;
		f = (f * f + f * 2.0F) / 3.0F;
		if (f > 1.0F)
			f = 1.0F;
		return f;
	}

	public record PackageOrderData(int orderId, int linkIndex, boolean isFinalLink, int fragmentIndex,
		boolean isFinal, @Nullable PackageOrderWithCrafts orderContext) {
		public PackageOrderData(int orderId, int linkIndex, boolean isFinalLink, int fragmentIndex,
			boolean isFinal, Optional<PackageOrderWithCrafts> orderContext) {
			this(orderId, linkIndex, isFinalLink, fragmentIndex, isFinal, orderContext.orElse(null));
		}

		public static final Codec<PackageOrderData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("order_id").forGetter(PackageOrderData::orderId),
			Codec.INT.fieldOf("link_index").forGetter(PackageOrderData::linkIndex),
			Codec.BOOL.fieldOf("is_final_link").forGetter(PackageOrderData::isFinalLink),
			Codec.INT.fieldOf("fragment_index").forGetter(PackageOrderData::fragmentIndex),
			Codec.BOOL.fieldOf("is_final").forGetter(PackageOrderData::isFinal),
			PackageOrderWithCrafts.CODEC.optionalFieldOf("order_context")
				.forGetter(i -> Optional.ofNullable(i.orderContext))
		).apply(instance, PackageOrderData::new));
	}
}

package dev.createpolymer.foundation.blockEntity.behaviour.filtering;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsBoard;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.InvWrapper;
import dev.createpolymer.logistics.filter.FilterItem;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.VecHelper;
import org.jetbrains.annotations.NotNull;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class FilteringBehaviour extends BlockEntityBehaviour implements ValueSettingsBehaviour {
	public static final BehaviourType<FilteringBehaviour> TYPE = new BehaviourType<>();

	public MutableComponent customLabel;
	ValueBoxTransform slotPositioning;
	boolean showCount;

	protected FilterItemStack filter;
	public int count;
	public boolean upTo;
	private Predicate<ItemStack> predicate;
	private Consumer<ItemStack> callback;
	private Supplier<Boolean> isActive;
	private Supplier<Boolean> showCountPredicate;

	boolean recipeFilter;
	boolean fluidFilter;

	public FilteringBehaviour(SmartBlockEntity be, ValueBoxTransform slot) {
		super(be);
		filter = FilterItemStack.empty();
		slotPositioning = slot;
		showCount = false;
		callback = stack -> {
		};
		predicate = stack -> true;
		isActive = () -> true;
		count = 64;
		showCountPredicate = () -> showCount;
		recipeFilter = false;
		fluidFilter = false;
		upTo = true;
	}

	@Override
	public void write(ValueOutput nbt) {
		nbt.store("Filter", ItemStack.OPTIONAL_CODEC, getFilter());
		nbt.putInt("FilterAmount", count);
		nbt.putBoolean("UpTo", upTo);
		super.write(nbt);
	}

	@Override
	public void read(ValueInput nbt) {
		filter = FilterItemStack.of(nbt.read("Filter", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
		count = nbt.getIntOr("FilterAmount", 0);
		upTo = nbt.getBooleanOr("UpTo", false);

		// Migrate from previous behaviour
		if (count == 0) {
			upTo = true;
			count = getMaxStackSize();
		}

		super.read(nbt);
	}

	public FilteringBehaviour withCallback(Consumer<ItemStack> filterCallback) {
		callback = filterCallback;
		return this;
	}

	public FilteringBehaviour withPredicate(Predicate<ItemStack> filterPredicate) {
		predicate = filterPredicate;
		return this;
	}

	public FilteringBehaviour forRecipes() {
		recipeFilter = true;
		return this;
	}

	public FilteringBehaviour forFluids() {
		fluidFilter = true;
		return this;
	}

	public FilteringBehaviour onlyActiveWhen(Supplier<Boolean> condition) {
		isActive = condition;
		return this;
	}

	public FilteringBehaviour showCountWhen(Supplier<Boolean> condition) {
		showCountPredicate = condition;
		return this;
	}

	public FilteringBehaviour showCount() {
		showCount = true;
		return this;
	}

	public boolean setFilter(Direction face, ItemStack stack) {
		return setFilter(stack);
	}

	public void setLabel(MutableComponent label) {
		this.customLabel = label;
	}

	public boolean setFilter(ItemStack stack) {
		ItemStack filter = stack.copy();
		if (!filter.isEmpty() && !predicate.test(filter))
			return false;
		this.filter = FilterItemStack.of(filter);
		if (!upTo && !stack.isEmpty())
			count = Math.min(count, stack.getMaxStackSize());
		callback.accept(filter);
		blockEntity.setChanged();
		blockEntity.sendData();
		return true;
	}

	@Override
	public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
		if (getValueSettings().equals(settings))
			return;
		count = Mth.clamp(settings.value(), 1, getMaxStackSize());
		upTo = settings.row() == 0;
		blockEntity.setChanged();
		blockEntity.sendData();
		playFeedbackSound(this);
	}

	@Override
	public ValueSettings getValueSettings() {
		return new ValueSettings(upTo ? 0 : 1, count == 0 ? getMaxStackSize() : count);
	}

	@Override
	public void destroy() {
		if (filter.isFilterItem()) {
			Vec3 pos = VecHelper.getCenterOf(getPos());
			Level world = getWorld();
			world.addFreshEntity(new ItemEntity(world, pos.x, pos.y, pos.z, getFilter().copy()));
		}
		super.destroy();
	}

	public int getMaxStackSize() {
		return getMaxStackSize(getFilter());
	}

	public int getMaxStackSize(Direction face) {
		return getMaxStackSize(getFilter(face));
	}

	public int getMaxStackSize(ItemStack filter) {
		if (filter.isEmpty())
			return 64;
		return filter.getMaxStackSize();
	}

	public ItemStack getFilter(Direction side) {
		return getFilter();
	}

	public ItemStack getFilter() {
		return filter.item();
	}

	public boolean isCountVisible() {
		return showCountPredicate.get() && getMaxStackSize() > 1;
	}

	public boolean test(ItemStack stack) {
		return !isActive() || filter.test(blockEntity.getLevel(), stack);
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

	@Override
	public boolean testHit(Vec3 hit) {
		BlockState state = blockEntity.getBlockState();
		Vec3 localHit = hit.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
		return slotPositioning.testHit(getWorld(), getPos(), state, localHit);
	}

	public int getAmount() {
		return count;
	}

	public boolean anyAmount() {
		return count == 0;
	}

	@Override
	public boolean acceptsValueSettings() {
		return isCountVisible();
	}

	@Override
	public boolean isActive() {
		return isActive.get();
	}

	@Override
	public ValueBoxTransform getSlotPositioning() {
		return slotPositioning;
	}

	@Override
	public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
		int maxAmount = getMaxStackSize(hitResult.getDirection());
		return new ValueSettingsBoard(translate("logistics.filter.extracted_amount"), maxAmount, 16,
			List.of(translate("logistics.filter.up_to"), translate("logistics.filter.exactly")),
			new ValueSettingsFormatter(this::formatValue));
	}

	public MutableComponent formatValue(ValueSettings value) {
		if (value.row() == 0 && value.value() == getMaxStackSize())
			return translate("logistics.filter.any_amount_short");
		return Component.literal(((value.row() == 0) ? "≤" : "=") + Math.max(1, value.value()));
	}

	@Override
	public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
		Level level = getWorld();
		BlockPos pos = getPos();
		ItemStack itemInHand = player.getItemInHand(hand);
		ItemStack toApply = itemInHand.copy();

		if (!canShortInteract(toApply))
			return;
		if (level.isClientSide())
			return;

		if (getFilter(side).getItem() instanceof FilterItem) {
			if (!player.isCreative() || ItemHelper
				.extract(new InvWrapper(player.getInventory()),
					stack -> ItemStack.isSameItemSameComponents(stack, getFilter(side)), true)
				.isEmpty())
				player.getInventory()
					.placeItemBackInInventory(getFilter(side).copy(), Prediction.SERVER_ONLY);
		}

		if (toApply.getItem() instanceof FilterItem)
			toApply.setCount(1);

		if (!setFilter(side, toApply)) {
			player.sendOverlayMessage(translate("logistics.filter.invalid_item"));
			level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1, .5f);
			return;
		}

		if (!player.isCreative()) {
			if (toApply.getItem() instanceof FilterItem) {
				if (itemInHand.getCount() == 1)
					player.setItemInHand(hand, ItemStack.EMPTY);
				else
					itemInHand.shrink(1);
			}
		}

		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .25f, .1f);
	}

	public boolean canShortInteract(ItemStack toApply) {
		if (toApply.is(ModItems.WRENCH))
			return false;
		if (BuiltInRegistries.ITEM.getKey(toApply.getItem()).equals(CreatePolymer.id("mechanical_arm")))
			return false;
		return true;
	}

	public MutableComponent getLabel() {
		if (customLabel != null)
			return customLabel;
		return translate(
			recipeFilter ? "logistics.recipe_filter" : fluidFilter ? "logistics.fluid_filter" : "logistics.filter");
	}

	public MutableComponent getTip() {
		return translate(filter.isEmpty() ? "logistics.filter.click_to_set" : "logistics.filter.click_to_replace");
	}

	public MutableComponent getAmountTip() {
		return translate("logistics.filter.hold_to_set_amount");
	}

	public MutableComponent getCountLabelForValueBox() {
		return Component.literal(isCountVisible() ? upTo &&
			getMaxStackSize() == count ? "*" : String.valueOf(count) : "");
	}

	public boolean isRecipeFilter() {
		return recipeFilter;
	}

	@Override
	public String getClipboardKey() {
		return "Filtering";
	}

	@Override
	public boolean writeToClipboard(HolderLookup.@NotNull Provider registries, CompoundTag tag, Direction side) {
		ValueSettingsBehaviour.super.writeToClipboard(registries, tag, side);
		ItemStack filter = getFilter(side);
		tag.store("Filter", ItemStack.OPTIONAL_CODEC, registries.createSerializationContext(NbtOps.INSTANCE), filter);
		return true;
	}

	@Override
	public boolean readFromClipboard(HolderLookup.@NotNull Provider registries, CompoundTag tag, Player player, Direction side, boolean simulate) {
		if (!mayInteract(player))
			return false;
		boolean upstreamResult = ValueSettingsBehaviour.super.readFromClipboard(registries, tag, player, side, simulate);
		if (!tag.contains("Filter"))
			return upstreamResult;
		if (simulate)
			return true;
		if (getWorld().isClientSide())
			return true;

		ItemStack refund = ItemStack.EMPTY;
		if (getFilter(side).getItem() instanceof FilterItem && !player.isCreative())
			refund = getFilter(side).copy();

		ItemStack copied = tag.read("Filter", ItemStack.OPTIONAL_CODEC, registries.createSerializationContext(NbtOps.INSTANCE))
			.orElse(ItemStack.EMPTY);

		if (copied.getItem() instanceof FilterItem filterType && !player.isCreative()) {
			InvWrapper inv = new InvWrapper(player.getInventory());

			for (boolean preferStacksWithoutData : Iterate.trueAndFalse) {
				if (refund.getItem() != filterType && ItemHelper
					.extract(inv, stack -> stack.getItem() == filterType && preferStacksWithoutData == stack.getComponentsPatch().isEmpty(),
						1, false)
					.isEmpty())
					continue;

				if (!refund.isEmpty() && refund.getItem() != filterType)
					player.getInventory()
						.placeItemBackInInventory(refund, Prediction.SERVER_ONLY);

				return setFilter(side, copied);
			}

			player.sendOverlayMessage(Lang.builder(CreatePolymer.MOD_ID)
				.translate("logistics.filter.requires_item_in_inventory", copied.getHoverName()
					.copy()
					.withStyle(ChatFormatting.WHITE))
				.component()
				.copy()
				.withStyle(ChatFormatting.RED));
			getWorld().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1, .5f);
			return false;
		}

		if (!refund.isEmpty())
			player.getInventory()
				.placeItemBackInInventory(refund, Prediction.SERVER_ONLY);

		return setFilter(side, copied);
	}

	@Override
	public int netId() {
		return 1;
	}

	private static MutableComponent translate(String key) {
		return Lang.builder(CreatePolymer.MOD_ID).translate(key).component();
	}
}

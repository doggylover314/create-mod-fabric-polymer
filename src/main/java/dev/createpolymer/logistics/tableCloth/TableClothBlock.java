package dev.createpolymer.logistics.tableCloth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsInputHandler;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.logistics.depot.DepotRenderer;
import dev.createpolymer.logistics.redstoneRequester.AutoRequestData;
import dev.createpolymer.registry.feature.StockRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TableClothBlock extends Block implements IWrenchable, IBE<TableClothBlockEntity>, PolymerModelBlock {

	public static final BooleanProperty HAS_BE = BooleanProperty.create("entity");

	/** Create's AllShapes.TABLE_CLOTH and TABLE_CLOTH_OCCLUSION */
	public static final VoxelShape TABLE_CLOTH = ShapeUtil.box(-1, -9, -1, 17, 1, 17);
	public static final VoxelShape TABLE_CLOTH_OCCLUSION = ShapeUtil.box(0, 0, 0, 16, 1, 16);

	@Nullable
	private DyeColor colour;
	/** The name of the textures and models of this cloth: the colour, or andesite, brass or copper. */
	private final String type;

	public TableClothBlock(Properties pProperties, DyeColor colour) {
		super(pProperties);
		this.colour = colour;
		this.type = colour.getSerializedName();
		registerDefaultState(defaultBlockState().setValue(HAS_BE, false));
	}

	public TableClothBlock(Properties pProperties, String type) {
		super(pProperties);
		this.type = type;
		registerDefaultState(defaultBlockState().setValue(HAS_BE, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(HAS_BE));
	}

	@Override
	public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
		super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
		if (!(pPlacer instanceof Player player))
			return;

		AutoRequestData requestData = AutoRequestData.readFromItem(pLevel, player, pPos, pStack);
		if (requestData == null)
			return;

		pLevel.setBlockAndUpdate(pPos, pState.setValue(HAS_BE, true));
		withBlockEntityDo(pLevel, pPos, dcbe -> {
			dcbe.requestData = requestData;
			dcbe.owner = player.getUUID();
			dcbe.facing = player.getDirection()
				.getOpposite();
			dcbe.notifyUpdate();
		});
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (hitResult.getDirection() == Direction.DOWN)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		ItemStack heldItem = player.getItemInHand(hand);
		boolean shiftKeyDown = player.isShiftKeyDown();
		if (!player.mayBuild())
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		// Create's PlacementHelper: using a table cloth on a table cloth places the next one beside it
		if (heldItem.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof TableClothBlock) {
			if (shiftKeyDown)
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			List<Direction> directions = orderedByDistanceExceptYAxis(pos, hitResult.getLocation(),
				dir -> level.getBlockState(pos.relative(dir))
					.canBeReplaced());
			if (directions.isEmpty())
				return InteractionResult.SUCCESS;
			BlockPos target = pos.relative(directions.get(0));
			BlockHitResult placeAt = new BlockHitResult(Vec3.atBottomCenterOf(target), Direction.UP, target, false);
			blockItem.place(new BlockPlaceContext(level, player, hand, heldItem, placeAt));
			return InteractionResult.SUCCESS;
		}

		if ((shiftKeyDown || heldItem.isEmpty()) && !state.getValue(HAS_BE))
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		if (!level.isClientSide() && !state.getValue(HAS_BE))
			level.setBlockAndUpdate(pos, state.cycle(HAS_BE));

		return onBlockEntityUseItemOn(level, pos, dcbe -> dcbe.use(player, hitResult));
	}

	/** Catnip's IPlacementHelper.orderedByDistanceExceptAxis(pos, ray, Y, filter). */
	private static List<Direction> orderedByDistanceExceptYAxis(BlockPos pos, Vec3 ray, java.util.function.Predicate<Direction> filter) {
		Vec3 centerToRay = ray.subtract(Vec3.atCenterOf(pos));
		List<Direction> directions = new ArrayList<>();
		for (Direction d : Iterate.horizontalDirections)
			if (filter.test(d))
				directions.add(d);
		directions.sort(Comparator.comparingDouble(d -> Vec3.atLowerCornerOf(d.getUnitVec3i())
			.distanceTo(centerToRay)));
		return directions;
	}

	@Override
	protected List<ItemStack> getDrops(BlockState pState, LootParams.Builder pParams) {
		List<ItemStack> drops = super.getDrops(pState, pParams);

		if (!(pParams.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TableClothBlockEntity dcbe))
			return drops;
		if (!dcbe.isShop())
			return drops;

		for (ItemStack stack : drops) {
			if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof TableClothBlock) {
				ItemStack drop = new ItemStack(this);
				dcbe.requestData.writeToItem(dcbe.getBlockPos(), drop);
				return List.of(drop);
			}
		}

		return drops;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return TABLE_CLOTH;
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState pState) {
		return TABLE_CLOTH_OCCLUSION;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		return TABLE_CLOTH_OCCLUSION;
	}

	@Override
	protected boolean canSurvive(BlockState p_152922_, LevelReader p_152923_, BlockPos p_152924_) {
		return true;
	}

	@Nullable
	public DyeColor getColor() {
		return colour;
	}

	public String getType() {
		return type;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(HAS_BE) ? IBE.super.newBlockEntity(pos, state) : null;
	}

	@Override
	public Class<TableClothBlockEntity> getBlockEntityClass() {
		return TableClothBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends TableClothBlockEntity> getBlockEntityType() {
		return StockRegistry.TABLE_CLOTH_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.CARPET.white().defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, type);
	}

	/**
	 * Create's TableClothModel (the drapes on the sides that are not covered by a neighbour, and the corners between
	 * two drapes), TableClothRenderer (the price tag and the displayed items) and the filter in the price tag.
	 */
	public static class Model extends BaseModel {
		private final String type;
		private final Part base;
		private final Part[] flaps = new Part[4];
		private final Part[] corners = new Part[4];
		private final Part priceTag;
		private final List<ItemSlot> slots = new ArrayList<>();
		private final FilterDisplay filter = new FilterDisplay(this);
		private final InteractionElement tagHitbox;
		private boolean tagHitboxAdded;
		private int shownVersion = -1;
		private boolean[] culled;
		private boolean shownShop;
		private boolean shownOccluded;
		private Direction shownFacing;

		public Model(BlockState state, String type) {
			super(state);
			this.type = type;
			base = addPart(ModelItems.block("table_cloth/" + type + "_base"));
			for (Direction side : Iterate.horizontalDirections) {
				flaps[side.get2DDataValue()] = addPart(ItemStack.EMPTY);
				corners[side.get2DDataValue()] = addPart(ItemStack.EMPTY);
			}
			priceTag = addPart(ItemStack.EMPTY);
			tagHitbox = new InteractionElement(new VirtualElement.InteractionHandler() {
				@Override
				public void interact(ServerPlayer player, InteractionHand hand, Vec3 pos, boolean usingSecondaryAction) {
					clickPriceTag(player, hand);
				}
			});
			for (int i = 0; i < 4; i++)
				slots.add(new ItemSlot());
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			updateDrapes(level, pos, time);

			TableClothBlockEntity cloth = be instanceof TableClothBlockEntity c ? c : null;
			if (cloth == null) {
				if (shownVersion != -1) {
					shownVersion = -1;
					priceTag.setItem(ItemStack.EMPTY);
					slots.forEach(ItemSlot::hide);
					removeTagHitbox();
					shownShop = false;
				}
				return;
			}

			filter.update(cloth);

			boolean shop = cloth.isShop();
			if (shownVersion == cloth.getDataVersion() && shownShop == shop && shownOccluded == cloth.sideOccluded
				&& shownFacing == cloth.facing)
				return;
			shownVersion = cloth.getDataVersion();
			shownShop = shop;
			shownOccluded = cloth.sideOccluded;
			shownFacing = cloth.facing;

			float rotationInRadians = (float) Math.toRadians(180 - cloth.facing.toYRot());
			if (shop) {
				priceTag.setItem(ModelItems.block("table_cloth/" + (cloth.sideOccluded ? "price_tag_top" : "price_tag_side")));
				priceTag.orientation(new Quaternionf().rotationY(rotationInRadians))
					.apply(0);
				addTagHitbox(cloth, level, pos);
			} else {
				priceTag.setItem(ItemStack.EMPTY);
				removeTagHitbox();
			}

			cloth.invalidateItemsForRender();
			List<ItemStack> stacks = cloth.getItemsForRender();
			for (int i = 0; i < slots.size(); i++) {
				if (i >= stacks.size()) {
					slots.get(i)
						.hide();
					continue;
				}
				ItemStack entry = stacks.get(i);
				Matrix4f m = new Matrix4f().rotateY(rotationInRadians);
				m.translate(0, 3 / 16f - .5f, 0);

				if (stacks.size() > 1) {
					m.rotateY((float) Math.toRadians(i * (360f / stacks.size()) + 45f));
					m.translate(0, i % 2 == 0 ? -0.005f : 0, 5 / 16f);
					m.rotateY((float) Math.toRadians(-i * (360f / stacks.size()) - 45f));
				}

				if (!DepotRenderer.isBlockItem(entry))
					m.rotateY(-rotationInRadians + (float) Math.PI);

				slots.get(i)
					.show(entry, DepotRenderer.renderItem(m, entry, 0, null, true));
			}
		}

		private void updateDrapes(ServerLevel level, BlockPos pos, long time) {
			if (culled != null && time % 10 != 0)
				return;
			boolean[] newCulled = new boolean[4];
			for (Direction side : Iterate.horizontalDirections)
				newCulled[side.get2DDataValue()] = !Block.shouldRenderFace(state, level.getBlockState(pos.relative(side)), side);
			if (culled != null && java.util.Arrays.equals(culled, newCulled))
				return;
			culled = newCulled;

			for (Direction side : Iterate.horizontalDirections) {
				int index = side.get2DDataValue();
				flaps[index].setItem(culled[index] ? ItemStack.EMPTY
					: ModelItems.block("table_cloth/" + type + "_flap_" + side.getName()));
				boolean corner = !culled[index] && !culled[side.getClockWise()
					.get2DDataValue()];
				corners[index].setItem(corner ? ModelItems.block("table_cloth/" + type + "_corner_" + cornerName(side))
					: ItemStack.EMPTY);
			}
		}

		private static String cornerName(Direction side) {
			return switch (side) {
				case SOUTH -> "sw";
				case WEST -> "nw";
				case NORTH -> "ne";
				default -> "se";
			};
		}

		private void addTagHitbox(TableClothBlockEntity cloth, ServerLevel level, BlockPos pos) {
			ValueBoxTransform slot = cloth.priceTag.getSlotPositioning();
			Vec3 offset = slot.getLocalOffset(level, pos, cloth.getBlockState());
			if (offset == null) {
				removeTagHitbox();
				return;
			}
			float size = .45f;
			tagHitbox.setSize(size, size);
			tagHitbox.setOffset(offset.subtract(.5, .5 + size / 2, .5));
			if (!tagHitboxAdded) {
				tagHitboxAdded = true;
				addElement(tagHitbox);
			}
		}

		private void removeTagHitbox() {
			if (!tagHitboxAdded)
				return;
			tagHitboxAdded = false;
			removeElement(tagHitbox);
		}

		/** The price tag hangs outside the block, where vanilla clients cannot aim; the hitbox stands in for it. */
		private void clickPriceTag(ServerPlayer player, InteractionHand hand) {
			BlockAwareAttachment attachment = BlockAwareAttachment.get(this);
			if (attachment == null || !(attachment.getWorld() instanceof ServerLevel level))
				return;
			BlockPos pos = attachment.getBlockPos();
			if (!(level.getBlockEntity(pos) instanceof TableClothBlockEntity cloth))
				return;
			Vec3 offset = cloth.priceTag.getSlotPositioning()
				.getLocalOffset(level, pos, cloth.getBlockState());
			if (offset == null)
				return;
			BlockHitResult hit = new BlockHitResult(Vec3.atLowerCornerOf(pos)
				.add(offset), Direction.UP, pos, false);
			if (ValueSettingsInputHandler.onBlockActivated(player, level, hand, hit) == InteractionResult.PASS)
				cloth.use(player, hit);
		}

		/** The display entities of one drawn stack, one per layer (DepotRenderer's slot). */
		private class ItemSlot {
			private final List<Part> layers = new ArrayList<>();
			private ItemStack shown = ItemStack.EMPTY;
			private final List<Matrix4f> last = new ArrayList<>();

			void show(ItemStack stack, List<Matrix4f> matrices) {
				ItemStack one = stack.copyWithCount(1);
				for (int i = 0; i < matrices.size(); i++) {
					if (i == layers.size()) {
						Part part = new Part(ItemStack.EMPTY);
						part.element.setItemDisplayContext(ItemDisplayContext.FIXED);
						addElement(part.element);
						layers.add(part);
						last.add(null);
					}
					Part part = layers.get(i);
					if (!ItemStack.isSameItemSameComponents(shown, one) || last.get(i) == null)
						part.setItem(one);
					part.setMatrix(matrices.get(i), 0);
					last.set(i, matrices.get(i));
				}
				shown = one;
				for (int i = matrices.size(); i < layers.size(); i++)
					hideLayer(i);
			}

			void hide() {
				shown = ItemStack.EMPTY;
				for (int i = 0; i < layers.size(); i++)
					hideLayer(i);
			}

			private void hideLayer(int i) {
				if (last.get(i) == null)
					return;
				layers.get(i)
					.setItem(ItemStack.EMPTY);
				last.set(i, null);
			}
		}
	}
}

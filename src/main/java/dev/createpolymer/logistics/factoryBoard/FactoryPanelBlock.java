package dev.createpolymer.logistics.factoryBoard;

import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.createpolymer.api.schematic.requirement.SpecialBlockItemRequirement;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBlockItem;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.registry.feature.FactoryGaugeRegistry;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Create's FactoryPanelBlock. Players see a barrier with display entities on it ({@link FactoryPanelModel}), which has
 * two consequences: the hit location the client sends is on the whole block instead of on the panels, so the
 * interactions here ray-cast again against the real panel shapes ({@link #pickLocation}); and breaking one of several
 * panels is cancelled through Fabric's break event instead of NeoForge's onDestroyedByPlayer.
 */
public class FactoryPanelBlock extends FaceAttachedHorizontalDirectionalBlock
	implements SpecialBlockItemRequirement, ProperWaterloggedBlock, IBE<FactoryPanelBlockEntity>, IWrenchable, PolymerModelBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	/** Create's AllShapes.FACTORY_PANEL_FALLBACK */
	public static final Map<Direction, VoxelShape> FACTORY_PANEL_FALLBACK =
		ShapeUtil.forDirectional(ShapeUtil.box(0, 0, 0, 16, 2, 16));

	public enum PanelSlot implements StringRepresentable {
		TOP_LEFT(1, 1),
		TOP_RIGHT(0, 1),
		BOTTOM_LEFT(1, 0),
		BOTTOM_RIGHT(0, 0);

		public static final Codec<PanelSlot> CODEC = StringRepresentable.fromValues(PanelSlot::values);

		public final int xOffset;
		public final int yOffset;

		PanelSlot(int xOffset, int yOffset) {
			this.xOffset = xOffset;
			this.yOffset = yOffset;
		}

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}
	}

	public static enum PanelState {
		PASSIVE, ACTIVE
	}

	public static enum PanelType {
		NETWORK, PACKAGER
	}

	public FactoryPanelBlock(Properties p_53182_) {
		super(p_53182_);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false)
			.setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(FACE, FACING, WATERLOGGED, POWERED));
	}

	@Override
	protected boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		return canAttachLenient(pLevel, pPos, getConnectedDirection(pState).getOpposite());
	}

	public static boolean canAttachLenient(LevelReader pReader, BlockPos pPos, Direction pDirection) {
		BlockPos blockpos = pPos.relative(pDirection);
		return !pReader.getBlockState(blockpos)
			.getCollisionShape(pReader, blockpos)
			.isEmpty();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement = super.getStateForPlacement(pContext);
		if (stateForPlacement == null)
			return null;
		if (stateForPlacement.getValue(FACE) == AttachFace.FLOOR)
			stateForPlacement = stateForPlacement.setValue(FACING, stateForPlacement.getValue(FACING)
				.getOpposite());

		Level level = pContext.getLevel();
		BlockPos pos = pContext.getClickedPos();
		BlockState blockState = level.getBlockState(pos);
		FactoryPanelBlockEntity fpbe = getBlockEntity(level, pos);

		Vec3 location = pContext.getClickLocation();
		if (blockState.is(this) && location != null && fpbe != null) {
			if (!level.isClientSide()) {
				Player pPlayer = pContext.getPlayer();
				PanelSlot targetedSlot = getTargetedSlot(pos, blockState, pickLocation(pPlayer, level, pos, blockState, location));
				ItemStack panelItem = FactoryPanelBlockItem.fixCtrlCopiedStack(pContext.getItemInHand());
				UUID networkFromStack = LogisticallyLinkedBlockItem.networkFromStack(panelItem);

				if (fpbe.addPanel(targetedSlot, networkFromStack) && pPlayer != null) {
					pPlayer.sendOverlayMessage(translate("logistically_linked.connected"));

					if (!pPlayer.isCreative()) {
						panelItem.shrink(1);
						if (panelItem.isEmpty())
							pPlayer.setItemInHand(pContext.getHand(), ItemStack.EMPTY);
					}
				}
			}
			stateForPlacement = blockState;
		}

		return withWater(stateForPlacement, pContext);
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		PanelSlot slot = getTargetedSlot(pos, state, pickLocation(player, world, pos, state, context.getClickLocation()));

		if (!(world instanceof ServerLevel))
			return InteractionResult.SUCCESS;

		return onBlockEntityUse(world, pos, be -> {
			FactoryPanelBehaviour behaviour = be.panels.get(slot);
			if (behaviour == null || !behaviour.isActive())
				return InteractionResult.SUCCESS;

			if (player != null && !PlayerBlockBreakEvents.BEFORE.invoker()
				.beforeBlockBreak(world, player, pos, world.getBlockState(pos), be))
				return InteractionResult.SUCCESS;

			if (!be.removePanel(slot))
				return InteractionResult.SUCCESS;

			if (player != null && !player.isCreative())
				player.getInventory()
					.placeItemBackInInventory(new ItemStack(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM), Prediction.SERVER_ONLY);

			ModSounds.wrenchRemove(world, pos);
			if (be.activePanels() == 0)
				world.destroyBlock(pos, false);

			return InteractionResult.SUCCESS;
		});
	}

	/** With a wrench in hand, a click cycles the way the arrows of the clicked gauge are bent. */
	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		if (player == null || world.isClientSide())
			return InteractionResult.SUCCESS;
		PanelSlot slot = getTargetedSlot(pos, state, pickLocation(player, world, pos, state, context.getClickLocation()));
		withBlockEntityDo(world, pos, be -> {
			FactoryPanelBehaviour behaviour = be.panels.get(slot);
			if (behaviour != null && behaviour.isActive() && behaviour.targeting.size() + behaviour.targetedByLinks.size() > 0)
				behaviour.onShortInteract(player, context.getHand(), context.getClickedFace(),
					new BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, false));
		});
		return InteractionResult.SUCCESS;
	}

	@Override
	public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer,
		ItemStack pStack) {
		super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
		if (pPlacer == null)
			return;
		double range = pPlacer.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
		HitResult hitResult = pPlacer.pick(range, 1, false);
		Vec3 location = hitResult.getLocation();
		if (location == null)
			return;
		PanelSlot initialSlot = getTargetedSlot(pPos, pState, location);
		withBlockEntityDo(pLevel, pPos,
			fpbe -> fpbe.addPanel(initialSlot, LogisticallyLinkedBlockItem.networkFromStack(pStack)));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (player == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!stack.is(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		Vec3 location = hitResult.getLocation();
		if (location == null)
			return InteractionResult.SUCCESS;

		if (!FactoryPanelBlockItem.isTuned(stack)) {
			FactoryGaugeRegistry.deny(level, pos);
			player.sendOverlayMessage(translate("factory_panel.tune_before_placing"));
			return InteractionResult.FAIL;
		}

		PanelSlot newSlot = getTargetedSlot(pos, state, pickLocation(player, level, pos, state, location));
		withBlockEntityDo(level, pos, fpbe -> {
			if (!fpbe.addPanel(newSlot,
				LogisticallyLinkedBlockItem.networkFromStack(FactoryPanelBlockItem.fixCtrlCopiedStack(stack))))
				return;
			player.sendOverlayMessage(translate("logistically_linked.connected"));
			level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS);
			if (player.isCreative())
				return;
			stack.shrink(1);
			if (stack.isEmpty())
				player.setItemInHand(hand, ItemStack.EMPTY);
		});
		return InteractionResult.SUCCESS;
	}

	/**
	 * Create's onDestroyedByPlayer: breaking a block that holds several panels only removes the one the player
	 * looks at. Returns whether a panel was removed, in which case the block itself must stay.
	 */
	public boolean tryDestroySubPanelFirst(BlockState state, Level level, BlockPos pos, Player player) {
		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
		HitResult hitResult = player.pick(range, 1, false);
		Vec3 location = hitResult.getLocation();
		PanelSlot destroyedSlot = getTargetedSlot(pos, state, location);
		return InteractionResult.SUCCESS == onBlockEntityUse(level, pos, fpbe -> {
			if (fpbe.activePanels() < 2)
				return InteractionResult.FAIL;
			if (!fpbe.removePanel(destroyedSlot))
				return InteractionResult.FAIL;
			if (!player.isCreative())
				popResource(level, pos, new ItemStack(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM));
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	protected boolean isSignalSource(BlockState pState) {
		return true;
	}

	@Override
	protected int getSignal(BlockState pBlockState, BlockGetter pBlockAccess, BlockPos pPos, Direction pSide) {
		return pBlockState.getValue(POWERED) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState pBlockState, BlockGetter pBlockAccess, BlockPos pPos, Direction pSide) {
		return pBlockState.getValue(POWERED) && getConnectedDirection(pBlockState) == pSide ? 15 : 0;
	}

	@Override
	protected boolean canBeReplaced(BlockState pState, BlockPlaceContext pUseContext) {
		if (!pUseContext.getItemInHand()
			.is(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM))
			return false;
		Vec3 location = pUseContext.getClickLocation();

		BlockPos pos = pUseContext.getClickedPos();
		PanelSlot slot = getTargetedSlot(pos, pState,
			pickLocation(pUseContext.getPlayer(), pUseContext.getLevel(), pos, pState, location));
		FactoryPanelBlockEntity blockEntity = getBlockEntity(pUseContext.getLevel(), pos);

		if (blockEntity == null)
			return false;
		return !blockEntity.panels.get(slot)
			.isActive();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		if (pContext instanceof EntityCollisionContext ecc && ecc.getEntity() == null)
			return getShape(pState, pLevel, pPos, pContext);
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		FactoryPanelBlockEntity blockEntity = getBlockEntity(pLevel, pPos);
		if (blockEntity != null)
			return blockEntity.getShape();
		return FACTORY_PANEL_FALLBACK.get(getConnectedDirection(pState));
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pCurrentPos,
		Direction pFacing, BlockPos pFacingPos, BlockState pFacingState, RandomSource random) {
		updateWater(level, ticks, pState, pCurrentPos);
		return super.updateShape(pState, level, ticks, pCurrentPos, pFacing, pFacingPos, pFacingState, random);
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	public static Direction connectedDirection(BlockState state) {
		return getConnectedDirection(state);
	}

	public static PanelSlot getTargetedSlot(BlockPos pos, BlockState blockState, Vec3 clickLocation) {
		double bestDistance = Double.MAX_VALUE;
		PanelSlot bestSlot = PanelSlot.BOTTOM_LEFT;
		Vec3 localClick = clickLocation.subtract(Vec3.atLowerCornerOf(pos));
		float xRot = Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(blockState);
		float yRot = Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(blockState);

		for (PanelSlot slot : PanelSlot.values()) {
			Vec3 vec = new Vec3(.25 + slot.xOffset * .5, 0, .25 + slot.yOffset * .5);
			vec = VecHelper.rotateCentered(vec, 180, Axis.Y);
			vec = VecHelper.rotateCentered(vec, xRot + 90, Axis.X);
			vec = VecHelper.rotateCentered(vec, yRot, Axis.Y);

			double diff = vec.distanceToSqr(localClick);
			if (diff > bestDistance)
				continue;
			bestDistance = diff;
			bestSlot = slot;
		}

		return bestSlot;
	}

	/**
	 * The client sees a barrier, so the location it sends is where its ray met the whole block. This casts the
	 * player's own ray against the panels that exist, or else against the plane free panel slots would be on, and
	 * returns that location. Falls back to the location the client sent when the ray does not cross the block.
	 */
	@Nullable
	public static Vec3 pickLocation(@Nullable Player player, Level level, BlockPos pos, BlockState state,
		@Nullable Vec3 clientHit) {
		if (player == null || level.isClientSide())
			return clientHit;
		double reach = player.blockInteractionRange() + 1;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1);
		Vec3 end = eye.add(look.scale(reach));

		BlockHitResult real = state.getShape(level, pos)
			.clip(eye, end, pos);
		if (real != null)
			return real.getLocation();

		Direction normal = connectedDirection(state);
		double lookAlongNormal = look.dot(Vec3.atLowerCornerOf(normal.getUnitVec3i()));
		if (Math.abs(lookAlongNormal) < 1e-4)
			return clientHit;
		Vec3 planePoint = Vec3.atCenterOf(pos)
			.add(Vec3.atLowerCornerOf(normal.getUnitVec3i())
				.scale(-(.5 - 1.5 / 16)));
		double t = planePoint.subtract(eye)
			.dot(Vec3.atLowerCornerOf(normal.getUnitVec3i())) / lookAlongNormal;
		if (t < 0 || t > reach)
			return clientHit;
		Vec3 hit = eye.add(look.scale(t));
		double e = 1e-3;
		if (hit.x < pos.getX() - e || hit.x > pos.getX() + 1 + e || hit.y < pos.getY() - e || hit.y > pos.getY() + 1 + e
			|| hit.z < pos.getZ() - e || hit.z > pos.getZ() + 1 + e)
			return clientHit;
		return hit;
	}

	@Override
	public Class<FactoryPanelBlockEntity> getBlockEntityClass() {
		return FactoryPanelBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FactoryPanelBlockEntity> getBlockEntityType() {
		return FactoryGaugeRegistry.FACTORY_PANEL_BLOCK_ENTITY;
	}

	public static float getXRot(BlockState state) {
		AttachFace face = state.getOptionalValue(FactoryPanelBlock.FACE)
			.orElse(AttachFace.FLOOR);
		return face == AttachFace.CEILING ? Mth.PI / 2 : face == AttachFace.FLOOR ? -Mth.PI / 2 : 0;
	}

	public static float getYRot(BlockState state) {
		Direction facing = state.getOptionalValue(FactoryPanelBlock.FACING)
			.orElse(Direction.SOUTH);
		AttachFace face = state.getOptionalValue(FactoryPanelBlock.FACE)
			.orElse(AttachFace.FLOOR);
		return (face == AttachFace.CEILING ? Mth.PI : 0) + AngleHelper.rad(AngleHelper.horizontalAngle(facing));
	}

	private static Component translate(String key) {
		return Component.translatable("create." + key);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.COPPER_BLOCK.weathering()
			.unaffected()
			.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new FactoryPanelModel(state);
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity blockEntity) {
		return ItemRequirement.NONE;
	}
}

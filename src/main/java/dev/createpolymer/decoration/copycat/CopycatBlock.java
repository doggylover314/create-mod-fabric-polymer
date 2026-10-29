package dev.createpolymer.decoration.copycat;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.feature.CopycatRegistry;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Create's CopycatBlock: a block that looks (and, where the game lets a server decide, behaves) like the block item
 * that was used on it.
 * <p>
 * Differences that come from running without a client mod: Create's block entity keeps the light of its material in
 * NeoForge's AuxiliaryLightManager, which vanilla does not have, so the light of the material is kept in the hidden
 * {@link #LIGHT} block state property, and the look of the material is drawn by block displays
 * (see {@link CopycatModel}).
 */
public abstract class CopycatBlock extends Block implements IBE<CopycatBlockEntity>, IWrenchable, PolymerModelBlock {

	/** Light level of the material; the block state is the only place vanilla reads a block's light from. */
	public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);

	/** Create's block tags create:copycat_allow / create:copycat_deny. */
	public static final TagKey<Block> COPYCAT_ALLOW = TagKey.create(Registries.BLOCK, CreatePolymer.id("copycat_allow"));
	public static final TagKey<Block> COPYCAT_DENY = TagKey.create(Registries.BLOCK, CreatePolymer.id("copycat_deny"));

	public CopycatBlock(Properties pProperties) {
		super(pProperties.lightLevel(state -> state.hasProperty(LIGHT) ? state.getValue(LIGHT) : 0));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(LIGHT));
	}

	/** The state without the light of its material: what two copycats are compared by. */
	public static BlockState withoutLight(BlockState state) {
		return state.hasProperty(LIGHT) ? state.setValue(LIGHT, 0) : state;
	}

	@Nullable
	@Override
	public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level p_153212_, BlockState p_153213_,
		BlockEntityType<S> p_153214_) {
		return null;
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		onWrenched(state, context);
		return IWrenchable.super.onSneakWrenched(state, context);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		return onBlockEntityUse(context.getLevel(), context.getClickedPos(), ufte -> {
			ItemStack consumedItem = ufte.getConsumedItem();
			if (!ufte.hasCustomMaterial())
				return InteractionResult.PASS;
			Player player = context.getPlayer();
			if (!player.isCreative())
				player.getInventory()
					.placeItemBackInInventory(consumedItem, Prediction.SERVER_ONLY);
			context.getLevel()
				.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, context.getClickedPos(), Block.getId(ufte.getMaterial()));
			ufte.setMaterial(CopycatRegistry.COPYCAT_BASE.defaultBlockState());
			ufte.setConsumedItem(ItemStack.EMPTY);
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (player == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		Direction face = hitResult.getDirection();
		BlockState materialIn = getAcceptedBlockState(level, pos, stack, face);

		if (materialIn != null)
			materialIn = prepareMaterial(level, pos, state, player, hand, hitResult, materialIn);
		if (materialIn == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		BlockState material = materialIn;
		return onBlockEntityUseItemOn(level, pos, ufte -> {
			if (ufte.getMaterial()
				.is(material.getBlock())) {
				if (!ufte.cycleMaterial())
					return InteractionResult.TRY_WITH_EMPTY_HAND;
				ufte.getLevel()
					.playSound(null, ufte.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .75f,
						.95f);
				return InteractionResult.SUCCESS;
			}
			if (ufte.hasCustomMaterial())
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			if (level.isClientSide())
				return InteractionResult.SUCCESS;

			ufte.setMaterial(material);
			ufte.setConsumedItem(stack);
			ufte.getLevel()
				.playSound(null, ufte.getBlockPos(), material.getSoundType()
					.getPlaceSound(), SoundSource.BLOCKS, 1, .75f);

			if (player.isCreative())
				return InteractionResult.SUCCESS;

			stack.shrink(1);
			if (stack.isEmpty())
				player.setItemInHand(hand, ItemStack.EMPTY);
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, LivingEntity pPlacer, ItemStack pStack) {
		if (pPlacer == null)
			return;
		ItemStack offhandItem = pPlacer.getItemInHand(InteractionHand.OFF_HAND);
		BlockState appliedState =
			getAcceptedBlockState(pLevel, pPos, offhandItem, Direction.orderedByNearest(pPlacer)[0]);

		if (appliedState == null)
			return;
		withBlockEntityDo(pLevel, pPos, ufte -> {
			if (ufte.hasCustomMaterial())
				return;

			ufte.setMaterial(appliedState);
			ufte.setConsumedItem(offhandItem);

			if (pPlacer instanceof Player player && player.isCreative())
				return;
			offhandItem.shrink(1);
			if (offhandItem.isEmpty())
				pPlacer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		});
	}

	@Nullable
	public BlockState getAcceptedBlockState(Level pLevel, BlockPos pPos, ItemStack item, Direction face) {
		if (!(item.getItem() instanceof BlockItem bi))
			return null;

		Block block = bi.getBlock();
		if (block instanceof CopycatBlock)
			return null;

		BlockState appliedState = block.defaultBlockState();
		boolean hardCodedAllow = isAcceptedRegardless(appliedState);

		if (!appliedState.is(COPYCAT_ALLOW) && !hardCodedAllow) {

			if (appliedState.is(COPYCAT_DENY))
				return null;
			if (block instanceof EntityBlock)
				return null;
			if (block instanceof StairBlock)
				return null;

			if (pLevel != null) {
				VoxelShape shape = appliedState.getShape(pLevel, pPos);
				if (shape.isEmpty() || !shape.bounds()
					.equals(Shapes.block()
						.bounds()))
					return null;

				VoxelShape collisionShape = appliedState.getCollisionShape(pLevel, pPos);
				if (collisionShape.isEmpty())
					return null;
			}
		}

		if (face != null) {
			Axis axis = face.getAxis();

			if (appliedState.hasProperty(BlockStateProperties.FACING))
				appliedState = appliedState.setValue(BlockStateProperties.FACING, face);
			if (appliedState.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && axis != Axis.Y)
				appliedState = appliedState.setValue(BlockStateProperties.HORIZONTAL_FACING, face);
			if (appliedState.hasProperty(BlockStateProperties.AXIS))
				appliedState = appliedState.setValue(BlockStateProperties.AXIS, axis);
			if (appliedState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) && axis != Axis.Y)
				appliedState = appliedState.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
		}

		return appliedState;
	}

	public boolean isAcceptedRegardless(BlockState material) {
		return false;
	}

	public BlockState prepareMaterial(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer,
		InteractionHand pHand, BlockHitResult pHit, BlockState material) {
		return material;
	}

	/**
	 * Create pops the consumed item in onRemove; here the block entity does it in destroy(). What is mined drops
	 * only if the player could harvest the material (NeoForge's canHarvestBlock).
	 */
	@Override
	public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
		@Nullable BlockEntity blockEntity, ItemStack destroyedWith) {
		if (blockEntity instanceof CopycatBlockEntity ufte && !player.hasCorrectToolForDrops(ufte.getMaterial()))
			return;
		super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
	}

	@Override
	public BlockState playerWillDestroy(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
		super.playerWillDestroy(pLevel, pPos, pState, pPlayer);
		if (pPlayer.isCreative())
			withBlockEntityDo(pLevel, pPos, ufte -> ufte.setConsumedItem(ItemStack.EMPTY));
		return pState;
	}

	@Override
	public Class<CopycatBlockEntity> getBlockEntityClass() {
		return CopycatBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CopycatBlockEntity> getBlockEntityType() {
		return CopycatRegistry.COPYCAT_BLOCK_ENTITY;
	}

	//

	public static BlockState getMaterial(BlockGetter reader, BlockPos targetPos) {
		if (reader.getBlockEntity(targetPos) instanceof CopycatBlockEntity cbe)
			return cbe.getMaterial();
		return Blocks.AIR.defaultBlockState();
	}

	// Wrapped properties

	@Override
	protected float getDestroyProgress(BlockState pState, Player pPlayer, BlockGetter pLevel, BlockPos pPos) {
		BlockState material = getMaterial(pLevel, pPos);
		if (material.isAir())
			return super.getDestroyProgress(pState, pPlayer, pLevel, pPos);
		return material.getDestroyProgress(pPlayer, pLevel, pPos);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		BlockState material = getMaterial(level, pos);
		if (material.isAir() || material.is(CopycatRegistry.COPYCAT_BASE))
			return new ItemStack(this);
		return material.getCloneItemStack(level, pos, includeData);
	}

	@Override
	public void fallOn(Level pLevel, BlockState pState, BlockPos pPos, Entity pEntity, double fallDistance) {
		BlockState material = getMaterial(pLevel, pPos);
		if (material.isAir()) {
			super.fallOn(pLevel, pState, pPos, pEntity, fallDistance);
			return;
		}
		material.getBlock()
			.fallOn(pLevel, material, pPos, pEntity, fallDistance);
	}

	/** Break particles and sound of whatever the copycat looks like. */
	@Override
	public void spawnDestroyByEntityParticles(Level level, @Nullable Entity entity, BlockPos pos, BlockState state) {
		BlockState material = getMaterial(level, pos);
		if (material.isAir() || material.is(CopycatRegistry.COPYCAT_BASE)) {
			super.spawnDestroyByEntityParticles(level, entity, pos, state);
			return;
		}
		super.spawnDestroyByEntityParticles(level, entity, pos, material);
		// the player who mines on the server only sees what is sent to them, see Polymer's BlockMixin
		if (entity instanceof ServerPlayer player && !player.getAbilities().instabuild
			&& PolymerBlockUtils.shouldMineServerSide(player, pos, state))
			player.connection.send(
				new ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(material), false));
	}

	@Override
	public BlockState particleState() {
		return Blocks.IRON_BLOCK.defaultBlockState();
	}

}

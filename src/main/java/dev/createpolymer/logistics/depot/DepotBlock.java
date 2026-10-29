package dev.createpolymer.logistics.depot;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.SmartBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's DepotBlock (MIT, The Create Team). */
public class DepotBlock extends Block implements EntityBlock, PolymerBlock, BlockWithElementHolder {
	public static final VoxelShape SHAPE = ShapeUtil.box(0, 0, 0, 16, 13, 16);

	public DepotBlock(Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DepotBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != ModBlockEntities.DEPOT)
			return null;
		return (l, p, s, be) -> ((SmartBlockEntity) be).tick();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hit) {
		if (hit.getDirection() != Direction.UP)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DepotBlockEntity depot)
			depot.use(player, hand);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (hit.getDirection() != Direction.UP)
			return InteractionResult.PASS;
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DepotBlockEntity depot)
			depot.use(player, InteractionHand.MAIN_HAND);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		if (!(level.getBlockEntity(pos) instanceof DepotBlockEntity depot))
			return 0;
		ItemStack held = depot.getHeldItem();
		float f = held.isEmpty() ? 0 : held.getCount() / (float) held.getMaxStackSize();
		return Mth.clamp(Mth.floor(f * 14.0F) + (f > 0 ? 1 : 0), 0, 15);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	// ---- Polymer ----

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return Blocks.BARRIER.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	public boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
		return true;
	}

	@Override
	public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
		return new Model(initialBlockState);
	}

	public static class Model extends BaseModel {
		private final Part item;
		private ItemStack shown = ItemStack.EMPTY;
		private int lastVersion = -1;

		public Model(BlockState state) {
			super(state);
			showVariant(addPart(ModelItems.block("depot/block")), "depot", state);
			item = addPart(ItemStack.EMPTY);
			item.element.setItemDisplayContext(ItemDisplayContext.FIXED);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof DepotBlockEntity depot) || depot.getDataVersion() == lastVersion)
				return;
			lastVersion = depot.getDataVersion();
			ItemStack held = depot.getHeldItem();
			if (ItemStack.isSameItemSameComponents(held, shown) && held.isEmpty() == shown.isEmpty())
				return;
			shown = held.copyWithCount(1);
			item.setItem(shown);
			boolean upright = held.getItem() instanceof BlockItem;
			Matrix4f m = new Matrix4f().translate(0, 13 / 16f - .5f + (upright ? .125f : .02f), 0);
			if (!upright)
				m.rotateX((float) (Math.PI / 2));
			m.scale(upright ? .25f : .5f);
			item.setMatrix(m, 0);
		}
	}
}

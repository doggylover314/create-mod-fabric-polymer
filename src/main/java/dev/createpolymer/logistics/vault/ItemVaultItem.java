package dev.createpolymer.logistics.vault;

import dev.createpolymer.api.connectivity.ConnectivityHandler;
import dev.createpolymer.registry.feature.VaultRegistry;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ItemVaultItem extends PolymerBlockItem {

	public ItemVaultItem(Block p_i48527_1_, Properties p_i48527_2_) {
		super(p_i48527_1_, p_i48527_2_, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult place(BlockPlaceContext ctx) {
		removeMultiblockData(ctx.getItemInHand());
		InteractionResult initialResult = super.place(ctx);
		if (!initialResult.consumesAction())
			return initialResult;
		tryMultiPlace(ctx);
		return initialResult;
	}

	/**
	 * Create's updateCustomBlockEntityTag: an item with stored block entity data must not carry the multiblock
	 * structure of the vault it came from. (26.3 applies the data in a static method, so it is cleaned up front.)
	 */
	private static void removeMultiblockData(ItemStack itemStack) {
		TypedEntityData<BlockEntityType<?>> blockEntityData = itemStack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (blockEntityData == null)
			return;
		CompoundTag nbt = blockEntityData.copyTagWithoutId();
		nbt.remove("Length");
		nbt.remove("Size");
		nbt.remove("Controller");
		nbt.remove("LastKnownPos");
		itemStack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(VaultRegistry.ITEM_VAULT_BLOCK_ENTITY, nbt));
	}

	private void tryMultiPlace(BlockPlaceContext ctx) {
		Player player = ctx.getPlayer();
		if (player == null)
			return;
		if (player.isShiftKeyDown())
			return;
		Direction face = ctx.getClickedFace();
		ItemStack stack = ctx.getItemInHand();
		Level world = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		BlockPos placedOnPos = pos.relative(face.getOpposite());
		BlockState placedOnState = world.getBlockState(placedOnPos);

		if (!ItemVaultBlock.isVault(placedOnState))
			return;
		ItemVaultBlockEntity tankAt = ConnectivityHandler.partAt(VaultRegistry.ITEM_VAULT_BLOCK_ENTITY, world, placedOnPos);
		if (tankAt == null)
			return;
		ItemVaultBlockEntity controllerBE = tankAt.getControllerBE();
		if (controllerBE == null)
			return;

		int width = controllerBE.radius;
		if (width == 1)
			return;

		int tanksToPlace = 0;
		Axis vaultBlockAxis = ItemVaultBlock.getVaultBlockAxis(placedOnState);
		if (vaultBlockAxis == null)
			return;
		if (face.getAxis() != vaultBlockAxis)
			return;

		Direction vaultFacing = Direction.fromAxisAndDirection(vaultBlockAxis, AxisDirection.POSITIVE);
		BlockPos startPos = face == vaultFacing.getOpposite() ? controllerBE.getBlockPos()
			.relative(vaultFacing.getOpposite())
			: controllerBE.getBlockPos()
				.relative(vaultFacing, controllerBE.length);

		if (VecHelper.getCoordinate(startPos, vaultBlockAxis) != VecHelper.getCoordinate(pos, vaultBlockAxis))
			return;

		for (int xOffset = 0; xOffset < width; xOffset++) {
			for (int zOffset = 0; zOffset < width; zOffset++) {
				BlockPos offsetPos = vaultBlockAxis == Axis.X ? startPos.offset(0, xOffset, zOffset)
					: startPos.offset(xOffset, zOffset, 0);
				BlockState blockState = world.getBlockState(offsetPos);
				if (ItemVaultBlock.isVault(blockState))
					continue;
				if (!blockState.canBeReplaced())
					return;
				tanksToPlace++;
			}
		}

		if (!player.isCreative() && stack.getCount() < tanksToPlace)
			return;

		for (int xOffset = 0; xOffset < width; xOffset++) {
			for (int zOffset = 0; zOffset < width; zOffset++) {
				BlockPos offsetPos = vaultBlockAxis == Axis.X ? startPos.offset(0, xOffset, zOffset)
					: startPos.offset(xOffset, zOffset, 0);
				BlockState blockState = world.getBlockState(offsetPos);
				if (ItemVaultBlock.isVault(blockState))
					continue;
				BlockPlaceContext context = BlockPlaceContext.at(ctx, offsetPos, face);
				ItemVaultBlock.silenceVaultSound = true;
				try {
					super.place(context);
				} finally {
					ItemVaultBlock.silenceVaultSound = false;
				}
			}
		}
	}

}

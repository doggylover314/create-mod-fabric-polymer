package dev.createpolymer.fluids.tank;

import dev.createpolymer.equipment.symmetryWand.SymmetryWandItem;
import dev.createpolymer.api.connectivity.ConnectivityHandler;
import dev.createpolymer.registry.feature.TanksRegistry;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class FluidTankItem extends PolymerBlockItem {

	public FluidTankItem(Block p_i48527_1_, Properties p_i48527_2_) {
		super(p_i48527_1_, p_i48527_2_, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult place(BlockPlaceContext ctx) {
		updateCustomBlockEntityTag(ctx.getItemInHand());
		InteractionResult initialResult = super.place(ctx);
		if (!initialResult.consumesAction())
			return initialResult;
		tryMultiPlace(ctx);
		return initialResult;
	}

	/**
	 * Create's updateCustomBlockEntityTag: an item with stored block entity data must not carry the multiblock
	 * structure of the tank it came from, and holds at most one block's worth of fluid.
	 * (26.3 applies the data in a static method, so it is cleaned up front.)
	 */
	private void updateCustomBlockEntityTag(ItemStack itemStack) {
		TypedEntityData<BlockEntityType<?>> blockEntityData = itemStack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (blockEntityData == null)
			return;
		CompoundTag nbt = blockEntityData.copyTagWithoutId();
		nbt.remove("Luminosity");
		nbt.remove("Size");
		nbt.remove("Height");
		nbt.remove("Controller");
		nbt.remove("LastKnownPos");
		CompoundTag fluid = nbt.getCompoundOrEmpty("TankContent")
			.getCompoundOrEmpty("Fluid");
		if (fluid.contains("amount")) {
			fluid.putInt("amount", Math.min(FluidTankBlockEntity.getCapacityMultiplier(), fluid.getIntOr("amount", 0)));
			CompoundTag content = nbt.getCompoundOrEmpty("TankContent");
			content.put("Fluid", fluid);
			nbt.put("TankContent", content);
		}
		BlockEntityType<?> type = ((FluidTankBlock) getBlock()).getBlockEntityType();
		itemStack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(type, nbt));
	}

	private void tryMultiPlace(BlockPlaceContext ctx) {
		Player player = ctx.getPlayer();
		if (player == null)
			return;
		if (player.isShiftKeyDown())
			return;
		if (SymmetryWandItem.presentInHotbar(player))
			return;
		Direction face = ctx.getClickedFace();
		if (!face.getAxis()
			.isVertical())
			return;
		ItemStack stack = ctx.getItemInHand();
		Level world = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		BlockPos placedOnPos = pos.relative(face.getOpposite());
		BlockState placedOnState = world.getBlockState(placedOnPos);

		if (!FluidTankBlock.isTank(placedOnState))
			return;
		boolean creative = getBlock().equals(TanksRegistry.CREATIVE_FLUID_TANK);
		FluidTankBlockEntity tankAt = ConnectivityHandler.partAt(
			creative ? TanksRegistry.CREATIVE_FLUID_TANK_BLOCK_ENTITY : TanksRegistry.FLUID_TANK_BLOCK_ENTITY, world, placedOnPos
		);
		if (tankAt == null)
			return;
		FluidTankBlockEntity controllerBE = tankAt.getControllerBE();
		if (controllerBE == null)
			return;

		int width = controllerBE.getWidth();
		if (width == 1)
			return;

		int tanksToPlace = 0;
		BlockPos startPos = face == Direction.DOWN ? controllerBE.getBlockPos()
			.below()
			: controllerBE.getBlockPos()
				.above(controllerBE.getHeight());

		if (startPos.getY() != pos.getY())
			return;

		for (int xOffset = 0; xOffset < width; xOffset++) {
			for (int zOffset = 0; zOffset < width; zOffset++) {
				BlockPos offsetPos = startPos.offset(xOffset, 0, zOffset);
				BlockState blockState = world.getBlockState(offsetPos);
				if (FluidTankBlock.isTank(blockState))
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
				BlockPos offsetPos = startPos.offset(xOffset, 0, zOffset);
				BlockState blockState = world.getBlockState(offsetPos);
				if (FluidTankBlock.isTank(blockState))
					continue;
				BlockPlaceContext context = BlockPlaceContext.at(ctx, offsetPos, face);
				FluidTankBlock.silenceTankSound = true;
				try {
					super.place(context);
				} finally {
					FluidTankBlock.silenceTankSound = false;
				}
			}
		}
	}

}

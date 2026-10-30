package dev.createpolymer.equipment.symmetryWand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.createpolymer.contraptions.mounted.CartAssemblerBlock;
import dev.createpolymer.equipment.symmetryWand.mirror.CrossPlaneMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.EmptyMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.PlaneMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.SymmetryMirror;
import dev.createpolymer.foundation.block.BlockHelper;
import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.registry.feature.WandsRegistry;
import dev.createpolymer.util.Iterate;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Ported from Create's SymmetryWandItem (MIT, The Create Team). Differences: the configuration screen is a server side
 * GUI ({@link SymmetryWandGui}); the blocks the wand places are not announced to other mods as place events, since
 * Fabric has no such event, so the wand checks {@link Level#mayInteract} itself; the wand's mirror is shown by
 * display entities, see {@link SymmetryHandler}.
 */
public class SymmetryWandItem extends SimplePolymerItem {

	public SymmetryWandItem(Properties properties) {
		super(properties.stacksTo(1), Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		BlockPos pos = context.getClickedPos();
		if (player == null)
			return InteractionResult.PASS;
		ItemStack wand = player.getItemInHand(context.getHand());
		player.getCooldowns()
			.addCooldown(wand, 5);
		checkComponents(wand);

		// Shift -> open GUI
		if (player.isShiftKeyDown()) {
			if (player instanceof ServerPlayer serverPlayer)
				new SymmetryWandGui(serverPlayer, wand, context.getHand()).open();
			return InteractionResult.SUCCESS;
		}

		if (context.getLevel().isClientSide() || context.getHand() != InteractionHand.MAIN_HAND)
			return InteractionResult.SUCCESS;

		pos = pos.relative(context.getClickedFace());
		SymmetryMirror previousElement = wand.get(WandsRegistry.SYMMETRY_WAND);

		// No Shift -> Make / Move Mirror
		wand.set(WandsRegistry.SYMMETRY_WAND_ENABLE, true);
		Vec3 pos3d = new Vec3(pos.getX(), pos.getY(), pos.getZ());
		SymmetryMirror newElement = new PlaneMirror(pos3d);

		if (previousElement instanceof EmptyMirror) {
			newElement.setOrientation(
				(player.getDirection() == Direction.NORTH || player.getDirection() == Direction.SOUTH)
					? PlaneMirror.Align.XY.ordinal()
					: PlaneMirror.Align.YZ.ordinal());
			newElement.enable = true;
			wand.set(WandsRegistry.SYMMETRY_WAND_ENABLE, true);
		} else {
			// the component may be shared with copies of the stack
			previousElement = previousElement.copy();
			previousElement.setPosition(pos3d);

			if (previousElement instanceof PlaneMirror) {
				previousElement.setOrientation(
					(player.getDirection() == Direction.NORTH || player.getDirection() == Direction.SOUTH)
						? PlaneMirror.Align.XY.ordinal()
						: PlaneMirror.Align.YZ.ordinal());
			}

			if (previousElement instanceof CrossPlaneMirror) {
				float rotation = player.getYHeadRot();
				float abs = Math.abs(rotation % 90);
				boolean diagonal = abs > 22 && abs < 45 + 22;
				previousElement
					.setOrientation(diagonal ? CrossPlaneMirror.Align.D.ordinal() : CrossPlaneMirror.Align.Y.ordinal());
			}

			newElement = previousElement;
		}

		wand.set(WandsRegistry.SYMMETRY_WAND, newElement);

		player.setItemInHand(context.getHand(), wand);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level worldIn, Player playerIn, InteractionHand handIn) {
		ItemStack wand = playerIn.getItemInHand(handIn);
		checkComponents(wand);

		// Shift -> Open GUI
		if (playerIn.isShiftKeyDown()) {
			if (playerIn instanceof ServerPlayer serverPlayer) {
				new SymmetryWandGui(serverPlayer, wand, handIn).open();
				playerIn.getCooldowns()
					.addCooldown(wand, 5);
			}
			return InteractionResult.SUCCESS;
		}

		// No Shift -> Clear Mirror
		wand.set(WandsRegistry.SYMMETRY_WAND_ENABLE, false);
		return InteractionResult.SUCCESS;
	}

	private static void checkComponents(ItemStack wand) {
		if (!wand.has(WandsRegistry.SYMMETRY_WAND)) {
			wand.set(WandsRegistry.SYMMETRY_WAND, new EmptyMirror(new Vec3(0, 0, 0)));
			wand.set(WandsRegistry.SYMMETRY_WAND_ENABLE, false);
		}
	}

	public static boolean isEnabled(ItemStack stack) {
		checkComponents(stack);
		return stack.getOrDefault(WandsRegistry.SYMMETRY_WAND_ENABLE, false);
	}

	public static SymmetryMirror getMirror(ItemStack stack) {
		checkComponents(stack);
		return stack.get(WandsRegistry.SYMMETRY_WAND);
	}

	public static void configureSettings(ItemStack stack, SymmetryMirror mirror) {
		checkComponents(stack);
		stack.set(WandsRegistry.SYMMETRY_WAND, mirror);
	}

	public static boolean isWand(ItemStack stack) {
		return stack.getItem() instanceof SymmetryWandItem;
	}

	/**
	 * Create's apply, run when {@code player} has placed {@code block} at {@code pos}: places the mirrored blocks.
	 * Survival players pay for them with items from their inventory. The block counts as placed already, so the
	 * item it cost is gone from the inventory, unlike in Create where the event is sent before.
	 */
	public static void apply(Level world, ItemStack wand, Player player, BlockPos pos, BlockState block) {
		checkComponents(wand);
		if (!isEnabled(wand))
			return;
		if (!Item.BY_BLOCK.containsKey(block.getBlock()))
			return;

		Map<BlockPos, BlockState> blockSet = new HashMap<>();
		blockSet.put(pos, block);
		SymmetryMirror symmetry = wand.get(WandsRegistry.SYMMETRY_WAND);

		Vec3 mirrorPos = symmetry.getPosition();
		if (mirrorPos.distanceTo(Vec3.atLowerCornerOf(pos)) > SymmetryWandConfig.maxSymmetryWandRange)
			return;

		symmetry.process(blockSet);
		BlockPos to = BlockPos.containing(mirrorPos);
		List<BlockPos> targets = new ArrayList<>();
		targets.add(pos);

		for (BlockPos position : blockSet.keySet()) {
			if (position.equals(pos))
				continue;

			if (!world.mayInteract(player, position))
				continue;

			if (world.isUnobstructed(block, position, CollisionContext.of(player))) {
				BlockState blockState = blockSet.get(position);
				for (Direction face : Iterate.directions)
					blockState = blockState.updateShape(world, world, position, face, position.relative(face),
						world.getBlockState(position.relative(face)), world.getRandom());

				if (player.isCreative()) {
					world.setBlockAndUpdate(position, blockState);
					targets.add(position);
					continue;
				}

				BlockState toReplace = world.getBlockState(position);
				if (!toReplace.canBeReplaced())
					continue;
				if (toReplace.getDestroySpeed(world, position) == -1)
					continue;

				if (blockState.is(MinecartRegistry.CART_ASSEMBLER)) {
					BlockState railBlock = CartAssemblerBlock.getRailBlock(blockState);
					if (BlockHelper.findAndRemoveInInventory(railBlock, player, 1) == 0)
						continue;
					if (BlockHelper.findAndRemoveInInventory(blockState, player, 1) == 0)
						blockState = railBlock;
				} else {
					if (BlockHelper.findAndRemoveInInventory(blockState, player, 1) == 0)
						continue;
				}

				FluidState ifluidstate = world.getFluidState(position);
				world.setBlock(position, ifluidstate.createLegacyBlock(), Block.UPDATE_KNOWN_SHAPE);
				world.setBlockAndUpdate(position, blockState);
				targets.add(position);
			}
		}

		SymmetryHandler.sendEffect(world, player, to, targets);
	}

	/**
	 * Create's remove, run when {@code player} has broken {@code ogBlock} at {@code pos}: breaks the mirrored blocks.
	 * Survival players only break blocks of the same kind, and get their drops.
	 */
	public static void remove(Level world, ItemStack wand, Player player, BlockPos pos, BlockState ogBlock) {
		BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		checkComponents(wand);
		if (!isEnabled(wand))
			return;

		Map<BlockPos, BlockState> blockSet = new HashMap<>();
		blockSet.put(pos, air);
		SymmetryMirror symmetry = wand.get(WandsRegistry.SYMMETRY_WAND);

		Vec3 mirrorPos = symmetry.getPosition();
		if (mirrorPos.distanceTo(Vec3.atLowerCornerOf(pos)) > SymmetryWandConfig.maxSymmetryWandRange)
			return;

		symmetry.process(blockSet);

		BlockPos to = BlockPos.containing(mirrorPos);
		List<BlockPos> targets = new ArrayList<>();

		targets.add(pos);
		for (BlockPos position : blockSet.keySet()) {
			if (!player.isCreative() && ogBlock.getBlock() != world.getBlockState(position)
				.getBlock())
				continue;
			if (position.equals(pos))
				continue;
			if (!world.mayInteract(player, position))
				continue;

			BlockState blockstate = world.getBlockState(position);
			if (!blockstate.isAir()) {
				targets.add(position);
				world.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK, position,
					Block.getId(blockstate));
				world.setBlock(position, air, Block.UPDATE_ALL);

				if (!player.isCreative()) {
					if (!player.getMainHandItem()
						.isEmpty())
						player.getMainHandItem()
							.mineBlock(world, blockstate, position, player);
					net.minecraft.world.level.block.entity.BlockEntity blockEntity =
						blockstate.hasBlockEntity() ? world.getBlockEntity(position) : null;
					Block.dropResources(blockstate, world, pos, blockEntity, player, player.getMainHandItem()); // Add fortune, silk touch and other loot modifiers
				}
			}
		}

		SymmetryHandler.sendEffect(world, player, to, targets);
	}

	public static boolean presentInHotbar(Player player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.getSelectionSize(); i++)
			if (isWand(inv.getItem(i)))
				return true;
		return false;
	}

}

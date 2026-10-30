package dev.createpolymer.redstone.link.controller;

import java.util.ArrayList;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.schematic.requirement.SpecialBlockItemRequirement;
import dev.createpolymer.equipment.ToolsSounds;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.registry.feature.ToolsRegistry;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A lectern holding a linked controller. Players see an ordinary lectern, with the controller drawn on it by display
 * entities. Right-click uses the controller (its buttons are the movement keys, see {@link LinkedControllerHandler}),
 * and right-click again lets go; sneak-right-click gives the controller back.
 */
public class LecternControllerBlock extends LecternBlock implements IBE<LecternControllerBlockEntity>, SpecialBlockItemRequirement, PolymerModelBlock {

	public LecternControllerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(HAS_BOOK, true));
	}

	@Override
	public Class<LecternControllerBlockEntity> getBlockEntityClass() {
		return LecternControllerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends LecternControllerBlockEntity> getBlockEntityType() {
		return ToolsRegistry.LECTERN_CONTROLLER_BLOCK_ENTITY;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return IBE.super.newBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (!player.isShiftKeyDown() && LecternControllerBlockEntity.playerInRange(player, level, pos)) {
			if (!level.isClientSide())
				withBlockEntityDo(level, pos, be -> {
					if (be.isUsedBy(player))
						be.tryStopUsing(player);
					else
						be.tryStartUsing(player);
				});
			return InteractionResult.SUCCESS;
		}

		if (player.isShiftKeyDown()) {
			if (!level.isClientSide())
				replaceWithLectern(state, level, pos);
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return InteractionResult.PASS;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return 15;
	}

	public void replaceLectern(BlockState lecternState, Level world, BlockPos pos, ItemStack controller) {
		world.setBlockAndUpdate(pos, defaultBlockState().setValue(FACING, lecternState.getValue(FACING))
			.setValue(POWERED, lecternState.getValue(POWERED)));
		withBlockEntityDo(world, pos, be -> be.setController(controller));
	}

	public void replaceWithLectern(BlockState state, Level world, BlockPos pos) {
		ToolsSounds.controllerTake(world, pos);
		world.setBlockAndUpdate(pos, Blocks.LECTERN.defaultBlockState()
			.setValue(FACING, state.getValue(FACING))
			.setValue(POWERED, state.getValue(POWERED)));
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(Blocks.LECTERN);
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity be) {
		ArrayList<ItemStack> requiredItems = new ArrayList<>();
		requiredItems.add(new ItemStack(Blocks.LECTERN));
		requiredItems.add(new ItemStack(ToolsRegistry.LINKED_CONTROLLER));
		return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, requiredItems);
	}

	// ---- Polymer ----

	/** Players see a lectern without a book, the controller is a display entity on top of it. */
	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return Blocks.LECTERN.defaultBlockState()
			.setValue(FACING, state.getValue(FACING))
			.setValue(POWERED, state.getValue(POWERED))
			.setValue(HAS_BOOK, false);
	}

	@Override
	public BlockState particleState() {
		return Blocks.LECTERN.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * Create's LecternControllerRenderer and LinkedControllerItemRenderer.renderInLectern: the controller lies on the
	 * lectern, lit while somebody uses it, and its buttons sink while the user presses them.
	 */
	public static class Model extends BaseModel {
		private static final float S = 1 / 16f;
		/** Create's button offsets in the model, in pixels (x, z), in the order of the six controls. */
		private static final float[][] BUTTONS = { { 2, 8 }, { 6, 8 }, { 4, 10 }, { 4, 6 }, { 3, 3 }, { 5, 3 } };

		private final Part controller;
		private final Part[] buttons = new Part[6];
		private boolean lit;
		private int pressed;
		private Matrix4f origin = new Matrix4f();

		public Model(BlockState state) {
			super(state);
			controller = addPart(ModelItems.block("linked_controller/item"));
			for (int i = 0; i < buttons.length; i++)
				buttons[i] = addPart(ModelItems.block("linked_controller/button"));
			place(state);
			apply();
		}

		private void place(BlockState state) {
			Direction facing = state.getValue(FACING);
			Matrix4f m = new Matrix4f().translate(.5f, 1.45f, .5f)
				.rotateY((float) Math.toRadians(AngleHelper.horizontalAngle(facing) - 90))
				.translate(.28f, 0, 0)
				.rotateZ((float) Math.toRadians(-22));
			// The renderer places the corner of the model at that spot; a display entity draws the model around its
			// centre and is itself at the centre of the block.
			origin = new Matrix4f().translate(-.5f, -.5f, -.5f)
				.mul(m);
		}

		private void apply() {
			controller.setMatrix(new Matrix4f(origin).translate(.5f, .5f, .5f), 0);
			for (int i = 0; i < buttons.length; i++) {
				boolean down = (pressed & 1 << i) != 0;
				buttons[i].setMatrix(new Matrix4f(origin)
					.translate(BUTTONS[i][0] * S, down ? -.75f * S : 0, BUTTONS[i][1] * S)
					.translate(.5f, .5f, .5f), 0);
			}
		}

		@Override
		protected void onStateChanged(BlockState state) {
			place(state);
			apply();
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof LecternControllerBlockEntity lectern))
				return;
			boolean nowLit = lectern.hasUser();
			int nowPressed = 0;
			if (nowLit && level.getEntity(lectern.getUser()) instanceof ServerPlayer player)
				for (int key : LinkedControllerHandler.session(player).currentlyPressed)
					nowPressed |= 1 << key;
			if (nowLit != lit) {
				lit = nowLit;
				controller.setItem(ModelItems.block(lit ? "linked_controller/powered" : "linked_controller/item"));
			}
			if (nowPressed != pressed) {
				pressed = nowPressed;
				apply();
			}
		}
	}
}

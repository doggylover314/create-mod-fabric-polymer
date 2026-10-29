package dev.createpolymer.redstone.diodes;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.redstone.RedstoneWrenchable;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class AbstractDiodeBlock extends DiodeBlock implements RedstoneWrenchable, PolymerModelBlock {

	public AbstractDiodeBlock(Properties builder) {
		super(builder);
	}

	/** The vanilla repeater does not need a collision of its own: this is the same 2 pixel high board. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	/** Which of Create's blockstate files draws this diode. */
	protected abstract String blockstateFile();

	// ---- Polymer ----

	/** A flat invisible plate on the floor, as high as the board. */
	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return RedstoneClientStates.plate(Direction.DOWN, false);
	}

	@Override
	public BlockState particleState() {
		return Blocks.STONE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, blockstateFile());
	}

	/** One model, picked from Create's blockstate file by the diode's state. */
	public static class Model extends BaseModel {
		private final Part board;
		private final String file;

		public Model(BlockState state, String file) {
			super(state);
			this.file = file;
			board = addPart(ModelItems.block("diodes/pulse_repeater"));
			showVariant(board, file, state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(board, file, state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}
}

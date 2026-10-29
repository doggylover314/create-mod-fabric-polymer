package dev.createpolymer.contraptions.actors.harvester;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.actors.AttachedActorBlock;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's HarvesterBlock (MIT, The Create Team). Create's block entity of the harvester only serves the
 * renderer, so there is none here.
 */
public class HarvesterBlock extends AttachedActorBlock {

	public HarvesterBlock(Properties p_i48377_1_) {
		super(p_i48377_1_);
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		var orientation = Orientation.forHorizontal(state.getValue(FACING));
		return List.of(new Piece(ModelItems.block("mechanical_harvester/block"), orientation),
			new Piece(ModelItems.block("mechanical_harvester/blade"), orientation));
	}

	/** The casing, and the blade that sweeps in front of it (it stands still: only the contraption turns it) */
	public static class Model extends BaseModel {
		private final Part casing;
		private final Part blade;

		public Model(BlockState state) {
			super(state);
			casing = addPart(ModelItems.block("mechanical_harvester/block"));
			blade = addPart(ModelItems.block("mechanical_harvester/blade"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			var orientation = Orientation.forHorizontal(state.getValue(FACING));
			casing.orientation(orientation).apply(0);
			blade.orientation(orientation).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}
}

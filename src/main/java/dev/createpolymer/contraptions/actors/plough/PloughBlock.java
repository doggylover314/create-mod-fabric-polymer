package dev.createpolymer.contraptions.actors.plough;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import dev.createpolymer.contraptions.actors.AttachedActorBlock;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's PloughBlock (MIT, The Create Team). */
public class PloughBlock extends AttachedActorBlock {

	public PloughBlock(Properties p_i48377_1_) {
		super(p_i48377_1_);
	}

	/**
	 * The OnHoeUse event takes a player, so we better not pass null
	 */
	static class PloughFakePlayer extends FakePlayer {

		public static final GameProfile PLOUGH_PROFILE =
			new GameProfile(UUID.fromString("9e2faded-eeee-4ec2-c314-dad129ae971d"), "Plough");

		public PloughFakePlayer(ServerLevel world) {
			super(world, PLOUGH_PROFILE);
		}

	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		return List.of(new Piece(ModelItems.block("mechanical_plough"), Orientation.forHorizontal(state.getValue(FACING))));
	}

	public static class Model extends BaseModel {
		private final Part part;

		public Model(BlockState state) {
			super(state);
			part = addPart(ModelItems.block("mechanical_plough"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			part.orientation(Orientation.forHorizontal(state.getValue(FACING))).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}
}

package dev.createpolymer.kinetics.encased;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's EncasedShaftBlock / AbstractEncasedShaftBlock (MIT, The Create Team).
 * Made by using a casing on a shaft; sneak-wrenching takes the casing off again.
 */
public class EncasedShaftBlock extends RotatedPillarKineticBlock implements IWrenchable {
	private final Supplier<Block> casing;
	private final String blockstateFile;

	public EncasedShaftBlock(Properties properties, Supplier<Block> casing, String blockstateFile) {
		super(properties);
		this.casing = casing;
		this.blockstateFile = blockstateFile;
	}

	public Block getCasing() {
		return casing.get();
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return ModBlockEntities.ENCASED_SHAFT;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())
			return super.getStateForPlacement(context);
		Axis preferredAxis = getPreferredAxis(context);
		return defaultBlockState().setValue(AXIS, preferredAxis == null ? context.getNearestLookingDirection().getAxis() : preferredAxis);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(AXIS);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		context.getLevel().levelEvent(2001, context.getClickedPos(), Block.getId(state));
		KineticBlockEntity.switchToBlockState(context.getLevel(), context.getClickedPos(),
			ModBlocks.SHAFT.defaultBlockState().setValue(AXIS, state.getValue(AXIS)));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, blockstateFile);
	}

	public static class Model extends BaseModel {
		private final String file;
		private final Part casing;
		private final Part shaft;

		public Model(BlockState state, String file) {
			super(state);
			this.file = file;
			casing = addPart(ModelItems.block("shaft"));
			shaft = addPart(ModelItems.block("shaft"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(casing, file, state);
			Axis axis = state.getValue(AXIS);
			shaft.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof KineticBlockEntity kbe) {
				Axis axis = state.getValue(AXIS);
				shaft.spin(time, kbe.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
			}
		}
	}
}

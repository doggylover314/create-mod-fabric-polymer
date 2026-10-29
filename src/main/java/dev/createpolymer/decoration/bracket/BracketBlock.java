package dev.createpolymer.decoration.bracket;

import java.util.Optional;

import dev.createpolymer.contraptions.piston.WrenchableDirectionalBlock;
import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.simple.AbstractSimpleShaftBlock;
import dev.createpolymer.kinetics.simple.CogWheelBlock;
import dev.createpolymer.util.Lang;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * The bracket is never placed in the world by its item: {@link BracketBlockItem} stores this block's state in the
 * {@link BracketedBlockEntityBehaviour} of a shaft, cogwheel or pipe. The block exists for its states, and for
 * commands that place it anyway.
 */
public class BracketBlock extends WrenchableDirectionalBlock implements PolymerModelBlock {

	public static final BooleanProperty AXIS_ALONG_FIRST_COORDINATE =
		DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE;
	public static final EnumProperty<BracketType> TYPE = EnumProperty.create("type", BracketType.class);

	public static enum BracketType implements StringRepresentable {
		PIPE, COG, SHAFT;

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}

	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(AXIS_ALONG_FIRST_COORDINATE)
			.add(TYPE));
	}

	public BracketBlock(Properties properties) {
		super(properties);
	}

	public Optional<BlockState> getSuitableBracket(BlockState blockState, Direction direction) {
		if (blockState.getBlock() instanceof AbstractSimpleShaftBlock)
			return getSuitableBracket(blockState.getValue(RotatedPillarKineticBlock.AXIS), direction,
				blockState.getBlock() instanceof CogWheelBlock ? BracketType.COG : BracketType.SHAFT);
		return getSuitableBracket(FluidPropagator.getStraightPipeAxis(blockState), direction, BracketType.PIPE);
	}

	private Optional<BlockState> getSuitableBracket(@Nullable Axis targetBlockAxis, Direction direction,
		BracketType type) {
		Axis axis = direction.getAxis();
		if (targetBlockAxis == null || targetBlockAxis == axis)
			return Optional.empty();

		boolean alongFirst = axis != Axis.Z ? targetBlockAxis == Axis.Z : targetBlockAxis == Axis.Y;
		return Optional.of(defaultBlockState().setValue(TYPE, type)
			.setValue(FACING, direction)
			.setValue(AXIS_ALONG_FIRST_COORDINATE, !alongFirst));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		if (rot.ordinal() % 2 == 1)
			state = state.cycle(AXIS_ALONG_FIRST_COORDINATE);
		return super.rotate(state, rot);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return blockstateFile().startsWith("wooden") ? Blocks.SCAFFOLDING.defaultBlockState()
			: Blocks.NETHERITE_BLOCK.defaultBlockState();
	}

	/** The blockstate file of Create's model for this bracket: "wooden_bracket" or "metal_bracket". */
	public String blockstateFile() {
		return BuiltInRegistries.BLOCK.getKey(this)
			.getPath();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, blockstateFile());
	}

	/** Shows the bracket model on its own. */
	public static class Model extends BaseModel {
		private final String file;
		private final Part part;

		public Model(BlockState state, String file) {
			super(state);
			this.file = file;
			part = showVariant(addPart(ItemStack.EMPTY), file, state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(part, file, state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}

}

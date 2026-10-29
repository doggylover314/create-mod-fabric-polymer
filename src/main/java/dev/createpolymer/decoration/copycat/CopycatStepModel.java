package dev.createpolymer.decoration.copycat;

import dev.createpolymer.visual.Orientation;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/** A step is the quarter of a block on the side it faces, in the lower or the upper half. */
public class CopycatStepModel extends CopycatModel {

	public CopycatStepModel(BlockState state, BlockState material) {
		super(state, material);
	}

	@Override
	protected void build(BlockState material) {
		Direction facing = state.getValue(CopycatStepBlock.FACING);
		boolean upperHalf = state.getValue(CopycatStepBlock.HALF) == Half.TOP;

		if (showsBase(material)) {
			// Create's copycat_base/step is centred on the block; the step sits against the side it faces
			addBase("copycat_base/step", Orientation.forHorizontal(facing), facing.getStepX() * .25f,
				upperHalf ? .5f : 0, facing.getStepZ() * .25f);
			return;
		}

		float[] min = {0, upperHalf ? 8 : 0, 0}, max = {16, upperHalf ? 16 : 8, 16};
		int axis = facing.getAxis().ordinal();
		if (facing.getAxisDirection() == AxisDirection.POSITIVE)
			min[axis] = 8;
		else
			max[axis] = 8;
		addBox(material, min[0], min[1], min[2], max[0], max[1], max[2]);
	}

}

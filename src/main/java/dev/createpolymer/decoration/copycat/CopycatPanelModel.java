package dev.createpolymer.decoration.copycat;

import dev.createpolymer.visual.Orientation;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A panel is 3 pixels thick and sits on the side opposite to its facing. Trapdoors are shown as they are (Create
 * picks the trapdoor state to fit the panel), bars as a flat pane ({@link CopycatBarsModel}), everything else is
 * the material squeezed into the panel.
 */
public class CopycatPanelModel extends CopycatModel {

	public CopycatPanelModel(BlockState state, BlockState material) {
		super(state, material);
	}

	@Override
	protected void build(BlockState material) {
		Direction facing = state.getValue(CopycatPanelBlock.FACING);

		if (showsBase(material)) {
			addBase("copycat_base/panel", Orientation.forFacing(facing), 0, 0, 0);
			return;
		}
		if (CopycatSpecialCases.isTrapdoorMaterial(material)) {
			addBlock(material, full());
			return;
		}
		if (CopycatSpecialCases.isBarsMaterial(material)) {
			CopycatBarsModel.build(this, material, facing);
			return;
		}

		float[] min = {0, 0, 0}, max = {16, 16, 16};
		int axis = facing.getAxis().ordinal();
		if (facing.getAxisDirection() == AxisDirection.POSITIVE)
			max[axis] = 3;
		else
			min[axis] = 13;
		addBox(material, min[0], min[1], min[2], max[0], max[1], max[2]);
	}

}

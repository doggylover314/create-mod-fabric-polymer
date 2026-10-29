package dev.createpolymer.decoration.copycat;

import org.joml.Matrix4f;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create shows a copycat panel with bars as material as one flat pane of the bars' texture on the inner face of the
 * panel (the copycat_bars model). Here it is one pane of the bars block itself (the two bars of an east-west or
 * north-south connected bars block, turned flat for vertical panels), 1 pixel from the panel's outer edge. The
 * pane is as thick as the bars' pane (2 pixels), where Create's is paper thin.
 */
public final class CopycatBarsModel {
	private CopycatBarsModel() {}

	static void build(CopycatModel model, BlockState material, Direction facing) {
		BlockState pane = material;
		if (pane.hasProperty(IronBarsBlock.WATERLOGGED))
			pane = pane.setValue(IronBarsBlock.WATERLOGGED, false);
		Axis axis = facing.getAxis();
		// the pane runs along the horizontal axis that is not the panel's
		boolean alongX = axis != Axis.X;
		pane = pane.setValue(IronBarsBlock.NORTH, !alongX)
			.setValue(IronBarsBlock.SOUTH, !alongX)
			.setValue(IronBarsBlock.EAST, alongX)
			.setValue(IronBarsBlock.WEST, alongX);

		// 7/16 from the centre is the pixel next to the panel's outer face
		float offset = -facing.getAxisDirection().getStep() * 7 / 16f;
		Matrix4f matrix = new Matrix4f().translate(axis == Axis.X ? offset : 0, axis == Axis.Y ? offset : 0,
			axis == Axis.Z ? offset : 0);
		if (axis == Axis.Y)
			matrix.rotateX((float) (Math.PI / 2));
		model.addBlock(pane, matrix.translate(-.5f, -.5f, -.5f));
	}

}

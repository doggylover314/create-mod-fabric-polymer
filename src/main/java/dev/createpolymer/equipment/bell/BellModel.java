package dev.createpolymer.equipment.bell;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;

/**
 * Create's bell renderer as a display model: the frame is the blockstate variant, the bell hangs in it and swings
 * while the block entity is ringing.
 */
public class BellModel extends BaseModel {

	private final String name;
	private final Part frame;
	private final Part bell;

	public BellModel(BlockState state, String name) {
		super(state);
		this.name = name;
		frame = addPart(ItemStack.EMPTY);
		bell = addPart(ModelItems.block(name));
		showVariant(frame, name, state);
	}

	@Override
	protected void onStateChanged(BlockState state) {
		showVariant(frame, name, state);
	}

	@Override
	protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		if (!(be instanceof AbstractBellBlockEntity bellBe))
			return;

		Direction facing = state.getValue(BellBlock.FACING);
		BellAttachType attachment = state.getValue(BellBlock.ATTACHMENT);
		float rY = AngleHelper.horizontalAngle(facing);
		if (attachment == BellAttachType.SINGLE_WALL || attachment == BellAttachType.DOUBLE_WALL)
			rY += 90;

		// The swing is about the world axis that is counter-clockwise from the ring direction, after the turn
		Matrix4f matrix = new Matrix4f();
		if (bellBe.isRinging) {
			Direction axis = bellBe.ringDirection.getCounterClockWise();
			matrix.rotation(getSwingAngle(bellBe.ringingTicks), axis.getStepX(), axis.getStepY(), axis.getStepZ());
		}
		matrix.rotateY(AngleHelper.rad(rY));
		bell.setMatrix(matrix, 1);
	}

	/** Create's BellRenderer.getSwingAngle */
	public static float getSwingAngle(float time) {
		float t = time / 1.5f;
		return 1.2f * (float) Math.sin(t / (float) Math.PI) / (2.5f + t / 3.0f);
	}
}

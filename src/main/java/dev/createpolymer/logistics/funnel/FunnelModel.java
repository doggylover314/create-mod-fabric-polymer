package dev.createpolymer.logistics.funnel;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Create's funnel models for the block state (from its blockstate file), the filter item and the four flap segments
 * of Create's FunnelVisual / FlapStuffs, driven by the block entity's chasing flap value.
 */
public class FunnelModel extends BaseModel {
	private static final int FLAP_COUNT = 4;
	private static final float X_OFFSET = 0.075f / 16f;
	private static final float SEGMENT_STEP = -3.05f / 16f;
	private static final Vec3 FUNNEL_PIVOT = VecHelper.voxelSpace(0, 10, 9.5f);

	private final Part body;
	private final Part[] flaps = new Part[FLAP_COUNT];
	private final float[] sentAngle = new float[FLAP_COUNT];
	private final FilterDisplay filter = new FilterDisplay(this);
	private BlockState shownState;
	private boolean flapIsBelt;
	private boolean flapShown;
	private float shownOffset = Float.NaN;
	private Direction shownFacing;

	public FunnelModel(BlockState state) {
		super(state);
		body = addPart(ItemStack.EMPTY);
		for (int i = 0; i < FLAP_COUNT; i++) {
			flaps[i] = addPart(ItemStack.EMPTY);
			sentAngle[i] = Float.NaN;
		}
		showBody(state);
	}

	private void showBody(BlockState state) {
		shownState = state;
		String file = BuiltInRegistries.BLOCK.getKey(state.getBlock())
			.getPath();
		showVariant(body, file, state);
	}

	@Override
	protected void onStateChanged(BlockState state) {
		showBody(state);
	}

	@Override
	protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		if (!(be instanceof FunnelBlockEntity funnel))
			return;
		BlockState current = funnel.getBlockState();
		if (current != shownState)
			showBody(current);
		filter.update(funnel);
		updateFlaps(funnel, current);
	}

	private void updateFlaps(FunnelBlockEntity funnel, BlockState state) {
		if (!AbstractFunnelBlock.isFunnel(state) || !funnel.hasFlap()) {
			if (flapShown) {
				flapShown = false;
				for (int i = 0; i < FLAP_COUNT; i++) {
					flaps[i].setItem(ItemStack.EMPTY);
					sentAngle[i] = Float.NaN;
				}
			}
			return;
		}

		boolean belt = !(state.getBlock() instanceof FunnelBlock);
		Direction facing = AbstractFunnelBlock.getFunnelFacing(state);
		float offset = -funnel.getFlapOffset();
		boolean reset = !flapShown || belt != flapIsBelt || offset != shownOffset || facing != shownFacing;
		if (reset) {
			flapShown = true;
			flapIsBelt = belt;
			shownOffset = offset;
			shownFacing = facing;
			for (int i = 0; i < FLAP_COUNT; i++) {
				flaps[i].setItem(ModelItems.block(belt ? "belt_funnel/flap" : "funnel/flap"));
				sentAngle[i] = Float.NaN;
			}
		}

		float flapness = funnel.flap.getValue();
		float horizontalAngle = AngleHelper.horizontalAngle(facing.getOpposite());
		for (int segment = 0; segment < FLAP_COUNT; segment++) {
			float angle = flapAngle(flapness, segment);
			if (sentAngle[segment] == angle)
				continue;
			boolean first = Float.isNaN(sentAngle[segment]);
			sentAngle[segment] = angle;
			Matrix4f m = new Matrix4f()
				.rotateY(Mth.DEG_TO_RAD * horizontalAngle)
				.translate(-.5f, -.5f, -.5f)
				.translate(X_OFFSET, 0, offset)
				.translate((float) FUNNEL_PIVOT.x, (float) FUNNEL_PIVOT.y, (float) FUNNEL_PIVOT.z)
				.rotateX(Mth.DEG_TO_RAD * angle)
				.translate((float) -FUNNEL_PIVOT.x, (float) -FUNNEL_PIVOT.y, (float) -FUNNEL_PIVOT.z)
				.translate(segment * SEGMENT_STEP, 0, 0);
			flaps[segment].setMatrix(m, first ? 0 : 1);
		}
	}

	/** Create's FlapStuffs.flapAngle */
	private static float flapAngle(float flapness, int segment) {
		float intensity = segment == 3 ? 1.5f : segment + 1;
		float abs = Math.abs(flapness);
		float flapAngle = Mth.sin((float) ((1 - abs) * Math.PI * intensity)) * 30 * flapness;
		if (flapness < 0)
			flapAngle *= .5f;
		return flapAngle;
	}
}

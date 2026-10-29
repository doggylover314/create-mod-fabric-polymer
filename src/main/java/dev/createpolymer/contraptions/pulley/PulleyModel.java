package dev.createpolymer.contraptions.pulley;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What Create's AbstractPulleyRenderer / ElevatorPulleyRenderer draw: the casing with a spinning shaft, the coil, a
 * magnet at the rope's end and the rope (or belt) between. Create slides one piece per block along with the contraption.
 * Here the pieces that are fully inside the rope sit still in the blocks under the pulley and are shown or hidden as the
 * rope grows; only the magnet and one stretched piece at the lower end move, so a moving pulley updates two entities per
 * tick. (The coil's scrolling texture is not shown.)
 */
public class PulleyModel extends BaseModel {
	/** The display glides over this many ticks, like the contraption's own entity does. */
	public static final int FOLLOW = 3;

	/**
	 * @param halfRopeAbove the half rope piece in the pulley's own block is shown above this offset
	 * @param ropeStart     how far the rope pieces start above the magnet's block (1 for the rope, whose magnet has its own rope stub)
	 * @param tailBase      where the lowest, stretched piece begins relative to the magnet's centre
	 */
	public record Style(String body, String coil, @Nullable String halfMagnet, String magnet, String halfRope,
		String rope, float halfRopeAbove, float ropeStart, float tailBase, Function<BlockState, Axis> shaftAxis,
		Function<BlockState, Quaternionf> coilRotation, Function<BlockState, Quaternionf> ropeRotation) {}

	public static final Style ROPE = new Style("rope_pulley", "rope_pulley/rope_coil", "rope_pulley/rope_half_magnet",
		"rope_pulley/pulley_magnet", "rope_pulley/rope_half", "rope_pulley/rope", .75f, 1, .5f,
		state -> state.getValue(BlockStateProperties.HORIZONTAL_AXIS),
		state -> Orientation.horizontalAngle(Direction.get(AxisDirection.POSITIVE,
			state.getValue(BlockStateProperties.HORIZONTAL_AXIS))),
		state -> new Quaternionf());

	public static final Style ELEVATOR = new Style("elevator_pulley", "elevator_pulley/rope_coil", null,
		"elevator_pulley/pulley_magnet", "elevator_pulley/rope_half", "elevator_pulley/rope", 0, 0, -.5f,
		state -> state.getValue(BlockStateProperties.HORIZONTAL_FACING)
			.getClockWise()
			.getAxis(),
		state -> Orientation.horizontalAngle(state.getValue(BlockStateProperties.HORIZONTAL_FACING)),
		state -> new Quaternionf().rotationY(
			AngleHelper.rad(180 + AngleHelper.horizontalAngle(state.getValue(BlockStateProperties.HORIZONTAL_FACING)))));

	private final Style style;
	private final Part body;
	private final Part shaft;
	private final Part coil;
	private final Part magnet;
	private final Part halfRope;
	private final Part tail;
	private final List<Part> ropes = new ArrayList<>();

	private int shownRopes;
	private float shownOffset = Float.NaN;
	private boolean shownRunning;
	private String shownMagnet = "";
	private boolean halfRopeShown;
	private boolean tailShown;
	private boolean first = true;

	public PulleyModel(BlockState state, Style style) {
		super(state);
		this.style = style;
		body = addPart(ItemStack.EMPTY);
		shaft = addPart(ModelItems.block("shaft"));
		coil = addPart(ModelItems.block(style.coil));
		magnet = addPart(ItemStack.EMPTY);
		halfRope = addPart(ItemStack.EMPTY);
		tail = addPart(ItemStack.EMPTY);
		onStateChanged(state);
	}

	@Override
	protected void onStateChanged(BlockState state) {
		showVariant(body, style.body, state);
		Axis shaftAxis = style.shaftAxis.apply(state);
		shaft.orientation(Orientation.forAxis(shaftAxis))
			.spinAxis(shaftAxis)
			.apply(0);
		coil.orientation(style.coilRotation.apply(state))
			.apply(0);
		Quaternionf ropeRotation = style.ropeRotation.apply(state);
		magnet.orientation(ropeRotation);
		halfRope.orientation(ropeRotation)
			.apply(0);
		for (int i = 0; i < ropes.size(); i++)
			ropes.get(i)
				.orientation(ropeRotation)
				.apply(0);
		shownOffset = Float.NaN;
	}

	@Override
	protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		if (!(be instanceof PulleyBlockEntity pulley))
			return;
		Axis shaftAxis = style.shaftAxis.apply(state);
		shaft.spin(time, pulley.getSpeed(),
			Orientation.cogOffset(false, shaftAxis, pos.getX(), pos.getY(), pos.getZ()));

		float offset = pulley.getDisplayOffset();
		boolean running = pulley.isDisplayedAsRunning();
		if (offset == shownOffset && running == shownRunning)
			return;
		shownOffset = offset;
		shownRunning = running;
		int interpolation = first ? 0 : FOLLOW;
		first = false;

		// magnet
		String magnetModel = running || offset == 0
			? (style.halfMagnet == null || offset > .25f ? style.magnet : style.halfMagnet)
			: "";
		if (!magnetModel.equals(shownMagnet)) {
			shownMagnet = magnetModel;
			magnet.setItem(magnetModel.isEmpty() ? ItemStack.EMPTY : ModelItems.block(magnetModel));
		}
		magnet.translation(0, -offset, 0)
			.apply(interpolation);

		// the piece of rope inside the pulley's own block
		boolean wantHalfRope = offset > style.halfRopeAbove;
		if (wantHalfRope != halfRopeShown) {
			halfRopeShown = wantHalfRope;
			halfRope.setItem(wantHalfRope ? ModelItems.block(style.halfRope) : ItemStack.EMPTY);
		}

		// full pieces that sit still, and one stretched piece up to the magnet
		float span = running ? offset - style.ropeStart : 0;
		int full = span > 0 ? (int) Math.floor(span) : 0;
		float gap = span > 0 ? span - full : 0;

		Quaternionf ropeRotation = style.ropeRotation.apply(state);
		while (ropes.size() < full) {
			Part rope = addPart(ItemStack.EMPTY);
			rope.orientation(ropeRotation)
				.translation(0, -(ropes.size() + 1), 0)
				.apply(0);
			ropes.add(rope);
		}
		for (int i = Math.min(shownRopes, full); i < Math.max(shownRopes, full); i++)
			ropes.get(i)
				.setItem(i < full ? ModelItems.block(style.rope) : ItemStack.EMPTY);
		shownRopes = full;

		boolean wantTail = gap > .02f;
		if (wantTail != tailShown) {
			tailShown = wantTail;
			tail.setItem(wantTail ? ModelItems.block(style.rope) : ItemStack.EMPTY);
		}
		if (wantTail) {
			float centre = -offset + style.tailBase + gap / 2;
			tail.setMatrix(new Matrix4f().translate(0, centre, 0)
				.rotate(ropeRotation)
				.scale(1, gap, 1), interpolation);
		}
	}
}

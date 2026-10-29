package dev.createpolymer.kinetics.belt;

import java.util.HashMap;
import java.util.Map;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.belt.BeltBlockEntity.CasingType;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.BlockstateModels;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.util.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The display entities of one belt block: the moving surface (two parts, or one on diagonal belts), the pulley
 * shaft, the casing and cover of an encased belt and, on a belt's controller, the items it carries.
 * <p>
 * Create picks the partial models in BeltRenderer.getBeltPartial and scrolls their texture. Scrolling is done with
 * animated textures here, see {@link BeltScroll}: the model is swapped when the speed, direction or part changes.
 */
public class BeltVisual extends BaseModel {
	private static final Map<String, ItemStack> MODELS = new HashMap<>();

	private final Part top = addPart(ItemStack.EMPTY);
	private final Part bottom = addPart(ItemStack.EMPTY);
	private final Part pulley = addPart(ItemStack.EMPTY);
	private final Part casing = addPart(ItemStack.EMPTY);
	private final Part cover = addPart(ItemStack.EMPTY);
	private final BeltRenderer items = new BeltRenderer(this);

	private String topModel = "", bottomModel = "", casingModel = "", coverModel = "";
	private Axis pulleyAxis;
	private BlockstateModels.@Nullable Variant casingVariant;

	public BeltVisual(BlockState state) {
		super(state);
		onStateChanged(state);
	}

	private static ItemStack model(String path) {
		return MODELS.computeIfAbsent(path, ModelItems::block);
	}

	private static void setModel(Part part, String current, String wanted, java.util.function.Consumer<String> remember) {
		if (current.equals(wanted))
			return;
		remember.accept(wanted);
		part.setItem(wanted.isEmpty() ? ItemStack.EMPTY : model(wanted));
	}

	@Override
	protected void onStateChanged(BlockState state) {
		BeltSlope slope = state.getValue(BeltBlock.SLOPE);
		BeltPart part = state.getValue(BeltBlock.PART);
		Direction facing = state.getValue(BeltBlock.HORIZONTAL_FACING);
		AxisDirection axisDirection = facing.getAxisDirection();

		boolean downward = slope == BeltSlope.DOWNWARD;
		boolean upward = slope == BeltSlope.UPWARD;
		boolean diagonal = downward || upward;
		boolean sideways = slope == BeltSlope.SIDEWAYS;

		// Create's BeltRenderer: center, rotate Y, Z and X, uncenter
		Quaternionf rotation = new Quaternionf()
			.rotationY(Mth.DEG_TO_RAD * (AngleHelper.horizontalAngle(facing) + (upward ? 180 : 0) + (sideways ? 270 : 0)))
			.rotateZ(Mth.DEG_TO_RAD * (sideways ? 90 : 0))
			.rotateX(Mth.DEG_TO_RAD * (!diagonal && slope != BeltSlope.HORIZONTAL ? 90 : 0));
		top.orientation(rotation).apply(0);
		bottom.orientation(rotation).apply(0);

		if (part != BeltPart.MIDDLE) {
			Direction dir = sideways ? Direction.UP : facing.getClockWise();
			Quaternionf q = new Quaternionf()
				.rotationY(dir.getAxis() == Axis.X ? Mth.DEG_TO_RAD * 90 : 0)
				.rotateX(Mth.DEG_TO_RAD * (dir.getAxis() == Axis.Y ? 180 : 90));
			pulleyAxis = dir.getAxis();
			pulley.orientation(q).spinAxis(pulleyAxis).apply(0);
			pulley.setItem(model("belt_pulley"));
		} else {
			pulleyAxis = null;
			pulley.setItem(ItemStack.EMPTY);
		}

		casingVariant = null;
		if (state.getValue(BeltBlock.CASING)) {
			BlockstateModels.Variant variant = BlockstateModels.get("belt", state);
			if (variant != null && variant.model().getPath().startsWith("block/belt_casing/"))
				casingVariant = variant;
		}

		// invalidate what depends on the state, update() puts it back
		topModel = bottomModel = casingModel = coverModel = "\0";
		top.setItem(ItemStack.EMPTY);
		bottom.setItem(ItemStack.EMPTY);
		casing.setItem(ItemStack.EMPTY);
		cover.setItem(ItemStack.EMPTY);
	}

	/** The partial model name below models/block/belt, e.g. "middle_bottom" or "diagonal_end". */
	static String partial(BlockState state, boolean bottom) {
		BeltSlope slope = state.getValue(BeltBlock.SLOPE);
		BeltPart part = state.getValue(BeltBlock.PART);
		AxisDirection axisDirection = state.getValue(BeltBlock.HORIZONTAL_FACING).getAxisDirection();
		boolean downward = slope == BeltSlope.DOWNWARD;
		boolean diagonal = slope.isDiagonal();
		boolean start = part == BeltPart.START;
		boolean end = part == BeltPart.END;
		if (downward || slope == BeltSlope.VERTICAL && axisDirection == AxisDirection.POSITIVE) {
			boolean b = start;
			start = end;
			end = b;
		}
		// Create's BeltRenderer.getBeltPartial
		String name = start ? "start" : end ? "end" : "middle";
		if (diagonal)
			return "diagonal_" + name;
		return bottom ? name + "_bottom" : name;
	}

	@Override
	protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		if (!(be instanceof BeltBlockEntity belt))
			return;

		BeltSlope slope = state.getValue(BeltBlock.SLOPE);
		boolean diagonal = slope.isDiagonal();
		float speed = belt.getSpeed();

		// moving surface
		int rate = BeltScroll.rateIndex(speed, diagonal);
		boolean positive = rate >= 0 && BeltScroll.positiveScroll(slope, state.getValue(BeltBlock.HORIZONTAL_FACING), speed);
		String topPartial = partial(state, false);
		String bottomPartial = partial(state, true);
		setModel(top, topModel, surface(topPartial, rate, positive), s -> topModel = s);
		setModel(bottom, bottomModel, diagonal ? "" : surface(bottomPartial, rate, positive), s -> bottomModel = s);

		// pulley
		if (pulleyAxis != null)
			pulley.spin(time, speed, 0);

		// casing and cover
		String wantedCasing = casingVariant == null || belt.casing == CasingType.NONE ? "" : casingModel(belt, casingVariant);
		if (!wantedCasing.equals(casingModel)) {
			if (casingVariant != null)
				casing.orientation(casingVariant.rotation()).apply(0);
			setModel(casing, casingModel, wantedCasing, s -> casingModel = s);
		}
		setModel(cover, coverModel, coverModel(belt), s -> coverModel = s);

		if (belt.isController())
			items.render(belt);
		else
			items.clear();
	}

	private static String surface(String partial, int rate, boolean positive) {
		if (rate < 0)
			return "belt/" + partial;
		return BeltScroll.modelPath(partial, positive, rate);
	}

	/** The casing model, with the andesite casing's texture where Create's BeltModel shifts it. */
	private static String casingModel(BeltBlockEntity belt, BlockstateModels.Variant variant) {
		String name = variant.model().getPath().substring("block/".length());
		return belt.casing == CasingType.ANDESITE ? name.replace("belt_casing/", "belt_casing_andesite/") : name;
	}

	private String coverModel(BeltBlockEntity belt) {
		if (!belt.covered || !state.getValue(BeltBlock.CASING) || belt.casing == CasingType.NONE)
			return "";
		boolean alongX = state.getValue(BeltBlock.HORIZONTAL_FACING)
			.getAxis() == Axis.X;
		return "belt_cover/" + (belt.casing == CasingType.BRASS ? "brass" : "andesite") + "_belt_cover_" + (alongX ? "x" : "z");
	}
}

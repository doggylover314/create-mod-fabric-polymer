package dev.createpolymer.processing.burner;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Display-entity version of Create's BlazeBurnerRenderer / BlazeBurnerVisual: the cage, the bobbing blaze head
 * (turned by the block entity), the two rods and the flame. The flame's scrolling texture is an animated texture here.
 * The train and logistics hats are not ported.
 */
public class BlazeBurnerModel extends BaseModel {
	/** Ticks between position updates of the bobbing parts; the client interpolates over this time. */
	private static final int BOB_INTERVAL = 6;
	/** Ticks between updates while the head is turning or lowering. */
	private static final int MOVE_INTERVAL = 2;

	private final Part head;
	private final Part goggles;
	private final Part smallRods;
	private final Part largeRods;
	private final Part flame;

	private HeatLevel shownHeat = HeatLevel.NONE;
	private boolean shownBlockAbove;
	private boolean shownGoggles;
	private boolean shownFlame;
	private float lastAngle = Float.NaN;
	private float lastAnimation = Float.NaN;

	public BlazeBurnerModel(BlockState state) {
		super(state);
		addPart(ModelItems.block("blaze_burner/block"));
		head = addFullBright(ItemStack.EMPTY);
		goggles = addFullBright(ItemStack.EMPTY);
		smallRods = addFullBright(ItemStack.EMPTY);
		largeRods = addFullBright(ItemStack.EMPTY);
		flame = addFullBright(ItemStack.EMPTY);
	}

	private Part addFullBright(ItemStack stack) {
		Part part = addPart(stack);
		part.element.setBrightness(Brightness.FULL_BRIGHT);
		return part;
	}

	@Override
	protected void onStateChanged(BlockState state) {
		// the next update compares the heat level with what is shown
	}

	@Override
	protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		HeatLevel heat = BlazeBurnerBlock.getHeatLevelOf(state);
		if (heat == HeatLevel.NONE || !(be instanceof BlazeBurnerBlockEntity burner)) {
			if (shownHeat != HeatLevel.NONE)
				hideHead();
			return;
		}

		float animation = burner.headAnimation.getValue() * .175f;
		boolean blockAbove = animation > 0.125f;
		float angle = burner.headAngle.getValue();
		boolean canDrawFlame = heat.isAtLeast(HeatLevel.FADING) && blockAbove;

		boolean changed = false;
		if (heat != shownHeat || blockAbove != shownBlockAbove) {
			boolean hadRods = shownHeat.isAtLeast(HeatLevel.FADING);
			boolean hasRods = heat.isAtLeast(HeatLevel.FADING);
			head.setItem(ModelItems.block(getBlazeModel(heat, blockAbove)));
			if (hasRods) {
				boolean seething = heat == HeatLevel.SEETHING;
				smallRods.setItem(ModelItems.block("blaze_burner/" + (seething ? "superheated_rods_small" : "rods_small")));
				largeRods.setItem(ModelItems.block("blaze_burner/" + (seething ? "superheated_rods_large" : "rods_large")));
			} else if (hadRods) {
				smallRods.setItem(ItemStack.EMPTY);
				largeRods.setItem(ItemStack.EMPTY);
			}
			shownHeat = heat;
			shownBlockAbove = blockAbove;
			changed = true;
			// the goggles model depends on the head model
			shownGoggles = false;
		}

		if (canDrawFlame != shownFlame || (canDrawFlame && changed)) {
			flame.setItem(canDrawFlame ? ModelItems.block("blaze_burner/" + flameModel(heat)) : ItemStack.EMPTY);
			shownFlame = canDrawFlame;
			changed = true;
		}

		if (burner.goggles != shownGoggles) {
			goggles.setItem(burner.goggles ? ModelItems.block("blaze_burner/" + (heat.isAtLeast(HeatLevel.FADING) ? "goggles" : "goggles_small")) : ItemStack.EMPTY);
			shownGoggles = burner.goggles;
			changed = true;
		}

		boolean moving = Float.isNaN(lastAngle) || Math.abs(angle - lastAngle) > 0.4f
			|| Math.abs(animation - lastAnimation) > 0.0005f;
		int interpolation;
		if (changed)
			interpolation = 0;
		else if (moving && time % MOVE_INTERVAL == 0)
			interpolation = MOVE_INTERVAL;
		else if (time % BOB_INTERVAL == 0)
			interpolation = BOB_INTERVAL;
		else
			return;

		lastAngle = angle;
		lastAnimation = animation;
		updateTransforms(heat, animation, angle, pos.hashCode(), time + interpolation, interpolation);
	}

	/** The bobbing and turning of BlazeBurnerRenderer.renderShared, for the given tick. */
	private void updateTransforms(HeatLevel heat, float animation, float angle, int hashCode, long time, int interpolation) {
		float horizontalAngle = AngleHelper.rad(angle);
		float renderTick = time + (hashCode % 13) * 16f;
		float offsetMult = heat.isAtLeast(HeatLevel.FADING) ? 64 : 16;
		float offset = Mth.sin((float) ((renderTick / 16f) % (2 * Math.PI))) / offsetMult;
		float offset1 = Mth.sin((float) ((renderTick / 16f + Math.PI) % (2 * Math.PI))) / offsetMult;
		float offset2 = Mth.sin((float) ((renderTick / 16f + Math.PI / 2) % (2 * Math.PI))) / offsetMult;
		float headY = offset - (animation * .75f);

		head.setMatrix(new Matrix4f().translate(0, headY, 0).rotateY(horizontalAngle), interpolation);

		if (shownGoggles)
			goggles.setMatrix(new Matrix4f().translate(0, headY + 8 / 16f, 0).rotateY(horizontalAngle), interpolation);

		if (heat.isAtLeast(HeatLevel.FADING)) {
			smallRods.setMatrix(new Matrix4f().translate(0, offset1 + animation + .125f, 0), interpolation);
			largeRods.setMatrix(new Matrix4f().translate(0, offset2 + animation - 3 / 16f, 0), interpolation);
		}

		if (shownFlame)
			flame.setMatrix(new Matrix4f().rotateY(horizontalAngle), interpolation);
	}

	private void hideHead() {
		head.setItem(ItemStack.EMPTY);
		goggles.setItem(ItemStack.EMPTY);
		smallRods.setItem(ItemStack.EMPTY);
		largeRods.setItem(ItemStack.EMPTY);
		flame.setItem(ItemStack.EMPTY);
		shownHeat = HeatLevel.NONE;
		shownGoggles = false;
		shownFlame = false;
		lastAngle = Float.NaN;
	}

	/** BlazeBurnerRenderer.getBlazeModel, as a model path. */
	public static String getBlazeModel(HeatLevel heatLevel, boolean blockAbove) {
		if (heatLevel.isAtLeast(HeatLevel.SEETHING)) {
			return "blaze_burner/blaze/" + (blockAbove ? "super_active" : "super");
		} else if (heatLevel.isAtLeast(HeatLevel.FADING)) {
			return "blaze_burner/blaze/" + (blockAbove && heatLevel.isAtLeast(HeatLevel.KINDLED) ? "active" : "idle");
		} else {
			return "blaze_burner/blaze/inert";
		}
	}

	/** One flame model per heat level, because the scroll speed depends on it. */
	private static String flameModel(HeatLevel heat) {
		return switch (heat) {
			case SEETHING -> "flame_seething";
			case KINDLED -> "flame_kindled";
			default -> "flame_fading";
		};
	}
}

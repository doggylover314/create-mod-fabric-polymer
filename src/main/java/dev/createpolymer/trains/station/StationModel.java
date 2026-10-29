package dev.createpolymer.trains.station;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

import dev.createpolymer.logistics.depot.DepotRenderer;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What players see of a station: the base block, and the flag on its pole (Create's StationRenderer). The flag goes
 * up while a train is at the station or the station assembles, and it shows a different model for each of the
 * states.
 */
public class StationModel extends BaseModel {

	private static final ItemStack BASE = ModelItems.block("track_station/block");
	private static final ItemStack FLAG_ON = ModelItems.block("track_station/flag_on");
	private static final ItemStack FLAG_OFF = ModelItems.block("track_station/flag_off");
	private static final ItemStack FLAG_ASSEMBLE = ModelItems.block("track_station/flag_assemble");

	private final Part base;
	private final Part flag;
	/** The auto-schedule that rests on the station (Create: StationRenderer draws the items of the depot). */
	private final DepotRenderer items;

	private ItemStack shownFlag = ItemStack.EMPTY;
	private float shownFlagValue = Float.NaN;
	private int shownYRot = Integer.MIN_VALUE;
	private boolean shownFlipped;

	public StationModel(BlockState state) {
		super(state);
		base = addPart(BASE);
		base.apply(0);
		flag = addPart(ItemStack.EMPTY);
		items = new DepotRenderer(this);
	}

	@Override
	protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		if (be instanceof StationBlockEntity station)
			items.render(station.depotBehaviour, station.getDataVersion());

		if (!(be instanceof StationBlockEntity station) || !station.resolveFlagAngle()) {
			if (!shownFlag.isEmpty()) {
				flag.setItem(ItemStack.EMPTY);
				shownFlag = ItemStack.EMPTY;
			}
			return;
		}

		GlobalStation globalStation = station.getStation();
		boolean assembling = station.isAssembling();
		ItemStack model;
		if (!assembling || (globalStation == null || globalStation.getPresentTrain() != null))
			model = station.flag.getValue() > 0.75f ? FLAG_ON : FLAG_OFF;
		else
			model = FLAG_ASSEMBLE;

		boolean modelChanged = model != shownFlag;
		if (modelChanged) {
			flag.setItem(model);
			shownFlag = model;
		}

		float value = station.flag.getValue();
		if (!modelChanged && value == shownFlagValue && station.getFlagYRot() == shownYRot
			&& station.isFlagFlipped() == shownFlipped)
			return;
		shownFlagValue = value;
		shownYRot = station.getFlagYRot();
		shownFlipped = station.isFlagFlipped();

		flag.setMatrix(flagMatrix(station), 1);
	}

	/** Create's StationRenderer.renderFlag / transformFlag, moved to the centre of the block the display uses. */
	static Matrix4f flagMatrix(StationBlockEntity be) {
		float value = be.flag.getValue();
		float progress = (float) (Math.pow(Math.min(value * 5, 1), 2));
		if (be.flag.getChaseTarget() > 0 && !be.flag.settled() && progress == 1) {
			float wiggleProgress = (value - .2f) / .8f;
			progress += (Math.sin(wiggleProgress * (2 * Math.PI) * 4) / 8f) / Math.max(1, 8f * wiggleProgress);
		}

		boolean flipped = be.isFlagFlipped();
		float nudge = 1 / 512f;
		return new Matrix4f().translate(.5f, .5f, .5f)
			.rotateY((float) Math.toRadians(be.getFlagYRot()))
			.translate(nudge, 9.5f / 16f, flipped ? 14f / 16f - nudge : 2f / 16f + nudge)
			.translate(-.5f, -.5f, -.5f)
			.rotateX((float) Math.toRadians((flipped ? 1 : -1) * (progress * 90 + 270)))
			.translate(0.5f / 16, 0, 0)
			.rotateY(flipped ? 0 : (float) Math.PI)
			.translate(-0.5f / 16, 0, 0)
			.translate(.5f, .5f, .5f);
	}
}

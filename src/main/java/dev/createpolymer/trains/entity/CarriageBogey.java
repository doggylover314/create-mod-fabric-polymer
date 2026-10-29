package dev.createpolymer.trains.entity;

import static dev.createpolymer.trains.bogey.AbstractBogeyBlockEntity.BOGEY_DATA_KEY;
import static dev.createpolymer.trains.bogey.AbstractBogeyBlockEntity.BOGEY_STYLE_KEY;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.trains.bogey.AbstractBogeyBlock;
import dev.createpolymer.trains.bogey.AbstractBogeyBlockEntity;
import dev.createpolymer.trains.bogey.AllBogeyStyles;
import dev.createpolymer.trains.bogey.BogeySizes.BogeySize;
import dev.createpolymer.trains.bogey.BogeyStyle;
import dev.createpolymer.trains.graph.DimensionPalette;
import dev.createpolymer.trains.graph.TrackGraph;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.NBTHelper;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public class CarriageBogey {

	public static final String UPSIDE_DOWN_KEY = "UpsideDown";

	public Carriage carriage;
	boolean isLeading;

	public CompoundTag bogeyData;

	AbstractBogeyBlock<?> type;
	boolean upsideDown;
	public Couple<TravellingPoint> points;

	public LerpedFloat wheelAngle;
	public LerpedFloat yaw;
	public LerpedFloat pitch;

	public Couple<Vec3> couplingAnchors;

	int derailAngle;

	/** How far the wheels turned in the last tick, in degrees (the display limits what it shows). */
	public double wheelAngleStep;

	public CarriageBogey(AbstractBogeyBlock<?> type, boolean upsideDown, CompoundTag bogeyData) {
		this(type, upsideDown, bogeyData, new TravellingPoint(), new TravellingPoint());
	}

	public CarriageBogey(AbstractBogeyBlock<?> type, boolean upsideDown, CompoundTag bogeyData, TravellingPoint point,
		TravellingPoint point2) {
		this.type = type;
		this.upsideDown = type.canBeUpsideDown() && upsideDown;
		point.upsideDown = this.upsideDown;
		point2.upsideDown = this.upsideDown;
		if (bogeyData == null || bogeyData.isEmpty())
			bogeyData = this.createBogeyData(); // Prevent Crash When Updating
		bogeyData.putBoolean(UPSIDE_DOWN_KEY, upsideDown);
		this.bogeyData = bogeyData;
		points = Couple.create(point, point2);
		wheelAngle = LerpedFloat.angular();
		yaw = LerpedFloat.angular();
		pitch = LerpedFloat.angular();
		derailAngle = TravellingPoint.RANDOM.nextInt(60) - 30;
		couplingAnchors = Couple.create(null, null);
	}

	public AbstractBogeyBlock<?> getBlock() {
		return type;
	}

	public ResourceKey<Level> getDimension() {
		TravellingPoint leading = leading();
		TravellingPoint trailing = trailing();
		if (leading.edge == null || trailing.edge == null)
			return null;
		if (leading.edge.isInterDimensional() || trailing.edge.isInterDimensional())
			return null;
		ResourceKey<Level> dimension1 = leading.node1.getLocation().dimension;
		ResourceKey<Level> dimension2 = trailing.node1.getLocation().dimension;
		if (dimension1.equals(dimension2))
			return dimension1;
		return null;
	}

	public void updateAngles(CarriageContraptionEntity entity, double distanceMoved) {
		double angleDiff = 360 * distanceMoved / (Math.PI * 2 * type.getWheelRadius());
		wheelAngleStep = -angleDiff;

		float xRot = 0;
		float yRot = 0;

		if (leading().edge == null || carriage.train.derailed) {
			yRot = -90 + entity.yaw - derailAngle;
		} else if (!entity.level()
			.dimension()
			.equals(getDimension())) {
			yRot = -90 + entity.yaw;
			xRot = 0;
		} else {
			Vec3 positionVec = leading().getPosition(carriage.train.graph);
			Vec3 coupledVec = trailing().getPosition(carriage.train.graph);
			double diffX = positionVec.x - coupledVec.x;
			double diffY = positionVec.y - coupledVec.y;
			double diffZ = positionVec.z - coupledVec.z;
			yRot = AngleHelper.deg(Mth.atan2(diffZ, diffX)) + 90;
			xRot = AngleHelper.deg(Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ)));
		}

		double newWheelAngle = (wheelAngle.getValue() - angleDiff) % 360;

		for (boolean twice : Iterate.trueAndFalse) {
			if (twice && !entity.firstPositionUpdate)
				continue;
			wheelAngle.setValue(newWheelAngle);
			pitch.setValue(xRot);
			yaw.setValue(-yRot);
		}
	}

	public TravellingPoint leading() {
		TravellingPoint point = points.getFirst();
		point.upsideDown = isUpsideDown();
		return point;
	}

	public TravellingPoint trailing() {
		TravellingPoint point = points.getSecond();
		point.upsideDown = isUpsideDown();
		return point;
	}

	public double getStress() {
		if (getDimension() == null)
			return 0;
		if (carriage.train.derailed)
			return 0;
		return type.getWheelPointSpacing() - leading().getPosition(carriage.train.graph)
			.distanceTo(trailing().getPosition(carriage.train.graph));
	}

	@Nullable
	public Vec3 getAnchorPosition() {
		return getAnchorPosition(false);
	}

	@Nullable
	public Vec3 getAnchorPosition(boolean flipUpsideDown) {
		if (leading().edge == null)
			return null;
		return points.getFirst()
			.getPosition(carriage.train.graph, flipUpsideDown)
			.add(points.getSecond()
				.getPosition(carriage.train.graph, flipUpsideDown))
			.scale(.5);
	}

	public void updateCouplingAnchor(Vec3 entityPos, float entityXRot, float entityYRot, int bogeySpacing,
		float partialTicks, boolean leading) {
		boolean selfUpsideDown = isUpsideDown();
		boolean leadingUpsideDown = carriage.leadingBogey()
			.isUpsideDown();
		Vec3 thisOffset = type.getConnectorAnchorOffset(selfUpsideDown);
		thisOffset = thisOffset.multiply(1, 1, leading ? -1 : 1);

		thisOffset = VecHelper.rotate(thisOffset, pitch.getValue(partialTicks), Axis.X);
		thisOffset = VecHelper.rotate(thisOffset, yaw.getValue(partialTicks), Axis.Y);
		thisOffset = VecHelper.rotate(thisOffset, -entityYRot - 90, Axis.Y);
		thisOffset = VecHelper.rotate(thisOffset, entityXRot, Axis.X);
		thisOffset = VecHelper.rotate(thisOffset, -180, Axis.Y);
		thisOffset = thisOffset.add(0, 0, leading ? 0 : -bogeySpacing);
		thisOffset = VecHelper.rotate(thisOffset, 180, Axis.Y);
		thisOffset = VecHelper.rotate(thisOffset, -entityXRot, Axis.X);
		thisOffset = VecHelper.rotate(thisOffset, entityYRot + 90, Axis.Y);
		if (selfUpsideDown != leadingUpsideDown)
			thisOffset = thisOffset.add(0, selfUpsideDown ? -2 : 2, 0);

		couplingAnchors.set(leading, entityPos.add(thisOffset));
	}

	public CompoundTag write(DimensionPalette dimensions) {
		CompoundTag tag = new CompoundTag();
		tag.putString("Type", BuiltInRegistries.BLOCK.getKey((Block) type)
			.toString());
		tag.put("Points", points.serializeEach(tp -> tp.write(dimensions)));
		tag.putBoolean("UpsideDown", upsideDown);
		bogeyData.putBoolean(UPSIDE_DOWN_KEY, upsideDown);
		NBTHelper.writeResourceLocation(bogeyData, BOGEY_STYLE_KEY, getStyle().id);
		tag.put(BOGEY_DATA_KEY, bogeyData);
		return tag;
	}

	public static CarriageBogey read(CompoundTag tag, TrackGraph graph, DimensionPalette dimensions) {
		Identifier location = Identifier.parse(tag.getStringOr("Type", ""));
		AbstractBogeyBlock<?> type = (AbstractBogeyBlock<?>) BuiltInRegistries.BLOCK.getValue(location);
		boolean upsideDown = tag.getBooleanOr("UpsideDown", false);
		Couple<TravellingPoint> points = Couple.deserializeEach(tag.getListOrEmpty("Points"),
			c -> TravellingPoint.read(c, graph, dimensions));
		CompoundTag data = tag.getCompoundOrEmpty(AbstractBogeyBlockEntity.BOGEY_DATA_KEY);
		return new CarriageBogey(type, upsideDown, data, points.getFirst(), points.getSecond());
	}

	public BogeyStyle getStyle() {
		Identifier location = NBTHelper.readResourceLocation(this.bogeyData, BOGEY_STYLE_KEY);
		BogeyStyle style = AllBogeyStyles.BOGEY_STYLES.get(location);
		return style != null ? style : AllBogeyStyles.STANDARD; // just for safety
	}

	public BogeySize getSize() {
		return type.getSize();
	}

	private CompoundTag createBogeyData() {
		BogeyStyle style = type != null ? type.getDefaultStyle() : AllBogeyStyles.STANDARD;
		CompoundTag nbt = style.defaultData != null ? style.defaultData.copy() : new CompoundTag();
		NBTHelper.writeResourceLocation(nbt, BOGEY_STYLE_KEY, style.id);
		nbt.putBoolean(UPSIDE_DOWN_KEY, isUpsideDown());
		return nbt;
	}

	void setLeading() {
		isLeading = true;
	}

	public boolean isLeading() {
		return isLeading;
	}

	public boolean isUpsideDown() {
		return type.canBeUpsideDown() && upsideDown;
	}
}

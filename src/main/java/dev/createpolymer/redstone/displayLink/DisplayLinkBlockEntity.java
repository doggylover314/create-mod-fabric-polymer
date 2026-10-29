package dev.createpolymer.redstone.displayLink;

import java.util.List;

import dev.createpolymer.api.behaviour.display.DisplaySource;
import dev.createpolymer.api.behaviour.display.DisplayTarget;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelPosition;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class DisplayLinkBlockEntity extends LinkWithBulbBlockEntity {

	protected BlockPos targetOffset;

	public DisplaySource activeSource;
	private CompoundTag sourceConfig;

	public DisplayTarget activeTarget;
	public int targetLine;

	public int refreshTicks;
	public FactoryPanelSupportBehaviour factoryPanelSupport;

	public DisplayLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		targetOffset = BlockPos.ZERO;
		sourceConfig = new CompoundTag();
		targetLine = 0;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(factoryPanelSupport = new FactoryPanelSupportBehaviour(this, () -> false, () -> false, () -> {
			updateGatheredData();
		}));
	}

	@Override
	public void tick() {
		super.tick();

		if (activeSource == null)
			return;
		if (level.isClientSide())
			return;

		refreshTicks++;
		if (refreshTicks < activeSource.getPassiveRefreshTicks() || !activeSource.shouldPassiveReset())
			return;
		tickSource();
	}

	public void tickSource() {
		refreshTicks = 0;
		if (getBlockState().getOptionalValue(DisplayLinkBlock.POWERED)
			.orElse(true))
			return;
		if (!level.isClientSide())
			updateGatheredData();
	}

	public void onNoLongerPowered() {
		if (activeSource == null)
			return;
		refreshTicks = 0;
		activeSource.onSignalReset(new DisplayLinkContext(level, this));
		updateGatheredData();
	}

	public void updateGatheredData() {
		BlockPos sourcePosition = getSourcePosition();
		BlockPos targetPosition = getTargetPosition();

		if (!level.isLoaded(targetPosition) || !level.isLoaded(sourcePosition))
			return;

		DisplayTarget target = DisplayTarget.get(level, targetPosition);
		List<DisplaySource> sources = DisplaySource.getAll(level, sourcePosition);
		boolean notify = false;

		if (activeTarget != target) {
			activeTarget = target;
			notify = true;
		}

		if (activeSource != null && !sources.contains(activeSource)) {
			activeSource = null;
			sourceConfig = new CompoundTag();
			notify = true;
		}

		if (notify)
			notifyUpdate();
		if (activeSource == null || activeTarget == null)
			return;

		DisplayLinkContext context = new DisplayLinkContext(level, this);
		activeSource.transferData(context, activeTarget, targetLine);
		sendPulseNextSync();
		setChanged();
		sendData();
	}

	/**
	 * Create's DisplayLinkConfigurationPacket.applySettings: what the configuration screen sends when it closes.
	 * {@code configData} holds the id of the chosen source and the values of its options.
	 */
	public void applySettings(CompoundTag configData, int targetLine) {
		this.targetLine = targetLine;

		if (!configData.contains("Id")) {
			notifyUpdate();
			return;
		}

		Identifier id = Identifier.tryParse(configData.getStringOr("Id", ""));
		DisplaySource source = DisplaySource.get(id);
		if (source == null) {
			notifyUpdate();
			return;
		}

		if (activeSource == null || activeSource != source) {
			activeSource = source;
			setSourceConfig(configData.copy());
		} else {
			getSourceConfig()
				.merge(configData);
		}

		updateGatheredData();
		notifyUpdate();
	}

	private void writeGatheredData(ValueOutput output) {
		output.store("TargetOffset", BlockPos.CODEC, targetOffset);
		output.putInt("TargetLine", targetLine);

		if (activeSource != null) {
			CompoundTag data = sourceConfig.copy();
			Identifier id = DisplaySource.getKey(this.activeSource);
			if (id != null) {
				data.putString("Id", id.toString());
			}
			output.store("Source", CompoundTag.CODEC, data);
		}
	}

	@Override
	public void writeSafe(ValueOutput output) {
		super.writeSafe(output);
		writeGatheredData(output);
	}

	@Override
	protected void write(ValueOutput output) {
		super.write(output);
		writeGatheredData(output);
	}

	@Override
	protected void read(ValueInput input) {
		super.read(input);
		targetOffset = input.read("TargetOffset", BlockPos.CODEC)
			.orElse(BlockPos.ZERO);
		targetLine = input.getIntOr("TargetLine", 0);

		CompoundTag data = input.read("Source", CompoundTag.CODEC)
			.orElse(null);
		if (data == null)
			return;

		activeSource = DisplaySource.get(Identifier.tryParse(data.getStringOr("Id", "")));
		sourceConfig = new CompoundTag();
		if (activeSource != null)
			sourceConfig = data.copy();
	}

	public void target(BlockPos targetPosition) {
		this.targetOffset = targetPosition.subtract(worldPosition);
	}

	public BlockPos getSourcePosition() {
		for (FactoryPanelPosition position : factoryPanelSupport.getLinkedPanels())
			return position.pos();
		return worldPosition.relative(getDirection());
	}

	public CompoundTag getSourceConfig() {
		return sourceConfig;
	}

	public void setSourceConfig(CompoundTag sourceConfig) {
		this.sourceConfig = sourceConfig;
	}

	public Direction getDirection() {
		return getBlockState().getOptionalValue(DisplayLinkBlock.FACING)
			.orElse(Direction.UP)
			.getOpposite();
	}

	public BlockPos getTargetPosition() {
		return worldPosition.offset(targetOffset);
	}

	public BlockPos getTargetOffset() {
		return targetOffset;
	}

	private static final Vec3 bulbOffset = VecHelper.voxelSpace(11, 7, 5);
	private static final Vec3 bulbOffsetVertical = VecHelper.voxelSpace(5, 7, 11);

	@Override
	public Vec3 getBulbOffset(BlockState state) {
		if (state.getOptionalValue(DisplayLinkBlock.FACING).orElse(Direction.UP).getAxis().isVertical())
			return bulbOffsetVertical;
		return bulbOffset;
	}

}

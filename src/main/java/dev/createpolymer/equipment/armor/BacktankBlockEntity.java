package dev.createpolymer.equipment.armor;

import dev.createpolymer.equipment.EquipmentSounds;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Ported from Create's BacktankBlockEntity (MIT, The Create Team). */
public class BacktankBlockEntity extends KineticBlockEntity implements Nameable {

	public int airLevel;
	public int airLevelTimer;
	private Component defaultName;
	private @Nullable Component customName;

	private int capacityEnchantLevel;

	private DataComponentPatch componentPatch;

	public BacktankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		defaultName = getDefaultName(state);
		componentPatch = DataComponentPatch.EMPTY;
	}

	public static Component getDefaultName(BlockState state) {
		if (state.is(EquipmentRegistry.NETHERITE_BACKTANK)) {
			return EquipmentRegistry.NETHERITE_BACKTANK_ITEM.getDefaultInstance()
				.getItemName();
		}

		return EquipmentRegistry.COPPER_BACKTANK_ITEM.getDefaultInstance()
			.getItemName();
	}

	@Override
	public void tick() {
		super.tick();
		if (getSpeed() == 0)
			return;

		BlockState state = getBlockState();
		BooleanProperty waterProperty = BlockStateProperties.WATERLOGGED;
		if (state.hasProperty(waterProperty) && state.getValue(waterProperty))
			return;

		if (airLevelTimer > 0) {
			airLevelTimer--;
			return;
		}

		int max = BacktankUtil.maxAir(capacityEnchantLevel);
		if (level.isClientSide())
			return;

		if (airLevel == max)
			return;

		int prevComparatorLevel = getComparatorOutput();
		float abs = Math.abs(getSpeed());
		int increment = Mth.clamp(((int) abs - 100) / 20, 1, 5);
		airLevel = Math.min(max, airLevel + increment);
		if (getComparatorOutput() != prevComparatorLevel && !level.isClientSide())
			level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
		if (airLevel == max) {
			setChanged();
			playFilledEffect();
		}
		airLevelTimer = Mth.clamp((int) (128f - abs / 5f) - 108, 0, 20);
	}

	public int getComparatorOutput() {
		int max = BacktankUtil.maxAir(capacityEnchantLevel);
		return fractionToRedstoneLevel(airLevel / (float) max);
	}

	/** Create's ComparatorUtil.fractionToRedstoneLevel */
	public static int fractionToRedstoneLevel(double frac) {
		return Mth.floor(Mth.clamp(frac * 14 + (frac > 0 ? 1 : 0), 0, 15));
	}

	@Override
	protected void write(ValueOutput compound) {
		super.write(compound);
		compound.putInt("Air", airLevel);
		compound.putInt("Timer", airLevelTimer);
		compound.putInt("CapacityEnchantment", capacityEnchantLevel);

		if (this.customName != null)
			compound.store("CustomName", ComponentSerialization.CODEC, this.customName);

		compound.store("Components", DataComponentPatch.CODEC, componentPatch);
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		airLevel = compound.getIntOr("Air", 0);
		airLevelTimer = compound.getIntOr("Timer", 0);
		capacityEnchantLevel = compound.getIntOr("CapacityEnchantment", 0);

		this.customName = compound.read("CustomName", ComponentSerialization.CODEC)
			.orElse(null);

		componentPatch = compound.read("Components", DataComponentPatch.CODEC)
			.orElse(DataComponentPatch.EMPTY);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter componentInput) {
		setAirLevel(componentInput.getOrDefault(EquipmentRegistry.BACKTANK_AIR, 0));
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		components.set(EquipmentRegistry.BACKTANK_AIR, airLevel);
	}

	protected void playFilledEffect() {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		EquipmentSounds.confirm(level, worldPosition, 0.4f, 1);
		Vec3 baseMotion = new Vec3(.25, 0.1, 0);
		Vec3 baseVec = VecHelper.getCenterOf(worldPosition);
		for (int i = 0; i < 360; i += 10) {
			Vec3 m = VecHelper.rotate(baseMotion, i, Axis.Y);
			Vec3 v = baseVec.add(m.normalize()
				.scale(.25f));

			serverLevel.sendParticles(ParticleTypes.SPIT, v.x, v.y, v.z, 0, m.x, m.y, m.z, 1);
		}
	}

	@Override
	public Component getName() {
		return this.customName != null ? this.customName : defaultName;
	}

	@Override
	public @Nullable Component getCustomName() {
		return customName;
	}

	public int getAirLevel() {
		return airLevel;
	}

	public void setAirLevel(int airLevel) {
		this.airLevel = airLevel;
		sendData();
	}

	public void setCustomName(Component customName) {
		this.customName = customName;
	}

	public void setCapacityEnchantLevel(int capacityEnchantLevel) {
		this.capacityEnchantLevel = capacityEnchantLevel;
	}

	public void setComponentPatch(DataComponentPatch componentPatch) {
		this.componentPatch = componentPatch;
	}

	public DataComponentPatch getComponentPatch() {
		return componentPatch;
	}

}

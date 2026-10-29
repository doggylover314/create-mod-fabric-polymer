package dev.createpolymer.fluids.tank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.DoubleSupplier;

import org.jetbrains.annotations.NotNull;

import dev.createpolymer.api.boiler.BoilerHeater;
import dev.createpolymer.foundation.fluid.FluidHelper;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.LangBuilder;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The state of a fluid tank used as a boiler: the engines and whistles attached to it, the heat below it and the water
 * it is supplied with. Steam engines and whistles are not ported yet; they register themselves in
 * {@link #ENGINE_ATTACHMENTS} and {@link #WHISTLE_ATTACHMENTS} so the boiler can count them.
 * The sounds, the dial animation of the client and the advancements of Create are left out.
 */
public class BoilerData {

	static final int SAMPLE_RATE = 5;

	private static final int waterSupplyPerLevel = 10;
	private static final float passiveEngineEfficiency = 1 / 8f;

	/**
	 * Tests whether the block next to a tank is a steam engine that is attached to it. The direction is the one
	 * from the tank to that block (Create: {@code SteamEngineBlock.getFacing(attachedState) == d}).
	 */
	public static final List<BiPredicate<BlockState, Direction>> ENGINE_ATTACHMENTS = new ArrayList<>();
	/**
	 * Tests whether the block next to a tank is a steam whistle that is attached to it. The direction is the one
	 * from the tank to that block (Create: {@code WhistleBlock.getAttachedDirection(attachedState).getOpposite() == d}).
	 */
	public static final List<BiPredicate<BlockState, Direction>> WHISTLE_ATTACHMENTS = new ArrayList<>();
	/** Stress capacity per RPM of one steam engine (Create: {@code BlockStressValues.getCapacity(STEAM_ENGINE)}). */
	public static DoubleSupplier engineCapacity = () -> 0;

	// pooled water supply
	int gatheredSupply;
	float[] supplyOverTime = new float[10];
	int ticksUntilNextSample;
	int currentIndex;

	// heat score
	public boolean needsHeatLevelUpdate;
	public boolean passiveHeat;
	public int activeHeat;

	public float waterSupply;
	public int attachedEngines;
	public int attachedWhistles;

	// display
	private int maxHeatForSize = 0;
	private int maxHeatForWater = 0;
	private int minValue = 0;
	private int maxValue = 0;

	public LerpedFloat gauge = LerpedFloat.linear();

	public void tick(FluidTankBlockEntity controller) {
		if (!isActive())
			return;
		Level level = controller.getLevel();
		if (level.isClientSide())
			return;
		if (needsHeatLevelUpdate && updateTemperature(controller))
			controller.notifyUpdate();
		gauge.tickChaser();
		ticksUntilNextSample--;
		if (ticksUntilNextSample > 0)
			return;
		int capacity = controller.tankInventory.getCapacity();
		if (capacity == 0)
			return;

		ticksUntilNextSample = SAMPLE_RATE;
		supplyOverTime[currentIndex] = gatheredSupply / (float) SAMPLE_RATE;
		waterSupply = Math.max(waterSupply, supplyOverTime[currentIndex]);
		currentIndex = (currentIndex + 1) % supplyOverTime.length;
		gatheredSupply = 0;

		if (currentIndex == 0) {
			waterSupply = 0;
			for (float i : supplyOverTime)
				waterSupply = Math.max(i, waterSupply);
		}

		if (controller instanceof CreativeFluidTankBlockEntity)
			waterSupply = waterSupplyPerLevel * 20;

		controller.notifyUpdate();
	}

	public int getTheoreticalHeatLevel() {
		return activeHeat;
	}

	public int getMaxHeatLevelForBoilerSize(int boilerSize) {
		return (int) Math.min(18, boilerSize / 4);
	}

	public int getMaxHeatLevelForWaterSupply() {
		return (int) Math.min(18, Mth.ceil(waterSupply) / waterSupplyPerLevel);
	}

	public boolean isPassive() {
		return passiveHeat && maxHeatForSize > 0 && maxHeatForWater > 0;
	}

	public boolean isPassive(int boilerSize) {
		calcMinMaxForSize(boilerSize);
		return isPassive();
	}

	public float getEngineEfficiency(int boilerSize) {
		if (isPassive(boilerSize))
			return passiveEngineEfficiency / attachedEngines;
		if (activeHeat == 0)
			return 0;
		int actualHeat = getActualHeat(boilerSize);
		return attachedEngines <= actualHeat ? 1 : (float) actualHeat / attachedEngines;
	}

	public int getActualHeat(int boilerSize) {
		int forBoilerSize = getMaxHeatLevelForBoilerSize(boilerSize);
		int forWaterSupply = getMaxHeatLevelForWaterSupply();
		int actualHeat = Math.min(activeHeat, Math.min(forWaterSupply, forBoilerSize));
		return actualHeat;
	}

	private static LangBuilder lang() {
		return Lang.builder("create");
	}

	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking, int boilerSize) {
		if (!isActive())
			return false;

		calcMinMaxForSize(boilerSize);

		lang().translate("boiler.status", getHeatLevelTextComponent().withStyle(ChatFormatting.GREEN))
			.forGoggles(tooltip);
		lang().add(getSizeComponent(true, false)).forGoggles(tooltip, 1);
		lang().add(getWaterComponent(true, false)).forGoggles(tooltip, 1);
		lang().add(getHeatComponent(true, false)).forGoggles(tooltip, 1);

		if (attachedEngines == 0)
			return true;

		int boilerLevel = Math.min(activeHeat, Math.min(maxHeatForWater, maxHeatForSize));
		double totalSU = getEngineEfficiency(boilerSize) * 16 * Math.max(boilerLevel, attachedEngines)
			* engineCapacity.getAsDouble();

		tooltip.add(CommonComponents.EMPTY);

		if (attachedEngines > 0 && maxHeatForSize > 0 && maxHeatForWater == 0 && (passiveHeat ? 1 : activeHeat) > 0) {
			lang().translate("boiler.water_input_rate")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip);
			lang().text(String.valueOf(Math.round(waterSupply * 10) / 10d))
				.style(ChatFormatting.BLUE)
				.add(lang().translate("generic.unit.millibuckets"))
				.add(lang().text(" / ")
					.style(ChatFormatting.GRAY))
				.add(lang().translate("boiler.per_tick", lang().text(String.valueOf(waterSupplyPerLevel))
						.add(lang().translate("generic.unit.millibuckets")))
					.style(ChatFormatting.DARK_GRAY))
				.forGoggles(tooltip, 1);
			return true;
		}

		lang().translate("tooltip.capacityProvided")
			.style(ChatFormatting.GRAY)
			.forGoggles(tooltip);

		lang().text(String.valueOf(Math.round(totalSU * 10) / 10d))
			.translate("generic.unit.stress")
			.style(ChatFormatting.AQUA)
			.space()
			.add((attachedEngines == 1 ? lang().translate("boiler.via_one_engine")
				: lang().translate("boiler.via_engines", attachedEngines)).style(ChatFormatting.DARK_GRAY))
			.forGoggles(tooltip, 1);

		return true;
	}

	public void calcMinMaxForSize(int boilerSize) {
		maxHeatForSize = getMaxHeatLevelForBoilerSize(boilerSize);
		maxHeatForWater = getMaxHeatLevelForWaterSupply();

		minValue = Math.min(passiveHeat ? 1 : activeHeat, Math.min(maxHeatForWater, maxHeatForSize));
		maxValue = Math.max(passiveHeat ? 1 : activeHeat, Math.max(maxHeatForWater, maxHeatForSize));
	}

	@NotNull
	public MutableComponent getHeatLevelTextComponent() {
		int boilerLevel = Math.min(activeHeat, Math.min(maxHeatForWater, maxHeatForSize));

		return isPassive() ? translateDirect("boiler.passive")
			: (boilerLevel == 0 ? translateDirect("boiler.idle")
			: boilerLevel == 18 ? translateDirect("boiler.max_lvl")
			: translateDirect("boiler.lvl", String.valueOf(boilerLevel)));
	}

	private static MutableComponent translateDirect(String key, Object... args) {
		return Component.translatable("create." + key, LangBuilder.resolveBuilders(args));
	}

	public MutableComponent getSizeComponent(boolean forGoggles, boolean useBlocksAsBars, ChatFormatting... styles) {
		return componentHelper("size", maxHeatForSize, forGoggles, useBlocksAsBars, styles);
	}

	public MutableComponent getWaterComponent(boolean forGoggles, boolean useBlocksAsBars, ChatFormatting... styles) {
		return componentHelper("water", maxHeatForWater, forGoggles, useBlocksAsBars, styles);
	}

	public MutableComponent getHeatComponent(boolean forGoggles, boolean useBlocksAsBars, ChatFormatting... styles) {
		return componentHelper("heat", passiveHeat ? 1 : activeHeat, forGoggles, useBlocksAsBars, styles);
	}

	private MutableComponent componentHelper(String label, int level, boolean forGoggles, boolean useBlocksAsBars,
											 ChatFormatting... styles) {
		MutableComponent base = useBlocksAsBars ? blockComponent(level) : barComponent(level);

		if (!forGoggles)
			return base;

		ChatFormatting style1 = styles.length >= 1 ? styles[0] : ChatFormatting.GRAY;
		ChatFormatting style2 = styles.length >= 2 ? styles[1] : ChatFormatting.DARK_GRAY;

		return translateDirect("boiler." + label)
			.withStyle(style1)
			.append(translateDirect("boiler." + label + "_dots")
				.withStyle(style2))
			.append(base);
	}

	private MutableComponent blockComponent(int level) {
		return Component.literal("" + "█".repeat(minValue) + "▒".repeat(Math.max(0, level - minValue)) + "░".repeat(Math.max(0, maxValue - level)));
	}

	private MutableComponent barComponent(int level) {
		return Component.empty()
			.append(bars(Math.max(0, minValue - 1), ChatFormatting.DARK_GREEN))
			.append(bars(minValue > 0 ? 1 : 0, ChatFormatting.GREEN))
			.append(bars(Math.max(0, level - minValue), ChatFormatting.DARK_GREEN))
			.append(bars(Math.max(0, maxValue - level), ChatFormatting.DARK_RED))
			.append(bars(Math.max(0, Math.min(18 - maxValue, ((maxValue / 5 + 1) * 5) - maxValue)),
				ChatFormatting.DARK_GRAY));

	}

	private MutableComponent bars(int level, ChatFormatting format) {
		return Component.literal("|".repeat(level))
			.withStyle(format);
	}

	public boolean evaluate(FluidTankBlockEntity controller) {
		BlockPos controllerPos = controller.getBlockPos();
		Level level = controller.getLevel();
		int prevEngines = attachedEngines;
		int prevWhistles = attachedWhistles;
		attachedEngines = 0;
		attachedWhistles = 0;

		for (int yOffset = 0; yOffset < controller.height; yOffset++) {
			for (int xOffset = 0; xOffset < controller.width; xOffset++) {
				for (int zOffset = 0; zOffset < controller.width; zOffset++) {

					BlockPos pos = controllerPos.offset(xOffset, yOffset, zOffset);
					BlockState blockState = level.getBlockState(pos);
					if (!FluidTankBlock.isTank(blockState))
						continue;
					for (Direction d : Iterate.directions) {
						BlockPos attachedPos = pos.relative(d);
						BlockState attachedState = level.getBlockState(attachedPos);
						for (BiPredicate<BlockState, Direction> engine : ENGINE_ATTACHMENTS)
							if (engine.test(attachedState, d))
								attachedEngines++;
						for (BiPredicate<BlockState, Direction> whistle : WHISTLE_ATTACHMENTS)
							if (whistle.test(attachedState, d))
								attachedWhistles++;
					}
				}
			}
		}

		needsHeatLevelUpdate = true;
		return prevEngines != attachedEngines || prevWhistles != attachedWhistles;
	}

	public boolean updateTemperature(FluidTankBlockEntity controller) {
		BlockPos controllerPos = controller.getBlockPos();
		Level level = controller.getLevel();
		needsHeatLevelUpdate = false;

		boolean prevPassive = passiveHeat;
		int prevActive = activeHeat;
		passiveHeat = false;
		activeHeat = 0;

		for (int xOffset = 0; xOffset < controller.width; xOffset++) {
			for (int zOffset = 0; zOffset < controller.width; zOffset++) {
				BlockPos pos = controllerPos.offset(xOffset, -1, zOffset);
				BlockState blockState = level.getBlockState(pos);
				float heat = BoilerHeater.findHeat(level, pos, blockState);
				if (heat == 0) {
					passiveHeat = true;
				} else if (heat > 0) {
					activeHeat += heat;
				}
			}
		}

		passiveHeat &= activeHeat == 0;

		return prevActive != activeHeat || prevPassive != passiveHeat;
	}

	public boolean isActive() {
		return attachedEngines > 0 || attachedWhistles > 0;
	}

	public void clear() {
		waterSupply = 0;
		activeHeat = 0;
		passiveHeat = false;
		attachedEngines = 0;
		Arrays.fill(supplyOverTime, 0);
	}

	public void write(ValueOutput nbt) {
		nbt.putFloat("Supply", waterSupply);
		nbt.putInt("ActiveHeat", activeHeat);
		nbt.putBoolean("PassiveHeat", passiveHeat);
		nbt.putInt("Engines", attachedEngines);
		nbt.putInt("Whistles", attachedWhistles);
		nbt.putBoolean("Update", needsHeatLevelUpdate);
	}

	public void read(ValueInput nbt, int boilerSize) {
		waterSupply = nbt.getFloatOr("Supply", 0);
		activeHeat = nbt.getIntOr("ActiveHeat", 0);
		passiveHeat = nbt.getBooleanOr("PassiveHeat", false);
		attachedEngines = nbt.getIntOr("Engines", 0);
		attachedWhistles = nbt.getIntOr("Whistles", 0);
		needsHeatLevelUpdate = nbt.getBooleanOr("Update", false);
		Arrays.fill(supplyOverTime, (int) waterSupply);

		int forBoilerSize = getMaxHeatLevelForBoilerSize(boilerSize);
		int forWaterSupply = getMaxHeatLevelForWaterSupply();
		int actualHeat = Math.min(activeHeat, Math.min(forWaterSupply, forBoilerSize));
		float target = isPassive(boilerSize) ? 1 / 8f : forBoilerSize == 0 ? 0 : actualHeat / (forBoilerSize * 1f);
		gauge.chase(target, 0.125f, Chaser.EXP);
	}

	public BoilerFluidHandler createHandler() {
		return new BoilerFluidHandler();
	}

	public class BoilerFluidHandler implements IFluidHandler {

		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return 10000;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return FluidHelper.isWater(stack.getFluid());
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (!isFluidValid(0, resource))
				return 0;
			int amount = resource.getAmount();
			if (action.execute())
				gatheredSupply += amount;
			return amount;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY;
		}

	}

}

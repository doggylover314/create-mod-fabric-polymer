package dev.createpolymer.trains.display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Ported from Create's FlapDisplayBlockEntity. The controller (the top left block of a board) holds the text of the
 * whole board; the flip animation runs in {@link #tick()} as Create's client does.
 */
public class FlapDisplayBlockEntity extends KineticBlockEntity {

	/** Create's DyeHelper front colour of each dye, indexed by dye id. */
	private static final int[] DYE_COLORS = { 0xEDEAE5, 0xEE9246, 0xF062B0, 0x69CED2, 0xE6D756, 0xA3DF55, 0xD5A8CB,
		0x5D666F, 0x95969B, 0x3EABB4, 0x9F54AE, 0x5391E1, 0xAC855C, 0x208A46, 0xB13937, 0x45403B };

	public List<FlapDisplayLayout> lines;
	public boolean isController;
	public boolean isRunning;
	public int xSize, ySize;
	public DyeColor[] colour;
	public boolean[] glowingLines;
	public boolean[] manualLines;

	public FlapDisplayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(10);
		isController = false;
		xSize = 1;
		ySize = 1;
		colour = new DyeColor[2];
		manualLines = new boolean[2];
		glowingLines = new boolean[2];
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		updateControllerStatus();
	}

	public void updateControllerStatus() {
		if (level.isClientSide())
			return;

		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof FlapDisplayBlock))
			return;

		Direction leftDirection = blockState.getValue(FlapDisplayBlock.HORIZONTAL_FACING)
			.getClockWise();
		boolean shouldBeController = !blockState.getValue(FlapDisplayBlock.UP)
			&& level.getBlockState(worldPosition.relative(leftDirection)) != blockState;

		int newXSize = 1;
		int newYSize = 1;

		if (shouldBeController) {
			for (int xOffset = 1; xOffset < 32; xOffset++) {
				if (level.getBlockState(worldPosition.relative(leftDirection.getOpposite(), xOffset)) != blockState)
					break;
				newXSize++;
			}
			for (int yOffset = 0; yOffset < 32; yOffset++) {
				if (!level.getBlockState(worldPosition.relative(Direction.DOWN, yOffset))
					.getOptionalValue(FlapDisplayBlock.DOWN)
					.orElse(false))
					break;
				newYSize++;
			}
		}

		if (isController == shouldBeController && newXSize == xSize && newYSize == ySize)
			return;

		isController = shouldBeController;
		xSize = newXSize;
		ySize = newYSize;
		colour = Arrays.copyOf(colour, ySize * 2);
		glowingLines = Arrays.copyOf(glowingLines, ySize * 2);
		manualLines = new boolean[ySize * 2];
		lines = null;
		setChanged();
		sendData();
	}

	@Override
	public void tick() {
		super.tick();
		isRunning = super.isSpeedRequirementFulfilled();
		if (!isRunning || !isController)
			return;
		int activeFlaps = 0;
		boolean instant = Math.abs(getSpeed()) > 128;
		for (FlapDisplayLayout line : getLines())
			for (FlapDisplaySection section : line.getSections())
				activeFlaps += section.tick(instant, level.getRandom());
		if (activeFlaps == 0)
			return;

		float volume = Mth.clamp(activeFlaps / 20f, 0.25f, 1.5f);
		float bgVolume = Mth.clamp(activeFlaps / 40f, 0.25f, 1f);
		BlockPos middle = worldPosition.relative(getDirection().getClockWise(), xSize / 2)
			.relative(Direction.DOWN, ySize / 2);
		level.playSound(null, middle, SoundEvents.CALCITE_HIT, SoundSource.BLOCKS, .35f * bgVolume, 1.95f);
		level.playSound(null, middle, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, volume * .25f, 0.56f);
	}

	@Override
	public boolean isSpeedRequirementFulfilled() {
		return isRunning;
	}

	public void applyTextManually(int lineIndex, Component componentText) {
		List<FlapDisplayLayout> lines = getLines();
		if (lineIndex >= lines.size())
			return;

		FlapDisplayLayout layout = lines.get(lineIndex);
		if (!layout.isLayout("Default"))
			layout.loadDefault(getMaxCharCount());
		List<FlapDisplaySection> sections = layout.getSections();

		FlapDisplaySection flapDisplaySection = sections.get(0);
		if (componentText == null) {
			manualLines[lineIndex] = false;
			flapDisplaySection.setText(CommonComponents.EMPTY);
			notifyUpdate();
			return;
		}

		manualLines[lineIndex] = true;
		flapDisplaySection.setText(componentText);
		notifyUpdate();
	}

	public void setColour(int lineIndex, DyeColor color) {
		colour[lineIndex] = color == DyeColor.WHITE ? null : color;
		notifyUpdate();
	}

	public void setGlowing(int lineIndex) {
		glowingLines[lineIndex] = true;
		notifyUpdate();
	}

	public List<FlapDisplayLayout> getLines() {
		if (lines == null)
			initDefaultSections();
		return lines;
	}

	public void initDefaultSections() {
		lines = new ArrayList<>();
		for (int i = 0; i < ySize * 2; i++)
			lines.add(new FlapDisplayLayout(getMaxCharCount()));
	}

	public int getMaxCharCount() {
		return getMaxCharCount(0);
	}

	public int getMaxCharCount(int gaps) {
		return (int) ((xSize * 16f - 2f - 4f * gaps) / 3.5f);
	}

	@Override
	protected void write(ValueOutput output) {
		super.write(output);

		output.putBoolean("Controller", isController);
		output.putInt("XSize", xSize);
		output.putInt("YSize", ySize);

		for (int j = 0; j < manualLines.length; j++)
			if (manualLines[j])
				output.putBoolean("CustomLine" + j, true);

		for (int j = 0; j < glowingLines.length; j++)
			if (glowingLines[j])
				output.putBoolean("GlowingLine" + j, true);

		for (int j = 0; j < colour.length; j++)
			if (colour[j] != null)
				output.putString("Dye" + j, colour[j].getSerializedName());

		if (!isController)
			return;
		List<FlapDisplayLayout> lines = getLines();
		for (int i = 0; i < lines.size(); i++)
			lines.get(i)
				.write(output.child("Display" + i));
	}

	@Override
	protected void read(ValueInput input) {
		super.read(input);
		int prevX = xSize;
		int prevY = ySize;

		isController = input.getBooleanOr("Controller", false);
		xSize = input.getIntOr("XSize", 1);
		ySize = input.getIntOr("YSize", 1);

		manualLines = new boolean[ySize * 2];
		for (int i = 0; i < ySize * 2; i++)
			manualLines[i] = input.getBooleanOr("CustomLine" + i, false);

		glowingLines = new boolean[ySize * 2];
		for (int i = 0; i < ySize * 2; i++)
			glowingLines[i] = input.getBooleanOr("GlowingLine" + i, false);

		colour = new DyeColor[ySize * 2];
		for (int i = 0; i < ySize * 2; i++)
			colour[i] = input.getString("Dye" + i)
				.map(name -> DyeColor.byName(name, null))
				.orElse(null);

		if (prevX != xSize || prevY != ySize)
			lines = null;

		if (!isController)
			return;
		List<FlapDisplayLayout> lines = getLines();
		for (int i = 0; i < lines.size(); i++)
			input.child("Display" + i)
				.ifPresent(lines.get(i)::read);
	}

	public int getLineIndexAt(double yCoord) {
		return (int) Mth.clamp(Math.floor(2 * (worldPosition.getY() - yCoord + 1)), 0, ySize * 2);
	}

	public @Nullable FlapDisplayBlockEntity getController() {
		if (isController)
			return this;

		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof FlapDisplayBlock))
			return null;

		MutableBlockPos pos = getBlockPos().mutable();
		Direction side = blockState.getValue(FlapDisplayBlock.HORIZONTAL_FACING)
			.getClockWise();

		for (int i = 0; i < 64; i++) {
			BlockState other = level.getBlockState(pos);

			if (other.getOptionalValue(FlapDisplayBlock.UP)
				.orElse(false)) {
				pos.move(Direction.UP);
				continue;
			}

			if (!level.getBlockState(pos.relative(side))
				.getOptionalValue(FlapDisplayBlock.UP)
				.orElse(true)) {
				pos.move(side);
				continue;
			}

			BlockEntity found = level.getBlockEntity(pos);
			if (found instanceof FlapDisplayBlockEntity flap && flap.isController)
				return flap;

			break;
		}

		return null;
	}

	public Direction getDirection() {
		return getBlockState().getOptionalValue(FlapDisplayBlock.HORIZONTAL_FACING)
			.orElse(Direction.SOUTH)
			.getOpposite();
	}

	public int getLineColor(int line) {
		DyeColor color = colour[line];
		return color == null ? 0xFF_D3C6BA : DYE_COLORS[color.getId()] | 0xFF_000000;
	}

	public boolean isLineGlowing(int line) {
		return glowingLines[line];
	}

}

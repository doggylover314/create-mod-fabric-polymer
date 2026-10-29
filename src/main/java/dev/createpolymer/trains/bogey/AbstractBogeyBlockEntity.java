package dev.createpolymer.trains.bogey;

import static dev.createpolymer.trains.entity.CarriageBogey.UPSIDE_DOWN_KEY;

import org.jetbrains.annotations.NotNull;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.util.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class AbstractBogeyBlockEntity extends SmartBlockEntity {
	public static final String BOGEY_STYLE_KEY = "BogeyStyle";
	public static final String BOGEY_DATA_KEY = "BogeyData";

	private CompoundTag bogeyData;

	public AbstractBogeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public abstract BogeyStyle getDefaultStyle();

	public CompoundTag getBogeyData() {
		if (this.bogeyData == null || !this.bogeyData.contains(BOGEY_STYLE_KEY))
			this.bogeyData = this.createBogeyData();
		return this.bogeyData;
	}

	public void setBogeyData(@NotNull CompoundTag newData) {
		if (!newData.contains(BOGEY_STYLE_KEY)) {
			Identifier style = getDefaultStyle().id;
			NBTHelper.writeResourceLocation(newData, BOGEY_STYLE_KEY, style);
		}
		this.bogeyData = newData;
		notifyUpdate();
	}

	public void setBogeyStyle(@NotNull BogeyStyle style) {
		Identifier location = style.id;
		CompoundTag data = this.getBogeyData();
		NBTHelper.writeResourceLocation(data, BOGEY_STYLE_KEY, location);
		notifyUpdate();
	}

	@NotNull
	public BogeyStyle getStyle() {
		CompoundTag data = this.getBogeyData();
		Identifier currentStyle = NBTHelper.readResourceLocation(data, BOGEY_STYLE_KEY);
		BogeyStyle style = AllBogeyStyles.BOGEY_STYLES.get(currentStyle);
		if (style == null) {
			setBogeyStyle(getDefaultStyle());
			return getStyle();
		}
		return style;
	}

	@Override
	protected void write(ValueOutput output) {
		CompoundTag data = this.getBogeyData();
		if (data != null)
			output.store(BOGEY_DATA_KEY, CompoundTag.CODEC, data); // Now contains style
		super.write(output);
	}

	@Override
	protected void read(ValueInput input) {
		this.bogeyData = input.read(BOGEY_DATA_KEY, CompoundTag.CODEC)
			.orElseGet(this::createBogeyData);
		super.read(input);
	}

	private CompoundTag createBogeyData() {
		CompoundTag nbt = new CompoundTag();
		NBTHelper.writeResourceLocation(nbt, BOGEY_STYLE_KEY, getDefaultStyle().id);
		boolean upsideDown = false;
		if (getBlockState().getBlock() instanceof AbstractBogeyBlock<?> bogeyBlock)
			upsideDown = bogeyBlock.isUpsideDown(getBlockState());
		nbt.putBoolean(UPSIDE_DOWN_KEY, upsideDown);
		return nbt;
	}

}

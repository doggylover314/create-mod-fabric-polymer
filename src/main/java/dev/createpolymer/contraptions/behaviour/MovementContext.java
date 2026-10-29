package dev.createpolymer.contraptions.behaviour;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.Nullable;

import com.google.common.base.Suppliers;

import dev.createpolymer.api.contraption.storage.fluid.MountedFluidStorage;
import dev.createpolymer.api.contraption.storage.item.MountedItemStorage;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

public class MovementContext {

	public Vec3 position;
	public Vec3 motion;
	public Vec3 relativeMotion;
	public UnaryOperator<Vec3> rotation;

	public Level world;
	public BlockState state;
	public BlockPos localPos;
	public CompoundTag blockEntityData;

	public boolean stall;
	public boolean disabled;
	public boolean firstMovement;
	public CompoundTag data;
	public Contraption contraption;
	public Object temporaryData;

	private FilterItemStack filter;

	private final Supplier<MountedItemStorage> itemStorage;
	private final Supplier<MountedFluidStorage> fluidStorage;

	public MovementContext(Level world, StructureBlockInfo info, Contraption contraption) {
		this.world = world;
		this.state = info.state();
		this.blockEntityData = info.nbt();
		this.contraption = contraption;
		localPos = info.pos();

		disabled = false;
		firstMovement = true;
		motion = Vec3.ZERO;
		relativeMotion = Vec3.ZERO;
		rotation = v -> v;
		position = null;
		data = new CompoundTag();
		stall = false;
		filter = null;
		this.itemStorage = Suppliers.memoize(() -> contraption.getStorage().getAllItemStorages().get(this.localPos));
		this.fluidStorage = Suppliers.memoize(() -> contraption.getStorage().getFluids().storages.get(this.localPos));
	}

	public float getAnimationSpeed() {
		int modifier = 1000;
		double length = -motion.length();
		if (disabled)
			return 0;
		if (world.isClientSide() && contraption.stalled)
			return 700;
		if (Math.abs(length) < 1 / 512f)
			return 0;
		return (((int) (length * modifier + 100 * Math.signum(length))) / 100) * 100;
	}

	public static MovementContext readNBT(Level world, StructureBlockInfo info, CompoundTag nbt, Contraption contraption) {
		MovementContext context = new MovementContext(world, info, contraption);
		context.motion = VecHelper.readNBT(nbt.getListOrEmpty("Motion"));
		context.relativeMotion = VecHelper.readNBT(nbt.getListOrEmpty("RelativeMotion"));
		if (nbt.contains("Position"))
			context.position = VecHelper.readNBT(nbt.getListOrEmpty("Position"));
		context.stall = nbt.getBooleanOr("Stall", false);
		context.firstMovement = nbt.getBooleanOr("FirstMovement", false);
		context.data = nbt.getCompoundOrEmpty("Data");
		return context;
	}

	public CompoundTag writeToNBT(CompoundTag nbt) {
		nbt.put("Motion", VecHelper.writeNBT(motion));
		nbt.put("RelativeMotion", VecHelper.writeNBT(relativeMotion));
		if (position != null)
			nbt.put("Position", VecHelper.writeNBT(position));
		nbt.putBoolean("Stall", stall);
		nbt.putBoolean("FirstMovement", firstMovement);
		nbt.put("Data", data.copy());
		return nbt;
	}

	/** The filter stack of the filtering behaviour saved in the block entity data (its "Filter" entry). */
	public FilterItemStack getFilterFromBE() {
		if (filter != null)
			return filter;
		ItemStack stack = ItemStack.EMPTY;
		if (blockEntityData != null)
			stack = TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), blockEntityData)
				.read("Filter", ItemStack.OPTIONAL_CODEC)
				.orElse(ItemStack.EMPTY);
		return filter = FilterItemStack.of(stack);
	}

	@Nullable
	public MountedItemStorage getItemStorage() {
		return this.itemStorage.get();
	}

	@Nullable
	public MountedFluidStorage getFluidStorage() {
		return this.fluidStorage.get();
	}
}

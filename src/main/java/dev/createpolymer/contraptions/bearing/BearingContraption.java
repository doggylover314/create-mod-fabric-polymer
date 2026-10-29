package dev.createpolymer.contraptions.bearing;

import org.apache.commons.lang3.tuple.Pair;

import dev.createpolymer.api.contraption.ContraptionType;
import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.registry.feature.BearingRegistry;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public class BearingContraption extends Contraption {

	protected int sailBlocks;
	protected Direction facing;

	private boolean isWindmill;

	public BearingContraption() {}

	public BearingContraption(boolean isWindmill, Direction facing) {
		this.isWindmill = isWindmill;
		this.facing = facing;
	}

	@Override
	public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
		BlockPos offset = pos.relative(facing);
		if (!searchMovedStructure(world, offset, null))
			return false;
		startMoving(world);
		expandBoundsAroundAxis(facing.getAxis());
		if (isWindmill && sailBlocks < ContraptionRegistry.minimumWindmillSails)
			throw AssemblyException.notEnoughSails(sailBlocks);
		if (blocks.isEmpty())
			return false;
		return true;
	}

	@Override
	public ContraptionType getType() {
		return BearingRegistry.BEARING;
	}

	@Override
	protected boolean isAnchoringBlockAt(BlockPos pos) {
		return pos.equals(anchor.relative(facing.getOpposite()));
	}

	@Override
	public void addBlock(Level level, BlockPos pos, Pair<StructureBlockInfo, BlockEntity> capture) {
		BlockPos localPos = pos.subtract(anchor);
		if (!getBlocks().containsKey(localPos) && isSail(getSailBlock(capture)))
			sailBlocks++;
		super.addBlock(level, pos, capture);
	}

	/** Create's create:windmill_sails block tag: the sails, the sail frame and wool. */
	public static boolean isSail(BlockState state) {
		return state.getBlock() instanceof SailBlock || state.is(BlockTags.WOOL);
	}

	private BlockState getSailBlock(Pair<StructureBlockInfo, BlockEntity> capture) {
		return capture.getKey().state();
	}

	@Override
	public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
		CompoundTag tag = super.writeNBT(registries, spawnPacket);
		tag.putInt("Sails", sailBlocks);
		tag.putInt("Facing", facing.get3DDataValue());
		return tag;
	}

	@Override
	public void readNBT(Level world, CompoundTag tag, boolean spawnData) {
		sailBlocks = tag.getIntOr("Sails", 0);
		facing = Direction.from3DDataValue(tag.getIntOr("Facing", 0));
		super.readNBT(world, tag, spawnData);
	}

	public int getSailBlocks() {
		return sailBlocks;
	}

	public Direction getFacing() {
		return facing;
	}

	@Override
	public Direction getBearingFacing() {
		return facing;
	}

	@Override
	public boolean canBeStabilized(Direction facing, BlockPos localPos) {
		if (facing.getOpposite() == this.facing && BlockPos.ZERO.equals(localPos))
			return false;
		return facing.getAxis() == this.facing.getAxis();
	}

}

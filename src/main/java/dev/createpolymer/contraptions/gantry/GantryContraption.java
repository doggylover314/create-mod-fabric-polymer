package dev.createpolymer.contraptions.gantry;

import dev.createpolymer.api.contraption.ContraptionType;
import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.TranslatingContraption;
import dev.createpolymer.registry.feature.PulleyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public class GantryContraption extends TranslatingContraption {

	protected Direction facing;

	public GantryContraption() {}

	public GantryContraption(Direction facing) {
		this.facing = facing;
	}

	@Override
	public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
		if (!searchMovedStructure(world, pos, null))
			return false;
		startMoving(world);
		return true;
	}

	@Override
	public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
		CompoundTag tag = super.writeNBT(registries, spawnPacket);
		tag.putInt("Facing", facing.get3DDataValue());
		return tag;
	}

	@Override
	public void readNBT(Level world, CompoundTag tag, boolean spawnData) {
		facing = Direction.from3DDataValue(tag.getIntOr("Facing", 0));
		super.readNBT(world, tag, spawnData);
	}

	@Override
	protected boolean isAnchoringBlockAt(BlockPos pos) {
		return super.isAnchoringBlockAt(pos.relative(facing));
	}

	@Override
	public ContraptionType getType() {
		return PulleyRegistry.GANTRY;
	}

	public Direction getFacing() {
		return facing;
	}

	@Override
	protected boolean shouldUpdateAfterMovement(StructureBlockInfo info) {
		return super.shouldUpdateAfterMovement(info) && !(info.state().getBlock() instanceof GantryCarriageBlock);
	}

}

package dev.createpolymer.schematics;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Filters the block entity data of a placed schematic. Create's version also drops entities that only operators may
 * place; 26.3 has no entity hook in structure processors, so {@link SchematicLevel#addFreshEntity} does that.
 */
public class SchematicProcessor implements StructureProcessor {
	public static final SchematicProcessor INSTANCE = new SchematicProcessor();
	public static final MapCodec<SchematicProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

	private SchematicProcessor() {
	}

	@Nullable
	@Override
	public StructureTemplate.StructureBlockInfo processBlock(LevelReader world, BlockPos pos, BlockPos anotherPos,
		BlockPos templateRelativePos, StructureTemplate.StructureBlockInfo info, StructurePlaceSettings settings) {
		if (info.nbt() != null && info.state()
			.hasBlockEntity()) {
			BlockEntity be = ((EntityBlock) info.state()
				.getBlock()).newBlockEntity(info.pos(), info.state());
			if (be != null) {
				CompoundTag nbt = NBTProcessors.process(info.state(), be, info.nbt(), false, world.registryAccess());
				if (nbt != info.nbt())
					return new StructureTemplate.StructureBlockInfo(info.pos(), info.state(), nbt);
			}
		}
		return info;
	}

	@Override
	public MapCodec<? extends StructureProcessor> codec() {
		return CODEC;
	}
}

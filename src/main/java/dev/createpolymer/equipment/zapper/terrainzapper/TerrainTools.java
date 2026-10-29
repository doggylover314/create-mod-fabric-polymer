package dev.createpolymer.equipment.zapper.terrainzapper;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.createpolymer.equipment.zapper.ZapperItem;
import dev.createpolymer.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum TerrainTools implements StringRepresentable {
	Fill(Items.BUCKET),
	Place(Items.BRICK),
	Replace(Items.PISTON),
	Clear(Items.BARRIER),
	Overlay(Items.MOSS_CARPET),
	Flatten(Items.SMOOTH_STONE_SLAB);

	public static final Codec<TerrainTools> CODEC = StringRepresentable.fromValues(TerrainTools::values);
	public String translationKey;
	/** Create's AllIcons are textures of a client screen; the GUI shows this item instead. */
	public Item icon;

	TerrainTools(Item icon) {
		this.translationKey = Lang.asId(name());
		this.icon = icon;
	}

	@Override
	public @NotNull String getSerializedName() {
		return Lang.asId(name());
	}

	public boolean requiresSelectedBlock() {
		return this != Clear && this != Flatten;
	}

	public void run(Level world, List<BlockPos> targetPositions, Direction facing, @Nullable BlockState paintedState, @Nullable CompoundTag data, Player player) {
		switch (this) {
		case Clear:
			targetPositions.forEach(p -> world.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState()));
			break;
		case Fill:
			targetPositions.forEach(p -> {
				BlockState toReplace = world.getBlockState(p);
				if (!isReplaceable(toReplace))
					return;
				world.setBlockAndUpdate(p, paintedState);
				ZapperItem.setBlockEntityData(world, p, paintedState, data, player);
			});
			break;
		case Flatten:
			FlattenTool.apply(world, targetPositions, facing);
			break;
		case Overlay:
			targetPositions.forEach(p -> {
				BlockState toOverlay = world.getBlockState(p);
				if (isReplaceable(toOverlay))
					return;
				if (toOverlay == paintedState)
					return;

				p = p.above();

				BlockState toReplace = world.getBlockState(p);
				if (!isReplaceable(toReplace))
					return;
				world.setBlockAndUpdate(p, paintedState);
				ZapperItem.setBlockEntityData(world, p, paintedState, data, player);
			});
			break;
		case Place:
			targetPositions.forEach(p -> {
				world.setBlockAndUpdate(p, paintedState);
				ZapperItem.setBlockEntityData(world, p, paintedState, data, player);
			});
			break;
		case Replace:
			targetPositions.forEach(p -> {
				BlockState toReplace = world.getBlockState(p);
				if (isReplaceable(toReplace))
					return;
				world.setBlockAndUpdate(p, paintedState);
				ZapperItem.setBlockEntityData(world, p, paintedState, data, player);
			});
			break;
		}
	}

	public static boolean isReplaceable(BlockState toReplace) {
		return toReplace.canBeReplaced();
	}
}

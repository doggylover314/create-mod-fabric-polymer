package dev.createpolymer.equipment.zapper;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;

import com.google.common.base.Predicates;
import com.mojang.serialization.Codec;

import dev.createpolymer.registry.feature.WandsRegistry;
import dev.createpolymer.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum PlacementPatterns implements StringRepresentable {
	Solid(Items.CONCRETE.pick(DyeColor.WHITE), 1),
	Checkered(Items.GLAZED_TERRACOTTA.pick(DyeColor.WHITE), 1),
	InverseCheckered(Items.GLAZED_TERRACOTTA.pick(DyeColor.BLACK), 1),
	Chance25(Items.RABBIT_FOOT, 1),
	Chance50(Items.RABBIT_FOOT, 2),
	Chance75(Items.RABBIT_FOOT, 3);

	public static final Codec<PlacementPatterns> CODEC = StringRepresentable.fromValues(PlacementPatterns::values);

	public final String translationKey;
	/** Create's AllIcons are textures of a client screen; the GUI shows this item instead. */
	public final Item icon;
	public final int iconCount;

	private PlacementPatterns(Item icon, int iconCount) {
		this.translationKey = Lang.asId(name());
		this.icon = icon;
		this.iconCount = iconCount;
	}

	public static void applyPattern(List<BlockPos> blocksIn, ItemStack stack, RandomSource random) {
		PlacementPatterns pattern = stack.getOrDefault(WandsRegistry.PLACEMENT_PATTERN, Solid);
		Predicate<BlockPos> filter = Predicates.alwaysFalse();

		switch (pattern) {
		case Chance25:
			filter = pos -> random.nextBoolean() || random.nextBoolean();
			break;
		case Chance50:
			filter = pos -> random.nextBoolean();
			break;
		case Chance75:
			filter = pos -> random.nextBoolean() && random.nextBoolean();
			break;
		case Checkered:
			filter = pos -> (pos.getX() + pos.getY() + pos.getZ()) % 2 == 0;
			break;
		case InverseCheckered:
			filter = pos -> (pos.getX() + pos.getY() + pos.getZ()) % 2 != 0;
			break;
		case Solid:
		default:
			break;
		}

		blocksIn.removeIf(filter);
	}

	@Override
	public @NotNull String getSerializedName() {
		return Lang.asId(name());
	}
}

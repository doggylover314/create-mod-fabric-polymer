package dev.createpolymer.redstone.displayLink.source;

import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;

public class EnchantPowerDisplaySource extends NumericSingleLineDisplaySource {

	protected static final RandomSource random = RandomSource.create();
	/** Created when first needed: items cannot be made while this class loads with the registries. */
	protected static ItemStack stack;

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		if (!(context.getSourceBlockEntity() instanceof EnchantingTableBlockEntity))
			return ZERO.copy();

		BlockPos pos = context.getSourcePos();
		Level level = context.level();
		float enchantPower = 0;

		for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			if (!EnchantingTableBlock.isValidBookShelf(level, pos, offset))
				continue;

			// 26.3 has no power bonus per block: every enchantment power provider counts once
			enchantPower += 1;
		}

		if (stack == null)
			stack = new ItemStack(Items.DIAMOND_PICKAXE);
		int cost = EnchantmentHelper.getEnchantmentCost(random, 2, (int) enchantPower, stack);

		return Component.literal(String.valueOf(cost));
	}

	@Override
	protected String getTranslationKey() {
		return "max_enchant_level";
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}
}

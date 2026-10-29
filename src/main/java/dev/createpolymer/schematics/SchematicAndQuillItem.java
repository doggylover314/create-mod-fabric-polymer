package dev.createpolymer.schematics;

import java.util.Optional;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The item that saves a region to a schematic file. The selection happens in {@link SchematicAndQuillHandler}, which
 * Create runs on the client; here it is driven by the item's use.
 */
public class SchematicAndQuillItem extends SimplePolymerItem {

	public SchematicAndQuillItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null)
			return InteractionResult.PASS;
		return SchematicAndQuillHandler.onUse(player, context.getHand(), true);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return SchematicAndQuillHandler.onUse(player, hand, false);
	}

	public static void replaceStructureVoidWithAir(CompoundTag nbt) {
		String air = BuiltInRegistries.BLOCK.getKey(Blocks.AIR)
			.toString();
		String structureVoid = BuiltInRegistries.BLOCK.getKey(Blocks.STRUCTURE_VOID)
			.toString();

		ListTag palette = nbt.getListOrEmpty("palette");
		for (int i = 0; i < palette.size(); i++) {
			Optional<CompoundTag> entry = palette.getCompound(i);
			if (entry.isPresent() && entry.get()
				.getStringOr("Name", "")
				.equals(structureVoid))
				entry.get()
					.putString("Name", air);
		}
	}

	public static void clampGlueBoxes(Level level, AABB aabb, CompoundTag nbt) {
		ListTag listtag = nbt.getListOrEmpty("entities")
			.copy();

		Identifier glueId = BuiltInRegistries.ENTITY_TYPE.getKey(ContraptionRegistry.SUPER_GLUE);
		for (int i = listtag.size() - 1; i >= 0; i--) {
			Optional<CompoundTag> entry = listtag.getCompound(i);
			if (entry.isEmpty())
				continue;
			CompoundTag entityNbt = entry.get()
				.getCompoundOrEmpty("nbt");
			if (entry.get()
				.contains("nbt") && glueId.equals(Identifier.tryParse(entityNbt.getStringOr("id", ""))))
				listtag.remove(i);
		}

		for (SuperGlueEntity entity : SuperGlueEntity.collectCropped(level, aabb)) {
			Vec3 vec3 = new Vec3(entity.getX() - aabb.minX, entity.getY() - aabb.minY, entity.getZ() - aabb.minZ);
			CompoundTag compoundtag;
			try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(),
				CreatePolymer.LOGGER)) {
				TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
				entity.save(output);
				compoundtag = output.buildResult();
			}
			BlockPos blockpos = BlockPos.containing(vec3);

			CompoundTag entityTag = new CompoundTag();
			entityTag.put("pos", newDoubleList(vec3.x, vec3.y, vec3.z));
			entityTag.put("blockPos", newIntegerList(blockpos.getX(), blockpos.getY(), blockpos.getZ()));
			entityTag.put("nbt", compoundtag.copy());
			listtag.add(entityTag);
		}

		nbt.put("entities", listtag);
	}

	private static ListTag newIntegerList(int... pValues) {
		ListTag listtag = new ListTag();
		for (int i : pValues)
			listtag.add(IntTag.valueOf(i));
		return listtag;
	}

	private static ListTag newDoubleList(double... pValues) {
		ListTag listtag = new ListTag();
		for (double d0 : pValues)
			listtag.add(DoubleTag.valueOf(d0));
		return listtag;
	}
}

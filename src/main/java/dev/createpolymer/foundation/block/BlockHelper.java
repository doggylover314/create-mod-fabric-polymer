package dev.createpolymer.foundation.block;



import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.schematic.nbt.PartialSafeNBT;
import dev.createpolymer.api.schematic.nbt.SafeNbtWriterRegistry;
import dev.createpolymer.api.schematic.state.SchematicStateFilter;
import dev.createpolymer.api.schematic.state.SchematicStateFilterRegistry;
import dev.createpolymer.foundation.blockEntity.IMergeableBE;
import dev.createpolymer.foundation.blockEntity.IMultiBlockEntityContainer;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.feature.BeltRegistry;
import dev.createpolymer.schematics.NBTProcessors;

/**
 * Create's BlockHelper. hasBlockSolidSide and the schematic placement helpers live here; destroyBlock and its
 * variants are in {@link dev.createpolymer.fluids.PipeHelper}.
 */
public final class BlockHelper {
	private BlockHelper() {}

	public static boolean hasBlockSolidSide(BlockState state, BlockGetter blockGetter, BlockPos pos, Direction dir) {
		return !state.is(BlockTags.LEAVES)
			&& Block.isFaceFull(state.getCollisionShape(blockGetter, pos), dir);
	}

	public static BlockState setZeroAge(BlockState blockState) {
		if (blockState.hasProperty(BlockStateProperties.AGE_1))
			return blockState.setValue(BlockStateProperties.AGE_1, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_2))
			return blockState.setValue(BlockStateProperties.AGE_2, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_3))
			return blockState.setValue(BlockStateProperties.AGE_3, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_5))
			return blockState.setValue(BlockStateProperties.AGE_5, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_7))
			return blockState.setValue(BlockStateProperties.AGE_7, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_15))
			return blockState.setValue(BlockStateProperties.AGE_15, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_25))
			return blockState.setValue(BlockStateProperties.AGE_25, 0);
		if (blockState.hasProperty(BlockStateProperties.LEVEL_HONEY))
			return blockState.setValue(BlockStateProperties.LEVEL_HONEY, 0);
		if (blockState.hasProperty(BlockStateProperties.HATCH))
			return blockState.setValue(BlockStateProperties.HATCH, 0);
		if (blockState.hasProperty(BlockStateProperties.STAGE))
			return blockState.setValue(BlockStateProperties.STAGE, 0);
		if (blockState.is(BlockTags.CAULDRONS))
			return Blocks.CAULDRON.defaultBlockState();
		if (blockState.hasProperty(BlockStateProperties.LEVEL_COMPOSTER))
			return blockState.setValue(BlockStateProperties.LEVEL_COMPOSTER, 0);
		if (blockState.hasProperty(BlockStateProperties.EXTENDED))
			return blockState.setValue(BlockStateProperties.EXTENDED, false);
		return blockState;
	}

	/** Create's create:safe_nbt block tag (data/create/tags/block/safe_nbt.json): blocks whose data is copied whole */
	public static final Set<String> SAFE_NBT = Set.of("creative_motor", "creative_fluid_tank", "mechanical_piston",
		"sticky_mechanical_piston", "windmill_bearing", "mechanical_bearing", "clockwork_bearing", "rope_pulley",
		"cart_assembler", "linear_chassis", "secondary_linear_chassis", "radial_chassis", "sequenced_gearshift",
		"rotation_speed_controller", "andesite_funnel", "andesite_belt_funnel", "brass_funnel", "brass_belt_funnel",
		"redstone_link", "analog_lever", "placard", "pulse_repeater", "pulse_extender", "pulse_timer", "clipboard");

	private static boolean isSafeNbtBlock(BlockState state) {
		Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return key.getNamespace()
			.equals(CreatePolymer.MOD_ID) && SAFE_NBT.contains(key.getPath()) || state.is(BlockTags.BANNERS)
			|| state.is(BlockTags.ALL_SIGNS);
	}

	@Nullable
	public static CompoundTag prepareBlockEntityData(Level level, BlockState blockState, BlockEntity blockEntity) {
		if (blockEntity == null)
			return null;
		CompoundTag data = null;
		SafeNbtWriterRegistry.SafeNbtWriter writer = SafeNbtWriterRegistry.REGISTRY.get(blockEntity.getType());
		if (isSafeNbtBlock(blockState)) {
			data = blockEntity.saveWithFullMetadata(level.registryAccess());
		} else if (writer != null) {
			try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(blockEntity.problemPath(),
				CreatePolymer.LOGGER)) {
				TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
				writer.writeSafe(blockEntity, output);
				data = output.buildResult();
			}
		} else if (blockEntity instanceof PartialSafeNBT safeNbtBE) {
			try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(blockEntity.problemPath(),
				CreatePolymer.LOGGER)) {
				TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
				safeNbtBE.writeSafe(output);
				data = output.buildResult();
			}
		}

		return NBTProcessors.process(blockState, blockEntity, data, true, level.registryAccess());
	}

	public static void placeSchematicBlock(Level world, BlockState state, BlockPos target, ItemStack stack,
		@Nullable CompoundTag data) {
		Block block = state.getBlock();
		BlockEntity existingBlockEntity = world.getBlockEntity(target);

		SchematicStateFilterRegistry.StateFilter filter = SchematicStateFilterRegistry.REGISTRY.get(state);
		if (filter != null) {
			state = filter.filterStates(existingBlockEntity, state);
		} else if (block instanceof SchematicStateFilter schematicStateFilter) {
			state = schematicStateFilter.filterStates(existingBlockEntity, state);
		}

		// Piston
		if (state.hasProperty(BlockStateProperties.EXTENDED))
			state = state.setValue(BlockStateProperties.EXTENDED, Boolean.FALSE);
		if (state.hasProperty(BlockStateProperties.WATERLOGGED))
			state = state.setValue(BlockStateProperties.WATERLOGGED, Boolean.FALSE);

		if (block == Blocks.COMPOSTER) {
			state = Blocks.COMPOSTER.defaultBlockState();
		} else if (state.is(BlockTags.CAULDRONS)) {
			state = Blocks.CAULDRON.defaultBlockState();
		}

		if (world.environmentAttributes()
			.getValue(EnvironmentAttributes.WATER_EVAPORATES, target) && state.getFluidState()
				.is(FluidTags.WATER)) {
			world.playSound(null, target, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
				2.6F + (world.getRandom().nextFloat() - world.getRandom().nextFloat()) * 0.8F);

			if (world instanceof ServerLevel serverLevel)
				serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX() + .5, target.getY() + .5,
					target.getZ() + .5, 8, .5, .5, .5, 0);
			Block.dropResources(state, world, target);
			return;
		}

		if (state.getBlock() instanceof BaseRailBlock) {
			placeRailWithoutUpdate(world, state, target);
		} else if (BeltRegistry.BELT.defaultBlockState()
			.is(state.getBlock())) {
			world.setBlock(target, state, Block.UPDATE_CLIENTS);
		} else {
			world.setBlock(target, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}

		if (data != null) {
			if (existingBlockEntity instanceof IMergeableBE mergeable) {
				CompoundTag withId = data.copy();
				withId.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(existingBlockEntity.getType())
					.toString());
				BlockEntity loaded = BlockEntity.loadStatic(target, state, withId, world.registryAccess());
				if (loaded != null) {
					if (existingBlockEntity.getType()
						.equals(loaded.getType())) {
						mergeable.accept(loaded);
						return;
					}
				}
			}
			BlockEntity blockEntity = world.getBlockEntity(target);
			if (blockEntity != null) {
				if (blockEntity instanceof KineticBlockEntity kbe)
					kbe.warnOfMovement();
				if (blockEntity instanceof IMultiBlockEntityContainer imbe)
					if (!imbe.isController())
						data.store("Controller", BlockPos.CODEC, imbe.getController());
				loadBlockEntityData(world, blockEntity, data);
			}
		}

		try {
			state.getBlock()
				.setPlacedBy(world, target, state, null, stack);
		} catch (Exception ignored) {
		}
	}

	/** Loads the data a schematic holds into a block entity */
	public static void loadBlockEntityData(Level world, BlockEntity blockEntity, CompoundTag data) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(blockEntity.problemPath(),
			CreatePolymer.LOGGER)) {
			blockEntity.loadWithComponents(TagValueInput.create(reporter, world.registryAccess(), data));
		}
	}

	private static void placeRailWithoutUpdate(Level world, BlockState state, BlockPos target) {
		// UPDATE_SKIP_ON_PLACE: the rail keeps the shape the schematic gives it, instead of connecting to its neighbours
		world.setBlock(target, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_MOVE_BY_PISTON
			| Block.UPDATE_SKIP_ON_PLACE);
		world.neighborChanged(target, world.getBlockState(target.below())
			.getBlock(), null);
	}
}

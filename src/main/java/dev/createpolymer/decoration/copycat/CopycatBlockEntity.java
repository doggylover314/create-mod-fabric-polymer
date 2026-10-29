package dev.createpolymer.decoration.copycat;

import java.util.List;
import java.util.Optional;

import dev.createpolymer.api.contraption.transformable.TransformableBlockEntity;
import dev.createpolymer.contraptions.StructureTransform;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.redstone.RoseQuartzLampBlock;
import dev.createpolymer.registry.feature.CopycatRegistry;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CopycatBlockEntity extends SmartBlockEntity implements TransformableBlockEntity, Clearable {

	private BlockState material = CopycatRegistry.COPYCAT_BASE.defaultBlockState();
	private ItemStack consumedItem = ItemStack.EMPTY;

	public CopycatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public BlockState getMaterial() {
		return material;
	}

	public boolean hasCustomMaterial() {
		return !material.is(CopycatRegistry.COPYCAT_BASE);
	}

	public void setMaterial(BlockState blockState) {
		BlockState wrapperState = CopycatBlock.withoutLight(getBlockState());

		if (!material.is(blockState.getBlock()))
			for (Direction side : Iterate.directions) {
				BlockPos neighbour = worldPosition.relative(side);
				BlockState neighbourState = level.getBlockState(neighbour);
				if (CopycatBlock.withoutLight(neighbourState) != wrapperState)
					continue;
				if (!(level.getBlockEntity(neighbour) instanceof CopycatBlockEntity cbe))
					continue;
				BlockState otherMaterial = cbe.getMaterial();
				if (!otherMaterial.is(blockState.getBlock()))
					continue;
				blockState = otherMaterial;
				break;
			}

		material = blockState;
		updateLight();
		notifyUpdate();
	}

	public boolean cycleMaterial() {
		if (material.hasProperty(TrapDoorBlock.HALF) && material.getOptionalValue(TrapDoorBlock.OPEN)
			.orElse(false))
			setMaterial(material.cycle(TrapDoorBlock.HALF));
		else if (material.hasProperty(BlockStateProperties.FACING))
			setMaterial(material.cycle(BlockStateProperties.FACING));
		else if (material.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
			setMaterial(material.setValue(BlockStateProperties.HORIZONTAL_FACING,
				material.getValue(BlockStateProperties.HORIZONTAL_FACING)
					.getClockWise()));
		else if (material.hasProperty(BlockStateProperties.AXIS))
			setMaterial(material.cycle(BlockStateProperties.AXIS));
		else if (material.hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
			setMaterial(material.cycle(BlockStateProperties.HORIZONTAL_AXIS));
		else if (material.hasProperty(BlockStateProperties.LIT))
			setMaterial(material.cycle(BlockStateProperties.LIT));
		else if (material.hasProperty(RoseQuartzLampBlock.POWERING))
			setMaterial(material.cycle(RoseQuartzLampBlock.POWERING));
		else
			return false;

		return true;
	}

	public ItemStack getConsumedItem() {
		return consumedItem;
	}

	public void setConsumedItem(ItemStack stack) {
		consumedItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
		setChanged();
	}

	/**
	 * Create keeps the light of the material in the level's AuxiliaryLightManager; vanilla reads a block's light from
	 * its block state, so the wrapper state carries it (see {@link CopycatBlock#LIGHT}).
	 */
	private void updateLight() {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		BlockState state = getBlockState();
		if (!state.hasProperty(CopycatBlock.LIGHT))
			return;
		int light = material.getLightEmission();
		if (state.getValue(CopycatBlock.LIGHT) != light)
			serverLevel.setBlock(worldPosition, state.setValue(CopycatBlock.LIGHT, light), Block.UPDATE_ALL);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		// the light a block was saved with can differ from its material (reset invalid material, changed light)
		if (level instanceof ServerLevel serverLevel && getBlockState().hasProperty(CopycatBlock.LIGHT)
			&& getBlockState().getValue(CopycatBlock.LIGHT) != material.getLightEmission())
			serverLevel.getServer()
				.execute(() -> {
					if (!isRemoved() && serverLevel.isLoaded(worldPosition) && serverLevel.getBlockEntity(worldPosition) == this)
						updateLight();
				});
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	@Override
	public void transform(BlockEntity be, StructureTransform transform) {
		material = transform.apply(material);
		notifyUpdate();
	}

	@Override
	protected void read(ValueInput view) {
		super.read(view);

		consumedItem = view.read("Item", ItemStack.OPTIONAL_CODEC)
			.orElse(ItemStack.EMPTY);

		Optional<BlockState> stored = view.read("Material", BlockState.CODEC);
		if (stored.isEmpty()) {
			consumedItem = ItemStack.EMPTY;
			return;
		}

		material = stored.get();

		// Validate Material
		BlockState blockState = getBlockState();
		if (blockState == null)
			return;
		if (!(blockState.getBlock() instanceof CopycatBlock cb))
			return;
		BlockState acceptedBlockState = cb.getAcceptedBlockState(level, worldPosition, consumedItem, null);
		if (acceptedBlockState != null && material.is(acceptedBlockState.getBlock()))
			return;
		consumedItem = ItemStack.EMPTY;
		material = CopycatRegistry.COPYCAT_BASE.defaultBlockState();
	}

	@Override
	protected void write(ValueOutput view) {
		super.write(view);
		view.store("Item", ItemStack.OPTIONAL_CODEC, consumedItem);
		view.store("Material", BlockState.CODEC, material);
	}

	/** The material is gone with the block: its item goes back to the world (Create does this in the block's onRemove). */
	@Override
	public void destroy() {
		super.destroy();
		if (level != null && !consumedItem.isEmpty())
			Block.popResource(level, worldPosition, consumedItem);
	}

	@Override
	public void clearContent() {
		material = CopycatRegistry.COPYCAT_BASE.defaultBlockState();
		consumedItem = ItemStack.EMPTY;
	}
}

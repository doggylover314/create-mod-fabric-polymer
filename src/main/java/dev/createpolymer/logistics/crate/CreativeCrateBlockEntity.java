package dev.createpolymer.logistics.crate;

import java.util.List;

import org.joml.Matrix4f;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.registry.feature.MiscRegistry;
import dev.createpolymer.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Ported from Create's CreativeCrateBlockEntity (MIT, The Create Team). */
public class CreativeCrateBlockEntity extends CrateBlockEntity implements Clearable {
	FilteringBehaviour filtering;
	BottomlessItemHandler inv;

	public CreativeCrateBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		inv = new BottomlessItemHandler(filtering::getFilter);
	}

	/** Create's registerCapabilities: the bottomless handler is what hoppers and other mods see. */
	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(MiscRegistry.CREATIVE_CRATE_BLOCK_ENTITY, (be, side) -> be.inv);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(filtering = createFilter());
		filtering.setLabel(Lang.builder(CreatePolymer.MOD_ID).translate("logistics.creative_crate.supply").component());
	}

	@Override
	public void clearContent() {
		filtering.setFilter(ItemStack.EMPTY);
	}

	public FilteringBehaviour createFilter() {
		return new FilteringBehaviour(this, new ValueBoxTransform() {
			@Override
			public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
				ms.rotateX((float) Math.toRadians(90));
			}

			@Override
			public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
				return new Vec3(0.5, 13.5 / 16d, 0.5);
			}
		});
	}
}

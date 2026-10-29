package dev.createpolymer.logistics.crate;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import dev.createpolymer.registry.feature.MiscRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import dev.createpolymer.visual.ModelItems;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's CreativeCrateBlock (MIT, The Create Team). */
public class CreativeCrateBlock extends CrateBlock implements IBE<CreativeCrateBlockEntity>, PolymerModelBlock {

	public CreativeCrateBlock(Properties properties) {
		super(properties);
	}

	@Override
	public Class<CreativeCrateBlockEntity> getBlockEntityClass() {
		return CreativeCrateBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CreativeCrateBlockEntity> getBlockEntityType() {
		return MiscRegistry.CREATIVE_CRATE_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** The creative crate's blockstate has one model for every facing; its filter is shown on top. */
	public static class Model extends BaseModel {
		private final Part crate;
		private final FilterDisplay filter = new FilterDisplay(this);

		public Model(BlockState state) {
			super(state);
			crate = addPart(ModelItems.block("crate/creative/single"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(crate, "creative_crate", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof SmartBlockEntity sbe)
				filter.update(sbe);
		}
	}
}

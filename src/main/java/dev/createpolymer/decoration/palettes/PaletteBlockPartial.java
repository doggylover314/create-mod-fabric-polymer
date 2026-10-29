package dev.createpolymer.decoration.palettes;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.DecorationStates;
import dev.createpolymer.visual.BlockstateModels;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Stairs, slabs and walls of a palette block. Polymer has no free state for a wall, and only a few for stairs and
 * slabs, so these show a vanilla block of the same shape (see {@link AllPaletteStoneTypes.Look}). A few cut stairs and
 * slabs ({@link AllPaletteStoneTypes#hasTexturedStairsAndSlabs}) use those spare states and show Create's models.
 * The double slab is a full cube and always has Create's texture.
 */
public abstract class PaletteBlockPartial<B extends Block> {

	public static final PaletteBlockPartial<StairBlock> STAIR = new Stairs();
	public static final PaletteBlockPartial<SlabBlock> SLAB = new Slab(false);
	public static final PaletteBlockPartial<SlabBlock> UNIQUE_SLAB = new Slab(true);
	public static final PaletteBlockPartial<WallBlock> WALL = new Wall();

	public static final PaletteBlockPartial<?>[] ALL_PARTIALS = {STAIR, SLAB, WALL};
	public static final PaletteBlockPartial<?>[] FOR_POLISHED = {STAIR, UNIQUE_SLAB, WALL};

	private final String name;

	private PaletteBlockPartial(String name) {
		this.name = name;
	}

	/** Id of the partial of the pattern block called {@code patternBlockName}: "cut_limestone_bricks" gives "cut_limestone_brick_stairs". */
	public String blockName(String patternBlockName) {
		String patternName = patternBlockName.endsWith("s") ? patternBlockName.substring(0, patternBlockName.length() - 1)
			: patternBlockName;
		return patternName + "_" + this.name;
	}

	/** Which vanilla block the partial looks like. */
	protected abstract Block look(AllPaletteStoneTypes.Look look);

	/**
	 * @param textured whether to spend Polymer's few stair/slab states on Create's own model
	 */
	public abstract B createBlock(Properties properties, Block patternBlock, String patternBlockName, String blockName,
		AllPaletteStoneTypes.Look look, boolean textured);

	public Properties properties(Block patternBlock) {
		return Properties.ofFullCopy(patternBlock);
	}

	public boolean canRecycle() {
		return true;
	}

	public String getName() {
		return name;
	}

	public static class PaletteStairBlock extends StairBlock implements PolymerTexturedBlock {
		private final Block look;
		private final Map<BlockState, BlockState> clientStates = new IdentityHashMap<>();

		public PaletteStairBlock(BlockState baseState, Properties properties, Block look, @Nullable String blockstateFile) {
			super(baseState, properties);
			this.look = look;
			if (blockstateFile == null)
				return;
			for (BlockState state : getStateDefinition().getPossibleStates()) {
				BlockModelType type = BlockModelType.getStairs(state.getValue(FACING), state.getValue(HALF),
					state.getValue(SHAPE), state.getValue(WATERLOGGED));
				BlockstateModels.Variant variant = BlockstateModels.get(blockstateFile, state);
				if (variant == null)
					continue;
				BlockState client = DecorationStates.request(type,
					PolymerBlockModel.of(variant.model(), variant.x(), variant.y(), true));
				if (client != null)
					clientStates.put(state, client);
			}
		}

		public boolean isTextured() {
			return !clientStates.isEmpty();
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			BlockState client = clientStates.get(state);
			return client != null ? client : look.withPropertiesOf(state);
		}

		@Override
		public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
			return look.defaultBlockState();
		}
	}

	public static class PaletteSlabBlock extends SlabBlock implements PolymerTexturedBlock {
		private final Block look;
		private final Block cubeLook;
		private final BlockState doubleState;
		private final Map<BlockState, BlockState> clientStates = new IdentityHashMap<>();

		public PaletteSlabBlock(Properties properties, Block look, Block cubeLook, String doubleModel,
			@Nullable String blockstateFile) {
			super(properties);
			this.look = look;
			this.cubeLook = cubeLook;
			this.doubleState = DecorationStates.fullBlock(DecorationStates.model(doubleModel));
			if (blockstateFile == null)
				return;
			for (BlockState state : getStateDefinition().getPossibleStates()) {
				SlabType slabType = state.getValue(TYPE);
				if (slabType == SlabType.DOUBLE)
					continue;
				BlockModelType type = BlockModelType.getSlab(slabType == SlabType.BOTTOM, state.getValue(WATERLOGGED));
				BlockstateModels.Variant variant = BlockstateModels.get(blockstateFile, state);
				if (variant == null)
					continue;
				BlockState client = DecorationStates.request(type,
					PolymerBlockModel.of(variant.model(), variant.x(), variant.y()));
				if (client != null)
					clientStates.put(state, client);
			}
		}

		public boolean isTextured() {
			return !clientStates.isEmpty();
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			if (state.getValue(TYPE) == SlabType.DOUBLE)
				return doubleState != null ? doubleState : cubeLook.defaultBlockState();
			BlockState client = clientStates.get(state);
			return client != null ? client : look.withPropertiesOf(state);
		}

		@Override
		public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
			return look.defaultBlockState();
		}
	}

	public static class PaletteWallBlock extends WallBlock implements PolymerBlock {
		private final Block look;

		public PaletteWallBlock(Properties properties, Block look) {
			super(properties);
			this.look = look;
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			return look.withPropertiesOf(state);
		}

		@Override
		public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
			return look.defaultBlockState();
		}
	}

	private static class Stairs extends PaletteBlockPartial<StairBlock> {

		public Stairs() {
			super("stairs");
		}

		@Override
		protected Block look(AllPaletteStoneTypes.Look look) {
			return look.stairs();
		}

		@Override
		public StairBlock createBlock(Properties properties, Block patternBlock, String patternBlockName,
			String blockName, AllPaletteStoneTypes.Look look, boolean textured) {
			return new PaletteStairBlock(patternBlock.defaultBlockState(), properties, look(look),
				textured ? blockName : null);
		}

	}

	private static class Slab extends PaletteBlockPartial<SlabBlock> {

		private final boolean customSide;

		public Slab(boolean customSide) {
			super("slab");
			this.customSide = customSide;
		}

		@Override
		protected Block look(AllPaletteStoneTypes.Look look) {
			return look.slab();
		}

		@Override
		public boolean canRecycle() {
			return false;
		}

		@Override
		public SlabBlock createBlock(Properties properties, Block patternBlock, String patternBlockName,
			String blockName, AllPaletteStoneTypes.Look look, boolean textured) {
			// The double slab of a polished block has its own column model, the others are the pattern block
			String doubleModel = customSide ? blockName + "_double" : patternBlockName;
			return new PaletteSlabBlock(properties, look(look), look.cube(), doubleModel,
				textured ? blockName : null);
		}

	}

	private static class Wall extends PaletteBlockPartial<WallBlock> {

		public Wall() {
			super("wall");
		}

		@Override
		protected Block look(AllPaletteStoneTypes.Look look) {
			return look.wall();
		}

		@Override
		public Properties properties(Block patternBlock) {
			return super.properties(patternBlock).forceSolidOn();
		}

		@Override
		public WallBlock createBlock(Properties properties, Block patternBlock, String patternBlockName,
			String blockName, AllPaletteStoneTypes.Look look, boolean textured) {
			return new PaletteWallBlock(properties, look(look));
		}

	}

}

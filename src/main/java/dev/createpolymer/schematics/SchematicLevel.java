package dev.createpolymer.schematics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;

/**
 * A level that holds the blocks, block entities and entities of a placed structure template, at the position the
 * template was placed at ("anchor"), without touching the world. This is Catnip's SchematicLevel: the printer
 * places a schematic into one of these and then reads the result block by block.
 * <p>
 * Everything that is not about the structure is answered by the level it wraps. Entities are created in that level
 * and only collected here, never added to it.
 */
public class SchematicLevel extends Level implements ServerLevelAccessor {

	protected final ServerLevel level;
	protected Map<BlockPos, BlockState> blocks;
	protected Map<BlockPos, BlockEntity> blockEntities;
	protected List<Entity> entities;
	protected BoundingBox bounds;

	public BlockPos anchor;
	public boolean renderMode;
	/** Create's SchematicProcessor.processEntity: entities that only operators may place are left out */
	public boolean filterUnsafeEntities;

	public SchematicLevel(ServerLevel original) {
		this(BlockPos.ZERO, original);
	}

	public SchematicLevel(BlockPos anchor, ServerLevel original) {
		super((WritableLevelData) original.getLevelData(), original.dimension(), original.registryAccess(),
			original.dimensionTypeRegistration(), false, original.isDebug(), 0, 0);
		this.level = original;
		this.blocks = new HashMap<>();
		this.blockEntities = new HashMap<>();
		this.bounds = new BoundingBox(BlockPos.ZERO);
		this.anchor = anchor;
		this.entities = new ArrayList<>();
	}

	public Set<BlockPos> getAllPositions() {
		return blocks.keySet();
	}

	public List<Entity> getEntityList() {
		return entities;
	}

	public Map<BlockPos, BlockState> getBlockMap() {
		return blocks;
	}

	public BoundingBox getBounds() {
		return bounds;
	}

	public void setBounds(BoundingBox bounds) {
		this.bounds = bounds;
	}

	public Collection<BlockEntity> getBlockEntities() {
		return blockEntities.values();
	}

	@Override
	public boolean addFreshEntity(Entity entityIn) {
		if (filterUnsafeEntities && entityIn.getType()
			.onlyOpCanSetNbt())
			return false;
		if (entityIn instanceof ItemFrame itemFrame)
			itemFrame.setItem(SchematicHelper.withUnsafeComponentsDiscarded(itemFrame.getItem()));
		if (entityIn instanceof ArmorStand armorStand)
			for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES)
				armorStand.setItemSlot(equipmentSlot,
					SchematicHelper.withUnsafeComponentsDiscarded(armorStand.getItemBySlot(equipmentSlot)));

		return entities.add(entityIn);
	}

	@Nullable
	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		if (isOutsideBuildHeight(pos))
			return null;
		if (blockEntities.containsKey(pos))
			return blockEntities.get(pos);
		if (!blocks.containsKey(pos.subtract(anchor)))
			return null;

		BlockState blockState = getBlockState(pos);
		if (blockState.hasBlockEntity()) {
			try {
				BlockEntity blockEntity = ((EntityBlock) blockState.getBlock()).newBlockEntity(pos.immutable(), blockState);
				if (blockEntity != null) {
					blockEntity.setLevel(this);
					blockEntities.put(pos.immutable(), blockEntity);
				}
				return blockEntity;
			} catch (Exception e) {
				dev.createpolymer.CreatePolymer.LOGGER.debug("Could not create BlockEntity of block " + blockState, e);
			}
		}
		return null;
	}

	@Override
	public BlockState getBlockState(BlockPos globalPos) {
		BlockPos pos = globalPos.subtract(anchor);

		if (pos.getY() - bounds.minY() == -1 && !renderMode)
			return Blocks.DIRT.defaultBlockState();
		if (bounds.isInside(pos) && blocks.containsKey(pos))
			return processBlockStateForPrinting(blocks.get(pos));
		return Blocks.AIR.defaultBlockState();
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
		return level.registryAccess()
			.lookupOrThrow(Registries.BIOME)
			.getOrThrow(Biomes.PLAINS);
	}

	@Override
	public int getBrightness(LightLayer lightLayer, BlockPos pos) {
		return 15;
	}

	@Override
	public LevelTickAccess<Block> getBlockTicks() {
		return BlackholeTickAccess.emptyLevelList();
	}

	@Override
	public LevelTickAccess<Fluid> getFluidTicks() {
		return BlackholeTickAccess.emptyLevelList();
	}

	@Override
	public List<Entity> getEntities(@Nullable Entity except, AABB bb, Predicate<? super Entity> selector) {
		return Collections.emptyList();
	}

	@Override
	public List<? extends Player> players() {
		return Collections.emptyList();
	}

	@Override
	public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
		return predicate.test(getBlockState(pos));
	}

	@Override
	public boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity breaker, int limit) {
		return setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	@Override
	public boolean removeBlock(BlockPos pos, boolean movedByPiston) {
		return setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	@Override
	public boolean setBlock(BlockPos pos, BlockState state, int flags, int limit) {
		BlockPos global = pos.immutable();
		BlockPos relative = global.subtract(anchor);
		bounds = SchematicHelper.encapsulate(bounds, relative);
		blocks.put(relative, state);
		BlockEntity existing = blockEntities.get(global);
		if (existing != null && !existing.getType()
			.isValid(state))
			blockEntities.remove(global);

		getBlockEntity(global);
		return true;
	}

	@Override
	public void sendBlockUpdated(BlockPos pos, BlockState old, BlockState current, int flags) {}

	/** The structure is not updated by what happens in it: shapes are updated once, when it is placed */
	@Override
	public void neighborShapeChanged(net.minecraft.core.Direction direction, BlockPos pos, BlockPos neighborPos,
		BlockState neighborState, int updateFlags, int updateLimit) {}

	protected BlockState processBlockStateForPrinting(BlockState state) {
		if (state.getBlock() instanceof AbstractFurnaceBlock && state.hasProperty(BlockStateProperties.LIT))
			state = state.setValue(BlockStateProperties.LIT, false);
		return state;
	}

	// ---- ServerLevelAccessor ----

	@Override
	public ServerLevel getLevel() {
		return level;
	}

	@Override
	public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
		return level.getCurrentDifficultyAt(pos);
	}

	// ---- everything else is the wrapped level's ----

	@Nullable
	@Override
	public MinecraftServer getServer() {
		return level.getServer();
	}

	@Override
	public net.minecraft.world.level.border.WorldBorder getWorldBorder() {
		return level.getWorldBorder();
	}

	@Override
	public ChunkSource getChunkSource() {
		return level.getChunkSource();
	}

	@Override
	public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() {
		return level.getLightEngine();
	}

	@Override
	public void levelEvent(@Nullable Entity source, int type, BlockPos pos, int data) {}

	@Override
	public void playSeededSound(@Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound,
		SoundSource source, float volume, float pitch, long seed) {}

	@Override
	public void playSeededSound(@Nullable Entity except, Entity sourceEntity, Holder<SoundEvent> sound,
		SoundSource source, float volume, float pitch, long seed) {}

	@Override
	public void explode(@Nullable Entity source, @Nullable net.minecraft.world.damagesource.DamageSource damageSource,
		@Nullable net.minecraft.world.level.ExplosionDamageCalculator damageCalculator, double x, double y, double z,
		float r, boolean fire, ExplosionInteraction interaction, net.minecraft.core.particles.ParticleOptions smallExplosionParticles,
		net.minecraft.core.particles.ParticleOptions largeExplosionParticles,
		net.minecraft.util.random.WeightedList<net.minecraft.core.particles.ExplosionParticleInfo> blockParticles,
		Holder<SoundEvent> explosionSound) {}

	@Override
	public void gameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context) {}

	@Override
	public String gatherChunkSourceStats() {
		return level.gatherChunkSourceStats();
	}

	@Override
	public void setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData respawnData) {}

	@Override
	public net.minecraft.world.level.storage.LevelData.RespawnData getRespawnData() {
		return level.getRespawnData();
	}

	@Nullable
	@Override
	public Entity getEntity(int id) {
		return null;
	}

	@Override
	public Collection<EnderDragonPart> dragonParts() {
		return Collections.emptyList();
	}

	@Override
	public TickRateManager tickRateManager() {
		return level.tickRateManager();
	}

	@Nullable
	@Override
	public MapItemSavedData getMapData(MapId id) {
		return null;
	}

	@Override
	public void destroyBlockProgress(int id, BlockPos pos, int progress) {}

	@Override
	public Scoreboard getScoreboard() {
		return level.getScoreboard();
	}

	@Override
	public RecipeAccess recipeAccess() {
		return level.recipeAccess();
	}

	@Override
	protected LevelEntityGetter<Entity> getEntities() {
		return NO_ENTITIES;
	}

	@Override
	public ClockManager clockManager() {
		return level.clockManager();
	}

	@Override
	public net.minecraft.world.attribute.EnvironmentAttributeSystem environmentAttributes() {
		return level.environmentAttributes();
	}

	@Override
	public FeatureFlagSet enabledFeatures() {
		return level.enabledFeatures();
	}

	@Override
	public int getSeaLevel() {
		return level.getSeaLevel();
	}

	private static final LevelEntityGetter<Entity> NO_ENTITIES = new LevelEntityGetter<>() {
		@Nullable
		@Override
		public Entity get(int id) {
			return null;
		}

		@Nullable
		@Override
		public Entity get(UUID id) {
			return null;
		}

		@Override
		public Iterable<Entity> getAll() {
			return Collections.emptyList();
		}

		@Override
		public <U extends Entity> void get(EntityTypeTest<Entity, U> type, AbortableIterationConsumer<U> consumer) {}

		@Override
		public void get(AABB bb, Consumer<Entity> output) {}

		@Override
		public <U extends Entity> void get(EntityTypeTest<Entity, U> type, AABB bb,
			AbortableIterationConsumer<U> consumer) {}
	};
}

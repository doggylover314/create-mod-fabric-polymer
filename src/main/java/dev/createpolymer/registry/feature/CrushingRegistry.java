package dev.createpolymer.registry.feature;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.crusher.CrushingWheelBlock;
import dev.createpolymer.kinetics.crusher.CrushingWheelBlockEntity;
import dev.createpolymer.kinetics.crusher.CrushingWheelControllerBlock;
import dev.createpolymer.kinetics.crusher.CrushingWheelControllerBlockEntity;
import dev.createpolymer.kinetics.drill.DrillBlock;
import dev.createpolymer.kinetics.drill.DrillBlockEntity;
import dev.createpolymer.processing.recipe.CrushingRecipe;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class CrushingRegistry {
	private CrushingRegistry() {}

	/** Create's CKinetics.crushingDamage: damage dealt by active crushing wheels. */
	public static int crushingDamage = 4;

	// Create: SharedProperties.stone with a podzol colour / metal colour
	public static final DrillBlock MECHANICAL_DRILL = ModBlocks.register("mechanical_drill", DrillBlock::new,
		ModBlocks.stone().mapColor(MapColor.PODZOL));
	public static final CrushingWheelBlock CRUSHING_WHEEL = ModBlocks.register("crushing_wheel", CrushingWheelBlock::new,
		ModBlocks.stone().mapColor(MapColor.METAL));
	// Create also marks the controller as .air(); in 26.3 entities skip air blocks when checking which blocks they are inside
	public static final CrushingWheelControllerBlock CRUSHING_WHEEL_CONTROLLER = ModBlocks.register("crushing_wheel_controller",
		CrushingWheelControllerBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE)
			.noOcclusion()
			.noLootTable()
			.noCollision()
			.pushReaction(PushReaction.IMMOVEABLE));

	public static final BlockEntityType<DrillBlockEntity> DRILL_BLOCK_ENTITY = ModBlockEntities.register("drill",
		(pos, state) -> new DrillBlockEntity(CrushingRegistry.DRILL_BLOCK_ENTITY, pos, state), MECHANICAL_DRILL);
	public static final BlockEntityType<CrushingWheelBlockEntity> CRUSHING_WHEEL_BLOCK_ENTITY = ModBlockEntities.register("crushing_wheel",
		(pos, state) -> new CrushingWheelBlockEntity(CrushingRegistry.CRUSHING_WHEEL_BLOCK_ENTITY, pos, state), CRUSHING_WHEEL);
	public static final BlockEntityType<CrushingWheelControllerBlockEntity> CRUSHING_WHEEL_CONTROLLER_BLOCK_ENTITY = ModBlockEntities.register(
		"crushing_wheel_controller",
		(pos, state) -> new CrushingWheelControllerBlockEntity(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER_BLOCK_ENTITY, pos, state),
		CRUSHING_WHEEL_CONTROLLER);

	public static final Item MECHANICAL_DRILL_ITEM = ModItems.block(MECHANICAL_DRILL);
	public static final Item CRUSHING_WHEEL_ITEM = ModItems.block(CRUSHING_WHEEL);

	public static final RecipeType<CrushingRecipe> CRUSHING = type("crushing");
	public static final RecipeSerializer<CrushingRecipe> CRUSHING_SERIALIZER = serializer("crushing", ProcessingRecipe.codec(CrushingRecipe::new));

	// Create's AllDamageTypes: the types themselves are data (data/create/damage_type)
	public static final ResourceKey<DamageType> DRILL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CreatePolymer.id("mechanical_drill"));
	public static final ResourceKey<DamageType> CRUSH_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CreatePolymer.id("crush"));

	/** Create's CreateDamageSources.drill */
	public static DamageSource drillDamage(LevelReader level) {
		return source(DRILL_DAMAGE, level);
	}

	/** Create's CreateDamageSources.crush */
	public static DamageSource crushDamage(LevelReader level) {
		return source(CRUSH_DAMAGE, level);
	}

	private static DamageSource source(ResourceKey<DamageType> key, LevelReader level) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key));
	}

	private static <T extends Recipe<?>> RecipeType<T> type(String name) {
		RecipeType<T> type = new RecipeType<>() {
			@Override
			public String toString() {
				return CreatePolymer.MOD_ID + ":" + name;
			}
		};
		Registry.register(BuiltInRegistries.RECIPE_TYPE, CreatePolymer.id(name), type);
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_TYPE, type);
		return type;
	}

	private static <T extends Recipe<?>> RecipeSerializer<T> serializer(String name, MapCodec<T> codec) {
		RecipeSerializer<T> serializer = new RecipeSerializer<>(codec, ByteBufCodecs.fromCodecWithRegistries(codec.codec()));
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, CreatePolymer.id(name), serializer);
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_SERIALIZER, serializer);
		return serializer;
	}

	public static void init() {
		CrushingWheelControllerBlockEntity.registerCapabilities();
		// Stress impacts from Create's AllBlocks (CStress.setImpact)
		BlockStressValues.setImpact(MECHANICAL_DRILL, 4);
		BlockStressValues.setImpact(CRUSHING_WHEEL, 8);
	}
}

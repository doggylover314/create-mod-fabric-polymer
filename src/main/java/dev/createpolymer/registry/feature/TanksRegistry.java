package dev.createpolymer.registry.feature;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.behaviour.spouting.BlockSpoutingBehaviours;
import dev.createpolymer.fluids.drain.ItemDrainBlock;
import dev.createpolymer.fluids.drain.ItemDrainBlockEntity;
import dev.createpolymer.fluids.spout.SpoutBlock;
import dev.createpolymer.fluids.spout.SpoutBlockEntity;
import dev.createpolymer.fluids.tank.BoilerHeaters;
import dev.createpolymer.fluids.tank.CreativeFluidTankBlockEntity;
import dev.createpolymer.fluids.tank.FluidTankBlock;
import dev.createpolymer.fluids.tank.FluidTankBlockEntity;
import dev.createpolymer.fluids.tank.FluidTankItem;
import dev.createpolymer.fluids.transfer.EmptyingRecipe;
import dev.createpolymer.fluids.transfer.FillingRecipe;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.kinetics.deployer.AssemblyOperatorBlockItem;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Fluid tank, creative fluid tank, spout and item drain with their items and block entities, and the filling and
 * emptying recipe types. The fluids themselves are in {@link FluidsRegistry}, which this initialises too.
 */
public final class TanksRegistry {
	private TanksRegistry() {}

	// Create: SharedProperties.copperMetal is a copy of the copper block
	public static final FluidTankBlock FLUID_TANK = ModBlocks.register("fluid_tank", FluidTankBlock::regular,
		BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected())
			.noOcclusion()
			.isRedstoneConductor((state, level, pos) -> true)
			.lightLevel(FluidTankBlock::getLightEmission));
	public static final FluidTankBlock CREATIVE_FLUID_TANK = ModBlocks.register("creative_fluid_tank", FluidTankBlock::creative,
		BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected())
			.noOcclusion()
			.mapColor(MapColor.COLOR_PURPLE)
			.lightLevel(FluidTankBlock::getLightEmission));
	public static final ItemDrainBlock ITEM_DRAIN = ModBlocks.register("item_drain", ItemDrainBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected()));
	public static final SpoutBlock SPOUT = ModBlocks.register("spout", SpoutBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected()));

	public static final BlockEntityType<FluidTankBlockEntity> FLUID_TANK_BLOCK_ENTITY = ModBlockEntities.register("fluid_tank",
		(pos, state) -> new FluidTankBlockEntity(TanksRegistry.FLUID_TANK_BLOCK_ENTITY, pos, state), FLUID_TANK);
	public static final BlockEntityType<CreativeFluidTankBlockEntity> CREATIVE_FLUID_TANK_BLOCK_ENTITY = ModBlockEntities.register("creative_fluid_tank",
		(pos, state) -> new CreativeFluidTankBlockEntity(TanksRegistry.CREATIVE_FLUID_TANK_BLOCK_ENTITY, pos, state), CREATIVE_FLUID_TANK);
	public static final BlockEntityType<ItemDrainBlockEntity> ITEM_DRAIN_BLOCK_ENTITY = ModBlockEntities.register("item_drain",
		(pos, state) -> new ItemDrainBlockEntity(TanksRegistry.ITEM_DRAIN_BLOCK_ENTITY, pos, state), ITEM_DRAIN);
	public static final BlockEntityType<SpoutBlockEntity> SPOUT_BLOCK_ENTITY = ModBlockEntities.register("spout",
		(pos, state) -> new SpoutBlockEntity(TanksRegistry.SPOUT_BLOCK_ENTITY, pos, state), SPOUT);

	public static final Item FLUID_TANK_ITEM = ModItems.register("fluid_tank", p -> new FluidTankItem(FLUID_TANK, p),
		new Item.Properties().useBlockDescriptionPrefix());
	public static final Item CREATIVE_FLUID_TANK_ITEM = ModItems.register("creative_fluid_tank",
		p -> new FluidTankItem(CREATIVE_FLUID_TANK, p), new Item.Properties().rarity(Rarity.EPIC).useBlockDescriptionPrefix());
	public static final Item ITEM_DRAIN_ITEM = ModItems.block(ITEM_DRAIN);
	public static final Item SPOUT_ITEM = ModItems.register("spout", p -> new AssemblyOperatorBlockItem(SPOUT, p),
		new Item.Properties().useBlockDescriptionPrefix());

	// Create's AllRecipeTypes.FILLING / EMPTYING
	public static final RecipeType<FillingRecipe> FILLING = recipeType("filling");
	public static final RecipeType<EmptyingRecipe> EMPTYING = recipeType("emptying");
	public static final RecipeSerializer<FillingRecipe> FILLING_SERIALIZER = serializer("filling", ProcessingRecipe.codec(FillingRecipe::new));
	public static final RecipeSerializer<EmptyingRecipe> EMPTYING_SERIALIZER = serializer("emptying", ProcessingRecipe.codec(EmptyingRecipe::new));

	private static <T extends Recipe<?>> RecipeType<T> recipeType(String name) {
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
		FluidsRegistry.init();
		FluidTankBlockEntity.registerCapabilities();
		CreativeFluidTankBlockEntity.registerCapabilities();
		SpoutBlockEntity.registerCapabilities();
		ItemDrainBlockEntity.registerCapabilities();
		BasinBlockEntity.registerCapabilities();
		BlockSpoutingBehaviours.registerDefaults();
		BoilerHeaters.registerDefaults();
	}
}

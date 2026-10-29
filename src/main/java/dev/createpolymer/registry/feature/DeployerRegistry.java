package dev.createpolymer.registry.feature;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.deployer.AssemblyOperatorBlockItem;
import dev.createpolymer.kinetics.deployer.DeployerApplicationRecipe;
import dev.createpolymer.kinetics.deployer.DeployerBlock;
import dev.createpolymer.kinetics.deployer.DeployerBlockEntity;
import dev.createpolymer.kinetics.deployer.ItemApplicationRecipe;
import dev.createpolymer.kinetics.deployer.ManualApplicationRecipe;
import dev.createpolymer.processing.sequenced.SequencedAssemblyItem;
import dev.createpolymer.processing.sequenced.SequencedAssemblyRecipe;
import dev.createpolymer.processing.sequenced.SequencedAssemblyRecipe.SequencedAssembly;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

/** The deployer, its recipes (deploying, item application) and sequenced assembly. */
public final class DeployerRegistry {
	private DeployerRegistry() {}

	// Create: SharedProperties.stone with a podzol colour
	public static final DeployerBlock DEPLOYER = ModBlocks.register("deployer", DeployerBlock::new,
		ModBlocks.stone().mapColor(MapColor.PODZOL));

	public static final BlockEntityType<DeployerBlockEntity> DEPLOYER_BLOCK_ENTITY = ModBlockEntities.register("deployer",
		(pos, state) -> new DeployerBlockEntity(DeployerRegistry.DEPLOYER_BLOCK_ENTITY, pos, state), DEPLOYER);

	public static final Item DEPLOYER_ITEM = ModItems.register("deployer",
		p -> new AssemblyOperatorBlockItem(DEPLOYER, p), new Item.Properties().useBlockDescriptionPrefix());

	// Create's AllDataComponents.SEQUENCED_ASSEMBLY. Server-only: Polymer strips it from what players receive.
	public static final DataComponentType<SequencedAssembly> SEQUENCED_ASSEMBLY = component("sequenced_assembly",
		DataComponentType.<SequencedAssembly>builder()
			.persistent(SequencedAssembly.CODEC)
			.networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(SequencedAssembly.CODEC))
			.build());

	// Create's AllItems.INCOMPLETE_PRECISION_MECHANISM, INCOMPLETE_REINFORCED_SHEET and INCOMPLETE_TRACK
	public static final SequencedAssemblyItem INCOMPLETE_PRECISION_MECHANISM = sequencedIngredient("incomplete_precision_mechanism");
	public static final SequencedAssemblyItem INCOMPLETE_REINFORCED_SHEET = sequencedIngredient("unprocessed_obsidian_sheet");
	public static final SequencedAssemblyItem INCOMPLETE_TRACK_ITEM = sequencedIngredient("incomplete_track");

	public static final RecipeType<DeployerApplicationRecipe> DEPLOYING = type("deploying");
	public static final RecipeType<ManualApplicationRecipe> ITEM_APPLICATION = type("item_application");
	public static final RecipeType<SequencedAssemblyRecipe> SEQUENCED_ASSEMBLY_TYPE = type("sequenced_assembly");

	public static final RecipeSerializer<DeployerApplicationRecipe> DEPLOYING_SERIALIZER = serializer("deploying",
		ItemApplicationRecipe.codec(DeployerApplicationRecipe::new));
	public static final RecipeSerializer<ManualApplicationRecipe> ITEM_APPLICATION_SERIALIZER = serializer("item_application",
		ItemApplicationRecipe.codec(ManualApplicationRecipe::new));
	public static final RecipeSerializer<SequencedAssemblyRecipe> SEQUENCED_ASSEMBLY_SERIALIZER = serializer("sequenced_assembly",
		SequencedAssemblyRecipe.CODEC);

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	private static SequencedAssemblyItem sequencedIngredient(String name) {
		SequencedAssemblyItem item = ModItems.register(name, SequencedAssemblyItem::new, new Item.Properties());
		// Create keeps these out of the creative tab
		ModItems.TAB_ORDER.remove(item);
		return item;
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
		BlockStressValues.setImpact(DEPLOYER, 4.0);
		DeployerBlockEntity.registerCapabilities();
		ManualApplicationRecipe.init();
	}
}

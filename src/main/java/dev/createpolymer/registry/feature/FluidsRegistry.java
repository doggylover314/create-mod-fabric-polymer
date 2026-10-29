package dev.createpolymer.registry.feature;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.fluids.FluidBucketItem;
import dev.createpolymer.fluids.VirtualFluid;
import dev.createpolymer.fluids.potion.PotionFluid;
import dev.createpolymer.fluids.potion.PotionFluid.BottleType;
import dev.createpolymer.fluids.potion.PotionFluidHandler;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.material.Fluid;

/**
 * Create's AllFluids: potion, tea, honey and chocolate, registered under the create namespace.
 * <p>
 * All four are virtual: they have no block, so they live in tanks, basins and item containers but cannot be placed in
 * the world. (Create's honey and chocolate are flowing fluids with world blocks.) Honey and chocolate have buckets that
 * work with tanks, spouts and item drains but cannot be poured out. The fluids are server-only entries: Polymer does
 * not tell clients about them.
 */
public final class FluidsRegistry {
	private FluidsRegistry() {}

	/** Create's AllDataComponents.POTION_FLUID_BOTTLE_TYPE, kept on potion fluid stacks. */
	public static final DataComponentType<BottleType> POTION_FLUID_BOTTLE_TYPE = component("potion_fluid_bottle_type",
		DataComponentType.<BottleType>builder()
			.persistent(BottleType.CODEC)
			.build());

	public static final TagKey<Item> NOT_POTION = TagKey.create(Registries.ITEM, CreatePolymer.id("not_potion"));

	public static final PotionFluid POTION = virtualFluid("potion", PotionFluid::createSource, PotionFluid::createFlowing, null);
	public static final VirtualFluid TEA = virtualFluid("tea", VirtualFluid::createSource, VirtualFluid::createFlowing, null);
	public static final VirtualFluid HONEY = virtualFluid("honey", VirtualFluid::createSource, VirtualFluid::createFlowing,
		"honey_bucket");
	public static final VirtualFluid CHOCOLATE = virtualFluid("chocolate", VirtualFluid::createSource, VirtualFluid::createFlowing,
		"chocolate_bucket");

	public static final Item HONEY_BUCKET = bucket("honey_bucket", HONEY);
	public static final Item CHOCOLATE_BUCKET = bucket("chocolate_bucket", CHOCOLATE);

	@SuppressWarnings("unchecked")
	private static <F extends VirtualFluid> F virtualFluid(String name, Function<VirtualFluid.Properties, F> source,
		Function<VirtualFluid.Properties, F> flowing, @Nullable String bucket) {
		VirtualFluid.Properties properties = new VirtualFluid.Properties();
		F sourceFluid = source.apply(properties);
		F flowingFluid = flowing.apply(properties);
		properties.source(() -> sourceFluid).flowing(() -> flowingFluid);
		if (bucket != null)
			properties.bucket(() -> BuiltInRegistries.ITEM.getValue(CreatePolymer.id(bucket)));
		register(name, sourceFluid);
		register("flowing_" + name, flowingFluid);
		return sourceFluid;
	}

	private static void register(String name, Fluid fluid) {
		Registry.register(BuiltInRegistries.FLUID, CreatePolymer.id(name), fluid);
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.FLUID, fluid);
	}

	private static Item bucket(String name, VirtualFluid fluid) {
		return ModItems.register(name, p -> new FluidBucketItem(fluid, p), new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET));
	}

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	/** Create's Builder's Tea, if it is ported: filling a bottle with tea gives it. */
	@Nullable
	public static Item buildersTea() {
		return BuiltInRegistries.ITEM.getOptional(CreatePolymer.id("builders_tea")).orElse(null);
	}

	public static void init() {
		FluidVariantAttributes.register(POTION, new FluidVariantAttributeHandler() {
			@Override
			public Component getName(FluidVariant variant) {
				PotionContents contents = variant.getComponents().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
				BottleType bottleType = variant.getComponents().getOrDefault(POTION_FLUID_BOTTLE_TYPE, BottleType.REGULAR);
				return contents.getName(PotionFluidHandler.itemFromBottleType(bottleType).asItem().getDescriptionId() + ".effect.");
			}

			@Override
			public int getAssociatedColor(FluidVariant variant) {
				return variant.getComponents().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor() | 0xff000000;
			}
		});
		simpleAttributes(TEA, 0xB5651D);
		simpleAttributes(HONEY, 0xEAAE2F);
		simpleAttributes(CHOCOLATE, 0x622020);
	}

	private static void simpleAttributes(VirtualFluid fluid, int color) {
		String key = "fluid." + BuiltInRegistries.FLUID.getKey(fluid).getNamespace() + "." + BuiltInRegistries.FLUID.getKey(fluid).getPath();
		FluidVariantAttributes.register(fluid, new FluidVariantAttributeHandler() {
			@Override
			public Component getName(FluidVariant variant) {
				return Component.translatable(key);
			}

			@Override
			public int getAssociatedColor(FluidVariant variant) {
				return color | 0xff000000;
			}

			@Override
			public int getLightEmission(FluidVariant variant) {
				return 0;
			}
		});
	}
}

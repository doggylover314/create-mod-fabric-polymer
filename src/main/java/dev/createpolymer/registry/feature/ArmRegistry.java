package dev.createpolymer.registry.feature;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.mechanicalArm.AllArmInteractionPointTypes;
import dev.createpolymer.kinetics.mechanicalArm.ArmBlock;
import dev.createpolymer.kinetics.mechanicalArm.ArmBlockEntity;
import dev.createpolymer.kinetics.mechanicalArm.ArmInteractionPointHandler;
import dev.createpolymer.kinetics.mechanicalArm.ArmInteractionPointHandler.Selection;
import dev.createpolymer.kinetics.mechanicalArm.ArmItem;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** The mechanical arm, its item and the component that carries the targets chosen before placing it. */
public final class ArmRegistry {
	private ArmRegistry() {}

	/** Create's config: CLogistics.mechanicalArmRange, the maximum distance in blocks an arm can reach across. */
	public static int mechanicalArmRange = 5;

	/**
	 * The inputs and outputs picked with an arm item. Server-only: Polymer strips it from what players receive.
	 * Create keeps this selection in the client instead.
	 */
	public static final DataComponentType<Selection> ARM_POINTS = component("arm_points",
		DataComponentType.<Selection>builder()
			.persistent(Selection.CODEC)
			.networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(Selection.CODEC))
			.build());

	// Create: SharedProperties.softMetal (a gold block), terracotta yellow
	public static final ArmBlock MECHANICAL_ARM = ModBlocks.register("mechanical_arm", ArmBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).noOcclusion().mapColor(MapColor.TERRACOTTA_YELLOW));

	public static final BlockEntityType<ArmBlockEntity> MECHANICAL_ARM_BLOCK_ENTITY = ModBlockEntities.register(
		"mechanical_arm", (pos, state) -> new ArmBlockEntity(ArmRegistry.MECHANICAL_ARM_BLOCK_ENTITY, pos, state),
		MECHANICAL_ARM);

	public static final Item MECHANICAL_ARM_ITEM = ModItems.register("mechanical_arm",
		p -> new ArmItem(MECHANICAL_ARM, p), new Item.Properties().useBlockDescriptionPrefix());

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	public static void init() {
		// Create: CStress.setImpact(2.0)
		BlockStressValues.setImpact(MECHANICAL_ARM, 2);
		AllArmInteractionPointTypes.init();
		ArmInteractionPointHandler.init();
	}
}

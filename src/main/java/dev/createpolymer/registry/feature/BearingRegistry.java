package dev.createpolymer.registry.feature;

import java.util.EnumMap;
import java.util.Map;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.api.contraption.BlockMovementChecks;
import dev.createpolymer.api.contraption.BlockMovementChecks.CheckResult;
import dev.createpolymer.api.contraption.ContraptionType;
import dev.createpolymer.contraptions.bearing.BearingContraption;
import dev.createpolymer.contraptions.bearing.BlankSailBlockItem;
import dev.createpolymer.contraptions.bearing.ClockworkBearingBlock;
import dev.createpolymer.contraptions.bearing.ClockworkBearingBlockEntity;
import dev.createpolymer.contraptions.bearing.ClockworkContraption;
import dev.createpolymer.contraptions.bearing.MechanicalBearingBlock;
import dev.createpolymer.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.createpolymer.contraptions.bearing.SailBlock;
import dev.createpolymer.contraptions.bearing.StabilizedBearingMovementBehaviour;
import dev.createpolymer.contraptions.bearing.StabilizedContraption;
import dev.createpolymer.contraptions.bearing.WindmillBearingBlock;
import dev.createpolymer.contraptions.bearing.WindmillBearingBlockEntity;
import dev.createpolymer.contraptions.chassis.AbstractChassisBlock;
import dev.createpolymer.contraptions.chassis.ChassisBlockEntity;
import dev.createpolymer.contraptions.chassis.LinearChassisBlock;
import dev.createpolymer.contraptions.chassis.RadialChassisBlock;
import dev.createpolymer.contraptions.chassis.StickerBlock;
import dev.createpolymer.contraptions.chassis.StickerBlockEntity;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Mechanical, windmill and clockwork bearings, sails, linear and radial chassis, stickers. */
public final class BearingRegistry {
	private BearingRegistry() {}

	/** Create's CKinetics.windmillSailsPerRPM. */
	public static int windmillSailsPerRPM = 8;

	// ---- contraption types (Create's AllContraptionTypes) ----

	public static final ContraptionType BEARING = ContraptionType.register("bearing", BearingContraption::new);
	public static final ContraptionType CLOCKWORK = ContraptionType.register("clockwork", ClockworkContraption::new);
	public static final ContraptionType STABILIZED = ContraptionType.register("stabilized", StabilizedContraption::new);

	// ---- blocks ----

	public static final WindmillBearingBlock WINDMILL_BEARING = ModBlocks.register("windmill_bearing",
		WindmillBearingBlock::new, ModBlocks.stone().mapColor(MapColor.PODZOL));
	public static final MechanicalBearingBlock MECHANICAL_BEARING = ModBlocks.register("mechanical_bearing",
		MechanicalBearingBlock::new, ModBlocks.stone().mapColor(MapColor.PODZOL));
	public static final ClockworkBearingBlock CLOCKWORK_BEARING = ModBlocks.register("clockwork_bearing",
		ClockworkBearingBlock::new, ModBlocks.stone().mapColor(MapColor.TERRACOTTA_BROWN));

	// Create's SharedProperties.wooden, which is a full block
	private static BlockBehaviour.Properties chassis(MapColor color) {
		return BlockBehaviour.Properties.of().strength(2f, 3f).sound(SoundType.WOOD).mapColor(color);
	}

	public static final LinearChassisBlock LINEAR_CHASSIS = ModBlocks.register("linear_chassis",
		p -> new LinearChassisBlock(p, "linear_chassis"), chassis(MapColor.TERRACOTTA_BROWN));
	public static final LinearChassisBlock SECONDARY_LINEAR_CHASSIS = ModBlocks.register("secondary_linear_chassis",
		p -> new LinearChassisBlock(p, "secondary_linear_chassis"), chassis(MapColor.PODZOL));
	public static final RadialChassisBlock RADIAL_CHASSIS = ModBlocks.register("radial_chassis",
		RadialChassisBlock::new, chassis(MapColor.DIRT));

	public static final StickerBlock STICKER = ModBlocks.register("sticker", StickerBlock::new,
		ModBlocks.stone());

	public static final SailBlock SAIL_FRAME = ModBlocks.register("sail_frame", SailBlock::frame,
		ModBlocks.wooden().mapColor(MapColor.DIRT).sound(SoundType.SCAFFOLDING));
	public static final SailBlock SAIL = ModBlocks.register("white_sail", p -> SailBlock.withCanvas(p, DyeColor.WHITE),
		canvas(MapColor.SNOW));

	public static final Map<DyeColor, SailBlock> DYED_SAILS = registerDyedSails();

	private static BlockBehaviour.Properties canvas(MapColor color) {
		// Create's sails bounce and cancel falls: 26.3 keeps both in the block properties
		return ModBlocks.wooden().mapColor(color).sound(SoundType.SCAFFOLDING).bounceRestitution(.26f)
			.fallDistanceReduction(1f);
	}

	private static Map<DyeColor, SailBlock> registerDyedSails() {
		Map<DyeColor, SailBlock> sails = new EnumMap<>(DyeColor.class);
		for (DyeColor color : DyeColor.values()) {
			if (color == DyeColor.WHITE) {
				sails.put(color, SAIL);
				continue;
			}
			sails.put(color, ModBlocks.register(color.getSerializedName() + "_sail",
				p -> SailBlock.withCanvas(p, color), canvas(color.getMapColor())));
		}
		return sails;
	}

	// ---- block entities ----

	public static final BlockEntityType<WindmillBearingBlockEntity> WINDMILL_BEARING_BLOCK_ENTITY = ModBlockEntities
		.register("windmill_bearing", (pos, state) -> new WindmillBearingBlockEntity(
			BearingRegistry.WINDMILL_BEARING_BLOCK_ENTITY, pos, state), WINDMILL_BEARING);
	public static final BlockEntityType<MechanicalBearingBlockEntity> MECHANICAL_BEARING_BLOCK_ENTITY = ModBlockEntities
		.register("mechanical_bearing", (pos, state) -> new MechanicalBearingBlockEntity(
			BearingRegistry.MECHANICAL_BEARING_BLOCK_ENTITY, pos, state), MECHANICAL_BEARING);
	public static final BlockEntityType<ClockworkBearingBlockEntity> CLOCKWORK_BEARING_BLOCK_ENTITY = ModBlockEntities
		.register("clockwork_bearing", (pos, state) -> new ClockworkBearingBlockEntity(
			BearingRegistry.CLOCKWORK_BEARING_BLOCK_ENTITY, pos, state), CLOCKWORK_BEARING);
	public static final BlockEntityType<ChassisBlockEntity> CHASSIS_BLOCK_ENTITY = ModBlockEntities.register("chassis",
		(pos, state) -> new ChassisBlockEntity(BearingRegistry.CHASSIS_BLOCK_ENTITY, pos, state), RADIAL_CHASSIS,
		LINEAR_CHASSIS, SECONDARY_LINEAR_CHASSIS);
	public static final BlockEntityType<StickerBlockEntity> STICKER_BLOCK_ENTITY = ModBlockEntities.register("sticker",
		(pos, state) -> new StickerBlockEntity(BearingRegistry.STICKER_BLOCK_ENTITY, pos, state), STICKER);

	// ---- items ----

	public static final Item WINDMILL_BEARING_ITEM = ModItems.block(WINDMILL_BEARING);
	public static final Item MECHANICAL_BEARING_ITEM = ModItems.block(MECHANICAL_BEARING);
	public static final Item CLOCKWORK_BEARING_ITEM = ModItems.block(CLOCKWORK_BEARING);
	public static final Item LINEAR_CHASSIS_ITEM = ModItems.block(LINEAR_CHASSIS);
	public static final Item SECONDARY_LINEAR_CHASSIS_ITEM = ModItems.block(SECONDARY_LINEAR_CHASSIS);
	public static final Item RADIAL_CHASSIS_ITEM = ModItems.block(RADIAL_CHASSIS);
	public static final Item STICKER_ITEM = ModItems.block(STICKER);
	public static final Item SAIL_FRAME_ITEM = ModItems.block(SAIL_FRAME);
	/** Stands for every dyed sail as well, see {@link BlankSailBlockItem}. */
	public static final Item SAIL_ITEM = ModItems.register("white_sail", p -> new BlankSailBlockItem(SAIL, p),
		new Item.Properties().useBlockDescriptionPrefix());

	public static void init() {
		// Create's AllBlocks: CStress
		BlockStressValues.setImpact(MECHANICAL_BEARING, 4);
		BlockStressValues.setImpact(CLOCKWORK_BEARING, 4);
		BlockStressValues.setCapacity(WINDMILL_BEARING, 512);
		BlockStressValues.setGeneratorSpeed(WINDMILL_BEARING, 16);

		// Create's AllMovementBehaviours
		MovementBehaviour.REGISTRY.register(MECHANICAL_BEARING, new StabilizedBearingMovementBehaviour());

		// the dyed sails are placed with the white sail's item
		((BlankSailBlockItem) SAIL_ITEM).registerBlocks(Item.BY_BLOCK, SAIL_ITEM);

		registerMovementChecks();
	}

	/** The bearing, chassis, sail and sticker branches of Create's BlockMovementChecksImpl. */
	private static void registerMovementChecks() {
		BlockMovementChecks.registerMovementAllowedCheck((state, world, pos) -> {
			if (state.getBlock() instanceof AbstractChassisBlock)
				return CheckResult.SUCCESS;
			if (state.getBlock() instanceof MechanicalBearingBlock) {
				BlockEntity be = world.getBlockEntity(pos);
				if (be instanceof MechanicalBearingBlockEntity)
					return CheckResult.of(!((MechanicalBearingBlockEntity) be).isRunning());
			}
			if (state.getBlock() instanceof ClockworkBearingBlock) {
				BlockEntity be = world.getBlockEntity(pos);
				if (be instanceof ClockworkBearingBlockEntity)
					return CheckResult.of(!((ClockworkBearingBlockEntity) be).isRunning());
			}
			return CheckResult.PASS;
		});

		BlockMovementChecks.registerAttachedCheck((state, world, pos, direction) -> {
			if (state.getBlock() instanceof SailBlock)
				return CheckResult.of(direction.getAxis() != state.getValue(SailBlock.FACING)
					.getAxis());
			if (state.getBlock() instanceof StickerBlock && state.getValue(StickerBlock.EXTENDED)) {
				return CheckResult.of(direction == state.getValue(StickerBlock.FACING)
					&& !BlockMovementChecks.isNotSupportive(world.getBlockState(pos.relative(direction)),
						direction.getOpposite()));
			}
			return CheckResult.PASS;
		});

		BlockMovementChecks.registerNotSupportiveCheck((state, facing) -> {
			if (state.getBlock() instanceof MechanicalBearingBlock)
				return CheckResult.of(state.getValue(MechanicalBearingBlock.FACING) == facing);
			if (state.getBlock() instanceof SailBlock)
				return CheckResult.of(facing.getAxis() == state.getValue(SailBlock.FACING)
					.getAxis());
			if (state.getBlock() instanceof StickerBlock && !state.getValue(StickerBlock.EXTENDED))
				return CheckResult.of(facing == state.getValue(StickerBlock.FACING));
			return CheckResult.PASS;
		});
	}
}

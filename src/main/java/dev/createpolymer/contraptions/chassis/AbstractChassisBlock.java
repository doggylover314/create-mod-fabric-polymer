package dev.createpolymer.contraptions.chassis;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.contraption.transformable.TransformableBlock;
import dev.createpolymer.contraptions.StructureTransform;
import dev.createpolymer.contraptions.bearing.BearingSounds;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.feature.BearingRegistry;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import dev.createpolymer.util.Iterate;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's AbstractChassisBlock (MIT, The Create Team). Players see the chassis as a cube of Polymer's
 * textured block states, one for each combination of axis and sticky sides; where Polymer has none left they see
 * a stripped log.
 */
public abstract class AbstractChassisBlock extends RotatedPillarBlock
	implements IWrenchable, IBE<ChassisBlockEntity>, TransformableBlock, PolymerTexturedBlock {

	private final Map<BlockState, BlockState> clientStates = new IdentityHashMap<>();

	public AbstractChassisBlock(Properties properties) {
		super(properties);
	}

	/** Called by the subclasses once their properties exist: asks Polymer for the state of every blockstate. */
	protected void requestClientStates() {
		for (BlockState state : getStateDefinition().getPossibleStates()) {
			BlockState client = requestClientState(state);
			if (client != null)
				clientStates.put(state, client);
		}
	}

	protected abstract @Nullable BlockState requestClientState(BlockState state);

	/** True when every state of this block has a texture state of its own. */
	public boolean isTextured() {
		return clientStates.size() == getStateDefinition().getPossibleStates().size();
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		BlockState client = clientStates.get(state);
		return client != null ? client
			: Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState().setValue(AXIS, state.getValue(AXIS));
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (!player.mayBuild())
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		boolean isSlimeBall = stack.is(ConventionalItemTags.SLIME_BALLS) || stack.is(ContraptionRegistry.SUPER_GLUE_ITEM);

		BooleanProperty affectedSide = getGlueableSide(state, hitResult.getDirection());
		if (affectedSide == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		if (isSlimeBall && state.getValue(affectedSide)) {
			for (Direction face : Iterate.directions) {
				BooleanProperty glueableSide = getGlueableSide(state, face);
				if (glueableSide != null && !state.getValue(glueableSide)
					&& glueAllowedOnSide(level, pos, state, face)) {
					if (level.isClientSide())
						return InteractionResult.SUCCESS;
					slimeParticles(level, hitResult.getLocation());
					BearingSounds.slimeAdded(level, pos, .5f, 1);
					state = state.setValue(glueableSide, true);
				}
			}
			if (!level.isClientSide())
				level.setBlockAndUpdate(pos, state);
			return InteractionResult.SUCCESS;
		}

		if ((!stack.isEmpty() || !player.isShiftKeyDown()) && !isSlimeBall)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (state.getValue(affectedSide) == isSlimeBall)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!glueAllowedOnSide(level, pos, state, hitResult.getDirection()))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		slimeParticles(level, hitResult.getLocation());
		BearingSounds.slimeAdded(level, pos, .5f, 1);
		level.setBlockAndUpdate(pos, state.setValue(affectedSide, isSlimeBall));
		return InteractionResult.SUCCESS;
	}

	private static void slimeParticles(Level level, Vec3 vec) {
		if (level instanceof ServerLevel serverLevel)
			serverLevel.sendParticles(ParticleTypes.ITEM_SLIME, vec.x, vec.y, vec.z, 3, 0.1, 0.1, 0.1, 0);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (rotation == Rotation.NONE)
			return state;

		BlockState rotated = super.rotate(state, rotation);
		for (Direction face : Iterate.directions) {
			BooleanProperty glueableSide = getGlueableSide(rotated, face);
			if (glueableSide != null)
				rotated = rotated.setValue(glueableSide, false);
		}

		for (Direction face : Iterate.directions) {
			BooleanProperty glueableSide = getGlueableSide(state, face);
			if (glueableSide == null || !state.getValue(glueableSide))
				continue;
			Direction rotatedFacing = rotation.rotate(face);
			BooleanProperty rotatedGlueableSide = getGlueableSide(rotated, rotatedFacing);
			if (rotatedGlueableSide != null)
				rotated = rotated.setValue(rotatedGlueableSide, true);
		}

		return rotated;
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirrorIn) {
		if (mirrorIn == Mirror.NONE)
			return state;

		BlockState mirrored = state;
		for (Direction face : Iterate.directions) {
			BooleanProperty glueableSide = getGlueableSide(mirrored, face);
			if (glueableSide != null)
				mirrored = mirrored.setValue(glueableSide, false);
		}

		for (Direction face : Iterate.directions) {
			BooleanProperty glueableSide = getGlueableSide(state, face);
			if (glueableSide == null || !state.getValue(glueableSide))
				continue;
			Direction mirroredFacing = mirrorIn.mirror(face);
			BooleanProperty mirroredGlueableSide = getGlueableSide(mirrored, mirroredFacing);
			if (mirroredGlueableSide != null)
				mirrored = mirrored.setValue(mirroredGlueableSide, true);
		}

		return mirrored;
	}

	@Override
	public BlockState transform(BlockState state, StructureTransform transform) {
		if (transform.mirror != null) {
			state = mirror(state, transform.mirror);
		}

		if (transform.rotationAxis == Direction.Axis.Y) {
			return rotate(state, transform.rotation);
		}
		return transformInner(state, transform);
	}

	protected BlockState transformInner(BlockState state, StructureTransform transform) {
		if (transform.rotation == Rotation.NONE)
			return state;

		BlockState rotated = state.setValue(AXIS, transform.rotateAxis(state.getValue(AXIS)));
		AbstractChassisBlock block = (AbstractChassisBlock) state.getBlock();

		for (Direction face : Iterate.directions) {
			BooleanProperty glueableSide = block.getGlueableSide(rotated, face);
			if (glueableSide != null)
				rotated = rotated.setValue(glueableSide, false);
		}

		for (Direction face : Iterate.directions) {
			BooleanProperty glueableSide = block.getGlueableSide(state, face);
			if (glueableSide == null || !state.getValue(glueableSide))
				continue;
			Direction rotatedFacing = transform.rotateFacing(face);
			BooleanProperty rotatedGlueableSide = block.getGlueableSide(rotated, rotatedFacing);
			if (rotatedGlueableSide != null)
				rotated = rotated.setValue(rotatedGlueableSide, true);
		}

		return rotated;
	}

	public abstract BooleanProperty getGlueableSide(BlockState state, Direction face);

	protected boolean glueAllowedOnSide(BlockGetter world, BlockPos pos, BlockState state, Direction side) {
		return true;
	}

	@Override
	public Class<ChassisBlockEntity> getBlockEntityClass() {
		return ChassisBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ChassisBlockEntity> getBlockEntityType() {
		return BearingRegistry.CHASSIS_BLOCK_ENTITY;
	}

}

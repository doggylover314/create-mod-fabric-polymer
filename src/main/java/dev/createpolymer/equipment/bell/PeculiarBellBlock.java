package dev.createpolymer.equipment.bell;

import dev.createpolymer.registry.feature.ExtrasRegistry;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Create's peculiar bell (ported from Create's PeculiarBellBlock, MIT, The Create Team). Placed or pushed over soul
 * fire or a soul campfire it turns into a haunted bell.
 */
public class PeculiarBellBlock extends AbstractBellBlock<PeculiarBellBlockEntity> {

	public PeculiarBellBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends PeculiarBellBlockEntity> getBlockEntityType() {
		return ExtrasRegistry.PECULIAR_BELL_BLOCK_ENTITY;
	}

	@Override
	public Class<PeculiarBellBlockEntity> getBlockEntityClass() {
		return PeculiarBellBlockEntity.class;
	}

	@Override
	public void playSound(Level world, BlockPos pos) {
		world.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 0.94f);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		BlockState newState = super.getStateForPlacement(ctx);
		if (newState == null)
			return null;

		Level world = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		return tryConvert(world, pos, newState, world.getBlockState(pos.relative(Direction.DOWN)));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		BlockState newState = super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
		if (directionToNeighbour != Direction.DOWN)
			return newState;

		return tryConvert(level, pos, newState, neighbourState);
	}

	protected BlockState tryConvert(LevelReader world, BlockPos pos, BlockState state, BlockState underState) {
		if (!state.is(ExtrasRegistry.PECULIAR_BELL))
			return state;

		Block underBlock = underState.getBlock();
		if (!(Blocks.SOUL_FIRE.equals(underBlock) || Blocks.SOUL_CAMPFIRE.equals(underBlock)))
			return state;

		if (world instanceof ServerLevel serverLevel) {
			spawnConversionParticles(serverLevel, pos);
			serverLevel.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1f, 1f);
		}

		return ExtrasRegistry.HAUNTED_BELL.defaultBlockState()
			.setValue(HauntedBellBlock.FACING, state.getValue(FACING))
			.setValue(HauntedBellBlock.ATTACHMENT, state.getValue(ATTACHMENT))
			.setValue(HauntedBellBlock.POWERED, state.getValue(POWERED));
	}

	public void spawnConversionParticles(ServerLevel world, BlockPos blockPos) {
		RandomSource random = world.getRandom();
		int num = random.nextInt(10) + 15;
		for (int i = 0; i < num; i++) {
			float pitch = random.nextFloat() * 120 - 90;
			float yaw = random.nextFloat() * 360;
			Vec3 vel = Vec3.directionFromRotation(pitch, yaw).scale(random.nextDouble() * 0.1 + 0.1);
			Vec3 pos = Vec3.atCenterOf(blockPos);
			world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 0, vel.x, vel.y, vel.z, 1);
		}
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new BellModel(state, "peculiar_bell");
	}

	@Override
	public BlockState particleState() {
		return Blocks.GOLD_BLOCK.defaultBlockState();
	}
}

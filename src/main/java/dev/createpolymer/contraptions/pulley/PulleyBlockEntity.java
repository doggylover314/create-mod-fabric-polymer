package dev.createpolymer.contraptions.pulley;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.contraption.BlockMovementChecks;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.ContraptionCollider;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.piston.LinearActuatorBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.redstone.thresholdSwitch.ThresholdSwitchObservable;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import dev.createpolymer.registry.feature.PulleyRegistry;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class PulleyBlockEntity extends LinearActuatorBlockEntity implements ThresholdSwitchObservable {

	protected int initialOffset;

	protected BlockPos mirrorParent;
	protected List<BlockPos> mirrorChildren;
	public WeakReference<AbstractContraptionEntity> sharedMirrorContraption;

	public PulleyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void tick() {
		super.tick();

		// Create does this on the client, where the mirrored pulley has no contraption of its own; the model here
		// reads the contraption of the pulley it mirrors
		if (mirrorParent != null)
			if (sharedMirrorContraption == null || sharedMirrorContraption.get() == null
				|| !sharedMirrorContraption.get()
					.isAlive()) {
				sharedMirrorContraption = null;
				if (level.getBlockEntity(mirrorParent) instanceof PulleyBlockEntity pte && pte.movedContraption != null)
					sharedMirrorContraption = new WeakReference<>(pte.movedContraption);
			}
	}

	@Override
	protected boolean isPassive() {
		return mirrorParent != null;
	}

	@Nullable
	public AbstractContraptionEntity getAttachedContraption() {
		return mirrorParent != null && sharedMirrorContraption != null ? sharedMirrorContraption.get()
			: movedContraption;
	}

	/** Create's PulleyRenderer.isPulleyRunning */
	public boolean isDisplayedAsRunning() {
		return running || mirrorParent != null;
	}

	/**
	 * Create's PulleyRenderer.getBlockEntityOffset: the rope's length as the pulley shows it, taken from the
	 * contraption's position while there is one. Unlike {@link #getInterpolatedOffset} this does not ask for the
	 * movement speed, which elevators change when it is asked.
	 */
	public float getDisplayOffset() {
		float shown = offset;
		AbstractContraptionEntity attachedContraption = getAttachedContraption();
		if (attachedContraption != null && attachedContraption.getContraption() instanceof PulleyContraption c)
			shown = (float) -(attachedContraption.getY() - c.anchor.getY() - c.getInitialOffset());
		return shown;
	}

	@Override
	protected void assemble() throws AssemblyException {
		if (!(level.getBlockState(worldPosition)
			.getBlock() instanceof PulleyBlock))
			return;
		if (speed == 0 && mirrorParent == null)
			return;
		int maxLength = ContraptionRegistry.maxRopeLength;
		int i = 1;
		while (i <= maxLength) {
			BlockPos ropePos = worldPosition.below(i);
			BlockState ropeState = level.getBlockState(ropePos);
			if (!ropeState.is(PulleyRegistry.ROPE) && !ropeState.is(PulleyRegistry.PULLEY_MAGNET)) {
				break;
			}
			++i;
		}
		offset = i - 1;
		if (offset >= getExtensionRange() && getSpeed() > 0)
			return;
		if (offset <= 0 && getSpeed() < 0)
			return;

		// Collect Construct
		if (mirrorParent == null) {
			needsContraption = false;
			BlockPos anchor = worldPosition.below(Mth.floor(offset + 1));
			initialOffset = Mth.floor(offset);
			PulleyContraption contraption = new PulleyContraption(initialOffset);
			boolean canAssembleStructure = contraption.assemble(level, anchor);

			if (canAssembleStructure) {
				Direction movementDirection = getSpeed() > 0 ? Direction.DOWN : Direction.UP;
				if (ContraptionCollider.isCollidingWithWorld(level, contraption, anchor.relative(movementDirection),
					movementDirection))
					canAssembleStructure = false;
			}

			if (!canAssembleStructure && getSpeed() > 0)
				return;

			removeRopes();

			if (!contraption.getBlocks()
				.isEmpty()) {
				contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
				movedContraption = ControlledContraptionEntity.create(level, this, contraption);
				movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
				level.addFreshEntity(movedContraption);
				forceMove = true;
				needsContraption = true;

				for (BlockPos pos : contraption.createColliders(level, Direction.UP)) {
					if (pos.getY() != 0)
						continue;
					pos = pos.offset(anchor);
					if (level.getBlockEntity(
						new BlockPos(pos.getX(), worldPosition.getY(), pos.getZ())) instanceof PulleyBlockEntity pbe)
						pbe.startMirroringOther(worldPosition);
				}
			}
		}

		if (mirrorParent != null)
			removeRopes();

		running = true;
		sendData();
	}

	private void removeRopes() {
		for (int i = ((int) offset); i > 0; i--) {
			BlockPos offset = worldPosition.below(i);
			BlockState oldState = level.getBlockState(offset);
			level.setBlock(offset, oldState.getFluidState()
				.createLegacyBlock(), Block.UPDATE_CLIENTS | Block.UPDATE_MOVE_BY_PISTON);
		}
	}

	@Override
	public void disassemble() {
		if (!running && movedContraption == null && mirrorParent == null)
			return;
		offset = getGridOffset(offset);
		if (movedContraption != null)
			resetContraptionToOffset();

		if (shouldCreateRopes()) {
			if (offset > 0) {
				BlockPos magnetPos = worldPosition.below((int) offset);
				FluidState ifluidstate = level.getFluidState(magnetPos);
				if (level.getBlockState(magnetPos)
					.getDestroySpeed(level, magnetPos) != -1) {

					level.destroyBlock(magnetPos, level.getBlockState(magnetPos)
						.getCollisionShape(level, magnetPos)
						.isEmpty());
					level.setBlock(magnetPos, PulleyRegistry.PULLEY_MAGNET.defaultBlockState()
							.setValue(BlockStateProperties.WATERLOGGED,
								Boolean.valueOf(ifluidstate.getType() == Fluids.WATER)),
						Block.UPDATE_CLIENTS | Block.UPDATE_MOVE_BY_PISTON);
				}
			}

			boolean[] waterlog = new boolean[(int) offset];

			for (boolean destroyPass : Iterate.trueAndFalse) {
				for (int i = 1; i <= ((int) offset) - 1; i++) {
					BlockPos ropePos = worldPosition.below(i);
					if (level.getBlockState(ropePos)
						.getDestroySpeed(level, ropePos) == -1)
						continue;

					if (destroyPass) {
						FluidState ifluidstate = level.getFluidState(ropePos);
						waterlog[i] = ifluidstate.getType() == Fluids.WATER;
						level.destroyBlock(ropePos, level.getBlockState(ropePos)
							.getCollisionShape(level, ropePos)
							.isEmpty());
						continue;
					}

					level.setBlock(worldPosition.below(i), PulleyRegistry.ROPE.defaultBlockState()
						.setValue(BlockStateProperties.WATERLOGGED, waterlog[i]),
						Block.UPDATE_CLIENTS | Block.UPDATE_MOVE_BY_PISTON);
				}
			}

		}

		if (movedContraption != null && mirrorParent == null)
			movedContraption.disassemble();
		notifyMirrorsOfDisassembly();

		if (movedContraption != null)
			movedContraption.discard();

		movedContraption = null;
		initialOffset = 0;
		running = false;
		sendData();
	}

	protected boolean shouldCreateRopes() {
		return !remove;
	}

	@Override
	protected Vec3 toPosition(float offset) {
		if (movedContraption.getContraption() instanceof PulleyContraption contraption) {
			return Vec3.atLowerCornerOf(contraption.anchor)
				.add(0, contraption.getInitialOffset() - offset, 0);

		}
		return Vec3.ZERO;
	}

	@Override
	protected void visitNewPosition() {
		super.visitNewPosition();
		if (movedContraption != null)
			return;
		if (getSpeed() <= 0)
			return;

		BlockPos posBelow = worldPosition.below((int) (offset + getMovementSpeed()) + 1);
		BlockState state = level.getBlockState(posBelow);
		if (!BlockMovementChecks.isMovementNecessary(state, level, posBelow))
			return;
		if (BlockMovementChecks.isBrittle(state))
			return;

		disassemble();
		assembleNextTick = true;
	}

	@Override
	protected void read(ValueInput compound) {
		initialOffset = compound.getIntOr("InitialOffset", 0);
		// Create reads this although it never writes it, so it is false after every load
		needsContraption = compound.getBooleanOr("NeedsContraption", false);
		super.read(compound);

		BlockPos prevMirrorParent = mirrorParent;
		mirrorParent = compound.read("MirrorParent", BlockPos.CODEC)
			.orElse(null);
		mirrorChildren = null;
		List<BlockPos> children = new ArrayList<>();
		compound.listOrEmpty("MirrorChildren", BlockPos.CODEC)
			.forEach(children::add);
		if (!children.isEmpty())
			mirrorChildren = children;

		if (mirrorParent != null) {
			offset = 0;
			if (prevMirrorParent == null || !prevMirrorParent.equals(mirrorParent))
				sharedMirrorContraption = null;
		}

		if (mirrorParent == null)
			sharedMirrorContraption = null;
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putInt("InitialOffset", initialOffset);
		super.write(compound);

		if (mirrorParent != null)
			compound.store("MirrorParent", BlockPos.CODEC, mirrorParent);
		if (mirrorChildren != null) {
			ValueOutput.TypedOutputList<BlockPos> list = compound.list("MirrorChildren", BlockPos.CODEC);
			mirrorChildren.forEach(list::add);
		}
	}

	public void startMirroringOther(BlockPos parent) {
		if (parent.equals(worldPosition))
			return;
		if (!(level.getBlockEntity(parent) instanceof PulleyBlockEntity pbe))
			return;
		if (pbe.getType() != getType())
			return;
		if (pbe.mirrorChildren == null)
			pbe.mirrorChildren = new ArrayList<>();
		pbe.mirrorChildren.add(worldPosition);
		pbe.notifyUpdate();

		mirrorParent = parent;
		try {
			assemble();
		} catch (AssemblyException e) {
		}
		notifyUpdate();
	}

	public void notifyMirrorsOfDisassembly() {
		if (mirrorChildren == null)
			return;
		for (BlockPos blockPos : mirrorChildren) {
			if (!(level.getBlockEntity(blockPos) instanceof PulleyBlockEntity pbe))
				continue;
			pbe.offset = offset;
			pbe.disassemble();
			pbe.mirrorParent = null;
			pbe.notifyUpdate();
		}
		mirrorChildren.clear();
		notifyUpdate();
	}

	@Override
	protected int getExtensionRange() {
		return Math.max(0, Math.min(ContraptionRegistry.maxRopeLength,
			(worldPosition.getY() - 1) - level.getMinY()));
	}

	@Override
	protected int getInitialOffset() {
		return initialOffset;
	}

	@Override
	protected Vec3 toMotionVector(float speed) {
		return new Vec3(0, -speed, 0);
	}

	@Override
	protected ValueBoxTransform getMovementModeSlot() {
		return new CenteredSideValueBoxTransform((state, d) -> d == Direction.UP);
	}

	@Override
	public float getInterpolatedOffset(float partialTicks) {
		boolean moving = running && (movedContraption == null || !movedContraption.isStalled());
		return super.getInterpolatedOffset(moving ? partialTicks : 0.5f);
	}

	public BlockPos getMirrorParent() {
		return mirrorParent;
	}

	// Threshold switch

	@Override
	public int getCurrentValue() {
		return worldPosition.getY() - (int) getInterpolatedOffset(.5f);
	}

	@Override
	public int getMinValue() {
		return level.getMinY();
	}

	@Override
	public int getMaxValue() {
		return worldPosition.getY();
	}

	@Override
	public MutableComponent format(int value) {
		return Component.translatable("create.gui.threshold_switch.pulley_y_level", value);
	}

}

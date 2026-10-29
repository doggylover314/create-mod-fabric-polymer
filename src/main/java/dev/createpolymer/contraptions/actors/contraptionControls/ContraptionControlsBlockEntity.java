package dev.createpolymer.contraptions.actors.contraptionControls;

import java.util.List;

import org.joml.Matrix4f;

import dev.createpolymer.contraptions.actors.trainControls.ControlsBlock;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's ContraptionControlsBlockEntity (MIT, The Create Team). The button animation runs on the
 * server and is read by the display model.
 */
public class ContraptionControlsBlockEntity extends SmartBlockEntity implements Clearable {
	public FilteringBehaviour filtering;
	public boolean disabled;
	public boolean powered;

	public LerpedFloat indicator;
	public LerpedFloat button;

	public ContraptionControlsBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		indicator = LerpedFloat.angular()
			.startWithValue(0);
		button = LerpedFloat.linear()
			.startWithValue(0)
			.chase(0, 0.125f, Chaser.EXP);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(filtering = new FilteringBehaviour(this, new ControlsSlot()));
		filtering.setLabel(Component.translatable("create.contraptions.contoller.target"));
		filtering.withPredicate(stack -> stack.is(ActorsRegistry.CONTRAPTION_CONTROLLED));
	}

	public void pressButton() {
		button.setValue(1);
	}

	public void updatePoweredState() {
		if (level.isClientSide())
			return;
		boolean powered = level.hasNeighborSignal(worldPosition);
		if (this.powered == powered)
			return;
		this.powered = powered;
		this.disabled = powered;
		notifyUpdate();
	}

	@Override
	public void initialize() {
		super.initialize();
		updatePoweredState();
	}

	@Override
	public void tick() {
		super.tick();
		tickAnimations();
		int value = disabled ? 4 * 45 : 0;
		indicator.setValue(value);
		indicator.updateChaseTarget(value);
	}

	@Override
	public void clearContent() {
		filtering.setFilter(ItemStack.EMPTY);
	}

	public void tickAnimations() {
		button.tickChaser();
		indicator.tickChaser();
	}

	@Override
	protected void read(ValueInput tag) {
		super.read(tag);
		disabled = tag.getBooleanOr("Disabled", false);
		powered = tag.getBooleanOr("Powered", false);
	}

	@Override
	protected void write(ValueOutput tag) {
		super.write(tag);
		tag.putBoolean("Disabled", disabled);
		tag.putBoolean("Powered", powered);
	}

	/** Create's AllSoundEvents.CONTROLLER_CLICK: the item frame sound at .35 volume. */
	public static void playClick(Level level, BlockPos pos, float pitch) {
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .35f, pitch);
	}

	public static void sendStatus(Player player, ItemStack filter, boolean enabled) {
		MutableComponent state = Component.translatable("create.contraption.controls.actor_toggle." + (enabled ? "on" : "off"))
			.withColor(enabled ? DyeColor.LIME.getTextColor() : DyeColor.ORANGE.getTextColor());

		if (filter.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("create.contraption.controls.all_actor_toggle", state));
			return;
		}

		player.sendOverlayMessage(Component.translatable("create.contraption.controls.specific_actor_toggle",
			filter.getHoverName()
				.getString(),
			state));
	}

	public static class ControlsSlot extends ValueBoxTransform {
		@Override
		public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
			Direction facing = state.getValue(ControlsBlock.FACING);
			float yRot = AngleHelper.horizontalAngle(facing);
			return VecHelper.rotateCentered(VecHelper.voxelSpace(8, 14f, 5.5f), yRot, Axis.Y);
		}

		@Override
		public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
			Direction facing = state.getValue(ControlsBlock.FACING);
			float yRot = AngleHelper.horizontalAngle(facing);
			ms.rotateY((float) Math.toRadians(yRot + 180))
				.rotateX((float) Math.toRadians(67.5f));
		}

		@Override
		public float getScale() {
			return .508f;
		}
	}
}

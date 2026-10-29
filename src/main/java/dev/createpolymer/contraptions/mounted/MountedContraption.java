package dev.createpolymer.contraptions.mounted;

import static dev.createpolymer.contraptions.mounted.CartAssemblerBlock.RAIL_SHAPE;

import java.util.Queue;

import org.apache.commons.lang3.tuple.Pair;

import dev.createpolymer.api.contraption.ContraptionMovementSetting;
import dev.createpolymer.api.contraption.ContraptionType;
import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.mounted.CartAssemblerBlockEntity.CartMovementMode;
import dev.createpolymer.foundation.item.handler.InvWrapper;
import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.NBTHelper;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;

public class MountedContraption extends Contraption {

	public CartMovementMode rotationMode;
	public AbstractMinecart connectedCart;

	public MountedContraption() {
		this(CartMovementMode.ROTATE);
	}

	public MountedContraption(CartMovementMode mode) {
		rotationMode = mode;
	}

	@Override
	public ContraptionType getType() {
		return MinecartRegistry.MOUNTED;
	}

	@Override
	public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
		BlockState state = world.getBlockState(pos);
		if (!state.hasProperty(RAIL_SHAPE))
			return false;
		if (!searchMovedStructure(world, pos, null))
			return false;

		Axis axis = state.getValue(RAIL_SHAPE) == RailShape.EAST_WEST ? Axis.X : Axis.Z;
		addBlock(world, pos, Pair.of(new StructureBlockInfo(pos, MinecartRegistry.MINECART_ANCHOR.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis), null), null));

		if (blocks.size() == 1)
			return false;

		return true;
	}

	@Override
	protected boolean addToInitialFrontier(Level world, BlockPos pos, Direction direction, Queue<BlockPos> frontier) {
		frontier.clear();
		frontier.add(pos.above());
		return true;
	}

	@Override
	protected Pair<StructureBlockInfo, BlockEntity> capture(Level world, BlockPos pos) {
		Pair<StructureBlockInfo, BlockEntity> pair = super.capture(world, pos);
		StructureBlockInfo capture = pair.getKey();
		if (!capture.state().is(MinecartRegistry.CART_ASSEMBLER))
			return pair;

		Pair<StructureBlockInfo, BlockEntity> anchorSwap =
			Pair.of(new StructureBlockInfo(pos, CartAssemblerBlock.createAnchor(capture.state()), null), pair.getValue());
		if (pos.equals(anchor) || connectedCart != null)
			return anchorSwap;

		for (Axis axis : Iterate.axes) {
			if (axis.isVertical() || !VecHelper.onSameAxis(anchor, pos, axis))
				continue;
			for (AbstractMinecart abstractMinecartEntity : world.getEntitiesOfClass(AbstractMinecart.class,
				new AABB(pos))) {
				if (!CartAssemblerBlock.canAssembleTo(abstractMinecartEntity))
					break;
				connectedCart = abstractMinecartEntity;
				connectedCart.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
			}
		}

		return anchorSwap;
	}

	@Override
	protected boolean movementAllowed(BlockState state, Level world, BlockPos pos) {
		if (!pos.equals(anchor) && state.is(MinecartRegistry.CART_ASSEMBLER))
			return testSecondaryCartAssembler(world, state, pos);
		return super.movementAllowed(state, world, pos);
	}

	protected boolean testSecondaryCartAssembler(Level world, BlockState state, BlockPos pos) {
		for (Axis axis : Iterate.axes) {
			if (axis.isVertical() || !VecHelper.onSameAxis(anchor, pos, axis))
				continue;
			for (AbstractMinecart abstractMinecartEntity : world.getEntitiesOfClass(AbstractMinecart.class,
				new AABB(pos))) {
				if (!CartAssemblerBlock.canAssembleTo(abstractMinecartEntity))
					break;
				return true;
			}
		}
		return false;
	}

	@Override
	public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
		CompoundTag tag = super.writeNBT(registries, spawnPacket);
		NBTHelper.writeEnum(tag, "RotationMode", rotationMode);
		return tag;
	}

	@Override
	public void readNBT(Level world, CompoundTag nbt, boolean spawnData) {
		rotationMode = NBTHelper.readEnum(nbt, "RotationMode", CartMovementMode.class);
		super.readNBT(world, nbt, spawnData);
	}

	@Override
	protected boolean customBlockPlacement(LevelAccessor world, BlockPos pos, BlockState state) {
		return state.is(MinecartRegistry.MINECART_ANCHOR);
	}

	@Override
	protected boolean customBlockRemoval(LevelAccessor world, BlockPos pos, BlockState state) {
		return state.is(MinecartRegistry.MINECART_ANCHOR);
	}

	@Override
	public boolean canBeStabilized(Direction facing, BlockPos localPos) {
		return true;
	}

	@Override
	public boolean isRotationLocked() {
		return rotationMode == CartMovementMode.ROTATION_LOCKED;
	}

	@Override
	public boolean pausesActorsWhileRotating() {
		return rotationMode == CartMovementMode.ROTATE_PAUSED;
	}

	@Override
	public boolean forbidsPickup() {
		return ContraptionMovementSetting.isNoPickup(blocks.values());
	}

	@Override
	public boolean isMountedOnCart() {
		return true;
	}

	@Override
	public void addExtraInventories(Entity cart) {
		if (cart instanceof Container container)
			storage.attachExternal(new InvWrapper(container));
	}

}

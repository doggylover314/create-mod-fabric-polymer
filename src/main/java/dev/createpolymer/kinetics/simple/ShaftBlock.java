package dev.createpolymer.kinetics.simple;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.girder.GirderEncasedShaftBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.feature.DoorsRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.ModelItems;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's ShaftBlock (MIT, The Create Team). */
public class ShaftBlock extends AbstractSimpleShaftBlock implements PolymerTexturedBlock {
	public static final Map<Axis, VoxelShape> SHAPE = ShapeUtil.forAxis(ShapeUtil.box(5, 0, 5, 11, 16, 11));

	// Invisible lightning-rod states: same thin hitbox as a shaft, no visible model.
	private static final Map<Axis, BlockState> CLIENT_DRY = new java.util.EnumMap<>(Axis.class);
	private static final Map<Axis, BlockState> CLIENT_WET = new java.util.EnumMap<>(Axis.class);

	public ShaftBlock(Properties properties) {
		super(properties);
	}

	public static void requestClientStates() {
		for (Axis axis : Axis.values()) {
			CLIENT_DRY.put(axis, PolymerBlockResourceUtils.requestEmpty(BlockModelType.valueOf("LIGHTNING_ROD_" + axis.name())));
			CLIENT_WET.put(axis, PolymerBlockResourceUtils.requestEmpty(BlockModelType.valueOf("LIGHTNING_ROD_" + axis.name() + "_WATERLOGGED")));
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPE.get(state.getValue(AXIS));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		Map<Axis, BlockState> map = state.getValue(BlockStateProperties.WATERLOGGED) ? CLIENT_WET : CLIENT_DRY;
		BlockState client = map.get(state.getValue(AXIS));
		return client != null ? client : super.getPolymerBlockState(state, context);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hitResult) {
		if (player.isShiftKeyDown() || !player.mayBuild())
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		InteractionResult result = super.useItemOn(stack, state, level, pos, player, hand, hitResult);
		if (result.consumesAction())
			return result;

		if (stack.is(DoorsRegistry.METAL_GIRDER_ITEM) && state.getValue(AXIS) != Axis.Y) {
			KineticBlockEntity.switchToBlockState(level, pos, DoorsRegistry.METAL_GIRDER_ENCASED_SHAFT.defaultBlockState()
				.setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED))
				.setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS, state.getValue(AXIS) == Axis.Z ? Axis.Z : Axis.X));
			if (!level.isClientSide() && !player.isCreative()) {
				stack.shrink(1);
				if (stack.isEmpty())
					player.setItemInHand(hand, ItemStack.EMPTY);
			}
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected @Nullable BlockState getEncasedState(net.minecraft.world.item.ItemStack held, BlockState state,
												   net.minecraft.world.level.Level level, BlockPos pos) {
		if (held.is(dev.createpolymer.registry.ModItems.ANDESITE_CASING))
			return dev.createpolymer.registry.ModBlocks.ANDESITE_ENCASED_SHAFT.defaultBlockState().setValue(AXIS, state.getValue(AXIS));
		return null;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return model(state, ModelItems.block("shaft"), false);
	}
}

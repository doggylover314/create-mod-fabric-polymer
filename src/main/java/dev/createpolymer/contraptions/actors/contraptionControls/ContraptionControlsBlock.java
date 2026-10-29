package dev.createpolymer.contraptions.actors.contraptionControls;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.actors.trainControls.ControlsBlock;
import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.createpolymer.util.VoxelShaper;
import net.minecraft.core.Direction;

/** Ported from Create's ContraptionControlsBlock (MIT, The Create Team). */
public class ContraptionControlsBlock extends ControlsBlock implements IBE<ContraptionControlsBlockEntity>, ContraptionDisplay {

	/** Create's AllShapes.CONTRAPTION_CONTROLS and CONTRAPTION_CONTROLS_COLLISION */
	private static final VoxelShaper SHAPE = VoxelShaper.forHorizontal(Shapes.or(Shapes.box(0, 0, 6 / 16d, 2 / 16d, 1, 1),
		Shapes.box(14 / 16d, 0, 6 / 16d, 1, 1, 1), Shapes.box(0, 0, 14 / 16d, 1, 1, 1),
		Shapes.box(0, 0, 6 / 16d, 1, 12 / 16d, 1), Shapes.box(0, 0, 4 / 16d, 1, 2 / 16d, 1)), Direction.NORTH);
	private static final VoxelShaper COLLISION = VoxelShaper.forHorizontal(Shapes.or(
		Shapes.box(0, 0, 6 / 16d, 2 / 16d, 1, 1), Shapes.box(14 / 16d, 0, 6 / 16d, 1, 1, 1),
		Shapes.box(0, 0, 14 / 16d, 1, 1, 1), Shapes.box(0, 0, 7 / 16d, 1, 12 / 16d, 1)), Direction.NORTH);

	public ContraptionControlsBlock(Properties pProperties) {
		super(pProperties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hitResult) {
		return onBlockEntityUse(level, pos, cte -> {
			cte.pressButton();
			if (!level.isClientSide()) {
				cte.disabled = !cte.disabled;
				cte.notifyUpdate();
				ContraptionControlsBlockEntity.sendStatus(player, cte.filtering.getFilter(), !cte.disabled);
				ContraptionControlsBlockEntity.playClick(cte.getLevel(), cte.getBlockPos(), cte.disabled ? 0.8f : 1.5f);
			}
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	protected void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean pIsMoving) {
		withBlockEntityDo(pLevel, pPos, ContraptionControlsBlockEntity::updatePoweredState);
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return SHAPE.get(pState.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		return COLLISION.get(pState.getValue(FACING));
	}

	@Override
	public Class<ContraptionControlsBlockEntity> getBlockEntityClass() {
		return ContraptionControlsBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ContraptionControlsBlockEntity> getBlockEntityType() {
		return ActorsRegistry.CONTRAPTION_CONTROLS_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		var orientation = Orientation.forHorizontal(state.getValue(FACING));
		return List.of(new Piece(ModelItems.block("contraption_controls/block"), orientation),
			new Piece(ModelItems.block("contraption_controls/button"), orientation),
			new Piece(ModelItems.block("contraption_controls/indicator_0"), orientation));
	}

	/** The panel, its button (which dips when pressed) and the indicator that turns when the actors are off. */
	public static class Model extends BaseModel {
		private final Part panel;
		private final Part button;
		private final Part indicator;
		private final FilterDisplay filter = new FilterDisplay(this);
		private boolean disabledShown;
		private float buttonShown = -1;

		public Model(BlockState state) {
			super(state);
			panel = addPart(ModelItems.block("contraption_controls/block"));
			button = addPart(ModelItems.block("contraption_controls/button"));
			indicator = addPart(ModelItems.block("contraption_controls/indicator_0"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			var orientation = Orientation.forHorizontal(state.getValue(FACING));
			panel.orientation(orientation).apply(0);
			button.orientation(orientation).apply(0);
			indicator.orientation(orientation).apply(0);
			buttonShown = -1;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof ContraptionControlsBlockEntity controls))
				return;
			filter.update((SmartBlockEntity) be);

			if (controls.disabled != disabledShown) {
				disabledShown = controls.disabled;
				indicator.setItem(ModelItems.block("contraption_controls/indicator_" + (disabledShown ? 4 : 0)));
			}

			float depth = controls.button.getValue();
			if (depth != buttonShown) {
				buttonShown = depth;
				// Create moves the button along the axis (0, 1, -0.325) of the panel
				Vec3 axis = VecHelper.rotate(new Vec3(0, 1, -.325),
					dev.createpolymer.util.AngleHelper.horizontalAngle(state.getValue(FACING).getOpposite()), Axis.Y);
				Vec3 movement = axis.scale(-1 / 24f * depth);
				button.translation((float) movement.x, (float) movement.y, (float) movement.z);
				button.apply(1);
			}
		}
	}
}

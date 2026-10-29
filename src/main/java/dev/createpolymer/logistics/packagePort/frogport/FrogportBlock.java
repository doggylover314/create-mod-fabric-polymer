package dev.createpolymer.logistics.packagePort.frogport;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.box.PackageStyles.PackageStyle;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FrogportBlock extends Block implements IBE<FrogportBlockEntity>, IWrenchable, PolymerModelBlock {

	/** Create's AllShapes.PACKAGE_PORT */
	public static final VoxelShape PACKAGE_PORT = Shapes.or(ShapeUtil.box(0, 0, 0, 16, 4, 16),
		ShapeUtil.box(2, 2, 2, 14, 14, 14));

	public FrogportBlock(Properties pProperties) {
		super(pProperties);
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return PACKAGE_PORT;
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		return PACKAGE_PORT;
	}

	@Override
	public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer,
		ItemStack pStack) {
		super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
		if (pPlacer == null)
			return;
		withBlockEntityDo(pLevel, pPos, be -> {
			Vec3 diff = VecHelper.getCenterOf(pPos)
				.subtract(pPlacer.position());
			be.passiveYaw = (float) (Mth.atan2(diff.x, diff.z) * Mth.RAD_TO_DEG);
			be.passiveYaw = Math.round(be.passiveYaw / 11.25f) * 11.25f;
			be.notifyUpdate();
		});
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		return onBlockEntityUseItemOn(level, pos, be -> be.use(player));
	}

	@Override
	public Class<FrogportBlockEntity> getBlockEntityClass() {
		return FrogportBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FrogportBlockEntity> getBlockEntityType() {
		return PackagesRegistry.PACKAGE_FROGPORT_BLOCK_ENTITY;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState pState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState pState, Level pLevel, BlockPos pPos,
		net.minecraft.core.Direction direction) {
		return getBlockEntityOptional(pLevel, pPos).map(pbe -> pbe.getComparatorOutput())
			.orElse(0);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.TERRACOTTA.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * The frog (Create's FrogportRenderer / FrogportVisual): body, head and tongue as separate parts, plus the
	 * package on the tongue. The head pitches and the tongue stretches and tilts towards the target as the
	 * animation progresses.
	 */
	public static class Model extends BaseModel {
		private static final float PIVOT_Y = 10 / 16f - .5f, PIVOT_Z = 11 / 16f - .5f;

		private final Part base;
		private final Part body;
		private final Part head;
		private final Part tongue;
		private final Part box;
		private final Part rig;
		private final ItemStack headItem = ModelItems.block("package_frogport/head");
		private final ItemStack headGogglesItem = ModelItems.block("package_frogport/head_goggles");
		private boolean lastGoggles;
		private float lastYaw = Float.NaN;
		private final Matrix4f[] last = new Matrix4f[5];
		private ItemStack shownPackage = ItemStack.EMPTY;
		private ItemStack shownRig = ItemStack.EMPTY;

		public Model(BlockState state) {
			super(state);
			base = addPart(ModelItems.block("package_frogport/block"));
			body = addPart(ModelItems.block("package_frogport/body"));
			head = addPart(headItem);
			tongue = addPart(ModelItems.block("package_frogport/tongue"));
			box = addPart(ItemStack.EMPTY);
			rig = addPart(ItemStack.EMPTY);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(base, "package_frogport", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof FrogportBlockEntity port))
				return;

			float partialTicks = 1;
			float yaw = port.getYaw();

			float headPitch = 80;
			float tonguePitch = 0;
			float tongueLength = 0;
			float headPitchModifier = 1;

			boolean hasTarget = port.target != null;
			boolean animating = port.isAnimationInProgress();
			boolean depositing = port.currentlyDepositing;

			Vec3 diff = Vec3.ZERO;

			if (hasTarget) {
				diff = port.target
					.getExactTargetLocation(port, level, pos)
					.subtract(0, animating && depositing ? 0 : 0.75, 0)
					.subtract(Vec3.atCenterOf(pos));
				tonguePitch = (float) Mth.atan2(diff.y, diff.multiply(1, 0, 1)
					.length() + (3 / 16f)) * Mth.RAD_TO_DEG;
				tongueLength = Math.max((float) diff.length(), 1);
				headPitch = Mth.clamp(tonguePitch * 2, 60, 100);
			}

			Matrix4f boxMatrix = null;
			ItemStack wantedPackage = ItemStack.EMPTY;
			if (animating) {
				float progress = port.animationProgress.getValue(partialTicks);
				float scale = 1;
				float itemDistance = 0;

				if (depositing) {
					double modifier = Math.max(0, 1 - Math.pow((progress - 0.25) * 4 - 1, 4));
					itemDistance =
						(float) Math.max(tongueLength * Math.min(1, (progress - 0.25) * 3), tongueLength * modifier);
					tongueLength *= Math.max(0, 1 - Math.pow((progress * 1.25 - 0.25) * 4 - 1, 4));
					headPitchModifier = (float) Math.max(0, 1 - Math.pow((progress * 1.25) * 2 - 1, 4));
					scale = 0.25f + progress * 3 / 4;

				} else {
					tongueLength *= Math.pow(Math.max(0, 1 - progress * 1.25), 5);
					headPitchModifier = 1 - (float) Math.min(1, Math.max(0, (Math.pow(progress * 1.5, 2) - 0.5) * 2));
					scale = (float) Math.max(0.5, 1 - progress * 1.25);
					itemDistance = tongueLength;
				}

				if (PackageItem.isPackage(port.renderedPackage) && scale >= 0.45) {
					wantedPackage = port.renderedPackage;
					Vec3 offset = diff.normalize()
						.scale(itemDistance)
						.add(0, 3 / 16f - (depositing ? 0.75 : 0), 0);
					boxMatrix = new Matrix4f().translate((float) offset.x, (float) offset.y, (float) offset.z)
						.scale(scale);
				}

			} else {
				tongueLength = 0;
				float anticipation = port.anticipationProgress.getValue(partialTicks);
				headPitchModifier =
					anticipation > 0 ? (float) Math.max(0, 1 - Math.pow((anticipation * 1.25) * 2 - 1, 4)) : 0;
			}

			headPitch *= headPitchModifier;

			headPitch = Math.max(headPitch, port.manualOpenAnimationProgress.getValue(partialTicks) * 60);
			tongueLength = Math.max(tongueLength, port.manualOpenAnimationProgress.getValue(partialTicks) * 0.25f);

			if (port.goggles != lastGoggles) {
				lastGoggles = port.goggles;
				head.setItem(lastGoggles ? headGogglesItem : headItem);
			}

			Matrix4f yawM = new Matrix4f().rotateY((float) Math.toRadians(yaw));
			Matrix4f bodyM = new Matrix4f(yawM);
			Matrix4f headM = new Matrix4f(yawM).translate(0, PIVOT_Y, PIVOT_Z)
				.rotateX((float) Math.toRadians(headPitch))
				.translate(0, -PIVOT_Y, -PIVOT_Z);
			Matrix4f tongueM = new Matrix4f(yawM).translate(0, PIVOT_Y, PIVOT_Z)
				.rotateX((float) Math.toRadians(tonguePitch))
				.scale(1f, 1f, tongueLength / (7 / 16f))
				.translate(0, -PIVOT_Y, -PIVOT_Z);

			send(0, body, bodyM);
			send(1, head, headM);
			send(2, tongue, tongueM);

			// The package and, when the frog puts one down, the rope rigging around it
			if (!ItemStack.isSameItem(shownPackage, wantedPackage)) {
				shownPackage = wantedPackage.isEmpty() ? ItemStack.EMPTY : new ItemStack(wantedPackage.getItem());
				box.setItem(shownPackage);
			}
			ItemStack wantedRig = ItemStack.EMPTY;
			if (depositing && boxMatrix != null && wantedPackage.getItem() instanceof PackageItem pi) {
				PackageStyle style = pi.style;
				wantedRig = ModelItems.of(style.getRiggingModel());
			}
			if (!ItemStack.isSameItem(shownRig, wantedRig)
				|| !ItemStack.isSameItemSameComponents(shownRig, wantedRig)) {
				shownRig = wantedRig;
				rig.setItem(wantedRig);
			}
			if (boxMatrix != null) {
				send(3, box, boxMatrix);
				if (!wantedRig.isEmpty())
					send(4, rig, boxMatrix);
			}
		}

		private void send(int index, Part part, Matrix4f matrix) {
			if (matrix.equals(last[index]))
				return;
			boolean fresh = last[index] == null;
			last[index] = new Matrix4f(matrix);
			part.setMatrix(matrix, fresh ? 0 : 1);
		}
	}
}

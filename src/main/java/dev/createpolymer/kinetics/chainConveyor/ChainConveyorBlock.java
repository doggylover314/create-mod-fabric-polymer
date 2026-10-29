package dev.createpolymer.kinetics.chainConveyor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.KineticBlock;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorPackage.ChainConveyorPackagePhysicsData;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.registry.feature.ChainConveyorRegistry;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChainConveyorBlock extends KineticBlock implements IBE<ChainConveyorBlockEntity>, IWrenchable {

	public ChainConveyorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return Axis.Y;
	}

	/**
	 * Create's CHAIN_CONVEYOR_INTERACTION is a shape three blocks wide, for the outline players see. Players here
	 * see the shape of a barrier, and the server finds the chains itself, so the shape stays inside the block.
	 */
	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return Shapes.block();
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (!level.isClientSide() && stack.is(Items.IRON_CHAIN))
			return InteractionResult.SUCCESS;
		if (stack.is(PackagesRegistry.PACKAGE_FROGPORT_ITEM))
			return InteractionResult.SUCCESS;
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	public BlockState playerWillDestroy(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
		super.playerWillDestroy(pLevel, pPos, pState, pPlayer);
		if (pLevel.isClientSide())
			return pState;
		if (!pPlayer.isCreative())
			return pState;
		withBlockEntityDo(pLevel, pPos, be -> be.cancelDrops = true);
		return pState;
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null)
			return null;

		withBlockEntityDo(context.getLevel(), context.getClickedPos(), be -> {
			be.cancelDrops = true;
			if (player.isCreative())
				return;
			for (BlockPos targetPos : be.connections) {
				int chainCost = ChainConveyorBlockEntity.getChainCost(targetPos);
				while (chainCost > 0) {
					player.getInventory()
						.placeItemBackInInventory(new ItemStack(Items.IRON_CHAIN, Math.min(chainCost, 64)),
							net.minecraft.util.Prediction.SERVER_ONLY);
					chainCost -= 64;
				}
			}
		});

		return null;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		for (int x = -1; x <= 1; x++)
			for (int z = -1; z <= 1; z++)
				if (pContext.getLevel()
					.getBlockState(pContext.getClickedPos()
						.offset(x, 0, z))
					.getBlock() == this)
					return null;

		return super.getStateForPlacement(pContext);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		return Shapes.block();
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == getRotationAxis(state);
	}

	@Override
	public Class<ChainConveyorBlockEntity> getBlockEntityClass() {
		return ChainConveyorBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ChainConveyorBlockEntity> getBlockEntityType() {
		return ChainConveyorRegistry.CHAIN_CONVEYOR_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * Create's ChainConveyorVisual and ChainConveyorRenderer: the casing, the shaft and wheel, a guard and a chain
	 * for every connection, and the packages hanging on rigging from the chains. The chains do not scroll: each is a
	 * row of vanilla chain blocks. The boxes sway as Create's do (the physics run in {@link ChainConveyorBlockEntity#tickBoxVisuals}).
	 */
	public static class Model extends BaseModel {
		private static final BlockState CHAIN_LINK = Blocks.IRON_CHAIN.defaultBlockState()
			.setValue(BlockStateProperties.AXIS, Axis.Z);

		private final Part shaft;
		private final List<Part> guards = new ArrayList<>();
		private final List<BlockDisplayElement> links = new ArrayList<>();
		private Map<BlockPos, ConnectionStats> shownStats = new HashMap<>();
		private final Map<ChainConveyorPackage, PackageVisual> packages = new IdentityHashMap<>();
		private final ItemStack guardItem = ModelItems.block("chain_conveyor/guard");

		public Model(BlockState state) {
			super(state);
			addPart(ModelItems.block("chain_conveyor/block")).apply(0);
			shaft = addPart(ModelItems.block("chain_conveyor/shaft"));
			shaft.spinAxis(Axis.Y).apply(0);
			addPart(ModelItems.block("chain_conveyor/wheel")).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof ChainConveyorBlockEntity ccbe))
				return;
			shaft.spin(time, ccbe.getSpeed(), Orientation.cogOffset(false, Axis.Y, pos.getX(), pos.getY(), pos.getZ()));
			ccbe.prepareStats();
			syncChains(ccbe, pos);
			ccbe.tickBoxVisuals();
			syncPackages(ccbe, pos);
		}

		private void syncChains(ChainConveyorBlockEntity be, BlockPos pos) {
			if (be.connectionStats.equals(shownStats))
				return;
			shownStats = new HashMap<>(be.connectionStats);

			for (Part guard : guards)
				removeElement(guard.element);
			guards.clear();
			for (BlockDisplayElement link : links)
				removeElement(link);
			links.clear();

			Vec3 center = Vec3.atCenterOf(pos);
			for (Entry<BlockPos, ConnectionStats> entry : shownStats.entrySet()) {
				ConnectionStats stats = entry.getValue();
				Vec3 diff = stats.end()
					.subtract(stats.start());
				double yaw = Mth.RAD_TO_DEG * Mth.atan2(diff.x, diff.z);
				double pitch = Mth.RAD_TO_DEG * Mth.atan2(diff.y, diff.multiply(1, 0, 1)
					.length());

				Part guard = addPart(guardItem);
				guard.orientation(new Quaternionf().rotationY((float) Math.toRadians(yaw)))
					.apply(0);
				guards.add(guard);

				int segments = Math.max(1, Math.round(stats.chainLength()));
				float segmentLength = stats.chainLength() / segments;
				Vec3 direction = diff.normalize();
				for (int i = 0; i < segments; i++) {
					Vec3 middle = stats.start()
						.add(direction.scale((i + .5) * segmentLength))
						.subtract(center);
					Matrix4f m = new Matrix4f().translate((float) middle.x, (float) middle.y, (float) middle.z)
						.rotateY((float) Math.toRadians(yaw))
						.rotateX((float) -Math.toRadians(pitch))
						.scale(1, 1, segmentLength)
						.translate(-.5f, -.5f, -.5f);
					BlockDisplayElement link = new BlockDisplayElement(CHAIN_LINK);
					link.setTransformation(m);
					link.setViewRange(2f);
					link.setInterpolationDuration(0);
					addElement(link);
					links.add(link);
				}
			}
		}

		private void syncPackages(ChainConveyorBlockEntity be, BlockPos pos) {
			Vec3 center = Vec3.atCenterOf(pos);
			Map<ChainConveyorPackage, PackageVisual> unseen = new IdentityHashMap<>(packages);

			for (ChainConveyorPackage box : be.loopingPackages)
				showPackage(box, center, unseen);
			for (List<ChainConveyorPackage> list : be.travellingPackages.values())
				for (ChainConveyorPackage box : list)
					showPackage(box, center, unseen);

			for (Entry<ChainConveyorPackage, PackageVisual> entry : unseen.entrySet()) {
				entry.getValue()
					.remove(this);
				packages.remove(entry.getKey());
			}
		}

		private void showPackage(ChainConveyorPackage box, Vec3 center, Map<ChainConveyorPackage, PackageVisual> unseen) {
			if (box.worldPosition == null)
				return;
			if (box.item == null || box.item.isEmpty() || !(box.item.getItem() instanceof PackageItem packageItem))
				return;
			ChainConveyorPackagePhysicsData physicsData = box.existingPhysicsData();
			if (physicsData == null || physicsData.pos == null || physicsData.targetPos == null)
				return;

			PackageVisual visual = packages.get(box);
			if (visual == null) {
				visual = new PackageVisual(packageItem);
				visual.add(this);
				packages.put(box, visual);
			}
			unseen.remove(box);
			visual.update(box, physicsData, center);
		}
	}

	/** The box of a package and the rigging it hangs from. */
	private static class PackageVisual {
		private final Part box;
		private final Part rig;
		private boolean fresh = true;
		private Matrix4f lastBox, lastRig;

		PackageVisual(PackageItem item) {
			box = new Part(new ItemStack(item));
			rig = new Part(ModelItems.of(item.style.getRiggingModel()));
			box.element.setViewRange(2f);
			rig.element.setViewRange(2f);
		}

		void add(Model model) {
			model.addElement(box.element);
			model.addElement(rig.element);
		}

		void remove(Model model) {
			model.removeElement(box.element);
			model.removeElement(rig.element);
		}

		void update(ChainConveyorPackage pckg, ChainConveyorPackagePhysicsData physicsData, Vec3 center) {
			Vec3 position = physicsData.pos;
			Vec3 targetPosition = physicsData.targetPos;
			float yaw = physicsData.yaw;
			Vec3 offset = targetPosition.subtract(center);

			Vec3 dangleDiff = VecHelper.rotate(targetPosition.add(0, 0.5, 0)
				.subtract(position), -yaw, Axis.Y);
			float zRot = Mth.wrapDegrees((float) Mth.atan2(-dangleDiff.x, dangleDiff.y) * Mth.RAD_TO_DEG) / 2;
			float xRot = Mth.wrapDegrees((float) Mth.atan2(dangleDiff.z, dangleDiff.y) * Mth.RAD_TO_DEG) / 2;
			zRot = Mth.clamp(zRot, -25, 25);
			xRot = Mth.clamp(xRot, -25, 25);

			float hook = -PackageItem.getHookDistance(pckg.item) + 7 / 16f;
			Matrix4f boxM = new Matrix4f().translate((float) offset.x, (float) offset.y, (float) offset.z)
				.translate(0, 10 / 16f, 0)
				.rotateY((float) Math.toRadians(yaw))
				.rotateZ((float) Math.toRadians(zRot))
				.rotateX((float) Math.toRadians(xRot));
			Matrix4f rigM = new Matrix4f(boxM);
			if (physicsData.flipped)
				rigM.rotateY((float) Math.toRadians(180));
			boxM.translate(0, hook, 0);
			rigM.translate(0, hook, 0);

			int interpolation = fresh ? 0 : 1;
			if (!boxM.equals(lastBox)) {
				lastBox = boxM;
				box.setMatrix(boxM, interpolation);
			}
			if (!rigM.equals(lastRig)) {
				lastRig = rigM;
				rig.setMatrix(rigM, interpolation);
			}
			fresh = false;
		}
	}

}

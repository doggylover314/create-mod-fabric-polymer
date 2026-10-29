package dev.createpolymer.schematics.cannon;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.registry.feature.SchematicsRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SchematicannonBlock extends Block implements IBE<SchematicannonBlockEntity>, PolymerModelBlock {

	/** Create's AllShapes.SCHEMATICANNON_SHAPE */
	public static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
		ShapeUtil.box(1, 0, 1, 15, 8, 15), ShapeUtil.box(0.5, 8, 0.5, 15.5, 11, 15.5));

	public SchematicannonBlock(Properties properties) {
		super(properties);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack stack) {
		super.setPlacedBy(level, pos, state, entity, stack);
		if (entity != null) {
			withBlockEntityDo(level, pos, be -> {
				be.defaultYaw = (-Mth.floor((entity.getYRot() + (entity.isShiftKeyDown() ? 180.0F : 0.0F)) * 16.0F / 360.0F + 0.5F) & 15) * 360.0F / 16.0F;
			});
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.SUCCESS;
		withBlockEntityDo(level, pos, be -> be.openGui(serverPlayer));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
		@Nullable Orientation orientation, boolean isMoving) {
		withBlockEntityDo(worldIn, pos, be -> be.neighbourCheckCooldown = 0);
	}

	@Override
	public Class<SchematicannonBlockEntity> getBlockEntityClass() {
		return SchematicannonBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SchematicannonBlockEntity> getBlockEntityType() {
		return SchematicsRegistry.SCHEMATICANNON_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.SPRUCE_LOG.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * The cannon: its base, the connector that turns to the target and the pipe that also tilts and slides back when
	 * a shot leaves (Create's SchematicannonRenderer), with the launched blocks flying along their arc.
	 */
	public static class Model extends BaseModel {
		private final Part base;
		private final Part connector;
		private final Part pipe;
		private final Map<LaunchedItem, DisplayElement> flying = new IdentityHashMap<>();

		private double lastYaw = Double.NaN;
		private double lastPitch = Double.NaN;
		private double lastRecoil = Double.NaN;

		public Model(BlockState state) {
			super(state);
			base = addPart(ModelItems.block("schematicannon/block"));
			connector = addPart(ModelItems.block("schematicannon/connector"));
			pipe = addPart(ModelItems.block("schematicannon/pipe"));
			base.apply(0);
			animate(40, 0, 0);
		}

		/** Create's block space (0..1 around the corner) to the centred space of the displays */
		private static Matrix4f fromBlockSpace(Matrix4f blockSpace) {
			return new Matrix4f().translate(-.5f, -.5f, -.5f)
				.mul(blockSpace)
				.translate(.5f, .5f, .5f);
		}

		private void animate(double yaw, double pitch, double recoil) {
			if (yaw != lastYaw) {
				Matrix4f m = new Matrix4f().translate(.5f, 0, .5f)
					.rotateY((float) ((yaw + 90) / 180 * Math.PI))
					.translate(-.5f, 0, -.5f);
				connector.setMatrix(fromBlockSpace(m), 1);
			}

			if (pitch != lastPitch || recoil != lastRecoil || yaw != lastYaw) {
				Matrix4f m = new Matrix4f().translate(.5f, 15 / 16f, .5f)
					.rotateY((float) ((yaw + 90) / 180 * Math.PI))
					.rotateZ((float) (pitch / 180 * Math.PI))
					.translate(-.5f, -15 / 16f, -.5f)
					.translate(0, (float) (-recoil / 100), 0);
				pipe.setMatrix(fromBlockSpace(m), 1);
			}

			lastYaw = yaw;
			lastPitch = pitch;
			lastRecoil = recoil;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof SchematicannonBlockEntity cannon))
				return;
			double[] cannonAngles = getCannonAngles(cannon, pos);
			animate(cannonAngles[0], cannonAngles[1], getRecoil(cannon));
			updateLaunchedBlocks(cannon, pos);
		}

		private void updateLaunchedBlocks(SchematicannonBlockEntity cannon, BlockPos pos) {
			Iterator<Map.Entry<LaunchedItem, DisplayElement>> it = flying.entrySet()
				.iterator();
			while (it.hasNext()) {
				Map.Entry<LaunchedItem, DisplayElement> entry = it.next();
				if (!cannon.flyingBlocks.contains(entry.getKey()) || entry.getKey().ticksRemaining == 0) {
					removeElement(entry.getValue());
					it.remove();
				}
			}

			for (LaunchedItem launched : cannon.flyingBlocks) {
				if (launched.ticksRemaining == 0)
					continue;
				DisplayElement element = flying.get(launched);
				boolean fresh = element == null;
				if (fresh) {
					element = createElement(launched);
					flying.put(launched, element);
					element.setViewRange(2f);
					element.setTeleportDuration(0);
					element.setInterpolationDuration(0);
				}

				float t = progress(launched);
				Matrix4f m = new Matrix4f().translate(flightPosition(pos, launched).toVector3f())
					.translate(.125f, .125f, .125f)
					.rotateY((float) Math.toRadians(360 * t))
					.rotateX((float) Math.toRadians(360 * t))
					.translate(-.125f, -.125f, -.125f)
					.scale(element instanceof BlockDisplayElement ? .3f : 1.2f);
				element.setTransformation(m);
				element.setInterpolationDuration(fresh ? 0 : 1);
				element.startInterpolation();
				if (fresh)
					addElement(element);
			}
		}

		private static DisplayElement createElement(LaunchedItem launched) {
			if (launched instanceof LaunchedItem.ForBlockState forBlock && !(launched instanceof LaunchedItem.ForBelt)
				&& isVanilla(forBlock.state.getBlock()))
				return new BlockDisplayElement(forBlock.state);

			// Create's blocks are not blocks the player's game knows: what flies is the item of the block
			ItemStack icon = launched.stack.isEmpty() && launched instanceof LaunchedItem.ForBlockState forBlock
				? new ItemStack(forBlock.state.getBlock()
					.asItem())
				: launched.stack.copy();
			if (launched instanceof LaunchedItem.ForBelt)
				icon = new ItemStack(dev.createpolymer.registry.ModItems.SHAFT);
			ItemDisplayElement element = new ItemDisplayElement(icon.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.STONE) : icon);
			element.setItemDisplayContext(ItemDisplayContext.GROUND);
			return element;
		}

		private static boolean isVanilla(Block block) {
			return BuiltInRegistries.BLOCK.getKey(block)
				.getNamespace()
				.equals("minecraft") && !(block instanceof PolymerBlock);
		}

		/** The share of the flight a launched block has covered */
		private static float progress(LaunchedItem launched) {
			return ((float) launched.totalTicks - (launched.ticksRemaining + 1 - 1f)) / launched.totalTicks;
		}

		/** Create's SchematicannonRenderer.renderLaunchedBlocks: the arc, in the space centred on the cannon */
		public static Vec3 flightPosition(BlockPos cannonPos, LaunchedItem launched) {
			Vec3 start = Vec3.atCenterOf(cannonPos.above());
			Vec3 target = Vec3.atCenterOf(launched.target);
			Vec3 distance = target.subtract(start);

			double yDifference = target.y - start.y;
			double throwHeight = Math.sqrt(distance.lengthSqr()) * .6f + yDifference;
			Vec3 cannonOffset = distance.add(0, throwHeight, 0)
				.normalize()
				.scale(2);
			start = start.add(cannonOffset);
			yDifference = target.y - start.y;

			float t = progress(launched);
			Vec3 blockLocationXZ = target.subtract(start)
				.scale(t)
				.multiply(1, 0, 1);

			// Height is determined through a bezier curve
			double yOffset = 2 * (1 - t) * t * throwHeight + t * t * yDifference;
			return blockLocationXZ.add(0, yOffset + 1.0, 0)
				.add(cannonOffset);
		}

		public static double[] getCannonAngles(SchematicannonBlockEntity blockEntity, BlockPos pos) {
			double yaw;
			double pitch;

			BlockPos target = blockEntity.printer.getCurrentTarget();
			if (target != null) {

				// Calculate Angle of Cannon
				Vec3 diff = Vec3.atLowerCornerOf(target.subtract(pos));

				double diffX = diff.x();
				double diffZ = diff.z();
				yaw = Mth.atan2(diffX, diffZ);
				yaw = yaw / Math.PI * 180;

				float distance = Mth.sqrt((float) (diffX * diffX + diffZ * diffZ));
				double yOffset = 0 + distance * 2f;
				pitch = Mth.atan2(distance, diff.y() * 3 + yOffset);
				pitch = pitch / Math.PI * 180 + 10;

			} else {
				yaw = blockEntity.defaultYaw;
				pitch = 40;
			}

			return new double[] { yaw, pitch };
		}

		public static double getRecoil(SchematicannonBlockEntity blockEntity) {
			double recoil = 0;

			for (LaunchedItem launched : blockEntity.flyingBlocks) {

				if (launched.ticksRemaining == 0)
					continue;

				// Apply Recoil if block was just launched
				if ((launched.ticksRemaining + 1 - 1f) > launched.totalTicks - 10)
					recoil = Math.max(recoil, (launched.ticksRemaining + 1 - 1f) - launched.totalTicks + 10);
			}

			return recoil;
		}
	}
}

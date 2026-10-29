package dev.createpolymer.contraptions.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.AbstractContraptionEntity.ContraptionRotationState;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.ContraptionWorld;
import dev.createpolymer.visual.BlockstateModels;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.SimpleEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConduitBlock;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What vanilla clients see of a contraption: display entities for every block, attached to the contraption entity.
 * <p>
 * <b>Design.</b> The contraption entity is shown to clients as an invisible item display (the "carrier"). All block
 * displays are virtual <i>passengers</i> of that carrier, so while a contraption only translates (pistons, gantries,
 * elevators, minecarts) the server sends one position update per tick no matter how many blocks it has: the client
 * moves the riders with the carrier. Each display gets a transformation (translation + rotation) relative to the
 * carrier; it is re-sent only when the contraption's rotation changes, with interpolation towards a predicted
 * orientation ({@link AbstractContraptionEntity#getPredictedRotationState}), or when blocks change.
 * <p>
 * Solid blocks near the surface also get an invisible shulker as collision hitbox (a client collides with shulkers
 * but not with displays), and blocks with a moving interaction/storage get a click target. Those cannot ride the
 * carrier at an offset, so they are normal virtual entities that Polymer moves with relative move packets; they
 * are capped to {@link #MAX_PROXIES} per contraption.
 * <p>
 * Entities sitting on the contraption's seats do not ride the carrier on clients, but a seat proxy each (see
 * {@link ContraptionSeats}), so they and a riding player's camera are on their seat.
 * <p>
 * Limits: one display entity per block (a 1000 block contraption is 1000 entities for everyone tracking it);
 * moving parts of Create blocks (shafts, cogs, heads) do not animate; non-axis-aligned rotation moves the shulker
 * hitboxes but they stay axis-aligned.
 */
public class ContraptionVisual extends ElementHolder {
	public static final int MAX_PROXIES = 256;
	/** Largest turn, in degrees, one interpolation step may cover (clients take the shortest way round). */
	private static final float MAX_STEP_DEGREES = 45;

	private enum Kind {
		/** Block display: model coordinates are block corner based. */
		BLOCK,
		/** Item display: model coordinates are block centred. */
		CENTERED
	}

	private record Piece(BlockPos local, BlockState state, Kind kind, DisplayElement element, Quaternionf own) {}

	private record Proxy(BlockPos local, GenericEntityElement element) {}

	private final AbstractContraptionEntity entity;
	private final ContraptionSeats seats;
	private final List<Piece> pieces = new ArrayList<>();
	private final List<Proxy> proxies = new ArrayList<>();
	private final Map<BlockPos, BlockState> shownStates = new HashMap<>();
	private int structureVersion = Integer.MIN_VALUE;
	private Contraption contraption;

	private ContraptionRotationState sentTarget;
	private long nextSend;
	private long ticks;
	private boolean firstUpdate = true;

	public ContraptionVisual(AbstractContraptionEntity entity) {
		this.entity = entity;
		this.contraption = entity.getContraption();
		this.seats = new ContraptionSeats(this, entity);
	}

	@Override
	protected void onTick() {
		if (entity.isRemoved() || entity.getContraption() == null)
			return;
		ticks++;
		contraption = entity.getContraption();
		if (structureVersion != contraption.getStructureVersion())
			rebuild();
		updateTransforms();
		seats.update();
	}

	/** The proxies the entities sitting on the contraption's seats ride on for clients. */
	public ContraptionSeats getSeats() {
		return seats;
	}

	/** An entity boarded or left a seat: put its proxy up or take it down now. */
	public void seatsChanged() {
		seats.refresh();
		seats.requestResync();
	}

	@Override
	protected void startWatchingExtraPackets(ServerGamePacketListenerImpl player,
		Consumer<Packet<ClientGamePacketListener>> packetConsumer) {
		seats.watcherAdded(player);
	}

	/** Number of display entities currently used (for tests and diagnostics). */
	public int getPieceCount() {
		return pieces.size();
	}

	public int getProxyCount() {
		return proxies.size();
	}

	public List<DisplayElement> getDisplayElements() {
		List<DisplayElement> list = new ArrayList<>();
		for (Piece p : pieces)
			list.add(p.element);
		return list;
	}

	// ---- structure ----

	private void rebuild() {
		structureVersion = contraption.getStructureVersion();
		Map<BlockPos, StructureBlockInfo> blocks = contraption.getBlocks();

		// pieces: keep displays whose block did not change, replace the rest
		Set<BlockPos> unchanged = new HashSet<>();
		List<Piece> keep = new ArrayList<>();
		List<Piece> drop = new ArrayList<>();
		for (Piece p : pieces) {
			StructureBlockInfo info = blocks.get(p.local);
			if (info != null && info.state() == p.state && unchanged.add(p.local))
				keep.add(p);
			else
				drop.add(p);
		}
		removeAll(drop.stream().map(p -> (VirtualElement) p.element).toList());
		pieces.clear();
		pieces.addAll(keep);
		Set<BlockPos> shown = new HashSet<>(unchanged);
		for (StructureBlockInfo info : blocks.values()) {
			if (shown.contains(info.pos()))
				continue;
			createPieces(info.pos(), info.state());
		}

		// proxies are cheap to recompute: replace them all
		removeAll(proxies.stream().map(p -> (VirtualElement) p.element).toList());
		proxies.clear();
		createProxies(blocks);

		shownStates.clear();
		blocks.forEach((pos, info) -> shownStates.put(pos, info.state()));
		firstUpdate = true;
	}

	private void removeAll(List<VirtualElement> elements) {
		if (elements.isEmpty())
			return;
		int[] ids = elements.stream().flatMapToInt(e -> e.getEntityIds().intStream()).toArray();
		VirtualEntityUtils.removeVirtualPassenger(entity, ids);
		for (VirtualElement e : elements)
			removeElement(e);
	}

	private void addPiece(BlockPos local, BlockState state, Kind kind, DisplayElement element, Quaternionf own) {
		element.setViewRange(1.5f);
		element.setTeleportDuration(0);
		element.setInterpolationDuration(0);
		element.ignorePositionUpdates();
		pieces.add(new Piece(local, state, kind, element, own));
		addElement(element);
		VirtualEntityUtils.addVirtualPassenger(entity, element.getEntityIds().getInt(0));
	}

	private void createPieces(BlockPos local, BlockState state) {
		if (state.isAir())
			return;
		Block block = state.getBlock();

		if (block instanceof ContraptionDisplay display) {
			for (ContraptionDisplay.Piece piece : display.getContraptionPieces(state)) {
				ItemDisplayElement element = new ItemDisplayElement(piece.stack());
				element.setItemDisplayContext(ItemDisplayContext.NONE);
				addPiece(local, state, Kind.CENTERED, element, piece.orientation());
			}
			return;
		}

		if (block instanceof PolymerBlock polymerBlock) {
			BlockState client = polymerBlock.getPolymerBlockState(state, null);
			if (client.isAir())
				return;
			if (!client.is(Blocks.BARRIER)) {
				addPiece(local, state, Kind.BLOCK, new BlockDisplayElement(client), new Quaternionf());
				return;
			}
			createModelBlockPiece(local, state);
			return;
		}

		if (needsItemModel(block)) {
			ItemStack stack = new ItemStack(block.asItem());
			if (stack.isEmpty())
				return;
			ItemDisplayElement element = new ItemDisplayElement(stack);
			element.setItemDisplayContext(ItemDisplayContext.NONE);
			addPiece(local, state, Kind.CENTERED, element, orientationFor(state));
			return;
		}

		if (state.getRenderShape() == RenderShape.INVISIBLE)
			return;
		addPiece(local, state, Kind.BLOCK, new BlockDisplayElement(state), new Quaternionf());
	}

	/** Create blocks drawn with display models: their blockstate file when bundled, else their item's model. */
	private void createModelBlockPiece(BlockPos local, BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		BlockstateModels.Variant variant = null;
		if (ContraptionVisual.class.getResource("/create_polymer/blockstates/" + path + ".json") != null)
			variant = BlockstateModels.get(path, state);
		ItemStack stack;
		Quaternionf own;
		if (variant != null) {
			stack = variant.stack();
			own = variant.rotation();
		} else {
			stack = new ItemStack(state.getBlock().asItem());
			if (stack.isEmpty())
				return;
			own = orientationFor(state);
		}
		ItemDisplayElement element = new ItemDisplayElement(stack);
		element.setItemDisplayContext(ItemDisplayContext.NONE);
		addPiece(local, state, Kind.CENTERED, element, own);
	}

	/** Vanilla blocks drawn by a block entity renderer have no block model: show their item instead. */
	private static boolean needsItemModel(Block block) {
		return block instanceof AbstractChestBlock<?> || block instanceof AbstractBannerBlock || block instanceof SignBlock
			|| block instanceof AbstractBedBlock || block instanceof AbstractSkullBlock || block instanceof ShulkerBoxBlock
			|| block instanceof ConduitBlock || block instanceof DecoratedPotBlock || block instanceof CopperGolemStatueBlock;
	}

	/** Orientation of an item model that stands for a block with facing/axis properties (best effort). */
	private static Quaternionf orientationFor(BlockState state) {
		if (state.hasProperty(BlockStateProperties.AXIS))
			return dev.createpolymer.visual.Orientation.forAxis(state.getValue(BlockStateProperties.AXIS));
		if (state.hasProperty(BlockStateProperties.FACING))
			return dev.createpolymer.visual.Orientation.forFacing(state.getValue(BlockStateProperties.FACING));
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
			return dev.createpolymer.visual.Orientation.forHorizontal(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
		return new Quaternionf();
	}

	// ---- hitboxes and click targets ----

	private void createProxies(Map<BlockPos, StructureBlockInfo> blocks) {
		ContraptionWorld cw = contraption.getContraptionWorld();
		for (StructureBlockInfo info : blocks.values()) {
			if (proxies.size() >= MAX_PROXIES)
				break;
			BlockPos pos = info.pos();
			BlockState state = info.state();
			VoxelShape shape = state.getCollisionShape(cw, pos);
			boolean interactive = contraption.getInteractors().containsKey(pos)
				|| contraption.getStorage().getAllItemStorages().containsKey(pos)
				|| contraption.getSeats().contains(pos);
			boolean solid = !shape.isEmpty() && Block.isShapeFullBlock(shape) && isExposed(blocks, cw, pos);
			if (solid) {
				SimpleEntityElement shulker = new SimpleEntityElement(EntityTypes.SHULKER);
				shulker.setInvisible(true);
				shulker.setSilent(true);
				shulker.setNoGravity(true);
				shulker.setInteractionHandler(interaction(pos));
				proxies.add(new Proxy(pos, shulker));
				addElement(shulker);
			} else if (interactive) {
				InteractionElement click = new InteractionElement(interaction(pos));
				click.setSize(1f, 1f);
				click.setResponse(true);
				proxies.add(new Proxy(pos, click));
				addElement(click);
			}
		}
	}

	private static boolean isExposed(Map<BlockPos, StructureBlockInfo> blocks, ContraptionWorld cw, BlockPos pos) {
		for (Direction d : Direction.values()) {
			StructureBlockInfo neighbour = blocks.get(pos.relative(d));
			if (neighbour == null)
				return true;
			if (!Block.isShapeFullBlock(neighbour.state().getCollisionShape(cw, pos.relative(d))))
				return true;
		}
		return false;
	}

	private VirtualElement.InteractionHandler interaction(BlockPos local) {
		return new VirtualElement.InteractionHandler() {
			@Override
			public void interact(ServerPlayer player, InteractionHand hand, Vec3 pos, boolean usingSecondaryAction) {
				if (entity.isRemoved() || entity.level() != player.level())
					return;
				Vec3 eye = player.getEyePosition();
				Vec3 localEye = entity.toLocalVector(eye, 1);
				Vec3 center = Vec3.atCenterOf(local);
				Direction side = Direction.getApproximateNearest(localEye.x - center.x, localEye.y - center.y, localEye.z - center.z);
				if (entity.handlePlayerInteraction(player, local, side, hand))
					player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			}
		};
	}

	// ---- transforms ----

	private void updateTransforms() {
		ContraptionRotationState current = entity.getRotationState();
		if (firstUpdate) {
			firstUpdate = false;
			send(current, 0);
			sentTarget = current;
			nextSend = ticks;
			return;
		}
		if (ticks < nextSend)
			return;

		float rate = maxDifference(entity.getPredictedRotationState(1), current);
		if (rate < 1e-3f) {
			if (maxDifference(current, sentTarget) > 1e-3f) {
				send(current, 1);
				sentTarget = current;
				nextSend = ticks + 1;
			}
			return;
		}
		int steps = Math.max(1, Math.min(5, (int) (MAX_STEP_DEGREES / rate)));
		ContraptionRotationState target = entity.getPredictedRotationState(steps);
		send(target, steps);
		sentTarget = target;
		nextSend = ticks + steps;
	}

	private static float maxDifference(ContraptionRotationState a, ContraptionRotationState b) {
		return Math.max(Math.max(Math.abs(a.xRotation - b.xRotation), Math.abs(a.yRotation - b.yRotation)),
			Math.max(Math.abs(a.zRotation - b.zRotation), Math.abs(a.secondYRotation - b.secondYRotation)));
	}

	/**
	 * Sends the transforms for the contraption turned as {@code state}. Blocks are positioned like Create's
	 * {@code toGlobalVector}: anchor + 0.5 + rotation * (local - 0.5), expressed relative to the carrier.
	 */
	private void send(ContraptionRotationState state, int interpolation) {
		Quaternionf rotation = rotationOf(state);
		Vector3f anchorRel = entity.getAnchorVec().subtract(entity.position()).toVector3f();
		Matrix3f linear = new Matrix3f().rotation(rotation);

		for (Piece p : pieces) {
			Vector3f local = new Vector3f(p.local.getX(), p.local.getY(), p.local.getZ());
			if (p.kind == Kind.BLOCK)
				local.sub(.5f, .5f, .5f);
			Vector3f translation = linear.transform(local, new Vector3f()).add(anchorRel).add(.5f, .5f, .5f);
			Quaternionf left = new Quaternionf(rotation).mul(p.own);
			p.element.setTranslation(translation);
			p.element.setLeftRotation(left);
			p.element.setScale(new Vector3f(1, 1, 1));
			// Item displays are turned half way round by the client; undo it, as Part does
			p.element.setRightRotation(p.kind == Kind.CENTERED ? new Quaternionf().rotationY((float) Math.PI) : new Quaternionf());
			if (p.element.isTransformationDirty()) {
				p.element.setInterpolationDuration(interpolation);
				p.element.startInterpolation();
			}
		}

		// hitboxes follow the current position of the block centres (bottom centre for shulkers)
		for (Proxy proxy : proxies) {
			// bottom centre of the (rotated) block
			Vector3f bottomCentre = linear.transform(new Vector3f(proxy.local.getX(), proxy.local.getY(), proxy.local.getZ()), new Vector3f())
				.add(anchorRel).add(.5f, 0f, .5f);
			proxy.element.setOffset(new Vec3(bottomCentre.x, bottomCentre.y, bottomCentre.z));
		}
	}

	/** The rotation Create's applyRotation performs: x, then z, then y (see ContraptionRotationState#asMatrix). */
	private Quaternionf rotationOf(ContraptionRotationState state) {
		Vec3 x = state.applyTo(new Vec3(1, 0, 0));
		Vec3 y = state.applyTo(new Vec3(0, 1, 0));
		Vec3 z = state.applyTo(new Vec3(0, 0, 1));
		Matrix3f m = new Matrix3f(
			(float) x.x, (float) x.y, (float) x.z,
			(float) y.x, (float) y.y, (float) y.z,
			(float) z.x, (float) z.y, (float) z.z);
		return m.getNormalizedRotation(new Quaternionf());
	}

	@Override
	public void destroy() {
		seats.clear();
		super.destroy();
		pieces.clear();
		proxies.clear();
	}
}

package dev.createpolymer.schematics;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The outline of a box that only one player sees: Create draws these on the client with its outliner. Here it is
 * twelve block displays in an element holder that only this player is a watcher of. The holder sits at the world
 * origin, so the offsets of its elements are world positions.
 */
public class PlayerBoxOutline {
	/** Create's outline: lines 1/16 of a block wide */
	private static final float WIDTH = 1 / 16f;

	private final ServerPlayer player;
	private ServerLevel level;
	private ElementHolder holder;
	private BlockDisplayElement[] edges;
	private AABB box;

	public PlayerBoxOutline(ServerPlayer player) {
		this.player = player;
	}

	public boolean isShowing() {
		return holder != null;
	}

	@Nullable
	public AABB getBox() {
		return box;
	}

	/** Shows the box, or hides the outline for null */
	public void set(@Nullable AABB newBox) {
		if (newBox == null) {
			remove();
			return;
		}
		if (holder != null && player.level() != level)
			remove();
		if (holder == null)
			create(newBox);
		if (newBox.equals(box))
			return;
		box = newBox;
		layout(newBox);
	}

	private void create(AABB initial) {
		level = player.level();
		holder = new ElementHolder();
		new ManualAttachment(holder, level, () -> Vec3.ZERO);
		edges = new BlockDisplayElement[12];
		for (int i = 0; i < edges.length; i++) {
			BlockDisplayElement edge = new BlockDisplayElement(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.LIGHT_BLUE).defaultBlockState());
			edge.setBrightness(Brightness.FULL_BRIGHT);
			edge.setViewRange(8f);
			edge.setInterpolationDuration(0);
			edge.setTeleportDuration(0);
			edge.setShadowStrength(0);
			edge.setShadowRadius(0);
			holder.addElement(edge);
			edges[i] = edge;
		}
		box = initial;
		layout(initial);
		holder.startWatching(player);
	}

	private void layout(AABB b) {
		double[] min = { b.minX, b.minY, b.minZ };
		double[] max = { b.maxX, b.maxY, b.maxZ };
		int index = 0;
		for (int axis = 0; axis < 3; axis++) {
			int u = (axis + 1) % 3;
			int v = (axis + 2) % 3;
			for (int cu = 0; cu < 2; cu++) {
				for (int cv = 0; cv < 2; cv++) {
					double[] start = new double[3];
					Vector3f scale = new Vector3f();
					start[axis] = min[axis] - WIDTH / 2;
					start[u] = (cu == 0 ? min[u] : max[u]) - WIDTH / 2;
					start[v] = (cv == 0 ? min[v] : max[v]) - WIDTH / 2;
					float[] size = new float[3];
					size[axis] = (float) (max[axis] - min[axis]) + WIDTH;
					size[u] = WIDTH;
					size[v] = WIDTH;
					scale.set(size[0], size[1], size[2]);
					BlockDisplayElement edge = edges[index++];
					edge.setOffset(new Vec3(start[0], start[1], start[2]));
					edge.setScale(scale);
				}
			}
		}
	}

	/** Sends what changed to the player */
	public void tick() {
		if (holder == null)
			return;
		if (player.isRemoved() || player.level() != level) {
			remove();
			return;
		}
		holder.tick();
	}

	public void remove() {
		if (holder != null) {
			holder.destroy();
			holder = null;
			edges = null;
		}
		box = null;
	}
}

package dev.createpolymer.decoration.copycat;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import dev.createpolymer.registry.feature.CopycatRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What players see of a copycat. Create bakes a model out of the material's own quads (CopycatModel and its
 * subclasses crop the quads of the material's model to the shape of the copycat); a vanilla client cannot do that, so
 * the material is shown by a block display of the material's block state that is squeezed into the copycat's shape.
 * Squeezing stretches textures: the side faces of a 3 pixel panel show the whole texture in 3 pixels. Without a
 * material the copycat shows Create's own copycat_base models.
 */
public abstract class CopycatModel extends BaseModel {

	/**
	 * What one piece of a copycat shows, for drawing the copycat somewhere else than in the world (a contraption):
	 * either a block state or a model item, placed by {@code matrix} relative to the centre of the block. For a block
	 * state the matrix maps the unit cube of a block display (see {@link #box}).
	 */
	public record Shape(@Nullable BlockState block, @Nullable ItemStack item, Matrix4f matrix) {}

	private BlockState shownMaterial;
	private final List<VirtualElement> pieces = new ArrayList<>();
	private final List<Shape> shapes = new ArrayList<>();

	protected CopycatModel(BlockState state, BlockState material) {
		super(state);
		rebuild(material);
	}

	/** The material the pieces currently show. */
	public BlockState getShownMaterial() {
		return shownMaterial;
	}

	/** The shapes that make up the copycat. */
	public List<Shape> getShapes() {
		return List.copyOf(shapes);
	}

	/** The display entities that make up the copycat. */
	public List<VirtualElement> getPieces() {
		return List.copyOf(pieces);
	}

	public static boolean showsBase(BlockState material) {
		return material.isAir() || material.is(CopycatRegistry.COPYCAT_BASE);
	}

	/** Adds the pieces that show {@code material} in the shape of the copycat; {@link #state} is current. */
	protected abstract void build(BlockState material);

	private void rebuild(BlockState material) {
		for (VirtualElement piece : pieces)
			removeElement(piece);
		pieces.clear();
		shapes.clear();
		shownMaterial = material;
		build(material);
	}

	@Override
	protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		BlockState material = be instanceof CopycatBlockEntity cbe ? cbe.getMaterial()
			: CopycatRegistry.COPYCAT_BASE.defaultBlockState();
		if (material != shownMaterial)
			rebuild(material);
	}

	@Override
	protected void onStateChanged(BlockState state) {
		rebuild(shownMaterial);
	}

	/** A model of Create's copycat_base (a model under models/block, see the copycat manifest). */
	protected Part addBase(String model, Quaternionf orientation, float x, float y, float z) {
		ItemStack stack = ModelItems.block(model);
		Part part = addPart(stack);
		part.orientation(orientation)
			.translation(x, y, z)
			.apply(0);
		pieces.add(part.element);
		shapes.add(new Shape(null, stack, new Matrix4f().translate(x, y, z)
			.rotate(orientation)));
		return part;
	}

	/** A block display of {@code displayState}; {@code matrix} maps the unit cube at the block's corner. */
	protected BlockDisplayElement addBlock(BlockState displayState, Matrix4f matrix) {
		BlockDisplayElement display = new BlockDisplayElement(displayState);
		display.setTransformation(matrix);
		display.setViewRange(1f);
		display.setInterpolationDuration(0);
		display.setTeleportDuration(0);
		addElement(display);
		pieces.add(display);
		shapes.add(new Shape(displayState, null, new Matrix4f(matrix)));
		return display;
	}

	/** The material, stretched over the box from (x0, y0, z0) to (x1, y1, z1) in pixels (0 to 16). */
	protected BlockDisplayElement addBox(BlockState displayState, float x0, float y0, float z0, float x1, float y1,
		float z1) {
		return addBlock(displayState, box(x0, y0, z0, x1, y1, z1));
	}

	/** Block displays are cubes from the entity's position up; models are block-centred. */
	public static Matrix4f box(float x0, float y0, float z0, float x1, float y1, float z1) {
		return new Matrix4f().translate(x0 / 16 - .5f, y0 / 16 - .5f, z0 / 16 - .5f)
			.scale((x1 - x0) / 16, (y1 - y0) / 16, (z1 - z0) / 16);
	}

	/** The material at the size of a full block. */
	public static Matrix4f full() {
		return new Matrix4f().translate(-.5f, -.5f, -.5f);
	}

}

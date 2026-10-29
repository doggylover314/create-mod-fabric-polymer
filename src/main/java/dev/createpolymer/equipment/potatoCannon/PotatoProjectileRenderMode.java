package dev.createpolymer.equipment.potatoCannon;

import java.util.function.Function;

import org.joml.Quaternionf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.createpolymer.registry.feature.EquipmentRegistry;
import net.minecraft.world.entity.Display;

/**
 * Ported from Create's PotatoProjectileRenderMode (MIT, The Create Team). The projectile is drawn by an item display
 * entity, so a render mode picks the display's billboard constraint and the rotation it is given each tick.
 */
public interface PotatoProjectileRenderMode {
	Codec<PotatoProjectileRenderMode> CODEC = EquipmentRegistry.POTATO_PROJECTILE_RENDER_MODE.byNameCodec()
		.dispatch(PotatoProjectileRenderMode::codec, Function.identity());

	/** Whether the item turns to face the viewer before {@link #rotation} is applied */
	default Display.BillboardConstraints billboard() {
		return Display.BillboardConstraints.FIXED;
	}

	/** The rotation of the item relative to the display's orientation, see Create's {@code transform} */
	Quaternionf rotation(PotatoProjectileEntity entity);

	MapCodec<? extends PotatoProjectileRenderMode> codec();
}

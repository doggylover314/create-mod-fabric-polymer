package dev.createpolymer.equipment.potatoCannon;

import org.joml.Quaternionf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import net.minecraft.core.Registry;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's AllPotatoProjectileRenderModes (MIT, The Create Team). Create's transforms run on the client
 * with the camera position; the item display's CENTER billboard does the facing here.
 */
public class AllPotatoProjectileRenderModes {

	static {
		register("billboard", Billboard.CODEC);
		register("tumble", Tumble.CODEC);
		register("toward_motion", TowardMotion.CODEC);
		register("stuck_to_entity", StuckToEntity.CODEC);
	}

	public static void init() {
	}

	private static void register(String name, MapCodec<? extends PotatoProjectileRenderMode> codec) {
		Registry.register(EquipmentRegistry.POTATO_PROJECTILE_RENDER_MODE, CreatePolymer.id(name), codec);
	}

	public enum Billboard implements PotatoProjectileRenderMode {
		INSTANCE;

		public static final MapCodec<Billboard> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public Display.BillboardConstraints billboard() {
			return Display.BillboardConstraints.CENTER;
		}

		@Override
		public Quaternionf rotation(PotatoProjectileEntity entity) {
			return new Quaternionf();
		}

		@Override
		public MapCodec<? extends PotatoProjectileRenderMode> codec() {
			return CODEC;
		}
	}

	public enum Tumble implements PotatoProjectileRenderMode {
		INSTANCE;

		public static final MapCodec<Tumble> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public Display.BillboardConstraints billboard() {
			return Display.BillboardConstraints.CENTER;
		}

		@Override
		public Quaternionf rotation(PotatoProjectileEntity entity) {
			float time = entity.tickCount + 1;
			return new Quaternionf()
				.rotateZ((float) Math.toRadians(time * 2 * entityRandom(entity, 16)))
				.rotateX((float) Math.toRadians(time * entityRandom(entity, 32)));
		}

		@Override
		public MapCodec<? extends PotatoProjectileRenderMode> codec() {
			return CODEC;
		}
	}

	public record TowardMotion(int spriteAngleOffset, float spin) implements PotatoProjectileRenderMode {
		public static final MapCodec<TowardMotion> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.INT.fieldOf("sprite_angle_offset").forGetter(i -> i.spriteAngleOffset),
			Codec.FLOAT.fieldOf("spin").forGetter(i -> i.spin)
		).apply(instance, TowardMotion::new));

		@Override
		public Quaternionf rotation(PotatoProjectileEntity entity) {
			Vec3 diff = entity.getDeltaMovement();
			return new Quaternionf()
				.rotateY((float) Math.toRadians(deg(Mth.atan2(diff.x, diff.z))))
				.rotateX((float) Math.toRadians(270
					+ deg(Mth.atan2(diff.y, -Mth.sqrt((float) (diff.x * diff.x + diff.z * diff.z))))))
				.rotateY((float) Math.toRadians((entity.tickCount + 1) * 20 * spin + entityRandom(entity, 360)))
				.rotateZ((float) Math.toRadians(-spriteAngleOffset));
		}

		@Override
		public MapCodec<? extends PotatoProjectileRenderMode> codec() {
			return CODEC;
		}
	}

	public record StuckToEntity(Vec3 offset) implements PotatoProjectileRenderMode {
		public static final MapCodec<StuckToEntity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Vec3.CODEC.fieldOf("offset").forGetter(i -> i.offset)
		).apply(instance, StuckToEntity::new));

		@Override
		public Quaternionf rotation(PotatoProjectileEntity entity) {
			return new Quaternionf().rotateY((float) Math.toRadians(deg(Mth.atan2(offset.x, offset.z))));
		}

		@Override
		public MapCodec<? extends PotatoProjectileRenderMode> codec() {
			return CODEC;
		}
	}

	private static float deg(double radians) {
		return (float) Math.toDegrees(radians);
	}

	private static int entityRandom(Entity entity, int maxValue) {
		return Math.floorMod(System.identityHashCode(entity) * 31, maxValue);
	}
}

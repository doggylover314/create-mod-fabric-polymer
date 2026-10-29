package dev.createpolymer.contraptions.minecart;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import dev.createpolymer.CreatePolymer;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Access to the fuel of a furnace cart, which vanilla keeps private (Create reads it with a mixin accessor,
 * {@code MinecartFurnaceAccessor}; there are no mixins in this port, so this goes through a var handle).
 */
public final class FurnaceCartFuel {
	private FurnaceCartFuel() {}

	private static final VarHandle FUEL = find();

	private static VarHandle find() {
		try {
			return MethodHandles.privateLookupIn(MinecartFurnace.class, MethodHandles.lookup())
				.findVarHandle(MinecartFurnace.class, "fuel", int.class);
		} catch (ReflectiveOperationException | RuntimeException e) {
			CreatePolymer.LOGGER.error("Could not access the fuel of furnace minecarts, contraptions on them can only "
				+ "refuel carts that ran dry", e);
			return null;
		}
	}

	/** Remaining burn time in ticks. Without the var handle: whether the cart burns at all. */
	public static int getFuel(MinecartFurnace cart) {
		if (FUEL != null)
			return (int) FUEL.get(cart);
		return cart.getDefaultDisplayBlockState()
			.getValue(FurnaceBlock.LIT) ? 1 << 20 : 0;
	}

	public static void addFuel(MinecartFurnace cart, int ticks) {
		if (FUEL != null) {
			FUEL.set(cart, getFuel(cart) + ticks);
			return;
		}
		// vanilla's own refuelling turns the cart around; keep its direction
		Vec3 push = cart.push;
		cart.addFuel(cart.position(), new ItemStack(Items.COAL));
		cart.push = push;
	}

}

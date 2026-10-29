package harness.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void harness$tick(CallbackInfo ci) {
		harness.Harness.tick((Minecraft) (Object) this);
	}
}

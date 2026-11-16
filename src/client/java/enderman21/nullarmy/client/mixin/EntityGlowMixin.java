package enderman21.nullarmy.client.mixin;

import enderman21.nullarmy.client.ClientCommanderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Подсветка врагов разведкой командира: на клиенте флаг свечения нельзя включить через setGlowingTag, поэтому подменяем ответ. */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {
	@Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
	private void endermansnull$surveyGlow(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self.level().isClientSide() && ClientCommanderState.isGlowing(self.getId())) {
			cir.setReturnValue(true);
		}
	}
}

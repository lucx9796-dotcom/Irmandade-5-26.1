package br.com.irmandade.imdd.mixin;

import br.com.irmandade.imdd.expurgo.Expurgo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item com unbreakable={} no chão: não toma dano (explosão, fogo, lava, cacto...) e não some por tempo. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void imdd$indestrutivel(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (((ItemEntity) (Object) this).getItem().has(DataComponents.UNBREAKABLE)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void imdd$semDespawn(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getItem().has(DataComponents.UNBREAKABLE)) {
            self.setUnlimitedLifetime();
        }
    }

    /** Nether star destruída pelo fogo/lava => Dia do Expurgo. */
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void imdd$expurgo(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.isRemoved() && self.getItem().is(Items.NETHER_STAR) && source.is(DamageTypeTags.IS_FIRE)) {
            Expurgo.onNetherStarBurned(level, new Vec3(self.getX(), self.getY(), self.getZ()), self.getItem().getCount());
        }
    }
}

package com.polycore.mixin.selftest;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plays "another mod" that injects into every ItemEntity method ("*"), but only where an item is picked up, which
 * none of the item merging methods do: this must not count as a conflict. Does nothing; test runs only.
 */
@Mixin(ItemEntity.class)
abstract class ItemEntitySelfTestMixin {
    @Inject(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;take(Lnet/minecraft/world/entity/Entity;I)V"))
    private void polycore$selfTest(Player player, CallbackInfo ci) {}
}

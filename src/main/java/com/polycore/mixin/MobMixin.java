package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.polycore.guard.GuardedMob;
import com.polycore.guard.TpsGuard;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * TPS Guard, far mob AI: while active, a mob far from every player evaluates which goals to start or stop every 4
 * or 8 ticks instead of every 2. Its running goals, navigation and movement still tick exactly as in vanilla.
 */
@Mixin(Mob.class)
abstract class MobMixin implements GuardedMob {
    @Unique
    private int polycore$interval = 1;
    @Unique
    private int polycore$checkedAt = -1000;

    @Override
    public boolean polycore$skipAi() {
        if (!TpsGuard.mobAiActive()) return false;
        Mob self = (Mob) (Object) this;
        if (self.tickCount - polycore$checkedAt >= 20 || self.tickCount < polycore$checkedAt) {
            polycore$checkedAt = self.tickCount;
            polycore$interval = TpsGuard.aiInterval(self);
        }
        return polycore$interval > 1 && (self.tickCount + self.getId()) % polycore$interval != 0;
    }

    /** Both the target selector and the goal selector. */
    @WrapOperation(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"))
    private void polycore$goals(GoalSelector selector, Operation<Void> original) {
        if (polycore$skipAi()) selector.tickRunningGoals(true); // what vanilla's tick() does after choosing goals
        else original.call(selector);
    }
}

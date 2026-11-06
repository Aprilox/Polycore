package com.polycore.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import com.polycore.gen.StepProfiler;
import com.polycore.optim.Optimization;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Profiler : mesure chaque etape de generation, de l'appel de ChunkStep.apply a la fin du travail.
 * Fonctionne aussi avec Moonrise, qui appelle ChunkStep.apply depuis son propre planificateur.
 */
@Mixin(ChunkStep.class)
abstract class ChunkStepMixin {

    @Shadow
    public abstract ChunkStatus targetStatus();

    @Inject(method = "apply", at = @At("HEAD"))
    private void polycore$start(WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                                CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir,
                                @Share("polycoreStart") LocalLongRef start,
                                @Share("polycoreGenerated") LocalBooleanRef generated) {
        start.set(System.nanoTime());
        // Vrai si le chunk est vraiment genere a cette etape (faux s'il est simplement charge depuis le disque).
        generated.set(chunk != null && !chunk.getPersistedStatus().isOrAfter(this.targetStatus()));
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void polycore$end(WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                              CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir,
                              @Share("polycoreStart") LocalLongRef start,
                              @Share("polycoreGenerated") LocalBooleanRef generated) {
        Optimization.STEP_PROFILER.markHookSeen();
        if (!StepProfiler.enabled()) return;
        if (!generated.get()) {
            if ("full".equals(StepProfiler.shortName(this.targetStatus()))) StepProfiler.recordLoaded();
            return;
        }
        CompletableFuture<ChunkAccess> future = cir.getReturnValue();
        if (future == null) return;
        final long t0 = start.get();
        final String name = StepProfiler.shortName(this.targetStatus());
        // Simple observation : on n'ajoute qu'un rappel, la valeur de retour n'est pas modifiee.
        future.whenComplete((c, t) -> StepProfiler.record(name, System.nanoTime() - t0));
    }
}

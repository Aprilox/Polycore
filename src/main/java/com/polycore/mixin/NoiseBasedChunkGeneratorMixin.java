package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.polycore.gen.WorldgenHooks;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * En 1.21.1, createBiomes et fillFromNoise lancent leur calcul avec
 * CompletableFuture.supplyAsync(supplier, Util.backgroundExecutor()).
 * On remplace l'executeur par le pool Polycore (et on mesure le temps de calcul).
 * Non applique si Moonrise ou C2ME est present (voir PolycoreMixinPlugin).
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {

    @WrapOperation(
            method = "createBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Object> polycore$biomesExecutor(Supplier<Object> supplier, Executor executor,
                                                              Operation<CompletableFuture<Object>> original) {
        return WorldgenHooks.dispatch("biomes", supplier, executor, original);
    }

    @WrapOperation(
            method = "fillFromNoise",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Object> polycore$noiseExecutor(Supplier<Object> supplier, Executor executor,
                                                             Operation<CompletableFuture<Object>> original) {
        return WorldgenHooks.dispatch("noise", supplier, executor, original);
    }
}

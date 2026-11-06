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
 * In 1.21.1, createBiomes and fillFromNoise start their work with
 * CompletableFuture.supplyAsync(supplier, Util.backgroundExecutor()).
 * The executor is replaced by the Polycore pool (and the computation time is measured).
 * Not applied when Moonrise or C2ME is present (see PolyCoreMixinPlugin).
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

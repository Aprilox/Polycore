package com.polycore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.polycore.gen.WorldgenHooks;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** In 26.x, createBiomes lives in ChunkGenerator (no longer overridden): same executor swap as NoiseBasedChunkGeneratorMixin. */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {

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
}

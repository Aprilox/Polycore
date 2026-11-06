package com.polycore.neoforge.test;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Generates the terrain shape (noise stage) of normal overworld chunks inside the flat game test world, to profile
 * and check the noise optimizations. Must not run on the server thread: the structure lookup goes through it.
 */
final class NoiseHarness {
    private final ServerLevel level;
    private final NoiseBasedChunkGenerator generator;
    private final RandomState random;

    NoiseHarness(ServerLevel level, long seed) {
        this.level = level;
        RegistryAccess ra = level.registryAccess();
        Holder<NoiseGeneratorSettings> settings = ra.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        Holder<MultiNoiseBiomeSourceParameterList> biomes = ra.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        this.generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(biomes), settings);
        this.random = RandomState.create(settings.value(), ra.lookupOrThrow(Registries.NOISE), seed);
    }

    ProtoChunk fill(int cx, int cz) {
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(cx, cz), UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        return (ProtoChunk) generator.fillFromNoise(Blender.empty(), random, level.structureManager(), chunk).join();
    }

    /** Order-sensitive hash of every block of the chunk and of the fluids scheduled to flow. */
    static long hash(ProtoChunk chunk) {
        long h = 1125899906842597L;
        for (it.unimi.dsi.fastutil.shorts.ShortList list : chunk.getPostProcessing()) {
            h = 31 * h + (list == null ? -1 : list.size());
            if (list != null) for (int i = 0; i < list.size(); i++) h = 31 * h + list.getShort(i);
        }
        for (LevelChunkSection section : chunk.getSections()) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) h = 31 * h + Block.getId(section.getBlockState(x, y, z));
                }
            }
        }
        return h;
    }
}

package com.polycore.mixin;

import com.polycore.util.Compat;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Skips the mixins that would conflict with other optimization mods.
 */
public class PolyCoreMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("NoiseBasedChunkGeneratorMixin")) {
            // Moonrise and C2ME already redirect these calls: two redirects on the same call would crash.
            return Compat.worldgenPoolBlockReason() == null;
        }
        if (mixinClassName.endsWith("NoiseBasedAquiferMixin")) {
            // It replaces computeSubstance: leave it to C2ME or Noisium when they are there.
            return Compat.fastAquiferBlockReason() == null;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}

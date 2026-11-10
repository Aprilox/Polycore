package com.polycore.forge;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import com.polycore.util.Hooks;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin plugin of the Forge jar (set in polycore.mixins.json by forge/build.gradle): the same as
 * com.polycore.mixin.PolyCoreMixinPlugin, and it starts the MixinExtras merged into the jar under Polycore's own
 * package (Forge 49.0.2, the only Forge for 1.20.3, does not load a nested jarJar mod).
 */
public class ForgeMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        MixinExtrasBootstrap.init();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true; // whether an optimization runs is decided at start-up by util/Conflicts
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
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        Hooks.record(targetClassName, targetClass, mixinInfo); // injections that found nothing (util/Hooks)
    }
}

package com.polycore.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Finds Polycore injections that found nothing to change. Injectors do not require a target (defaultRequire 0), so
 * that a jar declared for several Minecraft versions still starts when one of them moved a method: the injection is
 * then silently skipped. A skipped injection leaves its handler uncalled in the final class, which is what this
 * checks. Util/Conflicts turns the affected optimizations off with the reason, logged and shown in the menu.
 */
public final class Hooks {
    private record Applied(String target, ClassNode node, String mixin, List<String> handlers) {}

    /** Every Polycore mixin applied, with the target class tree (Mixin keeps changing it until the class is defined). */
    private static final List<Applied> APPLIED = new CopyOnWriteArrayList<>();

    private Hooks() {}

    /** Called by the mixin plugin after a Polycore mixin was applied to a class. */
    public static void record(String target, ClassNode targetClass, IMixinInfo mixin) {
        List<String> handlers = handlers(mixin.getClassNode(0));
        if (!handlers.isEmpty()) {
            APPLIED.add(new Applied(target.replace('.', '/'), targetClass, mixin.getClassName(), handlers));
        }
    }

    /** Methods of a mixin carrying an injector annotation (Mixin's or MixinExtras'). */
    static List<String> handlers(ClassNode mixin) {
        List<String> out = new ArrayList<>();
        if (mixin == null || mixin.methods == null) return out;
        for (MethodNode m : mixin.methods) {
            List<AnnotationNode> all = new ArrayList<>();
            if (m.visibleAnnotations != null) all.addAll(m.visibleAnnotations);
            if (m.invisibleAnnotations != null) all.addAll(m.invisibleAnnotations);
            if (all.stream().anyMatch(a -> a.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                    || a.desc.startsWith("Lcom/llamalad7/mixinextras/injector/"))) {
                out.add(m.name);
            }
        }
        return out;
    }

    /**
     * Handlers never called from the final class. Mixin renames a merged handler "prefix$id$name" (Fabric:
     * "prefix$id$modid$name"), so a call is matched on the "$name" suffix.
     * shortcut: an injector with several targets counts as applied when one of them is found; enough for one target each.
     */
    public static List<String> uncalled(ClassNode target, List<String> handlers) {
        Set<String> called = new HashSet<>();
        for (MethodNode m : target.methods) {
            if (m.instructions == null) continue;
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(target.name)) called.add(call.name);
            }
        }
        List<String> out = new ArrayList<>();
        for (String h : handlers) {
            if (called.stream().noneMatch(c -> c.equals(h) || c.endsWith("$" + h))) out.add(h);
        }
        return out;
    }

    /** Number of Polycore injections recorded so far (one per handler). */
    public static int count() {
        return APPLIED.stream().mapToInt(a -> a.handlers().size()).sum();
    }

    /** Target class (internal name, as the game runs) -> "Mixin.handler" of the injections that found nothing. */
    public static Map<String, List<String>> missing() {
        Map<String, List<String>> out = new TreeMap<>();
        for (Applied a : APPLIED) {
            String simple = a.mixin().substring(a.mixin().lastIndexOf('.') + 1);
            for (String h : uncalled(a.node(), a.handlers())) {
                out.computeIfAbsent(a.target(), k -> new ArrayList<>()).add(simple + "." + h);
            }
        }
        return out;
    }
}

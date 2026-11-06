package com.polycore.guard;

/**
 * Added to every mob by MobMixin: whether the mob skips its full AI evaluation this tick (TPS Guard, far mob AI).
 */
public interface GuardedMob {
    boolean polycore$skipAi();
}

package com.polycore.gen;

import com.polycore.PolyConfig;
import com.polycore.PolyCore;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Pool de threads reserve au calcul du terrain (bruit + biomes).
 */
public final class WorldgenPool {
    private static volatile ThreadPoolExecutor executor;
    private static final AtomicInteger THREAD_ID = new AtomicInteger();

    /** Temps cumule passe a calculer dans le pool (nanosecondes), pour le taux d'utilisation. */
    private static final LongAdder BUSY_NANOS = new LongAdder();
    private static final LongAdder TASKS = new LongAdder();

    private WorldgenPool() {}

    public static ThreadPoolExecutor get() {
        ThreadPoolExecutor ex = executor;
        if (ex == null) {
            synchronized (WorldgenPool.class) {
                ex = executor;
                if (ex == null) {
                    ex = create(PolyConfig.poolThreads());
                    executor = ex;
                }
            }
        }
        return ex;
    }

    private static ThreadPoolExecutor create(int threads) {
        int priority = PolyConfig.safeGet(PolyConfig.WORLDGEN_POOL_PRIORITY, Thread.NORM_PRIORITY);
        ClassLoader loader = WorldgenPool.class.getClassLoader();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "Polycore-Worldgen-" + THREAD_ID.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, priority)));
            t.setContextClassLoader(loader);
            t.setUncaughtExceptionHandler((th, e) -> PolyCore.LOGGER.error("Error in {}", th.getName(), e));
            return t;
        };
        ThreadPoolExecutor ex = new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), factory);
        ex.prestartAllCoreThreads();
        PolyCore.LOGGER.info("[Polycore] Worldgen pool created: {} threads (priority {})", threads, priority);
        return ex;
    }

    /** Change le nombre de threads a chaud. */
    public static void resize(int threads) {
        threads = Math.max(1, Math.min(256, threads));
        ThreadPoolExecutor ex = get();
        if (threads > ex.getMaximumPoolSize()) {
            ex.setMaximumPoolSize(threads);
            ex.setCorePoolSize(threads);
        } else {
            ex.setCorePoolSize(threads);
            ex.setMaximumPoolSize(threads);
        }
        ex.prestartAllCoreThreads();
        PolyCore.LOGGER.info("[Polycore] Worldgen pool resized: {} threads", threads);
    }

    public static void recordBusy(long nanos) {
        BUSY_NANOS.add(nanos);
        TASKS.increment();
    }

    public static long busyNanos() {
        return BUSY_NANOS.sum();
    }

    public static long tasks() {
        return TASKS.sum();
    }

    public static boolean isCreated() {
        return executor != null;
    }

    public static int size() {
        ThreadPoolExecutor ex = executor;
        return ex == null ? PolyConfig.poolThreads() : ex.getMaximumPoolSize();
    }

    public static int active() {
        ThreadPoolExecutor ex = executor;
        return ex == null ? 0 : ex.getActiveCount();
    }

    public static int queued() {
        ThreadPoolExecutor ex = executor;
        return ex == null ? 0 : ex.getQueue().size();
    }
}

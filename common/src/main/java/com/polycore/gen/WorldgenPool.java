package com.polycore.gen;

import com.polycore.config.PolyConfig;
import com.polycore.Polycore;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread pool reserved for terrain work (noise, biomes, parallel stages).
 * Never uses more threads than the machine has logical cores.
 */
public final class WorldgenPool {
    private static volatile ThreadPoolExecutor executor;
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final List<Thread> THREADS = new CopyOnWriteArrayList<>();

    /** Total time spent computing in the pool (ns), for the utilization figure. */
    private static final LongAdder BUSY_NANOS = new LongAdder();
    private static final LongAdder TASKS = new LongAdder();

    private WorldgenPool() {}

    public static int maxThreads() {
        return Runtime.getRuntime().availableProcessors();
    }

    private static int clamp(int threads) {
        return Math.max(1, Math.min(maxThreads(), threads));
    }

    public static ThreadPoolExecutor get() {
        ThreadPoolExecutor ex = executor;
        if (ex == null) {
            synchronized (WorldgenPool.class) {
                ex = executor;
                if (ex == null) {
                    ex = create(clamp(PolyConfig.poolThreads()));
                    executor = ex;
                }
            }
        }
        return ex;
    }

    private static ThreadPoolExecutor create(int threads) {
        int priority = PolyConfig.get().worldgenPoolPriority;
        ClassLoader loader = WorldgenPool.class.getClassLoader();
        ThreadFactory factory = r -> {
            Thread t = new Thread(() -> {
                try {
                    r.run();
                } finally {
                    THREADS.remove(Thread.currentThread());
                }
            }, "Polycore-Worldgen-" + THREAD_ID.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(clampPriority(priority));
            t.setContextClassLoader(loader);
            t.setUncaughtExceptionHandler((th, e) -> Polycore.LOGGER.error("Error in {}", th.getName(), e));
            THREADS.add(t);
            return t;
        };
        ThreadPoolExecutor ex = new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), factory);
        ex.prestartAllCoreThreads();
        Polycore.LOGGER.info("[Polycore] Worldgen pool created: {} threads (priority {}, {} logical cores)",
                threads, priority, maxThreads());
        return ex;
    }

    private static int clampPriority(int p) {
        return Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, p));
    }

    /** Changes the number of threads live, capped to the machine's logical cores. */
    public static void resize(int threads) {
        threads = clamp(threads);
        ThreadPoolExecutor ex = get();
        if (threads > ex.getMaximumPoolSize()) {
            ex.setMaximumPoolSize(threads);
            ex.setCorePoolSize(threads);
        } else {
            ex.setCorePoolSize(threads);
            ex.setMaximumPoolSize(threads);
        }
        ex.prestartAllCoreThreads();
        Polycore.LOGGER.info("[Polycore] Worldgen pool resized: {} threads", threads);
    }

    public static void setPriority(int priority) {
        for (Thread t : THREADS) t.setPriority(clampPriority(priority));
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
        return ex == null ? clamp(PolyConfig.poolThreads()) : ex.getMaximumPoolSize();
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

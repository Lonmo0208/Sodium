package me.jellysquid.mods.sodium.client.render.util;

import java.util.concurrent.ConcurrentLinkedQueue;

public class DeferredRenderTask {
    private static final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    public static void schedule(Runnable runnable) {
        queue.add(runnable);
    }

    public static void runAll() {
        RenderAsserts.validateCurrentThread();

        Runnable runnable;
        while ((runnable = queue.poll()) != null) {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to execute deferred render task", throwable);
            }
        }
    }
}
/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.util.concurrent.ConcurrentLinkedQueue;

final class Cleaner extends Thread {

    static class LazyInstanceHolder {
        static Cleaner instance;
        static {
            instance = new Cleaner();
            Runtime.getRuntime().addShutdownHook(instance);
        }
    }

    static Cleaner instance() {
        return LazyInstanceHolder.instance;
    }

    private final ConcurrentLinkedQueue<Runnable> tasks =
        new ConcurrentLinkedQueue<Runnable>();

    private Cleaner() { }

    void add(Runnable r) {
        tasks.add(r);
    }

    @Override
    public void run() {
        for (Runnable r : tasks) {
            try {
                r.run();
            } catch (RuntimeException ignore) { }
        }
    }
}

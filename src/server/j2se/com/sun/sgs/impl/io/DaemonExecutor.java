package com.sun.sgs.impl.io;

import java.util.concurrent.Executor;

/**
 * An {@code Executor} that {@code execute}s tasks in a new,
 * daemon {@code Thread}.
 * 
 * @author James Megquier
 * @since 1.0
 */
public class DaemonExecutor implements Executor {

    /**
     * Default constructor.
     */
    public DaemonExecutor() {
        // empty
    }

    /**
     * Executes the given command immediately in a new
     * daemon {@code Thread}.
     * 
     * @see Thread#setDaemon
     */
    public void execute(Runnable command) {
        Thread t = new Thread(command);
        t.setDaemon(true);
        t.start();
    }
}
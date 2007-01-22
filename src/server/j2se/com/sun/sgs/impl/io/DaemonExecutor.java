package com.sun.sgs.impl.io;

import java.util.concurrent.Executor;

/**
 * An {@link Executor} that executes tasks in a new {@code Thread}
 * that has been created with {@code setDaemon(true)}.
 * 
 * @author James Megquier
 */
public class DaemonExecutor implements Executor {

    /**
     * Default constructor.
     */
    public DaemonExecutor() {
        // empty
    }

    /**
     * Executes {@code command} immediately in a new daemon {@code Thread}.
     *
     * @param command the runnable task.
     *
     * @throws NullPointerException if {@code command} is null.
     *
     * @see Thread#setDaemon
     */
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command must not be null");
        }
        Thread t = new Thread(command);
        t.setDaemon(true);
        t.start();
    }
}

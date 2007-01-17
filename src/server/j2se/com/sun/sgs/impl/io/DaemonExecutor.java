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
     * {@inheritDoc}
     * <p>
     * This implementation executes the {@code command} in a new
     * daemon {@code Thread}.
     */
    public void execute(Runnable command) {
        Thread t = new Thread(command);
        t.setDaemon(true);
        t.start();
    }
}
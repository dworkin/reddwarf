package com.sun.sgs.impl.io;

import java.util.concurrent.Executor;

public class DaemonExecutor implements Executor {
    public void execute(Runnable command) {
        Thread t = new Thread(command);
        t.setDaemon(true);
        t.start();
    }
}
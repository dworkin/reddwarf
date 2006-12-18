package com.sun.sgs.impl.io;

/**
 * This class adapts a {@code java.util.concurrent.Executor} to an 
 * {@code edu.emory.mathcs.backport.java.util.concurrent.Executor} which is
 * used by Mina.  
 * <p>
 * Note that newer versions of Mina will have Java 1.5 support, so this 
 * dependancy will no longer be necessary.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ExecutorAdapter implements 
                    edu.emory.mathcs.backport.java.util.concurrent.Executor {

    private java.util.concurrent.Executor executor;
    
    public ExecutorAdapter(java.util.concurrent.Executor executor) {
        this.executor = executor;
    }
    
    public void execute(Runnable command) {
        executor.execute(command);
    }

}

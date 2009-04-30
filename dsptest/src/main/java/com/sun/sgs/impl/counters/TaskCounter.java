package com.sun.sgs.impl.counters;

import java.io.Serializable;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;

/**
 * Version of a counter that uses a pool of counters for writes and a separate
 * counter exclusively for reads. A background task will run to continuously
 * update the read counter. The value will be a bit stale, but concurrent
 * read/writes will perform well.
 * 
 * The background task will lock a single integer of the pool of counters at a
 * time and reschedule itself to lock the next one in the next task. Once it
 * reaches the end, it will write the value to the read counter.
 */
public class TaskCounter extends PoolCounter implements Serializable
{

    /***
     * The version of the serialized form.
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * {@inheritDoc}
     */
    public TaskCounter()
    {
        this(0, DEFAULT_NUM_COUNTERS);
    }

    /**
     * {@inheritDoc}
     */
    public TaskCounter(int initialValue)
    {
        this(initialValue, DEFAULT_NUM_COUNTERS);
    }

    /**
     * {@inheritDoc}
     * 
     * This counter also starts a background task to continuously update the
     * counter.
     * 
     * @param initialValue
     * @param numCounters
     */
    public TaskCounter(int initialValue, int numCounters)
    {
        super(initialValue, numCounters);
        AppContext.getTaskManager().scheduleTask(new PushTask(counters));
    }

    /**
     * Add randomly to any entry except the zeroth index. The zeroth index is
     * reserved for the read value. It may be better to use a separate object
     * for this later.
     * 
     * @param value
     */
    @Override
    public void add(int value)
    {
        int counterIndex = 1 + random.nextInt(counters.length - 1);
        counters[counterIndex].getForUpdate().add(value);
    }

    /**
     * Read the approximate counter value from the read counter.
     * 
     * @return the counter value
     */
    public int get()
    {
        return counters[0].get().get();
    }

    /**
     * Background task that continuously updates the read counter. It reads
     * all the other integers successively.
     */
    private static final class PushTask implements Task, Serializable
    {
        /**
         * The version of the serialized form.
         */
        private static final long serialVersionUID = 1L;
        
        /**
         * Pointer to the pool of internal counters.
         */
        private final ManagedReference<InternalCounter>[] counters;

        public PushTask(ManagedReference<InternalCounter>[] counters)
        {
             this.counters = counters;
        }

        @Override
        public void run() throws Exception
        {
            int value = 0;
            for (int i = 1; i < counters.length; i++) {
                value += counters[i].getForUpdate().getAndSet(0);
            }

            counters[0].getForUpdate().addAndGet(value);
            AppContext.getTaskManager().scheduleTask(this, 50);
        }
    }

}
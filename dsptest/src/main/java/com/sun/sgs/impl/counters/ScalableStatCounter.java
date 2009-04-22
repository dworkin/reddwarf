package com.sun.sgs.impl.counters;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;

public class ScalableStatCounter implements Serializable, ManagedObjectRemoval
{

    /***
     * The version of the serialized form.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The default number of internal counters
     */
    private static final int DEFAULT_NUM_COUNTERS = 10;

    /**
     * 
     */
    private static final Random rand = new Random();

    /**
     * 
     */
    private final ManagedReference<InternalCounter>[] counters;

    public ScalableStatCounter()
    {
        this(0);
    }

    public ScalableStatCounter(int initialValue)
    {
        this(initialValue, DEFAULT_NUM_COUNTERS);
    }

    public ScalableStatCounter(int initialValue, int numCounters)
    {
        counters = new ManagedReference[numCounters];
        counters[0] = AppContext.getDataManager().createReference(
                new InternalCounter(initialValue));
        for (int i = 1; i < numCounters; i++) {
            counters[i] = AppContext.getDataManager().createReference(
                    new InternalCounter(0));
        }

        AppContext.getTaskManager().scheduleTask(new PushTask(counters));
    }

    /**
     * Add randomly to any entry EXCEPT the 0-th index.
     * 
     * @param value
     * @return
     */
    public int add(int value)
    {
        int counterIndex = 1 + rand.nextInt(counters.length - 1);
        return counters[counterIndex].getForUpdate().addAndGet(value);
    }

    public int inc()
    {
        return add(1);
    }

    public int get()
    {
        return counters[0].get().get();
    }

    private static final class PushTask implements Task, Serializable
    {
        private final ManagedReference<InternalCounter>[] counters;

        /**
         * The version of the serialized form.
         */
        private static final long serialVersionUID = 1L;

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
            AppContext.getTaskManager().scheduleTask(this);
        }
    }

    private static class InternalCounter extends AtomicInteger
            implements ManagedObject
    {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public InternalCounter(int initialValue)
        {
            super(initialValue);
        }

    }

    @Override
    public void removingObject()
    {
        // TODO Auto-generated method stub

    }
}

package com.sun.sgs.kernel.scheduling;

import com.sun.sgs.kernel.*;

import org.junit.*;
import static org.junit.Assert.*;

import java.util.*;


/**
 * A JUnit 4 test suite for {@link TaskScheduler} operations and
 * implementations.
 *
 *
 * @since  1.0
 * @author David Jurgens
 */
public class SchedulerTests {

    /**
     * Enqueues a {@link Task} requests of different priorities and
     * tests whether they are dequeued in a fair manner and also with
     * respect to priorities.
     */
    @Test public void testFairDequeueRatio() {
	Priority[] priorities = new Priority[3];
	priorities[0] = NumericPriority.HIGH;
	priorities[1] = NumericPriority.NORMAL;
	priorities[2] = NumericPriority.LOW;

	AppContext appContext;

	Collection<Task> tasks = new LinkedList<Task>();
	int numOfTasksPerPriority = 1000;

	for (int i = 0; i < numOfTasksPerPriority; ++i) {
	    for (Priority p : priorities) {
		final Priority priority = p;
		Runnable r = new Runnable() {
			public void run() {
			    
			}
		    };
		
		tasks.add(new TaskImpl(r, 
				       null, // no AppContext
				       null, // no Quality
				       priority, Kernel.instance().getSystemUser()));
	    }
	}

// 	TaskScheduler scheduler = new FairPriorityTaskScheduler();
// 	for (Task t : tasks) 
// 	    scheduler.queueTask(t);
    }
}

/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.profile;

import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileCounter;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileSample;
import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.EmptyStackException;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * This simple implementation of <code>ProfileConsumer</code> is paired
 * with a <code>ProfileProducer</code> and reports all data to a
 * backing <code>ProfileCollectorImpl</code>.
 */
class ProfileConsumerImpl implements ProfileConsumer {
    // the name of the consumer
    private final String name;

    // the collector that aggregates our data
    private final ProfileCollectorImpl profileCollector;

    // the profile level for this consumer
    private ProfileLevel profileLevel;
    
    private final AtomicInteger opIdCounter;
    // the ops, samples, and counters for this consumer
    private final ConcurrentHashMap<String, ProfileOperation> ops;
    private final ConcurrentHashMap<String, ProfileSample> samples;
    private final ConcurrentHashMap<String, ProfileCounter> counters;

    /**
     * Creates an instance of <code>ProfileConsumerImpl</code>.
     *
     * @param profileCollector the backing <code>ProfileCollectorImpl</code>
     * @param name an identifier for this consumer
     */
    ProfileConsumerImpl(ProfileCollectorImpl profileCollector, String name) {
        if (profileCollector == null) {
            throw new NullPointerException("The collector must not be null");
        }

        this.name = name;
        this.profileCollector = profileCollector;
        // default profile level is taken from the collector
        this.profileLevel = profileCollector.getDefaultProfileLevel();

        opIdCounter = new AtomicInteger(0);
        ops = new ConcurrentHashMap<String, ProfileOperation>();
        samples = new ConcurrentHashMap<String, ProfileSample>(); 
        counters = new ConcurrentHashMap<String, ProfileCounter>(); 
    }
    
    /** {@inheritDoc} */
    public ProfileLevel getProfileLevel() {
        return profileLevel;
    }

    /** {@inheritDoc} */
    public void setProfileLevel(ProfileLevel level) {
        profileLevel = level;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if no more operations can be registered
     */
    public ProfileOperation registerOperation(String name, 
                                              ProfileLevel minLevel) 
    {
	ProfileOperation op = ops.get(name);

	if (op == null) {
            int opId = opIdCounter.getAndIncrement();
	    op = new ProfileOperationImpl(name, opId, minLevel);
	    ops.putIfAbsent(name, op);
	    op = ops.get(name);
	}

	PropertyChangeEvent event = 
	    new PropertyChangeEvent(this, 
                                    "com.sun.sgs.profile.newop", null, op);
        profileCollector.notifyListeners(event);
        return op;
    }

    /**
     * {@inheritDoc}
     */
    public ProfileCounter registerCounter(String name, boolean taskLocal,
                                          ProfileLevel minLevel) 
    {
        if (counters.containsKey(name)) {
            return counters.get(name);
        } else {
            ProfileCounter counter;
            if (taskLocal) {
                counter = new TaskLocalProfileCounter(name, minLevel);
            } else {
                counter = new AggregateProfileCounter(name, minLevel);
            }
            counters.put(name, counter);
            return counter;
        }
    }

    /**
     * {@inheritDoc}
     */
    public ProfileSample registerSampleSource(String name, boolean taskLocal,
					       long maxSamples, 
                                               ProfileLevel minLevel) 
    {
        // REMINDER: this assume maxSamples isn't necessary when
	// deciding whether a sample source is already present.
        if (samples.containsKey(name)) {
            return samples.get(name);
        } else {
            ProfileSample sample;
            if (taskLocal) {
                sample = new TaskLocalProfileSample(name, minLevel);
            } else {
                sample = new AggregateProfileSample(name, maxSamples, minLevel);
            }
            samples.put(name, sample);
            return sample;
        }
    }
    
    /** {@inheritDoc} */
    public String getName() {
        return name;
    }
    
    /**
     * Package private method to access all operations. Used by the
     * {@code ProfileCollector}.
     * @return a snapshot of the registered operations
     */
    Collection<ProfileOperation> getOperations() {
        return ops.values();
    }

    /**
     * A private implementation of {@code ProfileOperation} that is
     * returned from any call to {@code createOperation}.
     */
    private class ProfileOperationImpl implements ProfileOperation {
        private final String opName;
        private final int opId;
        private final ProfileLevel minLevel;
        ProfileOperationImpl(String opName, int opId, ProfileLevel minLevel) {
            this.opName = opName;
            this.opId = opId;
            this.minLevel = minLevel;
        }

        public String getOperationName() {
            return opName;
        }

        public int getId() {
            return opId;
        }

        public String toString() {
            return opName;
        }
        /**
         * Note that this throws {@code IllegalStateException} if called
         * outside the scope of a started task.
         */
        public void report() {
            // If the minimum level we want to profile at is greater than
            // the current level, just return.
            if (minLevel.ordinal() > profileLevel.ordinal()) {
                return;
            }
            try {
                ProfileReportImpl profileReport = 
                        profileCollector.getCurrentProfileReport();
                profileReport.ops.add(this);
            } catch (EmptyStackException ese) {
                throw new IllegalStateException("Cannot report operation " +
                                                "because no task is active");
            }
        }
    }

    /**
     * A private implementation of {@code ProfileCounter} that is
     * returned from any call to {@code registerCounter}.
     */
    private abstract class AbstractProfileCounter implements ProfileCounter {
        private final String name;
        private final boolean taskLocal;
        protected final ProfileLevel minLevel;
        AbstractProfileCounter(String name, boolean taskLocal, 
                               ProfileLevel minLevel) 
        {
            this.name = name;
            this.taskLocal = taskLocal;
            this.minLevel = minLevel;
        }
        public String getCounterName() {
            return name;
        }
        public boolean isTaskLocal() {
            return taskLocal;
        }
    }

    /**
     * The concrete implementation of {@code AbstractProfileCounter} used
     * for counters that aggregate across tasks.
     */
    private class AggregateProfileCounter extends AbstractProfileCounter {
        private AtomicLong count;
        AggregateProfileCounter(String name, ProfileLevel minLevel) {
            super(name, false, minLevel);
            count = new AtomicLong();
        }
        public void incrementCount() {
            // If the minimum level we want to profile at is greater than
            // the current level, just return.
            if (minLevel.ordinal() > profileLevel.ordinal()) {
                return;
            }

            try {
                ProfileReportImpl profileReport = 
                        profileCollector.getCurrentProfileReport();
                profileReport.updateAggregateCounter(getCounterName(),
                                                     count.incrementAndGet());
            } catch (EmptyStackException ese) {
                throw new IllegalStateException("Cannot report counter " +
                                                "because no task is active");
            }
        }
        public void incrementCount(long value) {
            // If the minimum level we want to profile at is greater than
            // the current level, just return.
            if (minLevel.ordinal() > profileLevel.ordinal()) {
                return;
            }
            
            if (value < 0) {
                throw new IllegalArgumentException("Increment value must be " +
                                                   "greater than zero");
            }

            try {
                ProfileReportImpl profileReport = 
                        profileCollector.getCurrentProfileReport();
                profileReport.updateAggregateCounter(getCounterName(),
                                               count.addAndGet(value));
            } catch (EmptyStackException ese) {
                throw new IllegalStateException("Cannot report counter " +
                                                "because no task is active");
            }
        }
    }


    /**
     * The concrete implementation of {@code AbstractProfileCounter} used
     * for counters that are local to tasks.
     */
    private class TaskLocalProfileCounter extends AbstractProfileCounter {
        TaskLocalProfileCounter(String name, ProfileLevel minLevel) {
            super(name, true, minLevel);
        }
        public void incrementCount() {
            // If the minimum level we want to profile at is greater than
            // the current level, just return.
            if (minLevel.ordinal() > profileLevel.ordinal()) {
                return;
            }
            
            try {
                ProfileReportImpl profileReport =
                        profileCollector.getCurrentProfileReport();
                profileReport.incrementTaskCounter(getCounterName(), 1L);
            } catch (EmptyStackException ese) {
                throw new IllegalStateException("Cannot report counter " +
                                                "because no task is active");
            }
        }
        public void incrementCount(long value) {
            // If the minimum level we want to profile at is greater than
            // the current level, just return.
            if (minLevel.ordinal() > profileLevel.ordinal()) {
                return;
            }
            
            if (value < 0) {
                throw new IllegalArgumentException("Increment value must be " +
                                                   "greater than zero");
            }

            try {
                ProfileReportImpl profileReport =
                        profileCollector.getCurrentProfileReport();
                profileReport.incrementTaskCounter(getCounterName(), value);
            } catch (EmptyStackException ese) {
                throw new IllegalStateException("Cannot report counter " +
                                                "because no task is active");
            }
        }
    }

    /** A base-class for creating {@code ProfileSample}s. */
    private abstract class AbstractProfileSample implements ProfileSample {
        private final String name;
        private final boolean taskLocal;
        protected final ProfileLevel minLevel;

        AbstractProfileSample(String name, boolean taskLocal, 
                              ProfileLevel minLevel) 
        {
            this.name = name;
            this.taskLocal = taskLocal;
            this.minLevel = minLevel;
        }

        public String getSampleName() {
            return name;
        }

        public boolean isTaskLocal() {
            return taskLocal;
        }
    }

    /** The task-local implementation of {@code ProfileSample} */
    private class TaskLocalProfileSample extends AbstractProfileSample {
	TaskLocalProfileSample(String name, ProfileLevel minLevel) {
            super(name, true, minLevel);
        }
        public void addSample(long value) {
            // If the minimum level we want to profile at is greater than
            // the current level, just return.
            if (minLevel.ordinal() > profileLevel.ordinal()) {
                return;
            }
            try {
                ProfileReportImpl profileReport =
                        profileCollector.getCurrentProfileReport();
                profileReport.addLocalSample(getSampleName(), value);
            } catch (EmptyStackException ese) {
                throw new IllegalStateException("Cannot report sample " +
                                                "because no task is active");
            }
        }
    }

    /**
     * The {@code ProfileSample} implementation that collects samples
     * for the lifetime the program.
     */
    private class AggregateProfileSample extends AbstractProfileSample {    
	private final LinkedList<Long> samples;
	private final long maxSamples;       
	
	AggregateProfileSample(String name, long maxSamples, 
                               ProfileLevel minLevel) 
        {
            super(name, false, minLevel);
	    this.maxSamples = maxSamples;
	    samples = new LinkedList<Long>();
        }

        public void addSample(long value) {
            // If the minimum level we want to profile at is greater than
            // the current level, just return.
            if (minLevel.ordinal() > profileLevel.ordinal()) {
                return;
            }
            
	    if (samples.size() == maxSamples) {
		samples.removeFirst(); // remove oldest
            }
	    samples.add(value);
	    // NOTE: we return a sub-list view to ensure that the
	    // ProfileReport only sees the samples that were added,
	    // during its lifetime.  Creating a sub-list view is much
	    // faster than copying the list, and is suitable for this
	    // situation.  However, we still rely on the the
	    // ProfileReport to make the view unmodifyable.  This
	    // isn't done here since we can avoid doing that operation
	    // until the getSamples() call, instead of having to do it
	    // for each sample addition
            try {
                ProfileReportImpl profileReport =
                        profileCollector.getCurrentProfileReport();
                profileReport.registerAggregateSamples(getSampleName(), 
					 samples.subList(0, samples.size()));
            } catch (EmptyStackException ese) {
                throw new IllegalStateException("Cannot report sample " +
                                                "because no task is active");
            }
        }
    }
}

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

import com.sun.sgs.profile.AggregateProfileCounter;
import com.sun.sgs.profile.AggregateProfileOperation;
import com.sun.sgs.profile.AggregateProfileSample;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileCounter;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileSample;
import com.sun.sgs.profile.TaskProfileCounter;
import com.sun.sgs.profile.TaskProfileOperation;
import com.sun.sgs.profile.TaskProfileSample;
import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.EmptyStackException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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

    /** {@inheritDoc} */
    public synchronized ProfileOperation createOperation(String name, 
            ProfileDataType type, ProfileLevel minLevel) 
    {
	ProfileOperation op = ops.get(name);
        
	if (op == null) {
            switch (type) {
                case TASK:
                    op = new TaskProfileOperationImpl(name, type, minLevel);
                    break;
                case AGGREGATE:
                    op = new AggregateProfileOperationImpl(name, type, 
                                                           minLevel);
                    break;
                case TASK_AGGREGATE:
                default:
                    op = new AggregateTaskProfileOperationImpl(name, type, 
                                                               minLevel);
                    break;
            }
	    ops.put(name, op);
	} else {
            // Check minLevel and type
            if (op instanceof AbstractProfileData) {
                AbstractProfileData oldData = (AbstractProfileData) op;
                ProfileLevel oldLevel = oldData.getMinLevel();
                if (oldLevel != minLevel) {
                    throw new IllegalArgumentException(
                            "Operation with name " + name + 
                            " already created, but with level " +  oldLevel);
                }
                
                ProfileDataType oldType = oldData.getType();
                if (oldType != type) {
                    throw new  IllegalArgumentException(
                            "Operation with name " + name + 
                            " already created, but with type " +  oldType);
                }
            } else {
                // Can't happen in this implementation
                throw new IllegalArgumentException(
                        "Operation with name " + name + 
                        " already created with an unknown type");
            }   
        }

	PropertyChangeEvent event = 
	    new PropertyChangeEvent(this, 
                                    "com.sun.sgs.profile.newop", null, op);
        profileCollector.notifyListeners(event);
        return op;
    }

    /** {@inheritDoc} */
    public synchronized ProfileCounter createCounter(String name, 
            ProfileDataType type, ProfileLevel minLevel) 
    {
        if (counters.containsKey(name)) {
            ProfileCounter oldCounter = counters.get(name);
            // Check minLevel and type
            if (oldCounter instanceof AbstractProfileData) {
                AbstractProfileData oldData = (AbstractProfileData) oldCounter;
                ProfileLevel oldLevel = oldData.getMinLevel();
                if (oldLevel != minLevel) {
                    throw new IllegalArgumentException(
                            "Counter with name " + name + 
                            " already created, but with level " +  oldLevel);
                }
                
                ProfileDataType oldType = oldData.getType();
                if (oldType != type) {
                    throw new  IllegalArgumentException(
                            "Counter with name " + name + 
                            " already created, but with type " +  oldType);
                }
            } else {
                // Can't happen in this implementation
                throw new IllegalArgumentException(
                        "Counter with name " + name + 
                        " already created with an unknown type");
            }   
            return oldCounter;
        } else {
            ProfileCounter counter;
            switch (type) {
                case TASK:
                    counter = new TaskProfileCounterImpl(name, type, minLevel);
                    break;
                case AGGREGATE:
                    counter = new AggregateProfileCounterImpl(name, type, 
                                                              minLevel);
                    break;
                case TASK_AGGREGATE:
                default:
                    counter = 
                            new AggregateTaskProfileCounterImpl(name, type, 
                                                                minLevel);
                    break;
            }
            
            counters.put(name, counter);
            return counter;
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized ProfileSample createSample(String name, 
            ProfileDataType type, long maxSamples, ProfileLevel minLevel) 
    {
        // REMINDER: this assume maxSamples isn't necessary when
	// deciding whether a sample source is already present.
        if (samples.containsKey(name)) {
            ProfileSample oldSample = samples.get(name);
            // Check minLevel and type
            if (oldSample instanceof AbstractProfileData) {
                AbstractProfileData oldData = (AbstractProfileData) oldSample;
                ProfileLevel oldLevel = oldData.getMinLevel();
                if (oldLevel != minLevel) {
                    throw new IllegalArgumentException(
                            "Sample with name " + name + 
                            " already created, but with level " +  oldLevel);
                }
                
                ProfileDataType oldType = oldData.getType();
                if (oldType != type) {
                    throw new  IllegalArgumentException(
                            "Sample with name " + name + 
                            " already created, but with type " +  oldType);
                }
            } else {
                // Can't happen in this implementation
                throw new IllegalArgumentException(
                        "Sample with name " + name + 
                        " already created with an unknown type");
            }   
            return samples.get(name);
        } else {
            ProfileSample sample;
            switch (type) {
                case TASK:
                    sample = new TaskProfileSampleImpl(name, type, minLevel);
                    break;
                case AGGREGATE:
                    sample =  new AggregateProfileSampleImpl(name, type,
                                                             maxSamples, 
                                                             minLevel);
                    break;
                case TASK_AGGREGATE:
                default:
                    sample = 
                            new AggregateTaskProfileSampleImpl(name, type,
                                                               maxSamples, 
                                                               minLevel);
                    break;
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

    private abstract class AbstractProfileData {
        protected final String name;
        protected final ProfileLevel minLevel;
        /* Type used for error checking in factory method */
        private final ProfileDataType type;
        AbstractProfileData(String name, ProfileDataType type, 
                            ProfileLevel minLevel) 
        {
            this.name = name;
            this.type = type;
            this.minLevel = minLevel;
        }

        public String getName() {
            return name;
        }

        public String toString() {
            return name;
        }
        
        ProfileLevel getMinLevel() {
            return minLevel;
        }
        
        ProfileDataType getType() {
            return type;
        }
    }
    /**
     * A private implementation of {@code ProfileOperation} that is
     * returned from any call to {@code createOperation}.
     */
    private class AggregateProfileOperationImpl 
            extends AbstractProfileData
            implements AggregateProfileOperation 
    {
        protected final AtomicLong count = new AtomicLong();
        AggregateProfileOperationImpl(String opName, ProfileDataType type,
                                      ProfileLevel minLevel) {
            super(opName, type, minLevel);
        }

        /** {@inheritDoc} */
        public void clearCount() {
            count.set(0);
        }

        /** {@inheritDoc} */
        public long getCount() {
            return count.get();
        }

        /** {@inheritDoc} */
        public void report() {
            // If the minimum level we want to profile at is greater than
            // the current level, just return.
            if (minLevel.ordinal() > profileLevel.ordinal()) {
                return;
            }
            count.incrementAndGet();
        }
    }
    
    private class TaskProfileOperationImpl 
            extends AbstractProfileData
            implements TaskProfileOperation
    {
        TaskProfileOperationImpl(String opName, ProfileDataType type, 
                                 ProfileLevel minLevel) {
            super(opName, type, minLevel);
        }
        
        /** {@inheritDoc} */
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
    
    private class AggregateTaskProfileOperationImpl
            extends AggregateProfileOperationImpl
            implements TaskProfileOperation
    {
        private final TaskProfileOperationImpl taskOperation;
        AggregateTaskProfileOperationImpl(String opName, ProfileDataType type, 
                                          ProfileLevel minLevel) 
        {
            super(opName, type, minLevel);
            taskOperation = 
                    new TaskProfileOperationImpl(opName, type, minLevel);
        }
        
        /** {@inheritDoc} */
        public void report() {
            super.report();
            taskOperation.report();
        }
    }

    /**
     * The concrete implementation of {@code AbstractProfileCounter} used
     * for counters that aggregate across tasks.
     */
    private class AggregateProfileCounterImpl
            extends AbstractProfileData 
            implements AggregateProfileCounter
    {
        private final AtomicLong count = new AtomicLong();
        AggregateProfileCounterImpl(String name, ProfileDataType type,
                                    ProfileLevel minLevel) {
            super(name, type, minLevel);
        }
        /** {@inheritDoc} */
        public void incrementCount() {
            // If the minimum level we want to profile at is greater than
            // the current level, just return.
            if (minLevel.ordinal() > profileLevel.ordinal()) {
                return;
            }
            count.incrementAndGet();
        }
        /** {@inheritDoc} */
        public void incrementCount(long value) {
            // If the minimum level we want to profile at is greater than
            // the current level, just return.
            if (minLevel.ordinal() > profileLevel.ordinal()) {
                return;
            }
            
            if (value < 0) {
                throw new IllegalArgumentException("Increment value must be " +
                                                   "non-negative");
            }
            count.addAndGet(value);
        }

        /** {@inheritDoc} */
        public void clearCount() {
            count.set(0);
        }

        /** {@inheritDoc} */
        public long getCount() {
            return count.get();
        }
    }


    /**
     * The concrete implementation of {@code AbstractProfileCounter} used
     * for counters that are local to tasks.
     */
    private class TaskProfileCounterImpl 
            extends AbstractProfileData 
            implements TaskProfileCounter
    {
        TaskProfileCounterImpl(String name, ProfileDataType type, 
                               ProfileLevel minLevel) {
            super(name, type, minLevel);
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
                profileReport.incrementTaskCounter(name, 1L);
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
                profileReport.incrementTaskCounter(name, value);
            } catch (EmptyStackException ese) {
                throw new IllegalStateException("Cannot report counter " +
                                                "because no task is active");
            }
        }
    }

    private class AggregateTaskProfileCounterImpl
            extends AggregateProfileCounterImpl
            implements TaskProfileCounter 
    {
        private final TaskProfileCounterImpl taskCounter;
        AggregateTaskProfileCounterImpl(String opName, ProfileDataType type,
                                        ProfileLevel minLevel) 
        {
            super(opName, type, minLevel);
            taskCounter = new TaskProfileCounterImpl(opName, type, minLevel);
        }
        
        /** {@inheritDoc} */
        public void incrementCount() {
            super.incrementCount();
            taskCounter.incrementCount();
        }
        
        /** {@inheritDoc} */
        public void incrementCount(long value) {
            super.incrementCount(value);
            taskCounter.incrementCount(value);
        }
    }

    /** The task-local implementation of {@code ProfileSample} */
    private class TaskProfileSampleImpl 
            extends AbstractProfileData 
            implements TaskProfileSample
    {
	TaskProfileSampleImpl(String name, ProfileDataType type, 
                              ProfileLevel minLevel) {
            super(name, type, minLevel);
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
                profileReport.addLocalSample(name, value);
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
    private class AggregateProfileSampleImpl
            extends AbstractProfileData 
            implements AggregateProfileSample
    {    
	private final LinkedList<Long> samples = new LinkedList<Long>();
	private final long maxSamples;       
	
        /** 
         * Smoothing factor for exponential smoothing, between 0 and 1.
         * A value closer to one provides less smoothing of the data, and
         * more weight to recent data;  a value closer to zero provides more
         * smoothing but is less responsive to recent changes.
         */
        private float smoothingFactor = (float) 0.9;
        private long minSampleValue = Long.MAX_VALUE;
        private long maxSampleValue = Long.MIN_VALUE;
        private final ExponentialAverage avgSampleValue = 
                new ExponentialAverage();
        
	AggregateProfileSampleImpl(String name, ProfileDataType type,
                                   long maxSamples, ProfileLevel minLevel) 
        {
            super(name, type, minLevel);
	    this.maxSamples = maxSamples;
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
            // Update the statistics
            if (value > maxSampleValue) {
                maxSampleValue = value;
            } 
            if (value < minSampleValue) {
                minSampleValue = value;
            }
            avgSampleValue.update(value);

        }
        
        /** {@inheritDoc} */
         public synchronized List<Long> getSamples() {
             return new LinkedList<Long>(samples);
         }
         /** {@inheritDoc} */
         public synchronized void clearSamples() {
             samples.clear();
             avgSampleValue.clear();
             maxSampleValue = Long.MIN_VALUE;
             minSampleValue = Long.MAX_VALUE;
         }

        /** {@inheritDoc} */
        public synchronized double getAverage() {
            return avgSampleValue.avg;
        }

        /** {@inheritDoc} */
        public synchronized long getMaxSample() {
            return maxSampleValue;
        }

        /** {@inheritDoc} */
        public synchronized long getMinSample() {
            return minSampleValue;
        }
         
        private class ExponentialAverage {

            private double last;
            double avg;

            void update(long sample) {
                // calculate the exponential smoothed data:
                // current avg = 
                //   (smoothingFactor * current sample) 
                // + ((1 - smoothingFactor) * last avg)
                // This is the same as:
                // current avg =
                //    (smoothingFactor * current sample)
                //  + (last avg)
                //  - (smoothingFactor * last avg)
                // Which simplifies to:
                // current avg =
                //   (current sample - lastAvg) * smoothingFactor + lastAvg

                avg = (sample - last) * smoothingFactor + last;
                last = avg;
//                System.out.println("avg: " + avg);
            }
            
            void clear() {
                last = 0;
                avg = 0;
            }
        }
    }
    
    private class AggregateTaskProfileSampleImpl
            extends AggregateProfileSampleImpl
            implements TaskProfileSample
    {
        private final TaskProfileSample taskSample;
        AggregateTaskProfileSampleImpl(String name, ProfileDataType type,
                                       long maxSamples, 
                                       ProfileLevel minLevel) 
        {
            super(name, type, maxSamples, minLevel);
            taskSample = new TaskProfileSampleImpl(name, type, minLevel);
        }
        
        /** {@inheritDoc} */
        public void addSample(long value) {
            super.addSample(value);
            taskSample.addSample(value);
        }
    }
}

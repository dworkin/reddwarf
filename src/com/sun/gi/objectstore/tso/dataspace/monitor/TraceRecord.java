
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.ObjectStreamException;
import java.io.InvalidObjectException;

public abstract class TraceRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private long startTime;
    private long endTime;

    protected TraceRecord() { }

    /**
     * Constructs a TraceRecord with a given starting time.  The
     * ending time is implicitly taken to be the time when the
     * constructor is called (which is accurate if the trace record is
     * created immediately after the operation has finished).
     *
     * @param startTime the time when the operation was started (as
     * returned by System.currentTimeMillis())
     */
    public TraceRecord(long startTime) {
	this(startTime, System.currentTimeMillis());
    }

    public TraceRecord(long startTime, long endTime) {
	this.startTime = startTime;
	this.endTime = endTime;
    }

    /**
     * Returns the starting time of the operation for which this is a
     * trace record.
     *
     * @return the starting time, in milliseconds since the begining
     * of the epoch, of the start of the operation
     */
    public long getStartTime() {
	return startTime;
    }

    /**
     * Returns the ending time of the operation for which this is a
     * trace record.
     *
     * @return the ending time, in milliseconds since the begining
     * of the epoch, of the start of the operation
     */
    public long getEndTime() {
	return endTime;
    }

    /** 
     * Returns the unqualified name of the class of this object.  <p>
     *
     * From com.sun.neuromancer.demo1.impl.record.RecordImpl.java.
     *
     * @param the unqualified name of the class of this
     */
    public String getUnqualifiedClassName() {
	String name = getClass().getName();
	int i = name.lastIndexOf('.');
	return (i != -1) ? name.substring(i + 1) : name;
    }

    public String toString() {
	return getUnqualifiedClassName() + "[" +
		"startTime:" + getStartTime() +
		",endTime:" + getEndTime() +
		"]";
    }

    /**
     * Replay the operation captured by this trace record against the
     * given {@link DataSpace}.
     *
     * @param dataSpace the DataSpace on which to execute the
     * operation
     *
     * @param replayState the state of the replay, including the mapping
     * between each object identifiers in the original trace and its
     * values during replay 
     */
    public abstract void replay(DataSpace dataSpace, ReplayState replayState);

    /** 
     * Causes deserialization to fail if there is no data for this
     * class. 
     */
    private void readObjectNoData() throws ObjectStreamException {
	throw new InvalidObjectException("no data");
    }
}

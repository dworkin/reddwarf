package com.sun.gi.objectstore.tso.dataspace.monitor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;

public class LookupTraceRecord extends TraceRecord implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected final long id;
    protected final String name;

    public LookupTraceRecord(long startTime, String name, long id) {
        super(startTime);
        this.id = id;
        this.name = name;
    }

    public void replay(DataSpace dataSpace, ReplayState replayState) {
        // OK.
        long gotId = dataSpace.lookup(name);
        long expectedId = replayState.getMappedOid(this.id);
        // check return (but *can* fail, because lookup can fail)

        if (gotId != expectedId) {
            System.out.println("Contradiction: name = (" + name + ")"
                    + " expected oid = " + expectedId + " got = " + gotId);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException,
            ClassNotFoundException {
        in.defaultReadObject();
    }
}

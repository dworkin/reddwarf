
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class GetObjBytesTraceRecord extends TraceRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final long id;
    protected final int length;

    public GetObjBytesTraceRecord(long startTime, long id, int length) {
	super(startTime);
	this.id = id;
	this.length = length;
    }

    public void replay(DataSpace dataSpace, ReplayState replayState) {
	// OK
	long mappedId = replayState.getMappedOid(this.id);
	// check return.

	byte[] res = dataSpace.getObjBytes(mappedId);

	if (res.length != this.length) {
	    System.out.println("Contradiction: oid " + mappedId +
		    " length = " + this.length +
		    " new length = " + res.length);
	}
    }

    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}


package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class GetAppIdTraceRecord extends TraceRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final long id;

    public GetAppIdTraceRecord(long startTime, long id) {
	super(startTime);
	this.id = id;
    }

    public void replay(DataSpace dataSpace, ReplayState replayState) {
	long id = dataSpace.getAppID();

	// Maybe it's not an error if the replay dataspace does not
	// know its AppId? -DJE

	if (id != this.id) {
	    System.out.println("Contradiction: old appId = " + this.id +
		    " new = " + id);
	}
    }

    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}

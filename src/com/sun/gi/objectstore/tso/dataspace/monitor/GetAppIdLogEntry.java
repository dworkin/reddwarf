
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class GetAppIdLogEntry extends LogEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final long id;

    public GetAppIdLogEntry(long startTime, long id) {
	super(startTime);
	this.id = id;
    }

    public void replay(DataSpace dataSpace) {
	long id = dataSpace.getAppID();
	if (id != this.id) {
	    // XXX: ??
	}
    }
    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}

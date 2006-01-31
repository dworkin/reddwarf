
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class GetObjBytesLogEntry extends LogEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final long id;
    protected final int length;

    public GetObjBytesLogEntry(long startTime, long id, int length) {
	super(startTime);
	this.id = id;
	this.length = length;
    }

    public void replay(DataSpace dataSpace) {
	byte[] res = dataSpace.getObjBytes(id);
	if (res.length != length) {
	    // XXX ??
	}
    }
    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}

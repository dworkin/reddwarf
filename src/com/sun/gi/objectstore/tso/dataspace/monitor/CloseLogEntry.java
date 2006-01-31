
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class CloseLogEntry extends LogEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    public CloseLogEntry(long startTime) {
	super(startTime);
    }

    public void replay(DataSpace dataSpace) {
	dataSpace.close();
    }
    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}

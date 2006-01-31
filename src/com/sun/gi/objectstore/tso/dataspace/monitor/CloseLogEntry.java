
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;

public class CloseLogEntry extends LogEntry implements Serializable {

    public CloseLogEntry(long startTime) {
	super(startTime);
    }

    public void replay(DataSpace dataSpace) {
	dataSpace.close();
    }
}


package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;

public class ClearLogEntry extends LogEntry implements Serializable {

    public ClearLogEntry(long startTime) {
	super(startTime);
    }

    public void replay(DataSpace dataSpace) {
	dataSpace.clear();
    }
}

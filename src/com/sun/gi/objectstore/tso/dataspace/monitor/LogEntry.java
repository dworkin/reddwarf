
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;

public interface LogEntry {

    long getStartTime();

    long getEndTime();

    abstract void replay(DataSpace dataSpace);
}

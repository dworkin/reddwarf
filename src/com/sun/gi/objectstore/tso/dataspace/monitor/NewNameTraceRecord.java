
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class NewNameTraceRecord extends TraceRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final String name;

    public NewNameTraceRecord(long startTime, String name) {
	super(startTime);
	this.name = name;
    }

    public void replay(DataSpace dataSpace, ReplayState replayState) {
	// OK.
	boolean rc = false; /*dataSpace.newName(name);*/  //TODO: sten commented out to fix build
	boolean expectedRc = replayState.setNewName(name);

	if (rc != expectedRc) {
	    System.out.println("Contradiction: name = (" + name + ")" +
		    " expected rc = " + expectedRc + " got = " + rc);
	}
    }

    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}

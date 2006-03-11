/**
 *
 * <p>Title: RoomObject.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.apps.swordworld;

import com.sun.gi.logic.GLO;

/**
 * This Game Logic Class is used to create Game Logic Objects that
 * rerpesent items in the Room.  Currently only one tem is created
 * on startup by SwordWorldBoot-- a shiney sword.  (Hence the
 * name of the demo.)
 * <p>Title: RoomObject.java</p>
 * <p>Description: </p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class RoomObject implements GLO {
	//This is a string describing the object
	private String description;
	
	/**
	 * @param string
	 */
	public RoomObject(String string) {
		description = string;
	}

	/**
	 * This method is called by the Room GLO in order to assist
	 * it in putting together a list of room contents.
	 * @return The item's description
	 */
	public String getDescription() {
		// TODO Auto-generated method stub
		return description;
	}

	
	
	
}

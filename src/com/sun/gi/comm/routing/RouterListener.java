package com.sun.gi.comm.routing;

import java.nio.ByteBuffer;

/**
 * This class defines a listener who listens for router messages that have to be
 * propigated up to the second tier of the server stack.
 * @author jeffpk
 *
 */

public interface RouterListener {
	public void serverMessage(UserID from, ByteBuffer data, boolean reliable);
}

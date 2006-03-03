/*
 * $Id: Area.java 460 2006-03-03 06:41:36Z jmegq $
 *
 * Copyright 2006 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * -Redistributions of source code must retain the above copyright
 * notice, this  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF  OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

package com.sun.gi.apps.jnwn;

import java.io.Serializable;

public class Pos implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;

    public long  timestamp;
    public float x;
    public float y;
    public float z;
    public float heading; // in radians
    public float velocity;

    public Pos(long timestamp, float x, float y, float z,
	    float heading, float velocity) {
	update(timestamp, x, y, z, heading, velocity);
    }

    public void update(long timestamp, float x, float y, float z,
	    float heading, float velocity) {
	this.timestamp = timestamp;
	this.x = x;
	this.y = y;
	this.z = z;
	this.heading = heading;
	this.velocity = velocity;
    }

    public Pos clone() {
	try {
	    return (Pos) super.clone();
	} catch (CloneNotSupportedException e) {
	    return null;
	}
    }

    public float distance(final Pos o) {
	final float dX = x - o.x;
	final float dY = y - o.y;
	final float dZ = z - o.z;
	return (float) Math.sqrt((dX * dX) + (dY * dY) + (dZ * dZ));
    }
}

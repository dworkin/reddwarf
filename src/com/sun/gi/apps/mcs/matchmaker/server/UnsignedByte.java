package com.sun.gi.apps.mcs.matchmaker.server;

/**
 * 
 * <p>Title: UnsignedByte.java</p>
 * 
 * <p>Description:  This class represents an unsigned byte value.  It uses
 *  an int to store the value since a regular Java signed byte isn't wide enough.  
 *  
 *  This class exists primarily to mark an int to be intrepreted
 *  as an unsigned byte instead of an int. </p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */

class UnsignedByte extends Number {
	
	private int value;
	
	public UnsignedByte(int num) {
		this.value = num;
	}
	
	public float floatValue() {
		return value;
	}
	
	public long longValue() {
		return value;
	}
	
	public double doubleValue() {
		return value;
	}
	
	public int intValue() {
		return value;
	}
	
	public byte byteValue() {
		return (byte) value;
	}
	
	public boolean equals(Object obj) {
		return (obj instanceof UnsignedByte && ((UnsignedByte) obj).intValue() == value);
	}
	
	public int hashCode() {
		return value;
	}
	
	public int compareTo(UnsignedByte b) {
		return b.intValue() == value ? 0 : b.intValue() > value ? -1 : 1;
	}
	
}

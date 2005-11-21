package com.sun.gi.utils.types;

public class BYTEARRAY implements Comparable {
	byte[] data;

	public BYTEARRAY(byte[] data) {
		this.data = new byte[data.length];
		System.arraycopy(data, 0, this.data, 0, data.length);
	}

	/**
	 * compareTo
	 * 
	 * @param o
	 *            Object
	 * @return int
	 */
	public int compareTo(Object o) {
		byte[] otherdata;
		if (o instanceof byte[]){
			otherdata = (byte[])o;
		} else {
			otherdata = ((BYTEARRAY) o).data;
		}
		if (data.length < otherdata.length) {
			return -1;
		} else if (data.length > otherdata.length) {
			return 1;
		} else {
			for (int i = 0; i < data.length; i++) {
				if (data[i] < otherdata[i]) {
					return -1;
				} else if (data[i] > otherdata[i]) {
					return 1;
				}
			}
			return 0;
		}
	}

	/**
	 * data
	 * 
	 * @return byte[]
	 */
	public byte[] data() {
		return data;
	}

	/**
	 * Indicates whether some other object is "equal to" this one.
	 * 
	 * @param obj
	 *            the reference object with which to compare.
	 * @return <code>true</code> if this object is the same as the obj
	 *         argument; <code>false</code> otherwise.
	 * @todo Implement this java.lang.Object method
	 */
	public boolean equals(Object obj) {
		return (compareTo(obj) == 0);
	}

	/**
	 * Returns a hash code value for the object.
	 * 
	 * @return a hash code value for this object.
	 * @todo Implement this java.lang.Object method
	 */
	public int hashCode() {

		int hash = 0;
		int len = data.length;
		for (int i = 0; i < len; i++) {
			hash = 31 * hash + data[i];
		}
		return hash;
	}

	public String toString() {
		return ("BYTEARRAY(" + toHex() + ")");
	}

	/**
	 * @return
	 */
	public String toHex() {
		return StringUtils.bytesToHex(data);
	}

}

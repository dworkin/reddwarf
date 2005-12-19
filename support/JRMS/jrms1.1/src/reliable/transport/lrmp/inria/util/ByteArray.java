/**
 * *******************************************************************
 * This Software is copyright INRIA. 1997.
 * 
 * INRIA holds all the ownership rights on the Software. The scientific
 * community is asked to use the SOFTWARE in order to test and evaluate
 * it.
 * 
 * INRIA freely grants the right to use the Software. Any use or
 * reproduction of this Software to obtain profit or for commercial ends
 * being subject to obtaining the prior express authorization of INRIA.
 * 
 * INRIA authorizes any reproduction of this Software
 * 
 * - in limits defined in clauses 9 and 10 of the Berne agreement for
 * the protection of literary and artistic works respectively specify in
 * their paragraphs 2 and 3 authorizing only the reproduction and quoting
 * of works on the condition that :
 * 
 * - "this reproduction does not adversely affect the normal
 * exploitation of the work or cause any unjustified prejudice to the
 * legitimate interests of the author".
 * 
 * - that the quotations given by way of illustration and/or tuition
 * conform to the proper uses and that it mentions the source and name of
 * the author if this name features in the source",
 * 
 * - under the condition that this file is included with any
 * reproduction.
 * 
 * Any commercial use made without obtaining the prior express agreement
 * of INRIA would therefore constitute a fraudulent imitation.
 * 
 * The Software beeing currently developed, INRIA is assuming no
 * liability, and should not be responsible, in any manner or any case,
 * for any direct or indirect dammages sustained by the user.
 * ******************************************************************
 */

/*
 * ByteArray.java - an array of bytes for fast access in network packages.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 20 May 1998.
 * Updated: no.
 */
package inria.util;

import java.util.*;

/**
 * The ByteArray class represents an array of bytes for fast access in networking
 * packages. ByteArrays are constant; their values cannot be changed after they are
 * created. ByteBuffers support mutable byte arrays.
 * <p>
 * ByteArray is a good substitute of String in networking packages.
 */
public class ByteArray implements Cloneable {
    protected static final byte caseDiff = (byte) 'a' - 'A';
    protected byte[] buff;
    protected int count;
    protected String stringValue;

    /**
     * constructs an empty ByteArray object for subclasses.
     */
    protected ByteArray() {}

    /**
     * constructs a ByteArray object and initializes it from the given string.
     * @param s the string.
     */
    public ByteArray(String s) {
        stringValue = s;
        buff = s.getBytes();
        count = buff.length;
    }

    /**
     * constructs a ByteArray object and initializes it from the given array.
     * @param array the array of bytes.
     * @param offset the offset in the array.
     * @param length the length of bytes.
     */
    public ByteArray(byte[] array, int offset, int length) {
        stringValue = null;
        count = length;
        buff = new byte[length];

        System.arraycopy(array, offset, buff, 0, length);
    }

    /**
     * returns the length of data.
     */
    public int getLength() {
        return count;
    }

    /**
     * returns the length of data.
     */
    public int length() {
        return count;
    }

    /**
     * returns true if the given array equals the current array. Two ByteArray's
     * are equal if they have the same length and the same sequence of bytes.
     * @param array the array to compare.
     */
    public boolean equals(ByteArray array) {
        if (array.count != count) {
            return false;
        } 

        byte[] a1 = array.buff;

        for (int offset = 0; offset < count; offset++) {
            if (a1[offset] != buff[offset]) {
                return false;
            } 
        }

        return true;
    }

    /**
     * compares with the given array. This is a string-like comparison.
     * @param array the array to compare.
     */
    public int compare(ByteArray array) {
        int max;

        if (array.count > count) {
            max = count;
        } else {
            max = array.count;
        }

        byte[] a1 = array.buff;

        for (int offset = 0; offset < max; offset++) {
            if (a1[offset] != buff[offset]) {
                return (buff[offset] - a1[offset]);
            } 
        }

        return 0;
    }

    /**
     * returns true if the given array contains the current array.
     * @param array the array to compare.
     */
    public boolean containedIn(ByteArray array) {
        return containedIn(array.buff, 0);
    }

    /**
     * returns true if the given array contains the current array at the
     * given offset.
     * @param array the array to compare.
     * @param offset the offset in the given array.
     */
    public boolean containedIn(byte[] array, int offset) {
        if ((offset + count) > array.length) {
            return false;
        } 

        for (int i = 0; i < count; i++) {
            if (array[offset] != buff[i]) {
                return false;
            } 

            offset++;
        }

        return true;
    }

    /**
     * returns true if the given array equals this array. The comparison
     * is case insensitive if characters are present.
     * @param s the array to compare.
     */
    public boolean equalsIgnoreCase(ByteArray s) {
        if (s.count != count) {
            return false;
        } 

        byte[] a1 = s.buff;

        for (int offset = 0; offset < count; offset++) {

            /* convert to upper case */

            byte b1 = a1[offset];

            if (b1 >= (byte) 'a' && b1 <= (byte) 'z') {
                b1 -= caseDiff;
            } 

            byte b2 = buff[offset];

            if (b2 >= (byte) 'a' && b2 <= (byte) 'z') {
                b2 -= caseDiff;
            } 
            if (b1 != b2) {
                return false;
            } 
        }

        return true;
    }

    /**
     * returns true if the given array contains this array at the
     * given offset. The comparison is case insensitive if characters are present.
     * @param array the array to compare.
     * @param offset the offset in the given array.
     */
    public boolean containedInIgnoreCase(byte[] array, int offset) {
        if ((offset + count) > array.length) {
            return false;
        } 

        for (int i = 0; i < count; i++) {

            /* convert to upper case */

            byte b1 = array[offset];

            if (b1 >= (byte) 'a' && b1 <= (byte) 'z') {
                b1 -= caseDiff;
            } 

            byte b2 = buff[i];

            if (b2 >= (byte) 'a' && b2 <= (byte) 'z') {
                b2 -= caseDiff;
            } 
            if (b1 != b2) {
                return false;
            } 

            offset++;
        }

        return true;
    }

    /**
     * compares with the given array. This is a string-like comparison.
     * @param array the array to compare.
     */
    public int compareIgnoreCase(ByteArray array) {
        int max;

        if (array.count > count) {
            max = count;
        } else {
            max = array.count;
        }

        byte[] a1 = array.buff;

        for (int offset = 0; offset < max; offset++) {

            /* convert to upper case */

            byte b1 = a1[offset];

            if (b1 >= (byte) 'a' && b1 <= (byte) 'z') {
                b1 -= caseDiff;
            } 

            byte b2 = buff[offset];

            if (b2 >= (byte) 'a' && b2 <= (byte) 'z') {
                b2 -= caseDiff;
            } 
            if (b1 != b2) {
                return (b2 - b1);
            } 
        }

        return 0;
    }

    /**
     * Tests if this array starts with the specified prefix.
     * @param prefix the prefix.
     * @return  <code>true</code> if the byte array represented by the
     * argument is a prefix of the sub-array of this object;
     * <code>false</code> otherwise.
     */
    public boolean startsWith(ByteArray prefix) {
        return startsWith(prefix, 0);
    }

    /**
     * Tests if this array starts with the specified prefix.
     * @param prefix the prefix.
     * @param offset where to begin looking in the array.
     * @return  <code>true</code> if the byte array represented by the
     * argument is a prefix of the sub-array of this object starting
     * at index <code>offset</code>; <code>false</code> otherwise.
     */
    public boolean startsWith(ByteArray prefix, int offset) {
        if ((offset < 0) || (offset > count - prefix.count)) {
            return false;
        } 

        byte[] v1 = buff;
        byte[] v2 = prefix.buff;

        for (int offset2 = 0; offset2 < prefix.count; offset++, offset2++) {
            if (v1[offset] != v2[offset2]) {
                return false;
            } 
        }

        return true;
    }

    /**
     * Tests if this array ends with the specified suffix.
     * @param   suffix   the suffix.
     * @return  <code>true</code> if the byte array represented by the
     * argument is a suffix of this array represented by
     * this object; <code>false</code> otherwise.
     */
    public boolean endsWith(ByteArray suffix) {
        return startsWith(suffix, count - suffix.count);
    }

    /**
     * Tests if this array starts with the specified byte.
     * @param b the byte value.
     */
    public boolean startsWith(byte b) {
        if (count == 0) {
            return false;
        } 

        return buff[0] == b;
    }

    /**
     * Tests if this array ends with the specified byte.
     * @param b the byte value.
     */
    public boolean endsWith(byte b) {
        if (count == 0) {
            return false;
        } 

        return buff[count - 1] == b;
    }

    /**
     * returns the index of the first occurrence of the given array within this
     * array. Returns -1 if not found.
     * @param str the array to search.
     */
    public int indexOf(ByteArray str, int from) {
        if (from >= count) {
            if (count == 0 && from == 0 && str.count == 0) {

                /* There is an empty array at index 0 in an empty array. */

                return 0;
            }

            return -1;
        }
        if (str.count == 0) {
            return from;
        } 
        if (from < 0) {
            from = 0;
        } 

        byte v1[] = buff;
        byte v2[] = str.buff;
        int offset1 = from;

        /* if the remained length can contain subarray */

        while (str.count <= (count - offset1)) {
            if (v1[offset1] != v2[0]) {
                offset1++;

                continue;
            }

            /* the first byte matches */

            from = offset1;

            int offset2 = 1;

            for (offset1 = from + 1; offset2 < str.count; 
                    offset1++, offset2++) {
                if (v1[offset1] != v2[offset2]) {
                    break;
                } 
            }

            if (offset2 == str.count) {
                return from;
            } 

            offset1 = from + 1;
        }

        return -1;
    }

    /**
     * Copies the byte array to the given byte array.
     * @param array the destination byte array.
     * @param offset the offset in the destination byte array.
     */
    public void copyTo(byte array[], int offset) {
        if (count > 0) {
            System.arraycopy(buff, 0, array, offset, count);
        }
    }

    /**
     * returns the string representation of data.
     */
    public String toString() {
        if (count > 0) {
            if (stringValue == null || stringValue.length() != count) {
                stringValue = new String(buff, 0, count);
            } 
        }

        return stringValue;
    }

    /**
     * returns a cloned instance of the current object.
     */
    public Object clone() {
        try {
            ByteArray obj = (ByteArray) super.clone();

            obj.buff = new byte[buff.length];

            System.arraycopy(buff, 0, obj.buff, 0, count);

            return obj;
        } catch (CloneNotSupportedException e) {

            // this shouldn't happen, since we are Cloneable

            throw new InternalError();
        }
    }

}


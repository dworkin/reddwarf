/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

/*
 * ByteBuffer.java
 *
 * Created on January 12, 2006, 1:25 PM
 *
 *
 */

package com.sun.gi.utils.jme;

/**
 * Implementation of ByteBuffer for JME clients
 * @author as93050
 */
public class ByteBuffer {
    
    /** Creates a new instance of ByteBuffer */
    public ByteBuffer() {
    }
    
 
    private byte[] hb;			
    private int offset;
    private boolean isReadOnly;	
    private int mark = -1;
    private int position = 0;
    private int limit;
    private int capacity;
    private boolean bigEndian = true;

    // Creates a new buffer with the given mark, position, limit, capacity,
    // backing array, and array offset
    //
    ByteBuffer(int mark, int pos, int lim, int cap,	// package-private
		 byte[] hb, int offset) {
	if (cap < 0) {
	    throw new IllegalArgumentException();
        }
	this.capacity = cap;
	limit = lim;
	position = pos;
	if (mark > 0) {
	    if (mark > pos) {
		throw new IllegalArgumentException();
            }
	    this.mark = mark;
	}
	this.hb = hb;
	this.offset = offset;
    }

    
	
    
    // Creates a new buffer with the given mark, position, limit, and capacity
    //
    ByteBuffer(int mark, int pos, int lim, int cap) {	// package-private
	this(mark, pos, lim, cap, null, 0);
    }

    ByteBuffer(byte[] array,int offset,int length) {
        hb = new byte[length];
        System.arraycopy(array,offset,hb,0,length);
        position = offset;
        capacity = length;
        limit = length;
    }
    ByteBuffer(int capacity) {
        this.capacity = capacity;
        this.limit = capacity;
        hb = new byte[capacity];
    }
    
   /**
     * Allocates a new byte buffer.
     *
     * <p> The new buffer's position will be zero, its limit will be its
     * capacity, and its mark will be undefined.  It will have a {@link #array
     * </code>backing array<code>}, and its {@link #arrayOffset </code>array
     * offset<code>} will be zero.
     *
     * @param  capacity
     *         The new buffer's capacity, in bytes
     *
     * @return  The new byte buffer
     *
     * @throws  IllegalArgumentException
     *          If the <tt>capacity</tt> is a negative integer
     */
    public static ByteBuffer allocate(int capacity) {
	if (capacity < 0)
	    throw new IllegalArgumentException();
	return new ByteBuffer(capacity);
    }

    /**
     * Wraps a byte array into a buffer.
     *
     * <p> The new buffer will be backed by the given byte array;
     * that is, modifications to the buffer will cause the array to be modified
     * and vice versa.  The new buffer's capacity will be
     * <tt>array.length</tt>, its position will be <tt>offset</tt>, its limit
     * will be <tt>offset + length</tt>, and its mark will be undefined.  Its
     * {@link #array </code>backing array<code>} will be the given array, and
     * its {@link #arrayOffset </code>array offset<code>} will be zero.  </p>
     *
     * @param  array
     *         The array that will back the new buffer
     *
     * @param  offset
     *         The offset of the subarray to be used; must be non-negative and
     *         no larger than <tt>array.length</tt>.  The new buffer's position
     *         will be set to this value.
     *
     * @param  length
     *         The length of the subarray to be used;
     *         must be non-negative and no larger than
     *         <tt>array.length - offset</tt>.
     *         The new buffer's limit will be set to <tt>offset + length</tt>.
     *
     * @return  The new byte buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If the preconditions on the <tt>offset</tt> and <tt>length</tt>
     *          parameters do not hold
     */
    public static ByteBuffer wrap(byte[] array,
				    int offset, int length)
    {
	try {
	    return new ByteBuffer(array, offset, length);
	} catch (IllegalArgumentException x) {
	    throw new IndexOutOfBoundsException();
	}
    }

    /**
     * Wraps a byte array into a buffer.
     *
     * <p> The new buffer will be backed by the given byte array;
     * that is, modifications to the buffer will cause the array to be modified
     * and vice versa.  The new buffer's capacity and limit will be
     * <tt>array.length</tt>, its position will be zero, and its mark will be
     * undefined.  Its {@link #array </code>backing array<code>} will be the
     * given array, and its {@link #arrayOffset </code>array offset<code>} will
     * be zero.  </p> 
     *
     * @param  array
     *         The array that will back this buffer
     *
     * @return  The new byte buffer
     */
    public static ByteBuffer wrap(byte[] array) {
	return wrap(array, 0, array.length);
    }

    /**
     * Creates a new byte buffer whose content is a shared subsequence of
     * this buffer's content.
     *
     * <p> The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     * <p> The new buffer's position will be zero, its capacity and its limit
     * will be the number of bytes remaining in this buffer, and its mark
     * will be undefined.  The new buffer will be direct if, and only if, this
     * buffer is direct, and it will be read-only if, and only if, this buffer
     * is read-only.  </p>
     *
     * @return  The new byte buffer
     */
    public ByteBuffer slice() {
        return new ByteBuffer(mark,position,limit,capacity,hb,offset);
        
    }

    public byte[] array() {
        return hb;
    }

    

    final int nextGetIndex() {				// package-private
	if (position >= limit)
	    throw new RuntimeException();
	return position++;
    }
    
    final int checkIndex(int i) {			// package-private
	if ((i < 0) || (i >= limit))
	    throw new IndexOutOfBoundsException();
	return i;
    }
    // -- Singleton get/put methods --

    /**
     * Relative <i>get</i> method.  Reads the byte at this buffer's
     * current position, and then increments the position. </p>
     *
     * @return  The byte at the buffer's current position
     *
     * @throws  BufferUnderflowException
     *          If the buffer's current position is not smaller than its limit
     */
    public byte get() {
        return hb[nextGetIndex()];        
    }

    /**
     * Relative <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     * 
     * <p> Writes the given byte into this buffer at the current
     * position, and then increments the position. </p>
     *
     * @param  b
     *         The byte to be written
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     *          If this buffer's current position is not smaller than its limit
     *
     * @throws  ReadOnlyBufferException
     *          If this buffer is read-only
     */
    public ByteBuffer put(byte b) {
        hb[nextGetIndex()] = b;
        return this;
    }

    /**
     * Absolute <i>get</i> method.  Reads the byte at the given
     * index. </p>
     *
     * @param  index
     *         The index from which the byte will be read
     *
     * @return  The byte at the given index
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit
     */
    public byte get(int index) {
        return hb[checkIndex(index)];
    }

    /**
     * Absolute <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     * 
     * <p> Writes the given byte into this buffer at the given
     * index. </p>
     *
     * @param  index
     *         The index at which the byte will be written
     *
     * @param  b
     *         The byte value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit
     *
     * @throws  ReadOnlyBufferException
     *          If this buffer is read-only
     */
    public ByteBuffer put(int index, byte b) {
        hb[checkIndex(index)] = b;
        return this;
    }
    public ByteBuffer put(byte[] src, int offset, int length) {
	for (int i = 0; i < length; i++) {
	    this.put(src[i]);
        }
	return this;
    }
    /**
     * Put a byte[] into the buffer. By default add it starting at the current 
     * position
     */
    public ByteBuffer put(byte[] src) {
        return put(src, position, src.length);
    }
    
    public ByteBuffer get(byte[] b) {
        System.arraycopy(hb,position,b,0,b.length);
        position += b.length;
        return this;
    }
    
    public ByteBuffer clear() {
	position = 0;
	limit = capacity;
	mark = -1;
	return this;
    }
    
    public final int capacity() {
	return capacity;
    }
    
    public final ByteBuffer flip() {
	limit = position;
	position = 0;
	mark = -1;
	return this;
    }

    /**
     * Returns this buffer's position. </p>
     *
     * @return  The position of this buffer
     */
    public final int position() {
	return position;
    }
    
    public final int remaining() {
	return limit - position;
    }
    
    public final ByteBuffer position(int newPosition) {
	if ((newPosition > limit) || (newPosition < 0))
	    throw new IllegalArgumentException();
	position = newPosition;
	if (mark > position) mark = -1;
	return this;
    }
    
    public ByteBuffer putInt(int x) {
        put(int3(x));
        put(int2(x));
        put(int1(x));
        put(int0(x));        
        return this;
    }
    
    public ByteBuffer putLong(long x) {
        put(long7(x));
        put(long6(x));
        put(long5(x));
        put(long4(x)); 
	put(long3(x));
        put(long2(x));
        put(long1(x));
        put(long0(x)); 
	return this;
    }
    
    public int getInt() {
	int retVal = makeInt(hb[position+ 0],
		       hb[position+ 1],
		       hb[position+ 2],
		       hb[position+ 3]);
        position += 4;
        return retVal;
    }
    
    public long getLong() {
        long retVal = makeLong(hb[position+ 0],
			hb[position+ 1],
			hb[position+ 2],
			hb[position+ 3],
			hb[position+ 4],
			hb[position+ 5],
			hb[position+ 6],
			hb[position+ 7]);
        position += 8;
        return retVal;
    }
    
    private int makeInt(byte b3, byte b2, byte b1, byte b0) {
	return (int)((((b3 & 0xff) << 24) |
		      ((b2 & 0xff) << 16) |
		      ((b1 & 0xff) <<  8) |
		      ((b0 & 0xff) <<  0)));
    }
    
    static private long makeLong(byte b7, byte b6, byte b5, byte b4,
				 byte b3, byte b2, byte b1, byte b0) {
	return ((((long)b7 & 0xff) << 56) |
		(((long)b6 & 0xff) << 48) |
		(((long)b5 & 0xff) << 40) |
		(((long)b4 & 0xff) << 32) |
		(((long)b3 & 0xff) << 24) |
		(((long)b2 & 0xff) << 16) |
		(((long)b1 & 0xff) <<  8) |
		(((long)b0 & 0xff) <<  0));
    }
    
    private byte int3(int x) { return (byte)(x >> 24); }
    private byte int2(int x) { return (byte)(x >> 16); }
    private byte int1(int x) { return (byte)(x >>  8); }
    private byte int0(int x) { return (byte)(x >>  0); }
    
    private byte long7(long x) { return (byte)(x >> 56); }
    private byte long6(long x) { return (byte)(x >> 48); }
    private byte long5(long x) { return (byte)(x >> 40); }
    private byte long4(long x) { return (byte)(x >> 32); }
    private byte long3(long x) { return (byte)(x >> 24); }
    private byte long2(long x) { return (byte)(x >> 16); }
    private byte long1(long x) { return (byte)(x >>  8); }
    private byte long0(long x) { return (byte)(x >>  0); }
}

    
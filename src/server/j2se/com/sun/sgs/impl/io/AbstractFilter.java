package com.sun.sgs.impl.io;

import com.sun.sgs.io.IOFilter;

// TODO move this functionality into protocol decode; we should
// do framing in the protocol, not the transport. -JM

/**
 * Common {@link IOFilter} functionality for reading a length
 * in a message and maintaining an index into the buffer array.
 *
 * @author Sten Anderson
 */
abstract class AbstractFilter implements IOFilter {
    
    /** The offset of the array that is currently being processed */
    protected int index;
    
    /**
     * Reads the next four bytes of the given array starting at the current
     * index and assembles them into a network byte-ordered int. It will
     * increment the index by four if the read was successful. It will
     * return zero if not enough bytes remain in the array.
     * 
     * @param array the array from which to read bytes
     * 
     * @return the next four bytes as an int, or zero if there aren't enough
     *         bytes remaining in the array
     */
    protected int readInt(byte[] array) {
        if (array.length < (index + 4)) {
            return 0;
        }
        int num =
              ((array[index]     & 0xFF) << 24) |
              ((array[index + 1] & 0xFF) << 16) |
              ((array[index + 2] & 0xFF) <<  8) |
               (array[index + 3] & 0xFF);
        
        index += 4;
        
        return num;
    }
}

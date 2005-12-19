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
 * FIFOQueue.java - FIFO queue.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 23 June 1998.
 * Updated: no.
 */
package inria.util;

/**
 * This class implements the first in first out queue.
 */
public class FIFOQueue {
    Object table[];
    int head = 0;
    int tail = 0;
    int count = 0;

    /**
     * Constructs a FIFOQueue instance.
     * @param maxSize the maximum size of the queue.
     */
    public FIFOQueue(int maxSize) {
        table = new Object[maxSize];
    }

    /**
     * Enqueues the given object. The calling thread will be blocked if the
     * queue is full.
     * @param obj the object to be added to the queue.
     */
    public synchronized void enqueue(Object obj) {
        while (count >= table.length) {
            try {
                wait();
            } catch (InterruptedException e) {
                return;
            }
        }

        table[tail] = obj;
        tail++;

        if (tail >= table.length) {
            tail = 0;
        } 

        count++;
    }

    /**
     * Dequeues an object from the queue.
     */
    public synchronized Object dequeue() {
        if (count > 0) {
            Object obj = table[head];

            table[head] = null;     // free the reference
            head++;

            if (head >= table.length) {
                head = 0;
            } 

            count--;

            if (count == (table.length - 1) || count == 0) {
                notify();
            } 

            return obj;
        }

        return null;
    }

    /**
     * The calling thread will be blocked until the queue is empty.
     */
    public synchronized void sync() {
        while (count > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /**
     * Returns the number of objects in the queue.
     */
    public int getSize() {
        return count;
    }

    /**
     * Clears the queue.
     */
    public synchronized void clear() {
        for (int i = 0; i < table.length; i++) {
            table[i] = null;
        }

        head = 0;
        tail = 0;
        count = 0;
    }

}


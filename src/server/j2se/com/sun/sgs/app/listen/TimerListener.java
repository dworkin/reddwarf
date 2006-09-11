
package com.sun.sgs.app.listen;

import com.sun.sgs.ManagedObject;


/**
 * This is a callback interface used to listen for timed events. It is
 * called when some previously-registered timed event fires.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface TimerListener extends ManagedObject
{

    /**
     * Called when the timed event fires.
     */
    public void timerEvent();

}

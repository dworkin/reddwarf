
package com.sun.sgs;


/**
 * This is a callback interface used to listen for the boot event for a
 * give application.
 * <p>
 * NOTE: While this was in the original system, it's not clear that it
 * continue to exist, or exist in the same form, in this system. For now,
 * this mostly is here to help drive test code.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface BootListener extends ManagedObject
{

    /**
     * Called when the application boots.
     */
    public void boot();

}

package com.sun.gi.logic;

/**
 * <p>Title: SimBoot</p>
 * <p>Description: This interface must be implemented by the boot object of a
 * Game Server application.</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface SimBoot<T extends SimBoot<T>> extends GLO {

    /**
     * This method is called once on the GLO when the system boots an
     * application.
     *
     * @param thisGLO A GLOReference to the boot object.  The type of
     *                this GLOReference is the type &lt;T&gt; of this boot
     *                object, which implements SimBoot&lt;T&gt;.
     *
     * @param firstBoot true if this is the first time the boot method
     *                  has been called in the life of this app in
     *                  this backend.  That is, if firstBoot is true,
     *                  then the objectstore did not previously
     *                  contain an instance of this boot object and
     *                  you may wish to do additional one-time setup.
     */
    public void boot(GLOReference<? extends T> thisGLO, boolean firstBoot);
}

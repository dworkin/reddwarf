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

public interface SimBoot extends GLO {
  /**
   * This method is called once on the GLO when the system boots an application.
   *
   * @param thisGLO A GLOReference to the boot object
   * @param firstBoot Whether this is the first time the boot method
   * has been called in the life of this app in this backednd
   */
  public void boot(GLOReference thisGLO, boolean firstBoot);
}

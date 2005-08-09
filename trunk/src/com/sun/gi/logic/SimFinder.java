package com.sun.gi.logic;

import java.util.Map;

/**
 * <p>Title: SimFinder</p>
 * <p>Description: This interface defines a class that identifies the apps
 * installedin a gvien slice of the Game Server.  It is used at bootup to
 * start the apss within the slice's context.</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */

public interface SimFinder {
  /**
   * Gets a map that maps assigned sim IDs to application boot classes
   * The sim ID is a global ID that identifies the aprticualr app int
   * the back end.
   * @return Map the map of <id, boot class>.
   */
  public Map getAllSims();
  /**
   * Returns the name assigend to a given app ID
   * @param appID long the id
   * @return String the name
   */
  public String getAppName(long appID);
}

package com.sun.gi.comm.routing.old.gtg;

import com.sun.gi.comm.routing.old.ChannelID;
import java.io.Serializable;
import com.sun.gi.utils.StatisticalUUID;
import com.sun.gi.utils.UUID;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class JRMSChannelID implements ChannelID, Comparable, Serializable {
  UUID uuid;

  public JRMSChannelID() {
    uuid = new StatisticalUUID();
  }

  /**
   * compareTo
   *
   * @param o Object
   * @return int
   */
  public int compareTo(Object o) {
    JRMSChannelID other = (JRMSChannelID) o;
    return uuid.compareTo(other.uuid);
  }

  public int hashCode() {
    return uuid.hashCode();
  }

  public boolean equals(Object parm1) {
    return (compareTo(parm1)==0);
  }


  public String toString(){
    return "Channel:"+uuid.toString();
  }


}

package com.sun.gi.comm.routing.old.gtg;

import com.sun.gi.utils.StatisticalUUID;
import com.sun.gi.comm.routing.old.UserID;
import com.sun.gi.utils.UUID;
import java.io.Serializable;

public class JRMSUserID implements UserID, Comparable, Serializable {
  UUID managerID;
  String appname;
  UUID userID;

  public JRMSUserID(UUID mgr, String appname) {
    managerID = mgr;
    this.appname=appname;
    userID = new StatisticalUUID();
  }

  public int compareTo(Object object) {
    JRMSUserID other = (JRMSUserID)object;
    return userID.compareTo(other.userID);
  }

  public String toString(){
    return userID.toString();
  }

  public int hashCode() {
    return userID.hashCode();
  }
  public boolean equals(Object parm1) {
    return (compareTo(parm1)==0);
  }




}

package com.sun.gi.server.chanproc;

import com.sun.gi.server.gle.Kernel;


/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface ChannelProcessor {
  public void setListener(Kernel appKernel,long oid,boolean take);
}

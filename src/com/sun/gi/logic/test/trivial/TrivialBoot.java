package com.sun.gi.logic.test.trivial;

import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class TrivialBoot implements SimBoot {
  public TrivialBoot() {
  }

  public void boot(SimTask task,boolean firstBoot) {
    System.out.println("Ran TrivialBoot.boot");
  }

}

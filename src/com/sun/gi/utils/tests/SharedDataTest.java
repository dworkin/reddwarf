package com.sun.gi.utils.tests;

import com.sun.gi.utils.SharedDataManager;
import com.sun.gi.utils.JRMSSharedDataManager;
import com.sun.gi.utils.SharedData;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class SharedDataTest {
  public SharedDataTest() {
    SharedDataManager mgr = new JRMSSharedDataManager();
    //System.out.println("Initializing shared long...");
    SharedData sharedLong  = mgr.getSharedData("SharedDataTest");
    //System.out.println("Init locking shared long...");
    sharedLong.lock();
    //System.out.println("Init locked shared long");
    Long data = (Long)sharedLong.getValue();
    if (data == null) { // new data
      sharedLong.setValue(new Long(0));
    }
    //System.out.println("Init realising shared long...");
    sharedLong.release();
    //System.out.println("Init released shared long");
    while(true){
      try {
        //System.out.println("Locking...");
        sharedLong.lock();
        //System.out.println("Locked...");
        data = (Long)sharedLong.getValue();
        System.out.println("Data: "+data.longValue());
        sharedLong.setValue(new Long(data.longValue()+1));
        // System.out.println("Releasing...");
        sharedLong.release();
        //System.out.println("Released...");
        Thread.sleep(500);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  public static void main(String[] args) {
    SharedDataTest sharedDataTest1 = new SharedDataTest();
  }

}
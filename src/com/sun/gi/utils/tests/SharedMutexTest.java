package com.sun.gi.utils.tests;

import com.sun.gi.utils.SharedDataManager;
import com.sun.gi.utils.JRMSSharedDataManager;
import java.io.InputStreamReader;
import com.sun.gi.utils.SharedMutex;
import java.io.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class SharedMutexTest {
  public SharedMutexTest() {
    SharedDataManager mgr = new JRMSSharedDataManager();
    SharedMutex mutex = mgr.getSharedMutex("MutexTest");
    InputStreamReader rdr = new InputStreamReader(System.in);
    while(true){
      mutex.lock();
      System.out.println("Locked");
      try {
        rdr.read();
      }
      catch (IOException ex) {
        ex.printStackTrace();
      }
      mutex.release();
      try {
        Thread.sleep(100);
      }
      catch (InterruptedException ex1) {
        ex1.printStackTrace();
      }
    }
  }
  public static void main(String[] args) {
    SharedMutexTest sharedMutexTest1 = new SharedMutexTest();
  }

}

/*
 * CurrThreadTest.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Tue Jul 11, 2006	 5:44:33 PM
 * Desc: 
 *
 */

import java.util.Date;


public class CurrThreadTest implements Runnable
{

    public CurrThreadTest() {

    }

    public void run() {
        int i = 0;
        Date start = new Date();

        while (i < 2000000000) {
            Thread.currentThread();
            i++;
        }

        Date end = new Date();

        System.out.println(end.getTime() - start.getTime());
    }

    public static void main(String [] args) {
        Thread t1 = new Thread(new CurrThreadTest());
        Thread t2 = new Thread(new CurrThreadTest());
        Thread t3 = new Thread(new CurrThreadTest());

        t1.start();
        t2.start();
        t3.start();
    }

}

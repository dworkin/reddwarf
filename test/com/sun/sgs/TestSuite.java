package com.sun.sgs;

/**
 * Runs all of the known JUnit tests for the SGS server.
 *
 * @author David Jurgens <dj202934@sun.com>
 */
public class TestSuite implements Runnable {
    
    /**
     * Runs all the tests.
     */
    public void run() {
	org.junit.runner.JUnitCore.main();
	//org.junit.runner.JUnitCore.main("com.sun.sgs.kernel.scheduling.PriorityTests",
	//				"com.sun.sgs.kernel.scheduling.SchedulerTests");
    }

    public static void main(String[] args) {
	// TODO: make some use of args so we can add tests?
	new TestSuite().run();
    }

}

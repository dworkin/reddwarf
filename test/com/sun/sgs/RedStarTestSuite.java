package com.sun.sgs;

// import com.sun.sgs.kernel.scheduling.PriorityTests;
// import com.sun.sgs.kernel.scheduling.SchedulerTests;

/**
 * A class runs all of the known JUnit tests for the RedStar code branch.
 */
public class RedStarTestSuite implements Runnable {
    
    /**
     * Runs all the tests.
     */
    public void run() {
	org.junit.runner.JUnitCore.main("com.sun.sgs.kernel.scheduling.PriorityTests",
					"com.sun.sgs.kernel.scheduling.SchedulerTests");
    }

    public static void main(String[] args) {
	// DEVELOPER NOTE: make some use of args so we can add tests?
	new RedStarTestSuite().run();
    }

}

package nachos.kernel.threads;

import nachos.machine.NachosThread;
import nachos.machine.Timer;
import nachos.kernel.threads.Semaphore;

public class extendedNachosThread extends NachosThread {

    public Semaphore semSleep;
    public int waitTicks;
    public int quantum;
    public int currCPUBurst;
    public int avgCPUBurst;
    
    /**
     * Initialize a new user thread.
     *
     * @param name  An arbitrary name, useful for debugging.
     * @param runObj Execution of the thread will begin with the run()
     * method of this object.
     * @param addrSpace  The context to be installed when this thread
     * is executing in user mode.
     */
    public extendedNachosThread(String name, Runnable runObj) {
	super(name, runObj);
	semSleep = new Semaphore("semaphore for thread sleep",0);
	quantum = Timer.DefaultInterval;
	currCPUBurst = 0;
	avgCPUBurst = 0;
    }
}

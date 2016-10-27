package nachos.kernel.threads;

import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.machine.NachosThread;

/**
 * A <CODE>CyclicBarrier</CODE> is an object that allows a set of threads to
 * all wait for each other to reach a common barrier point.
 * To find out more, read
 * <A HREF="http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/CyclicBarrier.html">the documentation</A>
 * for the Java API class <CODE>java.util.concurrent.CyclicBarrier</CODE>.
 *
 * The class is provided in skeletal form.  It is your job to
 * complete the implementation of this class in conformance with the
 * Javadoc specifications provided for each method.  The method
 * signatures specified below must not be changed.  However, the
 * implementation will likely require additional private methods as well
 * as (private) instance variables.  Also, it is essential to provide
 * proper synchronization for any data that can be accessed concurrently.
 * You may use ONLY semaphores for this purpose.  You may NOT disable
 * interrupts or use locks or spinlocks.
 *
 * NOTE: The skeleton below reflects some simplifications over the
 * version of this class in the Java API.
 */
public class CyclicBarrier {
    
    private int parties;
    private int waitingCount;
    private Runnable barrierAction;
    private Semaphore mutex;
    private Semaphore semWait;
    private Semaphore semExit;
    private boolean broken;
    
    /** Class of exceptions thrown in case of a broken barrier. */
    public static class BrokenBarrierException extends Exception {
	private static final long serialVersionUID = -7122487149233672317L;
	public BrokenBarrierException() {}
	public BrokenBarrierException(String message) {
	    super(message);
	}
    }

   /**
     * Creates a new CyclicBarrier that will trip when the given number
     * of parties (threads) are waiting upon it, and does not perform a
     * predefined action when the barrier is tripped.
     *
     * @param parties  The number of parties.
     */
    public CyclicBarrier(int parties) {
	this.parties = parties;
	this.mutex= new Semaphore("Semaphore for await",1);
	this.semWait = new Semaphore("Semaphore for threads to wait",0);
	this.semExit = new Semaphore("Semaphore for threads to exit",1);
	this.waitingCount = 0;
	this.broken = false;
    }
    
    /**
     * Creates a new CyclicBarrier that will trip when the given number of
     * parties (threads) are waiting upon it, and which will execute the
     * given barrier action when the barrier is tripped, performed by the
     * last thread entering the barrier.
     *
     * @param parties  The number of parties.
     * @param barrierAction  An action to be executed when the barrier
     * is tripped, performed by the last thread entering the barrier.
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
	this.parties = parties;
	this.barrierAction = barrierAction;
	this.mutex= new Semaphore("mutex",1);
	this.semWait = new Semaphore("Semaphore for waiting",0);
	this.semExit = new Semaphore("Semaphore for synchronizing",1);
	this.waitingCount = 0;
	this.broken = false;
    }

    /**
     * Waits until all parties have invoked await on this barrier.
     * If the current thread is not the last to arrive then it blocks
     * until either the last thread arrives or some other thread invokes
     * reset() on this barrier.
     *
     * @return  The arrival index of the current thread, where index
     * getParties() - 1 indicates the first to arrive and zero indicates
     * the last to arrive.
     * @throws  BrokenBarrierException in case this barrier is broken.
     */
    public int await() throws BrokenBarrierException {
	mutex.P();
	if (isBroken())
	    throw new BrokenBarrierException("Barrier is Broken");
	waitingCount += 1;
	int index = getParties() - waitingCount;
	if (waitingCount == parties) {
	    if (this.barrierAction != null)
		this.barrierAction.run();
	    semExit.P();
	    semWait.V();
	}
	mutex.V();
	semWait.P();
	if (isBroken())
	    throw new BrokenBarrierException("Barrier is Broken");
	semWait.V();
	
	mutex.P();
	waitingCount -= 1;
	if (waitingCount == 0) {
	    semWait.P();
	    semExit.V();
	}
	mutex.V();
	semExit.P();
	if (isBroken())
	    throw new BrokenBarrierException("Barrier is Broken");
	semExit.V();
	return index;
    }

    /**
     * Returns the number of parties currently waiting at the barrier.
     * @return the number of parties currently waiting at the barrier.
     */
    public int getNumberWaiting() {
	return this.waitingCount;
    }

    /**
     * Returns the number of parties required to trip this barrier.
     * @return the number of parties required to trip this barrier.
     */
    public int getParties() {
	return this.parties;
    }

    /**
     * Queries if this barrier is in a broken state.
     * @return true if this barrier was reset while one or more threads
     * were blocked in await(), false otherwise.
     */
    public boolean isBroken() {
	return this.broken;
    }

    /**
     * Resets the barrier to its initial state. 
     */
    public void reset() {
	mutex.P();
	this.broken = true;
	this.waitingCount = 0;
	semWait.V();
	semExit.V();
	this.broken = false;
	mutex.V();
    }

    /**
      * This method can be called to simulate "doing work".
      * Each time it is called it gives control to the NACHOS
      * simulator so that the simulated time can advance by a
      * few "ticks".
      */
    public static void allowTimeToPass() {
	dummy.P();
	dummy.V();
    }

    /** Semaphore used by allowTimeToPass above. */
    private static Semaphore dummy = new Semaphore("Time waster", 1);

    /**
     * Run a demonstration of the CyclicBarrier facility.
     * @param args  Arguments from the "command line" that can be
     * used to modify features of the demonstration such as the
     * number of parties, the amount of "work" performed by
     * each thread, etc.
     *
     * IMPORTANT: Be sure to test your demo with the "-rs xxxxx"
     * command-line option passed to NACHOS (the xxxxx should be
     * replaced by an integer to be used as the seed for
     * NACHOS' pseudorandom number generator).  If you fail to
     * include this option, then a thread that has been started will
     * always run to completion unless it explicitly yields the CPU.
     * This will result in the same (very uninteresting) execution
     * each time NACHOS is run, which will not be a very good
     * test of your code.
     */
    public static void demo(String[] args) {
	// Very simple example of the intended use of the CyclicBarrier
	// facility: you should replace this code with something much
	// more interesting.
	int parties = Integer.parseInt(args[0]);
	final int phases = Integer.parseInt(args[1]);
	final int workPerThread = Integer.parseInt(args[2]);
	final CyclicBarrier barrier = new CyclicBarrier(parties);
	Debug.println('1', "Demo starting");
	for(int i = 0; i < parties; i++) {
	    NachosThread thread =
		new NachosThread
		("Worker thread " + i, new Runnable() {
		    public void run() {
			Debug.println('1', "Thread "
				+ NachosThread.currentThread().name
				+ " is starting");
			for(int j = 0; j < phases; j++) {
			    Debug.println('1', "Thread "
					  + NachosThread.currentThread().name
					  + " beginning phase " + j);
			    for(int k = 0; k < workPerThread; k++) {
				Debug.println('1', "Thread "
					+ NachosThread.currentThread().name
					+ " is working");
				CyclicBarrier.allowTimeToPass();  // Do "work".
			    }
			    Debug.println('1', "Thread "
				    + NachosThread.currentThread().name
				    + " is waiting at the barrier");
			    try {
				barrier.await();
			    } catch (BrokenBarrierException e) {
				e.printStackTrace();
			    }
			    Debug.println('1', "Thread "
				    + NachosThread.currentThread().name
				    + " has finished phase " + j);
			}
			Debug.println('1', "Thread "
				+ NachosThread.currentThread().name
				+ " is terminating");
			Nachos.scheduler.finishThread();
		    }
		});
	    Nachos.scheduler.readyToRun(thread);
	}
	Debug.println('1', "Demo terminating");
    }
}

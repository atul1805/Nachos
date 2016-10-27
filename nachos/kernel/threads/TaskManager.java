package nachos.kernel.threads;

import nachos.util.FIFOQueue;
import nachos.util.Queue;
import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.machine.NachosThread;

/**
 * This class provides a facility for scheduling work to be performed
 * "in the background" by "child" threads and safely communicating the
 * results back to a "parent" thread.  It is loosely modeled after the
 * AsyncTask facility provided in the Android API.
 *
 * The class is provided in skeletal form.  It is your job to
 * complete the implementation of this class in conformance with the
 * Javadoc specifications provided for each method.  The method
 * signatures specified below must not be changed.  However, the
 * implementation will likely require additional private methods as well
 * as (private) instance variables.  Also, it is essential to provide
 * proper synchronization for any data that can be accessed concurrently.
 * You may use any combination of semaphores, locks, and conditions
 * for this purpose.
 *
 * NOTE: You may NOT disable interrupts or use spinlocks.
 */
public class TaskManager {
    
    NachosThread parent;
    private int numChild;
    private int count;
    private static Queue<Runnable> requestQueue;
    private Lock mutex;
    private Condition request;
    
    /**
     * Initialize a new TaskManager object, and register the
     * calling thread as the "parent" thread.  The parent thread is
     * responsible (after creating at least one Task object and
     * calling its execute() method) for calling processRequests() to
     * track the completion of "child" threads and process onCompletion()
     * or onCancellation() requests on their behalf.
     */
    public TaskManager(int numChild) {
	this.parent = NachosThread.currentThread();
	this.numChild = numChild;
	this.count = 0;
	TaskManager.requestQueue = new FIFOQueue<Runnable>();
	this.mutex = new Lock("mutex");
	this.request = new Condition("request",mutex);
    }
    
    /**
     * Posts a request for a Runnable to be executed by the parent thread.
     * Such a Runnable might consist of a call to <CODE>onCompletion()</CODE>
     * or <CODE>onCancellation() associated with the completion of a task
     * being performed by a child thread, or it might consist of
     * completely unrelated work (such as responding to user interface
     * events) for the parent thread to perform.
     * 
     * NOTE: This method should be safely callable by any thread.
     *
     * @param runnable  Runnable to be executed by the parent thread.
     */
    public static void postRequest(Runnable runnable) {
	requestQueue.offer(runnable);
    }

    /**
     * Called by the parent thread to process work requests posted
     * for it.  This method does not return unless there are no
     * further pending requests to be processed AND all child threads
     * have terminated.  If there are no requests to be processed,
     * but some child threads are still active, then the parent thread
     * will block within this method awaiting further requests
     * (for example, those that will eventually result from the termination
     * of the currently active child threads).
     *
     * @throws IllegalStateException  if the calling thread is not
     * registered as the parent thread for this TaskManager.
     */
    public void processRequests() throws IllegalStateException {
	mutex.acquire();
	if (NachosThread.currentThread() != this.parent)
	    throw new IllegalStateException();
	do {
	    if (requestQueue.isEmpty())	// conditional wait till queue is empty
		request.await();
	    while (!requestQueue.isEmpty()) {
		Runnable work = requestQueue.poll();
		work.run();		// Run all the queued tasks
	    }
	} while (count < numChild);	// Repeat the process until all the child threads are done
	mutex.release();
    }

    /**
     * Inner class representing a task to be executed in the background
     * by a child thread.  This class must be subclassed in order to
     * override the doInBackground() method and possibly also the
     * onCompletion() and onCancellation() methods.
     */
    public class Task {
	
	public String taskStatus = "INITIALIZED";	
	/**
	 * Cause the current task to be executed by a new child thread.
	 * In more detail, a new child thread is created, the child
	 * thread runs the doInBackground() method and upon termination
	 * of that method a request is posted for the parent thread to
	 * run either onCancellation() or onCompletion(), respectively,
	 * depending on	whether or not the task was cancelled.
	 */
	public void execute() {
	    
	    this.taskStatus = "InProgress";		// represents the status of child thread
	    
	    Runnable childTask = new Runnable() {	// Runnable for child
		public void run() {
		    doInBackground();
		    Runnable onComplete = new Runnable() {
			public void run() {
			    onCompletion();
			}
		    };
		    Runnable onCancel = new Runnable() {
			public void run() {
			    onCancellation();
			}
		    };
		    if (isCancelled()) {	// check the result of child thread
			postRequest(onCancel);	// post task in queue
			mutex.acquire();
			request.signal();	// signal the conditional lock to release once we have posted task in queue
			mutex.release();
		    } else {
			postRequest(onComplete);// post task in queue
			mutex.acquire();
			request.signal();	// signal the conditional lock to release once we have posted task in queue
			mutex.release();
		    }
		    Nachos.scheduler.finishThread();
		}
	    };
	    mutex.acquire();
	    NachosThread thread = new NachosThread ("Child Thread " + count, childTask );
	    Nachos.scheduler.readyToRun(thread);	// execute the child
	    count += 1;					// count tells the number of child threads in completed/cancelled state
	    mutex.release();
	}

	/**
	 * Flag the current Task as "cancelled", if the task has not
	 * already completed.  Successful cancellation (as indicated
	 * by a return value of true) guarantees that the onCancellation()
	 * method will be executed instead of the normal onCompletion()
	 * method.  This method should be safely callable by any thread.
	 *
	 * @return true if the task was successfully cancelled,
	 * otherwise false.
	 */
	public boolean cancel() {
	    if (this.taskStatus != "COMPLETED" && this.taskStatus != "CANCELLED") {
		this.taskStatus = "CANCELLED";
		return true;
	    } else {
		return false;
	    }
	}

	/**
	 * Determine whether this Task has been cancelled.
	 * This method should be safely callable by any thread.
	 *
	 * @return true if this Task has been cancelled, false otherwise.
	 */
	public boolean isCancelled() {
	    if (this.taskStatus == "CANCELLED")
		return true;
	    else
		return false;
	}

	/**
	 * Method to be executed in the background by a child thread.
	 * Subclasses will override this with desired code.  The default
	 * implementation is to do nothing and terminate immediately.
	 * Subclass implementations should call isCancelled() periodically
	 * so that this method will return promptly if this Task is
	 * cancelled.  This method should not be called directly;
	 * rather, it will be called indirectly as a result of a call to
	 * the execute() method.
	 */
	protected void doInBackground() {
	}

	/**
	 * Method to be executed by the main thread upon termination of
	 * of doInBackground().  Will not be executed if the task was
	 * cancelled.  This method should not be called directly; 
	 * rather, it will be called indirectly as a result of a call to
	 * the execute() method.
	 */
	protected void onCompletion() {
	}

	/**
	 * Method to be executed by the main thread upon termination
	 * of doInBackground().  Will only be executed if the task
	 * was cancelled.
	 */
	protected void onCancellation() {
	}
	
	/**
	 * This method can be called to simulate "doing work".
	 * Each time it is called it gives control to the NACHOS
	 * simulator so that the simulated time can advance by a
	 * few "ticks".
	 */
	protected void allowTimeToPass() {
	    dummy.P();
	    dummy.V();
	}

    }

    /** Semaphore used by allowTimeToPass above. */
    private static Semaphore dummy = new Semaphore("Time waster", 1);
}

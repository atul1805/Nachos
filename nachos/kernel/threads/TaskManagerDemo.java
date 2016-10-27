package nachos.kernel.threads;

import nachos.Debug;
import nachos.machine.NachosThread;

public class TaskManagerDemo extends TaskManager{

    public TaskManagerDemo(int numBackground) {
	super(numBackground);
    }

    public static void demo() {
	int numBackground = 5;
	TaskManager mgr = new TaskManager(numBackground);
	for(int i = 0; i < numBackground; i++) {
	    final int tn = i;
	    Task task = mgr.new Task() {
			protected void doInBackground() {
			    if (tn == 2) {
				cancel();
				Runnable postReqToParent = new Runnable() {
				    public void run() {
					Debug.println('1', "Thread " + NachosThread.currentThread().name + " is starting task " + tn);
					for(int j = 0; j < 2; j++) {
					    allowTimeToPass();   // Do "work".
					    Debug.println('1', "Thread " + NachosThread.currentThread().name + " is working on task " + tn);
					}
					Debug.println('1', "Thread " + NachosThread.currentThread().name + " is finishing task " + tn);
				    }
				};
				postRequest(postReqToParent);
			    }
			    if (tn == 3) {
				cancel();
			    }
			    if (!isCancelled()) {
				Debug.println('1', "Thread " + NachosThread.currentThread().name + " is starting task " + tn);
				for(int j = 0; j < 2; j++) {
				    allowTimeToPass();   // Do "work".
				    Debug.println('1', "Thread " + NachosThread.currentThread().name + " is working on task " + tn);
				}
				Debug.println('1', "Thread " + NachosThread.currentThread().name + " is finishing task " + tn);
			    }
			}

			protected void onCompletion() {
			    Debug.println('1', "Thread " + NachosThread.currentThread().name + " is executing onCompletion() " + " for task " + tn);
			}
		    
			protected void onCancellation() {
			    Debug.println('1', "Thread " + NachosThread.currentThread().name + " is executing onCancellation() " + " for task " + tn);
			}
	    };
	    task.execute();
	}
	mgr.processRequests();
	Debug.println('1', "Demo terminating");
    }
}
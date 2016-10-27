package nachos.kernel.threads;

import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.kernel.threads.CyclicBarrier.BrokenBarrierException;
import nachos.machine.NachosThread;


public class CyclicBarrierDemo implements Runnable{

    static int matrix[][] = {{1,2,3,4,5},{10,20,2,31,67}, {-3,36,91,-100,1},{40,19,84,2220}, {2015,2014,2013,2012,2011}};
    static int rows = matrix.length;
    static int rowMax[] = new int[rows];
    static int matrixMax;

    CyclicBarrier barrier;
    int row;
    
    public CyclicBarrierDemo(CyclicBarrier barrier,int row) {
        this.barrier = barrier;
        this.row = row;
    }

    public static void demo() {
	Runnable barrierAction = new Runnable() {
	    public void run() {
		int matrixMax = rowMax[0];
		for (int i = 1; i < rows; i++) {
		    if (matrixMax < rowMax[i])
			matrixMax = rowMax[i];
		}
		Debug.println('1', "BarrierAction executed and maximum value in matrix is " + matrixMax);
	    }
	};
	final CyclicBarrier barrier = new CyclicBarrier(rows,barrierAction);
	
	Debug.println('1', "Demo starting");
	for(int i = 0; i < rows; i++) {
	    NachosThread thread = new NachosThread ("Thread Row " + i, new CyclicBarrierDemo(barrier,i));
	    Nachos.scheduler.readyToRun(thread);
	}
	Debug.println('1', "Demo terminating");
    }
    
    public void run() {
	Debug.println('1', NachosThread.currentThread().name + " is starting");
	int columns = matrix[row].length;
	int max = matrix[row][0];
	for (int i = 1; i < columns; i++) {
	    if (max < matrix[row][i])
		max = matrix[row][i];
	}
	rowMax[row] = max;
	Debug.println('1', NachosThread.currentThread().name + " result is " + max);
	Debug.println('1', NachosThread.currentThread().name + " is waiting at the barrier");
	try {
	    barrier.await();
	} catch (BrokenBarrierException e) {
	    e.printStackTrace();
	}
	Debug.println('1', NachosThread.currentThread().name + " is terminating");
	Nachos.scheduler.finishThread();
    }
}
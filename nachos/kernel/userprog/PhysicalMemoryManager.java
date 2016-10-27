package nachos.kernel.userprog;

import nachos.Debug;
import nachos.kernel.threads.Lock;
import nachos.machine.Machine;


public class PhysicalMemoryManager {
    
    private static boolean[] physicalPage = new boolean[Machine.NumPhysPages];
    private static Lock PageLock = new Lock("PageLock");
        
    public static int getFreePage() {
	PageLock.acquire();
	int ppn = -1;
	for (int i = 0; i < Machine.NumPhysPages; ++i ) {
	    if (!physicalPage[i]) {
		physicalPage[i] = true;
		ppn = i;
		break;
	    }
	}
	PageLock.release();
	return ppn;
    }
    public static void setPageFree(int pageNumber){
	Debug.ASSERT((pageNumber >= 0 && pageNumber < Machine.NumPhysPages),"pageNumber invalid or beyond limit");
	PageLock.acquire();
	physicalPage[pageNumber] = false;
	PageLock.release();
    }
}
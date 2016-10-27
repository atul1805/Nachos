package nachos.kernel.userprog;

import java.util.HashMap;
import java.util.LinkedList;

import nachos.kernel.threads.Lock;
import nachos.machine.NachosThread;

public class ProcessManager {
    private static int pid = 0;
    static Lock processLock = new Lock("process lock");
    static HashMap<Integer, AddrSpace> processTable = new HashMap<Integer, AddrSpace>();
    static HashMap<Integer, LinkedList<Integer>> waitingPID = new HashMap<Integer, LinkedList<Integer>>();
    static HashMap<Integer, Integer> exitStatus = new HashMap<Integer, Integer>();
    static HashMap<Integer, LinkedList<Integer>> forkedPID = new HashMap<Integer, LinkedList<Integer>>();
    
    public static int getNextpid() {
	processLock.acquire();
	pid += 1;
	processLock.release();
	return pid;
    }
    
    public static AddrSpace getCurrentSpace() {
	processLock.acquire();
	AddrSpace currSpace = ((UserThread)NachosThread.currentThread()).space;
	processLock.release();
	return currSpace;
    }
}
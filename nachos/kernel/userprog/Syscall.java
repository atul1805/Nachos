// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// Copyright (c) 2003 State University of New York at Stony Brook.
// All rights reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.

package nachos.kernel.userprog;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.kernel.filesys.OpenFile;
import nachos.kernel.threads.Lock;
import nachos.kernel.threads.Scheduler;
import nachos.kernel.threads.extendedNachosThread;
import nachos.machine.CPU;
import nachos.machine.MIPS;
import nachos.machine.Machine;
import nachos.machine.NachosThread;
import nachos.machine.Simulation;
import nachos.machine.TranslationEntry;

/**
 * Nachos system call interface.  These are Nachos kernel operations
 * 	that can be invoked from user programs, by trapping to the kernel
 *	via the "syscall" instruction.
 *
 * @author Thomas Anderson (UC Berkeley), original C++ version
 * @author Peter Druschel (Rice University), Java translation
 * @author Eugene W. Stark (Stony Brook University)
 */
public class Syscall {

    // System call codes -- used by the stubs to tell the kernel 
    // which system call is being asked for.

    /** Integer code identifying the "Halt" system call. */
    public static final byte SC_Halt = 0;

    /** Integer code identifying the "Exit" system call. */
    public static final byte SC_Exit = 1;

    /** Integer code identifying the "Exec" system call. */
    public static final byte SC_Exec = 2;

    /** Integer code identifying the "Join" system call. */
    public static final byte SC_Join = 3;

    /** Integer code identifying the "Create" system call. */
    public static final byte SC_Create = 4;

    /** Integer code identifying the "Open" system call. */
    public static final byte SC_Open = 5;

    /** Integer code identifying the "Read" system call. */
    public static final byte SC_Read = 6;

    /** Integer code identifying the "Write" system call. */
    public static final byte SC_Write = 7;

    /** Integer code identifying the "Close" system call. */
    public static final byte SC_Close = 8;

    /** Integer code identifying the "Fork" system call. */
    public static final byte SC_Fork = 9;

    /** Integer code identifying the "Yield" system call. */
    public static final byte SC_Yield = 10;

    /** Integer code identifying the "Remove" system call. */
    public static final byte SC_Remove = 11;
    
    /** Integer code identifying the "PrintMessageAndValue" system call. */
    public static final int SC_PrintMessageAndValue = 12;
    
    public static final int SC_Sleep = 13;
    
    public static final int SC_Mkdir = 14;
    
    public static final int SC_Rmdir = 15;

    public static Lock processTableLock = new Lock("processTableLock");
    public static Lock waitingPIDLock = new Lock("waitingPIDLock");
    public static Lock exitStatusLock = new Lock("exitStatusLock");
    public static Lock forkedPIDLock = new Lock("forkedPIDLock");
    public static Lock runningProcessLock = new Lock("runningProcessLock");
    
    /**
     * Stop Nachos, and print out performance stats.
     */
    public static void halt() {
	Debug.print('+', "Shutdown, initiated by user program.\n");
	Simulation.stop();
    }

    /* Address space control operations: Exit, Exec, and Join */

    /**
     * This user program is done.
     *
     * @param status Status code to pass to processes doing a Join().
     * status = 0 means the program exited normally.
     */
    public static void exit(int status) {
	Debug.println('+', "User program exits with status=" + status
				+ ": " + NachosThread.currentThread().name);
	
	boolean isLastThread = true;
	AddrSpace currSpace = ProcessManager.getCurrentSpace();
	int currPID = currSpace.pid;
	TranslationEntry[] currPageTable = currSpace.pageTable;
	
	
	if (ProcessManager.forkedPID.containsKey(currPID))
	{
	    LinkedList<Integer> forkedProcesses = ProcessManager.forkedPID.get(currPID);
	    for (int i = 0; i< forkedProcesses.size(); i++) {
		if (!ProcessManager.exitStatus.containsKey(forkedProcesses.get(i))) {
		    isLastThread = false;
		    break;
		}
	    }
	} else {
	    Iterator it = ProcessManager.forkedPID.entrySet().iterator();
	    hashMapLoop : while (it.hasNext()) {
		Map.Entry pair = (Map.Entry)it.next();
		int parentPID = (int) pair.getKey();
		LinkedList<Integer> forkedProcesses = (LinkedList<Integer>) pair.getValue();
		if (forkedProcesses.contains(currPID)) {
		    if (!ProcessManager.exitStatus.containsKey(parentPID)) {
			isLastThread = false;
			break;
		    }
		    for (int i = 0; i< forkedProcesses.size(); i++) {
			if (!ProcessManager.exitStatus.containsKey(forkedProcesses.get(i))) {
			    isLastThread = false;
			    break hashMapLoop;
			}
		    }
	        }
	    }
	}
	
	if (isLastThread) {
	    for (int i = 0; i < currPageTable.length; i++) {
		PhysicalMemoryManager.setPageFree(currPageTable[i].physicalPage);
	    }
	} else {
	    for (int i = currPageTable.length - AddrSpace.StackLength; i < currPageTable.length; i++) {
		PhysicalMemoryManager.setPageFree(currPageTable[i].physicalPage);
	    }
	}
	
	if (AddrSpace.runningProcess == 1)
	    Nachos.consoleDriver.stop();

	runningProcessLock.acquire();
	AddrSpace.runningProcess--;
	runningProcessLock.release();
	
	exitStatusLock.acquire();
	ProcessManager.exitStatus.put(currPID,status);
	exitStatusLock.release();
	
	LinkedList<Integer> waitingProcesses = new LinkedList<Integer>();
	if (ProcessManager.waitingPID.containsKey(currPID)) {
	    waitingProcesses = ProcessManager.waitingPID.get(currPID);
	    while (waitingProcesses.size() > 0) {
		ProcessManager.processTable.get(waitingProcesses.removeFirst()).semJoin.V();	
	    }
	}
	Nachos.scheduler.finishThread();
    }

    /**
     * Run the executable, stored in the Nachos file "name", and return the 
     * address space identifier.
     *
     * @param name The name of the file to execute.
     */
    public static int exec(String name) {
	
	final String execName = name;
	AddrSpace space = new AddrSpace();
	Runnable execute = new Runnable() {
		public void run() {
		    OpenFile executable;
		    AddrSpace space = ProcessManager.getCurrentSpace();
			if((executable = Nachos.fileSystem.open(execName)) == null) {
			    Debug.println('+', "Unable to open executable file: " + execName);
			    
			    exitStatusLock.acquire();
			    ProcessManager.exitStatus.put(space.pid,-1);
			    exitStatusLock.release();
			    
			    runningProcessLock.acquire();
			    AddrSpace.runningProcess--;
			    runningProcessLock.release();
			    
			    if (AddrSpace.runningProcess == 1)
				Nachos.consoleDriver.stop();
			    Nachos.scheduler.finishThread();
			    return;
			}
			if(space.exec(executable) == -1) {
			    Debug.println('+', "Unable to read executable file: " + execName);
			    
			    exitStatusLock.acquire();
			    ProcessManager.exitStatus.put(space.pid,-1);
			    exitStatusLock.release();
			    
			    runningProcessLock.acquire();
			    AddrSpace.runningProcess--;
			    runningProcessLock.release();
			    
			    if (AddrSpace.runningProcess == 1)
				Nachos.consoleDriver.stop();
			    Nachos.scheduler.finishThread();
			    return;
			}
			space.initRegisters();
			space.restoreState();
			CPU.runUserCode();
		}
	    };
	UserThread t = new UserThread(execName, execute, space);
	Nachos.scheduler.readyToRun(t);
	return space.pid;
    }

    /**
     * Wait for the user program specified by "id" to finish, and
     * return its exit status.
     *
     * @param id The "space ID" of the program to wait for.
     * @return the exit status of the specified program.
     */
    public static int join(int id) {	
	if (!ProcessManager.exitStatus.containsKey(id)) {
	    AddrSpace currSpace = ProcessManager.getCurrentSpace();
		
	    LinkedList<Integer> waitingProcesses = new LinkedList<Integer>();
	    if (ProcessManager.waitingPID.containsKey(id)) {
		waitingProcesses = ProcessManager.waitingPID.get(id);
	    }
	    waitingProcesses.add(currSpace.pid);
		
	    waitingPIDLock.acquire();
	    ProcessManager.waitingPID.put(id,waitingProcesses);
	    waitingPIDLock.release();
	    
	    System.out.println("pid: " + currSpace.pid + " waiting");
	    currSpace.semJoin.P();
	    System.out.println("pid: " + currSpace.pid + " done waiting");
	}
	return ProcessManager.exitStatus.get(id);
    }


    /* File system operations: Create, Open, Read, Write, Close
     * These functions are patterned after UNIX -- files represent
     * both files *and* hardware I/O devices.
     *
     * If this assignment is done before doing the file system assignment,
     * note that the Nachos file system has a stub implementation, which
     * will work for the purposes of testing out these routines.
     */

    // When an address space starts up, it has two open files, representing 
    // keyboard input and display output (in UNIX terms, stdin and stdout).
    // Read and write can be used directly on these, without first opening
    // the console device.

    /** OpenFileId used for input from the keyboard. */
    public static final int ConsoleInput = 0;

    /** OpenFileId used for output to the display. */
    public static final int ConsoleOutput = 1;

    /**
     * Create a Nachos file with a specified name.
     *
     * @param name  The name of the file to be created.
     */
    public static void create(String name) { }

    /**
     * Remove a Nachos file.
     *
     * @param name  The name of the file to be removed.
     */
    public static void remove(String name) { }

    /**
     * Open the Nachos file "name", and return an "OpenFileId" that can 
     * be used to read and write to the file.
     *
     * @param name  The name of the file to open.
     * @return  An OpenFileId that uniquely identifies the opened file.
     */
    public static int open(String name) {return 0;}

    /**
     * Write "size" bytes from "buffer" to the open file.
     *
     * @param buffer Location of the data to be written.
     * @param size The number of bytes to write.
     * @param id The OpenFileId of the file to which to write the data.
     */
    public static void write(byte buffer[], int size, int id) {
	if (id == ConsoleOutput) {
	    for(int i = 0; i < size; i++) {
		Nachos.consoleDriver.putChar((char)buffer[i]);
		if((char)buffer[i] == '\n') {
		    Nachos.consoleDriver.putChar('\r');
		}
	    }
	}
    }

    /**
     * Read "size" bytes from the open file into "buffer".  
     * Return the number of bytes actually read -- if the open file isn't
     * long enough, or if it is an I/O device, and there aren't enough 
     * characters to read, return whatever is available (for I/O devices, 
     * you should always wait until you can return at least one character).
     *
     * @param vadr Virtual Address of where to put the data read.
     * @param size The number of bytes requested.
     * @param id The OpenFileId of the file from which to read the data.
     * @return The actual number of bytes read.
     */
    public static int read(int vadr, int size, int id) {
	byte buffer[] = new byte[size];
	
	if  (id == ConsoleInput) {
	    for(int i = 0; i < size; i++) {
		buffer[i] = (byte) Nachos.consoleDriver.getChar();;
	    }
	} else {
	    return -1;
	}
	return(ProcessManager.getCurrentSpace().copyoutByte(vadr, buffer, size));
    }

    /**
     * Close the file, we're done reading and writing to it.
     *
     * @param id  The OpenFileId of the file to be closed.
     */
    public static void close(int id) {}

    public static void setRegisters(int PrevPCReg,int PCReg,int NextPCReg,int StackReg) {
	CPU.writeRegister(MIPS.PrevPCReg,PrevPCReg);
	CPU.writeRegister(MIPS.PCReg,PCReg);
	CPU.writeRegister(MIPS.NextPCReg,NextPCReg);
	CPU.writeRegister(MIPS.StackReg,StackReg);
    }
    /*
     * User-level thread operations: Fork and Yield.  To allow multiple
     * threads to run within a user program. 
     */

    /**
     * Fork a thread to run a procedure ("func") in the *same* address space 
     * as the current thread.
     *
     * @param func The user address of the procedure to be run by the
     * new thread.
     */
    public static void fork(int func) {
	final int functionAdr = func;
	
	final AddrSpace space = new AddrSpace();
	space.pageTable =  ProcessManager.getCurrentSpace().newPageTable();
	
	int parentPID = ProcessManager.getCurrentSpace().pid;
	LinkedList<Integer> forkedProcesses = new LinkedList<Integer>();
	if (ProcessManager.forkedPID.containsKey(parentPID)) {
	    forkedProcesses = ProcessManager.forkedPID.get(parentPID);
	}
	forkedProcesses.add(space.pid);
	
	forkedPIDLock.acquire();
	ProcessManager.forkedPID.put(parentPID,forkedProcesses);
	forkedPIDLock.release();
	
	processTableLock.acquire();
	ProcessManager.processTable.put(space.pid,space);
	processTableLock.release();
	
	Runnable execute = new Runnable() {
		public void run() {
		    setRegisters(MIPS.PCReg,functionAdr,functionAdr + 4,Machine.PageSize*space.pageTable.length);
		    space.restoreState();
		    CPU.runUserCode();
		}
	};
	UserThread t = new UserThread("forked thread-" + space.pid, execute, space);
	Nachos.scheduler.readyToRun(t);
}

    /**
     * Yield the CPU to another runnable thread, whether in this address space 
     * or not. 
     */
    public static void yield() {
	Debug.println('t', NachosThread.currentThread().name + " yielded CPU");
	Nachos.scheduler.yieldThread();
    }
    public static void print_message_value(String msg, int val) {
	System.out.println(msg + " " + val);
    }

    public static void sleep(int ticks) {
	extendedNachosThread currThread = (extendedNachosThread) NachosThread.currentThread();
	CPU currentCPU = CPU.currentCPU();
	currThread.waitTicks = ticks;
	Scheduler.sleepingThreads.add(currThread);
	Scheduler.sleepingThreadsCPU.add(currentCPU);
	currThread.semSleep.P();
	Scheduler.sleepingThreads.remove(currThread);
	Scheduler.sleepingThreadsCPU.remove(currentCPU);
    }
    
    public static void mkdir(String path) {
	final String currPath;
	final String dirname;
	
	path = path.substring(1,path.length());
		  
	if (path.contains("/")) {
	    int index = path.lastIndexOf("/");
	    currPath = "/" + path.substring(0, index);
	    dirname = path.substring(index + 1, path.length());
	} else {
	    if (!path.isEmpty()) {
		currPath = "";
		dirname = path;
	    } else {
		currPath = null;
		dirname = null;
	    }
	}
	
	if (currPath != null && dirname != null) {
	    Runnable execute = new Runnable() {
		 public void run() {
		     if (!Nachos.fileSystem.makeDir(currPath, dirname)) {
			    Debug.printf('+', "Can't create %s directory\n", dirname);
			}
		     Nachos.scheduler.finishThread();
		 }
	    };
	    extendedNachosThread thread = new extendedNachosThread("mkdir thread", execute);
	    Nachos.scheduler.readyToRun(thread);
	}
    }
    
    public static void rmdir(String path) {
	final String currPath;
	final String dirname;
	
	path = path.substring(1,path.length());
		  
	if (path.contains("/")) {
	    int index = path.lastIndexOf("/");
	    currPath = "/" + path.substring(0, index);
	    dirname = path.substring(index + 1, path.length());
	} else {
	    if (!path.isEmpty()) {
		currPath = "";
		dirname = path;
	    } else {
		currPath = null;
		dirname = null;
	    }
	}
	
	if (currPath != null && dirname != null) {
	    Runnable execute = new Runnable() {
		 public void run() {
		     if (!Nachos.fileSystem.rmDir(currPath, dirname)) {
			    Debug.printf('+', "Can't delete %s directory\n", dirname);
			}
		     Nachos.scheduler.finishThread();
		 }
	    };
	    extendedNachosThread thread = new extendedNachosThread("rmDir thread", execute);
	    Nachos.scheduler.readyToRun(thread);
	}

    }

}

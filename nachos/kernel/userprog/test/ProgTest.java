// ProgTest.java
//	Test class for demonstrating that Nachos can load
//	a user program and execute it.  
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// Copyright (c) 2003 State University of New York at Stony Brook.
// All rights reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.

package nachos.kernel.userprog.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import nachos.Debug;
import nachos.Options;
import nachos.machine.CPU;
import nachos.kernel.Nachos;
import nachos.kernel.userprog.AddrSpace;
import nachos.kernel.userprog.ProcessManager;
import nachos.kernel.userprog.UserThread;
import nachos.kernel.filesys.OpenFile;

/**
 * This is a test class for demonstrating that Nachos can load a user
 * program and execute it.
 * 
 * @author Thomas Anderson (UC Berkeley), original C++ version
 * @author Peter Druschel (Rice University), Java translation
 * @author Eugene W. Stark (Stony Brook University)
 */
public class ProgTest implements Runnable {

    /** The name of the program to execute. */
    private String execName;
    private static final int TransferSize = 10;
    
    /**
     * Start the test by creating a new address space and user thread,
     * then arranging for the new thread to begin executing the run() method
     * of this class.
     *
     * @param filename The name of the program to execute.
     */
    
    private void copy(String from, String to) {
	File fp;
	FileInputStream fs;
	OpenFile openFile;
	int amountRead;
	long fileLength;
	byte buffer[];

	// Open UNIX file
	fp = new File(from);
	if (!fp.exists()) {
	    Debug.printf('+', "Copy: couldn't open input file %s\n", from);
	    return;
	}

	// Figure out length of UNIX file
	fileLength = fp.length();

	// Create a Nachos file of the same length
	Debug.printf('f', "Copying file %s, size %d, to file %s\n", from,
		new Long(fileLength), to);
	if (!Nachos.fileSystem.create(to, (int)fileLength)) {	 
	    // Create Nachos file
	    Debug.printf('+', "Copy: couldn't create output file %s\n", to);
	    return;
	}

	openFile = Nachos.fileSystem.open(to);
	Debug.ASSERT(openFile != null);

	// Copy the data in TransferSize chunks
	buffer = new byte[TransferSize];
	try {
	    fs = new FileInputStream(fp);
	    while ((amountRead = fs.read(buffer)) > 0)
		openFile.write(buffer, 0, amountRead);	
	} catch (IOException e) {
	    Debug.print('+', "Copy: data copy failed\n");      
	    return;
	}
	// Close the UNIX and the Nachos files
	//delete openFile;
	try {fs.close();} catch (IOException e) {}
    }
    
    public ProgTest(String filename, int num) {
	String name = "ProgTest"+ num + "(" + filename + ")";
	
	Debug.println('+', "starting ProgTest: " + name);

	execName = filename;
	AddrSpace space = new AddrSpace();
	UserThread t = new UserThread(name, this, space);
	Nachos.scheduler.readyToRun(t);
    }

    /**
     * Entry point for the thread created to run the user program.
     * The specified executable file is used to initialize the address
     * space for the current thread.  Once this has been done,
     * CPU.run() is called to transfer control to user mode.
     */
    public void run() {
	String newName = execName.substring(execName.lastIndexOf("/") + 1, execName.length());
	copy(execName, newName);
	OpenFile executable;

	if((executable = Nachos.fileSystem.open(newName)) == null) {
	    Debug.println('+', "Unable to open executable file: " + newName);
	    Nachos.scheduler.finishThread();
	    return;
	}

	AddrSpace space = ProcessManager.getCurrentSpace();
	if(space.exec(executable) == -1) {
	    Debug.println('+', "Unable to read executable file: " + newName);
	    Nachos.scheduler.finishThread();
	    return;
	}

	space.initRegisters();		// set the initial register values
	space.restoreState();		// load page table register

	CPU.runUserCode();			// jump to the user progam
	Debug.ASSERT(false);		// machine->Run never returns;
	// the address space exits
	// by doing the syscall "exit"
    }

    /**
     * Entry point for the test.  Command line arguments are checked for
     * the name of the program to execute, then the test is started by
     * creating a new ProgTest object.
     */
    public static void start() {
	Debug.ASSERT(Nachos.options.FILESYS_REAL || Nachos.options.FILESYS_STUB,
			"A filesystem is required to execute user programs");
	final int[] count = new int[1];
	Nachos.options.processOptions
		(new Options.Spec[] {
			new Options.Spec
				("-x",
				 new Class[] {String.class},
				 "Usage: -x <executable file>",
				 new Options.Action() {
				    public void processOption(String flag, Object[] params) {
					new ProgTest((String)params[0], count[0]++);
				    }
				 })
		 });
    }
}

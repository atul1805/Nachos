// AddrSpace.java
//	Class to manage address spaces (executing user programs).
//
//	In order to run a user program, you must:
//
//	1. link with the -N -T 0 option 
//	2. run coff2noff to convert the object file to Nachos format
//		(Nachos object code format is essentially just a simpler
//		version of the UNIX executable object code format)
//	3. load the NOFF file into the Nachos file system
//		(if you haven't implemented the file system yet, you
//		don't need to do this last step)
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// Copyright (c) 2003 State University of New York at Stony Brook.
// All rights reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.

package nachos.kernel.userprog;

import nachos.Debug;
import nachos.machine.CPU;
import nachos.machine.MIPS;
import nachos.machine.Machine;
import nachos.machine.TranslationEntry;
import nachos.noff.NoffHeader;
import nachos.kernel.filesys.OpenFile;
import nachos.kernel.threads.Lock;
import nachos.kernel.threads.Semaphore;

/**
 * This class manages "address spaces", which are the contexts in which
 * user programs execute.  For now, an address space contains a
 * "segment descriptor", which describes the the virtual-to-physical
 * address mapping that is to be used when the user program is executing.
 * As you implement more of Nachos, it will probably be necessary to add
 * other fields to this class to keep track of things like open files,
 * network connections, etc., in use by a user program.
 *
 * NOTE: Most of what is in currently this class assumes that just one user
 * program at a time will be executing.  You will have to rewrite this
 * code so that it is suitable for multiprogramming.
 * 
 * @author Thomas Anderson (UC Berkeley), original C++ version
 * @author Peter Druschel (Rice University), Java translation
 * @author Eugene W. Stark (Stony Brook University)
 */
public class AddrSpace {

  /** Page table that describes a virtual-to-physical address mapping. */
  public TranslationEntry pageTable[];
  public int pid;
  public static int runningProcess = 0;
  public Semaphore semJoin;
  
  /** Default size of the user stack area -- increase this as necessary! */
  private static final int UserStackSize = 1024;
  public  static final int StackLength = UserStackSize/Machine.PageSize;
	  
  private static final int MaxStringLength = 256;
  
  private Lock pageTableLock;
  private Lock codeDataLock;

  /**
   * Create a new address space.
   */
  public AddrSpace() { 
      this.pageTable = new TranslationEntry[Machine.NumPhysPages];
      this.pageTableLock = new Lock("pageTableLock");
      this.codeDataLock = new Lock("codeDataLock");
      this.pid = ProcessManager.getNextpid();
      this.semJoin = new Semaphore("Join Semaphore",0);
      runningProcess++;
  }
  
  public int getPhysicalAddress(int vadr) {
      TranslationEntry[] currPageTable = this.pageTable;
      
      int vpn = ((vadr>>7) & 0x1ffffff);
      int offset = (vadr & 0x7f);
      int ppn = currPageTable[vpn].physicalPage;
      Debug.ASSERT((ppn <= Machine.NumPhysPages) && ppn >= 0,"ppn out of bound");
      int padr = (ppn*Machine.PageSize) + offset;
      return padr;
  }
  
  public String copyinString(int vadr) {
      int padr = getPhysicalAddress(vadr);
      byte[] buffer = new byte[MaxStringLength];
      for (int i = 0; i < buffer.length; i++) {
	    System.arraycopy(Machine.mainMemory, padr + i, buffer, i, 1);
	    if (buffer[i] == 0) {
		return new String(buffer,0,i);
	    }
	}
      return null;
  }
  
  public byte[] copyinByte(int vadr, int len) {
      int padr = getPhysicalAddress(vadr);
      byte[] buffer = new byte[len];
      for (int i = 0; i < len; i++) {
	    System.arraycopy(Machine.mainMemory, padr + i, buffer, i, 1);
	}
      return buffer;
  }
  
  public int copyoutByte(int vadr, byte[] buffer, int len) {
      int padr = getPhysicalAddress(vadr);
      int i;
      for (i = 0; i < len; i++) {
	    System.arraycopy(buffer,i, Machine.mainMemory, padr + i, 1);
	    if (buffer[i] == 0) {
		return i;
	    }
	}
      return i;
  }
  
  /**
   * Load the program from a file "executable", and set everything
   * up so that we can start executing user instructions.
   *
   * Assumes that the object code file is in NOFF format.
   *
   * First, set up the translation from program memory to physical 
   * memory.  For now, this is really simple (1:1), since we are
   * only uniprogramming.
   *
   * @param executable The file containing the object code to 
   * 	load into memory
   * @return -1 if an error occurs while reading the object file,
   *    otherwise 0.
   */
  public int exec(OpenFile executable) {
    NoffHeader noffH;
    long size;
    
    if((noffH = NoffHeader.readHeader(executable)) == null)
	return(-1);

    // how big is address space?
    size = roundToPage(noffH.code.size)
	     + roundToPage(noffH.initData.size + noffH.uninitData.size)
	     + UserStackSize;	// we need to increase the size
    				// to leave room for the stack
    int numPages = (int)(size / Machine.PageSize);

    Debug.ASSERT((numPages <= Machine.NumPhysPages),// check we're not trying
		 "AddrSpace constructor: Not enough memory!");
                                                // to run anything too big --
						// at least until we have
						// virtual memory

    Debug.println('a', "Initializing address space, numPages=" 
		+ numPages + ", size=" + size);

    pageTableLock.acquire();
    // first, set up the translation 
    pageTable = new TranslationEntry[numPages];
    for (int i = 0; i < numPages; i++) {
      pageTable[i] = new TranslationEntry();
      pageTable[i].virtualPage = i;
      pageTable[i].physicalPage = PhysicalMemoryManager.getFreePage();
      Debug.ASSERT((pageTable[i].physicalPage != -1),"Free page not available");
      pageTable[i].valid = true;
      pageTable[i].use = false;
      pageTable[i].dirty = false;
      pageTable[i].readOnly = false;  // if code and data segments live on
				      // separate pages, we could set code 
				      // pages to be read-only
    }
    ProcessManager.processTable.put(pid,this);
    pageTableLock.release();

    codeDataLock.acquire();
    // Zero out the address space for the process, to zero the code,initialized,uninitialized data 
    // segment and the stack segment.
    for (int i = 0; i < numPages; i++) {
	Machine.mainMemory[pageTable[i].physicalPage*Machine.PageSize] = (byte)0;
    }
    
    int numCodePages = (int) roundToPage(noffH.code.size)/Machine.PageSize;
    // then, copy in the code and data segments into memory
    if (noffH.code.size > 0) {
	Debug.println('a', "Initializing code segment, at " +
		noffH.code.virtualAddr + ", size " +
		noffH.code.size);
	for (int i = 0; i < numCodePages; i++) {
	    executable.seek(noffH.code.inFileAddr + i*Machine.PageSize);
	    executable.read(Machine.mainMemory, pageTable[i].physicalPage*Machine.PageSize,
		    Machine.PageSize);
	}
    }
    
    int numDataPages = (int) roundToPage(noffH.initData.size)/Machine.PageSize;
    if (noffH.initData.size > 0) {
	Debug.println('a', "Initializing data segment, at " +
		    noffH.initData.virtualAddr + ", size " +
		    noffH.initData.size);
	for (int i = 0; i < numDataPages; i++) {
	    executable.seek(noffH.initData.inFileAddr + i*Machine.PageSize);
	    executable.read(Machine.mainMemory, numCodePages + pageTable[i].physicalPage*Machine.PageSize,
		    Machine.PageSize);
	}
    }
    codeDataLock.release();

    return(0);
  }

  public TranslationEntry[] newPageTable() {
      pageTableLock.acquire();
      int length = pageTable.length;
      TranslationEntry[] newpageTable = new TranslationEntry[length];
      for (int i = 0; i < length - StackLength; i++) {
	  newpageTable[i] = new TranslationEntry();
	  newpageTable[i].virtualPage = pageTable[i].virtualPage;
	  newpageTable[i].physicalPage = pageTable[i].physicalPage;
	  newpageTable[i].valid = pageTable[i].valid;
	  newpageTable[i].use = pageTable[i].use;
	  newpageTable[i].dirty = pageTable[i].dirty;
	  newpageTable[i].readOnly = pageTable[i].readOnly;
      }
      
      for (int i = length - StackLength ; i < length; i++) {
	  newpageTable[i] = new TranslationEntry();
	  newpageTable[i].virtualPage = i;
	  newpageTable[i].physicalPage = PhysicalMemoryManager.getFreePage();
	  Debug.ASSERT((newpageTable[i].physicalPage != -1),"Free page not available");
	  newpageTable[i].valid = true;
	  newpageTable[i].use = false;
	  newpageTable[i].dirty = false;
	  newpageTable[i].readOnly = false;
      }
      pageTableLock.release();
      return newpageTable;
  }
  /**
   * Initialize the user-level register set to values appropriate for
   * starting execution of a user program loaded in this address space.
   *
   * We write these directly into the "machine" registers, so
   * that we can immediately jump to user code.
   */
  public void initRegisters() {
    int i;
   
    for (i = 0; i < MIPS.NumTotalRegs; i++)
      CPU.writeRegister(i, 0);

    // Initial program counter -- must be location of "Start"
    CPU.writeRegister(MIPS.PCReg, 0);	

    // Need to also tell MIPS where next instruction is, because
    // of branch delay possibility
    CPU.writeRegister(MIPS.NextPCReg, 4);

    // Set the stack register to the end of the segment.
    // NOTE: Nachos traditionally subtracted 16 bytes here,
    // but that turns out to be to accomodate compiler convention that
    // assumes space in the current frame to save four argument registers.
    // That code rightly belongs in start.s and has been moved there.
    int sp = pageTable.length * Machine.PageSize;
    CPU.writeRegister(MIPS.StackReg, sp);
    Debug.println('a', "Initializing stack register to " + sp);
  }

  /**
   * On a context switch, save any machine state, specific
   * to this address space, that needs saving.
   *
   * For now, nothing!
   */
  public void saveState() { }

  /**
   * On a context switch, restore any machine state specific
   * to this address space.
   *
   * For now, just tell the machine where to find the page table.
   */
  public void restoreState() {
    CPU.setPageTable(pageTable);
  }

  /**
   * Utility method for rounding up to a multiple of CPU.PageSize;
   */
  private long roundToPage(long size) {
    return(Machine.PageSize * ((size+(Machine.PageSize-1))/Machine.PageSize));
  }
}

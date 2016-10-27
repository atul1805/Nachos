// ConsoleDriver.java
//
// Copyright (c) 2003 State University of New York at Stony Brook.
// All rights reserved.  See the COPYRIGHT file for copyright notice and 
// limitation of liability and disclaimer of warranty provisions.

package nachos.kernel.devices;

import java.util.LinkedList;
import nachos.machine.Console;
import nachos.machine.InterruptHandler;
import nachos.kernel.threads.Lock;
import nachos.kernel.threads.Semaphore;

/**
 * This class provides for the initialization of the NACHOS console,
 * and gives NACHOS user programs a capability of outputting to the console.
 * This driver does not perform any input or output buffering, so a thread
 * performing output must block waiting for each individual character to be
 * printed, and there are no input-editing (backspace, delete, and the like)
 * performed on input typed at the keyboard.
 * 
 * Students will rewrite this into a full-fledged interrupt-driven driver
 * that provides efficient, thread-safe operation, along with echoing and
 * input-editing features.
 * 
 * @author Eugene W. Stark
 */
public class ConsoleDriver {
    
    /** Raw console device. */
    private Console console;

    /** Lock used to ensure at most one thread trying to input at a time. */
    private Lock inputLock;
    
    /** Lock used to ensure at most one thread trying to output at a time. */
    private Lock outputLock;
    
    /** Semaphore used to indicate that an input character is available. */
    private Semaphore charAvail = new Semaphore("Console char avail", 0);
    
    boolean isConsoleBusy = false;
    
    /** Interrupt handler used for console keyboard interrupts. */
    private InterruptHandler inputHandler;
    
    /** Interrupt handler used for console output interrupts. */
    private InterruptHandler outputHandler;
    
    private LinkedList<Character> getCharBuf = new LinkedList<Character>();
    
    private LinkedList<Character> putCharBuf = new LinkedList<Character>();
    
    private LinkedList<Character> echoBuf = new LinkedList<Character>();
    
    /**
     * Initialize the driver and the underlying physical device.
     * 
     * @param console  The console device to be managed.
     */
    public ConsoleDriver(Console console) {
	inputLock = new Lock("console driver input lock");
	outputLock = new Lock("console driver output lock");
	this.console = console;
	// Delay setting the interrupt handlers until first use.
    }
    
    /**
     * Create and set the keyboard interrupt handler, if one has not
     * already been set.
     */
    private void ensureInputHandler() {
	if(inputHandler == null) {
	    inputHandler = new InputHandler();
	    console.setInputHandler(inputHandler);
	}
    }

    /**
     * Create and set the output interrupt handler, if one has not
     * already been set.
     */
    private void ensureOutputHandler() {
	if(outputHandler == null) {
	    outputHandler = new OutputHandler();
	    console.setOutputHandler(outputHandler);
	}
    }

    /**
     * Wait for a character to be available from the console and then
     * return the character.
     */
    public char getChar() {
	inputLock.acquire();
	ensureInputHandler();
	if (getCharBuf.isEmpty()) {
	    charAvail.P();
	}
	inputLock.release();
	return getCharBuf.removeFirst();
    }
    
    /**
     * Print a single character on the console.  If the console is already
     * busy outputting a character, then wait for it to finish before
     * attempting to output the new character.  A lock is employed to ensure
     * that at most one thread at a time will attempt to print.
     *
     * @param ch The character to be printed.
     */
    public void putChar(char ch) {
	outputLock.acquire();
	ensureOutputHandler();
	if(!isConsoleBusy) {
	    isConsoleBusy = true;
	    console.putChar(ch);
	} else {
	    putCharBuf.add(ch);
	}
	outputLock.release();
    }
    
    /**
     * Stop the console device.
     * This removes the interrupt handlers, which otherwise prevent the
     * Nachos simulation from terminating automatically.
     */
    public void stop() {
	inputLock.acquire();
	console.setInputHandler(null);
	inputLock.release();
	outputLock.acquire();
	console.setOutputHandler(null);
	outputLock.release();
    }
    
    /**
     * Interrupt handler for the input (keyboard) half of the console.
     */
    private class InputHandler implements InterruptHandler {
	
	@Override
	public void handleInterrupt() {
	    char ch = console.getChar();
	    if (ch >= 32 && ch <=126) {
		isConsoleBusy = true;
		console.putChar(ch);
		getCharBuf.add(ch);
	    } else if (ch == '\r' || ch == '\n') {
		isConsoleBusy = true;
		console.putChar('\r');
		echoBuf.add('\n');
		getCharBuf.add(ch);
		charAvail.V();
	    } else if (ch == '\b') {
		if (getCharBuf.size() > 0) {
		    isConsoleBusy = true;
		    console.putChar('\b');
		    echoBuf.add(' ');
		    echoBuf.add('\b');
		    getCharBuf.removeLast();
		}
	    } else if ( ch == 21) {
		int size = getCharBuf.size();
		int start = getCharBuf.lastIndexOf('\n') + 1;
		if (start == -1) {
		    start = 0;
		}
		isConsoleBusy = true;
		console.putChar('\b');
		echoBuf.add(' ');
		echoBuf.add('\b');
		getCharBuf.removeLast();
		for (int i = start; i < size-1; i++) {
		    echoBuf.add('\b');
		    echoBuf.add(' ');
		    echoBuf.add('\b');
		    getCharBuf.removeLast();
		}
	    } else if (ch == 18) {
		int size = getCharBuf.size();
		int start = getCharBuf.lastIndexOf('\n') + 1;
		if (start == -1) {
		    start = 0;
		}
		isConsoleBusy = true;
		console.putChar('\b');
		echoBuf.add(' ');
		echoBuf.add('\b');
		for (int i = start; i < size - 1; i++) {
		    echoBuf.add('\b');
		    echoBuf.add(' ');
		    echoBuf.add('\b');
		}
		for (int i = start; i < size; i++) {
		    echoBuf.add(getCharBuf.get(i));
		}
	    }
	}
    }
    
    /**
     * Interrupt handler for the output (screen) half of the console.
     */
    private class OutputHandler implements InterruptHandler {
	
	@Override
	public void handleInterrupt() {
	    if (putCharBuf.isEmpty() && echoBuf.isEmpty()) {
		isConsoleBusy = false;
	    }
	    if (!putCharBuf.isEmpty()) {
		char ch = putCharBuf.removeFirst();
		console.putChar(ch);
	    }
	    if (!echoBuf.isEmpty()){
		char ch = echoBuf.removeFirst();
		console.putChar(ch);
	    }
	}
 	
    }
}

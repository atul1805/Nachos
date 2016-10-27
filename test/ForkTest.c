#include "syscall.h"

int main() {
	void (*functionPtr1)();
	void (*functionPtr2)();
	void (*functionPtr3)();
	void (*functionPtr4)();
	void display1();
	void display2();
	void display3();
	void display4();
	functionPtr1 = display1;
	Fork(functionPtr1);
	functionPtr2 = display2;
	Fork(functionPtr2);
	functionPtr3 = display3;
	Fork(functionPtr3);
	functionPtr4 = display4;
	Fork(functionPtr4);
}

void display1() {
	int n;
	for (n = 1; n <= 2000; n++) 
	PrintMessageAndValue("loop counter",n);
	Exit(0);
}

void display2() {
	int n;
	for (n = 4000; n > 2000; n--) 
	PrintMessageAndValue("loop counter", n);
	Exit(0);
}

void display3() {
	int n;
	for (n = 6000; n > 4000; n--) 
	PrintMessageAndValue("loop counter", n);
	Exit(0);
}

void display4() {
	int n;
	for (n = 8000; n > 6000; n--) 
	PrintMessageAndValue("loop counter", n);
	Exit(0);
}

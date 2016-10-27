#include "syscall.h"

int main()
{
  void (*functionPtr1)();
  void display1();
  char buf[10];
  int num;

  Write("Stony Brook University\n", 23, ConsoleOutput);

  functionPtr1 = display1;
  Fork(functionPtr1);
  num = Read(buf, 10, ConsoleInput);
  PrintMessageAndValue("Bytes read", num);
}

void display1() {
	int n;
	Sleep(10000000);
	Exit(0);
}

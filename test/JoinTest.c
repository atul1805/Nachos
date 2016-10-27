#include "syscall.h"

int main()
{
  int waitpid;
  int result;

  waitpid =  Exec("test/test1");
  result = Join(waitpid);
  waitpid = Exec("test/test2");
  result = Join(waitpid);
  result = Join(waitpid);
}

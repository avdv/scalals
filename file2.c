#include <stdio.h>

extern void foo2(void);

void foo1(void) {
  foo2();
}

void foo4(void) {
  printf("Foo4");
}


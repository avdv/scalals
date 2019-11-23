#include <stdio.h>

extern void foo1(void);
extern void foo4(void);

void foo2(void) {
  printf("Foo2\n");
}

void foo3(void) {
  foo4();
}

int main(void) {
  foo1();
}


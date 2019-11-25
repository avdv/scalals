#include <sys/stat.h>

long f() {
  struct stat s;

  return s.st_mtim.tv_nsec;
}

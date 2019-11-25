#include <sys/stat.h>

long f() {
  struct stat s;

# ifndef __APPLE__
  return s.st_mtim.tv_nsec;
# else
  return s.st_mtimespec.tv_nsec;
# endif
}

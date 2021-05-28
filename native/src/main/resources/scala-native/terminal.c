#include <sys/ioctl.h>
#include <unistd.h>
#include <stdbool.h>

long int scalanative_tiocgwinsize() {
  return TIOCGWINSZ;
}

bool scalanative_isatty(int fd) {
  return isatty(fd);
}

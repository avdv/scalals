#include <stdbool.h>

#if defined(_WIN32)
#  define WIN32_LEAN_AND_MEAN
#  include <windows.h>
#  include <io.h>

long int scalanative_consoleWidth() {
  CONSOLE_SCREEN_BUFFER_INFO csbi;

  if (GetConsoleScreenBufferInfo(GetStdHandle(STD_OUTPUT_HANDLE), &csbi)) {
    return csbi.srWindow.Right - csbi.srWindow.Left + 1;
  } else {
    return 80;
  }
}

#else /* Unix */

#include <fcntl.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <unistd.h>

long int scalanative_consoleWidth() {
  struct winsize winsz;
  unsigned int cols;
  int tty = open("/dev/tty", O_RDWR, 0u);

  if (tty == -1) {
    tty = STDOUT_FILENO;
  }

  if (ioctl(tty, TIOCGWINSZ, &winsz) >= 0) {
    cols = winsz.ws_col;
  } else {
    const char* columns = getenv("COLUMNS");
    if (!columns || (cols = atoi(columns)) == 0) {
      cols = 80;
    }
  }

  if (tty != STDOUT_FILENO) {
    close(tty);
  }
  return cols;
}

#endif

bool scalanative_isatty(int fd) {
#ifdef _WIN32
  return _isatty(fd);
#else
  return isatty(fd);
#endif
}

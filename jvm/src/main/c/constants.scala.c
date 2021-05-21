
#include <sys/stat.h>
#include <stdio.h>

#define constant(LC) {#LC, LC}

int main() {
  struct {
    const char* name;
    unsigned int value;
  } list[] = {
    constant(S_ISUID),
    constant(S_ISGID),
    constant(S_ISVTX),
    constant(S_IRUSR),
    constant(S_IRGRP),
    constant(S_IROTH),
    constant(S_IWUSR),
    constant(S_IWGRP),
    constant(S_IWOTH),
    constant(S_IXUSR),
    constant(S_IXGRP),
    constant(S_IXOTH),

    constant(S_IFSOCK),
    constant(S_IFIFO),
    constant(S_IFBLK),
    constant(S_IFCHR),

    constant(S_IFMT)
  };

  puts("package de.bley.scalals\n"
       "\n"
       "object UnixConstants {");

  for (int i = 0; i < sizeof(list) / sizeof(*list); ++i) {
    printf("  val %s: Int = 0x%x\n", list[i].name, list[i].value);
  }

  puts("}");
}
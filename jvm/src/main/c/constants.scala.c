
#include <sys/stat.h>

#define constant(LC) _ ## LC = LC

constant(S_ISUID)
constant(S_ISGID)
constant(S_ISVTX)
constant(S_IRUSR)
constant(S_IRGRP)
constant(S_IROTH)
constant(S_IWUSR)
constant(S_IWGRP)
constant(S_IWOTH)
constant(S_IXUSR)
constant(S_IXGRP)
constant(S_IXOTH)

constant(S_IFSOCK)
constant(S_IFIFO)
constant(S_IFBLK)
constant(S_IFCHR)

constant(S_IFMT)

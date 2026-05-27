#if defined(__riscv) && __riscv_xlen == 64
// Stub for riscv64 to work around scala-native 0.5.x delimcc bug
void scalanative_continuation_init(void *(*alloc_f)(unsigned long, void *)) {
    // No-op stub for riscv64
}
#endif

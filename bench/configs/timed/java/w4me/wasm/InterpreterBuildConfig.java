package w4me.wasm;

/** Compile-time configuration for the non-instrumented timing artifact. */
final class InterpreterBuildConfig {
    static final boolean DIAGNOSTIC_COUNTERS = false;
    static final boolean PROFILING_SUPPORT = false;
    static final boolean RESIDENT_CODE_FAST_PATH = true;
    static final boolean DESCRIPTOR_SHADOW = false;
    static final boolean INLINE_BRANCH_FAST_PATH = false;
    static final boolean DIRECT_BRANCH_FAST_PATH = true;

    private InterpreterBuildConfig() {}
}

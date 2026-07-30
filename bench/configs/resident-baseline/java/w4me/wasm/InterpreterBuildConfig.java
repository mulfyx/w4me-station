package w4me.wasm;

/** Compile-time baseline for the resident-code range-check A/B. */
final class InterpreterBuildConfig {
    static final boolean DIAGNOSTIC_COUNTERS = true;
    static final boolean PROFILING_SUPPORT = true;
    static final boolean RESIDENT_CODE_FAST_PATH = false;
    static final boolean DESCRIPTOR_SHADOW = false;
    static final boolean INLINE_BRANCH_FAST_PATH = false;
    static final boolean DIRECT_BRANCH_FAST_PATH = true;

    private InterpreterBuildConfig() {}
}

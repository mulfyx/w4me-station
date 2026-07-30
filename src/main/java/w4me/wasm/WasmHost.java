package w4me.wasm;

/** Provides the WASM host implementation. */
public interface WasmHost {
    int IMPORT_TEXT_UTF8 = 0;
    int IMPORT_TEXT = 1;
    int IMPORT_TEXT_UTF16 = 2;
    int IMPORT_RECT = 3;
    int IMPORT_BLIT = 4;
    int IMPORT_BLIT_SUB = 5;
    int IMPORT_LINE = 6;
    int IMPORT_HLINE = 7;
    int IMPORT_VLINE = 8;
    int IMPORT_OVAL = 9;
    int IMPORT_DISK_READ = 10;
    int IMPORT_DISK_WRITE = 11;
    int IMPORT_TONE = 12;
    int IMPORT_TRACE = 13;
    int IMPORT_TRACEF = 14;
    int IMPORT_TRACE_UTF8 = 15;
    int IMPORT_TRACE_UTF16 = 16;

    /** Performs the invoke operation. */
    long invoke(int importId, long[] valueStack, int argumentBase, int argumentCount, WasmModule wasmModule);

    /** Performs the invoke operation. */
    long invoke(
            String module, String name, long[] valueStack, int argumentBase, int argumentCount, WasmModule wasmModule);
}

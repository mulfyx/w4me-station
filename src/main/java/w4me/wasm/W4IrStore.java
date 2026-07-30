package w4me.wasm;

/** Optional persistent backing store for decoded W4IR code. */
public interface W4IrStore {
    /** Reports whether complete. */
    boolean isComplete(int functionCount);

    /** Loads the function. */
    W4IrFunction loadFunction(int functionIndex) throws WasmException;

    /** Performs the begin operation. */
    void begin(int functionCount) throws WasmException;

    /** Writes the function. */
    void writeFunction(
            int functionIndex,
            int declaredLocalCount,
            int[] code,
            int[][] branchTables,
            int[] branchDescriptors,
            int[] branchDescriptorPcs,
            int[] branchDescriptorIndices,
            int[][] branchDescriptorTables,
            long fingerprint,
            int intrinsic)
            throws WasmException;

    /** Performs the commit operation. */
    void commit() throws WasmException;

    /** Loads the page. */
    int[] loadPage(W4IrFunction function, int pageIndex);

    /** Performs the page faults operation. */
    int pageFaults();

    /** Performs the page hits operation. */
    int pageHits();

    /** Performs the discard operation. */
    void discard();

    /** Performs the close operation. */
    void close();
}

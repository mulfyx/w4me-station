package w4me.wasm;

/** Persisted metadata for one decoded W4IR function. */
public final class W4IrFunction {
    // A multiple of the three-int instruction stride (3 KiB payload).
    public static final int INSTRUCTION_STRIDE = 3;
    public static final int PAGE_INTS = 768;
    public static final int BRANCH_DESCRIPTOR_STRIDE = 5;
    public static final int MAX_FUNCTIONS = 4096;
    public static final int MAX_DECLARED_LOCALS = 4096;
    public static final int MAX_BRANCH_TARGETS = 4096;
    public static final int MAX_BRANCH_DESCRIPTORS = 65536;
    public static final int MAX_INSTRUCTIONS = 32768;
    public static final int MAX_INTRINSIC = 2;

    private final int functionIndex;
    private final int declaredLocalCount;
    private final int instructionCount;
    private final int[][] branchTables;
    private final int[] branchDescriptors;
    private final int[] branchDescriptorPcs;
    private final int[] branchDescriptorIndices;
    private final int[][] branchDescriptorTables;
    private final long fingerprint;
    private final int intrinsic;
    private final int[] pageRecordIds;

    /** Creates a new W4IR function. */
    public W4IrFunction(
            int functionIndex,
            int declaredLocalCount,
            int instructionCount,
            int[][] branchTables,
            int[] branchDescriptors,
            int[] branchDescriptorPcs,
            int[] branchDescriptorIndices,
            int[][] branchDescriptorTables,
            long fingerprint,
            int intrinsic,
            int[] pageRecordIds) {
        this.functionIndex = functionIndex;
        this.declaredLocalCount = declaredLocalCount;
        this.instructionCount = instructionCount;
        this.branchTables = branchTables;
        this.branchDescriptors = branchDescriptors;
        this.branchDescriptorPcs = branchDescriptorPcs;
        this.branchDescriptorIndices = branchDescriptorIndices;
        this.branchDescriptorTables = branchDescriptorTables;
        this.fingerprint = fingerprint;
        this.intrinsic = intrinsic;
        this.pageRecordIds = pageRecordIds;
    }

    /** Performs the function index operation. */
    public int functionIndex() {
        return functionIndex;
    }

    /** Performs the declared local count operation. */
    public int declaredLocalCount() {
        return declaredLocalCount;
    }

    /** Performs the instruction count operation. */
    public int instructionCount() {
        return instructionCount;
    }

    /** Performs the branch tables operation. */
    public int[][] branchTables() {
        return branchTables;
    }

    /** Performs the branch descriptors operation. */
    public int[] branchDescriptors() {
        return branchDescriptors;
    }

    /** Performs the branch descriptor pcs operation. */
    public int[] branchDescriptorPcs() {
        return branchDescriptorPcs;
    }

    /** Performs the branch descriptor indices operation. */
    public int[] branchDescriptorIndices() {
        return branchDescriptorIndices;
    }

    /** Performs the branch descriptor tables operation. */
    public int[][] branchDescriptorTables() {
        return branchDescriptorTables;
    }

    /** Performs the fingerprint operation. */
    public long fingerprint() {
        return fingerprint;
    }

    /** Performs the intrinsic operation. */
    public int intrinsic() {
        return intrinsic;
    }

    /** Performs the page count operation. */
    public int pageCount() {
        return pageRecordIds.length;
    }

    /** Performs the page record id operation. */
    public int pageRecordId(int pageIndex) {
        return pageRecordIds[pageIndex];
    }
}

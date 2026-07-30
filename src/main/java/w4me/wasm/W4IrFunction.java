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

    public int functionIndex() {
        return functionIndex;
    }

    public int declaredLocalCount() {
        return declaredLocalCount;
    }

    public int instructionCount() {
        return instructionCount;
    }

    public int[][] branchTables() {
        return branchTables;
    }

    public int[] branchDescriptors() {
        return branchDescriptors;
    }

    public int[] branchDescriptorPcs() {
        return branchDescriptorPcs;
    }

    public int[] branchDescriptorIndices() {
        return branchDescriptorIndices;
    }

    public int[][] branchDescriptorTables() {
        return branchDescriptorTables;
    }

    public long fingerprint() {
        return fingerprint;
    }

    public int intrinsic() {
        return intrinsic;
    }

    public int pageCount() {
        return pageRecordIds.length;
    }

    public int pageRecordId(int pageIndex) {
        return pageRecordIds[pageIndex];
    }
}

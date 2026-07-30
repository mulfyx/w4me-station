package w4me.midp;

/** Provides the game of life generic corpus benchmark midlet implementation. */
public final class GameOfLifeGenericCorpusBenchmarkMidlet extends GenericCorpusBenchmarkMidlet {
    /** Performs the workload operation. */
    protected int workload() {
        return GAME_OF_LIFE;
    }
}

package w4me;

/** Deterministic pointer route that solves Untangle level 0. */
public final class UntangleBenchmarkRoute {
    public static final int FRAMES = 401;
    public static final int FINAL_FRAMEBUFFER_FNV1A = 0xbc0231d9;

    private UntangleBenchmarkRoute() {}

    /** Performs the mouse x operation. */
    public static int mouseX(int frame) {
        if (frame == 0) {
            return 32767;
        }
        if (frame <= 2) {
            return 80;
        }
        if (frame <= 152) {
            return 20;
        }
        if (frame == 153 || frame == 156) {
            return 28;
        }
        if (frame <= 158) {
            return 120;
        }
        if (frame == 159 || frame == 165) {
            return 131;
        }
        if (frame <= 162) {
            return 80;
        }
        return 40;
    }

    /** Performs the mouse y operation. */
    public static int mouseY(int frame) {
        if (frame == 0) {
            return 32767;
        }
        if (frame <= 2) {
            return 80;
        }
        if (frame <= 152) {
            return 72;
        }
        if (frame == 153) {
            return 49;
        }
        if (frame <= 155) {
            return 50;
        }
        if (frame == 156) {
            return 109;
        }
        if (frame <= 159) {
            return 110;
        }
        if (frame <= 161) {
            return 130;
        }
        if (frame == 162) {
            return 140;
        }
        if (frame <= 164) {
            return 110;
        }
        return 50;
    }

    /** Performs the mouse buttons operation. */
    public static int mouseButtons(int frame) {
        if (frame == 1 || frame == 4) {
            return 1;
        }
        if ((frame >= 153 && frame <= 154)
                || (frame >= 156 && frame <= 157)
                || (frame >= 159 && frame <= 160)
                || (frame >= 162 && frame <= 163)
                || (frame >= 165 && frame <= 166)) {
            return 1;
        }
        return 0;
    }

    /** Performs the phase operation. */
    public static int phase(int frame) {
        if (frame <= 5) {
            return 0;
        }
        if (frame <= 69) {
            return 1;
        }
        if (frame <= 71) {
            return 2;
        }
        if (frame <= 152) {
            return 3;
        }
        if (frame <= 168) {
            return 4;
        }
        return 5;
    }

    /** Performs the phase name operation. */
    public static String phaseName(int phase) {
        switch (phase) {
            case 0:
                return "title";
            case 1:
                return "curtain";
            case 2:
                return "init";
            case 3:
                return "ready";
            case 4:
                return "solve";
            case 5:
                return "win";
            default:
                throw new IllegalArgumentException("unknown phase: " + phase);
        }
    }
}

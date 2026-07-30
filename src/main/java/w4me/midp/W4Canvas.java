package w4me.midp;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;

import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.AudioBackends;
import w4me.runtime.audio.Wasm4Apu;
import w4me.runtime.storage.DiskBackend;
import w4me.runtime.storage.DiskBackends;
import w4me.runtime.storage.W4IrStores;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.W4IrStore;
import w4me.wasm.WasmModule;

final class W4Canvas extends GameCanvas implements Runnable {
    private static final int BAND_HEIGHT = 16;
    private static final int MENU_SOFTKEY = -7;
    private static final int BUTTON_1 = 1;
    private static final int BUTTON_2 = 2;
    private static final int BUTTON_LEFT = 16;
    private static final int BUTTON_RIGHT = 32;
    private static final int BUTTON_UP = 64;
    private static final int BUTTON_DOWN = 128;

    private volatile boolean running;
    private volatile SystemMenuState menuState = new SystemMenuState();
    private volatile Session activeSession;
    private final SingleSaveState saveState = new SingleSaveState();
    private Thread worker;
    // Guards every held/pending input pair below. LCDUI delivers key and pointer
    // events on the event thread while the worker samples them once per frame, so
    // the read-modify-write pairs cannot be left to volatile alone.
    private final Object inputLock = new Object();
    // held* mirrors the physical state; pending* latches a released-to-pressed
    // transition so that a press completed between two frame samples is still
    // reported for exactly one frame. This is the model GameCanvas already applies
    // to the game keys; it does not cover the inputs below, so W4Canvas applies it
    // itself rather than dropping events MIDP has already delivered.
    private volatile int extraButtons;
    private int pendingExtraButtons;
    private volatile int extraGamepad2;
    private int pendingExtraGamepad2;
    private volatile int pointerX = 0x7fff;
    private volatile int pointerY = 0x7fff;
    private volatile int pointerButtons;
    private int pendingPointerButtons;
    private int pendingPointerX = 0x7fff;
    private int pendingPointerY = 0x7fff;
    private volatile int touchButtons;
    private int pendingTouchButtons;
    private int frameGamepad1;
    private int frameGamepad2;
    private int framePointerButtons;
    private int framePointerX = 0x7fff;
    private int framePointerY = 0x7fff;
    private boolean touchControlGesture;
    private final W4MeMidlet midlet;
    private final String cartridgeResource;
    private final String cartridgeTitle;
    private final W4SessionMonitor monitor;
    private String status;
    private String notification;
    private int notificationFrames;
    private int[] bandPixels;
    private int[] xMap;
    private int[] yMap;
    private int renderSide;
    private boolean bandRenderer;

    W4Canvas(
            W4MeMidlet midlet,
            String cartridgeResource,
            String cartridgeTitle,
            W4SessionMonitor monitor) {
        super(false);
        this.midlet = midlet;
        this.cartridgeResource = cartridgeResource;
        this.cartridgeTitle = cartridgeTitle;
        this.monitor = monitor;
        status = "Loading " + cartridgeTitle + "...";
        setFullScreenMode(true);
    }

    synchronized void start() {
        if (running) {
            return;
        }
        if (worker != null && worker.isAlive()) {
            return;
        }
        if (menuState.isStopped()) {
            menuState = new SystemMenuState();
        }
        running = true;
        worker = new Thread(this);
        worker.start();
    }

    void stop() {
        Thread activeWorker;
        synchronized (this) {
            running = false;
            menuState.stop();
            activeWorker = worker;
        }
        clearInput();
        saveState.clear();
        if (activeWorker != null && activeWorker != Thread.currentThread()) {
            activeWorker.interrupt();
            try {
                activeWorker.join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (this) {
            if (worker == activeWorker
                    && (activeWorker == null || !activeWorker.isAlive())) {
                worker = null;
            }
        }
    }

    public void run() {
        boolean leaveToLibrary = false;
        try {
            while (running && !menuState.isStopped()) {
                int outcome = runSession();
                if (outcome == SystemMenuState.RESTART_REQUESTED
                        && running
                        && !menuState.isStopped()) {
                    status = "Restarting " + cartridgeTitle + "...";
                    notification = null;
                    notificationFrames = 0;
                    renderStatus();
                    menuState.completeRestart();
                    showNotification("Restarted");
                    continue;
                }
                if (outcome == SystemMenuState.LEAVE_REQUESTED) {
                    leaveToLibrary = true;
                }
                break;
            }
        } catch (Throwable failure) {
            saveState.clear();
            if (running && !menuState.isStopped()) {
                status = failure.toString();
                System.out.println("W4ME_ERROR " + failure.toString());
                midlet.showCartridgeFailure(this, cartridgeTitle, failure);
            }
        } finally {
            saveState.clear();
            synchronized (this) {
                running = false;
                if (worker == Thread.currentThread()) {
                    worker = null;
                }
            }
        }
        if (leaveToLibrary) {
            midlet.finishCanvasExit(this);
        }
    }

    private int runSession() throws Exception {
        Wasm4Apu audio = null;
        Wasm4Runtime activeRuntime = null;
        WasmModule activeModule = null;
        Session session = null;
        CartridgeStore installStore = null;
        int stagedRecordId = 0;
        int outcome = -1;
        try {
            byte[] cartridge;
            if (isExternalCartridge()) {
                status = "Reading " + cartridgeTitle + "...";
                repaint();
                installStore = CartridgeStore.open();
                if (monitor != null) {
                    monitor.onInstallState("DOWNLOADING", 0, 0, 0, 0);
                }
                stagedRecordId =
                        ResourceLoader.stage(installStore, cartridgeTitle, cartridgeResource);
                cartridge = installStore.readStaged(stagedRecordId);
                status = "Validating " + cartridgeTitle + "...";
                repaint();
                if (monitor != null) {
                    monitor.onInstallState(
                            "RECEIVED",
                            stagedRecordId,
                            cartridge.length,
                            (cartridge.length + 2047) / 2048,
                            0);
                    monitor.onInstallState("VALIDATING", 0, 0, 0, 0);
                }
            } else {
                cartridge = ResourceLoader.read(cartridgeResource);
            }
            byte[] font = ResourceLoader.read("/w4font.bin");
            boolean fastPathsEnabled =
                    !"true".equals(midlet.getAppProperty("W4ME-Disable-Fast-Paths"));
            boolean extendedFusionsEnabled =
                    !"true".equals(midlet.getAppProperty("W4ME-Disable-Extended-Fusions"));
            boolean compactExecutorEnabled =
                    !"true".equals(midlet.getAppProperty("W4ME-Disable-Compact-Executor"));
            boolean traceExecutorEnabled =
                    !"true".equals(midlet.getAppProperty("W4ME-Disable-Trace-Executor"));
            boolean directNumericIntrinsicsEnabled =
                    !"true".equals(
                            midlet.getAppProperty("W4ME-Disable-Direct-Numeric-Intrinsics"));
            W4IrStore w4irStore =
                    extendedFusionsEnabled ? W4IrStores.create(cartridge) : null;
            if (isExternalCartridge() && monitor != null) {
                monitor.onInstallState("TRANSLATING", 0, 0, 0, 0);
            }
            WasmModule module =
                    WasmModule.read(cartridge, w4irStore, extendedFusionsEnabled);
            activeModule = module;
            if (isExternalCartridge()) {
                status = "Committing " + cartridgeTitle + "...";
                repaint();
                CartridgeStore.CartridgeInfo installed =
                        installStore.commitStaged(stagedRecordId);
                stagedRecordId = 0;
                installStore.close();
                installStore = null;
                if (monitor != null) {
                    monitor.onInstallState(
                            "COMMITTED",
                            installed.recordId,
                            installed.length,
                            installed.chunks,
                            installed.hash);
                }
            }
            if (monitor != null) {
                monitor.onLoad(
                        cartridge.length,
                        cartridgeSource(),
                        module,
                        fastPathsEnabled,
                        extendedFusionsEnabled,
                        compactExecutorEnabled,
                        traceExecutorEnabled,
                        directNumericIntrinsicsEnabled);
            }
            audio =
                    new Wasm4Apu(
                            AudioBackends.create(midlet.audioBackendPreference()));
            midlet.configureAudio(audio);
            audio.setDiagnostic(monitor != null && monitor.audioDiagnostics());
            int cartridgeIdentity = DiskBackends.cartridgeIdentity(cartridge);
            DiskBackend disk = DiskBackends.create(cartridgeIdentity);
            Wasm4Runtime runtime = new Wasm4Runtime(font, audio, disk);
            activeRuntime = runtime;
            runtime.initialize(module);
            WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
            interpreter.setFastPathsEnabled(fastPathsEnabled);
            interpreter.setCompactExecutorEnabled(compactExecutorEnabled);
            interpreter.setTraceExecutorEnabled(traceExecutorEnabled);
            interpreter.setDirectNumericIntrinsicsEnabled(
                    directNumericIntrinsicsEnabled);
            interpreter.setInstructionLimit(150000000L);
            interpreter.invokeCartridgeLifecycle();
            status = null;
            session =
                    new Session(
                            runtime,
                            module,
                            interpreter,
                            audio,
                            cartridgeIdentity,
                            cartridge.length);
            activeSession = session;
            outcome = runFrames(session);
            return outcome;
        } finally {
            if (installStore != null) {
                if (stagedRecordId != 0) {
                    installStore.discardStaged(stagedRecordId);
                }
                installStore.close();
            }
            if (session != null) {
                session.close();
            } else {
                if (activeRuntime != null) {
                    activeRuntime.close();
                } else if (audio != null) {
                    audio.close();
                }
                if (activeModule != null) {
                    activeModule.close();
                }
            }
            if (activeSession == session) {
                activeSession = null;
            }
            if (session != null && monitor != null) {
                monitor.onSessionClosed(sessionCloseReason(outcome));
            }
        }
    }

    private String sessionCloseReason(int outcome) {
        if (outcome == SystemMenuState.RESTART_REQUESTED) {
            return "restart";
        }
        if (outcome == SystemMenuState.LEAVE_REQUESTED) {
            return "exit";
        }
        if (!running || menuState.isStopped()) {
            return "lifecycle";
        }
        return "failure";
    }

    private int runFrames(Session session) throws Exception {
        int frameNumber = 0;
        long windowUpdateMillis = 0;
        long windowRenderMillis = 0;
        int presentationDivisor = 1;
        int windowPresentedFrames = 0;
        boolean menuSuspendedAudio = false;
        while (running && !menuState.stopped) {
            if (menuState.state == SystemMenuState.MENU_REQUESTED
                    && menuState.acceptMenuAtFrameBoundary()) {
                clearInput();
                session.audio.suspendOutput();
                menuSuspendedAudio = true;
                renderFrame(session.runtime, session.module, true);
                midlet.showSystemMenu(this);
            }
            while (running && menuState.isMenuOpen()) {
                menuState.awaitChange();
            }
            if (!running || menuState.stopped) {
                break;
            }
            int requested = menuState.state;
            if (requested == SystemMenuState.RESTART_REQUESTED
                    || requested == SystemMenuState.LEAVE_REQUESTED) {
                saveState.clear();
                return requested;
            }
            boolean renderAfterMenuAction = false;
            if (requested == SystemMenuState.SAVE_REQUESTED) {
                boolean saved =
                        saveState.save(
                                session.cartridgeIdentity,
                                session.cartridgeLength,
                                session.module,
                                session.runtime);
                if (saved) {
                    showNotification("State saved");
                } else {
                    showNotification("State save failed");
                }
                reportSaveState(
                        "save", saved ? "saved" : "failed", session.module);
                menuState.completeSaveStateAction();
                renderAfterMenuAction = true;
            } else if (requested == SystemMenuState.LOAD_REQUESTED) {
                reportSaveState("load", "before", session.module);
                int loaded =
                        saveState.load(
                                session.cartridgeIdentity,
                                session.cartridgeLength,
                                session.module,
                                session.runtime);
                if (loaded == SingleSaveState.LOAD_OK) {
                    showNotification("State loaded");
                    reportSaveState("load", "loaded", session.module);
                } else if (loaded == SingleSaveState.LOAD_MISSING) {
                    showNotification("Need to save a state first");
                    reportSaveState("load", "missing", session.module);
                } else {
                    showNotification("State load failed");
                    reportSaveState("load", "failed", session.module);
                }
                menuState.completeSaveStateAction();
                renderAfterMenuAction = true;
            }
            if (menuSuspendedAudio) {
                session.audio.resumeOutput();
                menuSuspendedAudio = false;
                clearInput();
            }
            if (renderAfterMenuAction) {
                renderFrame(session.runtime, session.module, false);
            }

            long startedAt = System.currentTimeMillis();
            sampleInput();
            int gamepad = frameGamepad1;
            int gamepad2 = frameGamepad2;
            if (monitor != null) {
                gamepad = monitor.gamepad(frameNumber, gamepad);
                gamepad2 = monitor.gamepad2(frameNumber, gamepad2);
            }
            session.runtime.beginFrame(
                    session.module,
                    gamepad,
                    gamepad2,
                    framePointerX,
                    framePointerY,
                    framePointerButtons);
            if (monitor != null) {
                monitor.onInput(
                        frameNumber,
                        gamepad,
                        gamepad2,
                        touchButtons,
                        framePointerButtons,
                        framePointerX,
                        framePointerY);
            }
            session.interpreter.invoke("update");
            session.runtime.endFrame();
            long updatedAt = System.currentTimeMillis();
            boolean presented =
                    (monitor != null && monitor.renderEveryFrame())
                            || frameNumber % presentationDivisor == 0;
            if (presented) {
                renderFrame(session.runtime, session.module, false);
                windowPresentedFrames++;
            }
            long finishedAt = System.currentTimeMillis();
            long updateElapsed = updatedAt - startedAt;
            long renderElapsed = finishedAt - updatedAt;
            long elapsed = finishedAt - startedAt;
            windowUpdateMillis += updateElapsed;
            windowRenderMillis += renderElapsed;
            if ((monitor == null || !monitor.renderEveryFrame())
                    && frameNumber % 30 == 29) {
                presentationDivisor =
                        adaptPresentationDivisor(
                                presentationDivisor,
                                windowUpdateMillis / 30,
                                windowPresentedFrames == 0
                                        ? 0
                                        : windowRenderMillis / windowPresentedFrames);
                windowUpdateMillis = 0;
                windowRenderMillis = 0;
                windowPresentedFrames = 0;
            }
            if (monitor != null
                    && monitor.resetPresentationAfterFrame(frameNumber)) {
                windowUpdateMillis = 0;
                windowRenderMillis = 0;
                presentationDivisor = 1;
                windowPresentedFrames = 0;
            }
            if (monitor != null) {
                monitor.onFrame(
                        frameNumber,
                        session.runtime,
                        session.module,
                        session.interpreter,
                        updateElapsed,
                        renderElapsed,
                        elapsed,
                        presented,
                        presentationDivisor,
                        bandRenderer);
            }
            frameNumber++;
            long remaining = 16L - elapsed;
            if (remaining > 0) {
                Thread.sleep(remaining);
            }
        }
        return SystemMenuState.RUNNING;
    }

    private int choosePresentationDivisor(long updateMillis, long renderMillis) {
        if (updateMillis + renderMillis <= 17) {
            return 1;
        }
        if (updateMillis >= 48) {
            return 4;
        }
        if (updateMillis >= 32) {
            return 3;
        }
        if (updateMillis >= 16) {
            return 2;
        }
        long budget = 16 - updateMillis;
        long required = (renderMillis + budget - 1) / budget;
        if (required <= 1) {
            return 1;
        }
        if (required <= 2) {
            return 2;
        }
        if (required <= 3) {
            return 3;
        }
        return 4;
    }

    private int adaptPresentationDivisor(
            int currentDivisor, long updateMillis, long renderMillis) {
        int candidate = choosePresentationDivisor(updateMillis, renderMillis);
        if (candidate >= currentDivisor) {
            return candidate;
        }
        if (currentDivisor > 2) {
            return currentDivisor - 1;
        }
        return currentDivisor;
    }

    private boolean isExternalCartridge() {
        return cartridgeResource.charAt(0) != '/' && !CartridgeStore.isLocation(cartridgeResource);
    }

    private String cartridgeSource() {
        if (cartridgeResource.charAt(0) == '/') {
            return "bundled";
        }
        if (CartridgeStore.isLocation(cartridgeResource)) {
            return "installed";
        }
        return "external";
    }

    private void requestMenu() {
        if (menuState.requestMenu()) {
            clearInput();
        }
    }

    boolean continueFromSystemMenu() {
        boolean accepted = menuState.requestContinue();
        if (accepted) {
            clearInput();
        }
        return accepted;
    }

    boolean restartFromSystemMenu() {
        clearInput();
        boolean accepted = menuState.requestRestart();
        if (accepted) {
            saveState.clear();
        }
        return accepted;
    }

    void exitFromSystemMenu() {
        clearInput();
        if (menuState.requestLeave()) {
            saveState.clear();
        }
    }

    boolean saveFromSystemMenu() {
        clearInput();
        return menuState.requestSave();
    }

    boolean loadFromSystemMenu() {
        clearInput();
        return menuState.requestLoad();
    }

    boolean isSystemMenuOpen() {
        return menuState.isMenuOpen();
    }

    private void showNotification(String message) {
        notification = message;
        notificationFrames = 90;
    }

    private void reportSaveState(
            String operation, String outcome, WasmModule module) {
        if (monitor != null) {
            monitor.onSaveState(operation, outcome, module);
        }
    }

    int audioVolumeCapability() {
        Session session = activeSession;
        return session == null
                ? w4me.runtime.audio.AudioControl.VOLUME_CONTINUOUS
                : session.audio.volumeCapability();
    }

    /**
     * MIDP never promises that a {@code keyPressed} is followed by a matching
     * {@code keyReleased} when the canvas stops being shown, so a button held
     * while the settings form or the library takes over would stay held forever.
     * The MIDP reference implementation clears its own key masks on show for the
     * same reason.
     */
    protected void hideNotify() {
        clearInput();
    }

    protected void showNotify() {
        clearInput();
    }

    void applyAudioSettings(boolean apply, boolean muted, int gain) {
        Session session = activeSession;
        if (session != null) {
            if (apply) {
                session.audio.setMasterGain(gain);
                session.audio.setMuted(muted);
                showNotification("Audio updated");
            }
        }
    }

    protected void keyPressed(int keyCode) {
        if (keyCode == MENU_SOFTKEY) {
            requestMenu();
            return;
        }
        if (menuState.state() != SystemMenuState.RUNNING) {
            return;
        }
        int button = extraButton(keyCode);
        int button2 = gamepad2Button(keyCode);
        synchronized (inputLock) {
            // Latch only a released-to-pressed transition, so key auto-repeat
            // delivered as further keyPressed calls cannot synthesize extra edges.
            pendingExtraButtons |= button & ~extraButtons;
            extraButtons |= button;
            pendingExtraGamepad2 |= button2 & ~extraGamepad2;
            extraGamepad2 |= button2;
        }
    }

    protected void keyReleased(int keyCode) {
        if (menuState.state() != SystemMenuState.RUNNING) {
            return;
        }
        int button = extraButton(keyCode);
        int button2 = gamepad2Button(keyCode);
        synchronized (inputLock) {
            // Release clears the held state only; a latched press stays pending
            // until the next frame consumes it.
            extraButtons &= ~button;
            extraGamepad2 &= ~button2;
        }
    }

    private static int extraButton(int keyCode) {
        if (keyCode == KEY_NUM5 || keyCode == 'x' || keyCode == 'X') {
            return BUTTON_1;
        }
        if (keyCode == KEY_NUM0 || keyCode == 'z' || keyCode == 'Z') {
            return BUTTON_2;
        }
        return 0;
    }

    private static int gamepad2Button(int keyCode) {
        if (keyCode == 'e' || keyCode == 'E') {
            return BUTTON_UP;
        } else if (keyCode == 's' || keyCode == 'S') {
            return BUTTON_LEFT;
        } else if (keyCode == 'd' || keyCode == 'D') {
            return BUTTON_DOWN;
        } else if (keyCode == 'f' || keyCode == 'F') {
            return BUTTON_RIGHT;
        } else if (keyCode == 9) {
            return BUTTON_1;
        } else if (keyCode == 'q' || keyCode == 'Q') {
            return BUTTON_2;
        }
        return 0;
    }

    protected void pointerPressed(int x, int y) {
        if (menuState.state() != SystemMenuState.RUNNING) {
            return;
        }
        touchControlGesture = isTouchControl(y);
        if (touchControlGesture) {
            synchronized (inputLock) {
                pointerButtons = 0;
            }
            updateTouchButtons(x, y);
        } else {
            updatePointer(x, y);
            synchronized (inputLock) {
                if (pointerButtons == 0) {
                    pendingPointerButtons = 1;
                    // Keep the press coordinates for the latched frame: a click
                    // completed between two samples must not report the release
                    // position.
                    pendingPointerX = pointerX;
                    pendingPointerY = pointerY;
                }
                pointerButtons = 1;
            }
        }
    }

    protected void pointerDragged(int x, int y) {
        if (menuState.state() != SystemMenuState.RUNNING) {
            return;
        }
        if (touchControlGesture) {
            updateTouchButtons(x, y);
        } else {
            updatePointer(x, y);
        }
    }

    protected void pointerReleased(int x, int y) {
        if (menuState.state() != SystemMenuState.RUNNING) {
            return;
        }
        if (touchControlGesture) {
            synchronized (inputLock) {
                touchButtons = 0;
            }
            touchControlGesture = false;
        } else {
            updatePointer(x, y);
            synchronized (inputLock) {
                pointerButtons = 0;
            }
        }
    }

    /**
     * Samples every input source exactly once for the frame that is about to run.
     * {@code getKeyStates()} clears its own latch when read, so it must be called
     * here and nowhere else.
     */
    private void sampleInput() {
        int keyState = getKeyStates();
        boolean suppress =
                menuState.suppressNextGameInput
                        && menuState.consumeInputSuppression();
        if (suppress) {
            keyState = 0;
        }
        int keys = 0;
        if ((keyState & LEFT_PRESSED) != 0) {
            keys |= BUTTON_LEFT;
        }
        if ((keyState & RIGHT_PRESSED) != 0) {
            keys |= BUTTON_RIGHT;
        }
        if ((keyState & UP_PRESSED) != 0) {
            keys |= BUTTON_UP;
        }
        if ((keyState & DOWN_PRESSED) != 0) {
            keys |= BUTTON_DOWN;
        }
        if ((keyState & FIRE_PRESSED) != 0) {
            keys |= BUTTON_1;
        }
        synchronized (inputLock) {
            if (suppress) {
                extraButtons = 0;
                pendingExtraButtons = 0;
                extraGamepad2 = 0;
                pendingExtraGamepad2 = 0;
                touchButtons = 0;
                pendingTouchButtons = 0;
                pointerButtons = 0;
                pendingPointerButtons = 0;
            }
            frameGamepad1 =
                    keys
                            | extraButtons
                            | pendingExtraButtons
                            | touchButtons
                            | pendingTouchButtons;
            frameGamepad2 = extraGamepad2 | pendingExtraGamepad2;
            framePointerButtons = pointerButtons | pendingPointerButtons;
            if (pendingPointerButtons != 0) {
                framePointerX = pendingPointerX;
                framePointerY = pendingPointerY;
            } else {
                framePointerX = pointerX;
                framePointerY = pointerY;
            }
            pendingExtraButtons = 0;
            pendingExtraGamepad2 = 0;
            pendingTouchButtons = 0;
            pendingPointerButtons = 0;
        }
    }

    private void clearInput() {
        synchronized (inputLock) {
            extraButtons = 0;
            pendingExtraButtons = 0;
            extraGamepad2 = 0;
            pendingExtraGamepad2 = 0;
            touchButtons = 0;
            pendingTouchButtons = 0;
            pointerButtons = 0;
            pendingPointerButtons = 0;
            frameGamepad1 = 0;
            frameGamepad2 = 0;
            framePointerButtons = 0;
        }
        touchControlGesture = false;
    }

    private void updatePointer(int x, int y) {
        int width = getWidth();
        int gameHeight = gameAreaHeight();
        int side = width < gameHeight ? width : gameHeight;
        int left = (width - side) / 2;
        int top = (gameHeight - side) / 2;
        int localX = x - left;
        int localY = y - top;
        if (localX < 0) {
            localX = 0;
        } else if (localX >= side) {
            localX = side - 1;
        }
        if (localY < 0) {
            localY = 0;
        } else if (localY >= side) {
            localY = side - 1;
        }
        pointerX = localX * Wasm4Runtime.WIDTH / side;
        pointerY = localY * Wasm4Runtime.HEIGHT / side;
    }

    private boolean isTouchControl(int y) {
        return hasPointerEvents() && y >= getHeight() - touchControlHeight();
    }

    private void updateTouchButtons(int x, int y) {
        int next = touchButtonAt(x, y);
        synchronized (inputLock) {
            // Dragging across the pad from one cell to another is a new press for
            // the cell being entered, so it latches like a fresh tap.
            pendingTouchButtons |= next & ~touchButtons;
            touchButtons = next;
        }
    }

    /**
     * MIDP 2.0 delivers a single pointer, so at most one on-screen cell can be
     * held at a time. Hardware keys remain the multi-button path.
     */
    private int touchButtonAt(int x, int y) {
        int width = getWidth();
        int top = getHeight() - touchControlHeight();
        int leftHalf = width / 2;
        if (x < 0 || x >= width || y < top || y >= getHeight()) {
            return 0;
        }
        if (x >= leftHalf) {
            return x < leftHalf + (width - leftHalf) / 2 ? BUTTON_2 : BUTTON_1;
        }
        int third = leftHalf / 3;
        if (x < third) {
            return BUTTON_LEFT;
        } else if (x >= leftHalf - third) {
            return BUTTON_RIGHT;
        } else if (y < top + touchControlHeight() / 2) {
            return BUTTON_UP;
        }
        return BUTTON_DOWN;
    }

    private int touchControlHeight() {
        int height = getHeight() / 5;
        if (height < 40) {
            return 40;
        }
        if (height > 64) {
            return 64;
        }
        return height;
    }

    private int gameAreaHeight() {
        int height = getHeight();
        if (hasPointerEvents()) {
            height -= touchControlHeight();
        }
        return height > 0 ? height : 1;
    }

    private void renderFrame(
            Wasm4Runtime runtime, WasmModule module, boolean pausedOverlay) {
        int width = getWidth();
        int height = getHeight();
        int gameHeight = gameAreaHeight();
        int side = width < gameHeight ? width : gameHeight;
        int left = (width - side) / 2;
        int top = (gameHeight - side) / 2;
        if (side != renderSide) {
            if (monitor != null) {
                int controlHeight =
                        hasPointerEvents() ? touchControlHeight() : 0;
                monitor.onLayout(
                        width,
                        height,
                        left,
                        top,
                        side,
                        height - controlHeight,
                        controlHeight);
            }
            renderSide = side;
            if (side == Wasm4Runtime.WIDTH) {
                xMap = null;
                yMap = null;
            } else {
                xMap = new int[side];
                yMap = new int[side];
                int index;
                for (index = 0; index < side; index++) {
                    int source =
                            index * Wasm4Runtime.WIDTH / side;
                    xMap[index] =
                            (source >> 2)
                                    | ((source & 3) << 8);
                    yMap[index] =
                            source
                                    * (Wasm4Runtime.WIDTH >> 2);
                }
            }
            int fullPixels = side * side;
            long fullBytes = (long) fullPixels * 4L;
            bandRenderer = Runtime.getRuntime().freeMemory() < fullBytes + 262144L;
            if (!bandRenderer) {
                try {
                    bandPixels = new int[fullPixels];
                } catch (OutOfMemoryError unavailable) {
                    bandRenderer = true;
                }
            }
            if (bandRenderer) {
                bandPixels = new int[side * BAND_HEIGHT];
            }
        }
        Graphics graphics = getGraphics();
        graphics.setColor(0);
        if (left > 0) {
            graphics.fillRect(0, 0, left, height);
            graphics.fillRect(left + side, 0, width - left - side, height);
        }
        if (top > 0) {
            graphics.fillRect(0, 0, width, top);
            graphics.fillRect(0, top + side, width, height - top - side);
        }
        runtime.prepareArgb(module);
        int rowsPerBand = bandRenderer ? BAND_HEIGHT : side;
        int firstRow;
        for (firstRow = 0; firstRow < side; firstRow += rowsPerBand) {
            int rowCount = side - firstRow;
            if (rowCount > rowsPerBand) {
                rowCount = rowsPerBand;
            }
            if (side == Wasm4Runtime.WIDTH) {
                runtime.copyNativeArgbBand(
                        module,
                        bandPixels,
                        firstRow,
                        rowCount);
            } else if (side == 240) {
                runtime.copyArgb240Band(
                        module,
                        bandPixels,
                        yMap,
                        firstRow,
                        rowCount);
            } else if (side > Wasm4Runtime.WIDTH) {
                runtime.copyUpscaledArgbBand(
                        module,
                        bandPixels,
                        side,
                        xMap,
                        yMap,
                        firstRow,
                        rowCount);
            } else {
                runtime.copyArgbBand(
                        module,
                        bandPixels,
                        side,
                        xMap,
                        yMap,
                        firstRow,
                        rowCount);
            }
            graphics.drawRGB(
                    bandPixels,
                    0,
                    side,
                    left,
                    top + firstRow,
                    side,
                    rowCount,
                    false);
        }
        drawTouchControls(graphics);
        if (pausedOverlay) {
            drawPausedOverlay(graphics);
        } else {
            drawNotification(graphics);
        }
        flushGraphics();
    }

    private void drawPausedOverlay(Graphics graphics) {
        int width = getWidth();
        int height = getHeight();
        int y;
        graphics.setColor(0x000000);
        for (y = 0; y < height; y += 2) {
            graphics.fillRect(0, y, width, 1);
        }

        int fontHeight = graphics.getFont().getHeight();
        int panelHeight = fontHeight + 12;
        int panelTop = (height - panelHeight) / 2;
        int panelLeft = width / 12;
        if (panelLeft < 4) {
            panelLeft = 4;
        }
        int panelWidth = width - panelLeft * 2;

        graphics.setColor(0x071821);
        graphics.fillRect(panelLeft, panelTop, panelWidth, panelHeight);
        graphics.setColor(0x86c06c);
        graphics.drawRect(panelLeft, panelTop, panelWidth - 1, panelHeight - 1);
        graphics.setColor(0xe0f8cf);
        graphics.drawString(
                "Paused",
                panelLeft + panelWidth / 2,
                panelTop + 6,
                Graphics.HCENTER | Graphics.TOP);
    }

    private void drawNotification(Graphics graphics) {
        if (notification == null || notificationFrames <= 0) {
            return;
        }
        int labelWidth = graphics.getFont().stringWidth(notification);
        graphics.setColor(0x071821);
        graphics.fillRect(2, 2, labelWidth + 6, graphics.getFont().getHeight() + 4);
        graphics.setColor(0xe0f8cf);
        graphics.drawString(notification, 5, 4, Graphics.LEFT | Graphics.TOP);
        notificationFrames--;
        if (notificationFrames == 0) {
            notification = null;
        }
    }

    private void drawTouchControls(Graphics graphics) {
        if (!hasPointerEvents()) {
            return;
        }
        int width = getWidth();
        int height = getHeight();
        int controlHeight = touchControlHeight();
        int top = height - controlHeight;
        int leftHalf = width / 2;
        int third = leftHalf / 3;
        int middleWidth = leftHalf - third * 2;
        int rightHalf = width - leftHalf;
        int buttonWidth = rightHalf / 2;
        int halfHeight = controlHeight / 2;

        graphics.setColor(0x071821);
        graphics.fillRect(0, top, width, controlHeight);
        drawTouchCell(
                graphics, 0, top, third, controlHeight, "<", BUTTON_LEFT);
        drawTouchCell(
                graphics, third, top, middleWidth, halfHeight, "^", BUTTON_UP);
        drawTouchCell(
                graphics,
                third,
                top + halfHeight,
                middleWidth,
                controlHeight - halfHeight,
                "v",
                BUTTON_DOWN);
        drawTouchCell(
                graphics,
                third + middleWidth,
                top,
                third,
                controlHeight,
                ">",
                BUTTON_RIGHT);
        drawTouchCell(
                graphics, leftHalf, top, buttonWidth, controlHeight, "B", BUTTON_2);
        drawTouchCell(
                graphics,
                leftHalf + buttonWidth,
                top,
                width - leftHalf - buttonWidth,
                controlHeight,
                "A",
                BUTTON_1);
    }

    private void drawTouchCell(
            Graphics graphics,
            int x,
            int y,
            int width,
            int height,
            String label,
            int button) {
        if ((touchButtons & button) != 0) {
            graphics.setColor(0x306850);
            graphics.fillRect(x + 1, y + 1, width - 2, height - 2);
        }
        graphics.setColor(0x86c06c);
        graphics.drawRect(x, y, width - 1, height - 1);
        graphics.setColor(0xe0f8cf);
        graphics.drawString(
                label,
                x + width / 2,
                y + (height - graphics.getFont().getHeight()) / 2,
                Graphics.HCENTER | Graphics.TOP);
    }

    private void renderStatus() {
        Graphics graphics = getGraphics();
        graphics.setColor(0);
        graphics.fillRect(0, 0, getWidth(), getHeight());
        graphics.setColor(0xffffff);
        graphics.drawString(status == null ? "Starting..." : status, 2, 2, Graphics.TOP | Graphics.LEFT);
        flushGraphics();
    }

    public void paint(Graphics graphics) {
        if (status != null) {
            renderStatus();
        } else if (menuState.isMenuOpen()) {
            drawPausedOverlay(graphics);
        }
    }

    private static final class Session {
        final Wasm4Runtime runtime;
        final WasmModule module;
        final WasmInterpreter interpreter;
        final Wasm4Apu audio;
        final int cartridgeIdentity;
        final int cartridgeLength;
        private boolean closed;

        Session(
                Wasm4Runtime runtime,
                WasmModule module,
                WasmInterpreter interpreter,
                Wasm4Apu audio,
                int cartridgeIdentity,
                int cartridgeLength) {
            this.runtime = runtime;
            this.module = module;
            this.interpreter = interpreter;
            this.audio = audio;
            this.cartridgeIdentity = cartridgeIdentity;
            this.cartridgeLength = cartridgeLength;
        }

        synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            runtime.close();
            module.close();
        }
    }

}

package w4me.midp;

/**
 * Worker/UI handshake for the in-game menu.
 *
 * <p>The UI thread only records requests. The worker accepts MENU_REQUESTED
 * after the current frame and owns restart, leave, and resource teardown.
 */
final class SystemMenuState {
    static final int RUNNING = 0;
    static final int MENU_REQUESTED = 1;
    static final int MENU_OPEN = 2;
    static final int RESTART_REQUESTED = 3;
    static final int LEAVE_REQUESTED = 4;

    volatile int state = RUNNING;
    volatile boolean stopped;
    volatile boolean suppressNextGameInput;

    synchronized boolean requestMenu() {
        if (stopped || state != RUNNING) {
            return false;
        }
        state = MENU_REQUESTED;
        notifyAll();
        return true;
    }

    boolean acceptMenuAtFrameBoundary() {
        if (stopped || state != MENU_REQUESTED) {
            return false;
        }
        synchronized (this) {
            if (stopped || state != MENU_REQUESTED) {
                return false;
            }
            state = MENU_OPEN;
            notifyAll();
            return true;
        }
    }

    synchronized boolean requestContinue() {
        if (stopped || state != MENU_OPEN) {
            return false;
        }
        state = RUNNING;
        suppressNextGameInput = true;
        notifyAll();
        return true;
    }

    synchronized boolean requestRestart() {
        if (stopped || state != MENU_OPEN) {
            return false;
        }
        state = RESTART_REQUESTED;
        notifyAll();
        return true;
    }

    synchronized boolean requestLeave() {
        if (stopped || state != MENU_OPEN) {
            return false;
        }
        state = LEAVE_REQUESTED;
        notifyAll();
        return true;
    }

    synchronized void completeRestart() {
        if (!stopped && state == RESTART_REQUESTED) {
            state = RUNNING;
            suppressNextGameInput = true;
            notifyAll();
        }
    }

    boolean consumeInputSuppression() {
        if (!suppressNextGameInput) {
            return false;
        }
        synchronized (this) {
            boolean suppress = suppressNextGameInput;
            suppressNextGameInput = false;
            return suppress;
        }
    }

    boolean isMenuOpen() {
        return !stopped && state == MENU_OPEN;
    }

    boolean isStopped() {
        return stopped;
    }

    int state() {
        return state;
    }

    synchronized void awaitChange() throws InterruptedException {
        if (!stopped && state == MENU_OPEN) {
            wait();
        }
    }

    synchronized void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        notifyAll();
    }
}

package w4me.midp;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Ticker;

/** Native LCDUI cartridge library. */
final class LibraryList extends List implements CommandListener {
    // Order is deliberate. The first entries are the ones a person tries first, so
    // they are the cartridges whose frame cost is low enough that the handset
    // limitation is not visible. Runtime-coverage cartridges follow, pointer-driven
    // ones after the keypad-driven ones, and the service and technical demos last.
    // Plasma Cube is the most expensive cartridge in the set and must stay last.
    private static final String[] BUNDLED_TITLES = {
        "Sokoban",
        "Wasm Wars",
        "Annoying Robots",
        "Waternet",
        "Dragon Poker Draw",
        "Tic Tac Toe",
        "Watris",
        "Glowfish Chess",
        "Duck Maze",
        "Untangle",
        "Nyan Cat",
        "Sound Demo",
        "Plasma Cube"
    };
    private static final String[] BUNDLED_RESOURCES = {
        "/cartridges/sokoban.wasm",
        "/cartridges/wasm-wars.wasm",
        "/cartridges/annoyingrobots.wasm",
        "/cartridges/waternet.wasm",
        "/cartridges/dragon-poker-draw.wasm",
        "/cartridges/tictactoe.wasm",
        "/cartridges/watris.wasm",
        "/cartridges/glowfish-chess.wasm",
        "/cartridges/duck-maze.wasm",
        "/cartridges/untangle.wasm",
        "/cartridges/nyancat.wasm",
        "/cartridges/sound-demo.wasm",
        "/cartridges/plasma-cube.wasm"
    };

    private final W4MeMidlet midlet;
    private final Command runCommand = new Command("Run", Command.ITEM, 1);
    private final Command installCommand =
            new Command(
                    FileSystemAccessFactory.isAvailable()
                            ? "Choose .wasm file"
                            : "Install .wasm",
                    Command.SCREEN,
                    1);
    private final Command settingsCommand =
            new Command("Settings", Command.SCREEN, 2);
    private final Command exitCommand = new Command("Exit", Command.EXIT, 1);
    private CartridgeStore.CartridgeInfo[] installed = new CartridgeStore.CartridgeInfo[0];

    LibraryList(W4MeMidlet midlet) {
        super("W4ME Station", List.IMPLICIT);
        this.midlet = midlet;
        setSelectCommand(runCommand);
        addCommand(installCommand);
        addCommand(settingsCommand);
        addCommand(exitCommand);
        setCommandListener(this);
        reloadInstalled();
    }

    void reloadInstalled() {
        int previous = size() == 0 ? 0 : getSelectedIndex();
        CartridgeStore store = null;
        try {
            store = CartridgeStore.open();
            installed = store.list();
            setTicker(null);
        } catch (Throwable failure) {
            installed = new CartridgeStore.CartridgeInfo[0];
            setTicker(new Ticker("RMS library unavailable"));
        } finally {
            if (store != null) {
                store.close();
            }
        }

        deleteAll();
        int index;
        for (index = 0; index < BUNDLED_TITLES.length; index++) {
            append(BUNDLED_TITLES[index], null);
        }
        for (index = 0; index < installed.length; index++) {
            append(installed[index].title, null);
        }
        int count = size();
        if (count != 0) {
            if (previous >= count) {
                previous = count - 1;
            }
            setSelectedIndex(previous, true);
        }
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == runCommand || command == List.SELECT_COMMAND) {
            openSelected();
        } else if (command == installCommand) {
            midlet.showInstallOptions();
        } else if (command == settingsCommand) {
            midlet.showSettings(null);
        } else if (command == exitCommand) {
            midlet.exit();
        }
    }

    private void openSelected() {
        int selected = getSelectedIndex();
        if (selected >= 0 && selected < size()) {
            midlet.openCartridge(resourceAt(selected), titleAt(selected));
        }
    }

    private boolean isBundled(int index) {
        return index < BUNDLED_TITLES.length;
    }

    private String titleAt(int index) {
        if (isBundled(index)) {
            return BUNDLED_TITLES[index];
        }
        return installed[index - BUNDLED_TITLES.length].title;
    }

    private String resourceAt(int index) {
        if (isBundled(index)) {
            return BUNDLED_RESOURCES[index];
        }
        return CartridgeStore.location(installed[index - BUNDLED_TITLES.length].recordId);
    }
}

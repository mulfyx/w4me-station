package w4me.midp;

import java.io.IOException;
import java.util.Vector;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/**
 * Native LCDUI file picker. The platform owns navigation, scrolling, and the selection highlight, so this class only
 * maps a {@link FilePage} onto list items and turns a selection back into a directory descent or a file install.
 */
final class FileBrowserList extends List implements CommandListener {
    private static final int PAGE_SIZE = 48;
    private static final String PARENT_ROW = "[..]";
    private static final String EMPTY_ROW = "(No .wasm files)";

    private final W4MeMidlet midlet;
    private final FileSystemAccess access;
    private final Vector directories = new Vector();
    private final Vector directoryNames = new Vector();
    private final Vector pageKeys = new Vector();
    private final Command openCommand = new Command("Open", Command.ITEM, 1);
    private final Command backCommand = new Command("Back", Command.BACK, 1);
    private final Command manualCommand = new Command("Enter manually", Command.SCREEN, 2);
    private final Command nextCommand = new Command("Next", Command.SCREEN, 3);
    private final Command previousCommand = new Command("Previous", Command.SCREEN, 4);
    private FilePage page;
    private boolean showingNext;
    private boolean showingPrevious;

    FileBrowserList(W4MeMidlet midlet, FileSystemAccess access) {
        super("Choose .wasm file", List.IMPLICIT);
        this.midlet = midlet;
        this.access = access;
        setSelectCommand(openCommand);
        addCommand(backCommand);
        addCommand(manualCommand);
        setCommandListener(this);
    }

    void show() {
        loadPage();
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == openCommand || command == List.SELECT_COMMAND) {
            openSelected();
        } else if (command == nextCommand && page != null && page.hasMore) {
            pageKeys.addElement(page.nextKey);
            loadPage();
        } else if (command == previousCommand && !pageKeys.isEmpty()) {
            pageKeys.removeElementAt(pageKeys.size() - 1);
            loadPage();
        } else if (command == manualCommand) {
            midlet.showLocationEntry();
        } else if (command == backCommand) {
            goBack();
        }
    }

    private void loadPage() {
        try {
            String key = pageKeys.isEmpty() ? null : (String) pageKeys.elementAt(pageKeys.size() - 1);
            if (directories.isEmpty()) {
                page = access.listRoots(key, PAGE_SIZE);
                setTitle("Choose .wasm file");
            } else {
                page = access.list((String) directories.elementAt(directories.size() - 1), key, PAGE_SIZE);
                setTitle((String) directoryNames.elementAt(directoryNames.size() - 1));
            }
            rebuildRows();
            updateCommands();
            Display.getDisplay(midlet).setCurrent(this);
        } catch (SecurityException denied) {
            showFailure("File access denied", denied);
        } catch (IOException failure) {
            showFailure("Cannot open files", failure);
        } catch (RuntimeException failure) { // NOPMD -- Java 1.3 lacks multi-catch.
            showFailure("Cannot open files", failure);
        }
    }

    private void rebuildRows() {
        deleteAll();
        if (hasParentRow()) {
            append(PARENT_ROW, null);
        }
        if (page.entries.length == 0) {
            append(EMPTY_ROW, null);
        } else {
            int index;
            for (index = 0; index < page.entries.length; index++) {
                FileEntry entry = page.entries[index];
                append((entry.directory ? "[dir] " : "") + entry.name, null);
            }
        }
        if (size() != 0) {
            setSelectedIndex(0, true);
        }
    }

    private void updateCommands() {
        if (page.hasMore && !showingNext) {
            addCommand(nextCommand);
            showingNext = true;
        } else if (!page.hasMore && showingNext) {
            removeCommand(nextCommand);
            showingNext = false;
        }
        boolean hasPrevious = !pageKeys.isEmpty();
        if (hasPrevious && !showingPrevious) {
            addCommand(previousCommand);
            showingPrevious = true;
        } else if (!hasPrevious && showingPrevious) {
            removeCommand(previousCommand);
            showingPrevious = false;
        }
    }

    private void openSelected() {
        if (page == null) {
            return;
        }
        int selected = getSelectedIndex();
        if (selected < 0) {
            return;
        }
        if (hasParentRow()) {
            if (selected == 0) {
                goParent();
                return;
            }
            selected--;
        }
        if (page.entries.length == 0) {
            return;
        }
        if (selected < 0 || selected >= page.entries.length) {
            return;
        }
        FileEntry entry = page.entries[selected];
        if (entry.directory) {
            directories.addElement(entry.url);
            directoryNames.addElement(entry.name);
            pageKeys.removeAllElements();
            loadPage();
            return;
        }
        try {
            midlet.showFileSelection(this, access.inspect(entry.url));
        } catch (SecurityException denied) {
            showFailure("File access denied", denied);
        } catch (IOException failure) {
            showFailure("Cannot read file", failure);
        } catch (RuntimeException failure) { // NOPMD -- Java 1.3 lacks multi-catch.
            showFailure("Cannot read file", failure);
        }
    }

    private void goBack() {
        if (!pageKeys.isEmpty()) {
            pageKeys.removeAllElements();
            loadPage();
        } else if (!directories.isEmpty()) {
            goParent();
        } else {
            midlet.showLibrary();
        }
    }

    private void goParent() {
        if (!directories.isEmpty()) {
            directories.removeElementAt(directories.size() - 1);
            directoryNames.removeElementAt(directoryNames.size() - 1);
        }
        pageKeys.removeAllElements();
        loadPage();
    }

    private boolean hasParentRow() {
        return !directories.isEmpty() && pageKeys.isEmpty();
    }

    private void showFailure(String alertTitle, Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.length() == 0) {
            message = failure.toString();
        }
        Alert alert = new Alert(alertTitle, message, null, AlertType.ERROR);
        alert.setTimeout(Alert.FOREVER);
        alert.addCommand(backCommand);
        alert.addCommand(manualCommand);
        alert.setCommandListener(this);
        Display.getDisplay(midlet).setCurrent(alert, this);
    }
}

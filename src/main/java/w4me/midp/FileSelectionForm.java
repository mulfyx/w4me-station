package w4me.midp;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;

final class FileSelectionForm extends Form implements CommandListener {
    private final W4MeMidlet midlet;
    private final FileBrowserList browser;
    private final FileSelection selection;
    private final Command installCommand = new Command("Install", Command.OK, 1);
    private final Command backCommand = new Command("Back", Command.BACK, 2);
    private final Command manualCommand = new Command("Enter manually", Command.SCREEN, 3);

    FileSelectionForm(W4MeMidlet midlet, FileBrowserList browser, FileSelection selection) {
        super("Install .wasm");
        this.midlet = midlet;
        this.browser = browser;
        this.selection = selection;
        append("File: " + selection.name);
        append("\nSize: " + sizeText(selection.size));
        append("\n\nReady to read, validate, and install.");
        addCommand(installCommand);
        addCommand(backCommand);
        addCommand(manualCommand);
        setCommandListener(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == installCommand) {
            midlet.openCartridge(selection.url, title(selection.name));
        } else if (command == manualCommand) {
            midlet.showLocationEntry();
        } else {
            Display.getDisplay(midlet).setCurrent(browser);
        }
    }

    private static String sizeText(long size) {
        return size < 0 ? "unknown" : Long.toString(size) + " bytes";
    }

    private static String title(String name) {
        if (FilePageBuilder.endsWithIgnoreCase(name, ".wasm")) {
            name = name.substring(0, name.length() - 5);
        }
        return name.length() == 0 ? "External cartridge" : name;
    }
}

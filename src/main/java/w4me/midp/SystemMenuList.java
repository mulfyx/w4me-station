package w4me.midp;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/** Native LCDUI action list for a worker-paused cartridge session. */
final class SystemMenuList extends List implements CommandListener {
    private final W4MeMidlet midlet;
    private final W4Canvas source;
    private final SaveStateMenuActions saveStateActions;
    private final SystemMenuModel model;
    private final Command selectCommand = new Command("Select", Command.ITEM, 1);
    private final Command continueCommand = new Command("Continue", Command.BACK, 1);

    SystemMenuList(
            W4MeMidlet midlet,
            W4Canvas source,
            SaveStateMenuActions saveStateActions,
            int initialAction) {
        super("Paused", List.IMPLICIT);
        this.midlet = midlet;
        this.source = source;
        this.saveStateActions = saveStateActions;
        model = new SystemMenuModel(saveStateActions != null);
        int index;
        for (index = 0; index < model.size(); index++) {
            append(model.labelAt(index), null);
        }
        int initialIndex = model.indexOfAction(initialAction);
        if (initialIndex >= 0) {
            setSelectedIndex(initialIndex, true);
        }
        setSelectCommand(selectCommand);
        addCommand(continueCommand);
        setCommandListener(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == continueCommand) {
            midlet.continueFromSystemMenu(this, source);
            return;
        }
        if (command != selectCommand && command != List.SELECT_COMMAND) {
            return;
        }
        int selected = getSelectedIndex();
        if (selected < 0 || selected >= model.size()) {
            return;
        }
        int action = model.actionAt(selected);
        if (action == SystemMenuModel.ACTION_CONTINUE) {
            midlet.continueFromSystemMenu(this, source);
        } else if (action == SystemMenuModel.ACTION_SAVE_STATE) {
            saveStateActions.saveState(source);
        } else if (action == SystemMenuModel.ACTION_LOAD_STATE) {
            saveStateActions.loadState(source);
        } else if (action == SystemMenuModel.ACTION_SETTINGS) {
            midlet.showSettings(source, this);
        } else if (action == SystemMenuModel.ACTION_RESTART) {
            midlet.restartFromSystemMenu(this, source);
        } else if (action == SystemMenuModel.ACTION_EXIT) {
            source.exitFromSystemMenu();
        }
    }
}

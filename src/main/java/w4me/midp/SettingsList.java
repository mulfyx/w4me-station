package w4me.midp;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;

/** Native category hub shared by library-origin and paused-game Settings. */
final class SettingsList extends List implements CommandListener {
    private final W4MeMidlet midlet;
    private final W4Canvas source;
    private final SystemMenuList systemMenu;
    private final SettingsMenuModel model;
    private final Command openCommand = new Command("Open", Command.ITEM, 1);
    private final Command backCommand = new Command("Back", Command.BACK, 1);

    SettingsList(W4MeMidlet midlet, W4Canvas source, SystemMenuList systemMenu, SettingsMenuModel model) {
        super("Settings", List.IMPLICIT);
        this.midlet = midlet;
        this.source = source;
        this.systemMenu = systemMenu;
        this.model = model;
        int index;
        for (index = 0; index < model.size(); index++) {
            append(model.labelAt(index), null);
        }
        setSelectCommand(openCommand);
        addCommand(backCommand);
        setCommandListener(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == openCommand || command == List.SELECT_COMMAND) {
            int selected = getSelectedIndex();
            if (selected >= 0 && selected < model.size()) {
                midlet.showSettingsCategory(this, source, model.categoryAt(selected));
            }
        } else if (command == backCommand) {
            midlet.finishSettings(this, source, systemMenu);
        }
    }
}

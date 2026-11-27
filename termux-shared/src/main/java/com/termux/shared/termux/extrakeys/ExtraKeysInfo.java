package com.termux.shared.termux.extrakeys;

import java.util.List;

import static com.termux.shared.termux.extrakeys.ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS;

/** Converts validated TOML rows into terminal extra-key buttons. */
public final class ExtraKeysInfo {
    private final ExtraKeyButton[][] buttons;

    public ExtraKeysInfo(List<List<Object>> rows, String style, ExtraKeysConstants.ExtraKeyDisplayMap aliases) {
        ExtraKeysConstants.ExtraKeyDisplayMap labels;
        switch (style) {
            case "arrows-only": labels = EXTRA_KEY_DISPLAY_MAPS.ARROWS_ONLY_CHAR_DISPLAY; break;
            case "arrows-all": labels = EXTRA_KEY_DISPLAY_MAPS.LOTS_OF_ARROWS_CHAR_DISPLAY; break;
            case "all": labels = EXTRA_KEY_DISPLAY_MAPS.FULL_ISO_CHAR_DISPLAY; break;
            case "none": labels = new ExtraKeysConstants.ExtraKeyDisplayMap(); break;
            default: labels = EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY; break;
        }
        buttons = new ExtraKeyButton[rows.size()][];
        for (int row = 0; row < rows.size(); row++) {
            List<Object> entries = rows.get(row);
            buttons[row] = new ExtraKeyButton[entries.size()];
            for (int column = 0; column < entries.size(); column++) {
                buttons[row][column] = new ExtraKeyButton(entries.get(column), labels, aliases);
            }
        }
    }

    public ExtraKeyButton[][] getMatrix() {
        return buttons;
    }
}

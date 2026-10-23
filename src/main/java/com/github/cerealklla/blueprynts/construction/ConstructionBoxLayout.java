package com.github.cerealklla.blueprynts.construction;

/**
 * The Construction Box screen's shared layout geometry -- {@link ConstructionBoxMenu} (real slot
 * positions) and {@code client.ConstructionBoxScreen} (background art, text, the requirements icon
 * grid) both need the *exact* same numbers, and every time they instead kept two separately-edited
 * copies of these constants, the two drifted out of sync and broke the screen live -- three times on
 * this same screen in one day (2026-10-22): a stale `PLAYER_INV_Y`, a stale background-blit V
 * coordinate, and finally `REQUIREMENTS_VIEWPORT_HEIGHT` left at its old value in one file after the
 * icon-grid rework changed it in the other. One shared source of truth instead of two copies that can
 * drift is the actual fix, not a fourth round of re-syncing two numbers by hand.
 */
public final class ConstructionBoxLayout {

    private ConstructionBoxLayout() {
    }

    public static final int STATUS_Y = 24;
    public static final int MANAGE_BUTTON_OFFSET = 14;
    public static final int BUTTON_HEIGHT = 20;
    public static final int MANAGE_BUTTON_BOTTOM = STATUS_Y + MANAGE_BUTTON_OFFSET + BUTTON_HEIGHT;
    public static final int PANEL_MARGIN = 8;
    public static final int NEXT_NEEDED_Y = MANAGE_BUTTON_BOTTOM + PANEL_MARGIN;
    public static final int MISSING_ITEM_LINE_HEIGHT = 10; // "Next Needed" header line only
    public static final int REQUIREMENTS_VIEWPORT_Y = NEXT_NEEDED_Y + MISSING_ITEM_LINE_HEIGHT + 4;

    // Requirements icon grid, 2026-10-22 -- CELL_HEIGHT matches a vanilla slot's own 18px pitch
    // (16px icon + 2px breathing room).
    //
    // Real bug, found live immediately after this shipped: 3 columns gave each cell only ~53px,
    // which overlapped the next cell's icon for any real 3-digit/3-digit pair ("301/301" rendered as
    // "301/30" with the next icon drawn over the cut-off digit). 2 columns (80px/cell) comfortably
    // fits even a 4-digit/4-digit pair ("9999/9999"), which a large blueprint's real totals can
    // plausibly reach -- scrolling (already built) absorbs the extra rows this costs, so there's no
    // real downside to sizing for the worst case here.
    public static final int ICON_SIZE = 16;
    public static final int GRID_COLUMNS = 2;
    public static final int CELL_HEIGHT = 18;
    public static final int REQUIREMENTS_VIEWPORT_HEIGHT = CELL_HEIGHT * 3;

    public static final int PLAYER_INV_Y = REQUIREMENTS_VIEWPORT_Y + REQUIREMENTS_VIEWPORT_HEIGHT + PANEL_MARGIN;
}

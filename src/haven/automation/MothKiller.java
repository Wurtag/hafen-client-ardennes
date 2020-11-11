package haven.automation;


import haven.Coord;
import haven.GameUI;
import haven.Inventory;
import haven.WItem;
import haven.Widget;
import haven.purus.pbot.PBotUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class MothKiller implements Runnable {
    private GameUI gui;

    public MothKiller(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        WItem moth = null;
        List<WItem> cocoons = new ArrayList<>();
        List<WItem> deadheads = new ArrayList<>();
        synchronized (gui.ui.root.lchild) {
            try {
                for (Widget q = gui.ui.root.lchild; q != null; q = q.rnext()) {
                    if (q instanceof Inventory) {
                        cocoons.addAll(getcocoons((Inventory) q));
                        deadheads.addAll(getdeadheads((Inventory) q));
                    }

                }

            } catch (NullPointerException q) {
            }
        }

        //  trays2.addAll(trays);
        if (cocoons.size() > 0 || deadheads.size() > 0)
            PBotUtils.sysMsg(gui.ui, "Found " + (cocoons.size() + deadheads.size()) + " to kill.", Color.white);
        else {
            PBotUtils.sysMsg(gui.ui, "No cocoons found", Color.white);
            return;
        }
        int startid = gui.ui.next_predicted_id;
        int iteration = 0;
        if (cocoons.size() > 0) {
            for (WItem item : cocoons) {
                item.item.wdgmsg("iact", Coord.z, -1);
                gui.ui.wdgmsg(startid + iteration, "cl", 0, 0);
                iteration++;
            }
        }
        if (deadheads.size() > 0) {
            for (WItem item : deadheads) {
                item.item.wdgmsg("iact", Coord.z, -1);
                gui.ui.wdgmsg(startid + iteration, "cl", 1, 0);
                iteration++;
            }
        }
        PBotUtils.sysMsg(gui.ui, "Done", Color.white);
    }

    private List<WItem> getcocoons(Inventory inv) {
        return inv.getItemsPartial("Cocoon");
    }

    private List<WItem> getdeadheads(Inventory inv) {
        return inv.getItemsPartial("Chrysalis");
    }
}


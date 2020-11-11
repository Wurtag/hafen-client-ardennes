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

public class FillCheeseTray implements Runnable {
    private GameUI gui;
    WItem tray = null;
    List<WItem> trays = new ArrayList<>();
    List<WItem> trays2 = new ArrayList<>();
    private static final int TIMEOUT = 2000;

    public FillCheeseTray(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        synchronized (gui.ui.root.lchild) {
            try {
                for (Widget q = gui.ui.root.lchild; q != null; q = q.rnext()) {
                    if (q instanceof Inventory) {
                        tray = getTrays2((Inventory) q);
                        if (tray != null) {
                            trays = getTrays((Inventory) q);
                            System.out.println("trays size : " + trays.size());
                        }
                    }
                }
                for (WItem item : trays) {
                    if (item.item.getcontents() != null)
                        System.out.println("contents not null");
                    else
                        System.out.println("contents null");
                    if (item.item.getcontents() == null)
                        trays2.add(item);
                    else if (item.item.getcontents().iscurds)
                        trays2.add(item);
                }
            } catch (NullPointerException q) {
                q.printStackTrace();
            }
        }
        if (trays2.size() == 0) {
            PBotUtils.sysMsg(gui.ui, "No trays with space found, not running.", Color.white);
            return;
        }
        if (PBotUtils.getItemAtHand(gui.ui) == null) {
            WItem curd = gui.maininv.getItemPartial("Curd");
            try {
                PBotUtils.takeItem(gui.ui, curd.item);
            } catch (NullPointerException q) {
                PBotUtils.sysMsg(gui.ui, "Don't appear to have curds, stopping.", Color.white);
                return;
            }
            PBotUtils.sleep(250);
        }
        System.out.println("Number of Cheese trays found is : " + trays2.size());
        for (int i = 0; i < trays2.size(); i++) {
            if (gui.maininv.getItemPartial("Curd") == null)
                break;
            System.out.println("Tray number " + i);
            for (int l = 0; l < 5; l++) {
                if (gui.maininv.getItemPartial("Curd") == null)
                    break;
                trays2.get(i).item.wdgmsg("itemact", 1);
                PBotUtils.sleep(50);
            }
        }
        Coord slot = PBotUtils.getFreeInvSlot(gui.maininv);
        if (PBotUtils.getItemAtHand(gui.ui) != null)
            PBotUtils.dropItemToInventory(slot, gui.maininv);
        PBotUtils.sysMsg(gui.ui, "Done", Color.white);
    }

    private java.util.List<WItem> getTrays(Inventory inv) {
        List<WItem> trays = inv.getItemsPartial("Cheese Tray");
        // BotUtils.sysMsg("trying to find trays", Color.WHITE);
        if (trays == null)
            return null;
        return trays;
    }

    private WItem getTrays2(Inventory inv) {
        WItem trays = inv.getItemPartialTrays("Tray");
        // BotUtils.sysMsg("trying to find trays", Color.WHITE);
        if (trays == null)
            return null;
        return trays;
    }
}

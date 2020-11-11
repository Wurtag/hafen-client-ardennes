package haven.purus;

import haven.Button;
import haven.Coord;
import haven.FlowerMenu;
import haven.GItem;
import haven.Gob;
import haven.IMeter;
import haven.Inventory;
import haven.Label;
import haven.Resource;
import haven.Widget;
import haven.Window;
import haven.purus.pbot.PBotUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import static haven.OCache.posres;

public class SeedCropFarmer2 extends Window implements Runnable {

    private Coord rc1, rc2;

    private ArrayList<Gob> crops = new ArrayList<Gob>();

    private boolean stopThread = false;

    private Label lblProg;

    private int stage;
    private String cropName;
    private String seedName;
    private boolean container; // True = Only Container, False = Only replant
    private Gob barrel;

    public SeedCropFarmer2(Coord rc1, Coord rc2, String cropName, String seedName, int stage, boolean container, Gob barrel) {
        super(new Coord(120, 65), cropName.substring(cropName.lastIndexOf("/") + 1).substring(0, 1).toUpperCase()
                + cropName.substring(cropName.lastIndexOf("/") + 1).substring(1) + " Farmer");
        this.rc1 = rc1;
        this.rc2 = rc2;
        this.cropName = cropName;
        this.stage = stage;
        this.seedName = seedName;
        this.container = container;
        this.barrel = barrel;

        Label lblstxt = new Label("Progress:");
        add(lblstxt, new Coord(15, 35));
        lblProg = new Label("Initialising...");
        add(lblProg, new Coord(65, 35));

        Button stopBtn = new Button(120, "Stop") {
            @Override
            public void click() {
                stop();
            }
        };
        add(stopBtn, new Coord(0, 0));

    }

    public void run() {
        try {
            // Initialise crop list
            crops = Crops();
            int totalCrops = crops.size();
            int cropsHarvested = 0;
            lblProg.settext(cropsHarvested + "/" + totalCrops);
            for (Gob g : crops) {
                if (stopThread)
                    return;
                // Check if stamina is under 30%, drink if so
                IMeter.Meter stam = ui.gui.getmeter("stam", 0);
                if (stam.a <= 30) {
                    PBotUtils.drink(ui, true);
                }

                if (stopThread)
                    return;

                // Right click the crop
                //PBotUtils.doClick(ui, g, 1, 0);
                //PBotUtils.gui.map.wdgmsg("click", Coord.z, g.rc.floor(posres), 1, 0);
                PBotUtils.pfGobClick(ui, g, 3, 0);

                // Wait for harvest menu to appear and harvest the crop
                while (ui.root.findchild(FlowerMenu.class) == null) {
                    PBotUtils.sleep(10);
                }

                if (stopThread)
                    return;

                FlowerMenu menu = ui.root.findchild(FlowerMenu.class);
                if (menu != null) {
                    for (FlowerMenu.Petal opt : menu.opts) {
                        if (opt.name.equals("Harvest")) {
                            menu.choose(opt);
                            menu.destroy();
                        }
                    }
                }
                while (PBotUtils.findObjectById(ui, g.id) != null) {
                    PBotUtils.sleep(10);
                }

                if (stopThread)
                    return;
                if (seedName.equals("gfx/invobjs/hemp-fresh")) {
                    // Drop all (seed)items from the players inventory
                    PBotUtils.dropItem(ui, 0);
                    for (Widget w = PBotUtils.playerInventory(ui).inv.child; w != null; w = w.next) {
                        if (w instanceof GItem && ((GItem) w).resource().name.equals(seedName)) {
                            GItem item = (GItem) w;
                            try {
                                item.wdgmsg("drop", Coord.z);
                            } catch (Exception e) {
                            }
                        }
                    }
                } else {
                    // Replant
                    GItem item = null;
                    while (PBotUtils.getItemAtHand(ui) == null) {
                        Inventory inv = PBotUtils.playerInventory(ui).inv;
                        for (Widget w = inv.child; w != null; w = w.next) {
                            if (w instanceof GItem && ((GItem) w).resource().name.equals(seedName) && (!seedName.contains("seed") || PBotUtils.getAmount((GItem) w) >= 5)) {
                                item = (GItem) w;
                                break;
                            }
                        }
                        if (item != null)
                            PBotUtils.takeItem(ui, item);
                    }

                    while (PBotUtils.getItemAtHand(ui) == null)
                        PBotUtils.sleep(10);

                    // Plant the seed from hand
                    int amount = 0;
                    PBotUtils.mapInteractClick(ui);
                    while (PBotUtils.findNearestStageCrop(ui, 5, 0, cropName) == null || (PBotUtils.getItemAtHand(ui) != null && (seedName.contains("seed") && amount == PBotUtils.getAmount(PBotUtils.getItemAtHand(ui).witem)))) {
                        PBotUtils.sleep(10);
                    }

                    if (container) {
                        // Merge seed from hand into inventory or put it in inventory
                        for (Widget w = PBotUtils.playerInventory(ui).inv.child; w != null; w = w.next) {
                            if (w instanceof GItem && ((GItem) w).resource().name.equals(seedName)) {
                                item = (GItem) w;
                                if (PBotUtils.getItemAtHand(ui) != null && PBotUtils.getAmount(item) < 50) {
                                    int handAmount = PBotUtils.getAmount(PBotUtils.getItemAtHand().witem);
                                    try {
                                        item.wdgmsg("itemact", 0);
                                    } catch (Exception e) {
                                    }
                                    while (PBotUtils.getItemAtHand(ui) != null && PBotUtils.getAmount(PBotUtils.getItemAtHand().witem) == handAmount)
                                        PBotUtils.sleep(50);
                                }
                            }
                        }
                        if (PBotUtils.getItemAtHand(ui) != null) {
                            Coord slot = PBotUtils.getFreeInvSlot(PBotUtils.playerInventory().inv);
                            if (slot != null) {
                                int freeSlots = PBotUtils.invFreeSlots(ui);
                                PBotUtils.dropItemToInventory(slot, PBotUtils.playerInventory().inv);
                                while (PBotUtils.getItemAtHand(ui) != null)
                                    PBotUtils.sleep(50);
                            }
                        }
                    } else {
                        // Drop all (seed)items from the players inventory
                        PBotUtils.dropItem(ui, 0);
                        for (Widget w = PBotUtils.playerInventory(ui).inv.child; w != null; w = w.next) {
                            if (w instanceof GItem && ((GItem) w).resource().name.equals(seedName)) {
                                item = (GItem) w;
                                try {
                                    item.wdgmsg("drop", Coord.z);
                                } catch (Exception e) {
                                }
                            }
                        }
                    }

                    if (container) { // Put items into container if inventory is full
                        if (PBotUtils.invFreeSlots(ui) == 0) {
                            PBotUtils.pfGobClick(ui, barrel, 3, 0);
                            PBotUtils.waitForWindow(ui, "Barrel");
                            if (PBotUtils.getItemAtHand(ui) != null) {
                                ui.gui.map.wdgmsg("itemact", Coord.z, barrel.rc.floor(posres), 0, 0, (int) barrel.id,
                                        barrel.rc.floor(posres), 0, -1);
                                int i = 0;
                                while (PBotUtils.getItemAtHand(ui) != null) {
                                    if (i == 60000)
                                        break;
                                    PBotUtils.sleep(10);
                                    i++;
                                }
                            }
                            while (PBotUtils.getInventoryItemsByNames(PBotUtils.playerInventory(ui).inv, Arrays.asList(seedName)).size() != 0) {
                                if (stopThread)
                                    break;
                                item = PBotUtils.getInventoryItemsByNames(PBotUtils.playerInventory(ui).inv, Arrays.asList(seedName)).get(0).gitem;
                                PBotUtils.takeItem(ui, item);

                                ui.gui.map.wdgmsg("itemact", Coord.z, barrel.rc.floor(posres), 0, 0, (int) barrel.id,
                                        barrel.rc.floor(posres), 0, -1);
                                int i = 0;
                                while (PBotUtils.getItemAtHand(ui) != null) {
                                    if (i == 60000)
                                        break;
                                    PBotUtils.sleep(10);
                                    i++;
                                }
                            }
                        }
                    }
                }
                cropsHarvested++;
                lblProg.settext(cropsHarvested + "/" + totalCrops);
            }
            if (container) {
                if (PBotUtils.getItemAtHand(ui) != null)
                    PBotUtils.dropItem(ui, 0);
                PBotUtils.pfGobClick(barrel, 3, 0);
                PBotUtils.waitForWindow("Barrel");

                while (PBotUtils.getInventoryItemsByNames(PBotUtils.playerInventory(ui).inv, Arrays.asList(seedName)).size() != 0) {
                    if (stopThread)
                        break;
                    GItem item = PBotUtils.getInventoryItemsByNames(PBotUtils.playerInventory().inv, Arrays.asList(seedName)).get(0).gitem;
                    PBotUtils.takeItem(ui, item);

                    ui.gui.map.wdgmsg("itemact", Coord.z, barrel.rc.floor(posres), 0, 0, (int) barrel.id,
                            barrel.rc.floor(posres), 0, -1);
                    int i = 0;
                    while (PBotUtils.getItemAtHand(ui) != null) {
                        if (i == 60000)
                            break;
                        PBotUtils.sleep(10);
                        i++;
                    }
                }
            }
            PBotUtils.sysMsg(ui, cropName.substring(cropName.lastIndexOf("/") + 1).substring(0, 1).toUpperCase()
                    + cropName.substring(cropName.lastIndexOf("/") + 1).substring(1)
                    + " Farmer finished!", Color.white);
            this.destroy();
        } catch (Resource.Loading l) {
        }
    }

    public ArrayList<Gob> Crops() {
        // Initialises list of crops to harvest between the selected coordinates
        ArrayList<Gob> gobs = new ArrayList<Gob>();
        double bigX = rc1.x > rc2.x ? rc1.x : rc2.x;
        double smallX = rc1.x < rc2.x ? rc1.x : rc2.x;
        double bigY = rc1.y > rc2.y ? rc1.y : rc2.y;
        double smallY = rc1.y < rc2.y ? rc1.y : rc2.y;
        synchronized (ui.sess.glob.oc) {
            for (Gob gob : ui.sess.glob.oc) {
                if (gob.rc.x <= bigX && gob.rc.x >= smallX && gob.getres() != null && gob.rc.y <= bigY
                        && gob.rc.y >= smallY && gob.getres().name.contains(cropName) && gob.getStage() == stage) {
                    gobs.add(gob);
                }
            }
        }
        gobs.sort(new CoordSort());
        return gobs;
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (sender == cbtn) {
            stop();
            reqdestroy();
        } else
            super.wdgmsg(sender, msg, args);
    }

    // Sorts coordinate array to efficient sequence
    class CoordSort implements Comparator<Gob> {
        public int compare(Gob a, Gob b) {
            if (a.rc.floor().x == b.rc.floor().x) {
                if (a.rc.floor().x % 2 == 0)
                    return (a.rc.floor().y < b.rc.floor().y) ? 1 : (a.rc.floor().y > b.rc.floor().y) ? -1 : 0;
                else
                    return (a.rc.floor().y < b.rc.floor().y) ? -1 : (a.rc.floor().y > b.rc.floor().y) ? 1 : 0;
            } else
                return (a.rc.floor().x < b.rc.floor().x) ? -1 : (a.rc.floor().x > b.rc.floor().x) ? 1 : 0;
        }
    }

    public void stop() {
        // Stops thread
        PBotUtils.sysMsg(ui, cropName.substring(cropName.lastIndexOf("/") + 1).substring(0, 1).toUpperCase()
                + cropName.substring(cropName.lastIndexOf("/") + 1).substring(1)
                + " Farmer stopped!", Color.white);
        ui.gui.map.wdgmsg("click", Coord.z, ui.gui.map.player().rc.floor(posres), 1, 0);
        if (ui.gui.map.pfthread != null) {
            ui.gui.map.pfthread.interrupt();
        }
        stopThread = true;
        this.destroy();
    }
}
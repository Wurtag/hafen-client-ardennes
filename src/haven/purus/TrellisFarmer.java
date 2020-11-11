package haven.purus;

import haven.Button;
import haven.Coord;
import haven.Coord2d;
import haven.FastMesh;
import haven.GItem;
import haven.Gob;
import haven.Inventory;
import haven.Label;
import haven.Widget;
import haven.Window;
import haven.purus.pbot.PBotCharacterAPI;
import haven.purus.pbot.PBotUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static haven.OCache.posres;

public class TrellisFarmer extends Window implements Runnable {

    private Coord rc1, rc2;

    private List<Gob> crops = new ArrayList<Gob>();

    private boolean stopThread = false;

    private Label lblProg, lblProg2;

    private List<String> cropName = new ArrayList<String>();
    private List<String> seedName = new ArrayList<String>();
    private String trellis = "gfx/terobjs/plants/trellis";

    private boolean harvest = false;
    private boolean destroy = false;
    private boolean replant = false;
    private Gob chest;

    public TrellisFarmer(Coord rc1, Coord rc2, boolean harvest, boolean destroy, boolean replant, Gob chest) {
        super(new Coord(120, 65), "Trellis Farmer");
        this.rc1 = rc1;
        this.rc2 = rc2;
        this.harvest = harvest;
        this.destroy = destroy;
        this.replant = replant;
        this.chest = chest;

        // Initialise arraylists
        seedName.add("gfx/invobjs/peapod");
        seedName.add("gfx/invobjs/peppercorn");
        seedName.add("gfx/invobjs/seed-cucumber");
        seedName.add("gfx/invobjs/seed-grape");
        seedName.add("gfx/invobjs/hopcones");

        cropName.add("gfx/terobjs/plants/pepper");
        cropName.add("gfx/terobjs/plants/peas");
        cropName.add("gfx/terobjs/plants/hops");
        cropName.add("gfx/terobjs/plants/cucumber");
        cropName.add("gfx/terobjs/plants/wine");

        Label lblstxt = new Label("Progress:");
        add(lblstxt, new Coord(15, 35));
        lblProg = new Label("Initialising...");
        add(lblProg, new Coord(65, 35));
        lblProg2 = new Label("Info");
        add(lblProg2, new Coord(15, 55));

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
            PBotUtils.sysMsg(ui, "Trellis Farmer started!", Color.white);
            if (harvest) {
                // Initialise crop list
                crops = Crops(true);

                // Initialize progression label on window
                int totalCrops = crops.size();
                int cropsHarvested = 0;
                lblProg.settext(cropsHarvested + "/" + totalCrops);
                lblProg2.settext("Harvest");

                for (Gob g : crops) {
                    // Update progression
                    cropsHarvested++;
                    lblProg.settext(cropsHarvested + "/" + totalCrops);

                    if (PBotUtils.findObjectById(ui, g.id) == null
                            || PBotUtils.findObjectById(ui, g.id).getStage() != getMaxStage(g)) continue;

                    if (stopThread) // Checks if aborted
                        return;

                    // Check if stamina is under 30%, drink if needed
                    //GameUI gui = this.parent.findchild(GameUI.class);
                    if (PBotUtils.getStamina(ui) <= 30) {
                        lblProg2.settext("Drink");
                        PBotUtils.drink(ui, true);
                    }

                    if (stopThread)
                        return;

                    int stageBefore = g.getStage();

                    // Right click the crop
                    if (!pathTo(g)) continue;
                    lblProg2.settext("Right Click");
                    PBotUtils.doClick(ui, g, 3, 0);

                    // Wait for harvest menu to appear
                    PBotUtils.waitForFlowerMenu(ui, 3);
                    if (!PBotUtils.petalExists(ui)) continue;
                    if (stopThread) return;
//                while (ui.root.findchild(FlowerMenu.class) == null) {
//                    PBotUtils.sleep(10);
//                    if (stopThread)
//                        return;
//                }

                    // Select the harvest option
                    lblProg2.settext("Harvest");
                    PBotUtils.choosePetal(ui, "Harvest");
//                FlowerMenu menu = ui.root.findchild(FlowerMenu.class);
//                if (menu != null) {
//                    for (FlowerMenu.Petal opt : menu.opts) {
//                        if (opt.name.equals("Harvest")) {
//                            menu.choose(opt);
//                            menu.destroy();
//                        }
//                    }
//                }

                    // Wait until stage has changed = harvested
                    while (true) {
                        lblProg2.settext("Wait harvested");
                        if (PBotUtils.findObjectById(ui, g.id) == null
                                || PBotUtils.findObjectById(ui, g.id).getStage() != stageBefore)
                            break;
                        else
                            PBotUtils.sleep(20);
                        if (stopThread)
                            return;
                    }
                    try {
                        GItem dropitem;
                        for (Widget w = ui.gui.maininv.child; w != null; w = w.next) {
                            lblProg2.settext("Droping");
                            if (w instanceof GItem && (((GItem) w).resource().name.contains("grape")
                                    || ((GItem) w).resource().name.contains("peppercorn"))) {
                                dropitem = (GItem) w;
                                try {
                                    dropitem.wdgmsg("drop", Coord.z);
                                } catch (Exception e) {
                                    // Shouldnt matter
                                }
                            }
                        }
                    } catch (Exception e) {
                    }

                    if (PBotUtils.invFreeSlots(ui) < 4 && chest != null) {
                        PBotUtils.pfRightClick(ui, chest, 0);
                        try {
                            while (ui.gui.getwnd("Exquisite Chest") == null) {
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException iqp) {
                                }
                            }
                        } catch (NullPointerException ipo) {
                        }
                        PBotUtils.waitForWindow(ui, "Exquisite Chest");
                        for (Widget w = ui.gui.maininv.child; w != null; w = w.next) {
                            if (w instanceof GItem && ((GItem) w).res.get().name.contains("pepper")) {
                                GItem item = (GItem) w;
                                try {
                                    item.wdgmsg("transfer", Coord.z);

                                } catch (NullPointerException qip) {
                                    PBotUtils.sysMsg(ui, "Null Pointer on line 142", Color.white);
                                }
                            }
                        }
                        if (PBotUtils.invFreeSlots(ui) < 20)
                            for (Widget w = ui.gui.maininv.child; w != null; w = w.next) {
                                if (w instanceof GItem && ((GItem) w).res.get().name.contains("pepper")) {
                                    GItem item = (GItem) w;
                                    try {
                                        item.wdgmsg("drop", Coord.z);

                                    } catch (NullPointerException qip) {
                                        PBotUtils.sysMsg(ui, "Null Pointer on line 142", Color.white);
                                    }
                                }
                            }
                    }
                }
            }


            if (destroy) {
                crops = Crops(false);

                // Initialize progression label on window
                int totalCrops = crops.size();
                int cropsHarvested = 0;
                lblProg.settext(cropsHarvested + "/" + totalCrops);
                lblProg2.settext("Destroy");

                for (Gob g : crops) {
                    // Update progression
                    cropsHarvested++;
                    lblProg.settext(cropsHarvested + "/" + totalCrops);

                    if (PBotUtils.findObjectById(ui, g.id) == null) continue;

                    while (PBotUtils.findObjectById(ui, g.id) != null) {
                        if (stopThread) // Checks if aborted
                            return;

                        // Check if stamina is under 30%, drink if needed
                        //GameUI gui = this.parent.findchild(GameUI.class);
                        if (PBotUtils.getStamina(ui) <= 30) {
                            lblProg2.settext("Drink");
                            PBotUtils.drink(ui, true);
                        }

                        if (stopThread)
                            return;

                        // Click destroy on gob
                        if (!pathTo(g)) continue;
                        lblProg2.settext("Destroy");
                        PBotUtils.destroyGob(ui, g);
                        PBotCharacterAPI.cancelAct(ui);

                        // Wait until the gob is gone = destroyed
                        lblProg2.settext("Wait destroyed");
                        PBotUtils.waitForHourglass(ui);

                        if (stopThread)
                            return;
                    }
                }
            } // End of destroy

            if (replant) {
                crops = trellisWithOutPlant(Trellises(), Crops(false)); // in this case crops = trellis
                // Initialise progression label on window
                int totalCrops = crops.size();
                int cropsHarvested = 0;
                lblProg.settext(cropsHarvested + "/" + totalCrops);
                lblProg2.settext("Replant");

                for (Gob g : crops) {
                    // Update progression
                    cropsHarvested++;
                    lblProg.settext(cropsHarvested + "/" + totalCrops);

                    // Take a seed from inventory to hand
                    GItem item = null;
                    while (PBotUtils.getItemAtHand(ui) == null) {
                        lblProg2.settext("Wait item in inventory");
                        Inventory inv = ui.gui.maininv;
                        for (Widget w = inv.child; w != null; w = w.next) {
                            if (w instanceof GItem && seedName.contains(((GItem) w).resource().name)) {
                                item = (GItem) w;
                                break;
                            }
                        }
                        if (item != null)
                            PBotUtils.takeItem(ui, item);
                    }

                    if (stopThread)
                        return;

                    // Right click trellis with the seed
                    if (!pathTo(g)) continue;
                    int amount = PBotUtils.getItemAtHand(ui).getAmount();
                    lblProg2.settext("Plant");
                    PBotUtils.itemClick(ui, g, 0);

                    // Wait until item is gone from hand = Planted
                    int retry = 0; // IF no success for 10 seconds skip
                    while (PBotUtils.getItemAtHand(ui) != null) {
                        lblProg2.settext("Wait planted");
                        if (PBotUtils.getItemAtHand(ui) != null && PBotUtils.getItemAtHand(ui).getAmount() < amount)
                            break;
                        PBotUtils.sleep(10);
                        if (stopThread)
                            return;
                        retry++;
                        if (retry > 1000)
                            break;
                    }
                }
            }

            PBotUtils.sysMsg(ui, "Trellis Farmer finished!", Color.white);
            this.destroy();
        } catch (Exception e) {
            e.printStackTrace();
            PBotUtils.sysMsg(ui, e.getMessage());
        }
    }

    public int getMaxStage(Gob gob) {
        int cropstgmaxval = 0;
        for (FastMesh.MeshRes layer : gob.getres().layers(FastMesh.MeshRes.class)) {
            int stg = layer.id / 10;
            if (stg > cropstgmaxval)
                cropstgmaxval = stg;
        }
        return cropstgmaxval;
    }

    public ArrayList<Gob> Crops(boolean checkStage) {
        // Initialises list of crops to harvest between selected coordinates
        ArrayList<Gob> gobs = new ArrayList<Gob>();
        double bigX = Math.max(rc1.x, rc2.x);
        double smallX = Math.min(rc1.x, rc2.x);
        double bigY = Math.max(rc1.y, rc2.y);
        double smallY = Math.min(rc1.y, rc2.y);
        synchronized (ui.sess.glob.oc) {
            for (Gob gob : ui.sess.glob.oc) {
                if (gob.rc.x <= bigX && gob.rc.x >= smallX && gob.getres() != null && gob.rc.y <= bigY
                        && gob.rc.y >= smallY && cropName.contains(gob.getres().name)) {
                    // Add to list if its max stage
                    if (checkStage) {
                        if (gob.getStage() == getMaxStage(gob)) {
                            gobs.add(gob);
                        }
                    } else
                        gobs.add(gob);
                }
            }
        }
        gobs.sort(new CoordSort());
        return gobs;
    }

    public ArrayList<Gob> Trellises() {
        // Initialises list of crops to harvest between selected coordinates
        ArrayList<Gob> gobs = new ArrayList<Gob>();
        double bigX = Math.max(rc1.x, rc2.x);
        double smallX = Math.min(rc1.x, rc2.x);
        double bigY = Math.max(rc1.y, rc2.y);
        double smallY = Math.min(rc1.y, rc2.y);
        synchronized (ui.sess.glob.oc) {
            for (Gob gob : ui.sess.glob.oc) {
                if (gob.rc.x <= bigX && gob.rc.x >= smallX && gob.getres() != null && gob.rc.y <= bigY
                        && gob.rc.y >= smallY && gob.getres().name.equals(trellis)) {
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

            if (a.rc.x == b.rc.x) {
                if (a.rc.x % 2 == 0)
                    return Double.compare(b.rc.y, a.rc.y);
                else
                    return Double.compare(a.rc.y, b.rc.y);
            } else
                return Double.compare(a.rc.x, b.rc.x);
        }
    }

    public List<Gob> trellisWithOutPlant(List<Gob> trellises, List<Gob> plants) {
        List<Gob> result = new ArrayList<>();

        for (Gob trellis : trellises) {
            boolean isplanted = false;
            for (Gob plant : plants) {
                if (trellis.rc.equals(plant.rc))
                    isplanted = true;
            }
            if (!isplanted) result.add(trellis);
        }

        return result;
    }

    public void stop() {
        // Stops thread
        PBotUtils.sysMsg(ui, "Trellis Farmer stopped!", Color.white);
        ui.gui.map.wdgmsg("click", Coord.z, ui.gui.map.player().rc.floor(posres), 1, 0);
        stopThread = true;
        this.destroy();
    }

    public boolean pathTo(Gob g) {
        Coord2d gCoord = g.rc;

        lblProg2.settext("Find path");
        for (Coord2d c2d : near(gCoord)) {
            if (PBotUtils.pfmove(ui, c2d.x, c2d.y)) {
                return true;
            }
        }

        return false;
    }

    public List<Coord2d> near(Coord2d coord2d) {
        List<Coord2d> coord2ds = new ArrayList<>();
        double distance = 11;
        coord2ds.add(new Coord2d(coord2d.x + distance, coord2d.y));
        coord2ds.add(new Coord2d(coord2d.x - distance, coord2d.y));
        coord2ds.add(new Coord2d(coord2d.x, coord2d.y + distance));
        coord2ds.add(new Coord2d(coord2d.x, coord2d.y - distance));

        coord2ds.sort(Comparator.comparingDouble(a -> PBotUtils.player(ui).rc.dist(a)));

        return coord2ds;
    }

    public boolean isSeed(GItem gItem) {
        if (gItem == null) return false;
        if (gItem.res.get().name.equals("gfx/invobjs/seed-cucumber") && gItem.res.get().name.equals("gfx/invobjs/seed-grape")) {
            return true;
        } else return false;
    }
}
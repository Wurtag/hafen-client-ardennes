package haven.purus;

import haven.Button;
import haven.Coord;
import haven.FlowerMenu;
import haven.GItem;
import haven.GameUI;
import haven.Gob;
import haven.IMeter;
import haven.Inventory;
import haven.Label;
import haven.Loading;
import haven.Resource;
import haven.Sprite;
import haven.WItem;
import haven.Widget;
import haven.Window;
import haven.purus.pbot.PBotGobAPI;
import haven.purus.pbot.PBotItem;
import haven.purus.pbot.PBotUtils;
import haven.res.ui.tt.q.qbuff.QBuff;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static haven.OCache.posres;

public class SeedCropFarmer extends Window implements Runnable {

    private Coord rc1, rc2;

    private ArrayList<Gob> crops = new ArrayList<Gob>();

    private boolean stopThread = false;

    private Label lblProg, lblProg2;

    private int stage;
    private String cropName;
    private String seedName;
    private boolean replant;
    private boolean ispumpkin;
    private boolean stockpile;
    private boolean replantcontainer; // True = Only Container, False = Only replant
    private boolean containeronly;
    private ArrayList<Coord> stockpileLocs = new ArrayList<>();
    private ArrayList<Gob> containers = new ArrayList<>();
    //private Gob barrel;

    public SeedCropFarmer(Coord rc1, Coord rc2, String cropName, String seedName, int stage, boolean replant, boolean containeronly, boolean replantcontainer, ArrayList<Gob> containers, boolean stockpile, ArrayList<Coord> stockpileLocs) {
        super(new Coord(120, 65), cropName.substring(cropName.lastIndexOf("/") + 1).substring(0, 1).toUpperCase()
                + cropName.substring(cropName.lastIndexOf("/") + 1).substring(1) + " Farmer");
        this.rc1 = rc1;
        this.rc2 = rc2;
        this.cropName = cropName;
        this.stage = stage;
        this.containers = containers;
        this.seedName = seedName;
        this.replantcontainer = replantcontainer;
        this.replant = replant;
        this.containeronly = containeronly;
        this.stockpile = stockpile;
        this.stockpileLocs = stockpileLocs;
        //	this.barrel = barrel;

        Label lblstxt = new Label("Progress:");
        add(lblstxt, new Coord(15, 35));
        lblProg = new Label("Initialising...");
        add(lblProg, new Coord(67, 35));
        lblProg2 = new Label("Initialising...");
        add(lblProg2, new Coord(0, 55));

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
            if (stockpile)
                PBotUtils.sysMsg(ui, "Auto-Stockpile not yet fully tested.", Color.white);
            // Initialise crop list
            ui.gui.map.unregisterGobSelect();
            crops = Crops();
            int totalCrops = crops.size();
            int cropsHarvested = 0;
            lblProg.settext(cropsHarvested + "/" + totalCrops);
            lblProg2.settext("Starting");
            if (seedName.contains("pumpkin"))
                ispumpkin = true;
            else
                ispumpkin = false;
            for (Gob g : crops) {
                if (stopThread)
                    return;
                // Check if stamina is under 30%, drink if so
                //GameUI gui = this.parent.findchild(GameUI.class);
                GameUI gui = ui.gui;
                if (PBotUtils.getStamina(ui) < 60) {
                    lblProg2.settext("Drinking");
                    PBotUtils.drink(ui, true);
//                    PBotUtils.sleep(1000);//sleep while drinking
                }

                if (stopThread)
                    return;

                // Right click the crop
                lblProg2.settext("Harvesting");
                try {
                    PBotUtils.doClick(ui, g, 1, 0);
                    //BotUtils.pfRightClick(g,0);
                } catch (NullPointerException ii) {
                    continue;
                }
                //	BotUtils.gui.map.wdgmsg("click", Coord.z, g.rc.floor(posres), 1, 0);
                int retryharvest = 0;
                int retrycount = 0;
                while (PBotUtils.player(ui) != null && (PBotUtils.player(ui).rc.x != g.rc.x || PBotUtils.player(ui).rc.y != g.rc.y)) {
                    if (stopThread)
                        return;
                    lblProg2.settext("Moving to Harvest");
                    retryharvest++;
                    while (PBotUtils.isMoving(ui))
                        PBotUtils.sleep(10);//if we're moving, sleep and dont trigger unstucking
                    if (retryharvest > 300) {
                        lblProg2.settext("Unstucking");
                        PBotUtils.sysLogAppend(ui, "Moving char", "white");
                        Gob player = gui.map.player();
                        Coord location = player.rc.floor(posres);
                        int x = location.x + getrandom();
                        int y = location.y + getrandom();
                        Coord finalloc = new Coord(x, y);
                        if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                        ui.gui.map.wdgmsg("click", Coord.z, finalloc, 1, 0);
                        PBotUtils.sleep(1000);
                        if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                        PBotUtils.doClick(ui, g, 1, 0);
                        retryharvest = 0;
                        retrycount++;
                    }
                    if (retrycount > 5) {
                        PBotUtils.sysLogAppend(ui, "Tried to move to crop 3 times, skipping left click loop", "white");
                        //super stuck, fuck it skip this wait
                        break;
                    }
                    PBotUtils.sleep(10);
                }
                if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                PBotUtils.doClick(ui, g, 3, 0);
                ui.wdgmsg(ui.next_predicted_id, "cl", 0, 0);
                retryharvest = 0;
                while (PBotUtils.findObjectById(ui, g.id) != null) {
                    if (stopThread)
                        return;
                    lblProg2.settext("Waiting for crop to disappear");
                    retryharvest++;
                    if (retryharvest > 500) {
                        lblProg2.settext("Retry harvest");
                        //PBotUtils.pfRightClick(g,0);
                        if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                        PBotUtils.doClick(ui, g, 3, 0);
                        ui.wdgmsg(ui.next_predicted_id, "cl", 0, 0);
                        retryharvest = 0;
                    }
                    PBotUtils.sleep(10);
                }


                if (stopThread)
                    return;
                // Replant
                if (ispumpkin) {
                    try {
                        lblProg2.settext("Grabbing seeds ispumpkin2");
                        GItem item = null;
                        List<PBotItem> itemlist = PBotUtils.getInventoryItemsByName(ui.gui.maininv, "gfx/invobjs/seed-pumpkin");
                        if (cropsHarvested == 0) //half second delay for the first pumpkin harvest to register to start the seed rotation.
                            PBotUtils.sleep(500);
                        if (itemlist.size() > 0) {//If seeds are present in inventory, try to use them.
                            for (PBotItem witem : itemlist) {
                                if (PBotUtils.getAmount(witem.witem) > 5) {
                                    while (PBotUtils.getItemAtHand(ui) == null) {
                                        lblProg2.settext("Grabbing seeds ispumpkin2");
                                        if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                                        PBotUtils.takeItem(ui, witem.witem, 1000);
                                        int retrypickup = 0;
                                        while (PBotUtils.getItemAtHand(ui) == null) {
                                            retrypickup++;
                                            if (retrypickup > 200) {
                                                if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                                                PBotUtils.takeItem(ui, witem.witem, 1000);
                                                retrypickup = 0;
                                            }
                                            PBotUtils.sleep(10);
                                        }
                                    }
                                }
                            }
                        } else {   //Failed to pickup seeds, either from not having any or other reasons. Pick up a pumpkin and acquire seeds.
                            lblProg2.settext("Pickup Pumpkin");
                            Gob pumpkin = null;
                            while (PBotUtils.findObjectByNames(ui, 10, "gfx/terobjs/items/pumpkin") == null) {
                                if (stopThread)
                                    return;
                                PBotUtils.sleep(10);
                            }
                            pumpkin = PBotUtils.findObjectByNames(ui, 10, "gfx/terobjs/items/pumpkin");
                            PBotUtils.pfRightClick(ui, pumpkin, 0);
                            int retrypumpkinpickup = 0;
                            while (PBotUtils.getInventoryItemsByName(ui.gui.maininv, "gfx/invobjs/pumpkin").size() == 0) {
                                retrypumpkinpickup++;
                                if (retrypumpkinpickup > 50) {
                                    lblProg2.settext("Retry Pickup");
                                    retrypumpkinpickup = 0;
                                    PBotUtils.pfRightClick(ui, pumpkin, 0);
                                }
                                PBotUtils.sleep(50);
                            }
                            List<PBotItem> pumpkinlist = PBotUtils.getInventoryItemsByName(ui.gui.maininv, "gfx/invobjs/pumpkin");
                            PBotItem invpumpkin = pumpkinlist.get(0);
                            invpumpkin.witem.wdgmsg("iact", Coord.z, -1);
                            FlowerMenu.setNextSelection("Slice");
                            int retryslice = 0;
                            lblProg2.settext("Slicing");
                            while (gui.maininv.getItemsPartial("seeds").size() == 0) {
                                if (stopThread)
                                    return;
                                retryslice++;
                                if (retryslice > 50) {
                                    lblProg2.settext("Retry Slicing");
                                    retryslice = 0;
                                    invpumpkin.witem.wdgmsg("iact", Coord.z, -1);
                                    FlowerMenu.setNextSelection("Slice");
                                }
                                PBotUtils.sleep(50);
                            }
                            List<WItem> fleshlist = gui.maininv.getItemsPartial("Flesh");
                            for (WItem flesh : fleshlist) {
                                flesh.item.wdgmsg("drop", Coord.z);
                            }
                            itemlist.clear();
                            itemlist = PBotUtils.getInventoryItemsByName(ui.gui.maininv, "gfx/invobjs/seed-pumpkin");
                            if (itemlist.size() == 0) {//If seeds are present in inventory, try to use them.
                                PBotUtils.sysMsg(ui, "Somehow don't have seeds after picking up and slicing a pumpkin, stopping", Color.white);
                                stop();
                                stopThread = true;
                            }
                        }
                        lblProg2.settext("Grabbing Seeds ispumpkin3");
                        Inventory inv = ui.gui.maininv;
                        for (Widget w = inv.child; w != null; w = w.next) {
                            if (w instanceof GItem && ((GItem) w).resource().name.equals(seedName) && (!seedName.contains("seed") || PBotUtils.getAmount((GItem) w) >= 5)) {
                                item = (GItem) w;
                                break;
                            }
                        }
                        if (item != null) {
                            System.out.println("picking up item to plant");
                            if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                            PBotUtils.takeItem(ui, item, 1000);
                        } else {
                            System.out.println("picking up item null");
                        }
                        while (PBotUtils.getItemAtHand(ui) == null) {
                            if (stopThread)
                                return;
                            PBotUtils.sleep(10);
                        }
                        // Plant the seed from hand
                        int amount = 0;
                        if (seedName.contains("seed"))
                            PBotUtils.getAmount(PBotUtils.getGItemAtHand(ui));
                        lblProg2.settext("Planting");
                        //PBotUtils.mapInteractClick();
                        gui.map.wdgmsg("itemact", Coord.z, PBotUtils.player(ui).rc.floor(posres), 0, 0, (int) PBotUtils.player(ui).id, PBotUtils.player(ui).rc.floor(posres), 0, -1);
                        while (PBotUtils.findNearestStageCrop(ui, 5, 0, cropName) == null || (PBotUtils.getItemAtHand(ui) != null && (seedName.contains("seed") && amount == PBotUtils.getAmount(PBotUtils.getGItemAtHand(ui))))) {
                            if (stopThread)
                                return;
                            PBotUtils.sleep(10);
                        }
                        lblProg2.settext("Dropping seeds to inv");
                        Coord slot = PBotUtils.getFreeInvSlot(ui.gui.maininv);
                        if (slot != null) {
                            PBotUtils.dropItemToInventory(slot, ui.gui.maininv);
                            while (PBotUtils.getItemAtHand(ui) != null)
                                PBotUtils.sleep(50);
                        }
                    } catch (NullPointerException | Loading | Sprite.ResourceException q) {
                    }
                } else if (replant) {
                    try {
                        GItem item = null;
                        while (PBotUtils.getItemAtHand(ui) == null) {
                            if (stopThread)
                                return;
                            lblProg2.settext("Grabbing seeds1");
                            Inventory inv = ui.gui.maininv;
                            for (Widget w = inv.child; w != null; w = w.next) {
                                if (w instanceof GItem && ((GItem) w).resource().name.equals(seedName) && (!seedName.contains("seed") || PBotUtils.getAmount((GItem) w) >= 5)) {
                                    item = (GItem) w;
                                    break;
                                }
                            }
                            if (item != null) {
                                System.out.println("picking up item to plant");
                                if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                                PBotUtils.takeItem(ui, item, 1000);
                            } else {
                                System.out.println("picking up item null");
                            }
                        }
                        retryharvest = 0;
                        if (item != null) {
                            while (PBotUtils.getItemAtHand(ui) == null) {
                                retryharvest++;
                                if (retryharvest > 500) {
                                    lblProg2.settext("Failed to pickup seeds, retrying.");
                                    if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                                    PBotUtils.takeItem(ui, item, 1000);
                                    retryharvest = 0;
                                }
                                PBotUtils.sleep(10);
                            }
                            // Plant the seed from hand
                            int amount = 0;
                            if (seedName.contains("seed"))
                                PBotUtils.getAmount(PBotUtils.getGItemAtHand(ui));
                            lblProg2.settext("Planting");
                            //PBotUtils.mapInteractClick();
                            gui.map.wdgmsg("itemact", Coord.z, PBotUtils.player(ui).rc.floor(posres), 0, 0, (int) PBotUtils.player(ui).id, PBotUtils.player(ui).rc.floor(posres), 0, -1);
                            while (PBotUtils.findNearestStageCrop(ui, 5, 0, cropName) == null || (PBotUtils.getItemAtHand(ui) != null && (seedName.contains("seed") && amount == PBotUtils.getAmount(PBotUtils.getGItemAtHand(ui))))) {
                                if (stopThread)
                                    return;
                                PBotUtils.sleep(10);
                            }
                            PBotUtils.dropItem(ui, 0);
                            lblProg2.settext("Dropping seeds");
                            for (Widget w = ui.gui.maininv.child; w != null; w = w.next) {
                                if (w instanceof GItem && ((GItem) w).resource().name.equals(seedName)) {
                                    item = (GItem) w;
                                    try {
                                        item.wdgmsg("drop", Coord.z);
                                    } catch (Exception e) {
                                    }
                                }
                            }
                        }
                    } catch (NullPointerException | Loading | Sprite.ResourceException q) {
                    }
                } else if (replantcontainer) {
                    try {
                        retryharvest = 0;
                        while (PBotUtils.getItemAtHand(ui) == null) { // loops until successfully picked up seeds
                            if (stopThread)
                                return;
                            lblProg2.settext("Grabbing seeds2");
                            Inventory inv = ui.gui.maininv;
                            lblProg2.settext("Grabbing seeds21");
                            while (inv.getItemPartial("seed") == null) {
                                lblProg2.settext("Grabbing seeds22");
                                System.out.println(inv.getItemPartial("seed") + "");
                                lblProg2.settext("Grabbing seeds23");
                                PBotUtils.sysMsg(ui, inv.getItemPartial("seed") + "");
                                lblProg2.settext("Grabbing seeds24");
                                PBotUtils.sleep(10);
                            }
                            lblProg2.settext("Grabbing seeds25");
                            WItem flax = inv.getItemPartial("seed");
                            lblProg2.settext("Grabbing seeds26");
                            GItem flax2 = flax.item;
                            lblProg2.settext("Grabbing seeds27");
                            List<WItem> items = inv.getIdenticalItems((flax2)); // acquires all seed stacks in inventory
                            lblProg2.settext("Grabbing seeds28");
                            sort(items); // sorts by quality
                            lblProg2.settext("Grabbing seeds29");
                            for (WItem seeds : items) {
                                lblProg2.settext("Grabbing seeds210");
                                GItem item = seeds.item;
                                lblProg2.settext("Grabbing seeds210");
                                if (PBotUtils.getAmount(item) >= 5) {
                                    lblProg2.settext("Grabbing seeds212");
                                    if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                                    lblProg2.settext("Grabbing seeds213");
                                    PBotUtils.takeItem(ui, item, 1000);
                                    lblProg2.settext("Grabbing seeds214");
                                    while (PBotUtils.getItemAtHand(ui) == null) { // just a double verification that we have successfully picked up seeds, should account for lag
                                        retryharvest++;
                                        lblProg2.settext("Grabbing seeds215");
                                        if (retryharvest > 500) {
                                            lblProg2.settext("Grabbing seeds216");
                                            retryharvest = 0;
                                            lblProg2.settext("Grabbing seeds217");
                                            if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                                            lblProg2.settext("Grabbing seeds218");
                                            PBotUtils.takeItem(ui, item, 1000);
                                        }
                                        lblProg2.settext("Grabbing seeds219");
                                        System.out.println(PBotUtils.getItemAtHand(ui) + " " + retryharvest);
                                        lblProg2.settext("Grabbing seeds220");
                                        PBotUtils.sysMsg(ui, PBotUtils.getItemAtHand(ui) + " " + retryharvest);
                                        PBotUtils.sleep(10);
                                    }
                                    break;
                                }
                            }
                        }


                        // Plant the seed from hand
                        int amount = 0;
                        if (seedName.contains("seed"))
                            PBotUtils.getAmount(PBotUtils.getGItemAtHand(ui)); // logs the seed count in your hand so it can use the count to verify it successfully planted
                        lblProg2.settext("Planting");
                        //PBotUtils.mapInteractClick();
                        gui.map.wdgmsg("itemact", Coord.z, PBotUtils.player(ui).rc.floor(posres), 0, 0, (int) PBotUtils.player(ui).id, PBotUtils.player(ui).rc.floor(posres), 0, -1);
                        while (PBotUtils.findNearestStageCrop(ui, 5, 0, cropName) == null || (PBotUtils.getItemAtHand(ui) != null && (seedName.contains("seed") && amount == PBotUtils.getAmount(PBotUtils.getGItemAtHand(ui))))) {
                            if (stopThread)
                                return;
                            PBotUtils.sleep(10);
                        }
                        // Merge seed from hand into inventory or put it in inventory
                        for (Widget w = ui.gui.maininv.child; w != null; w = w.next) {
                            if (w instanceof GItem && ((GItem) w).resource().name.equals(seedName)) {
                                GItem item = (GItem) w;
                                if (PBotUtils.getItemAtHand(ui) != null && PBotUtils.getAmount(item) < 50) {//finds other seeds in inventory with less than 50 count
                                    lblProg2.settext("Merging stacks");
                                    int handAmount = PBotUtils.getAmount(PBotUtils.getGItemAtHand(ui));
                                    try {
                                        item.wdgmsg("itemact", 0);//merges
                                    } catch (Exception e) {
                                    }
                                    while (PBotUtils.getItemAtHand(ui) != null && PBotUtils.getAmount(PBotUtils.getGItemAtHand(ui)) == handAmount)//waits until the count changes to account for lag
                                        PBotUtils.sleep(50);
                                }
                            }
                        }
                        if (PBotUtils.getItemAtHand(ui) != null) {//still have seeds on cursor, dropping them in an empty inventory slot
                            lblProg2.settext("Dropping to inv");
                            Coord slot = PBotUtils.getFreeInvSlot(ui.gui.maininv);
                            if (slot != null) {
                                PBotUtils.dropItemToInventory(slot, ui.gui.maininv);
                                while (PBotUtils.getItemAtHand(ui) != null)
                                    PBotUtils.sleep(50);
                            }
                        }
                        if (PBotUtils.invFreeSlots(ui) == 0) {//inv full, time to barrel
                            lblProg2.settext("Barreling");
                            if (PBotUtils.getItemAtHand(ui) != null)
                                PBotUtils.dropItem(ui, 0);
                            PBotUtils.pfRightClick(ui, containers.get(0), 0);
                            if (containers.get(0).getres().basename().contains("barrel"))
                                PBotUtils.waitForWindow(ui, "Barrel");
                            else
                                PBotUtils.waitForWindow(ui, "Trough");
                            GItem item = PBotUtils.getInventoryItemsByNames(ui.gui.maininv, Arrays.asList(seedName)).get(0).gitem;
                            if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                            PBotUtils.takeItem(ui, item, 1000);
                            while (PBotUtils.getInventoryItemsByName(ui.gui.maininv, seedName).size() > 0) {
                                if (stopThread)
                                    return;
                                if (PBotUtils.getItemAtHand(ui) == null) {
                                    System.out.println("Hand null, breaking");
                                    break;
                                }
                                List<PBotItem> list = PBotUtils.getInventoryItemsByName(ui.gui.maininv, seedName);
                                ui.gui.map.wdgmsg("itemact", Coord.z, containers.get(0).rc.floor(posres), 1, 0, (int) containers.get(0).id, containers.get(0).rc.floor(posres), 0, -1);
                                int i = 0;
                                while (PBotUtils.getInventoryItemsByName(ui.gui.maininv, seedName).size() == list.size()) {
                                    if (stopThread)
                                        break;
                                    if (containers.size() == 1 && i > 250) {
                                        PBotUtils.sysMsg(ui, "Only container in list appears to be full, stopping.", Color.white);
                                        stopThread = true;
                                        stop();
                                        break;
                                    } else if (i > 250) {
                                        PBotUtils.sysMsg(ui, "Container appears to be full, removing.", Color.white);
                                        Coord slot = PBotUtils.getFreeInvSlot(ui.gui.maininv);
                                        PBotUtils.dropItemToInventory(slot, ui.gui.maininv);
                                        PBotUtils.sleep(250);
                                        containers.remove(0);
                                        PBotUtils.pfRightClick(ui, containers.get(0), 0);
                                        if (containers.get(0).getres().basename().contains("barrel"))
                                            PBotUtils.waitForWindow(ui, "Barrel");
                                        else
                                            PBotUtils.waitForWindow(ui, "Trough");
                                        item = PBotUtils.getInventoryItemsByNames(ui.gui.maininv, Arrays.asList(seedName)).get(0).gitem;
                                        if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                                        PBotUtils.takeItem(ui, item, 1000);
                                        break;
                                    }
                                    PBotUtils.sleep(10);
                                    i++;
                                }
                            }
                            PBotUtils.sleep(250);
                            if (stopThread)
                                return;
                            if (PBotUtils.getItemAtHand(ui) != null) {//still have seeds on cursor, dropping them in an empty inventory slot
                                ui.gui.map.wdgmsg("itemact", Coord.z, containers.get(0).rc.floor(posres), 0, 0, (int) containers.get(0).id, containers.get(0).rc.floor(posres), 0, -1);
                            }
                            if (PBotUtils.getItemAtHand(ui) != null) {//still have seeds on cursor, dropping them in an empty inventory slot
                                lblProg2.settext("Dropping to inv");
                                Coord slot = PBotUtils.getFreeInvSlot(ui.gui.maininv);
                                if (slot != null) {
                                    PBotUtils.dropItemToInventory(slot, ui.gui.maininv);
                                    while (PBotUtils.getItemAtHand(ui) != null) {
                                        if (stopThread)
                                            return;
                                        PBotUtils.sleep(10);
                                    }
                                }
                            }
                        }
                    } catch (NullPointerException | Loading | Resource.LoadException x) {
                    }
                } else {
                    try {
                        if (containeronly) { // Put items into container if inventory is full
                            GItem item;
                            if (PBotUtils.invFreeSlots(ui) == 0) {
                                lblProg2.settext("Barreling");
                                PBotUtils.pfRightClick(ui, containers.get(0), 0);
                                PBotUtils.waitForWindow(ui, "Barrel");
                                item = PBotUtils.getInventoryItemsByNames(ui.gui.maininv, Arrays.asList(seedName)).get(0).gitem;
                                if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                                PBotUtils.takeItem(ui, item, 1000);
                                while (PBotUtils.getInventoryItemsByName(ui.gui.maininv, seedName).size() > 0) {
                                    if (stopThread)
                                        return;
                                    if (PBotUtils.getItemAtHand(ui) == null) {
                                        System.out.println("Hand null, breaking");
                                        break;
                                    }
                                    List<PBotItem> list = PBotUtils.getInventoryItemsByName(ui.gui.maininv, seedName);
                                    ui.gui.map.wdgmsg("itemact", Coord.z, containers.get(0).rc.floor(posres), 1, 0, (int) containers.get(0).id, containers.get(0).rc.floor(posres), 0, -1);
                                    while (PBotUtils.getInventoryItemsByName(ui.gui.maininv, seedName).size() == list.size()) {
                                        if (stopThread)
                                            return;
                                        int i = 0;
                                        while (PBotUtils.getInventoryItemsByName(ui.gui.maininv, seedName).size() == list.size()) {
                                            if (stopThread)
                                                return;
                                            if (containers.size() == 1 && i > 250) {
                                                PBotUtils.sysMsg(ui, "Only container in list appears to be full, stopping.", Color.white);
                                                stopThread = true;
                                                stop();
                                                break;
                                            } else if (i > 250) {
                                                PBotUtils.sysMsg(ui, "Container appears to be full, removing.", Color.white);
                                                Coord slot = PBotUtils.getFreeInvSlot(ui.gui.maininv);
                                                PBotUtils.dropItemToInventory(slot, ui.gui.maininv);
                                                PBotUtils.sleep(250);
                                                containers.remove(0);
                                                PBotUtils.pfRightClick(ui, containers.get(0), 0);
                                                if (containers.get(0).getres().basename().contains("barrel"))
                                                    PBotUtils.waitForWindow(ui, "Barrel");
                                                else
                                                    PBotUtils.waitForWindow(ui, "Trough");
                                                item = PBotUtils.getInventoryItemsByNames(ui.gui.maininv, Arrays.asList(seedName)).get(0).gitem;
                                                if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                                                PBotUtils.takeItem(ui, item, 1000);
                                                break;
                                            }
                                            PBotUtils.sleep(10);
                                            i++;
                                        }
                                    }
                                }
                                PBotUtils.sleep(250);
                                if (PBotUtils.getItemAtHand(ui) != null) {//still have seeds on cursor, dropping them in an empty inventory slot
                                    ui.gui.map.wdgmsg("itemact", Coord.z, containers.get(0).rc.floor(posres), 0, 0, (int) containers.get(0).id, containers.get(0).rc.floor(posres), 0, -1);
                                }
                                if (PBotUtils.getItemAtHand(ui) != null) {//still have seeds on cursor, dropping them in an empty inventory slot
                                    lblProg2.settext("Dropping to inv");
                                    Coord slot = PBotUtils.getFreeInvSlot(ui.gui.maininv);
                                    if (slot != null) {
                                        PBotUtils.dropItemToInventory(slot, ui.gui.maininv);
                                        while (PBotUtils.getItemAtHand(ui) != null) {
                                            if (stopThread)
                                                return;
                                            PBotUtils.sleep(10);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (NullPointerException | Loading | Resource.LoadException p) {
                    }
                }
                cropsHarvested++;
                lblProg.settext(cropsHarvested + "/" + totalCrops);
            }
            if (replantcontainer || containeronly) {
                lblProg2.settext("Barreling");
                if (PBotUtils.getItemAtHand(ui) != null)
                    PBotUtils.dropItem(ui, 0);
                PBotUtils.pfRightClick(ui, containers.get(0), 0);
                if (containers.get(0).getres().basename().contains("barrel"))
                    PBotUtils.waitForWindow(ui, "Barrel");
                else
                    PBotUtils.waitForWindow(ui, "Trough");

                while (PBotUtils.getInventoryItemsByNames(ui.gui.maininv, Arrays.asList(seedName)).size() != 0) {
                    if (stopThread)
                        break;
                    GItem item = PBotUtils.getInventoryItemsByNames(ui.gui.maininv, Arrays.asList(seedName)).get(0).gitem;
                    if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                    PBotUtils.takeItem(ui, item, 1000);

                    ui.gui.map.wdgmsg("itemact", Coord.z, containers.get(0).rc.floor(posres), 0, 0, (int) containers.get(0).id,
                            containers.get(0).rc.floor(posres), 0, -1);
                    int i = 0;
                    while (PBotUtils.getItemAtHand(ui) != null) {
                        if (i == 60000)
                            break;
                        PBotUtils.sleep(10);
                        i++;
                    }
                }
            }
            if (stockpile) {//new feature, when done farming stockpile the leftover materials
                lblProg2.settext("Stockpiling");
                List<Gob> stockpiles = new ArrayList<>();
                List<Coord> initcoordlist = stockpileLocs;//retain list so we can get all the new stockpiles from it later
                String groundname = null; //most stuff goes from terobjs/plants/carrot to terobjs/items/carrot for example
                List<String> invname = new ArrayList<>();

                //this attempts to resolve the inventory res and ground res of what you harvested
                if (cropName.contains("carrot") || cropName.contains("yellowonion") || cropName.contains("redonion") || cropName.contains("beet")
                        || cropName.contains("leek") || cropName.contains("turnip") || cropName.contains("pumpkin")) {
                    groundname = seedName.replaceAll("plants", "items");
                    invname.add(cropName.replaceAll("terobjs/plants", "invobjs"));
                } else {
                    if (cropName.contains("pipeweed")) {
                        groundname = "gfx/terobjs/items/tobacco-fresh";
                        invname.add("gfx/invobjs/tobacco-fresh");
                    } else if (cropName.contains("hemp")) {
                        groundname = "gfx/terobjs/items/hempfibre";
                        invname.add("gfx/invobjs/hempfibre");
                    } else if (cropName.contains("flax")) {
                        groundname = "gfx/terobjs/items/flaxfibre";
                        invname.add("gfx/invobjs/flaxfibre");
                    } else if (cropName.contains("poppy")) {
                        groundname = "gfx/terobjs/items/flower-poppy";
                        invname.add("gfx/invobjs/flower-poppy");
                    } else if (cropName.contains("wheat") || cropName.contains("barley") || cropName.contains("millet")) {
                        groundname = "gfx/terobjs/items/straw";
                        invname.add("gfx/invobjs/straw");
                    } else if (cropName.contains("pumpkin")) {
                        groundname = "gfx/terobjs/items/pumpkin";
                        invname.add("gfx/invobjs/pumpkin");
                    }
                }

                while (PBotUtils.getItemAtHand(ui) == null) {
                    if (stopThread)
                        return;
                    lblProg2.settext("Grabbing items");
                    if (PBotUtils.findObjectByNames(ui, 5000, groundname) == null) {
                        break;
                    }
                    PBotUtils.sysLogAppend(ui, "Grabbing stuff.", "white");
                    Gob g = PBotUtils.findObjectByNames(ui, 5000, groundname);
                    PBotUtils.pfGobClick(g, 3, 1);
//                    ui.gui.map.wdgmsg("click", g.sc, g.rc.floor(posres), 3, 1, 0, (int) g.id, g.rc.floor(posres), 0, -1);
                    PBotUtils.sleep(1000);

                    while (/*PBotUtils.getItemAtHand(ui) == null &
                            PBotUtils.findObjectByNames(ui, 5000, groundname) != null && */
                            PBotUtils.isMoving(ui)) {
//                        System.out.println("waiting for item on  hand");
                        PBotUtils.sleep(10);
                    }
                    System.out.println("inv free slots : " + PBotUtils.invFreeSlots(ui));
                }
                PBotUtils.dropItemFromHand(ui, 0);

                while (PBotUtils.getGItemAtHand(ui) != null) {//wait for hand to be droped
                    if (stopThread)
                        return;
                    PBotUtils.sleep(10);
                }


                List<PBotItem> items = PBotUtils.getInventoryItemsByNames(PBotUtils.playerInventory(ui).inv, invname);
                System.out.println("Stocklocs size : " + stockpileLocs.size() + " items size : " + items.size() + " " + invname);
                lblProg2.settext("Creating Stockpiles");
                while (stockpileLocs.size() > 0 && items.size() > 0) {//build stockpiles
                    if (stopThread)
                        return;
                    PBotItem item = items.get(0);
                    Coord location = stockpileLocs.get(0);
                    if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                    item.takeItem(1000);
                    while (PBotUtils.getItemAtHand(ui) == null)
                        PBotUtils.sleep(15);
                    PBotGobAPI.makePile(ui);
                    while (PBotUtils.getItemAtHand(ui) != null) {
                        PBotUtils.sleep(15);
                    }
                    if (!PBotUtils.pfLeftClick(ui, location.x + 11, location.y) && !PBotUtils.pfLeftClick(ui, location.x - 11, location.y) && !PBotUtils.pfLeftClick(ui, location.x, location.y + 11) && !PBotUtils.pfLeftClick(ui, location.x, location.y - 11)) { // Couldn't find path next to the stockpile that we want to make next
                        items.remove(item);
                        stockpileLocs.remove(location);
                        continue;
                    }
                    PBotGobAPI.placeThing(ui, location.x, location.y);
                    while (PBotUtils.getGItemAtHand(ui) != null)
                        PBotUtils.sleep(10);
                    items.remove(item);
                    stockpileLocs.remove(location);
                    PBotUtils.sleep(2000);//putting in small delay, seems to miss creating the last stockpile
                }
                stockpiles.addAll(Stockpiles(PBotUtils.getSelectedAreaA(), PBotUtils.getSelectedAreaB()));
                boolean stop = false;
                lblProg2.settext("Stockpiling");
                while (PBotUtils.findObjectByNames(ui, 5000, groundname) != null) {
                    if (stopThread)
                        return;
                    System.out.println("In main loop");
                    if (stop)
                        break;
                    boolean pathfind = true;
                    while (PBotUtils.getItemAtHand(ui) == null) {
                        if (stopThread)
                            return;
                        if (PBotUtils.findObjectByNames(ui, 5000, groundname) == null) {
                            PBotUtils.sysLogAppend(ui, "Out of items to stockpile, finishing.", "white");
                            stop = true;
                            break;
                        }
                        PBotUtils.sysLogAppend(ui, "Grabbing stuff.", "white");
                        Gob g = PBotUtils.findObjectByNames(ui, 5000, groundname);
                        int retry = 0;
                        if (pathfind) {
                            pathfind = false;
                            ui.gui.map.pathto(g);
                            while (g.rc.dist(ui.gui.map.player().rc) > 11) { //get within one tile of the target
                                if (stopThread)
                                    return;
                                lblProg2.settext("Moving to Pickup");
                                retry++;
                                while (PBotUtils.isMoving(ui))
                                    PBotUtils.sleep(10);//if we're moving, sleep and dont trigger unstucking
                                if (retry > 500) {
                                    retry = 0;
                                    lblProg2.settext("Unstucking");
                                    PBotUtils.sysLogAppend(ui, "Moving char to unstuck", "white");
                                    Gob player = ui.gui.map.player();
                                    Coord location = player.rc.floor(posres);
                                    int x = location.x + getrandom();
                                    int y = location.y + getrandom();
                                    Coord finalloc = new Coord(x, y);
                                    ui.gui.map.wdgmsg("click", Coord.z, finalloc, 1, 0);
                                    PBotUtils.sleep(1000);
                                    ui.gui.map.pathto(g);
                                }
                                PBotUtils.sleep(10);
                            }
                        }
                        //shift right click
                        ui.gui.map.wdgmsg("click", g.sc, g.rc.floor(posres), 3, 1, 0, (int) g.id, g.rc.floor(posres), 0, -1);
                        PBotUtils.sleep(2000);//wait 2 seconds to start moving
                        while (PBotUtils.getItemAtHand(ui) == null & PBotUtils.findObjectByNames(ui, 5000, groundname) != null && PBotUtils.isMoving(ui)) {
                            if (stopThread)
                                return;
                            System.out.println("waiting for item on  hand");
                            PBotUtils.sleep(10);
                        }
                        System.out.println("inv free slots : " + PBotUtils.invFreeSlots(ui));
                    }

                    PBotUtils.sysLogAppend(ui, "Done Grabbing stuff.", "white");
                    if (stop)
                        break;
                    while (PBotUtils.getInventoryItemsByName(ui.gui.maininv, invname.get(0)).size() != 0 && !stop) {
                        if (stopThread)
                            return;
                        System.out.println("In stockpile loop");
                        PBotUtils.sleep(1000);
                        if (PBotUtils.getItemAtHand(ui) != null)
                            PBotUtils.dropItem(ui, 0);
                        if (stockpiles.isEmpty()) {
                            System.out.println("Stockpiles empty");
                            PBotUtils.sysMsg(ui, "All chosen stockpiles full!", Color.GREEN);
                            stop = true;
                            break;
                        }

                        if (PBotUtils.stockpileIsFull(PBotUtils.findObjectById(ui, stockpiles.get(0).id))) {
                            System.out.println("Stockpile full");
                            stockpiles.remove(0);
                            continue;
                        }
                        if (stop)
                            break;
                        if (stockpiles.size() == 0) {
                            PBotUtils.sysMsg(ui, "Stockpile list now empty, stopping.", Color.white);
                            stop = true;
                            stop();
                        }
                        PBotUtils.pfRightClick(ui, stockpiles.get(0), 0);
                        int retry = 0;
                        while (ui.gui.getwnd("Stockpile") == null) {
                            if (!PBotUtils.isMoving(ui))
                                retry++;
                            if (retry > 100) {
                                if (stop)
                                    break;
                                retry = 0;
                                System.out.println("Retry : " + retry);
                                PBotUtils.sysLogAppend(ui, "Retrying stockpile interaction", "white");
                                PBotUtils.dropItem(ui, 0);
                                PBotUtils.pfRightClick(ui, stockpiles.get(0), 0);
                            }
                            PBotUtils.sleep(10);
                        }
                        PBotUtils.sleep(1000);
                        System.out.println("clicking stockpile");
                        try {
                            while (PBotUtils.getItemAtHand(ui) == null) {
                                if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                                PBotUtils.takeItem(ui, PBotUtils.getInventoryItemsByName(ui.gui.maininv, invname.get(0)).get(0).witem, 1000);
                            }
                        } catch (NullPointerException q) {
                            //break on null pointer here, bot is prob done
                            stop = true;
                            break;
                        }
                        int cnt = PBotUtils.invFreeSlots(ui);
                        try {
                            ui.gui.map.wdgmsg("itemact", Coord.z, stockpiles.get(0).rc.floor(posres), 1, 0, (int) stockpiles.get(0).id, stockpiles.get(0).rc.floor(posres), 0, -1);
                        } catch (IndexOutOfBoundsException lolindexes) {
                            PBotUtils.sysMsg(ui, "Critical error in stockpile list, stopping thread to prevent crash.", Color.white);
                            stop = true;
                            stop();
                        }
                        while (PBotUtils.invFreeSlots(ui) == cnt) {
                            if (stopThread)
                                return;
                            System.out.println("waiting for inv update");
                            PBotUtils.sleep(100);
                        }
                    }
                    if (PBotUtils.findObjectByNames(ui, 5000, groundname) == null)
                        break;
                }
                if (PBotUtils.getInventoryItemsByNames(PBotUtils.playerInventory(ui).inv, invname).size() > 0) {
                    lblProg2.settext("Finishing Stockpiling");
                    System.out.println("In stockpile loop");
                    PBotUtils.sleep(1000);
                    if (PBotUtils.getItemAtHand(ui) != null)
                        PBotUtils.dropItem(ui, 0);
                    if (stockpiles.isEmpty()) {
                        System.out.println("Stockpiles empty");
                        PBotUtils.sysMsg(ui, "All chosen stockpiles full!", Color.GREEN);
                        return;
                    }
                    if (PBotUtils.stockpileIsFull(PBotUtils.findObjectById(ui, stockpiles.get(0).id))) {
                        System.out.println("Stockpile full");
                        stockpiles.remove(0);
                    }
                    if (stockpiles.size() == 0) {
                        PBotUtils.sysMsg(ui, "Stockpile list now empty, stopping.", Color.white);
                        stop = true;
                        stop();
                    }
                    PBotUtils.pfRightClick(ui, stockpiles.get(0), 0);
                    int retry = 0;
                    while (ui.gui.getwnd("Stockpile") == null) {
                        if (stopThread)
                            return;
                        if (!PBotUtils.isMoving(ui))
                            retry++;
                        if (retry > 100) {
                            if (stop)
                                break;
                            retry = 0;
                            System.out.println("Retry : " + retry);
                            PBotUtils.sysLogAppend(ui, "Retrying stockpile interaction", "white");
                            PBotUtils.dropItem(ui, 0);
                            PBotUtils.pfRightClick(ui, stockpiles.get(0), 0);
                        }
                        PBotUtils.sleep(10);
                    }
                    PBotUtils.sleep(1000);
                    System.out.println("clicking stockpile");
                    try {
                        while (PBotUtils.getItemAtHand(ui) == null) {
                            if (stopThread)
                                return;
                            if (PBotUtils.petalExists(ui)) PBotUtils.closeFlowermenu(ui);
                            PBotUtils.takeItem(ui, PBotUtils.getInventoryItemsByName(ui.gui.maininv, invname.get(0)).get(0).witem, 1000);
                        }
                    } catch (NullPointerException q) {
                    }
                    int cnt = PBotUtils.invFreeSlots(ui);
                    try {
                        ui.gui.map.wdgmsg("itemact", Coord.z, stockpiles.get(0).rc.floor(posres), 1, 0, (int) stockpiles.get(0).id, stockpiles.get(0).rc.floor(posres), 0, -1);
                    } catch (IndexOutOfBoundsException lolindexes) {
                        PBotUtils.sysMsg(ui, "Critical error in stockpile list, stopping thread to prevent crash.", Color.white);
                        stop();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        PBotUtils.sysMsg(ui, cropName.substring(cropName.lastIndexOf("/") + 1).substring(0, 1).toUpperCase()
                + cropName.substring(cropName.lastIndexOf("/") + 1).substring(1)
                + " Farmer finished!", Color.white);
        this.destroy();
    }

    public ArrayList<Gob> Crops() {
        // Initialises list of crops to harvest between the selected coordinates
        ArrayList<Gob> gobs = new ArrayList<Gob>();
        double bigX = Math.max(rc1.x, rc2.x);
        double smallX = Math.min(rc1.x, rc2.x);
        double bigY = Math.max(rc1.y, rc2.y);
        double smallY = Math.min(rc1.y, rc2.y);
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

    public void sort(List<WItem> items) {
        Collections.sort(items, (a, b) -> {
            QBuff aq = a.item.quality();
            QBuff bq = b.item.quality();
            if (aq == null || bq == null)
                return 0;
            else if (aq.q == bq.q)
                return 0;
            else if (aq.q > bq.q)
                return -1;
            else
                return 1;
        });
    }

    public int getrandom() {
        Random r = new Random();
        int randomNumber = r.ints(1, -6000, 6000).findFirst().getAsInt();
        return randomNumber;
    }

    public void stop() {
        // Stops thread
        PBotUtils.sysMsg(ui, cropName.substring(cropName.lastIndexOf("/") + 1).substring(0, 1).toUpperCase()
                + cropName.substring(cropName.lastIndexOf("/") + 1).substring(1)
                + " Farmer stopped!", Color.white);
        ui.gui.map.wdgmsg("click", Coord.z, ui.gui.map.player().rc.floor(posres), 1, 0);
        stopThread = true;
        this.destroy();
    }

    public ArrayList<Gob> Stockpiles(Coord a, Coord b) {
        // Initialises list of crops to harvest between the selected coordinates
        ArrayList<Gob> gobs = new ArrayList<Gob>();
        double bigX = Math.max(a.x, b.x);
        double smallX = Math.min(a.x, b.x);
        double bigY = Math.max(a.y, b.y);
        double smallY = Math.min(a.y, b.y);
        synchronized (ui.sess.glob.oc) {
            for (Gob gob : ui.sess.glob.oc) {
                if (gob.rc.x <= bigX && gob.rc.x >= smallX && gob.getres() != null && gob.rc.y <= bigY
                        && gob.rc.y >= smallY && gob.getres().name.contains("stockpile")) {
                    gobs.add(gob);
                }
            }
        }
        gobs.sort(new CoordSort());
        return gobs;
    }
}
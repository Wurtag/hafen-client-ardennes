package haven.purus;

import haven.Button;
import haven.Coord;
import haven.Gob;
import haven.Label;
import haven.Loading;
import haven.MCache;
import haven.WItem;
import haven.Widget;
import haven.WidgetVerticalAppender;
import haven.Window;
import haven.automation.GobSelectCallback;
import haven.purus.pbot.PBotAPI;
import haven.purus.pbot.PBotGob;
import haven.purus.pbot.PBotUtils;

import java.awt.Color;
import java.util.ArrayList;

import static haven.OCache.posres;

public class StockpileFiller extends Window implements GobSelectCallback, ItemClickCallback {

    private ArrayList<Gob> stockpiles = new ArrayList<Gob>();
    private String invobj, terobj;

    private boolean stop = false;
    private boolean terobjCallback = false;
    private Thread selectingarea;
    private Coord a;
    private Coord b;
    private Button clearBtn, startBtn;
    private Label infoLbl = new Label("");

    public StockpileFiller() {
        super(new Coord(270, 200), "Stockpile Filler");
        final WidgetVerticalAppender appender = new WidgetVerticalAppender(this);
        PBotUtils.sysMsg(ui, "Alt + Click to select stockpiles", Color.GREEN);
        Button gobselectBtn = new Button(140, "Choose gob area") {
            @Override
            public void click() {
                PBotUtils.sysMsg("Click and Drag over 2 wide area for stockpiles", Color.WHITE);
                StockpileFiller.this.selectingarea = new Thread((Runnable) new selectingarea(), "Farming Bots");
                StockpileFiller.this.selectingarea.start();
            }
        };
//        this.add(gobselectBtn, new Coord(20, 0));

        int y = 40;
        Button invobjBtn = new Button(140, "Choose item from inventory") {
            @Override
            public void click() {
                PBotUtils.sysMsg(ui, "Click the stockpile item in inventory", Color.GREEN);
                registerItemCallback();
            }
        };
//        add(invobjBtn, new Coord(20, y));
        y += 35;
        Button terobjBtn = new Button(140, "Choose item from ground") {
            @Override
            public void click() {
                terobjCallback = true;
                PBotUtils.sysMsg(ui, "Alt + Click to select ground item", Color.GREEN);
            }
        };
//        add(terobjBtn, new Coord(20, y));
        y += 35;
        clearBtn = new Button(140, "Clear/Stop") {
            @Override
            public void click() {
                try {
                    startBtn.show();
                    stop = true;
                    if (t != null)
                        t.interrupt();
                    stockpiles = new ArrayList<Gob>();
                    PBotUtils.sysMsg(ui, "Cleared the list of selected stockpiles", Color.GREEN);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
//        add(clearBtn, new Coord(20, y));
        y += 35;
        startBtn = new Button(140, "Start") {
            @Override
            public void click() {
                this.hide();
                stop = false;
                if (stockpiles.isEmpty()) {
                    PBotUtils.sysMsg(ui, "No stockpiles chosen!", Color.GREEN);
                } else if (terobj == null) {
                    PBotUtils.sysMsg(ui, "No ground item chosen!", Color.GREEN);
                } else if (invobj == null) {
                    PBotUtils.sysMsg(ui, "No inventory item chosen!", Color.GREEN);
                } else {
                    t.start();
                }
            }
        };
//        add(startBtn, new Coord(20, y));
        y += 35;

        appender.add(gobselectBtn);
        appender.add(invobjBtn);
        appender.add(terobjBtn);
        appender.add(clearBtn);
        appender.add(startBtn);
        appender.add(infoLbl);
        pack();
    }

    Thread t = new Thread(new Runnable() {
        public void run() {
            main:
            try {
                while (PBotUtils.findObjectByNames(ui, 5000, terobj) != null) {
                    setInfo("In main loop");
                    if (stop)
                        break;
                    while (PBotUtils.getItemAtHand(ui) == null) {
                        if (stop)
                            break;
                        if (PBotUtils.findObjectByNames(ui, 5000, terobj) == null) {
                            PBotUtils.sysLogAppend(ui, "Out of items to stockpile, finishing.", "white");
                            stop = true;
                            break;
                        }
                        PBotUtils.sysLogAppend(ui, "Grabbing stuff.", "white");
                        Gob g = PBotUtils.findObjectByNames(ui, 5000, terobj);
                        PBotUtils.pfGobClick(g, 3,1);
//                        ui.gui.map.wdgmsg("click", g.sc, g.rc.floor(posres), 3, 1, 0, (int) g.id, g.rc.floor(posres), 0, -1);
                        PBotUtils.sleep(1000);

                        while (/*PBotUtils.getItemAtHand(ui) == null && PBotUtils.findObjectByNames(ui, 5000, terobj) != null && */PBotUtils.isMoving(ui)) {
//                            setInfo("waiting for item on  hand");
                            setInfo("moving");
                            PBotUtils.sleep(100);
                        }
                        setInfo("inv free slots : " + PBotUtils.invFreeSlots(ui));
                    }

                    if (PBotUtils.getItemAtHand(ui) != null) {
                        setInfo("dropping item");
                        PBotUtils.dropItem(ui, 0);
                    }

                    PBotUtils.sysLogAppend(ui, "Done Grabbing stuff.", "white");
                    if (stop)
                        break;
                    while (PBotUtils.getInventoryItemsByName(ui.gui.maininv, invobj).size() != 0 && !stop) {
                        setInfo("In stockpile loop");
                        PBotUtils.sleep(100);
                        if (PBotUtils.getItemAtHand(ui) != null)
                            PBotUtils.dropItem(ui, 0);
                        if (stockpiles.isEmpty()) {
                            setInfo("Stockpiles empty");
                            PBotUtils.sysMsg(ui, "All chosen stockpiles full!", Color.GREEN);
                            stop = true;
                            break;
                        }

                        if (PBotUtils.stockpileIsFull(PBotUtils.findObjectById(ui, stockpiles.get(0).id))) {
                            setInfo("Stockpile full");
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
                                setInfo("Retry : " + retry);
                                PBotUtils.sysLogAppend(ui, "Retrying stockpile interaction", "white");
                                PBotUtils.dropItem(ui, 0);
                                PBotUtils.pfRightClick(ui, stockpiles.get(0), 0);
                            }
                            PBotUtils.sleep(10);
                        }
                        PBotUtils.sleep(1000);
                        setInfo("clicking stockpile");
                        try {
                            while (PBotUtils.getItemAtHand(ui) == null)
                                PBotUtils.takeItem(ui, PBotUtils.getInventoryItemsByName(ui.gui.maininv, invobj).get(0).witem);
                        } catch (NullPointerException q) {
                            //break on null pointer here, bot is prob done
                            stop = true;
                            break main;
                        }
                        int cnt = PBotUtils.invFreeSlots(ui);
                        try {
                            ui.gui.map.wdgmsg("itemact", Coord.z, stockpiles.get(0).rc.floor(posres), 1, 0, (int) stockpiles.get(0).id, stockpiles.get(0).rc.floor(posres), 0, -1);
                        } catch (IndexOutOfBoundsException lolindexes) {
                            PBotUtils.sysMsg(ui, "Critical error in stockpile list, stopping thread to prevent crash.", Color.white);
                            stop = true;
                            stop();
                        }
                        while (PBotUtils.invFreeSlots(ui) == cnt && PBotUtils.getInventoryItemsByName(ui.gui.maininv, invobj).size() != 0) {
                            setInfo("waiting for inv update");
                            PBotUtils.sleep(100);
                        }
                    }
                    if (PBotUtils.findObjectByNames(ui, 5000, terobj) == null)
                        break;
                }
                PBotUtils.sysMsg(ui, "Stockpile Filler finished!", Color.GREEN);
                startBtn.show();
                reqdestroy();
            } catch (Loading | NullPointerException q) {
            }
        }
    });

    public void areaselect(Coord a, Coord b) {
        this.a = a.mul(MCache.tilesz2);
        this.b = b.mul(MCache.tilesz2).add(11, 11);
        PBotUtils.sysMsg("Area selected!", Color.WHITE);
        PBotAPI.gui.map.unregisterAreaSelect();
    }

    private class selectingarea
            implements Runnable {
        private selectingarea() {
        }

        @Override
        public void run() {
            if (StockpileFiller.this.stockpiles.size() == 0) {
                PBotUtils.sysMsg("Please select a first stockpile Alt + Click - try again.");
                return;
            }
            PBotUtils.selectArea();
            Coord aPnt = PBotUtils.getSelectedAreaA();
            Coord bPnt = PBotUtils.getSelectedAreaB();
            if (Math.abs(aPnt.x - bPnt.x) > 22 && Math.abs(aPnt.y - bPnt.y) > 22) {
                PBotUtils.sysMsg("Please select an area at least 2 tiles wide - try again.");
                return;
            }
            ArrayList<PBotGob> gobs = PBotUtils.gobsInArea(aPnt, bPnt);
            int i = 0;
            while (i < gobs.size()) {
                if (gobs.get((int) i).gob.getres().basename().equals(((Gob) StockpileFiller.this.stockpiles.get(0)).getres().basename())) {
                    StockpileFiller.this.gobselect(gobs.get((int) i).gob);
                }
                ++i;
            }
        }
    }

    private void registerItemCallback() {
        synchronized (GobSelectCallback.class) {
            ui.gui.registerItemCallback(this);
        }
    }

    @Override
    public void gobselect(Gob gob) {
        if (terobjCallback) {
            terobjCallback = false;
            terobj = gob.getres().name;
            PBotUtils.sysMsg(ui, "Ground item chosen!", Color.GREEN);
        } else if (gob.getres().basename().contains("stockpile")) {
            stockpiles.add(gob);
            PBotUtils.sysMsg(ui, "Stockpile added to list! Total stockpiles chosen: " + stockpiles.size(), Color.GREEN);
        }
        synchronized (GobSelectCallback.class) {
            ui.gui.map.registerGobSelect(this);
        }
    }

    @Override
    public void itemClick(WItem item) {
        invobj = item.item.getres().name;
        PBotUtils.sysMsg(ui, "Inventory item to put in the stockpiles chosen!", Color.GREEN);
        synchronized (ItemClickCallback.class) {
            ui.gui.unregisterItemCallback();
        }
    }

    public void setInfo(String text) {
        infoLbl.settext(text);
        System.out.println(text);
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (msg == "close") {
            stop();
            reqdestroy();
        } else
            super.wdgmsg(sender, msg, args);
    }

    public void stop() {
        t.interrupt();
        stop = true;
        synchronized (ItemClickCallback.class) {
            ui.gui.unregisterItemCallback();
        }
        synchronized (ItemClickCallback.class) {
            ui.gui.unregisterItemCallback();
        }
        reqdestroy();
        //ui.gui.map.wdgmsg("click", Coord.z, new Coord((int)BotUtils.player().rc.x, (int)BotUtils.player().rc.y), 1, 0);
    }
}
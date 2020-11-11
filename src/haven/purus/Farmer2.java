package haven.purus;

import haven.Button;
import haven.CheckBox;
import haven.Coord;
import haven.Gob;
import haven.Label;
import haven.MCache;
import haven.Widget;
import haven.WidgetVerticalAppender;
import haven.Window;
import haven.automation.AreaSelectCallback;
import haven.automation.GobSelectCallback;
import haven.purus.pbot.PBotUtils;

import java.awt.Color;

public class Farmer2 extends Window implements AreaSelectCallback, GobSelectCallback {

    private Coord a, b;
    private boolean container = false, replant = true;
    private CheckBox replantChkbox, fillContainerChkbox;
    private Gob barrel;

    public Farmer2() {
        super(new Coord(180, 615), "Farming Bots 2 Purus Version");
        final WidgetVerticalAppender appender = new WidgetVerticalAppender(this);
        int y = 0;
        Button carrotBtn = new Button(140, "Carrot") {
            @Override
            public void click() {
                if (container) {
                    PBotUtils.sysMsg(ui, "Choose replant for carrots!", Color.WHITE);
                } else if (a != null && b != null) {
                    ui.gui.map.unregisterAreaSelect();
                    // Start carrot farmer and close this window
                    SeedCropFarmer2 SCF = new SeedCropFarmer2(b, a, "gfx/terobjs/plants/carrot", "gfx/invobjs/carrot", 4, container, barrel);
                    ui.gui.add(SCF,
                            new Coord(ui.gui.sz.x / 2 - SCF.sz.x / 2, ui.gui.sz.y / 2 - SCF.sz.y / 2 - 200));
                    new Thread(SCF).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(carrotBtn, new Coord(20, y));
        y += 35;

        Button onionBtn = new Button(140, "Yellow Onion") {
            @Override
            public void click() {
                if (container) {
                    PBotUtils.sysMsg(ui, "Choose replant for onions!", Color.WHITE);
                } else if (a != null && b != null) {
                    // Start yellow onion farmer and close this window
                    SeedCropFarmer2 bf =
                            new SeedCropFarmer2(a, b, "gfx/terobjs/plants/yellowonion", "gfx/invobjs/yellowonion", 3, container, barrel);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(onionBtn, new Coord(20, y));
        y += 35;

        Button redOnionBtn = new Button(140, "Red Onion") {
            @Override
            public void click() {
                if (container) {
                    PBotUtils.sysMsg(ui, "Choose replant for onions!", Color.WHITE);
                } else if (a != null && b != null) {
                    // Start yellow onion farmer and close this window
                    SeedCropFarmer2 bf =
                            new SeedCropFarmer2(a, b, "gfx/terobjs/plants/redonion", "gfx/invobjs/redonion", 3, container, barrel);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(redOnionBtn, new Coord(20, y));
        y += 35;

        Button beetBtn = new Button(140, "Beetroot") {
            @Override
            public void click() {
                if (container) {
                    PBotUtils.sysMsg(ui, "Choose replant for beetroots!", Color.WHITE);
                } else if (a != null && b != null) {
                    // Start beetroot onion farmer and close this window
                    SeedCropFarmer2 bf = new SeedCropFarmer2(a, b, "gfx/terobjs/plants/beet", "gfx/invobjs/beet", 3, container, barrel);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(beetBtn, new Coord(20, y));
        y += 35;

        Button barleyBtn = new Button(140, "Barley") {
            @Override
            public void click() {
                if (a != null && b != null) {
                    System.out.println(a + "" + b);
                    // Start barley farmer and close this window
                    SeedCropFarmer2 bf =
                            new SeedCropFarmer2(a, b, "gfx/terobjs/plants/barley", "gfx/invobjs/seed-barley", 3, container, barrel);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(barleyBtn, new Coord(20, y));
        y += 35;

        Button wheatBtn = new Button(140, "Wheat") {
            @Override
            public void click() {
                if (a != null && b != null) {
                    // Start yellow onion farmer and close this window
                    SeedCropFarmer2 bf =
                            new SeedCropFarmer2(a, b, "gfx/terobjs/plants/wheat", "gfx/invobjs/seed-wheat", 3, container, barrel);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(wheatBtn, new Coord(20, y));
        y += 35;

        Button flaxBtn = new Button(140, "Flax") {
            @Override
            public void click() {
                if (a != null && b != null) {
                    // Start flax farmer and close this window
                    SeedCropFarmer2 bf = new SeedCropFarmer2(a, b, "gfx/terobjs/plants/flax", "gfx/invobjs/seed-flax", 3, container, barrel);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(flaxBtn, new Coord(20, y));
        y += 35;

        Button poppyBtn = new Button(140, "Poppy") {
            @Override
            public void click() {
                if (a != null && b != null) {
                    // Start poppy farmer and close this window
                    SeedCropFarmer2 bf =
                            new SeedCropFarmer2(a, b, "gfx/terobjs/plants/poppy", "gfx/invobjs/seed-poppy", 4, container, barrel);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(poppyBtn, new Coord(20, y));
        y += 35;

        Button pipeweedBtn = new Button(140, "Pipeweed") {
            @Override
            public void click() {
                if (a != null && b != null) {
                    // Start poppy farmer and close this window
                    SeedCropFarmer2 bf =
                            new SeedCropFarmer2(a, b, "gfx/terobjs/plants/pipeweed", "gfx/invobjs/seed-pipeweed", 4, container, barrel);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(pipeweedBtn, new Coord(20, y));
        y += 35;

        Button lettuceBtn = new Button(140, "Lettuce") {
            @Override
            public void click() {
                if (a != null && b != null) {
                    // Start poppy farmer and close this window
                    SeedCropFarmer2 bf =
                            new SeedCropFarmer2(a, b, "gfx/terobjs/plants/lettuce", "gfx/invobjs/seed-lettuce", 4, container, barrel);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(lettuceBtn, new Coord(20, y));
        y += 35;

        Button hempBtn = new Button(140, "Hemp") {
            @Override
            public void click() {
                if (a != null && b != null) {
                    // Start hemp farmer and close this window
                    SeedCropFarmer2 bf = new SeedCropFarmer2(a, b, "gfx/terobjs/plants/hemp", "gfx/invobjs/seed-hemp", 4, container, barrel);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
            }
        };
//        add(hempBtn, new Coord(20, y));
        y += 35;

        Button hempBudBtn = new Button(140, "Hemp-Buds") {
            @Override
            public void click() {
                if (a != null && b != null) {
                    // Start hemp farmer and close this window
                    SeedCropFarmer2 bf = new SeedCropFarmer2(a, b, "gfx/terobjs/plants/hemp", "gfx/invobjs/hemp-fresh", 3, container, barrel);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
            }
        };
//        add(hempBudBtn, new Coord(20, y));
        y += 35;

        Button trelHarBtn = new Button(140, "Trellis harvest") {
            @Override
            public void click() {
                if (a != null && b != null) {
                    // Start yellow onion farmer and close this window
                    TrellisFarmer2 bf = new TrellisFarmer2(a, b, true, false, false);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(trelHarBtn, new Coord(20, y));
        y += 35;

        Button trelDesBtn = new Button(140, "Trellis destroy") {
            @Override
            public void click() {
                if (a != null && b != null) {
                    // Start yellow onion farmer and close this window
                    TrellisFarmer2 bf = new TrellisFarmer2(a, b, false, true, false);

                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg("Area not selected!", Color.WHITE);
                }
            }
        };
//        add(trelDesBtn, new Coord(20, y));
        y += 35;

        Button trelPlantBtn = new Button(140, "Trellis plant") {
            @Override
            public void click() {
                if (a != null && b != null) {
                    // Start yellow onion farmer and close this window
                    TrellisFarmer2 bf = new TrellisFarmer2(a, b, false, false, true);
                    ui.gui.add(bf, new Coord(ui.gui.sz.x / 2 - bf.sz.x / 2, ui.gui.sz.y / 2 - bf.sz.y / 2 - 200));
                    new Thread(bf).start();
                    this.parent.destroy();
                } else {
                    PBotUtils.sysMsg(ui, "Area not selected!", Color.WHITE);
                }
            }
        };
//        add(trelPlantBtn, new Coord(20, y));
        y += 35;

        Button areaSelBtn = new Button(140, "Select Area") {
            @Override
            public void click() {
                PBotUtils.sysMsg(ui, "Drag area over crops", Color.WHITE);
                ui.gui.map.farmSelect = true;
            }
        };
//        add(areaSelBtn, new Coord(20, y));
        y += 35;
        replantChkbox = new CheckBox("Replant") {
            {
                a = replant;
            }

            public void set(boolean val) {
                a = val;
                replant = val;
                container = !val;
                fillContainerChkbox.a = !val;
            }
        };
//        add(replantChkbox, new Coord(20, y));

        fillContainerChkbox = new CheckBox("Fill container") {
            {
                a = container;
            }

            public void set(boolean val) {
                a = val;
                container = val;
                replant = !val;
                replantChkbox.a = !val;
            }
        };
//        add(fillContainerChkbox, new Coord(85, y));
        y += 15;

        Button contSelBtn = new Button(140, "Select Container") {
            @Override
            public void click() {
                PBotUtils.sysMsg(ui, "Alt + click a barrel", Color.WHITE);
                registerGobSelect();
            }
        };
//        add(contSelBtn, new Coord(20, y));
        y += 35;

        appender.addRow(areaSelBtn, contSelBtn);
        appender.addRow(replantChkbox, fillContainerChkbox);
        appender.add(new Label(""));
        appender.addRow(carrotBtn);
        appender.add(beetBtn);
        appender.addRow(onionBtn, redOnionBtn);
        appender.addRow(barleyBtn, wheatBtn);
        appender.addRow(flaxBtn, hempBtn, hempBudBtn);
        appender.add(poppyBtn);
        appender.add(pipeweedBtn);
        appender.add(lettuceBtn);
        appender.addRow(trelHarBtn, trelDesBtn, trelPlantBtn);

        pack();
    }

    private void registerGobSelect() {
        synchronized (GobSelectCallback.class) {
            ui.gui.map.registerGobSelect(this);
        }
    }

    public void areaselect(Coord a, Coord b) {
        this.a = a.mul(MCache.tilesz2);
        this.b = b.mul(MCache.tilesz2).add(11, 11);
        PBotUtils.sysMsg(ui, "Area selected!", Color.WHITE);
        ui.gui.map.unregisterAreaSelect();
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (sender == cbtn)
            reqdestroy();
        else
            super.wdgmsg(sender, msg, args);
    }

    @Override
    public void gobselect(Gob gob) {
        if (gob.getres().basename().contains("barrel")) {
            barrel = gob;
            PBotUtils.sysMsg(ui, "Barrel selected!", Color.WHITE);
        } else {
            PBotUtils.sysMsg(ui, "Please choose a barrel as a container!", Color.WHITE);
        }
        ui.gui.map.unregisterGobSelect();
    }
}

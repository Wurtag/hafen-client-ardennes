package haven;

import java.awt.Color;

public class InventoryStudy extends Inventory {
    private Tex[] histtex = null;
    private static final Color histclr = new Color(238, 238, 238, 160);

    @RName("inv-study")
    public static class $_ implements Factory {
        public Widget create(UI ui, Object[] args) {
            return new InventoryStudy((Coord) args[0]);
        }
    }

    public InventoryStudy(Coord sz) {
        super(sz);
    }

    @Override
    public void draw(GOut g) {
        if (Config.studyhist) {
            if (histtex == null) {
                histtex = new Tex[16];
                String chrid = ui.gui.chrid;
                if (chrid != "") {
                    String[] hist = Utils.getprefsa("studyhist_" + chrid, null);
                    if (hist != null) {
                        for (int i = 0; i < 16; i++) {
                            if (!hist[i].equals("null")) {
                                final int _i = i;
                                Defer.later(new Defer.Callable<Void>() {
                                    public Void call() {
                                        try {
                                            Resource res = Resource.remote().load(hist[_i]).get();
                                            histtex[_i] = res.layer(Resource.imgc).tex();
                                        } catch (Loading le) {
                                            Defer.later(this);
                                        }
                                        return null;
                                    }
                                });
                            }
                        }
                    }
                }
            }
            g.chcolor(histclr);
            for (int i = 0; i < 16; i++) {
                Tex tex = histtex[i];
                if (tex != null) {
                    try {
                        int y = i / 4 * Inventory.sqsz.y + 1;
                        int x = i % 4 * Inventory.sqsz.x + 1;
                        g.image(tex, new Coord(x, y));
                    } catch (Resource.LoadException le) {
                    }
                }
            }
            g.chcolor();
        }

        super.draw(g);
    }

    @Override
    public void addchild(Widget child, Object... args) {
        super.addchild(child, args);

        if (Config.studyhist) {
            String chrid = ui.gui.chrid;
            if (chrid != "") {
                String[] hist = Utils.getprefsa("studyhist_" + chrid, new String[16]);
                if (histtex == null) {
                    histtex = new Tex[16];
                    if (hist != null) {
                        for (int i = 0; i < 16; i++) {
                            final String resname = hist[i];
                            if (resname != null && !resname.equals("null")) {
                                final int _i = i;
                                Defer.later(new Defer.Callable<Void>() {
                                    public Void call() {
                                        try {
                                            Resource res = Resource.remote().load(resname).get();
                                            histtex[_i] = res.layer(Resource.imgc).tex();
                                        } catch (Loading le) {
                                            Defer.later(this);
                                        }
                                        return null;
                                    }
                                });
                            }
                        }
                    }
                }

                for (WItem itm : wmap.values()) {
                    int x = itm.c.x / Inventory.sqsz.x;
                    int y = itm.c.y / Inventory.sqsz.y;
                    int i = y * 4 + x;
                    try {
                        Resource res = itm.item.getres();
                        Resource.Image layer = res.layer(Resource.imgc);
                        if (layer == null)
                            continue;
                        Coord dim = layer.tex().dim;

                        int clearx = dim.x > 32 ? dim.x / 32 : 1;
                        int cleary = dim.y > 32 ? dim.y / 32 : 1;
                        for (int cx = x; cx < x + clearx; cx++) {
                            for (int cy = y; cy < y + cleary; cy++) {
                                int ci = cy * 4 + cx;
                                hist[ci] = null;
                                histtex[ci] = null;
                            }
                        }

                        hist[i] = res.name;
                        histtex[i] = res.layer(Resource.imgc).tex();
                    } catch (Loading l) {
                    }
                }
                Utils.setprefsa("studyhist_" + chrid, hist);
            }
        }

        if (Config.studybuff && getFreeSpace() == 0) {
            Buff tgl = ui.gui.buffs.gettoggle("brain");
            if (tgl != null)
                tgl.reqdestroy();
        }
    }

    @Override
    public void cdestroy(Widget w) {
        super.cdestroy(w);
        if (!(w instanceof WItem))
            return;
        GItem item = ((WItem) w).item;
        try {
            haven.resutil.Curiosity ci = ItemInfo.find(haven.resutil.Curiosity.class, item.info());
            if (ci != null && ((WItem) w).itemmeter.get() > 0.99) {
                Resource.Tooltip tt = item.resource().layer(Resource.Tooltip.class);
                if (tt != null)
                    ui.gui.syslog.append(tt.t + " LP: " + ci.exp, Color.LIGHT_GRAY);

                if (!Config.alarmstudy.equals("None"))
                    Audio.play(Resource.local().loadwait(Config.alarmstudy), Config.studyalarmvol);

                if (Config.autostudy) {
                    Window invwnd = ui.gui.getwnd("Inventory");
                    Window cupboard = ui.gui.getwnd("Cupboard");
                    Resource res = item.resource();
                    if (res != null) {
                        if (!replacecurio(invwnd, res, ((WItem) w).c) && cupboard != null)
                            replacecurio(cupboard, res, ((WItem) w).c);
                    }
                }
            }
        } catch (Loading l) {
        }

        if (Config.studybuff && getFreeSpace() > 0) {
            Buff tgl = ui.gui.buffs.gettoggle("brain");
            if (tgl == null)
                ui.gui.buffs.addchild(new Buff(Bufflist.buffbrain.indir()));
        }
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        return Config.studylock ? false : super.mousedown(c, button);
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (Config.studylock && msg.equals("invxf")) {
            return;
        } else if (Config.studylock && msg.equals("drop")) {
            Coord c = (Coord) args[0];
            for (WItem itm : wmap.values()) {
                for (int x = itm.c.x; x < itm.c.x + itm.sz.x; x += Inventory.sqsz.x) {
                    for (int y = itm.c.y; y < itm.c.y + itm.sz.y; y += Inventory.sqsz.y) {
                        if (x / Inventory.sqsz.x == c.x && y / Inventory.sqsz.y == c.y)
                            return;
                    }
                }
            }
        }
        super.wdgmsg(sender, msg, args);
    }

    private boolean replacecurio(Window wnd, Resource res, Coord c) {
        try {
            for (Widget invwdg = wnd.lchild; invwdg != null; invwdg = invwdg.prev) {
                if (invwdg instanceof Inventory) {
                    for (WItem itm : ((Inventory) invwdg).wmap.values()) {
                        GItem ngitm = itm.item;
                        Resource nres = ngitm.resource();
                        if (nres != null && nres.name.equals(res.name)) {
                            ngitm.wdgmsg("take", itm.c);
                            wdgmsg("drop", c.add(sqsz.div(2)).div(invsq.sz()));
                            return true;
                        }
                    }
                    return false;
                }
            }
        } catch (Exception e) { // ignored
        }
        return false;
    }
}

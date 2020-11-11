package haven;

import haven.res.ui.tt.q.qbuff.QBuff;

import java.awt.Color;

public class QuickSlotsWdg extends Widget implements DTarget {
    private static final Tex sbg = Resource.loadtex("gfx/hud/slots");
    public static final Coord lc = new Coord(6, 6);
    public static final Coord rc = new Coord(56, 6);
    private static final Coord ssz = new Coord(44, 44);
    private UI.Grab dragging;
    private Coord dc;
    private static final Color qualitybg = new Color(20, 20, 20, 255 - Config.qualitybgtransparency);

    public QuickSlotsWdg() {
        super(new Coord(44 + 44 + 6, 44));
    }

    @Override
    public void draw(GOut g) {
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            g.image(sbg, Coord.z);
            WItem left = e.quickslots[6];
            if (left != null) {
                drawitem(g.reclipl(lc, g.sz), left);
                drawamountbar(g, left.item, 44 + 6);
                if (Config.showquality)
                    drawQualityLeft(g, left);
            }
            WItem right = e.quickslots[7];
            if (right != null) {
                drawitem(g.reclipl(rc, g.sz), right);
                drawamountbar(g, right.item, 0);
                if (Config.showquality)
                    drawQualityRight(g, right);
            }
        }
    }

    private void drawitem(GOut g, WItem witem) {
        GItem item = witem.item;
        GSprite spr = item.spr();
        if (spr != null) {
            g.defstate();
            witem.drawmain(g, spr);
            g.defstate();
        } else {
            g.image(WItem.missing.layer(Resource.imgc).tex(), Coord.z, ssz);
        }
    }

    private void drawQualityLeft(GOut g, WItem witem) {
        QBuff quality = witem.item.quality();
        if (Config.showquality) {
            if (quality != null && quality.qtex != null) {
                Coord btm = new Coord(0, sz.y - 12);
                Tex t = Config.qualitywhole ? quality.qwtex : quality.qtex;
                if (Config.qualitybg) {
                    g.chcolor(qualitybg);
                    g.frect(btm, t.sz().add(1, -1));
                    g.chcolor();
                }
                g.image(t, btm);
            }
        }
    }

    private void drawQualityRight(GOut g, WItem witem) {
        QBuff quality = witem.item.quality();
        if (Config.showquality) {
            if (quality != null && quality.qtex != null) {
                Coord btm = new Coord(50, sz.y - 12);
                Tex t = Config.qualitywhole ? quality.qwtex : quality.qtex;
                if (Config.qualitybg) {
                    g.chcolor(qualitybg);
                    g.frect(btm, t.sz().add(1, -1));
                    g.chcolor();
                }
                g.image(t, btm);
            }
        }
    }

    public void drawamountbar(GOut g, GItem item, int offset) {
        if (item.spr() != null) {
            try {
                for (ItemInfo info : item.info()) {
                    if (info instanceof ItemInfo.Contents) {
                        ItemInfo.Contents imtcnt = (ItemInfo.Contents) info;
                        if (imtcnt.content > 0) {
                            double capacity;
                            if (item.getname().contains("Bucket"))
                                capacity = imtcnt.isseeds ? 1000D : 10.0D;
                            else
                                return;
                            double content = imtcnt.content;
                            int height = sz.y - 2;
                            int h = (int) (content / capacity * height);
                            g.chcolor(WItem.famountclr);
                            g.frect(new Coord(sz.x - 4 - offset, height - h + 1), new Coord(3, h));
                            g.chcolor();
                            return;
                        }
                    }
                }
            } catch (Exception ex) { // fail silently if info is not ready
            }
        }
    }

    @Override
    public boolean drop(Coord cc, Coord ul) {
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            e.wdgmsg("drop", cc.x <= 47 ? 6 : 7);
            return true;
        }
        return false;
    }

    @Override
    public boolean iteminteract(Coord cc, Coord ul) {
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            WItem w = e.quickslots[cc.x <= 47 ? 6 : 7];
            if (w != null) {
                return w.iteminteract(cc, ul);
            }
        }
        return false;
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        if (ui.modmeta)
            return true;
        if (ui.modctrl && button == 1 && Config.disablequickslotdrop)
            return true;
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            WItem w = e.quickslots[c.x <= 47 ? 6 : 7];
            if (w != null) {
                dragging = null;
                w.mousedown(new Coord(w.sz.x / 2, w.sz.y / 2), button);
                return true;
            } else if (button == 1) {
                dragging = ui.grabmouse(this);
                dc = c;
                return true;
            }
        }
        return false;
    }

    public void simulateclick(Coord c) {
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            WItem w = e.quickslots[c.x <= 47 ? 6 : 7];
            if (w != null)
                w.item.wdgmsg("take", new Coord(w.sz.x / 2, w.sz.y / 2));
        }
    }

    @Override
    public boolean mouseup(Coord c, int button) {
        if (dragging != null) {
            dragging.remove();
            dragging = null;
            Utils.setprefc("quickslotsc", this.c);
            return true;
        }
        return super.mouseup(c, button);
    }

    @Override
    public void mousemove(Coord c) {
        if (dragging != null) {
            this.c = this.c.add(c.x, c.y).sub(dc);
            return;
        }
        super.mousemove(c);
    }
}
/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import haven.res.ui.tt.Armor;

import java.awt.Color;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static haven.Inventory.invsq;

public class Equipory extends Widget implements DTarget {
    private static final Tex bg = Resource.loadtex("gfx/hud/equip/bg");
    private static final int rx = 34 + bg.sz().x;
    private static final int acx = 34 + bg.sz().x / 2;
    private static final Text.Foundry acf = new Text.Foundry(Text.sans, Text.cfg.def).aa(true);
    private Tex armorclass = null;
    private Tex percexp = null;
    private List<GItem> checkForDrop = new LinkedList<GItem>();
    public static final Coord ecoords[] = {
            new Coord(0, 0),
            new Coord(rx, 0),
            new Coord(0, 33),
            new Coord(rx, 33),
            new Coord(0, 66),
            new Coord(rx, 66),
            new Coord(0, 99),
            new Coord(rx, 99),
            new Coord(0, 132),
            new Coord(rx, 132),
            new Coord(0, 165),
            new Coord(rx, 165),
            new Coord(0, 198),
            new Coord(rx, 198),
            new Coord(0, 231),
            new Coord(rx, 231),
            new Coord(34, 0),
    };
    public static final Tex[] ebgs = new Tex[ecoords.length];
    public static final Text[] etts = new Text[ecoords.length];
    static Coord isz;

    static {
        isz = new Coord();
        for (Coord ec : ecoords) {
            if (ec.x + invsq.sz().x > isz.x)
                isz.x = ec.x + invsq.sz().x;
            if (ec.y + invsq.sz().y > isz.y)
                isz.y = ec.y + invsq.sz().y;
        }
        for (int i = 0; i < ebgs.length; i++) {
            Resource bgres = Resource.local().loadwait("gfx/hud/equip/ep" + i);
            Resource.Image img = bgres.layer(Resource.imgc);
            if (img != null) {
                ebgs[i] = bgres.layer(Resource.imgc).tex();
                etts[i] = Text.render(bgres.layer(Resource.tooltip).t);
            }
        }
    }


    Map<GItem, WItem[]> wmap = new HashMap<GItem, WItem[]>();
    private final Avaview ava;
    AttrBonusesWdg bonuses;
    public WItem[] quickslots = new WItem[ecoords.length];
    WItem[] slots = new WItem[ecoords.length];

    @RName("epry")
    public static class $_ implements Factory {
        public Widget create(UI ui, Object[] args) {
            long gobid;
            if (args.length < 1)
                gobid = -2;
            else if (args[0] == null)
                gobid = -1;
            else
                gobid = Utils.uint32((Integer) args[0]);
            return (new Equipory(gobid));
        }
    }

    protected void added() {
        if (ava.avagob == -2)
            ava.avagob = getparent(GameUI.class).plid;
    }

    public Equipory(long gobid) {
        super(isz);
        ava = add(new Avaview(bg.sz(), gobid, "equcam") {
            public boolean mousedown(Coord c, int button) {
                return (false);
            }

            public void draw(GOut g) {
                g.image(bg, Coord.z);
                super.draw(g);
            }

            Outlines outlines = new Outlines(true);

            protected void setup(RenderList rl) {
                super.setup(rl);
                rl.add(outlines, null);
            }

            protected java.awt.Color clearcolor() {
                return (null);
            }
        }, new Coord(34, 0));
        ava.color = null;
        bonuses = add(new AttrBonusesWdg(isz.y), isz.x + 5, 0);
        pack();
    }

    @Override
    public void tick(double dt) {
        if (Config.quickbelt && ui.beltWndId == -1 && ((Window) parent).cap.toString().equals("Equipment")) {
            for (WItem itm[] : wmap.values()) {
                try {
                    if (itm.length > 0 && itm[0].item.res.get().name.endsWith("belt"))
                        itm[0].mousedown(Coord.z, 3);
                } catch (Loading l) {
                }
            }
        }
        super.tick(dt);
        try {
            if (!checkForDrop.isEmpty()) {
                GItem g = checkForDrop.get(0);
                if (g.resource().name.equals("gfx/invobjs/leech")) {
                    g.drop = true;
                    //ui.gui.map.wdgmsg("drop", Coord.z);
                }
                checkForDrop.remove(0);
            }
        } catch (Resource.Loading ignore) {
        }
    }

    public static interface SlotInfo {
        public int slots();
    }

    public void addchild(Widget child, Object... args) {
        if (child instanceof GItem) {
            add(child);
            GItem g = (GItem) child;
            WItem[] v = new WItem[args.length];
            for (int i = 0; i < args.length; i++) {
                int ep = (Integer) args[i];
                v[i] = quickslots[ep] = slots[ep] = add(new WItem(g), ecoords[ep].add(1, 1));
                //v[i] = add(new WItem(g), ecoords[ep].add(1, 1));
                //slots[ep] = v[i];
                //quickslots[ep] = v[i];
            }
            g.sendttupdate = true;
            wmap.put(g, v);
            if (Config.leechdrop)
                checkForDrop.add(g);
            if (armorclass != null) {
                armorclass.dispose();
                armorclass = null;
            }
            if (percexp != null) {
                percexp.dispose();
                percexp = null;
            }
        } else {
            super.addchild(child, args);
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (sender instanceof GItem && wmap.containsKey(sender) && msg.equals("ttupdate")) {
            bonuses.update(slots);
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    public void cdestroy(Widget w) {
        super.cdestroy(w);
        if (w instanceof GItem) {
            GItem i = (GItem) w;
            for (WItem v : wmap.remove(i)) {
                ui.destroy(v);
                for (int s = 0; s < slots.length; s++) {
                    if (slots[s] == v)
                        slots[s] = null;
                    if (quickslots[s] == v)
                        quickslots[s] = null;
                }
            }
            if (armorclass != null) {
                armorclass.dispose();
                armorclass = null;
            }
            if (percexp != null) {
                percexp.dispose();
                percexp = null;
            }
            bonuses.update(slots);
        }
    }

    public void uimsg(String msg, Object... args) {
        if (msg == "pop") {
            ava.avadesc = Composited.Desc.decode(ui.sess, args);
        } else {
            super.uimsg(msg, args);
        }
    }

    public int epat(Coord c) {
        for (int i = 0; i < ecoords.length; i++) {
            if (c.isect(ecoords[i], invsq.sz()))
                return (i);
        }
        return (-1);
    }

    public boolean drop(Coord cc, Coord ul) {
        wdgmsg("drop", epat(cc));
        return (true);
    }

    public void drawslots(GOut g) {
        int slots = 0;
        if ((ui.gui != null) && (ui.gui.vhand != null)) {
            try {
                SlotInfo si = ItemInfo.find(SlotInfo.class, ui.gui.vhand.item.info());
                if (si != null)
                    slots = si.slots();
            } catch (Loading l) {
            }
        }
        for (int i = 0; i < 16; i++) {
            if ((slots & (1 << i)) != 0) {
                g.chcolor(255, 255, 0, 64);
                g.frect(ecoords[i].add(1, 1), invsq.sz().sub(2, 2));
                g.chcolor();
            }
            g.image(invsq, ecoords[i]);
            if (ebgs[i] != null)
                g.image(ebgs[i], ecoords[i]);
        }
    }

    public Object tooltip(Coord c, Widget prev) {
        Object tt = super.tooltip(c, prev);
        if (tt != null)
            return (tt);
        int sl = epat(c);
        if (sl >= 0)
            return (etts[sl]);
        return (null);
    }

    public void draw(GOut g) {
        drawslots(g);
        super.draw(g);
        if (armorclass == null) {
            int h = 0, s = 0;
            try {
                for (int i = 0; i < quickslots.length; i++) {
                    WItem itm = quickslots[i];
                    if (itm != null) {
                        for (ItemInfo info : itm.item.info()) {
                            if (info instanceof Armor) {
                                h += ((Armor) info).hard;
                                s += ((Armor) info).soft;
                                break;
                            }
                        }
                    }
                }
                armorclass = Text.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Armor Class: ") + h + "/" + s, Color.BLACK, acf).tex();
            } catch (Exception e) { // fail silently
            }
        }
        if (armorclass != null)
            g.image(armorclass, new Coord(acx - armorclass.sz().x / 2, bg.sz().y - armorclass.sz().y));
        if (percexp == null) {
            int h = 0, s = 0, x;
            CharWnd chrwdg = null;
            try {
                //   chrwdg = ((GameUI) parent).chrwdg;
                chrwdg = ui.gui.chrwdg;
                for (CharWnd.Attr attr : chrwdg.base) {
                    if (attr.attr.nm.contains("prc"))
                        h = attr.attr.comp;
                }
                for (CharWnd.SAttr attr : chrwdg.skill)
                    if (attr.attr.nm.contains("exp"))
                        s = attr.attr.comp;
                x = h * s;
                percexp = Text.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Perc*Exp: ") + x, Color.BLACK, acf).tex();
            } catch (Exception e) { // fail silently
            }
        }
        if (percexp != null)
            g.image(percexp, new Coord(acx - percexp.sz().x / 2, bg.sz().y - percexp.sz().y - 10));
    }

    public boolean iteminteract(Coord cc, Coord ul) {
        return (false);
    }
}

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

import haven.PUtils.BlurFurn;
import haven.PUtils.Convolution;
import haven.PUtils.Hanning;
import haven.PUtils.TexFurn;
import haven.purus.pbot.PBotUtils;
import haven.resutil.Curiosity;
import haven.resutil.FoodInfo;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;

import static haven.PUtils.blurmask2;
import static haven.PUtils.convolvedown;
import static haven.PUtils.imgblur;
import static haven.PUtils.rasterimg;
import static java.awt.Color.green;

/* XXX: There starts to seem to be reason to split the while character
 * sheet into some more modular structure, as it is growing quite
 * large. */
public class CharWnd extends Window {
    public static final RichText.Foundry ifnd = new RichText.Foundry(Resource.remote(), TextAttribute.FAMILY, Text.cfg.font.get("sans"), TextAttribute.SIZE, Text.cfg.charWndBox).aa(true);
    public static final Text.Furnace catf = new BlurFurn(new TexFurn(new Text.Foundry(Text.sans, 20).aa(true), Window.ctex), 2, 2, new Color(96, 48, 0));
    public static final Text.Furnace failf = new BlurFurn(new TexFurn(new Text.Foundry(Text.sans, 25).aa(true), Resource.loadimg("gfx/hud/fontred")), 3, 2, new Color(96, 48, 0));
    public static final Text.Foundry attrf = Text.attrf;
    public static final Text.Foundry numfnd = new Text.Foundry(Text.sans, 12);
    public static final Color debuff = new Color(255, 128, 128);
    public static final Color buff = new Color(128, 255, 128);
    public static final Color tbuff = new Color(128, 128, 255);
    public static final Color every = new Color(255, 255, 255, 16), other = new Color(255, 255, 255, 32);
    public final Collection<Attr> base;
    public final Collection<SAttr> skill;
    // public final List<Attr> base;
    // public final List<SAttr> skill;
    public final FoodMeter feps;
    public final GlutMeter glut;
    public final Constipations cons;
    public final SkillGrid skg;
    public final CredoGrid credos;
    public final ExpGrid exps;
    public final Widget woundbox;
    public static boolean abandonquest = false;
    public final WoundList wounds;
    public Wound.Info wound;
    private final Tabs.Tab questtab;
    public final Widget questbox;
    public final QuestList cqst, dqst;
    public Quest.Info quest;
    public int exp, enc;
    private int scost;
    private final Tabs.Tab sattr, fgt;
    public int level;

    public static class FoodMeter extends Widget {
        public static final Tex frame = Resource.loadtex("gfx/hud/chr/foodm");
        public static final Coord marg = new Coord(5, 5), trmg = new Coord(10, 10);
        public double cap;
        public List<El> els = new LinkedList<El>();
        private List<El> enew = null, etr = null;
        private Indir<Resource> trev = null;
        private Tex trol;
        private double trtm = 0;

        @Resource.LayerName("foodev")
        public static class Event extends Resource.Layer {
            public final Color col;
            public final String nm;
            public final String orignm;
            public final int sort;

            public Event(Resource res, Message buf) {
                res.super();
                int ver = buf.uint8();
                if (ver == 1) {
                    col = new Color(buf.uint8(), buf.uint8(), buf.uint8(), buf.uint8());
                    orignm = buf.string();
                    nm = Resource.getLocString(Resource.BUNDLE_TOOLTIP, res, orignm);
                    sort = buf.int16();
                } else {
                    throw (new Resource.LoadException("unknown foodev version: " + ver, res));
                }
            }

            public void init() {
            }
        }

        public static class El {
            public final Indir<Resource> res;
            public double a;

            public El(Indir<Resource> res, double a) {
                this.res = res;
                this.a = a;
            }

            private Event ev = null;

            public Event ev() {
                if (ev == null)
                    ev = res.get().layer(Event.class);
                return (ev);
            }
        }

        public static final Comparator<El> dcmp = new Comparator<El>() {
            public int compare(El a, El b) {
                int c;
                if ((c = (a.ev().sort - b.ev().sort)) != 0)
                    return (c);
                return (a.ev().nm.compareTo(b.ev().nm));
            }
        };

        public FoodMeter() {
            super(frame.sz());
        }

        private BufferedImage mktrol(List<El> els, Indir<Resource> trev) {
            BufferedImage buf = TexI.mkbuf(sz.add(trmg.mul(2)));
            Coord marg2 = marg.add(trmg);
            Graphics g = buf.getGraphics();
            double x = 0;
            int w = sz.x - (marg.x * 2);
            for (El el : els) {
                int l = (int) Math.floor((x / cap) * w);
                int r = (int) Math.floor(((x += el.a) / cap) * w);
                if (el.res == trev) {
                    g.setColor(Utils.blendcol(el.ev().col, Color.WHITE, 0.5));
                    g.fillRect(marg2.x - (trmg.x / 2) + l, marg2.y - (trmg.y / 2), r - l + trmg.x, sz.y - (marg.y * 2) + trmg.y);
                }
            }
            imgblur(buf.getRaster(), trmg.x, trmg.y);
            return (buf);
        }

        private void drawels(GOut g, List<El> els, int alpha) {
            double x = 0;
            int w = sz.x - (marg.x * 2);
            for (El el : els) {
                int l = (int) Math.floor((x / cap) * w);
                int r = (int) Math.floor(((x += el.a) / cap) * w);
                try {
                    Color col = el.ev().col;
                    g.chcolor(new Color(col.getRed(), col.getGreen(), col.getBlue(), alpha));
                    g.frect(new Coord(marg.x + l, marg.y), new Coord(r - l, sz.y - (marg.y * 2)));
                } catch (Loading e) {
                }
            }
        }

        public void tick(double dt) {
            if (enew != null) {
                try {
                    Collections.sort(enew, dcmp);
                    els = enew;
                    rtip = null;
                } catch (Loading l) {
                }
                enew = null;
            }
            if (trev != null) {
                try {
                    Collections.sort(etr, dcmp);
                    if (ui.gui != null)
                        ui.gui.msg("You gained " + Loading.waitfor(trev).layer(Event.class).orignm, Color.WHITE);
                    trol = new TexI(mktrol(etr, trev));
                    trtm = Utils.rtime();
                    trev = null;
                } catch (Loading l) {
                }
            }
        }

        public void draw(GOut g) {
            double d = (trtm > 0) ? (Utils.rtime() - trtm) : Double.POSITIVE_INFINITY;
            g.chcolor(0, 0, 0, 255);
            g.frect(marg, sz.sub(marg.mul(2)));
            drawels(g, els, 255);
            if (d < 1.0)
                drawels(g, etr, (int) (255 - (d * 255)));
            g.chcolor();
            g.image(frame, Coord.z);
            if (d < 2.5) {
                GOut g2 = g.reclipl(trmg.inv(), sz.add(trmg.mul(2)));
                g2.chcolor(255, 255, 255, (int) (255 - ((d * 255) * (1.0 / 2.5))));
                g2.image(trol, Coord.z);
            } else {
                trtm = 0;
            }
        }

        public void update(Object... args) {
            int n = 0;
            this.cap = (Float) args[n++];
            List<El> enew = new LinkedList<El>();
            while (n < args.length) {
                Indir<Resource> res = ui.sess.getres((Integer) args[n++]);
                double a = (Float) args[n++];
                enew.add(new El(res, a));
            }
            this.enew = enew;
        }

        public void trig(Indir<Resource> ev) {
            etr = (enew != null) ? enew : els;
            trev = ev;
        }

        private Tex rtip = null;

        public Object tooltip(Coord c, Widget prev) {
            if (rtip == null) {
                List<El> els = this.els;
                BufferedImage cur = null;
                double sum = 0.0;
                for (El el : els) {
                    Event ev = el.res.get().layer(Event.class);
                    Color col = Utils.blendcol(ev.col, Color.WHITE, 0.5);
                    BufferedImage ln = Text.render(String.format("%s: %s", ev.nm, Utils.odformat2(el.a, 2)), col).img;
                    Resource.Image icon = el.res.get().layer(Resource.imgc);
                    if (icon != null)
                        ln = ItemInfo.catimgsh(5, icon.img, ln);
                    cur = ItemInfo.catimgs(0, cur, ln);
                    sum += el.a;
                }
                cur = ItemInfo.catimgs(0, cur, Text.render(String.format(Resource.getLocString(Resource.BUNDLE_LABEL, "Total: %s/%s"), Utils.odformat2(sum, 2), Utils.odformat(cap, 2))).img);
                rtip = new TexI(cur);
            }
            return (rtip);
        }
    }

    public static class GlutMeter extends Widget {
        public static final Tex frame = Resource.loadtex("gfx/hud/chr/glutm");
        public static final Coord marg = new Coord(5, 5);
        public Color fg, bg;
        public double glut, lglut, gmod;
        public String lbl;

        private double lglut1 = 100, lglut2 = 100, lglut3 = 100;
        public long prevTime, glutTime, finishedTime;

        public GlutMeter() {
            super(frame.sz());
        }

        public void draw(GOut g) {
            Coord isz = sz.sub(marg.mul(2));
            g.chcolor(bg);
            g.frect(marg, isz);
            g.chcolor(fg);
            g.frect(marg, new Coord((int) Math.round(isz.x * (glut - Math.floor(glut))), isz.y));
            g.chcolor();
            g.image(frame, Coord.z);
        }

        public void update(Object... args) {
            int a = 0;
            this.glut = ((Number) args[a++]).doubleValue();
            this.lglut = ((Number) args[a++]).doubleValue();
            this.gmod = ((Number) args[a++]).doubleValue();
            this.lbl = Resource.getLocString(Resource.BUNDLE_LABEL, (String) args[a++]);
            this.bg = (Color) args[a++];
            this.fg = (Color) args[a++];
            if (lglut < lglut1) {
                if (lglut3 > 1) {
                    lglut3 = lglut2;
                    lglut2 = lglut1;
                    prevTime = glutTime;
                }
                lglut1 = lglut;
                glutTime = System.currentTimeMillis();
                if (lglut3 < 1) {
                    finishedTime = System.currentTimeMillis() + (long) ((lglut1) * (glutTime - prevTime) / (lglut2 - lglut1));
                }
            } else if (lglut > lglut1) {
                lglut3 = lglut2 = 100;
                lglut1 = lglut;
                glutTime = System.currentTimeMillis();
                finishedTime = -1;
            }
        }

        private Tex rtip = null;

        public Object tooltip(Coord c, Widget prev) {
            String add = "";
            if (finishedTime > System.currentTimeMillis())
                add = "\nNext level: " + Utils.timeLeft(finishedTime);
            return RichText.render(String.format("%s: %.3f%%\nFood efficacy: %d%%" + add, lbl, lglut * 100, Math.round(gmod * 100)), -1).tex();
        }
    }

    public static class Constipations extends Listbox<Constipations.El> {
        public static final Color hilit = new Color(255, 255, 0, 48);
        public static final Text.Foundry elf = attrf;
        public static final int elh = elf.height() + 2;
        public static final Convolution tflt = new Hanning(1);
        public static final Color buffed = new Color(160, 255, 160), full = new Color(250, 230, 64), none = new Color(250, 19, 43);
        public final List<El> els = new ArrayList<El>();
        private Integer[] order = {};

        public static Color color(double a) {
            return (a > 1.0) ? buffed : Utils.blendcol(none, full, a);
        }

        public class El {
            public final ResData t;
            public double a;
            private Tex tt, at;
            private BufferedImage tip;
            private boolean hl;

            public El(ResData t, double a) {
                this.t = t;
                this.a = a;
            }

            public void update(double a) {
                this.a = a;
                at = null;
            }

            public Tex tt() {
                if (tt == null) {
                    ItemSpec spec = new ItemSpec(OwnerContext.uictx.curry(ui), t, null);
                    BufferedImage img = spec.image();
                    String nm = spec.name();
                    Text rnm = elf.render(nm);
                    BufferedImage buf = TexI.mkbuf(new Coord(elh + 5 + rnm.sz().x, elh));
                    Graphics g = buf.getGraphics();
                    g.drawImage(convolvedown(img, new Coord(elh, elh), tflt), 0, 0, null);
                    g.drawImage(rnm.img, elh + 5, ((elh - rnm.sz().y) / 2) + 1, null);
                    g.dispose();
                    tt = new TexI(buf);
                }
                return (tt);
            }

            public Tex at() {
                if (at == null) {
                    Color c = (a > 1.0) ? buffed : Utils.blendcol(none, full, a);
                    at = elf.render(String.format("%d%%", (int) Math.floor(a * 100)), c).tex();
                }
                return (at);
            }
        }

        private WItem.ItemTip lasttip = null;

        public void draw(GOut g) {
            WItem.ItemTip tip = null;
            if (ui.lasttip instanceof WItem.ItemTip)
                tip = (WItem.ItemTip) ui.lasttip;
            if (tip != lasttip) {
                for (El el : els)
                    el.hl = false;
                FoodInfo finf;
                try {
                    finf = (tip == null) ? null : ItemInfo.find(FoodInfo.class, tip.item().info());
                } catch (Loading l) {
                    finf = null;
                }
                if (finf != null) {
                    for (int i = 0; i < els.size(); i++) {
                        El el = els.get(i);
                        for (int o = 0; o < finf.types.length; o++) {
                            if (finf.types[o] == i) {
                                el.hl = true;
                                break;
                            }
                        }
                    }
                }
                lasttip = tip;
            }
            super.draw(g);
        }


        public static final Comparator<El> ecmp = new Comparator<El>() {
            public int compare(El a, El b) {
                if (a.a < b.a)
                    return (-1);
                else if (a.a > b.a)
                    return (1);
                return (0);
            }
        };

        public Constipations(int w, int h) {
            super(w, h, elh);
        }

        protected void drawbg(GOut g) {
        }

        protected El listitem(int i) {
            return (els.get(order[i]));
        }

        protected int listitems() {
            return (order.length);
        }

        protected void drawitem(GOut g, El el, int idx) {
            g.chcolor(el.hl ? hilit : (((idx % 2) == 0) ? every : other));
            g.frect(Coord.z, g.sz);
            g.chcolor();
            try {
                g.image(el.tt(), Coord.z);
            } catch (Loading e) {
            }
            Tex at = el.at();
            g.image(at, new Coord(sz.x - at.sz().x - sb.sz.x, (elh - at.sz().y) / 2));
        }


        private void order() {
            int n = els.size();
            order = new Integer[n];
            for (int i = 0; i < n; i++)
                order[i] = i;
            Arrays.sort(order, new Comparator<Integer>() {
                public int compare(Integer a, Integer b) {
                    return (ecmp.compare(els.get(a), els.get(b)));
                }
            });
        }

        public void update(ResData t, double a) {
            prev:
            {
                for (Iterator<El> i = els.iterator(); i.hasNext(); ) {
                    El el = i.next();
                    if (!Utils.eq(el.t, t))
                        continue;
                    if (a == 1.0)
                        i.remove();
                    else
                        el.update(a);
                    break prev;
                }
                els.add(new El(t, a));
            }
            order();
        }


        protected void itemclick(El item, int button) {
        }
    }

    public static final int attrw = FoodMeter.frame.sz().x - wbox.bisz().x;

    public class Attr extends Widget {
        public final String nm;
        public final Text rnm;
        public final Glob.CAttr attr;
        public final Tex img;
        public final Color bg;
        public final Resource res;
        private double lvlt = 0.0;
        private Text ct;
        private int cbv, ccv;

        private Attr(Glob glob, String attr, Color bg) {
            super(new Coord(attrw, attrf.height() + 2));
            res = Resource.local().loadwait("gfx/hud/chr/" + attr);
            this.nm = attr;
            this.img = res.layer(Resource.imgc).tex();
            this.rnm = attrf.render(res.layer(Resource.tooltip).t);
            this.attr = glob.cattr.get(attr);
            this.bg = bg;
        }

        public void tick(double dt) {
            if ((attr.base != cbv) || (attr.comp != ccv)) {
                cbv = attr.base;
                ccv = attr.comp;
                Color c = Color.WHITE;
                if (ccv > cbv) {
                    c = buff;
                    tooltip = Text.render(String.format("%d + %d", cbv, ccv - cbv));
                } else if (ccv < cbv) {
                    c = debuff;
                    tooltip = Text.render(String.format("%d - %d", cbv, cbv - ccv));
                } else {
                    tooltip = null;
                }
                ct = attrf.render(Integer.toString(ccv), c);
            }
            if ((lvlt > 0.0) && ((lvlt -= dt) < 0))
                lvlt = 0.0;
        }

        public void draw(GOut g) {
            if (lvlt != 0.0)
                g.chcolor(Utils.blendcol(bg, new Color(128, 255, 128, 128), lvlt));
            else
                g.chcolor(bg);
            g.frect(Coord.z, sz);
            g.chcolor();
            Coord cn = new Coord(0, sz.y / 2);
            g.aimage(img, cn.add(5, 0), 0, 0.5);
            g.aimage(rnm.tex(), cn.add(img.sz().x + 10, 1), 0, 0.5);

            cbv = attr.base;
            ccv = attr.comp;
            if (ccv > cbv) {
                Text buffed = attrf.render(Integer.toString(ccv), buff);
                g.aimage(buffed.tex(), cn.add(sz.x - 7, 1), 1, 0.5);
            } else if (ccv < cbv) {
                Text debuffed = attrf.render(Integer.toString(ccv), debuff);
                g.aimage(debuffed.tex(), cn.add(sz.x - 7, 1), 1, 0.5);
            }

            Text base = attrf.render(Integer.toString(cbv), Color.WHITE);
            g.aimage(base.tex(), cn.add(sz.x - 50, 1), 1, 0.5);
        }

        public void lvlup() {
            lvlt = 1.0;
        }
    }

    public class SAttr extends Widget {
        public final String nm;
        public final Text rnm;
        public final Glob.CAttr attr;
        public final Tex img;
        public final Color bg;
        public final Resource res;
        public int tbv, tcv, cost;
        private Text ct;
        private int cbv, ccv;

        private SAttr(Glob glob, String attr, Color bg) {
            super(new Coord(attrw, attrf.height() + 2));
            res = Resource.local().loadwait("gfx/hud/chr/" + attr);
            this.nm = attr;
            this.img = res.layer(Resource.imgc).tex();
            this.rnm = attrf.render(res.layer(Resource.tooltip).t);
            this.attr = glob.cattr.get(attr);
            this.bg = bg;
            adda(new IButton("gfx/hud/buttons/add", "u", "d", null) {
                @Override
                public boolean mousedown(Coord c, int button) {
                    if (button == 3) {
                        adj(25);
                        return true;
                    } else
                        adj(1);
                    return true;

                }
            }, sz.x - 5, sz.y / 2, 1, 0.5);
            adda(new IButton("gfx/hud/buttons/sub", "u", "d", null) {
                public boolean mousedown(Coord c, int button) {
                    if (button == 3) {
                        adj(-25);
                        return true;
                    } else
                        adj(-1);
                    return true;

                }

                public boolean mousewheel(Coord c, int a) {
                    int b = a * Config.statgainsize;
                    adj(-b);
                    return (true);
                }
            }, sz.x - 20, sz.y / 2, 1, 0.5);
        }


        public void tick(double dt) {
            if ((attr.base != cbv) || (attr.comp != ccv)) {
                cbv = attr.base;
            }
            if (attr.comp != ccv) {
                ccv = attr.comp;
                if (tbv <= cbv) {
                    tbv = cbv;
                    tcv = ccv;
                    updcost();
                }
                Color c = Color.WHITE;
                if (ccv > cbv) {
                    c = buff;
                    tooltip = Text.render(String.format("%d + %d", cbv, ccv - cbv));
                } else if (ccv < cbv) {
                    c = debuff;
                    tooltip = Text.render(String.format("%d - %d", cbv, cbv - ccv));
                } else {
                    tooltip = null;
                }
                if (tcv > ccv)
                    c = tbuff;
                ct = attrf.render(Integer.toString(tcv), c);
                cbv = tcv;
            }
        }

        public void draw(GOut g) {
            g.chcolor(bg);
            g.frect(Coord.z, sz);
            g.chcolor();
            super.draw(g);
            Coord cn = new Coord(0, sz.y / 2);
            g.aimage(img, cn.add(5, 0), 0, 0.5);
            g.aimage(rnm.tex(), cn.add(img.sz().x + 10, 1), 0, 0.5);
            if (!Config.splitskills) {
                g.aimage(ct.tex(), cn.add(sz.x - 40, 1), 1, 0.5);
            } else {
                cbv = attr.base;
                ccv = attr.comp;

                if (ccv > cbv) {
                    if (tbv > ccv) {
                        Text buffed = attrf.render(Integer.toString(tbv + (ccv - cbv)), tbuff);
                        g.aimage(buffed.tex(), cn.add(sz.x - 35, 1), 1, 0.5);
                    } else {
                        Text buffed = attrf.render(Integer.toString(ccv), buff);
                        g.aimage(buffed.tex(), cn.add(sz.x - 35, 1), 1, 0.5);
                    }
                } else if (ccv < cbv) {
                    if (tbv > cbv) {
                        Text buffed = attrf.render(Integer.toString(tbv + (cbv - ccv)), tbuff);
                        g.aimage(buffed.tex(), cn.add(sz.x - 35, 1), 1, 0.5);
                    } else {
                        Text debuffed = attrf.render(Integer.toString(ccv), debuff);
                        g.aimage(debuffed.tex(), cn.add(sz.x - 35, 1), 1, 0.5);
                    }
                }

                if (tbv > cbv) {
                    Text base = attrf.render(Integer.toString(tbv), tbuff);
                    g.aimage(base.tex(), cn.add(sz.x - 65, 1), 1, 0.5);
                } else {
                    Text base = attrf.render(Integer.toString(cbv), Color.WHITE);
                    g.aimage(base.tex(), cn.add(sz.x - 65, 1), 1, 0.5);
                }
            }
        }

        private void updcost() {
            int cost = 100 * ((tbv + (tbv * tbv)) - (attr.base + (attr.base * attr.base))) / 2;
            scost += cost - this.cost;
            this.cost = cost;
        }

        public void adj(int a) {
            if (tbv + a < attr.base) a = attr.base - tbv;
            tbv += a;
            tcv += a;
            cbv = ccv = 0;
            updcost();
        }

        public void reset() {
            tbv = attr.base;
            tcv = attr.comp;
            cbv = ccv = 0;
            updcost();
        }

        public boolean mousewheel(Coord c, int a) {
            int b = a * Config.statgainsize;
            adj(-b);
            return (true);
        }
    }

    public static class RLabel extends Label {
        private Coord oc;

        public RLabel(Coord oc, String text) {
            super(text, numfnd);
            this.oc = oc;
        }

        protected void added() {
            this.c = oc.add(-sz.x, 0);
        }

        public void settext(String text) {
            super.settext(text);
            this.c = oc.add(-sz.x, 0);
        }
    }

    public class ExpLabel extends RLabel {
        private int cexp;

        public ExpLabel(Coord oc) {
            super(oc, "0");
            setcolor(new Color(192, 192, 255));
        }

        public void draw(GOut g) {
            super.draw(g);
            if (exp != cexp)
                settext(Utils.thformat(cexp = exp));
        }
    }

    public class EncLabel extends RLabel {
        private int cenc;

        public EncLabel(Coord oc) {
            super(oc, "0");
            setcolor(new Color(255, 255, 192));
        }

        public void draw(GOut g) {
            super.draw(g);
            if (enc != cenc)
                settext(Utils.thformat(cenc = enc));
        }
    }

    public class StudyInfo extends Widget {
        public Widget study;
        public int texp, tw, tenc, tlph;
        private final Text.UText<?> texpt = new Text.UText<Integer>(numfnd) {
            public Integer value() {
                return (texp);
            }

            public String text(Integer v) {
                return (Utils.thformat(v));
            }
        };
        private final Text.UText<?> twt = new Text.UText<String>(numfnd) {
            public String value() {
                return (tw + "/" + ui.sess.glob.cattr.get("int").comp);
            }
        };
        private final Text.UText<?> tenct = new Text.UText<Integer>(numfnd) {
            public Integer value() {
                return (tenc);
            }

            public String text(Integer v) {
                return (Integer.toString(tenc));
            }
        };
        private final Text.UText<?> tlpht = new Text.UText<Integer>(numfnd) {
            public Integer value() {
                return (tlph);
            }

            public String text(Integer v) {
                return (Integer.toString(tlph));
            }
        };

        private StudyInfo(Coord sz, Widget study) {
            super(sz);
            this.study = study;
            add(new Label("Attention:"), 2, 2);
            add(new Label("Experience cost:"), 2, 32);
            add(new Label("LP/hour"), 2, sz.y - 64);
            add(new Label("Learning points:"), 2, sz.y - 32);

            if (Config.studybuff && ((Inventory) study).getFreeSpace() > 0) {
                Buff tgl = study.ui.gui.buffs.gettoggle("brain");
                if (tgl == null)
                    study.ui.gui.buffs.addchild(new Buff(Bufflist.buffbrain.indir()));
            }
        }

        private void upd() {
            int texp = 0, tw = 0, tenc = 0;
            double tlph = 0;
            for (GItem item : study.children(GItem.class)) {
                try {
                    Curiosity ci = ItemInfo.find(Curiosity.class, item.info());
                    if (ci != null) {
                        texp += ci.exp;
                        tw += ci.mw;
                        tenc += ci.enc;
                        tlph += (ci.exp / (ci.time / 60));
                    }
                } catch (Loading l) {
                }
            }
            this.texp = texp;
            this.tw = tw;
            this.tenc = tenc;
            this.tlph = (int) Math.round(tlph);
        }

        public void draw(GOut g) {
            upd();
            super.draw(g);
            g.chcolor(255, 192, 255, 255);
            g.aimage(twt.get().tex(), new Coord(sz.x - 4, 17), 1.0, 0.0);
            g.chcolor(255, 255, 192, 255);
            g.aimage(tenct.get().tex(), new Coord(sz.x - 4, 47), 1.0, 0.0);
            g.chcolor(192, 192, 255, 255);
            g.aimage(texpt.get().tex(), sz.add(-4, -15), 1.0, 0.0);
            g.chcolor(192, 192, 255, 255);
            g.aimage(tlpht.get().tex(), sz.add(-4, -49), 1.0, 0.0);
        }
    }

    public static class LoadingTextBox extends RichTextBox {
        private Indir<String> text = null;

        public LoadingTextBox(Coord sz, String text, RichText.Foundry fnd) {
            super(sz, text, fnd);
        }

        public LoadingTextBox(Coord sz, String text, Object... attrs) {
            super(sz, text, attrs);
        }

        public void settext(Indir<String> text) {
            this.text = text;
        }

        public void draw(GOut g) {
            if (text != null) {
                try {
                    settext(text.get());
                    text = null;
                } catch (Loading l) {
                }
            }
            super.draw(g);
        }
    }

    public static final PUtils.Convolution iconfilter = new PUtils.Lanczos(3);

    public class Skill {
        public final String nm;
        public final Indir<Resource> res;
        public final int cost;
        public boolean has = false;
        private String sortkey;
        private Tex small;
        private final Text.UText<?> rnm = new Text.UText<String>(attrf) {
            public String value() {
                try {
                    return (res.get().layer(Resource.tooltip).t);
                } catch (Loading l) {
                    return ("...");
                }
            }
        };

        private Skill(String nm, Indir<Resource> res, int cost, boolean has) {
            this.nm = nm;
            this.res = res;
            this.cost = cost;
            this.has = has;
            this.sortkey = nm;
        }

        public String rendertext() {
            StringBuilder buf = new StringBuilder();
            Resource res = this.res.get();
            buf.append("$img[" + res.name + "]\n\n");
            buf.append("$b{$font[serif,16]{" + res.layer(Resource.tooltip).t + "}}\n\n\n");
            if (cost > 0)
                buf.append("Cost: " + cost + "\n\n");
            buf.append(res.layer(Resource.pagina).text);
            return (buf.toString());
        }

        private Text tooltip = null;

        public Text tooltip() {
            if (tooltip == null)
                tooltip = Text.render(res.get().layer(Resource.tooltip).t);
            return (tooltip);
        }
    }

    public class Credo {
        public final String nm;
        public final Indir<Resource> res;
        public boolean has = false;
        private String sortkey;
        private Tex small;

        private Credo(String nm, Indir<Resource> res, boolean has) {
            this.nm = nm;
            this.res = res;
            this.has = has;
            this.sortkey = nm;
        }

        public String rendertext() {
            StringBuilder buf = new StringBuilder();
            Resource res = this.res.get();
            buf.append("$img[" + res.name + "]\n\n");
            buf.append("$b{$font[serif,16]{" + res.layer(Resource.tooltip).t + "}}\n\n\n");
            buf.append(res.layer(Resource.pagina).text);
            return (buf.toString());
        }

        private Text tooltip = null;

        public Text tooltip() {
            if (tooltip == null)
                tooltip = Text.render(res.get().layer(Resource.tooltip).t);
            return (tooltip);
        }
    }

    public class Experience {
        public final Indir<Resource> res;
        public final int mtime, score;
        private String sortkey = "\uffff";
        private Tex small;
        private final Text.UText<?> rnm = new Text.UText<String>(attrf) {
            public String value() {
                try {
                    return (res.get().layer(Resource.tooltip).t);
                } catch (Loading l) {
                    return ("...");
                }
            }
        };

        private Experience(Indir<Resource> res, int mtime, int score) {
            this.res = res;
            this.mtime = mtime;
            this.score = score;
        }

        public String rendertext() {
            StringBuilder buf = new StringBuilder();
            Resource res = this.res.get();
            buf.append("$img[" + res.name + "]\n\n");
            buf.append("$b{$font[serif,16]{" + res.layer(Resource.tooltip).t + "}}\n\n\n");
            if (score > 0)
                buf.append(Resource.getLocString(Resource.BUNDLE_LABEL, "Experience points: ") + Utils.thformat(score) + "\n\n");
            buf.append(res.layer(Resource.pagina).text);
            return (buf.toString());
        }

        private Text tooltip = null;

        public Text tooltip() {
            if (tooltip == null)
                tooltip = Text.render(res.get().layer(Resource.tooltip).t);
            return (tooltip);
        }
    }

    public static class Wound {
        public final int id, parentid;
        public Indir<Resource> res;
        public Object qdata;
        public int level;
        private String sortkey = "\uffff";
        private Tex small;
        private int namew;
        private final Text.UText<?> rnm = new Text.UText<String>(attrf) {
            public String value() {
                try {
                    return (res.get().layer(Resource.tooltip).t);
                } catch (Loading l) {
                    return ("...");
                }
            }

            public Text render(String text) {
                Text.Foundry fnd = (Text.Foundry) this.fnd;
                Text.Line full = fnd.render(text);
                if (full.sz().x <= namew)
                    return (full);
                int ew = fnd.strsize("...").x;
                for (int i = full.text.length() - 1; i > 0; i--) {
                    if ((full.advance(i) + ew) < namew)
                        return (fnd.render(text.substring(0, i) + "..."));
                }
                return (full);
            }

	    /*
	    public Text render(String text) {
		Text.Foundry fnd = (Text.Foundry)this.fnd;
		Text.Line ret = fnd.render(text);
		while(ret.sz().x > namew) {
		    fnd = new Text.Foundry(fnd.font, fnd.font.getSize() - 1, fnd.defcol).aa(true);
		    ret = fnd.render(text);
		}
		return(ret);
	    }
	    */
        };
        private final Text.UText<?> rqd = new Text.UText<Object>(attrf) {
            public Object value() {
                return (qdata);
            }
        };

        private Wound(int id, Indir<Resource> res, Object qdata, int parentid) {
            this.id = id;
            this.res = res;
            this.qdata = qdata;
            this.parentid = parentid;
        }

        public static class Box extends LoadingTextBox implements Info {
            public final int id;
            public final Indir<Resource> res;

            public Box(int id, Indir<Resource> res) {
                super(Coord.z, "", ifnd);
                bg = null;
                this.id = id;
                this.res = res;
                settext(new Indir<String>() {
                    public String get() {
                        return (rendertext());
                    }
                });
            }

            protected void added() {
                resize(parent.sz);
            }

            public String rendertext() {
                StringBuilder buf = new StringBuilder();
                Resource res = this.res.get();
                buf.append("$img[" + res.name + "]\n\n");
                buf.append("$b{$font[serif,16]{" + res.layer(Resource.tooltip).t + "}}\n\n\n");
                buf.append(res.layer(Resource.pagina).text);
                if (Config.cures.containsKey(res.name)) {
                    buf.append(Resource.getLocString(Resource.BUNDLE_LABEL, "\n\nTreated with:\n"));
                    for (String c : Config.cures.get(res.name)) {
                        buf.append("$img[" + c + "]");
                        buf.append(Resource.remote().load(c).get().layer(Resource.tooltip).t + "\n");
                    }
                }
                return (buf.toString());
            }

            public int woundid() {
                return (id);
            }
        }

        @RName("wound")
        public static class $wound implements Factory {
            public Widget create(UI ui, Object[] args) {
                int id = (Integer) args[0];
                Indir<Resource> res = ui.sess.getres((Integer) args[1]);
                return (new Box(id, res));
            }
        }

        public interface Info {
            public int woundid();
        }
    }

    public static class Quest {
        public static final int QST_PEND = 0, QST_DONE = 1, QST_FAIL = 2, QST_DISABLED = 3;
        public static final Color[] stcol = {
                new Color(255, 255, 64), new Color(64, 255, 64), new Color(255, 64, 64),
        };
        public static final char[] stsym = {'\u2022', '\u2713', '\u2717'};
        public final int id;
        public Indir<Resource> res;
        public String title;
        public int done;
        public int mtime;
        private Tex small;
        private final Text.UText<?> rnm = new Text.UText<String>(attrf) {
            public String value() {
                try {
                    return (title());
                } catch (Loading l) {
                    return ("...");
                }
            }
        };

        private Quest(int id, Indir<Resource> res, String title, int done, int mtime) {
            this.id = id;
            this.res = res;
            this.title = Resource.getLocString(Resource.BUNDLE_LABEL, title);
            this.done = done;
            this.mtime = mtime;
        }

        public String title() {
            if (title != null)
                return (title);
            return (res.get().layer(Resource.tooltip).t);
        }

        public static class Condition {
            public final String desc;
            public int done;
            public String status;
            public Object[] wdata = null;

            public Condition(String desc, int done, String status) {
                this.desc = Resource.getLocString(Resource.BUNDLE_LABEL, desc);
                this.done = done;
                this.status = Resource.getLocString(Resource.BUNDLE_LABEL, status);
            }
        }

        private static final Tex qcmp = catf.render("Quest completed").tex();
        private static final Tex qfail = failf.render("Quest failed").tex();

        public void done(GameUI parent) {
            parent.add(new Widget() {
                double a = 0.0;
                Tex img, title, msg;

                public void draw(GOut g) {
                    if (img != null) {
                        if (a < 0.2)
                            g.chcolor(255, 255, 255, (int) (255 * Utils.smoothstep(a / 0.2)));
                        else if (a > 0.8)
                            g.chcolor(255, 255, 255, (int) (255 * Utils.smoothstep(1.0 - ((a - 0.8) / 0.2))));
                        /*
                        g.image(img, new Coord(0, (Math.max(img.sz().y, title.sz().y) - img.sz().y) / 2));
                        g.image(title, new Coord(img.sz().x + 25, (Math.max(img.sz().y, title.sz().y) - title.sz().y) / 2));
                        g.image(msg, new Coord((sz.x - qcmsgmp.sz().x) / 2, Math.max(img.sz().y, title.sz().y) + 25));
                        */
                        int y = 0;
                        g.image(img, new Coord((sz.x - img.sz().x) / 2, y));
                        y += img.sz().y + 15;
                        g.image(title, new Coord((sz.x - title.sz().x) / 2, y));
                        y += title.sz().y + 15;
                        g.image(msg, new Coord((sz.x - msg.sz().x) / 2, y));
                    }
                }

                public void tick(double dt) {
                    if (img == null) {
                        try {
                            title = (done == QST_DONE ? catf : failf).render(title()).tex();
                            img = res.get().layer(Resource.imgc).tex();
                            msg = (done == QST_DONE) ? qcmp : qfail;
                            /*
                            resize(new Coord(Math.max(img.sz().x + 25 + title.sz().x, msg.sz().x),
                                     Math.max(img.sz().y, title.sz().y) + 25 + msg.sz().y));
                            */
                            resize(new Coord(Math.max(Math.max(img.sz().x, title.sz().x), msg.sz().x),
                                    img.sz().y + 15 + title.sz().y + 15 + msg.sz().y));
                            presize();
                        } catch (Loading l) {
                            return;
                        }
                    }
                    if ((a += (dt * 0.2)) > 1.0)
                        destroy();
                }

                public void presize() {
                    c = parent.sz.sub(sz).div(2);
                }

                protected void added() {
                    presize();
                }
            });
        }

        public abstract static class CondWidget extends Widget {
            public final Condition cond;

            public CondWidget(Condition cond) {
                this.cond = cond;
            }

            public boolean update() {
                return (false);
            }
        }

        public static class DefaultCond extends CondWidget {
            public Text text;

            public DefaultCond(Condition cond) {
                super(cond);
            }

            @Deprecated
            public DefaultCond(Widget parent, Condition cond) {
                super(cond);
            }

            protected void added() {
                super.added();
                StringBuilder buf = new StringBuilder();
                buf.append(String.format("%s{%c %s", RichText.Parser.col2a(stcol[cond.done]), stsym[cond.done], cond.desc));
                if (cond.status != null) {
                    buf.append(' ');
                    buf.append(cond.status);
                }
                buf.append("}");
                text = ifnd.render(buf.toString(), parent.sz.x - 20);
                resize(text.sz().add(15, 1));
            }

            public void draw(GOut g) {
                g.image(text.tex(), new Coord(15, 0));
            }
        }

        public static class Box extends Widget implements Info, QView.QVInfo {
            public final int id;
            public final Indir<Resource> res;
            public final String title;
            public Condition[] cond = {};
            private QView cqv;

            public Box(int id, Indir<Resource> res, String title) {
                super(Coord.z);
                this.id = id;
                this.res = res;
                this.title = Resource.getLocString(Resource.BUNDLE_LABEL, title);
            }

            public int id() {
                return this.id;
            }

            public String title() {
                if (title != null)
                    return (title);
                return (res.get().layer(Resource.tooltip).t);
            }

            protected void added() {
                resize(parent.sz);
            }

            public Condition[] conds() {
                return (cond);
            }

            private CharWnd cw = null;

            public int done() {
                if (cw == null)
                    cw = getparent(CharWnd.class);
                if (cw == null)
                    return (Quest.QST_PEND);
                Quest qst;
                if ((qst = cw.cqst.get(id)) != null)
                    return (qst.done);
                if ((qst = cw.dqst.get(id)) != null)
                    return (qst.done);
                return (Quest.QST_PEND);
            }

            public void refresh() {
            }

            public String rendertext() {
                StringBuilder buf = new StringBuilder();
                Resource res = this.res.get();
                buf.append("$img[" + res.name + "]\n\n");
                buf.append("$b{$font[serif,16]{" + title() + "}}\n\n");
                Resource.Pagina pag = res.layer(Resource.pagina);
                if ((pag != null) && !pag.text.equals("")) {
                    buf.append("\n");
                    buf.append(pag.text);
                    buf.append("\n");
                }
                return (buf.toString());
            }

            public Condition findcond(String desc) {
                for (Condition cond : this.cond) {
                    if (cond.desc.equals(desc))
                        return (cond);
                }
                return (null);
            }

            public void uimsg(String msg, Object... args) {
                if (msg == "conds") {
                    int a = 0;
                    List<Condition> ncond = new ArrayList<Condition>(args.length);
                    while (a < args.length) {
                        String desc = (String) args[a++];
                        int st = (Integer) args[a++];
                        String status = (String) args[a++];
                        Object[] wdata = null;
                        if ((a < args.length) && (args[a] instanceof Object[]))
                            wdata = (Object[]) args[a++];
                        Condition cond = findcond(desc);
                        if (cond != null) {
                            boolean ch = false;
                            if (st != cond.done) {
                                cond.done = st;
                                ch = true;
                            }
                            if (!Utils.eq(status, cond.status)) {
                                cond.status = status;
                                ch = true;
                            }
                            if (!Arrays.equals(wdata, cond.wdata)) {
                                cond.wdata = wdata;
                                ch = true;
                            }
                            if (ch && (cqv != null))
                                cqv.update(cond);
                        } else {
                            cond = new Condition(desc, st, status);
                            cond.wdata = wdata;
                        }
                        ncond.add(cond);
                    }
                    this.cond = ncond.toArray(new Condition[0]);
                    this.ui.gui.questhelper.addConds(ncond, this.cqv.info.id());
                    refresh();
                    if (cqv != null)
                        cqv.update();
                } else {
                    super.uimsg(msg, args);
                }
            }

            public void destroy() {
                super.destroy();
                if (cqv != null)
                    cqv.reqdestroy();
            }


            public int questid() {
                return (id);
            }

            public Widget qview() {
                return (cqv = new QView(this));
            }
        }

        public static class QView extends Widget {
            public static final Text.Furnace qtfnd = new BlurFurn(new Text.Foundry(Text.serif.deriveFont(java.awt.Font.BOLD, 16)).aa(true), 2, 1, Color.BLACK);
            public static final Text.Foundry qcfnd = new Text.Foundry(Text.sans, 12).aa(true);
            public final QVInfo info;
            private Condition[] ccond;
            private Tex[] rcond = {};
            private Tex rtitle = null;
            private Tex glow, glowon;
            private double glowt = -1;

            public interface QVInfo {
                public String title();

                int id();

                public Condition[] conds();

                public int done();
            }

            public QView(QVInfo info) {
                this.info = info;
            }

            private void resize() {
                Coord sz = new Coord(0, 0);
                if (rtitle != null) {
                    sz.y += rtitle.sz().y + 5;
                    sz.x = Math.max(sz.x, rtitle.sz().x);
                }
                for (Tex c : rcond) {
                    sz.y += c.sz().y;
                    sz.x = Math.max(sz.x, c.sz().x);
                }
                sz.x += 3;
                resize(sz);
            }

            public void draw(GOut g) {
                int y = 0;
                if (rtitle != null) {
                    if (rootxlate(ui.mc).isect(Coord.z, rtitle.sz()))
                        g.chcolor(192, 192, 255, 255);
                    else if (info.done() == QST_DISABLED)
                        g.chcolor(255, 128, 0, 255);
                    g.image(rtitle, new Coord(3, y));
                    g.chcolor();
                    y += rtitle.sz().y + 5;
                }
                for (Tex c : rcond) {
                    g.image(c, new Coord(3, y));
                    if (c == glowon) {
                        double a = (1.0 - Math.pow(Math.cos(glowt * 2 * Math.PI), 2));
                        g.chcolor(255, 255, 255, (int) (128 * a));
                        g.image(glow, new Coord(0, y - 3));
                        g.chcolor();
                    }
                    y += c.sz().y;
                }
            }

            public boolean mousedown(Coord c, int btn) {
                if ((rtitle != null) && c.isect(Coord.z, rtitle.sz())) {
                    CharWnd cw = getparent(GameUI.class).chrwdg;
                    cw.show();
                    cw.raise();
                    cw.parent.setfocus(cw);
                    cw.questtab.showtab();
                    return (true);
                }
                return (super.mousedown(c, btn));
            }

            public void tick(double dt) {
                if (rtitle == null) {
                    try {
                        rtitle = qtfnd.render(info.title()).tex();
                        resize();
                    } catch (Loading l) {
                    }
                }
                if (glowt >= 0) {
                    if ((glowt += (dt * 0.5)) > 1.0) {
                        glowt = -1;
                        glow = glowon = null;
                    }
                }
            }

            private Text ct(Condition c) {
                return (qcfnd.render(" " + stsym[c.done] + " " + c.desc + ((c.status != null) ? (" " + c.status) : ""), stcol[c.done]));
            }

            void update() {
                Condition[] cond = info.conds();
                Tex[] rcond = new Tex[cond.length];
                for (int i = 0; i < cond.length; i++) {
                    Condition c = cond[i];
                    BufferedImage text = ct(c).img;
                    rcond[i] = new TexI(rasterimg(blurmask2(text.getRaster(), 1, 1, Color.BLACK)));
                }
                if (glowon != null) {
                    for (int i = 0; i < this.rcond.length; i++) {
                        if (this.rcond[i] == glowon) {
                            for (int o = 0; o < cond.length; o++) {
                                if (cond[o] == this.ccond[i]) {
                                    glowon = rcond[o];
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
                this.ccond = cond;
                this.rcond = rcond;
                resize();
            }

            void update(Condition c) {
                glow = new TexI(rasterimg(blurmask2(ct(c).img.getRaster(), 3, 2, stcol[c.done])));
                for (int i = 0; i < ccond.length; i++) {
                    if (ccond[i] == c) {
                        glowon = rcond[i];
                        break;
                    }
                }
                glowt = 0.0;
            }
        }

        public static class DefaultBox extends Box {
            private Widget current;
            private boolean refresh = true;
            public List<Pair<String, String>> options = Collections.emptyList();
            public CondWidget[] condw = {};

            public DefaultBox(int id, Indir<Resource> res, String title) {
                super(id, res, title);
            }

            protected void layouth(Widget cont) {
                RichText text = ifnd.render(rendertext(), cont.sz.x - 20);
                cont.add(new Img(text.tex()), new Coord(10, 10));
            }

            protected void layoutc(Widget cont) {
                int y = cont.contentsz().y + 10;
                CondWidget[] nw = new CondWidget[cond.length];
                CondWidget[] pw = condw;
                cond:
                for (int i = 0; i < cond.length; i++) {
                    for (int o = 0; o < pw.length; o++) {
                        if ((pw[o] != null) && (pw[o].cond == cond[i])) {
                            if (pw[o].update()) {
                                pw[o].unlink();
                                nw[i] = cont.add(pw[o], new Coord(0, y));
                                y += nw[i].sz.y;
                                pw[o] = null;
                                continue cond;
                            }
                        }
                    }
                    if (cond[i].wdata != null) {
                        Indir<Resource> wres = ui.sess.getres((Integer) cond[i].wdata[0]);
                        nw[i] = (CondWidget) wres.get().getcode(Widget.Factory.class, true).create(ui, new Object[]{cond[i]});
                    } else {
                        nw[i] = new DefaultCond(cont, cond[i]);
                    }
                    y += cont.add(nw[i], new Coord(10, y)).sz.y;
                }
                condw = nw;
            }

            protected void layouto(Widget cont) {
                int y = cont.contentsz().y + 10;
                for (Pair<String, String> opt : options) {
                    y += cont.add(new Button(cont.sz.x - 20, opt.b, false) {
                        public void click() {
                            DefaultBox.this.wdgmsg("opt", opt.a);
                        }
                    }, new Coord(10, y)).sz.y + 5;
                }
            }

            protected void layout(Widget cont) {
                layouth(cont);
                layoutc(cont);
                layouto(cont);
            }

            public void draw(GOut g) {
                refresh:
                if (refresh) {
                    Scrollport newch = new Scrollport(sz);
                    try {
                        layout(newch.cont);
                    } catch (Loading l) {
                        break refresh;
                    }
                    if (current != null)
                        current.destroy();
                    current = add(newch, Coord.z);
                    refresh = false;
                }
                super.draw(g);
            }

            public void refresh() {
                refresh = true;
            }

            public void uimsg(String msg, Object... args) {
                // for(Object obj : args) System.out.println("msg : "+msg+" arg : "+obj);

                if (msg == "opts") {
                    List<Pair<String, String>> opts = new ArrayList<>();
                    for (int i = 0; i < args.length; i += 2)
                        opts.add(new Pair<>((String) args[i], (String) args[i + 1]));
                    this.options = opts;
                    refresh();
                } else {
                    super.uimsg(msg, args);
                }
            }
        }

        @RName("quest")
        public static class $quest implements Factory {
            public Widget create(UI ui, Object[] args) {
                int id = (Integer) args[0];
                Indir<Resource> res = ui.sess.getres((Integer) args[1]);
                String title = (args.length > 2) ? (String) args[2] : null;
                return (new DefaultBox(id, res, title));
            }
        }

        public interface Info {
            public int questid();

            public Widget qview();
        }
    }

    public class SkillGrid extends GridList<Skill> {
        public final Group nsk, csk;
        private boolean loading = false;

        public SkillGrid(Coord sz) {
            super(sz);
            nsk = new Group(new Coord(40, 40), new Coord(-1, 5), Resource.getLocString(Resource.BUNDLE_LABEL, "Available Skills"), Collections.emptyList());
            csk = new Group(new Coord(40, 40), new Coord(-1, 5), Resource.getLocString(Resource.BUNDLE_LABEL, "Known Skills"), Collections.emptyList());
            itemtooltip = Skill::tooltip;
        }

        protected void drawitem(GOut g, Skill sk) {
            if (sk.small == null)
                sk.small = new TexI(convolvedown(sk.res.get().layer(Resource.imgc).img, new Coord(40, 40), iconfilter));
            g.image(sk.small, Coord.z);
        }

        protected void update() {
            super.update();
            loading = true;
        }

        private void sksort(List<Skill> skills) {
            for (Skill sk : skills) {
                try {
                    sk.sortkey = sk.res.get().layer(Resource.tooltip).t;
                } catch (Loading l) {
                    sk.sortkey = sk.nm;
                    loading = true;
                }
            }
            Collections.sort(skills, (a, b) -> a.sortkey.compareTo(b.sortkey));
        }

        public void tick(double dt) {
            super.tick(dt);
            if (loading) {
                loading = false;
                sksort(nsk.items);
                sksort(csk.items);
            }
        }
    }

    public class CredoGrid extends Scrollport {
        public final Coord crsz = new Coord(70, 88);
        public final Tex credoufr = new TexI(convolvedown(Resource.loadimg("gfx/hud/chr/yrkirframe"), crsz, iconfilter));
        public final Tex credosfr = new TexI(convolvedown(Resource.loadimg("gfx/hud/chr/yrkirsframe"), crsz, iconfilter));
        public final Text.Foundry prsf = Text.std;
        public List<Credo> ncr = Collections.emptyList(), ccr = Collections.emptyList();
        public Credo pcr = null;
        public int pcl, pclt, pcql, pcqlt, pqid, cost;
        public Credo sel = null;
        private final Img pcrc, ncrc, ccrc;
        private final Button pbtn, qbtn;
        private boolean loading = false;

        public CredoGrid(Coord sz) {
            super(sz);
            pcrc = new Img(GridList.dcatf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Pursuing")).tex());
            ncrc = new Img(GridList.dcatf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Credos Available")).tex());
            ccrc = new Img(GridList.dcatf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Credos Acquired")).tex());
            pbtn = new Button(100, "Pursue", false) {
                public void click() {
                    if (sel != null)
                        CharWnd.this.wdgmsg("crpursue", sel.nm);
                }
            };
            qbtn = new Button(100, "Show quest", false) {
                public void click() {
                    CharWnd.this.wdgmsg("qsel", pqid);
                    questtab.showtab();
                }
            };
        }

        private Tex crtex(Credo cr) {
            if (cr.small == null)
                cr.small = new TexI(convolvedown(cr.res.get().layer(Resource.imgc).img, crsz, iconfilter));
            return (cr.small);
        }

        private class CredoImg extends Img {
            private final Credo cr;

            CredoImg(Credo cr) {
                super(crtex(cr));
                this.cr = cr;
                this.tooltip = Text.render(cr.res.get().layer(Resource.tooltip).t);
            }

            public void draw(GOut g) {
                super.draw(g);
                g.image((cr == sel) ? credosfr : credoufr, Coord.z);
            }

            public boolean mousedown(Coord c, int button) {
                if (button == 1) {
                    change(cr);
                }
                return (true);
            }
        }

        private int crgrid(int y, Collection<Credo> crs) {
            int col = 0;
            for (Credo cr : crs) {
                if (col >= 3) {
                    col = 0;
                    y += crsz.y + 5;
                }
                cont.add(new CredoImg(cr), col * (crsz.x + 5) + 5, y);
                col++;
            }
            return (y + crsz.y + 5);
        }

        private void sort(List<Credo> buf) {
            Collections.sort(buf, Comparator.comparing(cr -> cr.res.get().layer(Resource.tooltip).t));
        }

        private void update() {
            sort(ccr);
            sort(ncr);
            for (Widget ch = cont.child; ch != null; ch = cont.child)
                ch.destroy();
            int y = 0;
            if (pcr != null) {
                cont.add(pcrc, 5, y);
                y += pcrc.sz.y + 5;
                Widget pcrim = cont.add(new CredoImg(pcr), 5, y);
                cont.add(new Label(String.format(Resource.getLocString(Resource.BUNDLE_LABEL, "Level: %d/%d"), pcl, pclt), prsf), pcrim.c.x + pcrim.sz.x + 5, y);
                cont.add(new Label(String.format(Resource.getLocString(Resource.BUNDLE_LABEL, "Quest: %d/%d"), pcql, pcqlt), prsf), pcrim.c.x + pcrim.sz.x + 5, y + 20);
                cont.adda(qbtn, pcrim.c.x + pcrim.sz.x + 5, y + pcrim.sz.y, 0, 1);
                y += pcrim.sz.y;
                y += 10;
            }

            if (ncr.size() > 0) {
                cont.add(ncrc, 5, y);
                y += ncrc.sz.y + 5;
                y = crgrid(y, ncr);
                if (pcr == null) {
                    cont.add(pbtn, 5, y);
                    if (cost > 0)
                        cont.adda(new Label(String.format("Cost: %,d LP", cost)), pbtn.c.x + pbtn.sz.x + 10, pbtn.c.y + (pbtn.sz.y / 2), 0, 0.5);
                    y += pbtn.sz.y;
                }
                y += 10;
            }

            if (ccr.size() > 0) {
                cont.add(ccrc, 5, y);
                y += ccrc.sz.y + 5;
                y = crgrid(y, ccr);
                y += 10;
            }
            cont.update();
        }

        public void tick(double dt) {
            if (loading) {
                loading = false;
                try {
                    update();
                } catch (Loading l) {
                    loading = true;
                }
            }
        }

        public void change(Credo cr) {
            sel = cr;
        }

        public void pcr(Credo cr, int crl, int crlt, int crql, int crqlt, int qid) {
            this.pcr = cr;
            this.pcl = crl;
            this.pclt = crlt;
            this.pcql = crql;
            this.pcqlt = crqlt;
            this.pqid = qid;
            loading = true;
        }

        public void ncr(List<Credo> cr) {
            this.ncr = cr;
            loading = true;
        }

        public void ccr(List<Credo> cr) {
            this.ccr = cr;
            loading = true;
        }

        public boolean mousedown(Coord c, int button) {
            if (super.mousedown(c, button))
                return (true);
            change(null);
            return (true);
        }
    }

    public class ExpGrid extends GridList<Experience> {
        public final Group seen;
        private boolean loading = false;

        public ExpGrid(Coord sz) {
            super(sz);
            seen = new Group(new Coord(40, 40), new Coord(-1, 5), null, Collections.emptyList());
            itemtooltip = Experience::tooltip;
        }

        protected void drawitem(GOut g, Experience exp) {
            if (exp.small == null)
                exp.small = new TexI(convolvedown(exp.res.get().layer(Resource.imgc).img, new Coord(40, 40), iconfilter));
            g.image(exp.small, Coord.z);
        }

        protected void update() {
            super.update();
            loading = true;
        }

        public void tick(double dt) {
            super.tick(dt);
            if (loading) {
                loading = false;
                Collections.sort(seen.items, Comparator.comparing((Experience a) -> a.mtime).reversed());
            }
        }
    }

    public class WoundList extends Listbox<Wound> implements DTarget {
        public List<Wound> wounds = new ArrayList<Wound>();
        private boolean loading = false;
        private final Comparator<Wound> wcomp = new Comparator<Wound>() {
            public int compare(Wound a, Wound b) {
                return (a.sortkey.compareTo(b.sortkey));
            }
        };

        private WoundList(int w, int h) {
            super(w, h, attrf.height() + 2);
        }

        private List<Wound> treesort(List<Wound> from, int pid, int level) {
            List<Wound> direct = new ArrayList<>(from.size());
            for (Wound w : from) {
                if (w.parentid == pid) {
                    w.level = level;
                    direct.add(w);
                }
            }
            Collections.sort(direct, wcomp);
            List<Wound> ret = new ArrayList<>(from.size());
            for (Wound w : direct) {
                ret.add(w);
                ret.addAll(treesort(from, w.id, level + 1));
            }
            return (ret);
        }

        public void tick(double dt) {
            if (loading) {
                loading = false;
                for (Wound w : wounds) {
                    try {
                        w.sortkey = w.res.get().layer(Resource.tooltip).t;
                    } catch (Loading l) {
                        w.sortkey = "\uffff";
                        loading = true;
                    }
                }
                wounds = treesort(wounds, -1, 0);
            }
        }

        protected Wound listitem(int idx) {
            return (wounds.get(idx));
        }

        protected int listitems() {
            return (wounds.size());
        }

        protected void drawbg(GOut g) {
        }

        protected void drawitem(GOut g, Wound w, int idx) {
            if ((wound != null) && (wound.woundid() == w.id))
                drawsel(g);
            g.chcolor((idx % 2 == 0) ? every : other);
            g.frect(Coord.z, g.sz);
            g.chcolor();
            int x = w.level * itemh;
            try {
                if (w.small == null)
                    w.small = new TexI(PUtils.convolvedown(w.res.get().layer(Resource.imgc).img, new Coord(itemh, itemh), iconfilter));
                g.image(w.small, new Coord(x, 0));
                x += itemh + 5;
            } catch (Loading e) {
                g.image(WItem.missing.layer(Resource.imgc).tex(), new Coord(x, 0), new Coord(itemh, itemh));
                x += itemh + 5;
            }
            g.aimage(w.rnm.get().tex(), new Coord(x, itemh / 2), 0, 0.5);
            Text qd = w.rqd.get();
            if (qd != null)
                g.aimage(qd.tex(), new Coord(sz.x - 15, itemh / 2), 1.0, 0.5);
        }

        protected void itemclick(Wound item, int button) {
            if (button == 3) {
                CharWnd.this.wdgmsg("wclick", item.id, button, ui.modflags());
            } else {
                super.itemclick(item, button);
            }
        }

        public boolean drop(Coord cc, Coord ul) {
            return (false);
        }

        public boolean iteminteract(Coord cc, Coord ul) {
            Wound w = itemat(cc);
            if (w != null)
                CharWnd.this.wdgmsg("wiact", w.id, ui.modflags());
            return (true);
        }

        public void change(Wound w) {
            if (w == null)
                CharWnd.this.wdgmsg("wsel", (Object) null);
            else
                CharWnd.this.wdgmsg("wsel", w.id);
        }

        public Wound get(int id) {
            for (Wound w : wounds) {
                if (w.id == id)
                    return (w);
            }
            return (null);
        }

        public void add(Wound w) {
            wounds.add(w);
        }

        public Wound remove(int id) {
            for (Iterator<Wound> i = wounds.iterator(); i.hasNext(); ) {
                Wound w = i.next();
                if (w.id == id) {
                    i.remove();
                    return (w);
                }
            }
            return (null);
        }
    }

    public class QuestList extends Listbox<Quest> {
        public List<Quest> quests = new ArrayList<Quest>();
        private boolean loading = false;
        private final Comparator<Quest> comp = new Comparator<Quest>() {
            public int compare(Quest a, Quest b) {
                return (b.mtime - a.mtime);
            }
        };

        private QuestList(int w, int h) {
            super(w, h, attrf.height() + 2);
        }


        public void tick(double dt) {
            if (loading) {
                loading = false;
                Collections.sort(quests, comp);
            }
        }

        protected Quest listitem(int idx) {
            return (quests.get(idx));
        }

        protected int listitems() {
            return (quests.size());
        }

        protected void drawbg(GOut g) {
        }

        protected void drawitem(GOut g, Quest q, int idx) {
            if ((quest != null) && (quest.questid() == q.id))
                drawsel(g);
            g.chcolor((idx % 2 == 0) ? every : other);
            g.frect(Coord.z, g.sz);
            g.chcolor();
            try {
                if (q.small == null)
                    q.small = new TexI(PUtils.convolvedown(q.res.get().layer(Resource.imgc).img, new Coord(itemh, itemh), iconfilter));
                g.image(q.small, Coord.z);
            } catch (Loading e) {
                g.image(WItem.missing.layer(Resource.imgc).tex(), Coord.z, new Coord(itemh, itemh));
            }
            if (q.done == Quest.QST_DISABLED)
                g.chcolor(255, 128, 0, 255);
            g.aimage(q.rnm.get().tex(), new Coord(itemh + 5, itemh / 2), 0, 0.5);
            g.chcolor();
        }

        public boolean mousedown(Coord c, int button) {
            Quest q = itemat(c);
            if (button == 3 && Config.abandonrightclick) {
                abandonquest = true;
                CharWnd.this.wdgmsg("qsel", (Object) null);
                CharWnd.this.wdgmsg("qsel", q.id);
                PBotUtils.sysMsg(ui, "Dropping quest : " + q.title, green);
                remove(q);
            } else
                change(q);

            return true;

        }

        public void change(Quest q) {
            if (!ui.gui.questwnd.visible)
                ui.gui.questwnd.show();
            if ((q == null) || ((CharWnd.this.quest != null) && (q.id == CharWnd.this.quest.questid())))
                CharWnd.this.wdgmsg("qsel", (Object) null);
            else
                CharWnd.this.wdgmsg("qsel", q.id);
        }


        public Quest get(int id) {
            for (Quest q : quests) {
                if (q.id == id)
                    return (q);
            }
            return (null);
        }

        public void add(Quest q) {
            quests.add(q);
        }

        public Quest remove(int id) {
            for (Iterator<Quest> i = quests.iterator(); i.hasNext(); ) {
                Quest q = i.next();
                if (q.id == id) {
                    i.remove();
                    return (q);
                }
            }
            return (null);
        }

        public void remove(Quest q) {
            quests.remove(q);
        }
    }

    @RName("chr")
    public static class $_ implements Factory {
        public Widget create(UI ui, Object[] args) {
            return (new CharWnd(ui.sess.glob));
        }
    }

    public CharWnd(Glob glob) {
        super(Coord.z, "Character Sheet", "Character Sheet");

        final Tabs tabs = new Tabs(new Coord(15, 10), Coord.z, this);
        Tabs.Tab battr;
        {
            int x = 5, y = 0;

            battr = tabs.add();
            battr.add(new Img(catf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Base Attributes")).tex()), new Coord(x - 2, y));
            y += 35;
            base = new ArrayList<Attr>();
            Attr aw;
            base.add(aw = battr.add(new Attr(glob, "str", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            base.add(aw = battr.add(new Attr(glob, "agi", other), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            base.add(aw = battr.add(new Attr(glob, "int", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            base.add(aw = battr.add(new Attr(glob, "con", other), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            base.add(aw = battr.add(new Attr(glob, "prc", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            base.add(aw = battr.add(new Attr(glob, "csm", other), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            base.add(aw = battr.add(new Attr(glob, "dex", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            base.add(aw = battr.add(new Attr(glob, "wil", other), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            base.add(aw = battr.add(new Attr(glob, "psy", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            Frame.around(battr, base);
            y += 16;
            battr.add(new Img(catf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Food Event Points")).tex()), new Coord(x - 2, y));
            y += 35;
            feps = battr.add(new FoodMeter(), new Coord(x, y));

            x = 260;
            y = 0;
            battr.add(new Img(catf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Food Satiations")).tex()), new Coord(x - 2, y));
            y += 35;
            cons = battr.add(new Constipations(attrw, base.size()), wbox.btloff().add(x, y));
            y += cons.sz.y;
            Frame.around(battr, Collections.singletonList(cons));
            y += 16;
            battr.add(new Img(catf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Hunger Level")).tex()), new Coord(x - 2, y));
            y += 35;
            glut = battr.add(new GlutMeter(), new Coord(x, y));
        }

        {
            int x = 0, y = 0;

            sattr = tabs.add();
            sattr.add(new Img(catf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Abilities")).tex()), new Coord(x - 2, y));
            y += 35;
            skill = new ArrayList<SAttr>();
            SAttr aw;
            //            base.add(aw = battr.add(new Attr(glob, "str", every), wbox.btloff().add(x, y)));
            skill.add(aw = sattr.add(new SAttr(glob, "unarmed", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "melee", other), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "ranged", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "explore", other), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "stealth", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "sewing", other), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "smithing", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "masonry", other), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "carpentry", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "cooking", other), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "farming", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "survive", other), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            skill.add(aw = sattr.add(new SAttr(glob, "lore", every), wbox.btloff().add(x, y)));
            y += aw.sz.y;
            Frame.around(sattr, skill);

            x = 255;
            y = 0;
            sattr.add(new Img(catf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Study Report")).tex()), new Coord(x - 2, y));
            y += 35;
            y += 151;
            int rx = x + attrw + 15;
            Frame.around(sattr, Area.sized(new Coord(x, y).add(wbox.btloff()), new Coord(attrw + 20, 96)));
            sattr.add(new Label("Experience points:"), new Coord(x + 15, y + 10));
            sattr.add(new EncLabel(new Coord(rx, y + 10)));
            sattr.add(new Label("Learning points:"), new Coord(x + 15, y + 25));
            sattr.add(new ExpLabel(new Coord(rx, y + 25)));
            sattr.add(new Label("Learning cost:"), new Coord(x + 15, y + 40));
            sattr.add(new RLabel(new Coord(rx, y + 40), "0") {
                int cc;

                public void draw(GOut g) {
                    if (cc > exp)
                        g.chcolor(debuff);
                    super.draw(g);
                    if (cc != scost)
                        settext(Utils.thformat(cc = scost));
                }
            });
            sattr.add(new Button(75, "Buy") {
                public void click() {
                    ArrayList<Object> args = new ArrayList<Object>();
                    for (SAttr attr : skill) {
                        if (attr.tbv > attr.attr.base) {
                            args.add(attr.attr.nm);
                            args.add(attr.tbv);
                        }
                    }
                    CharWnd.this.wdgmsg("sattr", args.toArray(new Object[0]));
                    for (SAttr attr : skill)
                        attr.reset();
                }
            }, new Coord(rx - 75, y + 70));
            sattr.add(new Button(75, "Reset") {
                public void click() {
                    for (SAttr attr : skill)
                        attr.reset();
                }
            }, new Coord(rx - 165, y + 70));
        }

        Tabs.Tab skills;
        {
            int x = 0, y = 0;

            skills = tabs.add();
            skills.add(new Img(catf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Lore & Skills")).tex()), new Coord(x - 2, y));
            y += 35;
            final LoadingTextBox info = skills.add(new LoadingTextBox(new Coord(attrw + 10, 260), "", ifnd), new Coord(x, y).add(wbox.btloff()));
            info.bg = new Color(0, 0, 0, 128);
            Frame.around(skills, Collections.singletonList(info));

            x = 265;
            y = 0;
            skills.add(new Img(catf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Entries")).tex()), new Coord(x - 2, y));
            y += 35;
            Tabs lists = new Tabs(new Coord(x, y), new Coord(attrw + wbox.bisz().x + 10, 0), skills);
            Tabs.Tab sktab = lists.add();
            {
                Frame f = sktab.add(new Frame(new Coord(lists.sz.x, 192), false), 0, 0);
                y = f.sz.y + 5;
                skg = f.addin(new SkillGrid(Coord.z) {
                    public void change(Skill sk) {
                        Skill p = sel;
                        super.change(sk);
                        CharWnd.this.exps.sel = null;
                        CharWnd.this.credos.sel = null;
                        if (sk != null)
                            info.settext(sk::rendertext);
                        else if (p != null)
                            info.settext("");
                    }
                });
                int rx = attrw + wbox.btloff().x - 10;
                Frame.around(sktab, Area.sized(new Coord(0, y).add(wbox.btloff()), new Coord(attrw + 10, 34)));
                /*
                sktab.add(new Label("Learning points:"), new Coord(15, y + 10));
                sktab.add(new ExpLabel(new Coord(rx, y + 10)));
                */
                Button bbtn = sktab.add(new Button(50, "Buy") {
                    public void click() {
                        if (skg.sel != null)
                            CharWnd.this.wdgmsg("buy", skg.sel.nm);
                    }
                }, new Coord(rx - 50, y + 10));
                Label clbl = sktab.adda(new Label("Cost:"), new Coord(15, bbtn.c.y + (bbtn.sz.y / 2)), 0, 0.5);
                sktab.add(new RLabel(new Coord(bbtn.c.x - 10, clbl.c.y), "N/A") {
                    Integer cc = null;
                    int cexp;

                    public void draw(GOut g) {
                        if ((cc != null) && (cc > exp))
                            g.chcolor(debuff);
                        super.draw(g);
                        Integer cost = ((skg.sel == null) || skg.sel.has) ? null : skg.sel.cost;
                        if (!Utils.eq(cost, cc) || (cexp != exp)) {
                            if (cost == null) {
                                settext("N/A");
                            } else {
                                settext(String.format("%,d / %,d LP", cost, exp));
                            }
                            cc = cost;
                            cexp = exp;
                        }
                    }
                });
            }
            Tabs.Tab credos = lists.add();
            {
                Frame f = credos.add(new Frame(new Coord(lists.sz.x, 241), false), 0, 0);
                y = f.sz.y + 5;
                this.credos = f.addin(new CredoGrid(Coord.z) {
                    public void change(Credo cr) {
                        Credo p = sel;
                        super.change(cr);
                        CharWnd.this.skg.sel = null;
                        CharWnd.this.exps.sel = null;
                        if (cr != null)
                            info.settext(cr::rendertext);
                        else if (p != null)
                            info.settext("");
                    }
                });
                int rx = attrw + wbox.btloff().x - 10;
            }
            Tabs.Tab exps = lists.add();
            {
                Frame f = exps.add(new Frame(new Coord(lists.sz.x, 241), false), 0, 0);
                this.exps = f.addin(new ExpGrid(Coord.z) {
                    public void change(Experience exp) {
                        Experience p = sel;
                        super.change(exp);
                        CharWnd.this.skg.sel = null;
                        CharWnd.this.credos.sel = null;
                        if (exp != null)
                            info.settext(exp::rendertext);
                        else if (p != null)
                            info.settext("");
                    }
                });
            }
            lists.pack();
            int bw = (lists.sz.x + 5) / 3;
            x = lists.c.x;
            y = lists.c.y + lists.sz.y + 5;
            skills.add(lists.new TabButton(bw - 5, "Skills", sktab), new Coord(x, y));
            skills.add(lists.new TabButton(bw - 5, "Credos", credos), new Coord(x + bw * 1, y));
            skills.add(lists.new TabButton(bw - 5, "Lore", exps), new Coord(x + bw * 2, y));
        }

        Tabs.Tab wounds;
        {
            wounds = tabs.add();
            wounds.add(new Img(catf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Health & Wounds")).tex()), new Coord(0, 0));
            this.wounds = wounds.add(new WoundList(attrw + 10, 12), new Coord(265, 35).add(wbox.btloff()));
            Frame.around(wounds, Collections.singletonList(this.wounds));
            woundbox = wounds.add(new Widget(new Coord(attrw + 10, this.wounds.sz.y)) {
                public void draw(GOut g) {
                    g.chcolor(0, 0, 0, 128);
                    g.frect(Coord.z, sz);
                    g.chcolor();
                    super.draw(g);
                }

                public void cdestroy(Widget w) {
                    if (w == wound)
                        wound = null;
                }
            }, new Coord(0, 35).add(wbox.btloff()));
            Frame.around(wounds, Collections.singletonList(woundbox));
        }

        Tabs.Tab quests;
        {
            quests = tabs.add();
            quests.add(new Img(catf.render(Resource.getLocString(Resource.BUNDLE_LABEL, "Quest Log")).tex()), new Coord(0, 0));
            questbox = quests.add(new Widget(new Coord(attrw + 10, 260)) {
                public void draw(GOut g) {
                    g.chcolor(0, 0, 0, 128);
                    g.frect(Coord.z, sz);
                    g.chcolor();
                    super.draw(g);
                }

                public void cdestroy(Widget w) {
                    if (w == quest)
                        quest = null;
                }
            }, new Coord(0, 35).add(wbox.btloff()));
            Frame.around(quests, Collections.singletonList(questbox));
            Tabs lists = new Tabs(new Coord(265, 35), new Coord(attrw + 10 + wbox.bisz().x, 0), quests);
            Tabs.Tab cqst = lists.add();
            {
                this.cqst = cqst.add(new QuestList(attrw + 10, 11), new Coord(0, 0).add(wbox.btloff()));
                Frame.around(cqst, Collections.singletonList(this.cqst));
            }
            Tabs.Tab dqst = lists.add();
            {
                this.dqst = dqst.add(new QuestList(attrw + 10, 11), new Coord(0, 0).add(wbox.btloff()));
                Frame.around(dqst, Collections.singletonList(this.dqst));
            }
            lists.pack();
            int bw = (lists.sz.x + 5) / 2;
            int x = lists.c.x;
            int y = lists.c.y + lists.sz.y + 5;
            quests.add(lists.new TabButton(bw - 5, "Current", cqst), new Coord(x, y));
            quests.add(lists.new TabButton(bw - 5, "Completed", dqst), new Coord(x + bw, y));
            questtab = quests;
        }

        {
            Widget prev;

            class TB extends IButton {
                final Tabs.Tab tab;

                TB(String nm, Tabs.Tab tab) {
                    super(Resource.loadimg("gfx/hud/chr/" + nm + "u"), Resource.loadimg("gfx/hud/chr/" + nm + "d"));
                    this.tab = tab;
                }

                public void click() {
                    tabs.showtab(tab);
                }

                protected void depress() {
                    Audio.play(Button.lbtdown.stream());
                }

                protected void unpress() {
                    Audio.play(Button.lbtup.stream());
                }
            }

            tabs.pack();

            fgt = tabs.add();

            prev = add(new TB("battr", battr), new Coord(tabs.c.x + 5, tabs.c.y + tabs.sz.y + 10));
            prev.settip(Resource.getLocString(Resource.BUNDLE_LABEL, "Base Attributes"));
            prev = add(new TB("sattr", sattr), new Coord(prev.c.x + prev.sz.x + 5, prev.c.y));
            prev.settip(Resource.getLocString(Resource.BUNDLE_LABEL, "Abilities"));
            prev = add(new TB("skill", skills), new Coord(prev.c.x + prev.sz.x + 5, prev.c.y));
            prev.settip(Resource.getLocString(Resource.BUNDLE_LABEL, "Lore & Skills"));
            prev = add(new TB("fgt", fgt), new Coord(prev.c.x + prev.sz.x + 5, prev.c.y));
            prev.settip(Resource.getLocString(Resource.BUNDLE_LABEL, "Martial Arts & Combat Schools"));
            prev = add(new TB("wound", wounds), new Coord(prev.c.x + prev.sz.x + 5, prev.c.y));
            prev.settip(Resource.getLocString(Resource.BUNDLE_LABEL, "Health & Wounds"));
            prev = add(new TB("quest", quests), new Coord(prev.c.x + prev.sz.x + 5, prev.c.y));
            prev.settip(Resource.getLocString(Resource.BUNDLE_LABEL, "Quest Log"));
        }

        resize(contentsz().add(15, 10));
    }

    public Glob.CAttr findattr(String name) {
        for (SAttr skill : this.skill) {
            if (name.equals(skill.attr.nm)) {
                return skill.attr;
            }
        }
        for (Attr stat : base) {
            if (name.equals(stat.attr.nm)) {
                return stat.attr;
            }
        }
        return null;
    }

    public Glob.CAttr findattr(Resource res) {
        for (SAttr skill : this.skill) {
            if (res == skill.res) {
                return skill.attr;
            }
        }
        for (Attr stat : base) {
            if (res == stat.res) {
                return stat.attr;
            }
        }
        return null;
    }

    public int statIndex(Resource res) {
        if (base != null) {
            return IntStream.range(0, base.size())
                    .filter(i -> base.stream().equals(res))
                    .findFirst().orElse(Integer.MAX_VALUE);
        }
        return Integer.MAX_VALUE;
    }

    public int skillIndex(Resource res) {
        if (skill != null) {
            return IntStream.range(0, skill.size())
                    .filter(i -> skill.stream().equals(res))
                    .findFirst().orElse(Integer.MAX_VALUE);
        }
        return Integer.MAX_VALUE;
    }

    public int BY_PRIORITY(Resource r1, Resource r2) {
        int b1 = statIndex(r1);
        int b2 = statIndex(r2);

        if (b1 == b2) {
            b1 = skillIndex(r1);
            b2 = skillIndex(r2);
            if (b1 == b2) {
                return r1.name.compareTo(r2.name);
            } else {
                return Integer.compare(b1, b2);
            }
        } else {
            return Integer.compare(b1, b2);
        }
    }

    public void addchild(Widget child, Object... args) {


        String place = (args[0] instanceof String) ? (((String) args[0]).intern()) : null;
        if (place == "study") {
            sattr.add(child, new Coord(255, 35).add(wbox.btloff()));
            Frame.around(sattr, Collections.singletonList(child));
            Widget inf = sattr.add(new StudyInfo(new Coord(attrw - 130, child.sz.y), child), new Coord(255 + 150, child.c.y).add(wbox.btloff().x, 0));
            sattr.add(new CheckBox("Lock") {
                {
                    a = Config.studylock;
                }

                public void set(boolean val) {
                    Utils.setprefb("studylock", val);
                    Config.studylock = val;
                    a = val;
                }
            }, new Coord(407, 10));
            sattr.add(new CheckBox("Auto") {
                {
                    a = Config.autostudy;
                }

                public void set(boolean val) {
                    Utils.setprefb("autostudy", val);
                    Config.autostudy = val;
                    a = val;
                }
            }, new Coord(465, 10));
            Frame.around(sattr, Collections.singletonList(inf));
            getparent(GameUI.class).studywnd.setStudy((Inventory) child);
        } else if (place == "fmg") {
            fgt.add(child, 0, 0);
        } else if (place == "wound") {
            this.wound = (Wound.Info) child;
            woundbox.add(child, Coord.z);
        } else if (place == "quest") {
            this.quest = (Quest.Info) child;
            questbox.add(child, Coord.z);
            getparent(GameUI.class).addchild(this.quest.qview(), "qq");
        } else {
            super.addchild(child, args);
        }
    }

    private List<Skill> decsklist(Object[] args, int a, boolean has) {
        List<Skill> buf = new ArrayList<>();
        while (a < args.length) {
            String nm = (String) args[a++];
            Indir<Resource> res = ui.sess.getres((Integer) args[a++]);
            int cost = ((Number) args[a++]).intValue();
            buf.add(new Skill(nm, res, cost, has));
        }
        return (buf);
    }

    private List<Credo> deccrlist(Object[] args, int a, boolean has) {
        List<Credo> buf = new ArrayList<>();
        while (a < args.length) {
            String nm = (String) args[a++];
            Indir<Resource> res = ui.sess.getres((Integer) args[a++]);
            buf.add(new Credo(nm, res, has));
        }
        return (buf);
    }

    private List<Experience> decexplist(Object[] args, int a) {
        List<Experience> buf = new ArrayList<>();
        while (a < args.length) {
            Indir<Resource> res = ui.sess.getres((Integer) args[a++]);
            int mtime = ((Number) args[a++]).intValue();
            int score = ((Number) args[a++]).intValue();
            buf.add(new Experience(res, mtime, score));
        }
        return (buf);
    }

    private void decwound(Object[] args, int a, int len) {
        int id = (Integer) args[a];
        Indir<Resource> res = (args[a + 1] == null) ? null : ui.sess.getres((Integer) args[a + 1]);
        if (res != null) {
            Object qdata = args[a + 2];
            int parentid = (len > 3) ? ((args[a + 3] == null) ? -1 : (Integer) args[a + 3]) : -1;
            Wound w = wounds.get(id);
            if (w == null) {
                wounds.add(new Wound(id, res, qdata, parentid));
            } else {
                w.res = res;
                w.qdata = qdata;
            }
            wounds.loading = true;
        } else {
            wounds.remove(id);
        }
    }

    public void uimsg(String nm, Object... args) {
        if (nm == "exp") {
            exp = ((Number) args[0]).intValue();
        } else if (nm == "enc") {
            enc = ((Number) args[0]).intValue();
        } else if (nm == "food") {
            feps.update(args);
        } else if (nm == "glut") {
            glut.update(args);
        } else if (nm == "glut") {
        } else if (nm == "ftrig") {
            feps.trig(ui.sess.getres((Integer) args[0]));
        } else if (nm == "lvl") {
            for (Attr aw : base) {
                if (aw.nm.equals(args[0]))
                    aw.lvlup();
            }
        } else if (nm == "const") {
            int a = 0;
            while (a < args.length) {
                ResData t = new ResData(ui.sess.getres((Integer) args[a++]), MessageBuf.nil);
                if (args[a] instanceof byte[])
                    t.sdt = new MessageBuf((byte[]) args[a++]);
                double m = ((Number) args[a++]).doubleValue();
                ui.sess.character.constipation.update(t, m);
                cons.update(t, m);
            }
        } else if (nm == "csk") {
            skg.csk.update(decsklist(args, 0, true));
        } else if (nm == "nsk") {
            skg.nsk.update(decsklist(args, 0, false));
        } else if (nm == "ccr") {
            credos.ccr(deccrlist(args, 0, true));
        } else if (nm == "ncr") {
            credos.ncr(deccrlist(args, 0, false));
        } else if (nm == "crcost") {
            credos.cost = (Integer) args[0];
        } else if (nm == "pcr") {
            if (args.length > 0) {
                int a = 0;
                String cnm = (String) args[a++];
                Indir<Resource> res = ui.sess.getres((Integer) args[a++]);
                int crl = (Integer) args[a++], crlt = (Integer) args[a++];
                int crql = (Integer) args[a++], crqlt = (Integer) args[a++];
                int qid = (Integer) args[a++];
                credos.pcr(new Credo(cnm, res, false),
                        crl, crlt, crql, crqlt, qid);
            } else {
                credos.pcr(null, 0, 0, 0, 0, 0);
            }
        } else if (nm == "exps") {
            exps.seen.update(decexplist(args, 0));
        } else if (nm == "wounds") {
            if (args.length > 0) {
                if (args[0] instanceof Object[]) {
                    for (int i = 0; i < args.length; i++)
                        decwound((Object[]) args[i], 0, ((Object[]) args[i]).length);
                } else {
                    for (int i = 0; i < args.length; i += 3)
                        decwound(args, i, 3);
                }
            }
          /*  for (int i = 0; i < args.length; i += 3) {
                int id = (Integer) args[i];
                Indir<Resource> res = (args[i + 1] == null) ? null : ui.sess.getres((Integer) args[i + 1]);
                Object qdata = args[i + 2];
                if (res != null) {
                    Object qdata = args[a + 2];
                    int parentid = (len > 3) ? ((args[a + 3] == null) ? -1 : (Integer)args[a + 3]) : -1;
                    Wound w = wounds.get(id);
                    if (w == null) {
                        wounds.add(new Wound(id,res,qdata,parentid));
                    } else {
                        w.res = res;
                        w.qdata = qdata;
                    }
                    wounds.loading = true;
                } else {
                    wounds.remove(id);
                }
            }*/
        } else if (nm == "quests") {
            for (int i = 0; i < args.length; ) {
                int id = (Integer) args[i++];
                Integer resid = (Integer) args[i++];
                Indir<Resource> res = (resid == null) ? null : ui.sess.getres(resid);
                if (res != null) {
                    int st = (Integer) args[i++];
                    int mtime = (Integer) args[i++];
                    String title = null;
                    if ((i < args.length) && (args[i] instanceof String))
                        title = (String) args[i++];
                    QuestList cl = cqst;
                    Quest q = cqst.get(id);
                    if (q == null)
                        q = (cl = dqst).get(id);
                    if (q == null) {
                        cl = null;
                        q = new Quest(id, res, title, st, mtime);
                    } else {
                        int fst = q.done;
                        q.res = res;
                        q.done = st;
                        q.mtime = mtime;
                        if (((fst == Quest.QST_PEND) || (fst == Quest.QST_DISABLED)) &&
                                !((st == Quest.QST_PEND) || (st == Quest.QST_DISABLED)))
                            q.done(getparent(GameUI.class));
                    }
                    QuestList nl = ((q.done == Quest.QST_PEND) || (q.done == Quest.QST_DISABLED)) ? cqst : dqst;
                    if (nl != cl) {
                        if (cl != null)
                            cl.remove(q);
                        nl.add(q);
                    }
                    nl.loading = true;
                } else {
                    cqst.remove(id);
                    dqst.remove(id);
                }
            }
            this.ui.gui.questhelper.refresh();
        } else {
            super.uimsg(nm, args);
        }
    }
}

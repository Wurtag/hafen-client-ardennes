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

import haven.Resource.AButton;
import haven.automation.AddBranchesToOven;
import haven.automation.AddCoalToSmelter;
import haven.automation.ButcherFish;
import haven.automation.CoalToSmelters;
import haven.automation.Coracleslol;
import haven.automation.CountGobs;
import haven.automation.Discord;
import haven.automation.Dismount;
import haven.automation.DreamHarvester;
import haven.automation.EquipRusalka;
import haven.automation.EquipSacks;
import haven.automation.EquipSlippers;
import haven.automation.EquipSwordShield;
import haven.automation.EquipWeapon;
import haven.automation.FeedClover;
import haven.automation.FillCheeseTray;
import haven.automation.FlaxBot;
import haven.automation.GobSelectCallback;
import haven.automation.LeashAnimal;
import haven.automation.LightWithTorch;
import haven.automation.MinerAlert;
import haven.automation.MothKiller;
import haven.automation.OysterOpener;
import haven.automation.PepperBotPro;
import haven.automation.PepperFood;
import haven.automation.PlaceTrays;
import haven.automation.SliceCheese;
import haven.automation.SplitLogs;
import haven.automation.SteelRefueler;
import haven.automation.TakeTrays;
import haven.automation.TrellisDestroy;
import haven.automation.TrellisHarvest;
import haven.purus.Farmer;
import haven.purus.Farmer2;
import haven.purus.FlowerPicker;
import haven.purus.StockpileFiller;
import haven.purus.TroughFiller;
import haven.purus.pbot.PBotUtils;
import modification.configuration;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class MenuGrid extends Widget {
    public final static Coord bgsz = Inventory.invsq.sz().add(-1, -1);
    public final static RichText.Foundry ttfnd = new RichText.Foundry(TextAttribute.FAMILY, Text.cfg.font.get("sans"), TextAttribute.SIZE, Text.cfg.tooltipCap); //aa(true)
    public final Map<String, SpecialPagina> specialpag = new HashMap<>();
    public final ObservableCollection<Pagina> paginae = new ObservableCollection<>(new HashSet<>());
    public static Coord gsz = configuration.getMenuGrid();
    public Pagina cur, dragging;
    private Collection<PagButton> curbtns = null;
    public PagButton pressed, layout[][] = new PagButton[configuration.getMenuGrid().x][configuration.getMenuGrid().y];
    private UI.Grab grab;
    private int curoff = 0;
    public static int cap = (gsz.x * gsz.y) - 2;
    BufferedReader br;
    private boolean recons = true;
    private Map<Character, PagButton> hotmap = new HashMap<>();
    private boolean togglestuff = true;
    public boolean discordconnected;
    public Pagina lastCraft = null;
    public int pagseq = 0;

    @RName("scm")
    public static class $_ implements Factory {
        public Widget create(UI ui, Object[] args) {
            return (new MenuGrid());
        }
    }

    public static class PagButton implements ItemInfo.Owner {
        public final Pagina pag;
        public final Resource res;

        public PagButton(Pagina pag) {
            this.pag = pag;
            this.res = pag.res();
        }


        public BufferedImage img() {
            return (res.layer(Resource.imgc).img);
        }

        public String name() {
            return (res.layer(Resource.action).name);
        }

        public char hotkey() {
            return (res.layer(Resource.action).hk);
        }

        public void use() {
            pag.use();
        }

        public String sortkey() {
            AButton ai = pag.act();

            if (ai.ad.length == 0) {
                return ("\0" + name());
            }

            if (ai.ad[0].contains("!")) {
                return ("\0\0" + ai.ad[0] + name());
            }

            return (name());
        }

        private List<ItemInfo> info = null;

        public List<ItemInfo> info() {
            if (info == null)
                info = ItemInfo.buildinfo(this, pag.rawinfo);
            return (info);
        }

        private static final OwnerContext.ClassResolver<PagButton> ctxr = new OwnerContext.ClassResolver<PagButton>()
                .add(Glob.class, p -> p.pag.scm.ui.sess.glob)
                .add(Session.class, p -> p.pag.scm.ui.sess);

        public <T> T context(Class<T> cl) {
            return (ctxr.context(cl, this));
        }

        public BufferedImage rendertt(boolean withpg) {
            Resource.AButton ad = res.layer(Resource.action);
            Resource.Pagina pg = res.layer(Resource.pagina);
            String tt = ad.name;
            int pos = tt.toUpperCase().indexOf(Character.toUpperCase(ad.hk));
            if (pos >= 0)
                tt = tt.substring(0, pos) + "$b{$col[255,128,0]{" + tt.charAt(pos) + "}}" + tt.substring(pos + 1);
            else if (ad.hk != 0)
                tt += " [" + ad.hk + "]";
            BufferedImage ret = ttfnd.render(tt, 300).img;
            if (withpg) {
                List<ItemInfo> info = info();
                info.removeIf(el -> el instanceof ItemInfo.Name);
                if (!info.isEmpty())
                    ret = ItemInfo.catimgs(0, ret, ItemInfo.longtip(info));
                if (pg != null)
                    ret = ItemInfo.catimgs(0, ret, ttfnd.render("\n" + pg.text, 200).img);
            }
            return (ret);
        }

        @Resource.PublishedCode(name = "pagina")
        public interface Factory {
            public PagButton make(Pagina info);
        }
    }


    public final PagButton next = new PagButton(new Pagina(this, Resource.local().loadwait("gfx/hud/sc-next").indir())) {
        public void use() {
            if ((curoff + cap) >= curbtns.size())
                curoff = 0;
            else
                curoff += cap;
        }

        public BufferedImage rendertt(boolean withpg) {
            return (RichText.render("More... ($b{$col[255,128,0]{\u21e7N}})", 0).img);
        }
    };

    public final PagButton bk = new PagButton(new Pagina(this, Resource.local().loadwait("gfx/hud/sc-back").indir())) {
        public void use() {
            if ((curoff - cap) >= 0)
                curoff -= cap;
            else {
                pag.scm.cur = paginafor(pag.scm.cur.act().parent);
                curoff = 0;
            }


        }

        public BufferedImage rendertt(boolean withpg) {
            return (RichText.render("Back ($b{$col[255,128,0]{Backspace}})", 0).img);
        }
    };

    public static class Pagina implements ItemInfo.Owner {
        public final MenuGrid scm;
        public final Indir<Resource> res;
        public State st;
        public double meter, gettime, dtime, fstart;
        public Indir<Tex> img;
        public int newp;
        public Object[] rawinfo = {};
        private final Consumer<Pagina> onUse;

        public static enum State {
            ENABLED, DISABLED {
                public Indir<Tex> img(Pagina pag) {
                    return (Utils.cache(() -> new TexI(PUtils.monochromize(pag.button().img(), Color.LIGHT_GRAY))));
                }
            };

            public Indir<Tex> img(Pagina pag) {
                return (Utils.cache(() -> new TexI(pag.button().img())));
            }
        }

        public Pagina(MenuGrid scm, Indir<Resource> res) {
            this.scm = scm;
            this.res = res;
            state(State.ENABLED);
            this.onUse = (me) -> scm.wdgmsg("act", (Object[]) res().layer(Resource.action).ad);
        }

        public Pagina(MenuGrid scm, Indir<Resource> res, final Consumer<Pagina> onUse) {
            this.scm = scm;
            this.res = res;
            state(State.ENABLED);
            this.onUse = onUse;
        }


        public Resource res() {
            return (res.get());
        }

        public Resource.AButton act() {
            return (res().layer(Resource.action));
        }

        public void use() {
            onUse.accept(this);
        }

        private PagButton button = null;

        public PagButton button() {
            if (button == null) {
                Resource res = res();
                PagButton.Factory f = res.getcode(PagButton.Factory.class, false);
                if (f == null)
                    button = new PagButton(this);
                else
                    button = f.make(this);
            }
            return (button);
        }

        public void state(State st) {
            this.st = st;
            this.img = st.img(this);
        }

        public void button(PagButton btn) {
            button = btn;
        }

        private List<ItemInfo> info = null;

        public List<ItemInfo> info() {
            if (info == null)
                info = ItemInfo.buildinfo(this, rawinfo);
            return (info);
        }

        private static final OwnerContext.ClassResolver<Pagina> ctxr = new OwnerContext.ClassResolver<Pagina>()
                .add(Glob.class, p -> p.scm.ui.sess.glob)
                .add(Session.class, p -> p.scm.ui.sess)
                .add(UI.class, p -> p.scm.ui);

        public <T> T context(Class<T> cl) {
            return (ctxr.context(cl, this));
        }

        public boolean isAction() {
            Resource.AButton act = act();
            if (act == null) {
                return false;
            }
            String[] ad = act.ad;
            return ad != null && ad.length > 0;
        }

        public static String name(Pagina p) {
            String name = "";
            if (p.res instanceof Resource.Named) {
                name = ((Resource.Named) p.res).name;
            } else {
                try {
                    name = p.res.get().name;
                } catch (Loading ignored) {
                }
            }
            return name;
        }
    }

    public Map<Indir<Resource>, Pagina> pmap = new WeakHashMap<Indir<Resource>, Pagina>();

    public Pagina paginafor(Indir<Resource> res) {
        if (res == null)
            return (null);
        synchronized (pmap) {
            Pagina p = pmap.get(res);
            if (p == null)
                pmap.put(res, p = new Pagina(this, res));
            return (p);
        }
    }

    public Pagina paginafor(Resource res) {
        if (res != null) {
            synchronized (pmap) {
                for (Indir<Resource> key : pmap.keySet()) {
                    if (Objects.equals(key.get().name, res.name)) {
                        return pmap.get(key);
                    }
                }
            }
        }
        return null;
    }


    public Pagina paginafor(String name) {
        return paginafor(Resource.remote().load(name));
    }

    public static Comparator<Pagina> sorter = new Comparator<Pagina>() {
        public int compare(Pagina a, Pagina b) {
            AButton aa = a.act(), ab = b.act();
            if ((aa.ad.length == 0) && (ab.ad.length > 0))
                return (-1);
            if ((aa.ad.length > 0) && (ab.ad.length == 0))
                return (1);
            return (aa.name.compareTo(ab.name));
        }
    };


    public boolean cons(Pagina p, Collection<PagButton> buf) {
        Collection<Pagina> open, close = new HashSet<>();
        synchronized (paginae) {
            open = new LinkedList<>();
            for (Pagina pag : paginae) {
                if (pag.newp == 2) {
                    pag.newp = 0;
                    pag.fstart = 0;
                }
                open.add(pag);
            }
            for (Pagina pag : pmap.values()) {
                if (pag.newp == 2) {
                    pag.newp = 0;
                    pag.fstart = 0;
                }
            }
        }
        boolean ret = true;
        while (!open.isEmpty()) {
            Iterator<Pagina> iter = open.iterator();
            Pagina pag = iter.next();
            iter.remove();
            try {
                AButton ad = pag.act();
                if (ad == null)
                    throw (new RuntimeException("Pagina in " + pag.res + " lacks action"));
                Pagina parent = paginafor(ad.parent);
                if ((pag.newp != 0) && (parent != null) && (parent.newp == 0)) {
                    parent.newp = 2;
                    parent.fstart = (parent.fstart == 0) ? pag.fstart : Math.min(parent.fstart, pag.fstart);
                }
                if (parent == p)
                    buf.add(pag.button());
                else if ((parent != null) && !close.contains(parent) && !open.contains(parent))
                    open.add(parent);
                close.add(pag);
            } catch (Loading e) {
                ret = false;
            }
        }
        return (ret);
    }

    public boolean cons2(Pagina p, Collection<Pagina> buf) {
        Pagina[] cp = new Pagina[0];
        Collection<Pagina> open, close = new HashSet<Pagina>();
        synchronized (paginae) {
            open = new LinkedList<Pagina>();
            for (Pagina pag : paginae) {
                if (pag.newp == 2) {
                    pag.newp = 0;
                    pag.fstart = 0;
                }
                open.add(pag);
            }
            for (Pagina pag : pmap.values()) {
                if (pag.newp == 2) {
                    pag.newp = 0;
                    pag.fstart = 0;
                }
            }
        }
        boolean ret = true;
        while (!open.isEmpty()) {
            Iterator<Pagina> iter = open.iterator();
            Pagina pag = iter.next();
            iter.remove();
            try {
                AButton ad = pag.act();
                if (ad == null)
                    throw (new RuntimeException("Pagina in " + pag.res + " lacks action"));
                Pagina parent = paginafor(ad.parent);
                if ((pag.newp != 0) && (parent != null) && (parent.newp == 0)) {
                    parent.newp = 2;
                    parent.fstart = (parent.fstart == 0) ? pag.fstart : Math.min(parent.fstart, pag.fstart);
                }
                if (parent == p)
                    buf.add(pag);
                else if ((parent != null) && !close.contains(parent) && !open.contains(parent))
                    open.add(parent);
                close.add(pag);
            } catch (Loading e) {
                ret = false;
            }
        }
        return (ret);
    }

    public static class SpecialPagina extends Pagina {
        public final String key;

        public SpecialPagina(MenuGrid scm, String key, Indir<Resource> res, final Consumer<Pagina> onUse) {
            super(scm, res, onUse);
            this.key = key;
        }
    }

    private void addSpecial(final SpecialPagina pag) {
        paginae.add(pag);
        specialpag.put(pag.key, pag);

    }

    public MenuGrid() {
        super(bgsz.mul(gsz).add(1, 1));
        addSpecial(new SpecialPagina(this, "paginae::amber::coal12",
                Resource.local().load("paginae/amber/coal12"),
                (pag) -> {
                    if (ui.gui != null) {
                        Thread t = new Thread(new AddCoalToSmelter(ui.gui, 12), "AddCoalToSmelter");
                        t.start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::coal9",
                Resource.local().load("paginae/amber/coal9"),
                (pag) -> {

                    if (ui.gui != null) {
                        Thread t = new Thread(new AddCoalToSmelter(ui.gui, 9), "AddCoalToSmelter");
                        t.start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::branchoven",
                Resource.local().load("paginae/amber/branchoven"),
                (pag) -> {

                    if (ui.gui != null) {
                        Thread t = new Thread(new AddBranchesToOven(ui.gui, 4), "AddBranchesToOven");
                        t.start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::steel",
                Resource.local().load("paginae/amber/steel"),
                (pag) -> {

                    if (ui.gui != null) {
                        if (ui.gui.getwnd("Steel Refueler") == null) {
                            SteelRefueler sw = new SteelRefueler();
                            ui.gui.map.steelrefueler = sw;
                            ui.gui.add(sw, new Coord(ui.gui.sz.x / 2 - sw.sz.x / 2, ui.gui.sz.y / 2 - sw.sz.y / 2 - 200));
                            synchronized (GobSelectCallback.class) {
                                ui.gui.map.registerGobSelect(sw);
                            }
                        }
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::torch",
                Resource.local().load("paginae/amber/torch"),
                (pag) -> {

                    if (ui.gui != null) {
                        if (ui.gui.getwnd("Torch Lighter") == null) {
                            LightWithTorch sw = new LightWithTorch();
                            ui.gui.map.torchlight = sw;
                            ui.gui.add(sw, new Coord(ui.gui.sz.x / 2 - sw.sz.x / 2, ui.gui.sz.y / 2 - sw.sz.y / 2 - 200));
                            synchronized (GobSelectCallback.class) {
                                ui.gui.map.registerGobSelect(sw);
                            }
                        }
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::coaltosmelters",
                Resource.local().load("paginae/amber/CoalToSmelters"),
                (pag) -> {

                    if (ui.gui != null) {
                        if (ui.gui.getwnd("Add Coal To Smelters") == null) {
                            CoalToSmelters sw = new CoalToSmelters();
                            ui.gui.map.coaltosmelters = sw;
                            ui.gui.add(sw, new Coord(ui.gui.sz.x / 2 - sw.sz.x / 2, ui.gui.sz.y / 2 - sw.sz.y / 2 - 200));
                            synchronized (GobSelectCallback.class) {
                                ui.gui.map.registerGobSelect(sw);
                            }
                        }
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::coracleslol",
                Resource.local().load("paginae/amber/Coracleslol"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new Coracleslol(ui.gui), "Coracleslol").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::mineralert",
                Resource.local().load("paginae/amber/MinerAlert"),
                (pag) -> {

                    if (ui.gui != null) {
                        if (ui.gui.getwnd("Miner Alert") == null) {
                            MinerAlert sw = new MinerAlert();
                            ui.gui.map.mineralert = sw;
                            ui.gui.add(sw, new Coord(ui.gui.sz.x / 2 - sw.sz.x / 2, ui.gui.sz.y / 2 - sw.sz.y / 2 - 200));
                        }
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::clover",
                Resource.local().load("paginae/amber/clover"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new FeedClover(ui.gui), "FeedClover").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::rope",
                Resource.local().load("paginae/amber/rope"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new LeashAnimal(ui.gui), "LeashAnimal").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::fish",
                Resource.local().load("paginae/amber/fish"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new ButcherFish(ui.gui), "ButcherFish").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::windows::timers",
                Resource.local().load("paginae/amber/timers"),
                (pag) -> {

                    if (ui.gui != null) {
                        ui.gui.timerswnd.show(!ui.gui.timerswnd.visible);
                        ui.gui.timerswnd.raise();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::windows::livestock",
                Resource.local().load("paginae/amber/livestock"),
                (pag) -> {

                    if (ui.gui != null) {
                        ui.gui.livestockwnd.show(!ui.gui.livestockwnd.visible);
                        ui.gui.livestockwnd.raise();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::dream",
                Resource.local().load("paginae/amber/dream"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new DreamHarvester(ui.gui), "DreamHarvester").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::trellisharvest",
                Resource.local().load("paginae/amber/trellisharvest"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new TrellisHarvest(ui.gui), "TrellisHarvest").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::trellisdestroy",
                Resource.local().load("paginae/amber/trellisdestroy"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new TrellisDestroy(ui.gui), "TrellisDestroy").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::cheesetrayfiller",
                Resource.local().load("paginae/amber/cheesetrayfiller"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new FillCheeseTray(ui.gui), "FillCheeseTray").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::equipweapon",
                Resource.local().load("paginae/amber/equipweapon"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new EquipWeapon(ui.gui), "EquipWeapon").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::equipsacks",
                Resource.local().load("paginae/amber/equipsacks"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new EquipSacks(ui.gui), "EquipSacks").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::equipswordshield",
                Resource.local().load("paginae/amber/equipswordshield"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new EquipSwordShield(ui.gui), "EquipSwordShield").start();
                    }
                }
        ));//
        addSpecial(new SpecialPagina(this, "paginae::amber::bunnyshoes",
                Resource.local().load("paginae/amber/bunnyshoes"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new EquipSlippers(ui.gui), "bunnyshoes").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::rusalka",
                Resource.local().load("paginae/amber/rusalka"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new EquipRusalka(ui.gui), "rusalka").start();
                    }
                }
        ));//
        addSpecial(new SpecialPagina(this, "paginae::amber::slicecheese",
                Resource.local().load("paginae/amber/SliceCheese"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new SliceCheese(ui.gui), "SliceCheese").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::oysteropener",
                Resource.local().load("paginae/amber/OysterOpener"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new OysterOpener(ui.gui), "OysterOpener").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::splitlogs",
                Resource.local().load("paginae/amber/SplitLogs"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new SplitLogs(ui.gui), "SplitLogs").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::countgobs",
                Resource.local().load("paginae/amber/CountGobs"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new CountGobs(ui.gui), "CountGobs").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::mothkillers",
                Resource.local().load("paginae/amber/MothKiller"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new MothKiller(ui.gui), "MothKiller").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::flaxbot",
                Resource.local().load("paginae/amber/FlaxBot"),
                (pag) -> {

                    if (ui.gui != null) {
                        if (ui.gui.getwnd("Flax Bot") == null) {
                            FlaxBot sw = new FlaxBot(ui.gui);
                            ui.gui.map.flaxbot = sw;
                            ui.gui.add(sw, new Coord(ui.gui.sz.x / 2 - sw.sz.x / 2, ui.gui.sz.y / 2 - sw.sz.y / 2 - 200));
                        }
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::pepperbotpro",
                Resource.local().load("paginae/amber/PepperBotPro"),
                (pag) -> {

                    if (ui.gui != null) {
                        if (ui.gui.getwnd("Pepper Bot") == null) {
                            PepperBotPro sw = new PepperBotPro();
                            ui.gui.map.pepperbotpro = sw;
                            ui.gui.add(sw, new Coord(ui.gui.sz.x / 2 - sw.sz.x / 2, ui.gui.sz.y / 2 - sw.sz.y / 2 - 200));
                            synchronized (GobSelectCallback.class) {
                                // ui.gui.map.registerAreaSelect(sw);
                                ui.gui.map.registerGobSelect(sw);
                            }
                        }
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::taketrays",
                Resource.local().load("paginae/amber/TakeTrays"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new TakeTrays(ui.gui), "TakeTrays").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::pepperfood",
                Resource.local().load("paginae/amber/PepperFood"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new PepperFood(ui.gui), "PepperFood").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::placetrays",
                Resource.local().load("paginae/amber/PlaceTrays"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new PlaceTrays(ui.gui), "PlaceTrays").start();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::farmer",
                Resource.local().load("paginae/purus/farmer"),
                (pag) -> {

                    if (ui.gui != null) {
                        Farmer f = new Farmer();
                        Window w = f;
                        ui.gui.add(w, new Coord(ui.gui.sz.x / 2 - w.sz.x / 2, ui.gui.sz.y / 2 - w.sz.y / 2 - 200));
                        synchronized (GobSelectCallback.class) {
                            ui.gui.map.registerAreaSelect(f);
                            ui.gui.map.registerGobSelect(f);
                        }
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::farmer2",
                Resource.local().load("paginae/purus/farmer2"),
                (pag) -> {

                    if (ui.gui != null) {
                        Farmer2 f = new Farmer2();
                        Window w = f;
                        ui.gui.add(w, new Coord(ui.gui.sz.x / 2 - w.sz.x / 2, ui.gui.sz.y / 2 - w.sz.y / 2 - 200));
                        synchronized (GobSelectCallback.class) {
                            ui.gui.map.registerAreaSelect(f);
                            ui.gui.map.registerGobSelect(f);
                        }
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::flowerpicker",
                Resource.local().load("paginae/purus/flowerPicker"),
                (pag) -> {
                    new Thread(new FlowerPicker(ui.gui)).start();
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::troughfill",
                Resource.local().load("paginae/purus/troughfill"),
                (pag) -> {

                    if (ui.gui != null) {
                        TroughFiller tf = new TroughFiller();
                        ui.gui.add(tf, new Coord(ui.gui.sz.x / 2 - tf.sz.x / 2, ui.gui.sz.y / 2 - tf.sz.y / 2 - 200));
                        synchronized (GobSelectCallback.class) {
                            ui.gui.map.registerGobSelect(tf);
                        }
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::stockpilefill",
                Resource.local().load("paginae/purus/stockpilefill"),
                (pag) -> {

                    if (ui.gui != null) {
                        StockpileFiller spf = new StockpileFiller();
                        ui.gui.add(spf, new Coord(ui.gui.sz.x / 2 - spf.sz.x / 2, ui.gui.sz.y / 2 - spf.sz.y / 2 - 200));
                        synchronized (GobSelectCallback.class) {
                            ui.gui.map.registerGobSelect(spf);
                        }
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::amber::dismount",
                Resource.local().load("paginae/amber/dismount"),
                (pag) -> {

                    if (ui.gui != null) {
                        new Thread(new Dismount(ui.gui), "Dismount").start();
                    }
                }
        ));
        if (Config.showPBot) {
            addSpecial(new SpecialPagina(this, "paginae::amber::pbotmenu",
                    Resource.local().load("paginae/purus/PBotMenu"),
                    (pag) -> {

                        if (ui.gui != null) {
                            if (ui.gui.PBotScriptlist.show(!ui.gui.PBotScriptlist.visible)) {
                                ui.gui.PBotScriptlist.raise();
                                ui.gui.fitwdg(ui.gui.PBotScriptlist);
                                setfocus(ui.gui.PBotScriptlist);
                            }
                        }
                    }
            ));
        }
        if (Config.showPBotOld) {
            addSpecial(new SpecialPagina(this, "paginae::amber::pbotmenuold",
                    Resource.local().load("paginae/purus/PBotMenuOld"),
                    (pag) -> {

                        if (ui.gui != null) {
                            if (ui.gui.PBotScriptlistold.show(!ui.gui.PBotScriptlistold.visible)) {
                                ui.gui.PBotScriptlistold.raise();
                                ui.gui.fitwdg(ui.gui.PBotScriptlistold);
                                setfocus(ui.gui.PBotScriptlistold);
                            }
                        }
                    }
            ));
        }

        addSpecial(new SpecialPagina(this, "paginae::windows::chat",
                Resource.local().load("paginae/windows/chat"),
                (pag) -> {

                    if (ui.gui != null) {
                        ui.gui.OpenChat();
                    }
                }
        ));

        /*
        addSpecial(new SpecialPagina(this, "paginae::windows::char",
                Resource.local().load("paginae/windows/char"),
                (pag) -> {

                    if(ui.gui != null){
                       ui.gui.toggleCharWnd();
                    }}
        ));

        addSpecial(new SpecialPagina(this, "paginae::windows::equ",
                Resource.local().load("paginae/windows/equ"),
                (pag) -> {

                    if(ui.gui != null){
                      ui.gui.toggleEquipment();
                    }}
        ));
        addSpecial(new SpecialPagina(this, "paginae::windows::inv",
                Resource.local().load("paginae/windows/inv"),
                (pag) -> {

                    if(ui.gui != null){
                      ui.gui.toggleInv();
                    }}
        ));
        addSpecial(new SpecialPagina(this, "paginae::windows::kithnkin",
                Resource.local().load("paginae/windows/kithnkin"),
                (pag) -> {

                    if(ui.gui != null){
                      ui.gui.toggleKinList();
                    }}
        ));
        addSpecial(new SpecialPagina(this, "paginae::windows::search",
                Resource.local().load("paginae/windows/search"),
                (pag) -> {

                    if(ui.gui != null){
                      ui.gui.toggleSearch();
                    }}
        ));
        */

        if (!Config.lockedmainmenu) {
            addSpecial(new SpecialPagina(this, "paginae::char",
                    Resource.local().load("paginae/mainmenu/char"),
                    (pag) -> {

                        if (ui.gui != null) {
                            ui.gui.toggleCharWnd();
                        }
                    }
            ));

            addSpecial(new SpecialPagina(this, "paginae::equ",
                    Resource.local().load("paginae/mainmenu/equ"),
                    (pag) -> {

                        if (ui.gui != null) {
                            ui.gui.toggleEquipment();
                        }
                    }
            ));
            addSpecial(new SpecialPagina(this, "paginae::inv",
                    Resource.local().load("paginae/mainmenu/inv"),
                    (pag) -> {

                        if (ui.gui != null) {
                            ui.gui.toggleInv();
                        }
                    }
            ));
            addSpecial(new SpecialPagina(this, "paginae::kithnkin",
                    Resource.local().load("paginae/mainmenu/kithnkin"),
                    (pag) -> {

                        if (ui.gui != null) {
                            ui.gui.toggleKinList();
                        }
                    }
            ));
            addSpecial(new SpecialPagina(this, "paginae::search",
                    Resource.local().load("paginae/mainmenu/search"),
                    (pag) -> {

                        if (ui.gui != null) {
                            ui.gui.toggleSearch();
                        }
                    }
            ));

            addSpecial(new SpecialPagina(this, "paginae::options",
                    Resource.local().load("paginae/mainmenu/opt"),
                    (pag) -> {

                        if (ui.gui != null) {
                            ui.gui.toggleOptions();
                        }
                    }
            ));
        }


        addSpecial(new SpecialPagina(this, "paginae::windows::smap",
                Resource.local().load("paginae/windows/smap"),
                (pag) -> {

                    if (ui.gui != null) {
                        ui.gui.toggleMinimap();
                    }
                }
        ));
        addSpecial(new SpecialPagina(this, "paginae::windows::study",
                Resource.local().load("paginae/windows/study"),
                (pag) -> {

                    if (ui.gui != null) {
                        ui.gui.toggleStudy();
                    }
                }
        ));


        addSpecial(new SpecialPagina(this, "paginae::windows::alerted",
                Resource.local().load("paginae/windows/alerted"),
                (pag) -> ui.gui.toggleAlerted()));
        addSpecial(new SpecialPagina(this, "paginae::windows::hidden",
                Resource.local().load("paginae/windows/hidden"),
                (pag) -> ui.gui.toggleHidden()));
        addSpecial(new SpecialPagina(this, "paginae::windows::deleted",
                Resource.local().load("paginae/windows/deleted"),
                (pag) -> ui.gui.toggleDeleted()));
        addSpecial(new SpecialPagina(this, "paginae::windows::highlight",
                Resource.local().load("paginae/windows/highlight"),
                (pag) -> ui.gui.toggleHighlight()));
        addSpecial(new SpecialPagina(this, "paginae::windows::overlay",
                Resource.local().load("paginae/windows/highlight"), //FIXME ingame setting title and picture
                (pag) -> ui.gui.toggleOverlay()));
        addSpecial(new SpecialPagina(this, "paginae::windows::gobspawner",
                Resource.local().load("paginae/windows/gobspawner"),
                (pag) -> ui.gui.toggleGobSpawner()));

        addSpecial(new SpecialPagina(this, "paginae::windows::lmap",
                Resource.local().load("paginae/windows/lmap"),
                (pag) -> ui.gui.toggleMapfile()));

        addSpecial(new SpecialPagina(this, "paginae::decks::deck1",
                Resource.local().load("paginae/decks/deck1"),
                (pag) -> ui.gui.changeDecks(0)));

        addSpecial(new SpecialPagina(this, "paginae::decks::deck2",
                Resource.local().load("paginae/decks/deck2"),
                (pag) -> ui.gui.changeDecks(1)));

        addSpecial(new SpecialPagina(this, "paginae::decks::deck3",
                Resource.local().load("paginae/decks/deck3"),
                (pag) -> ui.gui.changeDecks(2)));

        addSpecial(new SpecialPagina(this, "paginae::decks::deck4",
                Resource.local().load("paginae/decks/deck4"),
                (pag) -> ui.gui.changeDecks(3)));

        addSpecial(new SpecialPagina(this, "paginae::decks::deck5",
                Resource.local().load("paginae/decks/deck5"),
                (pag) -> ui.gui.changeDecks(4)));

    }

    protected void updlayout() {
        synchronized (paginae) {
            List<PagButton> cur = new ArrayList<>();
            recons = !cons(this.cur, cur);
            cur.sort(Comparator.comparing(PagButton::sortkey));
            this.curbtns = cur;
            int i = curoff;
            hotmap.clear();
            for (PagButton btn : cur) {
                char hk = btn.hotkey();
                if (hk != 0)
                    hotmap.put(Character.toUpperCase(hk), btn);
            }
            for (int y = 0; y < gsz.y; y++) {
                for (int x = 0; x < gsz.x; x++) {
                    PagButton btn = null;
                    if ((this.cur != null) && (x == gsz.x - 1) && (y == gsz.y - 1)) {
                        btn = bk;
                    } else if ((cur.size() > ((gsz.x * gsz.y) - 1)) && (x == gsz.x - 2) && (y == gsz.y - 1)) {
                        btn = next;
                    } else if (i < cur.size()) {
                        btn = cur.get(i++);
                    }
                    layout[x][y] = btn;
                }
            }
        }
    }

    private static Map<PagButton, Tex> glowmasks = new WeakHashMap<>();

    private Tex glowmask(PagButton pag) {
        Tex ret = glowmasks.get(pag);
        if (ret == null) {
            ret = new TexI(PUtils.glowmask(PUtils.glowmask(pag.img().getRaster()), 4, new Color(32, 255, 32)));
            glowmasks.put(pag, ret);
        }
        return (ret);
    }

    private void selectCraft(Pagina r) {
        if (r == null) {
            return;
        }
        if (ui.gui.craftwnd != null) {
            ui.gui.craftwnd.select(r, true);
        }
    }

    public void draw(GOut g) {
        double now = Utils.rtime();
        for (int y = 0; y < gsz.y; y++) {
            for (int x = 0; x < gsz.x; x++) {
                Coord p = bgsz.mul(new Coord(x, y));
                g.image(Inventory.invsq, p);
                PagButton btn = layout[x][y];
                if (btn != null) {
                    Pagina info = btn.pag;
                    Tex btex = info.img.get();
                    g.image(btex, p.add(1, 1));
                    if (info.meter > 0) {
                        double m = info.meter;
                        if (info.dtime > 0)
                            m += (1 - m) * (now - info.gettime) / info.dtime;
                        m = Utils.clip(m, 0, 1);
                        g.chcolor(255, 255, 255, 128);
                        g.fellipse(p.add(bgsz.div(2)), bgsz.div(2), Math.PI / 2, ((Math.PI / 2) + (Math.PI * 2 * m)));
                        g.chcolor();
                    }
                    if (info.newp != 0) {
                        if (info.fstart == 0) {
                            info.fstart = now;
                        } else {
                            double ph = (now - info.fstart) - (((x + (y * gsz.x)) * 0.15) % 1.0);
                            if (ph < 1.25) {
                                g.chcolor(255, 255, 255, (int) (255 * ((Math.cos(ph * Math.PI * 2) * -0.5) + 0.5)));
                                g.image(glowmask(btn), p.sub(4, 4));
                                g.chcolor();
                            } else {
                                g.chcolor(255, 255, 255, 128);
                                g.image(glowmask(btn), p.sub(4, 4));
                                g.chcolor();
                            }
                        }
                    }
                    if (btn == pressed) {
                        g.chcolor(new Color(0, 0, 0, 128));
                        g.frect(p.add(1, 1), btex.sz());
                        g.chcolor();
                    }
                }
            }
        }
        super.draw(g);
        if (dragging != null) {
            Tex dt = dragging.img.get();
            ui.drawafter(new UI.AfterDraw() {
                public void draw(GOut g) {
                    g.image(dt, ui.mc.add(dt.sz().div(2).inv()));
                }
            });
        }
    }

    private PagButton curttp = null;
    private boolean curttl = false;
    private Tex curtt = null;
    private double hoverstart;

    public Object tooltip(Coord c, Widget prev) {
        PagButton pag = bhit(c);
        double now = Utils.rtime();
        if (pag != null) {
            if (prev != this)
                hoverstart = now;
            boolean ttl = (now - hoverstart) > 0.5;
            if ((pag != curttp) || (ttl != curttl)) {
                try {
                    BufferedImage ti = pag.rendertt(ttl);
                    curtt = (ti == null) ? null : new TexI(ti);
                } catch (Loading l) {
                    return (null);
                }
                curttp = pag;
                curttl = ttl;
            }
            return (curtt);
        } else {
            hoverstart = now;
            return (null);
        }
    }

    private PagButton bhit(Coord c) {
        Coord bc = c.div(bgsz);
        if ((bc.x >= 0) && (bc.y >= 0) && (bc.x < gsz.x) && (bc.y < gsz.y))
            return (layout[bc.x][bc.y]);
        else
            return (null);
    }


    public boolean mousedown(Coord c, int button) {
        PagButton h = bhit(c);
        if ((button == 1) && (h != null)) {
            pressed = h;
            grab = ui.grabmouse(this);
            return (true);
        } else {
            return true;
            //  return super.mousedown(c, button);
        }
    }

    public void mousemove(Coord c) {
        if ((dragging == null) && (pressed != null)) {
            PagButton h = bhit(c);
            if (h != pressed)
                dragging = pressed.pag;
        } else {
            super.mousemove(c);
        }
    }


    public void use(PagButton r, boolean reset) {
        Collection<PagButton> sub = new ArrayList<>();
        cons(r.pag, sub);
        selectCraft(r.pag);
        if (sub.size() > 0) {
            this.cur = r.pag;
            curoff = 0;
        } else if (r.pag == bk.pag) {
            if ((curoff - cap) >= 0)
                curoff -= cap;
            else {
                this.cur = paginafor(this.cur.act().parent);
                curoff = 0;
            }
            selectCraft(this.cur);
        } else {
            Resource.AButton act = r.pag.act();
            if (act == null) {
                r.use();
            } else {
                String[] ad = r.pag.act().ad;
                if (ad.length > 0 && (ad[0].equals("craft") || ad[0].equals("bp"))) {
                    lastCraft = r.pag;
                    ui.gui.histbelt.push(r.pag);
                }
                if (Config.confirmmagic && r.res.name.startsWith("paginae/seid/") && !r.res.name.equals("paginae/seid/rawhide")) {
                    Window confirmwnd = new Window(new Coord(225, 100), "Confirm") {
                        @Override
                        public void wdgmsg(Widget sender, String msg, Object... args) {
                            if (sender == cbtn)
                                reqdestroy();
                            else
                                super.wdgmsg(sender, msg, args);
                        }

                        @Override
                        public boolean type(char key, KeyEvent ev) {
                            if (key == 27) {
                                reqdestroy();
                                return true;
                            }
                            return super.type(key, ev);
                        }
                    };

                    confirmwnd.add(new Label(Resource.getLocString(Resource.BUNDLE_LABEL, "Using magic costs experience points. Are you sure you want to proceed?")),
                            new Coord(10, 20));
                    confirmwnd.pack();

                    MenuGrid mg = this;
                    Button yesbtn = new Button(70, "Yes") {
                        @Override
                        public void click() {
                            r.use();
                            parent.reqdestroy();
                        }
                    };
                    confirmwnd.add(yesbtn, new Coord(confirmwnd.sz.x / 2 - 60 - yesbtn.sz.x, 60));
                    Button nobtn = new Button(70, "No") {
                        @Override
                        public void click() {
                            parent.reqdestroy();
                        }
                    };
                    confirmwnd.add(nobtn, new Coord(confirmwnd.sz.x / 2 + 20, 60));
                    confirmwnd.pack();

                    ui.gui.add(confirmwnd, new Coord(ui.gui.sz.x / 2 - confirmwnd.sz.x / 2, ui.gui.sz.y / 2 - 200));
                    confirmwnd.show();
                } else {
                    r.pag.newp = 0;
                    r.use();
                    if (reset) {
                        this.cur = null;
                        curoff = 0;
                    }
                }
            }
        }
        updlayout();
    }


    public void senduse(String... ad) {
        wdgmsg("act", (Object[]) ad);
    }

    public void tick(double dt) {
        if (recons)
            updlayout();

        if (togglestuff) {
            if (Config.enabletracking && !GameUI.trackon) {
                wdgmsg("act", new Object[]{"tracking"});
                ui.gui.trackautotgld = true;
            }
            if (Config.autoconnectarddiscord && !discordconnected) {
                new Thread(new Discord(ui.gui, "ard")).start();
                ui.gui.discordconnected = true;
            }
            if (Config.autoconnectdiscord && !ui.gui.discordconnected) {
                if (Resource.getLocString(Resource.BUNDLE_LABEL, Config.discordbotkey) != null) {
                    new Thread(new Discord(ui.gui, "normal")).start();
                    ui.gui.discordconnected = true;
                }
            } else if (ui.gui.discordconnected)
                PBotUtils.sysMsg(ui, "Discord is already connected, you can only connect to one server at a time.", Color.white);

            if (Config.enablecrime && !GameUI.crimeon) {
                ui.gui.crimeautotgld = true;
                wdgmsg("act", new Object[]{"crime"});
            }
            if (Config.enableswimming && !GameUI.swimon) {
                ui.gui.swimautotgld = true;
                wdgmsg("act", new Object[]{"swim"});
            }
            if (Config.autowindows.get("Belt").selected) {
                WItem l = ui.gui.getequipory().quickslots[5];
                if (l != null)
                    l.item.wdgmsg("iact", Coord.z, -1);
            }
            for (Widget w = ui.gui.chat.lchild; w != null; w = w.prev) {
                if (w instanceof ChatUI.MultiChat) {
                    ChatUI.MultiChat chat = (ChatUI.MultiChat) w;
                    if (Config.chatalert != null) {
                        if (chat.name().equals(Resource.getLocString(Resource.BUNDLE_LABEL, Config.chatalert))) {
                            chat.select();
                            chat.getparent(ChatUI.class).expand();
                            break;
                        }
                    } else if (chat.name().equals(Resource.getLocString(Resource.BUNDLE_LABEL, "Area Chat"))) {
                        chat.select();
                        chat.getparent(ChatUI.class).expand();
                        break;
                    }
                }
            }

            if (!Config.autowindows.get("Quest Log").selected)
                ui.gui.questwnd.hide();
            if (Config.autowindows.get("Craft window").selected)
                ui.gui.toggleCraftDB();

            togglestuff = false;
        }
    }

    public boolean mouseup(Coord c, int button) {
        PagButton h = bhit(c);
        if ((button == 1) && (grab != null)) {
            if (dragging != null) {
                if (!(dragging instanceof SpecialPagina)) {
                    ui.dropthing(ui.root, ui.mc, dragging.res());
                } else {
                    ui.dropthing(ui.root, ui.mc, dragging);
                }
                pressed = null;
                dragging = null;
            } else if (pressed != null) {
                if (pressed == h)
                    use(h, false);
                pressed = null;
            }
            grab.remove();
            grab = null;
            return (true);
        } else {
            return super.mouseup(c, button);
        }
    }

    public void uimsg(String msg, Object... args) {
        if (msg == "goto") {
            if (args[0] == null)
                cur = null;
            else
                cur = paginafor(ui.sess.getres((Integer) args[0]));
            curoff = 0;
            updlayout();
        } else if (msg == "fill") {
            synchronized (paginae) {
                int a = 0;
                while (a < args.length) {
                    int fl = (Integer) args[a++];
                    Pagina pag = paginafor(ui.sess.getres((Integer) args[a++]));
                    if ((fl & 1) != 0) {
                        pag.state(Pagina.State.ENABLED);
                        pag.meter = 0;
                        if ((fl & 2) != 0)
                            pag.state(Pagina.State.DISABLED);
                        if ((fl & 4) != 0) {
                            pag.meter = ((Number) args[a++]).doubleValue() / 1000.0;
                            pag.gettime = Utils.rtime();
                            pag.dtime = ((Number) args[a++]).doubleValue() / 1000.0;
                        }
                        if ((fl & 8) != 0)
                            pag.newp = 1;
                        if ((fl & 16) != 0)
                            pag.rawinfo = (Object[]) args[a++];
                        else
                            pag.rawinfo = new Object[0];

                        // this is very crappy way to do this. needs to be redone probably
                        /*try {
                            Resource res = pag.res.get();
                            if (res.name.equals("ui/tt/q/quality"))
                                continue;
                        } catch (Loading l) {
                        }*/

                        paginae.add(pag);
                    } else {
                        paginae.remove(pag);
                    }
                }
                updlayout();
                pagseq++;
            }
        } else {
            super.uimsg(msg, args);
        }
    }

    public boolean globtype(char k, KeyEvent ev) {
        if (Config.disablemenugrid)
            return false;
        if (ev.isShiftDown() || ev.isAltDown()) {
            return false;
        } else if ((k == 27) && (this.cur != null)) {
            this.cur = null;
            curoff = 0;
            updlayout();
            return (true);
        } else if ((k == 8) && (this.cur != null)) {
            this.cur = paginafor(this.cur.act().parent);
            curoff = 0;
            updlayout();
            return (true);
        } else if ((k == 'N') && (layout[gsz.x - 2][gsz.y - 1] == next)) {
            use(next, false);
            return (true);
        }
        PagButton r = hotmap.get(Character.toUpperCase(k));
        if (r != null) {
            if (Config.disablemagaicmenugrid && r.res.name.startsWith("paginae/seid/"))
                return (false);
            use(r, true);
            return (true);
        }
        return (false);
    }

    public boolean isCrafting(Pagina p) {
        return (p != null) && (isCrafting(p.res()) || isCrafting(getParent(p)));
    }

    public Pagina getPagina(String ad) {
        for (Pagina p : paginae) {
            if (p.act().ad.length > 1) {
                if (p.act().ad[1].equals(ad)) {
                    return p;
                }
            }
        }
        return null;
    }

    public boolean isCrafting(Resource res) {
        return res.name.contains("paginae/act/craft");
    }

    public Pagina getParent(Pagina p) {
        if (p == null) {
            return null;
        }
        try {
            Resource res = p.res();
            Resource.AButton ad = res.layer(Resource.action);
            if (ad == null)
                return null;
            return paginafor(ad.parent);
        } catch (Loading e) {
            return null;
        }
    }

    public boolean isChildOf(Pagina item, Pagina parent) {
        Pagina p;
        while ((p = getParent(item)) != null) {
            if (p == parent) {
                return true;
            }
            item = p;
        }
        return false;
    }
}

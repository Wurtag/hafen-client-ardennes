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

import haven.GLProgram.VarID;
import haven.automation.AreaSelectCallback;
import haven.automation.CoalToSmelters;
import haven.automation.FlaxBot;
import haven.automation.GobSelectCallback;
import haven.automation.LightWithTorch;
import haven.automation.MinerAlert;
import haven.automation.MusselPicker;
import haven.automation.PepperBot;
import haven.automation.PepperBotPro;
import haven.automation.ShieldChecker;
import haven.automation.ShooTargeted;
import haven.automation.SteelRefueler;
import haven.overlays.OverlaySelector;
import haven.pathfinder.PFListener;
import haven.pathfinder.Pathfinder;
import haven.purus.Farmer;
import haven.purus.pbot.PBotUtils;
import haven.resutil.BPRadSprite;
import haven.sloth.gob.Alerted;
import haven.sloth.gob.Deleted;
import haven.sloth.gob.Hidden;
import haven.sloth.gob.Mark;
import haven.sloth.gob.Type;
import haven.sloth.gui.SoundSelector;
import haven.sloth.io.HighlightData;
import haven.sloth.script.pathfinding.Move;
import haven.sloth.script.pathfinding.NBAPathfinder;
import modification.configuration;

import javax.media.opengl.GL;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

import static haven.DefSettings.DARKMODE;
import static haven.DefSettings.DRAWGRIDRADIUS;
import static haven.DefSettings.NIGHTVISION;
import static haven.DefSettings.NVAMBIENTCOL;
import static haven.DefSettings.NVDIFFUSECOL;
import static haven.DefSettings.NVSPECCOC;
import static haven.DefSettings.SHOWKCLAIM;
import static haven.DefSettings.SHOWPCLAIM;
import static haven.DefSettings.SHOWVCLAIM;
import static haven.MCache.tilesz;
import static haven.OCache.posres;

public class MapView extends PView implements DTarget, Console.Directory, PFListener {
    public static long plgobid;
    public static boolean clickdb = false;
    public boolean foundPath = false;
    public long plgob = -1;
    public static Coord2d pllastcc;
    public Coord2d cc;
    public String curcamera;
    public final Glob glob;
    public int view = DRAWGRIDRADIUS.get();//(Config.lowerterraindistance ? 1 : 2);
    private Collection<Delayed> delayed = new LinkedList<Delayed>();
    private Collection<Delayed> delayed2 = new LinkedList<Delayed>();
    private Collection<Rendered> extradraw = new LinkedList<Rendered>();
    public Camera camera = restorecam();
    private Plob placing = null;
    private int[] visol = new int[32];
    private Grabber grab;
    public Selector selection;
    private MCache.Overlay miningOverlay;
    private Coord3f camoff = new Coord3f(Coord3f.o);
    public double shake = 0.0;
    public static int plobgran = Utils.getprefi("placegridval", 8);
    private static final Map<String, Class<? extends Camera>> camtypes = new HashMap<String, Class<? extends Camera>>();
    public String tooltip;
    private boolean showgrid;
    private TileOutline gridol;
    private Coord lasttc = Coord.z;
    public static Gob.Overlay rovlsupport = new Gob.Overlay(new BPRadSprite(100.0F, 0, BPRadSprite.smatSupports));
    public static Gob.Overlay rovlcolumn = new Gob.Overlay(new BPRadSprite(125.0F, 0, BPRadSprite.smatSupports));
    public static Gob.Overlay rovlbeam = new Gob.Overlay(new BPRadSprite(150.0F, 0, BPRadSprite.smatSupports));
    public static Gob.Overlay rovltrough = new Gob.Overlay(new BPRadSprite(200.0F, -10.0F, BPRadSprite.smatTrough));
    public static Gob.Overlay rovlbeehive = new Gob.Overlay(new BPRadSprite(151.0F, -10.0F, BPRadSprite.smatBeehive));
    private long lastmmhittest = System.currentTimeMillis();
    private Coord lasthittestc = Coord.z;
    private GobSelectCallback gobselcb;
    private AreaSelectCallback areaselcb;
    private Pathfinder pf;
    public Thread pfthread;
    public SteelRefueler steelrefueler;
    public Farmer farmer;
    public ShieldChecker shieldchecker;
    public LightWithTorch torchlight;
    public static Gob shooanimal;
    public CoalToSmelters coaltosmelters;
    public PepperBot pepperbot;
    public PepperBotPro pepperbotpro;
    public FlaxBot flaxbot;
    public MinerAlert mineralert;
    private Thread musselPicker;
    private Thread clayPicker;
    private final PartyHighlight partyHighlight;
    public static final Set<Long> markedGobs = new HashSet<>();
    public static final Material.Colors markedFx = new Material.Colors(new Color(21, 127, 208, 255));
    public Object[] lastItemactClickArgs;
    private static TexCube sky = new TexCube(Resource.loadimg("skycube"));
    private static DropSky skydrop = new DropSky(sky);
    public boolean farmSelect = false;
    public boolean PBotAPISelect = false;
    private Coord2d movingto;
    private Coord2d lastrc;
    private double mspeed, totaldist = 0, mspeedavg, totaldt = 0;
    public haven.purus.pathfinder.Pathfinder pastaPathfinder;
    private long lastMove = System.currentTimeMillis();
    private Queue<Coord2d> movequeue = new ArrayDeque<>();
    private List<Coord2d> questQueue = new ArrayList<>();
    private Gob pathfindGob;
    private int pathfindGobMod = 0;
    private String lasttt = "";
    private Object tt;

    private boolean ismousedown = false;

    public interface Delayed {
        public void run(GOut g);
    }

    public interface Grabber {
        boolean mmousedown(Coord mc, int button);

        boolean mmouseup(Coord mc, int button);

        boolean mmousewheel(Coord mc, int amount);

        void mmousemove(Coord mc);
    }

    public abstract class Camera extends GLState.Abstract {
        protected haven.Camera view = new haven.Camera(Matrix4f.identity());
        protected Projection proj = new Projection(Matrix4f.identity());

        public Camera() {
            resized();
        }

        public boolean keydown(KeyEvent ev) {
            return (false);
        }

        public boolean click(Coord sc) {
            return (false);
        }

        public void drag(Coord sc) {
        }

        public void release() {
        }

        public boolean wheel(Coord sc, int amount) {
            return (false);
        }

        public void resized() {
            float field = 0.5f;
            float aspect = ((float) sz.y) / ((float) sz.x);
            proj.update(Projection.makefrustum(new Matrix4f(), -field, field, -aspect * field, aspect * field, 1, 5000));
        }

        public void prep(Buffer buf) {
            proj.prep(buf);
            view.prep(buf);
        }

        public abstract float angle();

        public abstract void tick(double dt);
    }

    public class FollowCam extends Camera {
        private final float fr = 0.0f, h = 10.0f;
        private float ca, cd;
        private Coord3f curc = null;
        private float elev, telev;
        private float angl, tangl;
        private Coord dragorig = null;
        private float anglorig;

        public FollowCam() {
            elev = telev = (float) Math.PI / 6.0f;
            angl = tangl = 0.0f;
        }

        public void resized() {
            ca = (float) sz.y / (float) sz.x;
            cd = 400.0f * ca;
        }

        public boolean click(Coord c) {
            anglorig = tangl;
            dragorig = c;
            return (true);
        }

        public void drag(Coord c) {
            tangl = anglorig + ((float) (c.x - dragorig.x) / 100.0f);
            tangl = tangl % ((float) Math.PI * 2.0f);
        }

        private double f0 = 0.2, f1 = 0.5, f2 = 0.9;
        private double fl = Math.sqrt(2);
        private double fa = ((fl * (f1 - f0)) - (f2 - f0)) / (fl - 2);
        private double fb = ((f2 - f0) - (2 * (f1 - f0))) / (fl - 2);

        private float field(float elev) {
            double a = elev / (Math.PI / 4);
            return ((float) (f0 + (fa * a) + (fb * Math.sqrt(a))));
        }

        private float dist(float elev) {
            float da = (float) Math.atan(ca * field(elev));
            return ((float) (((cd - (h / Math.tan(elev))) * Math.sin(elev - da) / Math.sin(da)) - (h / Math.sin(elev))));
        }

        public void tick(double dt) {
            elev += (telev - elev) * (float) (1.0 - Math.pow(500, -dt));
            if (Math.abs(telev - elev) < 0.0001)
                elev = telev;

            float dangl = tangl - angl;
            while (dangl > Math.PI) dangl -= (float) (2 * Math.PI);
            while (dangl < -Math.PI) dangl += (float) (2 * Math.PI);
            angl += dangl * (float) (1.0 - Math.pow(500, -dt));
            if (Math.abs(tangl - angl) < 0.0001)
                angl = tangl;

            // Coord3f cc = getcc();
            Coord3f cc = getcc_old();
            if (Config.disableelev)
                cc.z = 0;
            cc.y = -cc.y;
            if (curc == null)
                curc = cc;
            float dx = cc.x - curc.x, dy = cc.y - curc.y;
            float dist = (float) Math.sqrt((dx * dx) + (dy * dy));
            if (dist > 250) {
                curc = cc;
            } else if (dist > fr) {
                Coord3f oc = curc;
                float pd = (float) Math.cos(elev) * dist(elev);
                Coord3f cambase = new Coord3f(curc.x + ((float) Math.cos(tangl) * pd), curc.y + ((float) Math.sin(tangl) * pd), 0.0f);
                float a = cc.xyangle(curc);
                float nx = cc.x + ((float) Math.cos(a) * fr), ny = cc.y + ((float) Math.sin(a) * fr);
                Coord3f tgtc = new Coord3f(nx, ny, cc.z);
                curc = curc.add(tgtc.sub(curc).mul((float) (1.0 - Math.pow(500, -dt))));
                if (curc.dist(tgtc) < 0.01)
                    curc = tgtc;
                tangl = curc.xyangle(cambase);
            }

            float field = field(elev);
            view.update(PointedCam.compute(curc.add(camoff).add(0.0f, 0.0f, h), dist(elev), elev, angl));
            proj.update(Projection.makefrustum(new Matrix4f(), -field, field, -ca * field, ca * field, 1, 5000));
        }

        public float angle() {
            return (angl);
        }

        private static final float maxang = (float) (Math.PI / 2 - 0.1);
        private static final float mindist = 50.0f;

        public boolean wheel(Coord c, int amount) {
            float fe = telev;
            telev += amount * telev * 0.02f;
            if (telev > maxang)
                telev = maxang;
            if (dist(telev) < mindist)
                telev = fe;
            return (true);
        }

        public String toString() {
            return (String.format("%f %f %f", elev, dist(elev), field(elev)));
        }
    }

    static {
        camtypes.put("follow", FollowCam.class);
    }

    public class FreeCam extends Camera {
        private float dist = configuration.badcamdistdefault; //50.0f;
        private float elev = configuration.badcamelevdefault; //(float) Math.PI / 4.0f;
        private float angl = configuration.badcamangldefault; //0.0f;
        private Coord dragorig = null;
        private float elevorig, anglorig;

        public void tick(double dt) {
            Coord3f cc = getcc();
            cc.y = -cc.y;
            if (Config.disableelev)
                cc.z = 0;
            view.update(PointedCam.compute(cc.add(camoff).add(0.0f, 0.0f, 15f), dist, elev, angl));
        }

        public float angle() {
            return (angl);
        }

        public boolean click(Coord c) {
            elevorig = elev;
            anglorig = angl;
            dragorig = c;
            return (true);
        }

        public void drag(Coord c) {
            if (c == null || dragorig == null)
                return;
            if (Config.reversebadcamx)
                c = new Coord(c.x + (dragorig.x - c.x) * 2, c.y);
            if (Config.reversebadcamy)
                c = new Coord(c.x, c.y + (dragorig.y - c.y) * 2);
            elev = elevorig - ((float) (c.y - dragorig.y) / 100.0f);
            if (elev < 0.0f) elev = 0.0f;
            if (elev > (Math.PI / 2.0)) elev = (float) Math.PI / 2.0f;
            angl = anglorig + ((float) (c.x - dragorig.x) / 100.0f);
            angl = angl % ((float) Math.PI * 2.0f);
            configuration.badcamelevdefault = elev;
            configuration.badcamangldefault = angl;
            Utils.setpreff("badcamelevdefault", elev);
            Utils.setpreff("badcamangldefault", angl);
        }

        public boolean wheel(Coord c, int amount) {
            float d = dist + (amount * Config.badcamsensitivity);
            if (d < configuration.badcamdistminimaldefault)
                d = configuration.badcamdistminimaldefault;
            dist = d;
            Utils.setpreff("badcamdistdefault", dist);
            return (true);
        }
    }

    static {
        camtypes.put("bad", FreeCam.class);
    }

    public class TopDownCam extends Camera {
        private final float pi2 = (float) (Math.PI * 2);
        private Coord3f cc;
        private float dist = 500.0f;
        private final float elev = (float) Math.toRadians(88);
        protected float field = (float) (100 * Math.sqrt(2));
        private float tfield = field;
        private Coord dragorig = null;
        private float angl = 0.0f;
        private float tangl = angl;
        private float anglorig;

        public TopDownCam() {
        }

        public void tick2(double dt) {
            Coord3f cc = getcc();
            if (Config.disableelev)
                cc.z = 0;
            cc.y = -cc.y;
            this.cc = cc;
        }

        public void tick(double dt) {
            tick2(dt);
            float aspect = ((float) sz.y) / ((float) sz.x);

            //Smooth transition for angle
            angl = angl + ((tangl - angl) * (1f - (float) Math.pow(500, -dt)));
            while (angl > pi2) {
                angl -= pi2;
                tangl -= pi2;
                anglorig -= pi2;
            }
            while (angl < 0) {
                angl += pi2;
                tangl += pi2;
                anglorig += pi2;
            }
            if (Math.abs(tangl - angl) < 0.001)
                angl = tangl;

            //Smooth transition for zoom in/out
            field = field + ((tfield - field) * (1f - (float) Math.pow(500, -dt)));
            if (Math.abs(tfield - field) < 0.1)
                field = tfield;

            view.update(PointedCam.compute(cc.add(camoff).add(0.0f, 0.0f, 15f), dist, elev, angl));
            proj.update(Projection.makeortho(new Matrix4f(), -field, field, -field * aspect, field * aspect, 1, 5000));
        }

        public float angle() {
            return (angl);
        }

        public boolean click(Coord c) {
            anglorig = angl;
            dragorig = c;
            return (true);
        }

        public void drag(Coord c) {
            tangl = anglorig + ((float) (c.x - dragorig.x) / 100.0f);
        }

        public void release() {
            tangl = (float) (Math.floor((tangl + Math.PI / 4) / (Math.PI / 2)) * Math.PI / 2);
        }

        private void chfield(float nf) {
            tfield = nf;
            tfield = Math.max(tfield, 50);
        }

        public boolean wheel(Coord c, int amount) {
            chfield(tfield + amount * 10);
            return (true);
        }

        public String toString() {
            return (String.format("%f", dist));
        }
    }

    static {
        camtypes.put("topdown", TopDownCam.class);
    }

    public class OrthoCam extends Camera {
        public boolean exact;
        protected float dist = 500.0f;
        protected float elev = (float) Math.PI / 6.0f;
        protected float angl = -(float) Math.PI / 4.0f;
        protected float field = (float) (100 * Math.sqrt(2));
        private Coord dragorig = null;
        private float anglorig;
        protected Coord3f cc, jc;

        public OrthoCam(boolean exact) {
            this.exact = exact;
        }

        public OrthoCam() {
            this(false);
        }

        public void tick2(double dt) {
            Coord3f cc = getcc();
            cc.y = -cc.y;
            if (Config.disableelev)
                cc.z = 0;
            this.cc = cc;
        }

        public void tick(double dt) {
            tick2(dt);
            float aspect = ((float) sz.y) / ((float) sz.x);
            Matrix4f vm = PointedCam.compute(cc.add(camoff).add(0.0f, 0.0f, 15f), dist, elev, angl);
            if (exact) {
                if (jc == null)
                    jc = cc;
                float pfac = sz.x / (field * 2);
                Coord3f vjc = vm.mul4(jc).mul(pfac);
                Coord3f corr = new Coord3f(Math.round(vjc.x) - vjc.x, Math.round(vjc.y) - vjc.y, 0).div(pfac);
                if ((Math.abs(vjc.x) > 500) || (Math.abs(vjc.y) > 500))
                    jc = null;
                vm = Location.makexlate(new Matrix4f(), corr).mul1(vm);
            }
            view.update(vm);
            proj.update(Projection.makeortho(new Matrix4f(), -field, field, -field * aspect, field * aspect, 1, 5000));
        }

        public float angle() {
            return (angl);
        }

        public boolean click(Coord c) {
            anglorig = angl;
            dragorig = c;
            return (true);
        }

        public void drag(Coord c) {
            angl = anglorig + ((float) (c.x - dragorig.x) / 100.0f);
            angl = angl % ((float) Math.PI * 2.0f);
        }

        public String toString() {
            return (String.format("%f %f %f %f", dist, elev / Math.PI, angl / Math.PI, field));
        }
    }

    public class SOrthoCam extends OrthoCam {
        private Coord dragorig = null;
        private float anglorig;
        private float tangl = angl;
        private float tfield = field;
        private final float pi2 = (float) (Math.PI * 2);

        public SOrthoCam(boolean exact) {
            super(exact);
        }

        public SOrthoCam(String... args) {
            PosixArgs opt = PosixArgs.getopt(args, "e");
            for (char c : opt.parsed()) {
                switch (c) {
                    case 'e':
                        exact = true;
                        break;
                }
            }
        }

        public void tick2(double dt) {
            Coord3f mc = getcc();
            mc.y = -mc.y;
            if (Config.disableelev)
                mc.z = 0;
            if ((cc == null) || (Math.hypot(mc.x - cc.x, mc.y - cc.y) > 250))
                cc = mc;
            else if (!exact || (mc.dist(cc) > 2))
                cc = cc.add(mc.sub(cc).mul(1f - (float) Math.pow(500, -dt)));

            angl = angl + ((tangl - angl) * (1f - (float) Math.pow(500, -dt)));
            while (angl > pi2) {
                angl -= pi2;
                tangl -= pi2;
                anglorig -= pi2;
            }
            while (angl < 0) {
                angl += pi2;
                tangl += pi2;
                anglorig += pi2;
            }
            if (Math.abs(tangl - angl) < 0.001)
                angl = tangl;
            else
                jc = cc;

            field = field + ((tfield - field) * (1f - (float) Math.pow(500, -dt)));
            if (Math.abs(tfield - field) < 0.1)
                field = tfield;
            else
                jc = cc;
        }

        public boolean click(Coord c) {
            anglorig = angl;
            dragorig = c;
            return (true);
        }

        public void drag(Coord c) {
            if (c == null || dragorig == null)
                return;
            tangl = anglorig + ((float) (c.x - dragorig.x) / 100.0f);
        }

        private void chfield(float nf) {
            tfield = nf;
            float zoutlimdiv = Config.enableorthofullzoom ? 4f : 8f;
            tfield = Math.max(Math.min(tfield, sz.x * (float) Math.sqrt(2) / zoutlimdiv), 50);
            if (tfield > 100)
                release();
        }

        public boolean wheel(Coord c, int amount) {
            chfield(tfield + amount * 10);
            return (true);
        }

        public boolean keydown(KeyEvent ev) {
            if (ui.modctrl) {
                if (ev.getKeyCode() == KeyEvent.VK_LEFT) {
                    tangl = (float) (2 * Math.PI);
                    return (true);
                } else if (ev.getKeyCode() == KeyEvent.VK_RIGHT) {
                    tangl = (float) Math.PI;
                    return (true);
                } else if (ev.getKeyCode() == KeyEvent.VK_UP) {
                    tangl = (float) (3 * Math.PI / 2);
                    return (true);
                } else if (ev.getKeyCode() == KeyEvent.VK_DOWN) {
                    tangl = (float) (Math.PI / 2);
                    return (true);
                }
            } else {
                if (ev.getKeyCode() == KeyEvent.VK_LEFT) {
                    tangl = (float) (Math.PI * 0.5 * (Math.floor((tangl / (Math.PI * 0.5)) - 0.51) + 0.5));
                    return (true);
                } else if (ev.getKeyCode() == KeyEvent.VK_RIGHT) {
                    tangl = (float) (Math.PI * 0.5 * (Math.floor((tangl / (Math.PI * 0.5)) + 0.51) + 0.5));
                    return (true);
                } else if (ev.getKeyCode() == KeyEvent.VK_UP) {
                    chfield(tfield - 50);
                    return (true);
                } else if (ev.getKeyCode() == KeyEvent.VK_DOWN) {
                    chfield(tfield + 50);
                    return (true);
                } else if (ev.getKeyCode() == KeyEvent.VK_HOME) {
                    tangl = angl + (float) Utils.cangle(-(float) Math.PI * 0.25f - angl);
                    chfield((float) (100 * Math.sqrt(2)));
                }
            }

            return (false);
        }
    }

    static {
        camtypes.put("ortho", SOrthoCam.class);
    }

    @RName("mapview")
    public static class $_ implements Factory {
        public Widget create(UI ui, Object[] args) {
            Coord sz = (Coord) args[0];
            Coord2d mc = ((Coord) args[1]).mul(posres);
            int pgob = -1;
            if (args.length > 2)
                pgob = (Integer) args[2];
            return (new MapView(sz, ui.sess.glob, mc, pgob));
        }
    }

    public MapView(Coord sz, Glob glob, Coord2d cc, long plgob) {
        super(sz);
        this.glob = glob;
        this.cc = cc;
        this.plgob = plgob;
        plgobid = plgob;
        this.gobs = new Gobs();
        this.gridol = new TileOutline(this);
        this.partyHighlight = new PartyHighlight(glob.party, plgob);
        setcanfocus(true);
        markedGobs.clear();

        if (SHOWPCLAIM.get()) { //TODO look at me
            enol(0, 1);
        }
        if (SHOWVCLAIM.get()) {
            enol(2, 3);
        }
        if (SHOWKCLAIM.get()) {
            enol(4, 5);
        }
    }

    public boolean visol(int ol) {
        return (visol[ol] > 0);
    }

    public void enol(int... overlays) {
        for (int ol : overlays)
            visol[ol]++;
    }

    public void disol(int... overlays) {
        for (int ol : overlays)
            visol[ol]--;
    }

    private final Rendered flavobjs = new Rendered() {
        private Collection<Gob> fol;
        private Coord cc = null;
        private int mseq = 0;
        private boolean loading = false;

        public void draw(GOut g) {
        }

        public Object staticp() {
            Coord cc = MapView.this.cc.floor(tilesz).div(MCache.cutsz);
            int mseq = glob.map.olseq;
            if (loading || !Utils.eq(cc, this.cc) || (mseq != this.mseq)) {
                loading = false;
                Collection<Gob> fol = new ArrayList<Gob>();
                Coord o = new Coord();
                for (o.y = -view; o.y <= view; o.y++) {
                    for (o.x = -view; o.x <= view; o.x++) {
                        try {
                            fol.addAll(glob.map.getfo(cc.add(o)));
                        } catch (Loading e) {
                            loading = true;
                        }
                    }
                }
                this.cc = cc;
                this.mseq = mseq;
                this.fol = fol;
            }
            return (fol);
        }

        public boolean setup(RenderList rl) {
            for (Gob fo : fol)
                addgob(rl, fo);
            return (false);
        }
    };

    private final Rendered map = new Rendered() {
        public void draw(GOut g) {
        }

        public boolean setup(RenderList rl) {
            Coord cc = MapView.this.cc.floor(tilesz).div(MCache.cutsz);
            Coord o = new Coord();
            if (Config.noloadscreen) {
                for (o.y = -view; o.y <= view; o.y++) {
                    for (o.x = -view; o.x <= view; o.x++) {
                        Coord2d pc = cc.add(o).mul(MCache.cutsz).mul(tilesz);
                        try {
                            MapMesh cut = glob.map.getcut(cc.add(o));
                            if (cut != null) {
                                rl.add(cut, Location.xlate(new Coord3f((float) pc.x, -(float) pc.y, 0)));
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            } else {
                for (o.y = -view; o.y <= view; o.y++) {
                    for (o.x = -view; o.x <= view; o.x++) {
                        Coord2d pc = cc.add(o).mul(MCache.cutsz).mul(tilesz);
                        try {
                            MapMesh cut = glob.map.getcut(cc.add(o));
                            if (cut != null) {
                                rl.add(cut, Location.xlate(new Coord3f((float) pc.x, -(float) pc.y, 0)));
                            }
                        } catch (Defer.DeferredException e) {
                            // there seems to be a rare problem with fetching gridcuts when teleporting, not sure why...
                            // we ignore Defer.DeferredException to prevent the client for crashing
                        }
                    }
                }
            }
            if (!Config.hideflocomplete) {
                if (!(rl.state().get(PView.ctx) instanceof ClickContext))
                    rl.add(flavobjs, null);
            }
            return (false);
        }
    };

    private final Rendered grid = new Rendered() {
        public void draw(GOut g) {
        }

        public boolean setup(RenderList rl) {
            Coord cc = MapView.this.cc.floor(tilesz).div(MCache.cutsz);
            Coord o = new Coord();
            for (o.y = -view; o.y <= view; o.y++) {
                for (o.x = -view; o.x <= view; o.x++) {
                    Coord2d pc = cc.add(o).mul(MCache.cutsz).mul(tilesz);
                    try {
                        FastMesh cut = glob.map.getgcut(cc.add(o));
                        if (cut != null) {
                            rl.add(cut, Location.xlate(new Coord3f((float) pc.x, (float) -pc.y, 0)));
                        }
                    } catch (Loading e) {
                    }
                }
            }
            return (false);
        }
    };

    {
        visol[6] = 1;
    }

    private final Rendered mapol = new Rendered() {
        private final GLState[] mats;

        {
            mats = new GLState[32];
            mats[0] = olmat(255, 0, 128, 32);
            mats[1] = olmat(0, 0, 255, 32);
            mats[2] = olmat(255, 0, 0, 32);
            mats[3] = olmat(128, 0, 255, 32);
            mats[4] = olmat(255, 255, 255, 32);
            mats[5] = olmat(0, 255, 128, 32);
            mats[6] = olmat(0, 0, 0, 64);
            mats[16] = olmat(0, 255, 0, 32);
            mats[17] = olmat(255, 255, 0, 32);
            mats[18] = olmat(29, 196, 51, 60);
        }

        private GLState olmat(int r, int g, int b, int a) {
            return (new Material(Light.deflight,
                    new Material.Colors(Color.BLACK, new Color(0, 0, 0, a), Color.BLACK, new Color(r, g, b, 255), 0),
                    States.presdepth));
        }

        public void draw(GOut g) {
        }

        public boolean setup(RenderList rl) {
            Coord cc = MapView.this.cc.floor(tilesz).div(MCache.cutsz);
            Coord o = new Coord();
            for (o.y = -view; o.y <= view; o.y++) {
                for (o.x = -view; o.x <= view; o.x++) {
                    Coord2d pc = cc.add(o).mul(MCache.cutsz).mul(tilesz);
                    for (int i = 0; i < visol.length; i++) {
                        if (mats[i] == null)
                            continue;
                        if (visol[i] > 0) {
                            Rendered olcut;
                            try {
                                olcut = glob.map.getolcut(i, cc.add(o));
                                if (olcut != null)
                                    rl.add(olcut, GLState.compose(Location.xlate(new Coord3f((float) pc.x, -(float) pc.y, 0)), mats[i]));
                            } catch (Loading e) {
                            }
                        }
                    }
                }
            }
            return (false);
        }
    };

    void addgob(RenderList rl, final Gob gob) {
        if (Config.hidegobs && Config.hideCrops && gob.type != null && (gob.type == Type.PLANT || gob.type == Type.MULTISTAGE_PLANT))
            return;
        GLState xf;
        try {
            xf = Following.xf(gob);
        } catch (Loading e) {
            xf = null;
        }
        GLState extra = null;
        if (xf == null) {
            xf = gob.loc;
            try {
                Coord3f c = gob.getc();
                Tiler tile = glob.map.tiler(glob.map.gettile(new Coord2d(c).floor(tilesz)));
                extra = tile.drawstate(glob, rl.cfg, c);
            } catch (Loading e) {
                extra = null;
            }
        }
        rl.add(gob, GLState.compose(extra, xf, gob.olmod, gob.save));

        Gob.Overlay rovl = null;
        boolean show = false;

        if (gob.type == Type.WOODEN_SUPPORT) {
            rovl = rovlsupport;
            show = Config.showminerad;
        } else if (gob.type == Type.STONE_SUPPORT) {
            rovl = rovlcolumn;
            show = Config.showminerad;
        } else if (gob.type == Type.METAL_SUPPORT) {
            rovl = rovlbeam;
            show = Config.showminerad;
        }

        if (gob.type == Type.TROUGH) {
            rovl = rovltrough;
            show = Config.showTroughrad;
        } else if (gob.type == Type.BEEHIVE) {
            rovl = rovlbeehive;
            show = Config.showBeehiverad;
        }

        if (show && !gob.ols.contains(rovl))
            gob.ols.add(rovl);
        else if (!show && rovl != null)
            gob.ols.remove(rovl);
    }

    public static class ChangeSet implements OCache.ChangeCallback {
        public final Set<Gob> changed = new HashSet<Gob>();
        public final Set<Gob> removed = new HashSet<Gob>();

        public void changed(Gob ob) {
            changed.add(ob);
        }

        public void removed(Gob ob) {
            changed.remove(ob);
            removed.add(ob);
        }
    }


    public void toggleCamera() {
        if ((camera != null && !ui.gui.chat.hasfocus) || (camera != null && !ui.gui.chatwnd.visible)) {
            String cam = "";
            if (camera instanceof MapView.FollowCam)
                curcamera = "follow";
            if (camera instanceof MapView.OrthoCam)
                curcamera = "ortho";
            if (camera instanceof MapView.FreeCam)
                curcamera = "bad";
            if (camera instanceof MapView.TopDownCam)
                curcamera = "topdown";

            if (curcamera.equals("follow")) {
                cam = "ortho";
                PBotUtils.sysMsg(ui, "Switched to Ortho Cam", Color.white);
            } else if (curcamera.equals("ortho")) {
                cam = "bad";
                PBotUtils.sysMsg(ui, "Switched to Bad Cam", Color.white);
            } else if (curcamera.equals("bad")) {
                cam = "topdown";
                PBotUtils.sysMsg(ui, "Switched to Topdown Cam", Color.white);
            } else if (curcamera.equals("topdown")) {
                cam = "follow";
                PBotUtils.sysMsg(ui, "Switched to Follow Cam", Color.white);
            }

            // String cam = camera instanceof MapView.OrthoCam ? "bad" : "ortho";
            String[] args = new String[0];
            camera = makecam(camtypes.get(cam), args);
            Utils.setpref("defcam", cam);
            Utils.setprefb("camargs", Utils.serialize(args));
            refreshGobsAll();
        }
    }

    private class Gobs implements Rendered {
        final OCache oc = glob.oc;
        final ChangeSet changed = new ChangeSet();
        final Map<Gob, GobSet> parts = new HashMap<Gob, GobSet>();
        Integer ticks = 0;

        {
            oc.callback(changed);
        }

        class GobSet implements Rendered {
            private final String nm;
            final Collection<Gob> obs = new HashSet<Gob>();
            Object seq = this;

            GobSet(String nm) {
                this.nm = nm;
            }

            void take(Gob ob) {
                obs.add(ob);
                seq = ticks;
            }

            void remove(Gob ob) {
                if (obs.remove(ob))
                    seq = ticks;
            }

            void update() {
            }

            public void draw(GOut g) {
            }

            public boolean setup(RenderList rl) {
                for (Gob gob : obs)
                    addgob(rl, gob);
                return (false);
            }

            public Object staticp() {
                return (seq);
            }

            public int size() {
                return (obs.size());
            }

            public String toString() {
                return ("GobSet(" + nm + ")");
            }
        }

        class Transitory extends GobSet {
            final Map<Gob, Integer> age = new HashMap<Gob, Integer>();

            Transitory(String nm) {
                super(nm);
            }

            void take(Gob ob) {
                super.take(ob);
                age.put(ob, ticks);
            }

            void remove(Gob ob) {
                super.remove(ob);
                age.remove(ob);
            }
        }

        final GobSet oldfags = new GobSet("old");
        final GobSet semistat = new GobSet("semistat");
        final GobSet semifags = new Transitory("semi") {
            int cycle = 0;

            void update() {
                if (++cycle >= 300) {
                    Collection<Gob> cache = new ArrayList<Gob>();
                    for (Map.Entry<Gob, Integer> ob : age.entrySet()) {
                        if (ticks - ob.getValue() > 450)
                            cache.add(ob.getKey());
                    }
                    for (Gob ob : cache)
                        put(oldfags, ob);
                    cycle = 0;
                }
            }
        };
        final GobSet newfags = new Transitory("new") {
            int cycle = 0;

            void update() {
                if (++cycle >= 20) {
                    Collection<Gob> cache = new ArrayList<Gob>();
                    Collection<Gob> scache = new ArrayList<Gob>();
                    for (Map.Entry<Gob, Integer> ob : age.entrySet()) {
                        if (ticks - ob.getValue() > 30) {
                            Gob gob = ob.getKey();
                            if (gob.staticp() instanceof Gob.SemiStatic)
                                scache.add(gob);
                            else
                                cache.add(gob);
                        }
                    }
                    for (Gob ob : cache)
                        put(semifags, ob);
                    for (Gob ob : scache)
                        put(semistat, ob);
                    cycle = 0;
                }
            }
        };
        final GobSet dynamic = new GobSet("dyn") {
            int cycle = 0;

            void update() {
                if (++cycle >= 5) {
                    Collection<Gob> cache = new ArrayList<Gob>();
                    for (Gob ob : obs) {
                        Object seq = ob.staticp();
                        if ((seq instanceof Gob.Static) || (seq instanceof Gob.SemiStatic))
                            cache.add(ob);
                    }
                    for (Gob ob : cache)
                        put(newfags, ob);
                    cycle = 0;
                }
            }

            public Object staticp() {
                return (null);
            }
        };
        final GobSet[] all = {oldfags, semifags, semistat, newfags, dynamic};

        void put(GobSet set, Gob ob) {
            GobSet p = parts.get(ob);
            if (p != set) {
                if (p != null)
                    p.remove(ob);
                parts.put(ob, set);
                set.take(ob);
            }
        }

        void remove(Gob ob) {
            GobSet p = parts.get(ob);
            if (p != null) {
                parts.remove(ob);
                p.remove(ob);
            }
        }

        Gobs() {
            synchronized (oc) {
                for (Gob ob : oc)
                    changed.changed(ob);
            }
        }

        void update() {
            for (Gob ob : changed.removed)
                remove(ob);
            changed.removed.clear();

            for (Gob ob : changed.changed) {
                if (ob.staticp() instanceof Gob.Static)
                    put(newfags, ob);
                else
                    put(dynamic, ob);
            }
            changed.changed.clear();

            for (GobSet set : all)
                set.update();
        }

        public void draw(GOut g) {
        }

        public boolean setup(RenderList rl) {
            synchronized (oc) {
                update();
                for (GobSet set : all)
                    rl.add(set, null);
                ticks++;
            }
            return (false);
        }

        public String toString() {
            return (String.format("%,dd %,dn %,dS %,ds %,do", dynamic.size(), newfags.size(), semistat.size(), semifags.size(), oldfags.size()));
        }
    }

    private final Rendered gobs;

    public String toString() {
        String cc;
        try {
            cc = getcc().toString();
        } catch (Loading l) {
            cc = "<nil>";
        }
        return (String.format("Camera[%s (%s)], Caches[%s]", cc, camera, gobs));
    }

    public GLState camera() {
        return (camera);
    }

    protected Projection makeproj() {
        return (null);
    }

    private Coord3f smapcc = null;
    private ShadowMap smap = null;
    private double lsmch = 0;

    private void updsmap(RenderList rl, DirLight light) {
        if (rl.cfg.pref.lshadow.val) {
            if (smap == null)
                smap = new ShadowMap(new Coord(2048, 2048), 750, 5000, 1);
            smap.light = light;
            Coord3f dir = new Coord3f(-light.dir[0], -light.dir[1], -light.dir[2]);
            Coord3f cc = getcc();
            cc.y = -cc.y;
            boolean ch = false;
            double now = Utils.rtime();
            if ((smapcc == null) || (smapcc.dist(cc) > 50)) {
                smapcc = cc;
                ch = true;
            } else {
                if (now - lsmch > 0.1)
                    ch = true;
            }
            if (ch) {
                smap.setpos(smapcc.add(dir.neg().mul(1000f)), dir);
                lsmch = now;
            }
            rl.prepc(smap);
        } else {
            if (smap != null)
                smap.dispose();
            smap = null;
            smapcc = null;
        }
    }

    public DirLight amb = null;
    private Outlines outlines = new Outlines(false);

    public void setup(RenderList rl) {
        Gob pl = player();
        if (pl != null)
            this.cc = new Coord2d(pl.getc());
        synchronized (glob) {
            if (glob.lightamb != null) {
                final boolean darkmode = DARKMODE.get();
                final boolean nightvision = NIGHTVISION.get();
                final Color lamb = darkmode ? Color.BLACK : nightvision ? NVAMBIENTCOL.get() : glob.lightamb;
                final Color ldif = darkmode ? Color.BLACK : nightvision ? NVDIFFUSECOL.get() : glob.lightdif;
                final Color lspc = darkmode ? Color.BLACK : nightvision ? NVSPECCOC.get() : glob.lightspc;

                DirLight light = new DirLight(lamb, ldif, lspc,
                        Coord3f.o.sadd((float) glob.lightelev, (float) glob.lightang, 1f));

                rl.add(light, null);
                updsmap(rl, light);
                amb = light;
            } else {
                amb = null;
            }
            if (Config.showweather) {
                try {
                    for (Glob.Weather w : glob.weather)
                        w.gsetup(rl);
                    for (Glob.Weather w : glob.weather) {
                        if (w instanceof Rendered)
                            rl.add((Rendered) w, null);
                    }
                } catch (Exception e) {
                    if (!Config.noloadscreen) {
                        throw e;
                    }
                }
            }
        }
        /* XXX: MSAA level should be configurable. */
        if (rl.cfg.pref.fsaa.val) {
            FBConfig cfg = ((PView.ConfContext) rl.state().get(PView.ctx)).cfg;
            cfg.ms = 4;
        }
        if (rl.cfg.pref.outline.val)
            rl.add(outlines, null);
        rl.add(map, null);
        if (showgrid) {
            rl.add(Config.slothgrid ? grid : gridol, null);
        }
        rl.add(mapol, null);
        rl.add(gobs, null);
        if (placing != null)
            addgob(rl, placing);
        synchronized (extradraw) {
            for (Rendered extra : extradraw)
                rl.add(extra, null);
            extradraw.clear();
        }

        // This solution is bad but currently no better avaible
        if (Config.skybox) {
            boolean skyb = true;
            if (player() != null) {
                Coord pltc = new Coord((int) player().getc().x / 11, (int) player().getc().y / 11);
                for (int x = -44; x < 44; x++) {
                    for (int y = -44; y < 44; y++) {
                        int t = glob.map.gettile(pltc.sub(x, y));
                        Resource res = glob.map.tilesetr(t);
                        if (res == null)
                            continue;

                        String name = res.name;
                        if (name.equals("gfx/tiles/mine") ||
                                name.equals("gfx/tiles/boards")) {
                            skyb = false;
                            break;
                        }

                    }
                }
            }
            if (skyb) {
                rl.add(skydrop, Rendered.last);
            }
        }
    }

    public static final haven.glsl.Uniform amblight = new haven.glsl.Uniform.AutoApply(haven.glsl.Type.INT) {
        public void apply(GOut g, VarID loc) {
            int idx = -1;
            RenderContext ctx = g.st.get(PView.ctx);
            if (ctx instanceof WidgetContext) {
                Widget wdg = ((WidgetContext) ctx).widget();
                if (wdg instanceof MapView)
                    idx = g.st.get(Light.lights).index(((MapView) wdg).amb);
            }
            g.gl.glUniform1i(loc, idx);
        }
    };

    public void drawadd(Rendered extra) {
        synchronized (extradraw) {
            extradraw.add(extra);
        }
    }

    public Gob player() {
        return ((plgob < 0) ? null : glob.oc.getgob(plgob));
    }

    public Coord3f getcc() {
        Gob pl = player();
        if (pl != null)
            return (pl.getc());
        else
            return (glob.map.getzp(cc));
    }

    public Coord3f getcc_old() {
        Gob pl = player();
        if (pl != null)
            return (pl.getc_old());
        else
            return (glob.map.getzp(cc));
    }//only exists because follow cam hates the new getz

    public static class ClickContext extends RenderContext {
    }

    private TexGL clickbuf = null;
    private GLFrameBuffer clickfb = null;
    private final RenderContext clickctx = new ClickContext();

    private GLState.Buffer clickbasic(GOut g) {
        GLState.Buffer ret = basic(g);
        clickctx.prep(ret);
        if ((clickbuf == null) || !clickbuf.sz().equals(sz)) {
            if (clickbuf != null) {
                clickfb.dispose();
                clickfb = null;
                clickbuf.dispose();
                clickbuf = null;
            }
            clickbuf = new TexE(sz, GL.GL_RGBA, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE);
            clickfb = new GLFrameBuffer(clickbuf, null);
        }
        clickfb.prep(ret);
        new States.Blending(GL.GL_ONE, GL.GL_ZERO).prep(ret);
        return (ret);
    }

    private abstract static class Clicklist<T> extends RenderList {
        private Map<States.ColState, T> rmap = new WeakHashMap<>();
        private Map<T, Reference<States.ColState>> idmap = new WeakHashMap<>();

        private int i = 1;
        private GLState.Buffer plain, bk;

        abstract protected T map(Rendered r);

        private Clicklist(GLConfig cfg) {
            super(cfg);
            this.bk = new GLState.Buffer(cfg);
        }

        protected States.ColState getcol(T t) {
            Reference<States.ColState> prevr = idmap.get(t);
            States.ColState prev = (prevr == null) ? null : prevr.get();
            if (prev != null)
                return (prev);
            int cr = ((i & 0x00000f) << 4) | ((i & 0x00f000) >> 12),
                    cg = ((i & 0x0000f0) << 0) | ((i & 0x0f0000) >> 16),
                    cb = ((i & 0x000f00) >> 4) | ((i & 0xf00000) >> 20);
            Color col = new Color(cr, cg, cb);
            States.ColState cst = new States.ColState(col);
            i++;
            rmap.put(cst, t);
            idmap.put(t, new WeakReference<States.ColState>(cst));
            return (cst);
        }

        protected void render(GOut g, Rendered r) {
            try {
                if (r instanceof FRendered)
                    ((FRendered) r).drawflat(g);
            } catch (RenderList.RLoad l) {
                if (ignload) return;
                else throw (l);
            }
        }

        public void get(GOut g, Coord c, final Callback<T> cb) {
            g.getpixel(c, col -> cb.done(rmap.get(new States.ColState(col))));
        }

        public void setup(Rendered r, GLState.Buffer t) {
            this.plain = t;
            super.setup(r, t);
        }

        protected void setup(Slot s, Rendered r) {
            T t = map(r);
            super.setup(s, r);
            s.os.copy(bk);
            plain.copy(s.os);
            bk.copy(s.os, GLState.Slot.Type.GEOM);
            if (t != null)
                getcol(t).prep(s.os);
        }

        public boolean aging() {
            return (i > (1 << 13));
        }
    }

    private static class Maplist extends Clicklist<MapMesh> {
        private int mode = 0;
        private MapMesh limit = null;

        private Maplist(GLConfig cfg) {
            super(cfg);
        }

        protected MapMesh map(Rendered r) {
            if (r instanceof MapMesh)
                return ((MapMesh) r);
            return (null);
        }

        protected void render(GOut g, Rendered r) {
            if (r instanceof MapMesh) {
                MapMesh m = (MapMesh) r;
                if (mode != 0)
                    g.state(States.vertexcolor);
                if ((limit == null) || (limit == m))
                    m.drawflat(g, mode);
            }
        }
    }

    private void checkmapclick(final GOut g, final Coord c, final Callback<Coord2d> cb) {
        new Object() {
            MapMesh cut;
            Coord tile;
            Coord2d pixel;
            int dfl = 0;

            {
                Maplist rl = new Maplist(g.gc);
                rl.setup(map, clickbasic(g));
                rl.fin();

                rl.render(g);
                if (clickdb)
                    g.getimage(img -> Debug.dumpimage(img, Debug.somedir("click1.png")));
                rl.get(g, c, hit -> {
                    cut = hit;
                    ckdone(1);
                });
                // rl.limit = hit;

                rl.mode = 1;
                rl.render(g);
                if (clickdb)
                    g.getimage(img -> Debug.dumpimage(img, Debug.somedir("click2.png")));
                g.getpixel(c, col -> {
                    tile = new Coord(col.getRed() - 1, col.getGreen() - 1);
                    pixel = new Coord2d((col.getBlue() * tilesz.x) / 255, (col.getAlpha() * tilesz.y) / 255);
                    ckdone(2);
                });
            }

            void ckdone(int fl) {
                synchronized (this) {
                    if ((dfl |= fl) == 3) {
                        if ((cut == null) || !tile.isect(Coord.z, cut.sz))
                            cb.done(null);
                        else
                            cb.done(cut.ul.add(tile).mul(tilesz).add(pixel));
                    }
                }
            }
        };
    }

    public static class ClickInfo {
        public final ClickInfo from;
        public final Rendered r;
        public Gob gob;

        public ClickInfo(ClickInfo from, Rendered r) {
            this.from = from;
            this.r = r;
        }

        public ClickInfo() {
            this(null, null);
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof ClickInfo))
                return (false);
            ClickInfo o = (ClickInfo) obj;
            return (Utils.eq(from, o.from) && (r == o.r));
        }

        public int hashCode() {
            return (((from != null) ? (from.hashCode() * 31) : 0) + System.identityHashCode(r));
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("#<clickinfo");
            for (ClickInfo c = this; c != null; c = c.from) {
                buf.append(' ');
                buf.append(c.r);
            }
            buf.append(">");
            return (buf.toString());
        }

        public Rendered[] array() {
            int n = 0;
            for (ClickInfo c = this; c != null; c = c.from)
                n++;
            Rendered[] buf = new Rendered[n];
            int i = 0;
            for (ClickInfo c = this; c != null; c = c.from)
                buf[i++] = c.r;
            return (buf);
        }
    }

    private static class Goblist extends Clicklist<ClickInfo> {
        private ClickInfo curinfo;

        public Goblist(GLConfig cfg) {
            super(cfg);
            curinfo = null;
        }

        public ClickInfo map(Rendered r) {
            if (r instanceof FRendered)
                return (curinfo);
            else
                return (null);
        }

        public void add(Rendered r, GLState t) {
            ClickInfo previnfo = curinfo;
            curinfo = new ClickInfo(previnfo, r);
            super.add(r, t);
            curinfo = previnfo;
        }
    }

    private Clicklist<ClickInfo> curgoblist = null;

    private void checkgobclick(GOut g, Coord c, Callback<ClickInfo> cb) {
        if ((curgoblist == null) || (curgoblist.cfg != g.gc) || curgoblist.aging())
            curgoblist = new Goblist(g.gc);
        Clicklist<ClickInfo> rl = curgoblist;
        rl.setup(gobs, clickbasic(g));
        rl.fin();
        rl.render(g);
        rl.get(g, c, cb);
    }

    public void delay(Delayed d) {
        synchronized (delayed) {
            delayed.add(d);
        }
    }

    public void delay2(Delayed d) {
        synchronized (delayed2) {
            delayed2.add(d);
        }
    }

    protected void undelay(Collection<Delayed> list, GOut g) {
        synchronized (list) {
            for (Delayed d : list)
                d.run(g);
            list.clear();
        }
    }

    static class PolText {
        Text text;
        double tm;

        PolText(Text text, double tm) {
            this.text = text;
            this.tm = tm;
        }
    }

    private static final Text.Furnace polownertf = new PUtils.BlurFurn(new Text.Foundry(Text.serif, 30).aa(true), 3, 1, Color.BLACK);
    private final Map<Integer, PolText> polowners = new HashMap<Integer, PolText>();


    public void setpoltext(int id, String text) {
        synchronized (polowners) {
            polowners.put(id, new PolText(polownertf.render(text), Utils.rtime()));
        }
    }

    private void poldraw(GOut g) {
        if (polowners.isEmpty())
            return;
        double now = Utils.rtime();
        synchronized (polowners) {
            int y = (sz.y - polowners.values().stream().map(t -> t.text.sz().y).reduce(0, (a, b) -> a + b + 10)) / 2;
            for (Iterator<PolText> i = polowners.values().iterator(); i.hasNext(); ) {
                PolText t = i.next();
                double poldt = now - t.tm;
                if (poldt < 6.0) {
                    int a;
                    if (poldt < 1.0)
                        a = (int) (255 * poldt);
                    else if (poldt < 4.0)
                        a = 255;
                    else
                        a = (int) ((255 * (2.0 - (poldt - 4.0))) / 2.0);
                    g.chcolor(255, 255, 255, a);
                    g.aimage(t.text.tex(), new Coord((sz.x - t.text.sz().x) / 2, y), 0.0, 0.0);
                    y += t.text.sz().y + 10;
                    g.chcolor();
                } else {
                    i.remove();
                }
            }
        }
    }

    private void drawarrow(GOut g, double a) {
        Coord hsz = sz.div(2);
        double ca = -Coord.z.angle(hsz);
        Coord ac;
        if ((a > ca) && (a < -ca)) {
            ac = new Coord(sz.x, hsz.y - (int) (Math.tan(a) * hsz.x));
        } else if ((a > -ca) && (a < Math.PI + ca)) {
            ac = new Coord(hsz.x - (int) (Math.tan(a - Math.PI / 2) * hsz.y), 0);
        } else if ((a > -Math.PI - ca) && (a < ca)) {
            ac = new Coord(hsz.x + (int) (Math.tan(a + Math.PI / 2) * hsz.y), sz.y);
        } else {
            ac = new Coord(0, hsz.y + (int) (Math.tan(a) * hsz.x));
        }
        Coord bc = ac.add(Coord.sc(a, -10));
        g.line(bc, bc.add(Coord.sc(a, -40)), 2);
        g.line(bc, bc.add(Coord.sc(a + Math.PI / 4, -10)), 2);
        g.line(bc, bc.add(Coord.sc(a - Math.PI / 4, -10)), 2);
    }

    public Coord3f screenxf(Coord3f mc) {
        if (Config.disableelev)
            mc.z = 0;
        Coord3f mloc = new Coord3f(mc.x, -mc.y, mc.z);
        /* XXX: Peeking into the camera really is doubtfully nice. */
        return (camera.proj.toscreen(camera.view.fin(Matrix4f.id).mul4(mloc), sz));
    }

    public Coord3f screenxf(Coord2d mc) {
        Coord3f cc;
        try {
            cc = getcc();
        } catch (Loading e) {
            return (null);
        }
        return (screenxf(new Coord3f((float) mc.x, (float) mc.y, cc.z)));
    }

    public double screenangle(Coord2d mc, boolean clip) {
        Coord3f cc;
        try {
            cc = getcc();
        } catch (Loading e) {
            return (Double.NaN);
        }
        Coord3f mloc = new Coord3f((float) mc.x, -(float) mc.y, cc.z);
        float[] sloc = camera.proj.toclip(camera.view.fin(Matrix4f.id).mul4(mloc));
        if (clip) {
            float w = sloc[3];
            if ((sloc[0] > -w) && (sloc[0] < w) && (sloc[1] > -w) && (sloc[1] < w))
                return (Double.NaN);
        }
        float a = ((float) sz.y) / ((float) sz.x);
        return (Math.atan2(sloc[1] * a, sloc[0]));
    }

    private void partydraw(GOut g) {
        synchronized (ui.sess.glob.party) {
            for (Party.Member m : ui.sess.glob.party.memb.values()) {
                if (m.gobid == this.plgob)
                    continue;
                Coord2d mc = m.getc();
                if (mc == null)
                    continue;
                double a = screenangle(mc, true);
                if (Double.isNaN(a))
                    continue;
                g.chcolor(m.col);
                drawarrow(g, a);
            }
        }
        g.chcolor();
    }

    private Loading camload = null, lastload = null;

    public void draw(GOut g) {

        if ((olftimer != 0) && (olftimer < Utils.rtime()))
            unflashol();
        try {
            if (camload != null)
                throw (new Loading(camload));
            undelay(delayed, g);
            super.draw(g);
            undelay(delayed2, g);
            poldraw(g);
            partydraw(g);
            if (showgrid) {
                double tx = Math.ceil(cc.x / tilesz.x / MCache.cutsz.x);
                double ty = Math.ceil(cc.y / tilesz.y / MCache.cutsz.y);
                Coord tc = new Coord((int) (tx - view - 1) * MCache.cutsz.x, (int) (ty - view - 1) * MCache.cutsz.y);
                if (!tc.equals(lasttc)) {
                    lasttc = tc;
                    gridol.update(tc);
                }
            }
            //  try {
            glob.map.reqarea(cc.floor(tilesz).sub(MCache.cutsz.mul(view + 1)),
                    cc.floor(tilesz).add(MCache.cutsz.mul(view + 1)));
        } catch (Loading e) {
            lastload = e;
            String text = e.getMessage();
            if (text == null)
                text = "Loading...";
            if (!Config.noloadscreen) {
                g.chcolor(Color.BLACK);
                g.frect(Coord.z, sz);
            } else {
                // System.out.println(text);
                // e.printStackTrace();
            }
            g.chcolor(Color.WHITE);
            g.atext(text, sz.div(2), 0.5, 0.5);
            if (e instanceof Resource.Loading) {
                ((Resource.Loading) e).boostprio(5);
            }
        }
    }


    private void updateSpeed(final double dt) {
        final Gob pl = ui.sess.glob.oc.getgob(plgob);
        if (pl != null) {
            final Coord2d plc = new Coord2d(pl.getc());
            if (lastrc != null) {
                totaldist += plc.dist(lastrc);
                totaldt += dt;
                if (totaldt >= 1) {
                    mspeedavg = totaldist / totaldt;
                    totaldt = 0;
                    totaldist = 0;
                }
                mspeed = plc.dist(lastrc) / dt;
            } else {
                mspeedavg = 0;
                totaldist = 0;
                totaldt = 0;
                mspeed = 0;
            }
            lastrc = plc;
        }
    }

    public double speed() {
        return mspeedavg;
    }

    /**
     * 1) If you made it to your destination within a reasonable limit
     * a) Exactly on target destination
     * b) Not moving anymore and within 5 units of it
     * c) Predictive model said it was okay
     */
    private boolean triggermove() {
        final Gob pl = ui.sess.glob.oc.getgob(plgob);
        if (pl != null) {
            if (movingto != null && pl.getattr(Moving.class) != null) {
                final Coord2d plc = new Coord2d(pl.getc());
                final double left = plc.dist(movingto) / mspeed;
                //Only predictive models can trigger here
                return movingto.dist(pl.rc) <= 5 || left == 0;
            } else if (movingto == null || movingto.dist(pl.rc) <= 5) {
                return true;
            } else {
                //Way off target and not moving, cancel
                //clearmovequeue();
                return false;
            }
        } else {
            return false;
        }
    }

    int finishTimes = 0;
    int maxfinish = 100;
    boolean isclickongob = false;

    public boolean isfinishmovequeue() {
        final Gob pl = PBotUtils.player(ui);
        if (pl != null) {
            if (!pl.isMoving()) {
                finishTimes++;
                if (finishTimes > maxfinish) {
                    return true;
                } else if (movequeue.size() > 0) {
                    return false;
                } else if (movingto != null) {
                    if (pathfindGob != null) {
                        GobHitbox.BBox box = GobHitbox.getBBox(pathfindGob);
                        GobHitbox.BBox pbox = GobHitbox.getBBox(pl);
                        if (box != null && pbox != null) {
                            return pathfindGob.rc.dist(pl.rc) <= Math.sqrt(Math.pow(Math.max(box.a.x, box.a.y), 2) * 2) + Math.sqrt(Math.pow(Math.max(pbox.a.x, pbox.a.y), 2) * 2);
                        } else
                            return movingto.dist(pl.rc) <= 5;
                    } else
                        return movingto.dist(pl.rc) <= 5;
                }
            } else {
                finishTimes = 0;
                return false;
            }
        }
        finishTimes = 0;
        return false;
    }

    public boolean isclearmovequeue() {
        return pathfindGob == null && pathfindGobMod == 0 && movequeue.size() == 0 && movingto == null && ui.gui.pointer.tc == null;
    }

    public void clearmovequeue() {
        finishTimes = 0;
        if (pathfindGob != null) {
            pathfindGob = null; //set pathfind gob back to null incase pathfinding was interrupted in the middle of a pathfind right click.
            pathfindGobMod = 0;
            isclickongob = false;
        }
        movequeue.clear();
        movingto = null;
        ui.gui.pointer.update(null);
    }

    public void queuemove(final Coord2d c) {
        movequeue.add(c);
    }

    public void questQueueAdd(final Coord2d c) {
        questQueue.add(c);
    }

    public boolean los(final Coord2d c) {
        final NBAPathfinder finder = new NBAPathfinder(ui);
        return finder.walk(new Coord(ui.sess.glob.oc.getgob(plgob).getc()), c.floor());
    }

    public void los(final Gob g) {

    }

    public Move[] findpath(final Coord2d c) {
        final NBAPathfinder finder = new NBAPathfinder(ui);
        final List<Move> moves = finder.path(new Coord(ui.sess.glob.oc.getgob(plgob).getc()), c.floor());
        return moves != null ? moves.toArray(new Move[0]) : null;
    }

    public Move[] findpath(final Gob g) {
        g.updatePathfindingBlackout(true);
        final Move[] moves = findpath(new Coord2d(g.getc()));
        g.updatePathfindingBlackout(false);
        return moves;
    }

    public boolean pathto(final Coord2d c) {
        final Move[] moves = findpath(c);
        if (moves != null) {
            clearmovequeue();
            for (final Move m : moves) {
                queuemove(m.dest());
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean pathto(final Gob g) {
        g.updatePathfindingBlackout(true);
        boolean yea = pathto(new Coord2d(g.getc()));
        g.updatePathfindingBlackout(false);
        return yea;
    }

    public boolean pathtoRightClick(final Gob g, int mod) {
        //	PBotAPI.gui.map.purusPfRightClick(gob.gob, -1, 3, mod, null);
        g.updatePathfindingBlackout(true);
        boolean yea = pathto(new Coord2d(g.getc()));
        pathfindGob = g;
        pathfindGobMod = mod;
        g.updatePathfindingBlackout(false);
        return yea;
    }

    public void moveto(final Coord2d c) {
        clearmovequeue();
        wdgmsg("click", new Coord(1, 1), c.floor(posres), 1, 0);
    }

    public void relMove(final Coord2d c) {
        final Gob g = ui.sess.glob.oc.getgob(plgob);
        if (g != null) {
            final Coord gc = new Coord2d(g.getc()).add(c).floor(posres);
            wdgmsg("click", new Coord(1, 1), gc, 1, 0);
        }
    }

    public Coord2d movingto() {
        return movingto;
    }

    public Iterator<Coord2d> movequeue() {
        configuration.sysPrintStackTrace("movequeue");
        return movequeue.iterator();
    }

    public List<Coord2d> questQueue() {
        return questQueue;
    }

    public void tick(double dt) {
        glob.map.sendreqs();
        camload = null;
        try {
            if ((shake = shake * Math.pow(100, -dt)) < 0.01)
                shake = 0;
            camoff.x = (float) ((Math.random() - 0.5) * shake);
            camoff.y = (float) ((Math.random() - 0.5) * shake);
            camoff.z = (float) ((Math.random() - 0.5) * shake);
            camera.tick(dt);
        } catch (Loading e) {
            camload = e;
        }
        updateSpeed(dt);
        if (placing != null)
            placing.ctick((int) (dt * 1000));
        if (movequeue.size() > 0 && (System.currentTimeMillis() - lastMove > 500) && triggermove()) {
            movingto = movequeue.poll();
            ui.gui.pointer.update(movingto);
            wdgmsg("click", new Coord(1, 1), movingto.floor(posres), 1, 0);
            lastMove = System.currentTimeMillis();
        }
        if (movequeue.size() == 0 && pathfindGob != null && !isclickongob) {
            wdgmsg("click", Coord.z, pathfindGob.rc.floor(posres), 3, pathfindGobMod, 0, (int) pathfindGob.id, pathfindGob.rc.floor(posres), 0, -1);
            isclickongob = true;
        }
        if (isfinishmovequeue() && !isclearmovequeue()) {
            clearmovequeue();
        }
        partyHighlight.update();
    }

    public void resize(Coord sz) {
        super.resize(sz);
        camera.resized();
    }

    public static interface PlobAdjust {
        public void adjust(Plob plob, Coord pc, Coord2d mc, int modflags);

        public boolean rotate(Plob plob, int amount, int modflags);
    }

    public static class StdPlace implements PlobAdjust {
        boolean freerot = false;
        Coord2d gran = (plobgran == 0) ? null : new Coord2d(1.0 / plobgran, 1.0 / plobgran).mul(tilesz);

        public void adjust(Plob plob, Coord pc, Coord2d mc, int modflags) {
            if ((modflags & 2) == 0)
                plob.rc = mc.floor(tilesz).mul(tilesz).add(tilesz.div(2));
            else if (gran != null)
                plob.rc = mc.add(gran.div(2)).floor(gran).mul(gran);
            else
                plob.rc = mc;
            Gob pl = plob.mv().player();
            if ((pl != null) && !freerot)
                plob.a = Math.round(plob.rc.angle(pl.rc) / (Math.PI / 2)) * (Math.PI / 2);
        }

        public boolean rotate(Plob plob, int amount, int modflags) {
            if ((modflags & 1) == 0)
                return (false);
            freerot = true;
            if ((modflags & 2) == 0)
                plob.a = (Math.PI / 4) * Math.round((plob.a + (amount * Math.PI / 4)) / (Math.PI / 4));
            else
                plob.a += amount * Math.PI / 16;
            plob.a = Utils.cangle(plob.a);
            return (true);
        }
    }

    public class Plob extends Gob {
        public PlobAdjust adjust = new StdPlace();
        Coord lastmc = null;

        private Plob(Indir<Resource> res, Message sdt) {
            super(MapView.this.glob, Coord2d.z);
            setattr(new ResDrawable(this, res, sdt));
            if (ui.mc.isect(rootpos(), sz)) {
                delay(new Adjust(ui.mc.sub(rootpos()), 0));
            }
        }

        public MapView mv() {
            return (MapView.this);
        }

        private class Adjust extends Maptest {
            int modflags;

            Adjust(Coord c, int modflags) {
                super(c);
                this.modflags = modflags;
            }

            public void hit(Coord pc, Coord2d mc) {
                adjust.adjust(Plob.this, pc, mc, modflags);
                lastmc = pc;
            }
        }
    }

    private int olflash;
    private double olftimer;

    private void unflashol() {
        for (int i = 0; i < visol.length; i++) {
            if ((olflash & (1 << i)) != 0)
                visol[i]--;
        }
        olflash = 0;
        olftimer = 0;
    }

    public Plob placing() {
        return placing;
    }

    public void uimsg(String msg, Object... args) {
        if (msg == "place") {
            int a = 0;
            Indir<Resource> res = ui.sess.getres((Integer) args[a++]);
            Message sdt;
            if ((args.length > a) && (args[a] instanceof byte[]))
                sdt = new MessageBuf((byte[]) args[a++]);
            else
                sdt = Message.nil;
            placing = new Plob(res, sdt);
            while (a < args.length) {
                Indir<Resource> ores = ui.sess.getres((Integer) args[a++]);
                Message odt;
                if ((args.length > a) && (args[a] instanceof byte[]))
                    odt = new MessageBuf((byte[]) args[a++]);
                else
                    odt = Message.nil;
                placing.ols.add(new Gob.Overlay(-1, ores, odt));
            }
        } else if (msg == "unplace") {
            placing = null;
        } else if (msg == "move") {
            cc = ((Coord) args[0]).mul(posres);
        } else if (msg == "plob") {
            if (args[0] == null)
                plgob = -1;
            else
                plgob = (Integer) args[0];
            plgobid = plgob;
        } else if (msg == "flashol") {
            unflashol();
            olflash = (Integer) args[0];
            for (int i = 0; i < visol.length; i++) {
                if ((olflash & (1 << i)) != 0)
                    visol[i]++;
            }
            olftimer = Utils.rtime() + (((Number) args[1]).doubleValue() / 1000.0);
        } else if (msg == "sel") {
            boolean sel = ((Integer) args[0]) != 0;
            synchronized (this) {
                if (sel && (selection == null)) {
                    selection = new Selector();
                } else if (!sel && (selection != null)) {
                    selection.destroy();
                    selection = null;
                }
            }
        } else if (msg == "shake") {
            shake += ((Number) args[0]).doubleValue();
        } else {
            super.uimsg(msg, args);
        }
    }

    private UI.Grab camdrag = null;

    public abstract class Maptest implements Delayed {
        private final Coord pc;

        public Maptest(Coord c) {
            this.pc = c;
        }

        public void run(GOut g) {
            GLState.Buffer bk = g.st.copy();
            try {
                BGL gl = g.gl;
                g.st.set(clickbasic(g));
                g.apply();
                gl.glClear(GL.GL_DEPTH_BUFFER_BIT | GL.GL_COLOR_BUFFER_BIT);
                checkmapclick(g, pc, mc -> {
                    synchronized (ui) {
                        if (mc != null)
                            hit(pc, mc);
                        else
                            nohit(pc);
                    }
                });
            } finally {
                g.st.set(bk);
            }
        }

        protected abstract void hit(Coord pc, Coord2d mc);

        protected void nohit(Coord pc) {
        }
    }

    public abstract class Hittest implements Delayed {
        private final Coord clickc;
        private Coord2d mapcl;
        private ClickInfo gobcl;
        private int dfl = 0;
        private final int flags;

        public Hittest(Coord c) {
            clickc = c;
            this.flags = 0;
        }

        public Hittest(Coord c, int flags) {
            clickc = c;
            this.flags = flags;
        }

        public void run(GOut g) {
            GLState.Buffer bk = g.st.copy();
            try {
                BGL gl = g.gl;
                g.st.set(clickbasic(g));
                g.apply();
                gl.glClear(GL.GL_DEPTH_BUFFER_BIT | GL.GL_COLOR_BUFFER_BIT);
                checkmapclick(g, clickc, mc -> {
                    mapcl = mc;
                    ckdone(1);
                });
                g.st.set(bk);
                g.st.set(clickbasic(g));
                g.apply();
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                checkgobclick(g, clickc, cl -> {
                    gobcl = cl;
                    ckdone(2);
                });
            } finally {
                g.st.set(bk);
            }
        }

        private void ckdone(int fl) {
            synchronized (this) {
                synchronized (ui) {
                    if ((dfl |= fl) == 3) {
                        if (mapcl != null) {
                            if (gobcl == null)
                                hit(clickc, mapcl, null);
                            else
                                hit(clickc, mapcl, gobcl);
                        } else {
                            nohit(clickc);
                        }
                    }
                }
            }
        }

        protected abstract void hit(Coord pc, Coord2d mc, ClickInfo inf);

        protected void nohit(Coord pc) {
        }
    }

    public static interface Clickable {
        public Object[] clickargs(ClickInfo inf);
    }

    //[ 0, id, rc.floor(posres), 0 ,-1 ]
    //  ^-- Contains overlay     ^   ^
    //                           |   |- FastMesh Res ID
    //                           |
    //                           +-- Overlay id

    public static Object[] gobclickargs(ClickInfo inf) {
        if (inf == null)
            return (new Object[0]);
        for (ClickInfo c = inf; c != null; c = c.from) {
            if (c.r instanceof Clickable)
                return (((Clickable) c.r).clickargs(inf));
        }
        Rendered[] st = inf.array();
        for (int g = 0; g < st.length; g++) {
            if (st[g] instanceof Gob) {
                Gob gob = (Gob) st[g];
                Object[] ret = {0, (int) gob.id, gob.rc.floor(posres), 0, -1};
                for (int i = g - 1; i >= 0; i--) {
                    if (st[i] instanceof Gob.Overlay) {
                        ret[0] = 1;
                        ret[3] = ((Gob.Overlay) st[i]).id;
                    }
                    if (st[i] instanceof FastMesh.ResourceMesh)
                        ret[4] = ((FastMesh.ResourceMesh) st[i]).id;
                }
                inf.gob = gob;
                return (ret);
            }
        }
        return (new Object[0]);
    }

    private class Click extends Hittest {
        int clickb;

        private Click(Coord c, int flags, int b) {
            super(c, flags);
            clickb = b;

            synchronized (Pathfinder.class) {
                if (pf != null) {
                    pf.terminate = true;
                    pfthread.interrupt();
                }
            }
        }

        protected void hit(Coord pc, Coord2d mc, ClickInfo inf) {
            if (clickb == 3 && ui.modmeta && ui.gui.vhand == null) {
                final Optional<Gob> clickgob = gobFromClick(inf);
                if (clickgob.isPresent()) {
                    showSpecialMenu(clickgob.get());
                } else if (configuration.proximityspecial) {
                    Gob target = null;
                    synchronized (glob.oc) {
                        for (Gob gob : glob.oc) {
                            if (!gob.isplayer()) {
                                double dist = gob.rc.dist(mc);
                                if ((target == null || dist < target.rc.dist(mc)) && dist <= 5 * tilesz.x)
                                    target = gob;
                            }
                        }
                        if (target != null)
                            showSpecialMenu(target);
                    }
                }
            } else {
                lastItemactClickArgs = null;
                if (clickb == 1)
                    pllastcc = mc;
                // reset alt so we could walk with alt+lmb while having item on the cursor
                int modflags = ui.modflags();
                if (ui.gui.vhand != null && clickb == 1)
                    modflags = modflags & ~4;

                Resource curs = ui.root.getcurs(c);

                if (musselPicker != null) {
                    try {
                        musselPicker.interrupt();
                    } catch (NullPointerException e) {
                    }
                }

                if (Config.proximityaggro && Config.proximityaggropvp && clickb == 1 && curs != null && curs.name.equals("gfx/hud/curs/atk")) {
                    Gob target = null;
                    synchronized (glob.oc) {
                        for (Gob gob : glob.oc) {
                            if (!gob.isplayer()) {
                                if (gob.type == Type.HUMAN || gob.type == Type.ANIMAL || gob.type == Type.DANGANIMAL) {
                                    double dist = gob.rc.dist(mc);
                                    if ((target == null || dist < target.rc.dist(mc)) && dist <= 5 * tilesz.x)
                                        target = gob;
                                }
                            }
                        }
                        if (target != null) {
                            wdgmsg("click", target.sc, target.rc.floor(posres), 1, 0, 0, (int) target.id, target.rc.floor(posres), 0, -1);
                            return;
                        }
                    }

                } else if (Config.proximityaggropvp && !Config.proximityaggro && clickb == 1 && curs != null && curs.name.equals("gfx/hud/curs/atk")) {
                    Gob target = null;
                    synchronized (glob.oc) {
                        for (Gob gob : glob.oc) {
                            if (gob.type == Type.HUMAN && !gob.isplayer()) {
                                double dist = gob.rc.dist(mc);
                                if ((target == null || dist < target.rc.dist(mc)) && dist <= 5 * tilesz.x)
                                    target = gob;
                            }
                        }
                        if (target != null) {
                            wdgmsg("click", target.sc, target.rc.floor(posres), 1, 0, 0, (int) target.id, target.rc.floor(posres), 0, -1);
                            return;
                        }
                    }

                } else if (Config.proximityaggro && !Config.proximityaggropvp && clickb == 1 && curs != null && curs.name.equals("gfx/hud/curs/atk")) {
                    Gob target = null;
                    synchronized (glob.oc) {
                        for (Gob gob : glob.oc) {
                            if (!gob.isplayer()) {
                                if (gob.type == Type.ANIMAL || gob.type == Type.DANGANIMAL) {
                                    double dist = gob.rc.dist(mc);
                                    if ((target == null || dist < target.rc.dist(mc)) && dist <= 5 * tilesz.x)
                                        target = gob;
                                }
                            }
                        }
                        if (target != null) {
                            wdgmsg("click", target.sc, target.rc.floor(posres), 1, 0, 0, (int) target.id, target.rc.floor(posres), 0, -1);
                            return;
                        }
                    }
                }
                final Object[] gobargs = gobclickargs(inf);
                Object[] args = {pc, mc.floor(posres), clickb, modflags};
                args = Utils.extend(args, gobclickargs(inf));

                if (inf == null) {
                    if (Config.pf && clickb == 1 && curs != null && !curs.name.equals("gfx/hud/curs/study")) {
                        pathto(mc);
                        //      purusPfLeftClick(mc.floor(), null);
                    } else if (clickb == 1 && ui.modmeta && ui.gui.vhand == null) {
                        //Queued movement
                        movequeue.add(mc);
                    } else {
                        args = Utils.extend(args, gobargs);
                        if (clickb == 1 || gobargs.length > 0) {
                            clearmovequeue();
                        }
                        wdgmsg("click", args);
                    }
                } else {
                    Gob gob = inf.gob;
                    if (gob != null && !ui.modctrl && !ui.modshift && !ui.modmeta && clickb == 1 && curs.name.equals("gfx/hud/curs/study")) {
                        //we're inspecting an object, prepared to intercept the system message.
                        ui.gui.inspectedgobid = gob.id;
                    }
                    if (ui.modctrl && clickb == 3 && gob != null && gob.type == Type.TAMEDANIMAL) {//only highlight on ctrl right clicks if we're clicking on an animal during milking
                        if (!gob.getres().name.contains("horse")) {//cant milk horses, so dont mark, send a right click.
                            if (markedGobs.contains(gob.id))
                                markedGobs.remove(gob.id);
                            else
                                markedGobs.add(gob.id);
                            glob.oc.changed(gob);
                        } else {
                            wdgmsg("click", args);
                        }
                    } else if (gob != null && gob.type == Type.TAMEDANIMAL && ui.modctrl && clickb == 1 && Config.shooanimals) {
                        Resource res = gob.getres();
                        if (res != null && (res.name.startsWith("gfx/kritter/horse") ||
                                res.name.startsWith("gfx/kritter/sheep") ||
                                res.name.startsWith("gfx/kritter/cattle") ||
                                res.name.startsWith("gfx/kritter/pig") ||
                                res.name.startsWith("gfx/kritter/goat"))) {
                            shooanimal = gob;
                            new Thread(new ShooTargeted(ui.gui), "ShooTargeted").start();
                        }
                    } else if (ui.modmeta && ui.modctrl && clickb == 1 && gob != null) {
                        if (markedGobs.contains(gob.id))
                            markedGobs.remove(gob.id);
                        else
                            markedGobs.add(gob.id);
                        glob.oc.changed(gob);
                    } else if (ui.modmeta && clickb == 1) {
                        if (gobselcb != null)
                            gobselcb.gobselect(gob);

                        if (gob == null && ui.gui.vhand == null) {
                            //   System.out.println("adding to move que");
                            //Queued movement
                            movequeue.add(mc);
                        }

                        if (ui.gui.vhand == null) {   // do not highlight when walking with an item
                            for (Widget w = ui.gui.chat.lchild; w != null; w = w.prev) {
                                if (w instanceof ChatUI.MultiChat) {
                                    ChatUI.MultiChat chat = (ChatUI.MultiChat) w;
                                    if (chat.name().equals(Resource.getLocString(Resource.BUNDLE_LABEL, "Area Chat"))) {
                                        chat.send(ChatUI.CMD_PREFIX_HLIGHT + gob.id);
                                        break;
                                    }
                                }
                            }
                        }
                    } else if (Config.pf && curs != null && !curs.name.equals("gfx/hud/curs/study") && gob != null) {
                        //  purusPfRightClick(gob, (int) args[8], clickb, 0, null);
                        pathtoRightClick(gob, 0);
                    } else {
                        args = Utils.extend(args, gobargs);
                        if (clickb == 1 || gobargs.length > 0) {
                            clearmovequeue();
                        }
                        wdgmsg("click", args);
                        if (gob != null && gob.getres() != null) {
                            CheckListboxItem itm = Config.autoclusters.get(gob.getres().name);
                            if (itm != null && itm.selected)
                                startMusselsPicker(gob);
                        }
                     /*   if (Config.autopickmussels && gob.getres() != null && (gob.getres().basename().equals("mussels") || gob.getres().basename().equals("oyster")))
                            startMusselsPicker(gob);
                        if (Config.autopickclay && gob.getres() != null &&  gob.getres().basename().equals("clay-gray"))
                            startMusselsPicker(gob);
                        if (Config.autopickbarnacles && gob.getres() != null &&  gob.getres().basename().equals("goosebarnacle"))
                            startMusselsPicker(gob);
                        if (Config.autopickcattails && gob.getres() != null &&  gob.getres().basename().equals("cattail"))
                            startMusselsPicker(gob);*/
                    }
                }
            }
        }
    }

    public void registerGobSelect(GobSelectCallback callback) {
        this.gobselcb = callback;
    }

    public void unregisterGobSelect() {
        this.gobselcb = null;
    }

    public void registerAreaSelect(AreaSelectCallback callback) {
        this.areaselcb = callback;
    }

    public void unregisterAreaSelect() {
        this.areaselcb = null;
    }

    public void purusPfLeftClick(Coord mc, String action) {
        synchronized (haven.purus.pathfinder.Pathfinder.class) {
            if (pastaPathfinder != null && pastaPathfinder.isAlive()) {
                pastaPathfinder.interrupt();
            }
            pastaPathfinder = new haven.purus.pathfinder.Pathfinder(ui.gui, new Coord2d(mc), action);
            pastaPathfinder.start();
        }
    }

    public void purusPfLeftClick(Coord2d mc, String action) {
        synchronized (haven.purus.pathfinder.Pathfinder.class) {
            if (pastaPathfinder != null && pastaPathfinder.isAlive()) {
                pastaPathfinder.interrupt();
            }
            pastaPathfinder = new haven.purus.pathfinder.Pathfinder(ui.gui, mc, action);
            pastaPathfinder.start();
        }
    }

    public void pfLeftClick(Coord mc, String action) {
        Gob player = player();
        if (player == null)
            return;
        synchronized (Pathfinder.class) {
            if (pf != null) {
                pf.terminate = true;
                pfthread.interrupt();
                // cancel movement
                if (player.getattr(Moving.class) != null)
                    wdgmsg("gk", 27);
            }

            Coord src = player.rc.floor();
            int gcx = haven.pathfinder.Map.origin - (src.x - mc.x);
            int gcy = haven.pathfinder.Map.origin - (src.y - mc.y);
            if (gcx < 0 || gcx >= haven.pathfinder.Map.sz || gcy < 0 || gcy >= haven.pathfinder.Map.sz)
                return;

            pf = new Pathfinder(this, new Coord(gcx, gcy), action);
            pf.addListener(this);
            pfthread = new Thread(pf, "Pathfinder");
            pfthread.start();
        }
    }

    public void purusPfRightClick(Gob gob, int meshid, int clickb, int modflags, String action) {
        synchronized (haven.purus.pathfinder.Pathfinder.class) {
            if (pastaPathfinder != null && pastaPathfinder.isAlive()) {
                pastaPathfinder.interrupt();
            }
            pastaPathfinder = new haven.purus.pathfinder.Pathfinder(ui.gui, gob, clickb, modflags, meshid, action);
            pastaPathfinder.start();
        }
    }

    public void pfRightClick(Gob gob, int meshid, int clickb, int modflags, String action) {
        //   System.out.println("pf right click");
        Gob player = player();
        if (player == null)
            return;
        synchronized (Pathfinder.class) {
            if (pf != null) {
                pf.terminate = true;
                pfthread.interrupt();
                // cancel movement
                if (player.getattr(Moving.class) != null)
                    wdgmsg("gk", 27);
            }

            Coord src = player.rc.floor();
            int gcx = haven.pathfinder.Map.origin - (src.x - gob.rc.floor().x);
            int gcy = haven.pathfinder.Map.origin - (src.y - gob.rc.floor().y);
            if (gcx < 0 || gcx >= haven.pathfinder.Map.sz || gcy < 0 || gcy >= haven.pathfinder.Map.sz)
                return;

            pf = new Pathfinder(this, new Coord(gcx, gcy), gob, meshid, clickb, modflags, action);
            pf.addListener(this);
            pfthread = new Thread(pf, "Pathfinder");
            pfthread.start();
        }
    }

    public void pfDone(final Pathfinder thread) {
        if (haven.pathfinder.Map.DEBUG_TIMINGS)
            System.out.println("-= PF DONE =-");
    }

    public void grab(Grabber grab) {
        this.grab = grab;
    }

    public void release(Grabber grab) {
        if (this.grab == grab)
            this.grab = null;
    }

    public boolean mousedown(Coord c, int button) {
        if (camdrag != null) { //this fixes a loftar bug where alt tabbing out while dragging the camera locks it in permanent drag state.
            camera.release();
            camdrag.remove();
            camdrag = null;
        }
        if (button == 1 && farmSelect) {
            synchronized (this) {
                if (selection == null) {
                    selection = new Selector();
                } else if (selection != null) {
                    selection.destroy();
                    selection = null;
                    farmSelect = false;
                }
            }
        }
        if (button == 1 && PBotAPISelect) {
            synchronized (this) {
                if (selection == null) {
                    selection = new Selector();
                } else if (selection != null) {
                    selection.destroy();
                    selection = null;
                    PBotAPISelect = false;
                }
            }
        }
        if (miningOverlay != null && button == 1) {
            miningOverlay.destroy();
            disol(18);
            miningOverlay = null;
        }
        parent.setfocus(this);
        if (button == 2) {
            if (camera.click(c))
                camdrag = ui.grabmouse(this);
        } else if (placing != null) {
            if (placing.lastmc != null)
                wdgmsg("place", placing.rc.floor(posres), (int) Math.round(placing.a * 32768 / Math.PI), button, ui.modflags());
        } else if ((grab != null) && grab.mmousedown(c, button)) {
        } else {
            if (configuration.autoclick)
                if (button == 1) {
                    ismousedown = true;
                }
            delay(new Click(c, ui.modflags(), button));
        }
        return (true);
    }

    public void mousemove(Coord c) {
        if (configuration.autoclick)
            if (ismousedown && ui.modflags() == 0)
                delay(new Click(c, ui.modflags(), 1));
        if (grab != null)
            grab.mmousemove(c);
        if (camdrag != null) {
            try {
                ((Camera) camera).drag(c);
            } catch (Exception e) {
            }//ignore exceptions here, possible to cause a crash if you change camera WHILE dragging the camera. Why you'd do this, I have no idea, but pls dont crash from it.
        } else if (placing != null) {
            if ((placing.lastmc == null) || !placing.lastmc.equals(c)) {
                delay(placing.new Adjust(c, ui.modflags()));
            }
        } else if (ui.modshift && !ui.modctrl && Config.detailedresinfo) {
            delay(new Hover(c));
        } else if (ui.modshift && !ui.modctrl && Config.resinfo) {
            long now = System.currentTimeMillis();
            if ((now - lastmmhittest > 500 || lasthittestc.dist(c) > tilesz.x) && ui.gui.hand.isEmpty()) {
                lastmmhittest = now;
                lasthittestc = c;

                delay(new Hittest(c, 0) {
                    protected void hit(Coord pc, Coord2d mc, ClickInfo inf) {
                        if (inf != null) {
                            MapView.gobclickargs(inf);
                            if (inf.gob != null) {
                                Resource res = inf.gob.getres();
                                if (res != null) {
                                    tooltip = res.name;
                                    return;
                                }
                            }
                        }
                        tooltip = null;
                    }

                    protected void nohit(Coord pc) {
                        tooltip = null;
                    }
                });
            }
        } else if (ui.modshift && ui.modctrl && Config.resinfo) {
            long now = System.currentTimeMillis();
            if (now - lastmmhittest > 500 || lasthittestc.dist(c) > tilesz.x) {
                lastmmhittest = now;
                lasthittestc = c;
                delay(new Hittest(c, 0) {
                    public void hit(Coord pc, Coord2d mc, ClickInfo inf) {
                        if (inf == null) {
                            MCache map = ui.sess.glob.map;
                            int t = map.gettile(mc.floor(tilesz));
                            Resource res = map.tilesetr(t);
                            if (res != null) {
                                tooltip = res.name;
                                return;
                            }
                        }
                        tooltip = null;
                    }

                    public void nohit(Coord pc) {
                        tooltip = null;
                    }
                });
            }
        } else {
            lasttt = "";
            tt = null;
        }

    }

    private class Hover extends Hittest {
        private Hover(Coord c) {
            super(c, 0);
        }

        private Optional<Gob> gobFromClick(final ClickInfo inf) {
            if (inf == null)
                return Optional.empty();
            Rendered[] st = inf.array();
            for (final Rendered g : st) {
                if (g instanceof Gob) {
                    return Optional.of((Gob) g);
                }
            }
            return Optional.empty();
        }

        private void updatett(final String ntt) {
            if (!ntt.equals(lasttt)) {
                lasttt = null;
                try {
                    tt = RichText.render(ntt, 300);
                } catch (Exception e) {
                    e.printStackTrace();
                    tt = null;
                }
            }
        }

        protected void hit(Coord pc, Coord2d mc, ClickInfo inf) {
            if (inf != null) {
                final Optional<Gob> gob = gobFromClick(inf);
                if (gob.isPresent()) {
                    updatett(gob.get().details());
                } else {
                    try {
                        final int tile_id = ui.sess.glob.map.gettile_safe(mc.div(MCache.tilesz).floor());
                        final MCache.Grid grid = ui.sess.glob.map.getgrid(mc.floor(tilesz).div(MCache.cmaps));
                        final Resource res = ui.sess.glob.map.tilesetr(tile_id);
                        final String name = ui.sess.glob.map.tiler(tile_id).getClass().getSimpleName();
                        updatett("Tile: " + res.name + "[" + tile_id + "] of type " + name + "\n" + mc + " [" + grid.id + "]");
                    } catch (Exception e) {
                        lasttt = "";
                        tt = null;
                    }
                }
            } else {
                try {
                    final int tile_id = ui.sess.glob.map.gettile_safe(mc.div(MCache.tilesz).floor());
                    final MCache.Grid grid = ui.sess.glob.map.getgrid(mc.floor(tilesz).div(MCache.cmaps));
                    final Resource res = ui.sess.glob.map.tilesetr(tile_id);
                    final String name = ui.sess.glob.map.tiler(tile_id).getClass().getSimpleName();
                    updatett("Tile: " + res.name + "[" + tile_id + "] of type " + name + "\n" + mc + " [" + grid.id + "]");
                } catch (Exception e) {
                    lasttt = "";
                    tt = null;
                }
            }
        }
    }

    public boolean mouseup(Coord c, int button) {
        if (configuration.autoclick)
            if (button == 1) {
                ismousedown = false;
            }
        if (button == 2) {
            if (camdrag != null) {
                camera.release();
                camdrag.remove();
                camdrag = null;
            }
        } else if (grab != null) {
            grab.mmouseup(c, button);
        }
        return (true);
    }

    public boolean mousewheel(Coord c, int amount) {
        if ((grab != null) && grab.mmousewheel(c, amount))
            return (true);
        if ((placing != null) && placing.adjust.rotate(placing, amount, ui.modflags()))
            return (true);
        return (((Camera) camera).wheel(c, amount));
    }

    public boolean drop(final Coord cc, final Coord ul) {
        delay(new Hittest(cc, ui.modflags()) {
            public void hit(Coord pc, Coord2d mc, ClickInfo inf) {
                if ((Config.nodropping || Config.nodropping_all) && !ui.modctrl) {
                    // no dropping at all or when we are on water
                    boolean nodropping = false;
                    if (Config.nodropping_all) {
                        // no dropping over anywhere
                        nodropping = true;
                    } else {
                        // we came here because Config.nodropping is set, check water tiles
                        int t = glob.map.gettile(player().rc.floor(tilesz));
                        Resource res = glob.map.tilesetr(t);
                        if (res != null &&
                                res.name.equals("gfx/tiles/water") ||
                                res.name.equals("gfx/tiles/deep") ||
                                res.name.equals("gfx/tiles/odeeper") ||
                                res.name.equals("gfx/tiles/odeep") ||
                                res.name.equals("gfx/tiles/owater")) {
                            nodropping = true;
                        }
                    }
                    if (nodropping) {
                        // we really don't want dropping, so click is moving
                        if (Config.pf) {
                            // purusPfLeftClick(mc.floor(), null);
                            pathto(mc);
                        } else {
                            wdgmsg("click", pc, mc.floor(posres), 1, 0);
                        }
                        return;
                    }
                }
                wdgmsg("drop", pc, mc.floor(posres), ui.modflags());
            }
        });
        return (true);
    }

    public boolean iteminteract(Coord cc, Coord ul) {
        delay(new Hittest(cc, ui.modflags()) {
            public void hit(Coord pc, Coord2d mc, ClickInfo inf) {
                lastItemactClickArgs = null;
                if (inf == null && Config.tilecenter) {
                    mc.x = ((int) mc.x / 11) * 11 + Integer.signum((int) mc.x) * 5;
                    mc.y = ((int) mc.y / 11) * 11 + Integer.signum((int) mc.y) * 5;
                }

                Object[] args = {pc, mc.floor(posres), ui.modflags()};
                args = Utils.extend(args, gobclickargs(inf));

                if (ui.modshift && ui.modmeta)
                    lastItemactClickArgs = args;
                wdgmsg("itemact", args);
            }
        });
        return (true);
    }

    public void iteminteractreplay() {
        wdgmsg("itemact", lastItemactClickArgs);
    }

    public boolean keydown(KeyEvent ev) {
        if (placing != null) {
            if ((ev.getKeyCode() == KeyEvent.VK_LEFT) && placing.adjust.rotate(placing, -1, ui.modflags()))
                return (true);
            if ((ev.getKeyCode() == KeyEvent.VK_RIGHT) && placing.adjust.rotate(placing, 1, ui.modflags()))
                return (true);
        }
        if (camera.keydown(ev))
            return (true);
        return (super.keydown(ev));
    }

    public boolean globtype(char c, KeyEvent ev) {
        int code = ev.getKeyCode();
        if (code == KeyEvent.VK_ADD)
            camera.wheel(Coord.z, -1);
        else if (code == KeyEvent.VK_SUBTRACT)
            camera.wheel(Coord.z, 1);

        return (false);
    }

    public Object tooltip(Coord c, Widget prev) {
        if (selection != null) {
            if (selection.tt != null)
                return (selection.tt);
        } else if (tt != null && ui.modshift) {
            return tt;
        } else if (tooltip != null && ui.modshift) {
            return Text.render(tooltip);
        }
        return (super.tooltip(c, prev));
    }

    public class GrabXL implements Grabber {
        private final Grabber bk;
        public boolean mv = false;

        public GrabXL(Grabber bk) {
            this.bk = bk;
        }

        public boolean mmousedown(Coord cc, final int button) {
            delay(new Maptest(cc) {
                public void hit(Coord pc, Coord2d mc) {
                    bk.mmousedown(mc.round(), button);
                }
            });
            return (true);
        }

        public boolean mmouseup(Coord cc, final int button) {
            delay(new Maptest(cc) {
                public void hit(Coord pc, Coord2d mc) {
                    bk.mmouseup(mc.round(), button);
                }
            });
            return (true);
        }

        public boolean mmousewheel(Coord cc, final int amount) {
            delay(new Maptest(cc) {
                public void hit(Coord pc, Coord2d mc) {
                    bk.mmousewheel(mc.round(), amount);
                }
            });
            return (true);
        }

        public void mmousemove(Coord cc) {
            if (mv) {
                delay(new Maptest(cc) {
                    public void hit(Coord pc, Coord2d mc) {
                        bk.mmousemove(mc.round());
                    }
                });
            }
        }
    }

    private class Selector implements Grabber {
        Coord sc;
        MCache.Overlay ol;
        UI.Grab mgrab;
        int modflags;
        Text tt;
        MapView mv;
        final GrabXL xl = new GrabXL(this) {
            public boolean mmousedown(Coord cc, int button) {
                // System.out.println("mousedown detected in selector");
                if (button != 1)
                    return (false);
                return (super.mmousedown(cc, button));
            }

            public boolean mmousewheel(Coord cc, int amount) {
                return (false);
            }
        };

        public Selector() {
        }

        public Selector(MapView mv) {
            this.mv = mv;
        }

        {
            grab(xl);
            enol(17);
        }

        public boolean mmousedown(Coord mc, int button) {
            synchronized (MapView.this) {
                if (selection != this)
                    return (false);
                if (sc != null) {
                    ol.destroy();
                    mgrab.remove();
                }
                sc = mc.div(MCache.tilesz2);
                modflags = ui.modflags();
                xl.mv = true;
                mgrab = ui.grabmouse(MapView.this);
                synchronized (glob.map.grids) {
                    ol = glob.map.new Overlay(sc, sc, 1 << 17);
                }
                return (true);
            }
        }

        public boolean mmouseup(Coord mc, int button) {
            synchronized (MapView.this) {
                if (sc != null) {
                    Coord ec = mc.div(MCache.tilesz2);
                    xl.mv = false;
                    tt = null;
                    Resource curs = ui.root.getcurs(c);
                    if (button == 1 && curs != null && curs.name.equals("gfx/hud/curs/mine")) {
                        synchronized (glob.map.grids) {
                            miningOverlay = glob.map.new Overlay(ol.getc1(), ol.getc2(), 1 << 18);
                        }
                        enol(18);
                    }
                    ol.destroy();
                    mgrab.remove();
                    if (areaselcb != null)
                        areaselcb.areaselect(ol.getc1(), ol.getc2());
                    wdgmsg("sel", sc, ec, modflags);
                    sc = null;
                    if (PBotAPISelect) {
                        PBotUtils.areaSelect(ui, ol.getc1(), ol.getc2());
                        PBotAPISelect = false;
                        selection.destroy();
                        selection = null;
                    }
                }
                return (true);
            }
        }

        public boolean mmousewheel(Coord mc, int amount) {
            return (false);
        }

        public void mmousemove(Coord mc) {
            synchronized (MapView.this) {
                if (sc != null) {
                    Coord tc = mc.div(MCache.tilesz2);
                    Coord c1 = new Coord(Math.min(tc.x, sc.x), Math.min(tc.y, sc.y));
                    Coord c2 = new Coord(Math.max(tc.x, sc.x), Math.max(tc.y, sc.y));
                    ol.update(c1, c2);
                    tt = Text.render(String.format("%d\u00d7%d", c2.x - c1.x + 1, c2.y - c1.y + 1));
                }
            }
        }

        public void destroy() {
            synchronized (MapView.this) {
                if (sc != null) {
                    ol.destroy();
                    mgrab.remove();
                }
                release(xl);
                disol(17);
            }
        }
    }

    private Camera makecam(Class<? extends Camera> ct, String... args) {
        try {
            try {
                Constructor<? extends Camera> cons = ct.getConstructor(MapView.class, String[].class);
                return (cons.newInstance(new Object[]{this, args}));
            } catch (IllegalAccessException e) {
            } catch (NoSuchMethodException e) {
            }
            try {
                Constructor<? extends Camera> cons = ct.getConstructor(MapView.class);
                return (cons.newInstance(new Object[]{this}));
            } catch (IllegalAccessException e) {
            } catch (NoSuchMethodException e) {
            }
        } catch (InstantiationException e) {
            throw (new Error(e));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException)
                throw ((RuntimeException) e.getCause());
            throw (new RuntimeException(e));
        }
        throw (new RuntimeException("No valid constructor found for camera " + ct.getName()));
    }

    private Camera restorecam() {
        Class<? extends Camera> ct = camtypes.get(Utils.getpref("defcam", null));
        if (ct == null)
            return (new SOrthoCam(true));
        String[] args = (String[]) Utils.deserialize(Utils.getprefb("camargs", null));
        if (args == null) args = new String[0];
        try {
            return (makecam(ct, args));
        } catch (Exception e) {
            return (new SOrthoCam(true));
        }
    }

    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();

    {
        cmdmap.put("cam", (cons, args) -> {
            if (args.length >= 2) {
                Class<? extends Camera> ct = camtypes.get(args[1]);
                String[] cargs = Utils.splice(args, 2);
                if (ct != null) {
                    camera = makecam(ct, cargs);
                    Utils.setpref("defcam", args[1]);
                    Utils.setprefb("camargs", Utils.serialize(cargs));
                } else {
                    throw (new Exception("no such camera: " + args[1]));
                }
            }
        });
        Console.setscmd("placegrid", new Console.Command() {
            public void run(Console cons, String[] args) {
                if ((plobgran = Integer.parseInt(args[1])) < 0)
                    plobgran = 0;
                Utils.setprefi("placegridval", plobgran);
            }
        });
        cmdmap.put("whyload", (cons, args) -> {
            Loading l = lastload;
            if (l == null)
                throw (new Exception("Not loading"));
            l.printStackTrace(cons.out);
        });
        Console.setscmd("clickdb", (cons, args) -> clickdb = Utils.parsebool(args[1], false));
    }

    public Map<String, Console.Command> findcmds() {
        return (cmdmap);
    }

    public void togglegrid() {
        showgrid = !showgrid;
        if (showgrid) {
            Coord tc = new Coord((int) (cc.x / tilesz.x / MCache.cutsz.x - view - 1) * MCache.cutsz.x,
                    (int) (cc.y / tilesz.y / MCache.cutsz.y - view - 1) * MCache.cutsz.y);
            lasttc = tc;
            gridol.update(tc);
        }
    }

    public void aggroclosest() {
        OCache oc = ui.sess.glob.oc;
        synchronized (oc) {
            Gob gobcls = null;
            double gobclsdist = Double.MAX_VALUE;

            for (Gob gob : oc) {
                try {
                    Resource res = gob.getres();
                    if (res != null && "body".equals(res.basename()) && gob.id != player().id) {
                        if (!gob.isFriend()) {
                            double dist = player().rc.dist(gob.rc);
                            if (dist < gobclsdist) {
                                gobcls = gob;
                                gobclsdist = dist;
                            }
                        }
                    }
                } catch (Loading l) {
                }
            }

            if (gobcls != null) {
                ui.gui.act("aggro");
                wdgmsg("click", gobcls.sc, Coord.z, 1, ui.modflags(), 0, (int) gobcls.id, gobcls.rc.floor(posres), 0, 0);
                Gob pl = player();
                wdgmsg("click", pl.sc, pl.rc.floor(posres), 3, 0);
            }
        }
    }

    public void canceltasks() {
        if (pf != null)
            pf.terminate = true;
        if (steelrefueler != null)
            steelrefueler.terminate();
        if (torchlight != null)
            torchlight.terminate();
        if (musselPicker != null)
            musselPicker.interrupt();
    }

    public void removeCustomSprites(int id) {
        OCache oc = ui.sess.glob.oc;
        synchronized (oc) {
            for (Gob gob : oc) {
                Gob.Overlay ol = gob.findol(id);
                if (ol != null)
                    gob.ols.remove(ol);
            }
        }
    }

    public void addHealthSprites() {
        OCache oc = glob.oc;
        synchronized (oc) {
            for (Gob gob : oc) {
                final GobHealth hlt = gob.getattr(GobHealth.class);
                if (hlt != null && hlt.hp < 4) {
                    Gob.Overlay ol = gob.findol(Sprite.GOB_HEALTH_ID);
                    if (ol == null)
                        gob.addol(new Gob.Overlay(Sprite.GOB_HEALTH_ID, new GobHealthSprite(hlt.hp)));
                    else if (((GobHealthSprite) ol.spr).val != hlt.hp)
                        ((GobHealthSprite) ol.spr).update(hlt.hp);
                    oc.changed(gob);
                }
            }
        }
    }

    public void addQualitySprites() {
        OCache oc = glob.oc;
        synchronized (oc) {
            for (Gob gob : oc) {
                final GobQuality hlt = gob.getattr(GobQuality.class);
                if (hlt != null && hlt.quality > 0) {
                    Gob.Overlay ol = gob.findol(Sprite.GOB_QUALITY_ID);
                    if (ol == null)
                        gob.addol(new Gob.Overlay(Sprite.GOB_QUALITY_ID, new GobQualitySprite(hlt.quality)));
                    else if (((GobQualitySprite) ol.spr).val != hlt.quality)
                        ((GobQualitySprite) ol.spr).update(hlt.quality);
                    oc.changed(gob);
                }
            }
        }
    }

    public void refreshGobsAll() {
        OCache oc = glob.oc;
        synchronized (oc) {
            for (Gob gob : oc)
                oc.changed(gob);
        }
    }

    public void refreshGobsGrowthStages() {
        OCache oc = glob.oc;
        synchronized (oc) {
            for (Gob gob : oc) {
                if (gob.type == Type.PLANT || gob.type == Type.MULTISTAGE_PLANT || gob.type == Type.TREE || gob.type == Type.BUSH)
                    oc.changed(gob);
            }
        }
    }

    public void startMusselsPicker(Gob gob) {
        musselPicker = new Thread(new MusselPicker(ui.gui, gob), "MusselPicker");
        musselPicker.start();
    }

    public void showSpecialMenu(final Gob g) {
        g.resname().ifPresent((name) -> {
            final FlowerMenu modmenu = new FlowerMenu((selection) -> {
                switch (selection) {
                    case 0: //Mark for party
                        g.mark(20000);
                        for (Widget wdg = ui.gui.chat.lchild; wdg != null; wdg = wdg.prev) {
                            if (wdg instanceof ChatUI.PartyChat) {
                                final ChatUI.PartyChat chat = (ChatUI.PartyChat) wdg;
                                chat.send(String.format(Mark.CHAT_FMT, g.id, 20000));
                            }
                        }
                        break;
                    case 1: //Highlight for yourself
                        if (!HighlightData.isHighlighted(name)) {
                            HighlightData.add(name);
                            ui.sess.glob.oc.highlightGobs(name);
                        } else {
                            HighlightData.remove(name);
                            ui.sess.glob.oc.unhighlightGobs(name);
                        }
                        break;
                    case 2: //Toggle hide
                        if (Hidden.isHidden(name)) {
                            Hidden.remove(name);
                            ui.sess.glob.oc.unhideAll(name);
                        } else {
                            Hidden.add(name);
                            ui.sess.glob.oc.hideAll(name);
                        }
                        break;
                    case 3: //Toggle Sound
                        if (Alerted.shouldAlert(name)) {
                            Alerted.remove(name);
                        } else {
                            ui.gui.add(new SoundSelector(name), ui.mc);
                        }
                        break;
                    case 4: //Delete all gobs like this one
                        if (g.type == Type.TAMEDANIMAL) {
                            PBotUtils.sysMsg(ui, "I know you tried to delete this, but it's basically just a bad idea. Not adding to delete. Deleting livestock can/will lead to game crashes. If you really want them gone, hide them.", Color.white);
                            break;
                        }
                        Deleted.add(name);
                        ui.sess.glob.oc.removeAll(name);
                        break;
                    case 5: //Custom overlays
                        ui.gui.add(new OverlaySelector(name), ui.mc);
                        break;
                    case 6: //Mark gob on map
                        ui.gui.mapfile.markobj(g.id, g, configuration.getDefaultTextName(g.resname().get()));
                        break;
                }
            }, "Mark for party",
                    !HighlightData.isHighlighted(name) ? "Highlight" : "Remove Highlight",
                    Hidden.isHidden(name) ? "Unhide" : "Hide",
                    Alerted.shouldAlert(name) ? "Remove Sound" : "Add Sound",
                    "Delete",
                    "Custom overlays",
                    "Mark on map");
            ui.gui.add(modmenu, ui.mc);
        });
    }

    private Optional<Gob> gobFromClick(final ClickInfo inf) {
        if (inf == null)
            return Optional.empty();
        Rendered[] st = inf.array();
        for (final Rendered g : st) {
            if (g instanceof Gob) {
                return Optional.of((Gob) g);
            }
        }
        return Optional.empty();
    }

}

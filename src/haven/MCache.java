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

import haven.sloth.gfx.GridMesh;
import haven.sloth.script.pathfinding.Tile;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public class MCache {
    //All for hitmaps/pathfinding
    private static final Pattern deepwater = Pattern.compile("(gfx/tiles/deep)|(gfx/tiles/odeep)|(gfx/tiles/odeeper)");
    private static final Pattern shallowater = Pattern.compile("(gfx/tiles/water)|(gfx/tiles/owater)");
    private static final Pattern cave = Pattern.compile("(gfx/tiles/cave)|(gfx/tiles/rocks/.+)");
    private static final Tile[] id2tile = new Tile[256];

    //
    public static final Coord2d tilesz = new Coord2d(11, 11);
    public static final Coord tilesz2 = tilesz.round(); /* XXX: Remove me in due time. */
    public static final Coord cmaps = new Coord(100, 100);
    public static final Coord cutsz = new Coord(25, 25);
    public static final Coord cutn = cmaps.div(cutsz);
    public final Resource.Spec[] nsets = new Resource.Spec[256];
    @SuppressWarnings("unchecked")
    private final Reference<Resource>[] sets = new Reference[256];
    @SuppressWarnings("unchecked")
    private final Reference<Tileset>[] csets = new Reference[256];
    @SuppressWarnings("unchecked")
    private final Reference<Tiler>[] tiles = new Reference[256];
    Map<Coord, Request> req = new HashMap<>();
    final Map<Coord, Grid> grids = new HashMap<>();
    Session sess;
    Set<Overlay> ols = new HashSet<>();
    public int olseq = 0;
    Map<Integer, Defrag> fragbufs = new TreeMap<>();

    public static class LoadingMap extends Loading {
        public final Coord gc;

        public LoadingMap(Coord gc) {
            super("Waiting for map data...");
            this.gc = gc;
        }

        public LoadingMap(Loading cause) {
            super(cause);
            this.gc = null;
        }
    }

    private static class Request {
        private long lastreq = 0;
        private int reqs = 0;
    }

    public class Overlay {
        private Coord c1, c2;
        private int mask;

        public Overlay(Coord c1, Coord c2, int mask) {
            this.c1 = c1;
            this.c2 = c2;
            this.mask = mask;
            ols.add(this);
            olseq++;
        }

        public void destroy() {
            ols.remove(this);
            olseq++;
        }

        public void update(Coord c1, Coord c2) {
            if (!c1.equals(this.c1) || !c2.equals(this.c2)) {
                olseq++;
                this.c1 = c1;
                this.c2 = c2;
            }
        }

        public Coord getc1() {
            return c1;
        }

        public Coord getc2() {
            return c2;
        }
    }

    public class Grid {
        public final int tiles[] = new int[cmaps.x * cmaps.y];
        public final int z[] = new int[cmaps.x * cmaps.y];
        public final int ol[] = new int[cmaps.x * cmaps.y];
        public final Tile hitmap[] = new Tile[cmaps.x * cmaps.y];
        public final Coord gc, ul;
        public long id;
        public int seq = -1;
        public String mnm;
        private int olseq = -1;
        private final Cut cuts[];
        private Collection<Gob>[] fo = null;

        private class Cut {
            MapMesh mesh;
            Defer.Future<MapMesh> dmesh;

            //Grid layout view
            FastMesh grid;
            Defer.Future<FastMesh> dgrid;

            Rendered[] ols;
        }

        private class Flavobj extends Gob {
            private Flavobj(Coord2d c, double a) {
                super(sess.glob, c);
                this.a = a;
            }

            public Random mkrandoom() {
                Random r = new Random(Grid.this.id);
                r.setSeed(r.nextLong() ^ Double.doubleToLongBits(rc.x));
                r.setSeed(r.nextLong() ^ Double.doubleToLongBits(rc.y));
                return (r);
            }
        }

        private class Flavdraw extends ResDrawable {
            final GLState extra;

            Flavdraw(Gob gob, Indir<Resource> res, Message sdt, GLState extra) {
                super(gob, res, sdt);
                this.extra = extra;
            }

            public void setup(RenderList rl) {
                try {
                    init();
                } catch (Loading e) {
                    return;
                }
                rl.add(spr, extra);
            }
        }

        public Grid(Coord gc) {
            this.gc = gc;
            this.ul = gc.mul(cmaps);
            cuts = new Cut[cutn.x * cutn.y];
            for (int i = 0; i < cuts.length; i++)
                cuts[i] = new Cut();
        }

        public Tile gethitmap(Coord tc) {
            return hitmap[tc.x + (tc.y * cmaps.x)];
        }

        public void sethitmap(Coord tc, Tile t) {
            hitmap[tc.x + (tc.y * cmaps.x)] = t;
        }

        public int gettile(Coord tc) {
            return (tiles[tc.x + (tc.y * cmaps.x)]);
        }

        public int getz(Coord tc) {
            return (z[tc.x + (tc.y * cmaps.x)]);
        }

        public int getol(Coord tc) {
            return (ol[tc.x + (tc.y * cmaps.x)]);
        }

        private void makeflavor() {
            @SuppressWarnings("unchecked")
            Collection<Gob>[] fo = (Collection<Gob>[]) new Collection[cutn.x * cutn.y];
            for (int i = 0; i < fo.length; i++)
                fo[i] = new LinkedList<Gob>();
            Coord c = new Coord(0, 0);
            Coord tc = gc.mul(cmaps);
            int i = 0;
            Random rnd = new Random(id);
            for (c.y = 0; c.y < cmaps.x; c.y++) {
                for (c.x = 0; c.x < cmaps.y; c.x++, i++) {
                    Tileset set = tileset(tiles[i]);
                    int fp = rnd.nextInt();
                    int rp = rnd.nextInt();
                    double a = rnd.nextDouble();
                    if (set.flavobjs.size() > 0) {
                        if ((fp % set.flavprob) == 0) {
                            Indir<Resource> r = set.flavobjs.pick(rp % set.flavobjs.tw);
                            if (Config.hideflovisual) {
                                Resource res = r.get();
                                if (res != null && res.name.startsWith("gfx/tiles/"))
                                    continue;
                            }
                            Gob g = new Flavobj(c.add(tc).mul(tilesz).add(tilesz.div(2)), a * 2 * Math.PI);
                            g.setattr(new Flavdraw(g, r, Message.nil, set.flavobjmat));
                            Coord cc = c.div(cutsz);
                            fo[cc.x + (cc.y * cutn.x)].add(g);
                        }
                    }
                }
            }
            this.fo = fo;
        }

        public Collection<Gob> getfo(Coord cc) {
            if (fo == null)
                makeflavor();
            return (fo[cc.x + (cc.y * cutn.x)]);
        }

        private Cut geticut(Coord cc) {
            return (cuts[cc.x + (cc.y * cutn.x)]);
        }

        public MapMesh getcut(Coord cc) {
            Cut cut = geticut(cc);
            if (cut.dmesh != null) {
                if (cut.dmesh.done() || (cut.mesh == null)) {
                    MapMesh old = cut.mesh;
                    cut.mesh = cut.dmesh.get();
                    cut.dmesh = null;
                    cut.ols = null;
                    if (old != null)
                        old.dispose();
                }
            }
            return (cut.mesh);
        }

        /**
         * returns the Grid layout cut given the coordinate
         */
        public FastMesh getgcut(Coord cc) {
            Cut cut = geticut(cc);
            if (cut.dgrid != null) {
                if (cut.dgrid.done() || cut.grid == null) {
                    FastMesh old = cut.grid;
                    cut.grid = cut.dgrid.get();
                    cut.dgrid = null;
                    if (old != null)
                        old.dispose();
                }
            }
            return cut.grid;
        }

        public Rendered getolcut(int ol, Coord cc) {
            int nseq = MCache.this.olseq;
            if (this.olseq != nseq) {
                for (int i = 0; i < cutn.x * cutn.y; i++) {
                    if (cuts[i].ols != null) {
                        for (Rendered r : cuts[i].ols) {
                            if (r instanceof Disposable)
                                ((Disposable) r).dispose();
                        }
                    }
                    cuts[i].ols = null;
                }
                this.olseq = nseq;
            }
            Cut cut = geticut(cc);
            if (cut.ols == null)
                cut.ols = getcut(cc).makeols();
            return (cut.ols[ol]);
        }

        private void buildcut(final Coord cc) {
            final Cut cut = geticut(cc);
            Defer.Future<?> prev = cut.dmesh;
            cut.dmesh = Defer.later(new Defer.Callable<MapMesh>() {
                public MapMesh call() {
                    Random rnd = new Random(id);
                    rnd.setSeed(rnd.nextInt() ^ cc.x);
                    rnd.setSeed(rnd.nextInt() ^ cc.y);
                    return (MapMesh.build(MCache.this, rnd, ul.add(cc.mul(cutsz)), cutsz));
                }

                public String toString() {
                    return ("Building map...");
                }
            });
            if (prev != null)
                prev.cancel();
            //automatically build a grid mesh with every cut
            buildgcut(cc);
        }

        /**
         * Builds the grid layout mesh
         */
        private void buildgcut(final Coord cc) {
            final Cut cut = geticut(cc);
            Defer.Future<?> gprev = cut.dgrid;
            Random rnd = new Random(id);
            cut.dgrid = Defer.later(new Defer.Callable<FastMesh>() {
                public FastMesh call() {
                    return (GridMesh.build(MCache.this, ul.add(cc.mul(cutsz)), cutsz));
                }

                public String toString() {
                    return ("Building grid overlay...");
                }
            });
            if (gprev != null)
                gprev.cancel();
        }

        public void ivneigh(Coord nc) {
            Coord cc = new Coord();
            for (cc.y = 0; cc.y < cutn.y; cc.y++) {
                for (cc.x = 0; cc.x < cutn.x; cc.x++) {
                    if ((((nc.x < 0) && (cc.x == 0)) || ((nc.x > 0) && (cc.x == cutn.x - 1)) || (nc.x == 0)) &&
                            (((nc.y < 0) && (cc.y == 0)) || ((nc.y > 0) && (cc.y == cutn.y - 1)) || (nc.y == 0))) {
                        buildcut(new Coord(cc));
                    }
                }
            }
        }

        public void tick(int dt) {
            if (fo != null) {
                for (Collection<Gob> fol : fo) {
                    for (Gob fo : fol)
                        fo.ctick(dt);
                }
            }
        }

        private void invalidate() {
            for (int y = 0; y < cutn.y; y++) {
                for (int x = 0; x < cutn.x; x++)
                    buildcut(new Coord(x, y));
            }
            fo = null;
            for (Coord ic : new Coord[]{
                    new Coord(-1, -1), new Coord(0, -1), new Coord(1, -1),
                    new Coord(-1, 0), new Coord(1, 0),
                    new Coord(-1, 1), new Coord(0, 1), new Coord(1, 1)}) {
                Grid ng = grids.get(gc.add(ic));
                if (ng != null)
                    ng.ivneigh(ic.inv());
            }
        }

        public void dispose() {
            for (Cut cut : cuts) {
                if (cut.dmesh != null)
                    cut.dmesh.cancel();
                if (cut.mesh != null)
                    cut.mesh.dispose();
                if (cut.ols != null) {
                    for (Rendered r : cut.ols) {
                        if (r instanceof Disposable)
                            ((Disposable) r).dispose();
                    }
                }
            }
        }

        public void fill(Message msg) {
            String mmname = msg.string().intern();
            if (mmname.equals(""))
                mnm = null;
            else
                mnm = mmname;
            int[] pfl = new int[256];
            while (true) {
                int pidx = msg.uint8();
                if (pidx == 255)
                    break;
                pfl[pidx] = msg.uint8();
            }
            Message blob = new ZMessage(msg);
            id = blob.int64();
            while (true) {
                int tileid = blob.uint8();
                if (tileid == 255)
                    break;
                String resnm = blob.string();
                int resver = blob.uint16();
                nsets[tileid] = new Resource.Spec(Resource.remote(), resnm, resver);

                if (shallowater.matcher(resnm).matches()) {
                    id2tile[tileid] = Tile.SHALLOWWATER;
                } else if (deepwater.matcher(resnm).matches()) {
                    id2tile[tileid] = Tile.DEEPWATER;
                } else if (cave.matcher(resnm).matches()) {
                    id2tile[tileid] = Tile.CAVE;
                }
            }
            for (int i = 0; i < tiles.length; i++) {
                tiles[i] = blob.uint8();

                //we can figure out shallow vs deep hitmap from this info, ridges will come later
                hitmap[i] = id2tile[tiles[i]];
            }
            for (int i = 0; i < z.length; i++)
                z[i] = blob.int16();
            for (int i = 0; i < ol.length; i++)
                ol[i] = 0;
            while (true) {
                int pidx = blob.uint8();
                if (pidx == 255)
                    break;
                int fl = pfl[pidx];
                int type = blob.uint8();
                Coord c1 = new Coord(blob.uint8(), blob.uint8());
                Coord c2 = new Coord(blob.uint8(), blob.uint8());
                int ol;
                if (type == 0) {
                    if ((fl & 1) == 1)
                        ol = 2;
                    else
                        ol = 1;
                } else if (type == 1) {
                    if ((fl & 1) == 1)
                        ol = 8;
                    else
                        ol = 4;
                } else if (type == 2) {
                    if ((fl & 1) == 1)
                        ol = 32;
                    else
                        ol = 16;
                } else if (type == 3) {
                    ol = 64;
                } else {
                    throw (new RuntimeException("Unknown plot type " + type));
                }
                for (int y = c1.y; y <= c2.y; y++) {
                    for (int x = c1.x; x <= c2.x; x++) {
                        this.ol[x + (y * cmaps.x)] |= ol;
                    }
                }
            }
            invalidate();
            seq++;
        }
    }

    public MCache(Session sess) {
        this.sess = sess;
    }

    public void ctick(int dt) {
        synchronized (grids) {
            for (Grid g : grids.values()) {
                g.tick(dt);
            }
        }
    }

    public void invalidateAll() {
        synchronized (grids) {
            for (final Grid g : grids.values()) {
                g.invalidate();
            }
        }
    }

    public void invalidate(Coord cc) {
        synchronized (req) {
            if (req.get(cc) == null)
                req.put(cc, new Request());
        }
    }

    public void invalblob(Message msg) {
        int type = msg.uint8();
        if (type == 0) {
            invalidate(msg.coord());
        } else if (type == 1) {
            Coord ul = msg.coord();
            Coord lr = msg.coord();
            trim(ul, lr);
        } else if (type == 2) {
            trimall();
        }
    }

    private Grid cached = null;

    public Grid getgrid(Coord gc) {
        synchronized (grids) {
            if ((cached == null) || !cached.gc.equals(gc)) {
                cached = grids.get(gc);
                if (cached == null) {
                    request(gc);
                    throw (new LoadingMap(gc));
                }
            }
            return (cached);
        }
    }

    public Optional<Grid> getgrido(final Coord gc) {
        synchronized (grids) {
            if ((cached == null) || !cached.gc.equals(gc)) {
                cached = grids.get(gc);
                if (cached == null) {
                    request(gc);
                    return Optional.empty();
                }
            }
            return Optional.of(cached);
        }
    }

    public Optional<Grid> getgrido(final long id) {
        synchronized (grids) {
            if ((cached == null) || cached.id != id) {
                for (Grid g : grids.values()) {
                    if (g.id == id) {
                        cached = g;
                        return Optional.of(g);
                    }
                }
                return Optional.empty();
            } else {
                return Optional.of(cached);
            }
        }
    }

    public Grid getgridt(Coord tc) {
        return (getgrid(tc.div(cmaps)));
    }

    public Optional<Grid> getgridto(Coord tc) {
        return (getgrido(tc.div(cmaps)));
    }

    public int gettile_safe(Coord tc) {
        final Optional<Grid> grid = getgridto(tc);
        if (grid.isPresent()) {
            final Grid g = grid.get();
            return g.gettile(tc.sub(g.ul));
        } else {
            return 0;
        }
    }

    public Tile gethitmap(Coord tc) {
        final Optional<Grid> g = getgridto(tc);
        if (g.isPresent()) {
            return g.get().gethitmap(tc.sub(g.get().ul));
        } else {
            return null;
        }
    }

    public void sethitmap(Coord tc, Tile t) {
        getgridto(tc).ifPresent(g -> {
            g.sethitmap(tc.sub(g.ul), t);
        });
    }

    public int gettile(Coord tc) {
        Grid g = getgridt(tc);
        return (g.gettile(tc.sub(g.ul)));
    }

    public int getz(Coord tc) {
        Grid g = getgridt(tc);
        return (g.getz(tc.sub(g.ul)));
    }

    public int getz_safe(Coord tc) {
        final Optional<Grid> grid = getgridto(tc);
        if (grid.isPresent()) {
            final Grid g = grid.get();
            return g.getz(tc.sub(g.ul));
        } else {
            return 0;
        }
    }

    public double getcz_old(double px, double py) {
        double tw = tilesz.x, th = tilesz.y;
        Coord ul = new Coord(Utils.floordiv(px, tw), Utils.floordiv(py, th));
        double sx = Utils.floormod(px, tw) / tw;
        double sy = Utils.floormod(py, th) / th;
        return (((1.0f - sy) * (((1.0f - sx) * getz(ul)) + (sx * getz(ul.add(1, 0))))) +
                (sy * (((1.0f - sx) * getz(ul.add(0, 1))) + (sx * getz(ul.add(1, 1))))));
    }

    public double getcz(double px, double py) {
        double tw = tilesz.x, th = tilesz.y;
        Coord ul = new Coord(Utils.floordiv(px, tw), Utils.floordiv(py, th));
        double sx = Utils.floormod(px, tw) / tw;
        double sy = Utils.floormod(py, th) / th;
        return (((1.0f - sy) * (((1.0f - sx) * getz_safe(ul)) + (sx * getz_safe(ul.add(1, 0))))) +
                (sy * (((1.0f - sx) * getz_safe(ul.add(0, 1))) + (sx * getz_safe(ul.add(1, 1))))));
    }

    public double getcz(Coord2d pc) {
        return (getcz(pc.x, pc.y));
    }

    public double getcz_old(Coord2d pc) {
        return (getcz_old(pc.x, pc.y));
    } //only exists because follow cam hates the new getz

    public float getcz(float px, float py) {
        return ((float) getcz((double) px, (double) py));
    }

    public float getcz(Coord pc) {
        return (getcz(pc.x, pc.y));
    }

    public Coord3f getzp(Coord2d pc) {
        return (new Coord3f((float) pc.x, (float) pc.y, (float) getcz(pc)));
    }

    public Coord3f getzp_old(Coord2d pc) {
        return (new Coord3f((float) pc.x, (float) pc.y, (float) getcz_old(pc)));
    }//only exists because follow cam hates the new getz


    public int getol(Coord tc) {
        Grid g = getgridt(tc);
        int ol = g.getol(tc.sub(g.ul));
        for (Overlay lol : ols) {
            if (tc.isect(lol.c1, lol.c2.add(lol.c1.inv()).add(new Coord(1, 1))))
                ol |= lol.mask;
        }
        return (ol);
    }

    public MapMesh getcut(Coord cc) {
        synchronized (grids) {
            return (getgrid(cc.div(cutn)).getcut(cc.mod(cutn)));
        }
    }

    public FastMesh getgcut(Coord cc) {
        synchronized (grids) {
            return (getgrid(cc.div(cutn)).getgcut(cc.mod(cutn)));
        }
    }

    public Collection<Gob> getfo(Coord cc) {
        synchronized (grids) {
            return (getgrid(cc.div(cutn)).getfo(cc.mod(cutn)));
        }
    }

    public Rendered getolcut(int ol, Coord cc) {
        synchronized (grids) {
            return (getgrid(cc.div(cutn)).getolcut(ol, cc.mod(cutn)));
        }
    }

    public void mapdata2(Message msg) {
        Coord c = msg.coord();
        synchronized (grids) {
            synchronized (req) {
                if (req.containsKey(c)) {
                    Grid g = grids.get(c);
                    if (g == null) {
                        grids.put(c, g = new Grid(c));
                        cached = null;
                    }
                    g.fill(msg);
                    req.remove(c);
                    olseq++;
                    final Grid _g = g;
                    final MCache _this = this;
                    if (Config.savemmap) {
                        Defer.later(new Defer.Callable<Void>() {
                            public Void call() {
                                try {
                                    new MapGridSave(_this, _g);
                                } catch (Loading e) {
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

    public void mapdata(Message msg) {
        long now = System.currentTimeMillis();
        int pktid = msg.int32();
        int off = msg.uint16();
        int len = msg.uint16();
        Defrag fragbuf;
        synchronized (fragbufs) {
            if ((fragbuf = fragbufs.get(pktid)) == null) {
                fragbuf = new Defrag(len);
                fragbufs.put(pktid, fragbuf);
            }
            fragbuf.add(msg.bytes(), off);
            fragbuf.last = now;
            if (fragbuf.done()) {
                mapdata2(fragbuf.msg());
                fragbufs.remove(pktid);
            }

            /* Clean up old buffers */
            for (Iterator<Map.Entry<Integer, Defrag>> i = fragbufs.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry<Integer, Defrag> e = i.next();
                Defrag old = e.getValue();
                if (now - old.last > 10000)
                    i.remove();
            }
        }
    }

    public Resource tilesetr(int i) {
        synchronized (sets) {
            Resource res = (sets[i] == null) ? null : (sets[i].get());
            if (res == null) {
                if (nsets[i] == null)
                    return (null);
                res = nsets[i].get();
                sets[i] = new SoftReference<Resource>(res);
            }
            return (res);
        }
    }

    public Tileset tileset(int i) {
        synchronized (csets) {
            Tileset cset = (csets[i] == null) ? null : (csets[i].get());
            if (cset == null) {
                Resource res = tilesetr(i);
                if (res == null)
                    return (null);
                try {
                    cset = res.layer(Tileset.class);
                } catch (Loading e) {
                    throw (new LoadingMap(e));
                }
                csets[i] = new SoftReference<Tileset>(cset);
            }
            return (cset);
        }
    }

    public Tiler tiler(int i) {
        synchronized (tiles) {
            Tiler tile = (tiles[i] == null) ? null : (tiles[i].get());
            if (tile == null) {
                Tileset set = tileset(i);
                if (set == null)
                    return (null);
                tile = set.tfac().create(i, set);
                tiles[i] = new SoftReference<Tiler>(tile);
            }
            return (tile);
        }
    }

    public void trimall() {
        synchronized (grids) {
            synchronized (req) {
                for (Grid g : grids.values())
                    g.dispose();
                grids.clear();
                req.clear();
                cached = null;
            }
        }
    }

    public void trim(Coord ul, Coord lr) {
        synchronized (grids) {
            synchronized (req) {
                for (Iterator<Map.Entry<Coord, Grid>> i = grids.entrySet().iterator(); i.hasNext(); ) {
                    Map.Entry<Coord, Grid> e = i.next();
                    Coord gc = e.getKey();
                    Grid g = e.getValue();
                    if ((gc.x < ul.x) || (gc.y < ul.y) || (gc.x > lr.x) || (gc.y > lr.y)) {
                        g.dispose();
                        i.remove();
                    }
                }
                for (Iterator<Coord> i = req.keySet().iterator(); i.hasNext(); ) {
                    Coord gc = i.next();
                    if ((gc.x < ul.x) || (gc.y < ul.y) || (gc.x > lr.x) || (gc.y > lr.y))
                        i.remove();
                }
                cached = null;
            }
        }
    }

    public void request(Coord gc) {
        synchronized (req) {
            if (!req.containsKey(gc))
                req.put(new Coord(gc), new Request());
        }
    }

    public void reqarea(Coord ul, Coord br) {
        ul = ul.div(cutsz);
        br = br.div(cutsz);
        Coord rc = new Coord();
        for (rc.y = ul.y; rc.y <= br.y; rc.y++) {
            for (rc.x = ul.x; rc.x <= br.x; rc.x++) {
                try {
                    getcut(new Coord(rc));
                } catch (Loading e) {
                }
            }
        }
    }

    public void sendreqs() {
        long now = System.currentTimeMillis();
        synchronized (req) {
            for (Iterator<Map.Entry<Coord, Request>> i = req.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry<Coord, Request> e = i.next();
                Coord c = e.getKey();
                Request r = e.getValue();
                if (now - r.lastreq > 1000) {
                    r.lastreq = now;
                    if (++r.reqs >= 5) {
                        i.remove();
                    } else {
                        PMessage msg = new PMessage(Session.MSG_MAPREQ);
                        msg.addcoord(c);
                        sess.sendmsg(msg);
                    }
                }
            }
        }
    }
}

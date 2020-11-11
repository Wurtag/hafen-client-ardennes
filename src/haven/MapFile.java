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

import haven.Defer.Future;
import haven.resutil.Ridges;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import static haven.MCache.cmaps;

public class MapFile {
    public static boolean debug = false;
    private static final int NOZ = Integer.MIN_VALUE;
    private static MapFile instance = null;
    public final ResCache store;
    public final String filename;
    public final Collection<Long> knownsegs = new HashSet<>();
    public final Collection<Marker> markers = new ArrayList<>();
    public final Map<Long, SMarker> smarkers = new HashMap<>(); //safety check for ensuring no duplicates based off oid
    public int markerseq = 0;
    public final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public MapFile(ResCache store, String filename) {
        this.store = store;
        this.filename = filename;
    }

    private void checklock() {
        if ((lock.getReadHoldCount() == 0) && !lock.isWriteLockedByCurrentThread())
            throw (new IllegalMonitorStateException());
    }

    private String mangle(String datum) {
        StringBuilder buf = new StringBuilder();
        buf.append("map/");
        if (!filename.equals("")) {
            buf.append(filename);
            buf.append('/');
        }
        buf.append(datum);
        return (buf.toString());
    }

    private InputStream sfetch(String ctl, Object... args) throws IOException {
        return (store.fetch(mangle(String.format(ctl, args))));
    }

    private OutputStream sstore(String ctl, Object... args) throws IOException {
        return (store.store(mangle(String.format(ctl, args))));
    }

    public static MapFile load(ResCache store, String filename) {
        if (instance != null)
            return instance;
        else {
            final MapFile file = new MapFile(store, filename);
            InputStream fp;
            try {
                fp = file.sfetch("index");
            } catch (FileNotFoundException e) {
                return (file);
            } catch (IOException e) {
                return (null);
            }
            try (StreamMessage data = new StreamMessage(fp)) {
                int ver = data.uint8();
                if (ver == 1) {
                    for (int i = 0, no = data.int32(); i < no; i++)
                        file.knownsegs.add(data.int64());
                    for (int i = 0, no = data.int32(); i < no; i++) {
                        try {
                            Marker mark = loadmarker(data);
                            file.markers.add(mark);
                            if (mark instanceof SMarker)
                                file.smarkers.put(((SMarker) mark).oid, (SMarker) mark);
                        } catch (Message.BinError e) {
                            Debug.log.printf("mapfile warning: error when loading marker, data may be missing: %s\n", e);
                        }
                    }
                } else {
                    Debug.log.printf("mapfile warning: unknown mapfile index version: %d\n", ver);
                    return (null);
                }
            } catch (Message.BinError e) {
                Debug.log.printf("mapfile warning: error when loading index: %s\n", e);
                return (null);
            }
            instance = file;
            return (file);
        }
    }

    private void save() {
        checklock();
        OutputStream fp = null;
        do {
            try {
                fp = sstore("index");
            } catch (IOException e) {
                if (e.getMessage().contains("another process")) {
                    try {
                        Thread.sleep(100);
                    } catch (Exception ex) {
                        return;
                    }
                } else {
                    throw (new StreamMessage.IOError(e));
                }
            }
        } while (fp == null);
        try (StreamMessage out = new StreamMessage(fp)) {
            out.adduint8(1);
            out.addint32(knownsegs.size());
            for (Long seg : knownsegs)
                out.addint64(seg);
            out.addint32(markers.size());
            for (Marker mark : markers)
                savemarker(out, mark);
        }
    }

    public void defersave() {
        synchronized (procmon) {
            gdirty = true;
            process();
        }
    }

    public static class GridInfo {
        public final long id, seg;
        public final Coord sc;

        public GridInfo(long id, long seg, Coord sc) {
            this.id = id;
            this.seg = seg;
            this.sc = sc;
        }
    }

    public final BackCache<Long, GridInfo> gridinfo = new BackCache<>(100, id -> {
        checklock();
        InputStream fp;
        try {
            fp = sfetch("gi-%x", id);
        } catch (IOException e) {
            return (null);
        }
        try (StreamMessage data = new StreamMessage(fp)) {
            int ver = data.uint8();
            if (ver == 1) {
                return (new GridInfo(data.int64(), data.int64(), data.coord()));
            } else {
                throw (new Message.FormatError("Unknown gridinfo version: " + ver));
            }
        } catch (Message.BinError e) {
            Debug.log.printf("mapfile warning: error when loading gridinfo for %x: %s\n", id, e);
            return (null);
        }
    }, (id, info) -> {
        checklock();
        OutputStream fp = null;
        do {
            try {
                fp = sstore("gi-%x", info.id);
            } catch (IOException e) {
                if (e.getMessage().contains("another process")) {
                    try {
                        Thread.sleep(100);
                    } catch (Exception ex) {
                        return;
                    }
                } else {
                    throw (new StreamMessage.IOError(e));
                }
            }
        } while (fp == null);
        try (StreamMessage out = new StreamMessage(fp)) {
            out.adduint8(1);
            out.addint64(info.id);
            out.addint64(info.seg);
            out.addcoord(info.sc);
        }
    });

    private static Runnable locked(Runnable r, Lock lock) {
        return (() -> {
            lock.lock();
            try {
                r.run();
            } finally {
                lock.unlock();
            }
        });
    }

    private static <A, R> Function<A, R> locked(Function<A, R> f, Lock lock) {
        return (v -> {
            lock.lock();
            try {
                return (f.apply(v));
            } finally {
                lock.unlock();
            }
        });
    }

    private final Object procmon = new Object();
    private Thread processor = null;
    private final Collection<Pair<MCache, Collection<MCache.Grid>>> updqueue = new HashSet<>();
    private final Collection<Segment> dirty = new HashSet<>();
    private final Collection<Pair<Long, GridInfo>> gidirty = new HashSet<>();
    private boolean gdirty = false;

    private class Processor extends HackThread {
        Processor() {
            super("Mapfile processor");
        }

        public void run() {
            try {
                long last = System.currentTimeMillis();
                while (true) {
                    Runnable task;
                    long now = System.currentTimeMillis();
                    synchronized (procmon) {
                        if (!updqueue.isEmpty()) {
                            Pair<MCache, Collection<MCache.Grid>> el = Utils.take(updqueue);
                            task = () -> MapFile.this.update(el.a, el.b);
                        } else if (!dirty.isEmpty()) {
                            Segment seg = Utils.take(dirty);
                            task = locked(() -> segments.put(seg.id, seg), lock.writeLock());
                        } else if (!gidirty.isEmpty()) {
                            Pair<Long, GridInfo> pair = Utils.take(gidirty);
                            task = locked(() -> gridinfo.put(pair.a, pair.b), lock.writeLock());
                        } else if (gdirty) {
                            task = locked(MapFile.this::save, lock.readLock());
                            gdirty = false;
                        } else {
                            if (now - last > 10000) {
                                processor = null;
                                return;
                            }
                            procmon.wait(5000);
                            continue;
                        }
                    }
                    task.run();
                    last = now;
                }
            } catch (InterruptedException e) {
            } finally {
                synchronized (procmon) {
                    processor = null;
                }
            }
        }
    }

    private void process() {
        synchronized (procmon) {
            if (processor == null) {
                Thread np = new Processor();
                np.start();
                processor = np;
            }
            procmon.notifyAll();
        }
    }

    public static Resource loadsaved(Resource.Pool pool, Resource.Spec spec) {
        try {
            return (spec.get());
        } catch (Loading l) {
            throw (l);
        } catch (Exception e) {
            return (pool.load(spec.name).get());
        }
    }

    public abstract static class Marker {
        public long seg;
        public Coord tc;
        public String nm;

        public Marker(long seg, Coord tc, String nm) {
            this.seg = seg;
            this.tc = tc;
            this.nm = nm;
        }

        public String name() {
            return nm;
        }

        public String tip() {
            return nm;
        }
    }

    public static class PMarker extends Marker {
        public Color color;

        public PMarker(long seg, Coord tc, String nm, Color color) {
            super(seg, tc, nm);
            this.color = color;
        }
    }

    public static class SMarker extends Marker {
        public long oid;
        public Resource.Spec res;

        public SMarker(long seg, Coord tc, String nm, long oid, Resource.Spec res) {
            super(seg, tc, nm);
            this.oid = oid;
            this.res = res;
        }
    }

    private static Marker loadmarker(Message fp) {
        int ver = fp.uint8();
        if (ver == 1) {
            long seg = fp.int64();
            Coord tc = fp.coord();
            String nm = fp.string();
            char type = (char) fp.uint8();
            switch (type) {
                case 'p':
                    Color color = fp.color();
                    return (new PMarker(seg, tc, nm, color));
                case 's':
                    long oid = fp.int64();
                    Resource.Spec res = new Resource.Spec(Resource.remote(), fp.string(), fp.uint16());
                    return (new SMarker(seg, tc, nm, oid, res));
                default:
                    throw (new Message.FormatError("Unknown marker type: " + (int) type));
            }
        } else {
            throw (new Message.FormatError("Unknown marker version: " + ver));
        }
    }

    private static void savemarker(Message fp, Marker mark) {
        fp.adduint8(1);
        fp.addint64(mark.seg);
        fp.addcoord(mark.tc);
        fp.addstring(mark.nm);
        if (mark instanceof PMarker) {
            fp.adduint8('p');
            fp.addcolor(((PMarker) mark).color);
        } else if (mark instanceof SMarker) {
            SMarker sm = (SMarker) mark;
            fp.adduint8('s');
            fp.addint64(sm.oid);
            fp.addstring(sm.res.name);
            fp.adduint16(sm.res.ver);
        } else {
            throw (new ClassCastException("Can only save PMarkers and SMarkers"));
        }
    }

    public void add(Marker mark) {
        lock.writeLock().lock();
        try {
            if (markers.add(mark)) {
                if (mark instanceof SMarker)
                    smarkers.put(((SMarker) mark).oid, (SMarker) mark);
                defersave();
                markerseq++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(Marker mark) {
        lock.writeLock().lock();
        try {
            if (markers.remove(mark)) {
                if (mark instanceof SMarker)
                    smarkers.remove(((SMarker) mark).oid, mark);
                defersave();
                markerseq++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void update(Marker mark) {
        lock.readLock().lock();
        try {
            if (markers.contains(mark)) {
                defersave();
                markerseq++;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public static class TileInfo {
        public final Resource.Spec res;
        public final int prio;

        public TileInfo(Resource.Spec res, int prio) {
            this.res = res;
            this.prio = prio;
        }
    }

    public static class DataGrid {
        public final TileInfo[] tilesets;
        public final byte[] tiles;
        public final int[] z;
        public final long mtime;

        public DataGrid(TileInfo[] tilesets, byte[] tiles, int[] z, long mtime) {
            this.tilesets = tilesets;
            this.tiles = tiles;
            this.z = z;
            this.mtime = mtime;
        }

        public int gettile(Coord c) {
            return (tiles[c.x + (c.y * cmaps.x)] & 0xff);
        }

        public int getz(Coord c) {
            return (z[c.x + (c.y * cmaps.x)]);
        }

        private BufferedImage tiletex(int t, BufferedImage[] texes, boolean[] cached) {
            if (cached[t])
                return texes[t];
            else {
                Resource r = null;
                try {
                    r = loadsaved(Resource.remote(), tilesets[t].res);
                } catch (Loading l) {
                    throw (l);
                } catch (Exception e) {
                    Debug.log.printf("mapfile warning: could not load tileset resource %s(v%d): %s\n", tilesets[t].res.name, tilesets[t].res.ver, e);
                }
                if (r != null) {
                    Resource.Image ir = r.layer(Resource.imgc);
                    if (ir != null) {
                        texes[t] = ir.img;
                    }
                }
                cached[t] = true;
                return texes[t];
            }
        }

        private Tiler tiler(int t, Tiler[] tilers, boolean[] cached) {
            if (cached[t])
                return tilers[t];
            else {
                final Resource r = loadsaved(Resource.remote(), tilesets[t].res);
                final Tileset ts = r.layer(Tileset.class);
                if (ts != null) {
                    //This can be null because some tiles are `notile`...
                    final Tiler tile = ts.tfac().create(t, ts);
                    tilers[t] = tile;
                }
                cached[t] = true;
                return tilers[t];
            }
        }

        private static final Coord[] tecs = {
                new Coord(0, -1),
                new Coord(1, 0),
                new Coord(0, 1),
                new Coord(-1, 0)
        };
        private static final Coord[] tccs = {
                new Coord(0, 0),
                new Coord(1, 0),
                new Coord(1, 1),
                new Coord(0, 1)
        };

        private boolean brokenp(Tiler t, Coord tc, final Tiler[] tilers, final boolean[] tlcache) {
            int bz = ((Ridges.RidgeTile) t).breakz();  //The distance at which a ridge is formed
            //Look at the four tiles around us to get the minimum break distance
            for (Coord ec : tecs) {
                t = tiler(gettile(tc.add(ec)), tilers, tlcache);
                if (t instanceof Ridges.RidgeTile)
                    bz = Math.min(bz, ((Ridges.RidgeTile) t).breakz());
            }

            //Now figure out based on other tiles around us if we hit that break limit and should be a ridge
            //Ignore NOZ heights as these are nonupdated maps
            for (int i = 0; i < 4; i++) {
                final int z1 = getz(tc.add(tccs[i]));
                final int z2 = getz(tc.add(tccs[(i + 1) % 4]));
                //dumb mistake - 99999999
                if (z1 != NOZ && z2 != NOZ && z1 != -99999999 && z2 != -99999999) {
                    if (Math.abs(z2 - z1) > bz) {
                        return (true);
                    }
                }
            }
            return (false);
        }

        public BufferedImage render(Coord off) {
            WritableRaster buf = PUtils.imgraster(cmaps);
            Coord c = new Coord();
            {
                BufferedImage[] texes = new BufferedImage[256];
                boolean[] cached = new boolean[256];
                for (c.y = 0; c.y < cmaps.y; c.y++) {
                    for (c.x = 0; c.x < cmaps.x; c.x++) {
                        int t = gettile(c);
                        BufferedImage tex = tiletex(t, texes, cached);
                        int rgb = 0;
                        if (tex != null)
                            rgb = tex.getRGB(Utils.floormod(c.x + off.x, tex.getWidth()),
                                    Utils.floormod(c.y + off.y, tex.getHeight()));
                        buf.setSample(c.x, c.y, 0, (rgb & 0x00ff0000) >>> 16);
                        buf.setSample(c.x, c.y, 1, (rgb & 0x0000ff00) >>> 8);
                        buf.setSample(c.x, c.y, 2, (rgb & 0x000000ff) >>> 0);
                        buf.setSample(c.x, c.y, 3, (rgb & 0xff000000) >>> 24);
                    }
                }
                for (c.y = 1; c.y < cmaps.y - 1; c.y++) {
                    for (c.x = 1; c.x < cmaps.x - 1; c.x++) {
                        int p = tilesets[gettile(c)].prio;
                        if ((tilesets[gettile(c.add(-1, 0))].prio > p) ||
                                (tilesets[gettile(c.add(1, 0))].prio > p) ||
                                (tilesets[gettile(c.add(0, -1))].prio > p) ||
                                (tilesets[gettile(c.add(0, 1))].prio > p)) {
                            buf.setSample(c.x, c.y, 0, 0);
                            buf.setSample(c.x, c.y, 1, 0);
                            buf.setSample(c.x, c.y, 2, 0);
                            buf.setSample(c.x, c.y, 3, 255);
                        }
                    }
                }
            }

            if (z[0] != NOZ) {
                Tiler[] tilers = new Tiler[256];
                boolean[] tlcached = new boolean[256];
                for (c.y = 1; c.y < MCache.cmaps.y - 1; ++c.y) {
                    for (c.x = 1; c.x < MCache.cmaps.x - 1; ++c.x) {
                        final Tiler t = tiler(gettile(c), tilers, tlcached);
                        if (t instanceof Ridges.RidgeTile && brokenp(t, c, tilers, tlcached)) {
                            for (int y = c.y - 1; y <= c.y + 1; ++y) {
                                for (int x = c.x - 1; x <= c.x + 1; ++x) {
                                    Color cc = new Color(buf.getSample(x, y, 0), buf.getSample(x, y, 1),
                                            buf.getSample(x, y, 2), buf.getSample(x, y, 3));
                                    final Color blended = Utils.blendcol(cc, Color.BLACK, x == c.x && y == c.y ? 1.0 : 0.1);
                                    buf.setSample(x, y, 0, blended.getRed());
                                    buf.setSample(x, y, 1, blended.getGreen());
                                    buf.setSample(x, y, 2, blended.getBlue());
                                    buf.setSample(x, y, 3, blended.getAlpha());
                                }
                            }
                        }
                    }
                }
            }
            return (PUtils.rasterimg(buf));
        }

        public static final Resource.Spec notile = new Resource.Spec(Resource.remote(), "gfx/tiles/notile", -1);
        public static final DataGrid nogrid;

        static {
            nogrid = new DataGrid(new TileInfo[]{new TileInfo(notile, 0)}, new byte[cmaps.x * cmaps.y], new int[cmaps.x * cmaps.y], 0);
        }
    }

    public static class Grid extends DataGrid {
        public final long id;
        private boolean[] norepl;
        private int useq = -1;

        public Grid(long id, TileInfo[] tilesets, byte[] tiles, int[] z, long mtime) {
            super(tilesets, tiles, z, mtime);
            this.id = id;
        }

        public static Grid from(MCache map, MCache.Grid cg) {
            int oseq = cg.seq;
            int nt = 0;
            Resource.Spec[] sets = new Resource.Spec[256];
            int[] tmap = new int[256];
            int[] rmap = new int[256];
            boolean[] norepl = new boolean[256];
            Arrays.fill(tmap, -1);
            for (int tn : cg.tiles) {
                if (tmap[tn] == -1) {
                    tmap[tn] = nt;
                    rmap[nt] = tn;
                    sets[nt] = map.nsets[tn];
                    try {
                        for (String tag : map.tileset(tn).tags) {
                            if (tag.equals("norepl"))
                                norepl[nt] = true;
                        }
                    } catch (Loading l) {
                    }
                    nt++;
                }
            }
            int[] prios = new int[nt];
            for (int i = 0, tn = 0; i < 256; i++) {
                if (tmap[i] != -1)
                    prios[tmap[i]] = tn++;
            }
            TileInfo[] infos = new TileInfo[nt];
            for (int i = 0; i < nt; i++) {
                infos[i] = new TileInfo(sets[i], prios[i]);
            }
            byte[] tiles = new byte[cmaps.x * cmaps.y];
            int[] z = new int[cmaps.x * cmaps.y];
            for (int i = 0; i < cg.tiles.length; i++) {
                tiles[i] = (byte) (tmap[cg.tiles[i]]);
                z[i] = cg.z[i];
            }
            Grid g = new Grid(cg.id, infos, tiles, z, System.currentTimeMillis());
            g.norepl = norepl;
            g.useq = oseq;
            return (g);
        }

        public Grid mergeprev(Grid prev) {
            if ((norepl == null) || (prev.tiles.length != this.tiles.length))
                return (this);
            boolean[] used = new boolean[prev.tilesets.length];
            boolean any = false;
            int[] tmap = new int[prev.tilesets.length];
            for (int i = 0; i < tmap.length; i++)
                tmap[i] = -1;
            for (int i = 0; i < this.tiles.length; i++) {
                if (norepl[this.tiles[i]]) {
                    used[prev.tiles[i]] = true;
                    any = true;
                }
            }
            if (!any)
                return (this);
            TileInfo[] ntilesets = this.tilesets;
            for (int i = 0; i < used.length; i++) {
                if (used[i] && (tmap[i] < 0)) {
                    dedup:
                    {
                        for (int o = 0; o < this.tilesets.length; o++) {
                            if (this.tilesets[o].res.name.equals(prev.tilesets[i].res.name)) {
                                tmap[i] = o;
                                break dedup;
                            }
                        }
                        tmap[i] = ntilesets.length;
                        ntilesets = Utils.extend(ntilesets, prev.tilesets[i]);
                    }
                }
            }
            byte[] ntiles = new byte[this.tiles.length];
            for (int i = 0; i < this.tiles.length; i++) {
                if (norepl[this.tiles[i]])
                    ntiles[i] = (byte) tmap[prev.tiles[i]];
                else
                    ntiles[i] = this.tiles[i];
            }
            Grid g = new Grid(this.id, ntilesets, ntiles, prev.z, this.mtime);
            g.useq = this.useq;
            return (g);
        }

        public void save(Message fp) {
            fp.adduint8(2);
            ZMessage z = new ZMessage(fp);
            z.addint64(id);
            z.addint64(mtime);
            z.adduint8(tilesets.length);
            for (int i = 0; i < tilesets.length; i++) {
                z.addstring(tilesets[i].res.name);
                z.adduint16(tilesets[i].res.ver);
                z.adduint8(tilesets[i].prio);
            }
            z.addbytes(tiles);
            for (int i = 0; i < this.z.length; ++i) {
                z.addint32(this.z[i]);
            }
            z.finish();
        }

        public void save(MapFile file) {
            OutputStream fp = null;
            do {
                try {
                    fp = file.sstore("grid-%x", id);
                } catch (IOException e) {
                    if (e.getMessage().contains("another process")) {
                        try {
                            Thread.sleep(100);
                        } catch (Exception ex) {
                            return;
                        }
                    } else {
                        throw (new StreamMessage.IOError(e));
                    }
                }
            } while (fp == null);
            try (StreamMessage out = new StreamMessage(fp)) {
                save(out);
            }
        }

        public void remove() {
            useq = -2;
        }

        public static Grid load(MapFile file, long id) {
            InputStream fp;
            try {
                fp = file.sfetch("grid-%x", id);
            } catch (IOException e) {
                Debug.log.printf("mapfile warning: error when locating grid %x: %s\n", id, e);
                return (null);
            }
            try (StreamMessage data = new StreamMessage(fp)) {
                int ver = data.uint8();
                if ((ver >= 1) && (ver <= 2)) {
                    ZMessage z = new ZMessage(data);
                    long storedid = z.int64();
                    if (storedid != id)
                        throw (new Message.FormatError(String.format("Grid ID mismatch: expected %s, got %s", id, storedid)));
                    long mtime = (ver >= 2) ? z.int64() : System.currentTimeMillis();
                    List<TileInfo> tilesets = new ArrayList<>();
                    for (int i = 0, no = z.uint8(); i < no; i++)
                        tilesets.add(new TileInfo(new Resource.Spec(Resource.remote(), z.string(), z.uint16()), z.uint8()));
                    byte[] tiles = z.bytes(cmaps.x * cmaps.y);
                    int[] zmap = new int[cmaps.x * cmaps.y];
                    for (int i = 0; i < zmap.length; ++i)
                        zmap[i] = NOZ;
                    return (new Grid(id, tilesets.toArray(new TileInfo[0]), tiles, zmap, mtime));
                } else if (ver == 3) {
                    ZMessage z = new ZMessage(data);
                    long storedid = z.int64();
                    if (storedid != id)
                        throw (new Message.FormatError(String.format("Grid ID mismatch: expected %s, got %s", id, storedid)));
                    long mtime = z.int64();
                    List<TileInfo> tilesets = new ArrayList<>();
                    for (int i = 0, no = z.uint8(); i < no; i++)
                        tilesets.add(new TileInfo(new Resource.Spec(Resource.remote(), z.string(), z.uint16()), z.uint8()));
                    byte[] tiles = z.bytes(cmaps.x * cmaps.y);
                    int[] zmap = new int[cmaps.x * cmaps.y];
                    for (int i = 0; i < zmap.length; ++i) {
                        zmap[i] = z.int32();
                    }
                    return (new Grid(id, tilesets.toArray(new TileInfo[0]), tiles, zmap, mtime));
                } else {
                    throw (new Message.FormatError(String.format("Unknown grid data version for %x: %d", id, ver)));
                }
            } catch (Message.BinError e) {
                Debug.log.printf("mapfile warning: error when loading grid %x: %s\n", id, e);
                return (null);
            }
        }

        private BufferedImage tiletex(int t, BufferedImage[] texes, boolean[] cached) {
            if (!cached[t]) {
                Resource r = null;
                try {
                    r = loadsaved(Resource.remote(), tilesets[t].res);
                } catch (Loading l) {
                    throw (l);
                } catch (Exception e) {
                    Debug.log.printf("mapfile warning: could not load tileset resource %s(v%d): %s\n", tilesets[t].res.name, tilesets[t].res.ver, e);
                }
                if (r != null) {
                    Resource.Image ir = r.layer(Resource.imgc);
                    if (ir != null) {
                        texes[t] = ir.img;
                    }
                }
                cached[t] = true;
            }
            return (texes[t]);
        }

        public int gettile(Coord c) {
            return (tiles[c.x + (c.y * cmaps.x)] & 0xff);
        }

        public BufferedImage render(Coord off) {
            BufferedImage[] texes = new BufferedImage[256];
            boolean[] cached = new boolean[256];
            WritableRaster buf = PUtils.imgraster(cmaps);
            Coord c = new Coord();
            for (c.y = 0; c.y < cmaps.y; c.y++) {
                for (c.x = 0; c.x < cmaps.x; c.x++) {
                    int t = gettile(c);
                    BufferedImage tex = tiletex(t, texes, cached);
                    int rgb = 0;
                    if (tex != null)
                        rgb = tex.getRGB(Utils.floormod(c.x + off.x, tex.getWidth()),
                                Utils.floormod(c.y + off.y, tex.getHeight()));
                    buf.setSample(c.x, c.y, 0, (rgb & 0x00ff0000) >>> 16);
                    buf.setSample(c.x, c.y, 1, (rgb & 0x0000ff00) >>> 8);
                    buf.setSample(c.x, c.y, 2, (rgb & 0x000000ff) >>> 0);
                    buf.setSample(c.x, c.y, 3, (rgb & 0xff000000) >>> 24);
                }
            }
            for (c.y = 1; c.y < cmaps.y - 1; c.y++) {
                for (c.x = 1; c.x < cmaps.x - 1; c.x++) {
                    int p = tilesets[gettile(c)].prio;
                    if ((tilesets[gettile(c.add(-1, 0))].prio > p) ||
                            (tilesets[gettile(c.add(1, 0))].prio > p) ||
                            (tilesets[gettile(c.add(0, -1))].prio > p) ||
                            (tilesets[gettile(c.add(0, 1))].prio > p)) {
                        buf.setSample(c.x, c.y, 0, 0);
                        buf.setSample(c.x, c.y, 1, 0);
                        buf.setSample(c.x, c.y, 2, 0);
                        buf.setSample(c.x, c.y, 3, 255);
                    }
                }
            }
            return (PUtils.rasterimg(buf));
        }
    }

    //And this is probably why Loftar avoid storing z levels
    public static class ZoomGrid extends DataGrid {
        public final long seg;
        public final int lvl;
        public final Coord sc;

        public ZoomGrid(long seg, int lvl, Coord sc, TileInfo[] tilesets, byte[] tiles, int[] z, long mtime) {
            super(tilesets, tiles, z, mtime);
            this.seg = seg;
            this.lvl = lvl;
            this.sc = sc;
        }

        /**
         * Get the max mtime of the grids that make up this zoomgrid
         */
        public static long localmtime(MapFile file, Segment seg, int lvl, Coord sc) {
            if ((lvl < 1) || ((sc.x & ((1 << lvl) - 1)) != 0) || ((sc.y & ((1 << lvl) - 1)) != 0))
                throw (new IllegalArgumentException(String.format("%s %s", sc, lvl)));
            DataGrid[] lower = new DataGrid[4];
            long maxmtime = 0;
            for (int i = 0; i < 4; i++) {
                int x = i % 2, y = i / 2;
                lower[i] = fetchg(file, seg, lvl - 1, sc.add(x << (lvl - 1), y << (lvl - 1)));
                if (lower[i] != null) {
                    maxmtime = Math.max(maxmtime, lower[i].mtime);
                }
            }
            return maxmtime;
        }

        public static ZoomGrid fetch(MapFile file, Segment seg, int lvl, Coord sc) {
            ZoomGrid loaded = load(file, seg.id, lvl, sc);
            //zoom grids should update anytime a grid they are made from updated
            if (loaded != null && loaded.mtime >= localmtime(file, seg, lvl, sc))
                return (loaded);
            return (from(file, seg, lvl, sc));
        }

        private static DataGrid fetchg(MapFile file, Segment seg, int lvl, Coord sc) {
            if (lvl == 0) {
                Long id = seg.map.get(sc);
                if (id == null)
                    return (null);
                return (Grid.load(file, id));
            } else {
                return (fetch(file, seg, lvl, sc));
            }
        }

        public static ZoomGrid from(MapFile file, Segment seg, int lvl, Coord sc) {
            if ((lvl < 1) || ((sc.x & ((1 << lvl) - 1)) != 0) || ((sc.y & ((1 << lvl) - 1)) != 0))
                throw (new IllegalArgumentException(String.format("%s %s", sc, lvl)));
            DataGrid[] lower = new DataGrid[4];
            boolean any = false;
            long maxmtime = 0;
            for (int i = 0; i < 4; i++) {
                int x = i % 2, y = i / 2;
                lower[i] = fetchg(file, seg, lvl - 1, sc.add(x << (lvl - 1), y << (lvl - 1)));
                if (lower[i] != null) {
                    any = true;
                    maxmtime = Math.max(maxmtime, lower[i].mtime);
                } else {
                    lower[i] = DataGrid.nogrid;
                }
            }
            if (!any)
                return (null);

            /* XXX: This is hardly "correct", but the correct
             * implementation would require a topological sort, and
             * it's not like it really matters that much. */
            int nt = 0;
            TileInfo[] infos;
            Map<String, Integer> rinfos;
            {
                Resource.Pool pool = null;
                String[] sets = new String[256];
                Set<String> hassets = new HashSet<>();
                Map<String, Integer> vers = new HashMap<>();
                for (int i = 0; i < 4; i++) {
                    if (lower[i] == null)
                        continue;
                    for (int tn = 0; tn < lower[i].tilesets.length; tn++) {
                        Resource.Spec set = lower[i].tilesets[tn].res;
                        if (pool == null)
                            pool = set.pool;
                        vers.put(set.name, Math.max(vers.getOrDefault(set.name, 0), set.ver));
                        if (!hassets.contains(set.name)) {
                            sets[nt++] = set.name;
                            hassets.add(set.name);
                        }
                    }
                }
                if (nt >= 256)
                    throw (new IllegalArgumentException(Integer.toString(nt)));
                infos = new TileInfo[nt];
                rinfos = new HashMap<>();
                for (int i = 0; i < nt; i++) {
                    infos[i] = new TileInfo(new Resource.Spec(pool, sets[i], vers.get(sets[i])), i);
                    rinfos.put(sets[i], i);
                }
            }

            byte[] tiles = new byte[cmaps.x * cmaps.y];
            int[] z = new int[cmaps.x * cmaps.y];
            //Each zoom level works by zooming out twice the distance as before.
            //It figures out which tile it should render by taking the highest tileid of the 4 around the original point
            //For z levels we'll do the same, altho ridges may be slightly off on zoommaps.
            for (int gn = 0; gn < 4; gn++) {
                int gx = gn % 2, gy = gn / 2;
                DataGrid cg = lower[gn];
                if (cg == null)
                    continue;
                byte[] tmap = new byte[256];
                Arrays.fill(tmap, (byte) -1);
                for (int i = 0; i < cg.tilesets.length; i++)
                    tmap[i] = rinfos.get(cg.tilesets[i].res.name).byteValue();
                Coord off = cmaps.div(2).mul(gx, gy);
                byte[] tc = new byte[4];
                int maxz;
                byte[] tcn = new byte[4];
                for (int y = 0; y < cmaps.y / 2; y++) {
                    for (int x = 0; x < cmaps.x / 2; x++) {
                        maxz = NOZ;
                        int nd = 0;
                        for (int sy = 0; sy < 2; sy++) {
                            for (int sx = 0; sx < 2; sx++) {
                                byte st = tmap[cg.gettile(new Coord(x * 2, y * 2))];
                                maxz = Math.max(maxz, cg.getz(new Coord(x * 2, y * 2)));
                                st:
                                {
                                    for (int i = 0; i < nd; i++) {
                                        if (tc[i] == st) {
                                            tcn[i]++;
                                            break st;
                                        }
                                    }
                                    tc[nd] = st;
                                    tcn[nd] = 1;
                                    nd++;
                                }
                            }
                        }
                        int mi = 0;
                        for (int i = 1; i < nd; i++) {
                            if (tcn[i] > tcn[mi])
                                mi = i;
                        }
                        tiles[(x + off.x) + ((y + off.y) * cmaps.x)] = tc[mi];
                        z[(x + off.x) + ((y + off.y) * cmaps.x)] = maxz;
                    }
                }
            }
            ZoomGrid ret = new ZoomGrid(seg.id, lvl, sc, infos, tiles, z, maxmtime);
            ret.save(file);
            return (ret);
        }

        //v1 = no z levels
        //v2 = z levels
        public void save(Message fp) {
            fp.adduint8(2);
            ZMessage z = new ZMessage(fp);
            z.addint64(seg);
            z.addint32(lvl);
            z.addcoord(sc);
            z.addint64(mtime);
            z.adduint8(tilesets.length);
            for (int i = 0; i < tilesets.length; i++) {
                z.addstring(tilesets[i].res.name);
                z.adduint16(tilesets[i].res.ver);
                z.adduint8(tilesets[i].prio);
            }
            z.addbytes(tiles);
            for (int i = 0; i < this.z.length; ++i) {
                z.addint32(this.z[i]);
            }
            z.finish();
        }

        public void save(MapFile file) {
            OutputStream fp = null;
            do {
                try {
                    fp = file.sstore("zgrid-%x-%d-%d-%d", seg, lvl, sc.x, sc.y);
                } catch (IOException e) {
                    if (e.getMessage().contains("another process")) {
                        try {
                            Thread.sleep(100);
                        } catch (Exception ex) {
                            return;
                        }
                    } else {
                        throw (new StreamMessage.IOError(e));
                    }
                }
            } while (fp == null);
            try (StreamMessage out = new StreamMessage(fp)) {
                save(out);
            }
        }

        public static ZoomGrid load(MapFile file, long seg, int lvl, Coord sc) {
            InputStream fp = null;
            do {
                try {
                    fp = file.sfetch("zgrid-%x-%d-%d-%d", seg, lvl, sc.x, sc.y);
                } catch (IOException e) {
                    Debug.log.printf("mapfile warning: error when locating zoomgrid (%d, %d) in %x@%d: %s\n", sc.x, sc.y, seg, lvl, e);
                    if (e.getMessage().contains("another process")) {
                        try {
                            Thread.sleep(100);
                        } catch (Exception ex) {
                            return null;
                        }
                    } else {
                        return null;
                    }
                }
            } while (fp == null);
            try (StreamMessage data = new StreamMessage(fp)) {
                if (data.eom())
                    return (null);
                int ver = data.uint8();
                if (ver == 1) {
                    ZMessage z = new ZMessage(data);
                    long storedseg = z.int64();
                    if (storedseg != seg)
                        throw (new Message.FormatError(String.format("Zoomgrid segment mismatch: expected %s, got %s", seg, storedseg)));
                    long storedlvl = z.int32();
                    if (storedlvl != lvl)
                        throw (new Message.FormatError(String.format("Zoomgrid level mismatch: expected %s, got %s", lvl, storedlvl)));
                    Coord storedsc = z.coord();
                    if (!sc.equals(storedsc))
                        throw (new Message.FormatError(String.format("Zoomgrid coord mismatch: expected %s, got %s", sc, storedsc)));

                    long mtime = z.int64();
                    List<TileInfo> tilesets = new ArrayList<>();
                    for (int i = 0, no = z.uint8(); i < no; i++)
                        tilesets.add(new TileInfo(new Resource.Spec(Resource.remote(), z.string(), z.uint16()), z.uint8()));
                    byte[] tiles = z.bytes(cmaps.x * cmaps.y);
                    int[] zmap = new int[cmaps.x * cmaps.y];
                    for (int i = 0; i < zmap.length; ++i)
                        zmap[i] = NOZ;
                    return (new ZoomGrid(seg, lvl, sc, tilesets.toArray(new TileInfo[0]), tiles, zmap, mtime));
                } else if (ver == 2) {
                    ZMessage z = new ZMessage(data);
                    long storedseg = z.int64();
                    if (storedseg != seg)
                        throw (new Message.FormatError(String.format("Zoomgrid segment mismatch: expected %s, got %s", seg, storedseg)));
                    long storedlvl = z.int32();
                    if (storedlvl != lvl)
                        throw (new Message.FormatError(String.format("Zoomgrid level mismatch: expected %s, got %s", lvl, storedlvl)));
                    Coord storedsc = z.coord();
                    if (!sc.equals(storedsc))
                        throw (new Message.FormatError(String.format("Zoomgrid coord mismatch: expected %s, got %s", sc, storedsc)));

                    long mtime = z.int64();
                    List<TileInfo> tilesets = new ArrayList<>();
                    for (int i = 0, no = z.uint8(); i < no; i++)
                        tilesets.add(new TileInfo(new Resource.Spec(Resource.remote(), z.string(), z.uint16()), z.uint8()));
                    byte[] tiles = z.bytes(cmaps.x * cmaps.y);
                    int[] zmap = new int[cmaps.x * cmaps.y];
                    for (int i = 0; i < zmap.length; ++i)
                        zmap[i] = z.int32();
                    return (new ZoomGrid(seg, lvl, sc, tilesets.toArray(new TileInfo[0]), tiles, zmap, mtime));
                } else {
                    throw (new Message.FormatError(String.format("Unknown zoomgrid data version for (%d, %d) in %x@%d: %d", sc.x, sc.y, seg, lvl, ver)));
                }
            } catch (Message.BinError e) {
                Debug.log.printf("Unknown zoomgrid data version for (%d, %d) in %x@%d: %s", sc.x, sc.y, seg, lvl, e);
                return (null);
            }
        }

        public static int inval(MapFile file, long seg, Coord sc) {
            for (int lvl = 1; true; lvl++) {
                sc = new Coord(sc.x & ~((1 << lvl) - 1), sc.y & ~((1 << lvl) - 1));
                try {
                    file.sfetch("zgrid-%x-%d-%d-%d", seg, lvl, sc.x, sc.y).close();
                } catch (FileNotFoundException e) {
                    return (lvl - 1);
                } catch (IOException e) {
                    Debug.log.printf("mapfile warning: error when invalidating zoomgrid (%d, %d) in %x@%d: %s\n", sc.x, sc.y, seg, lvl, e);
                    return (lvl - 1);
                }
                try {
                    file.sstore("zgrid-%x-%d-%d-%d", seg, lvl, sc.x, sc.y).close();
                } catch (IOException e) {
                    //Just ignore this one
                }
            }
        }
    }

    public static class ZoomCoord {
        public final int lvl;
        public final Coord c;

        public ZoomCoord(int lvl, Coord c) {
            this.lvl = lvl;
            this.c = c;
        }

        public int hashCode() {
            return ((c.hashCode() * 31) + lvl);
        }

        public boolean equals(Object o) {
            if (!(o instanceof ZoomCoord))
                return (false);
            ZoomCoord that = (ZoomCoord) o;
            return ((this.lvl == that.lvl) && this.c.equals(that.c));
        }
    }

    public class Segment {
        public final long id;
        public final BMap<Coord, Long> map = new HashBMap<>();
        private final Map<Long, Cached> cache = new CacheMap<>(CacheMap.RefType.WEAK);
        private final Map<Coord, ByCoord> ccache = new CacheMap<>(CacheMap.RefType.WEAK);
        private final Map<ZoomCoord, ByZCoord> zcache = new CacheMap<>(CacheMap.RefType.WEAK);

        public Segment(long id) {
            this.id = id;
        }

        private class Cached implements Indir<Grid> {
            Grid loaded;
            Future<Grid> loading;

            Cached(Future<Grid> loading) {
                this.loading = loading;
            }

            public Grid get() {
                if (loaded == null)
                    loaded = loading.get(0);
                return (loaded);
            }
        }

        private Grid loaded(long id) {
            checklock();
            synchronized (cache) {
                Cached cur = cache.get(id);
                if (cur != null)
                    return (cur.loaded);
            }
            return (null);
        }

        private Future<Grid> loadgrid(long id) {
            return (Defer.later(() -> Grid.load(MapFile.this, id)));
        }

        private Cached grid0(long id) {
            checklock();
            synchronized (cache) {
                return (cache.computeIfAbsent(id, k -> new Cached(loadgrid(k))));
            }
        }

        public Indir<Grid> grid(long id) {
            return (grid0(id));
        }

        public String gridtilename(final Coord tc, final Coord gc) {
            if (map.containsKey(gc)) {
                if (cache.containsKey(map.get(gc))) {
                    final Grid g = cache.get(map.get(gc)).loaded;
                    return g.tilesets[g.gettile(tc.sub(gc.mul(cmaps)))].res.name;
                } else {
                    return "Unknown";
                }
            } else {
                return "Unknown";
            }
        }

        public long gridid(Coord gc) {
            return map.get(gc);
        }

        public int gridseq(Coord gc) {
            if (map.containsKey(gc)) {
                return cache.get(map.get(gc)).loaded.useq;
            } else {
                return -10;
            }
        }

        public void invalidate(final Coord gc) {
            if (map.containsKey(gc)) {
                final long id = map.remove(gc);
                cache.remove(id);
                synchronized (procmon) {
                    dirty.add(this);
                    gidirty.add(new Pair<>(id, new GridInfo(id, -1, gc)));
                    process();
                }
            }
        }

        private class ByCoord implements Indir<Grid> {
            final Coord sc;
            Cached cur;

            ByCoord(Coord sc, Cached cur) {
                this.sc = sc;
                this.cur = cur;
            }

            public Grid get() {
                Cached cur = this.cur;
                if (cur == null)
                    return (null);
                return (cur.get());
            }
        }

        private Future<ZoomGrid> loadzgrid(ZoomCoord zc) {
            return (Defer.later(() -> ZoomGrid.fetch(MapFile.this, Segment.this, zc.lvl, zc.c)));
        }

        private class ByZCoord implements Indir<ZoomGrid> {
            final ZoomCoord zc;
            ZoomGrid loaded;
            Future<ZoomGrid> loading;

            ByZCoord(ZoomCoord zc, Future<ZoomGrid> loading) {
                this.zc = zc;
                this.loading = loading;
            }

            public ZoomGrid get() {
                if (loaded == null)
                    loaded = loading.get(0);
                return (loaded);
            }
        }

        public Indir<Grid> grid(Coord gc) {
            checklock();
            synchronized (ccache) {
                return (ccache.computeIfAbsent(gc, k -> {
                    Long id = map.get(k);
                    Cached cur = (id == null) ? null : grid0(id);
                    return (new ByCoord(k, cur));
                }));
            }
        }

        public Indir<? extends DataGrid> grid(int lvl, Coord gc) {
            if ((lvl < 0) || ((gc.x & ((1 << lvl) - 1)) != 0) || ((gc.y & ((1 << lvl) - 1)) != 0))
                throw (new IllegalArgumentException(String.format("%s %s", gc, lvl)));
            if (lvl == 0)
                return (grid(gc));
            synchronized (zcache) {
                ZoomCoord zc = new ZoomCoord(lvl, gc);
                return (zcache.computeIfAbsent(zc, k -> new ByZCoord(k, loadzgrid(k))));
            }
        }

        private void include(long id, Coord sc) {
            map.put(sc, id);
            int zl = ZoomGrid.inval(MapFile.this, this.id, sc);
            synchronized (zcache) {
                for (int lvl = 1; lvl < zl; lvl++) {
                    ZoomCoord zc = new ZoomCoord(lvl, new Coord(sc.x & ~((1 << lvl) - 1), sc.y & ~((1 << lvl) - 1)));
                    ByZCoord zg = zcache.get(zc);
                    if (zg != null) {
                        zg.loading = loadzgrid(zc);
                        zg.loaded = null;
                    }
                }
            }
            ByCoord bc;
            synchronized (ccache) {
                bc = ccache.get(sc);
            }
            if ((bc != null) && (bc.cur == null))
                bc.cur = grid0(id);
        }

        private void include(Grid grid, Coord sc) {
            checklock();
            include(grid.id, sc);
            synchronized (cache) {
                Cached cur = cache.get(grid.id);
                if (cur != null)
                    cur.loaded = grid;
            }
        }
    }

    public final BackCache<Long, Segment> segments = new BackCache<>(5, id -> {
        checklock();
        InputStream fp;
        try {
            fp = sfetch("seg-%x", id);
        } catch (IOException e) {
            return (null);
        }
        try (StreamMessage data = new StreamMessage(fp)) {
            int ver = data.uint8();
            if (ver == 1) {
                Segment seg = new Segment(id);
                ZMessage z = new ZMessage(data);
                long storedid = z.int64();
                if (storedid != id)
                    throw (new Message.FormatError(String.format("Segment ID mismatch: expected %x, got %x", id, storedid)));
                for (int i = 0, no = z.int32(); i < no; i++) {
                    final Coord sc = z.coord();
                    final long gid = z.int64();
                    seg.map.put(sc, gid);
                }
                return (seg);
            } else {
                throw (new Message.FormatError("Unknown segment data version: " + ver));
            }
        } catch (Message.BinError e) {
            Debug.log.printf("mapfile warning: error when loading segment %x: %s\n", id, e);
            return (null);
        }
    }, (id, seg) -> {
        checklock();
        OutputStream fp = null;
        do {
            try {
                fp = sstore("seg-%x", seg.id);
            } catch (IOException e) {
                if (e.getMessage().contains("another process")) {
                    try {
                        Thread.sleep(100);
                    } catch (Exception ex) {
                        return;
                    }
                } else {
                    throw (new StreamMessage.IOError(e));
                }
            }
        } while (fp == null);
        try (StreamMessage out = new StreamMessage(fp)) {
            out.adduint8(1);
            ZMessage z = new ZMessage(out);
            z.addint64(seg.id);
            z.addint32(seg.map.size());
            for (Map.Entry<Coord, Long> e : seg.map.entrySet()) {
                z.addcoord(e.getKey()).addint64(e.getValue());
            }
            z.finish();
        }
        if (knownsegs.add(id))
            defersave();
    });

    private void merge(Segment dst, Segment src, Coord soff) {
        checklock();
        for (Map.Entry<Coord, Long> gi : src.map.entrySet()) {
            long id = gi.getValue();
            Coord sc = gi.getKey();
            Coord dc = sc.sub(soff);
            dst.include(id, dc);
            gridinfo.put(id, new GridInfo(id, dst.id, dc));
        }
        boolean mf = false;
        for (Marker mark : markers) {
            if (mark.seg == src.id) {
                mark.seg = dst.id;
                mark.tc = mark.tc.sub(soff.mul(cmaps));
                mf = true;
            }
        }
        if (mf)
            markerseq++;
        knownsegs.remove(src.id);
        defersave();
        synchronized (procmon) {
            dirty.add(dst);
            process();
        }
    }

    public void update(MCache map, Collection<MCache.Grid> grids) {
        lock.writeLock().lock(); //write lock, why? Some of these need it, not all
        try {
            long mseg = -1;
            Coord moff = null;
            Collection<MCache.Grid> missing = new ArrayList<>(grids.size());
            Collection<Pair<Long, Coord>> merge = null;
            for (MCache.Grid g : grids) {
                GridInfo info = gridinfo.get(g.id);
                if (info == null) {
                    missing.add(g);
                    continue;
                }
                Segment seg = segments.get(info.seg);
                if (seg == null) {
                    missing.add(g);
                    continue;
                }
                if (moff == null) {
                    Coord psc = seg.map.reverse().get(g.id);
                    if (psc == null) {
                        if (debug)
                            Debug.log.printf("mapfile warning: grid %x is oddly gone from segment %x; was at %s\n", g.id, seg.id, info.sc);
                        missing.add(g);
                        continue;
                    } else if (!psc.equals(info.sc)) {
                        Debug.log.printf("mapfile warning: segment-offset mismatch for grid %x in segment %x: segment has %s, gridinfo has %s\n", g.id, seg.id, psc, info.sc);
                        missing.add(g);
                        continue;
                    }
                    mseg = seg.id;
                    moff = info.sc.sub(g.gc);
                }
                Grid cur = seg.loaded(g.id);
                //I want to force update on anything still sporting NOZ or day old grids..
                if (cur == null || cur.useq != g.seq || g.z[0] == NOZ) {
                    Grid sg = Grid.from(map, g);
                    Grid prev = cur;
                    if (prev == null)
                        prev = Grid.load(MapFile.this, sg.id);
                    if (prev != null)
                        sg = sg.mergeprev(prev);
                    sg.save(MapFile.this);
                    seg.include(sg, g.gc.add(moff)); //XXX: Write lock required
                    //seg.updateGrid(sg.id, sg);
                }
                if (seg.id != mseg) {
                    if (merge == null)
                        merge = new HashSet<>();
                    Coord soff = info.sc.sub(g.gc.add(moff));
                    merge.add(new Pair<>(seg.id, soff));
                }
            }
            if (!missing.isEmpty()) {
                Segment seg;
                if (mseg == -1) {
                    seg = new Segment(Utils.el(missing).id);
                    moff = Coord.z;
                    if (debug) Debug.log.printf("mapfile: creating new segment %x\n", seg.id);
                } else {
                    seg = segments.get(mseg);
                }
                synchronized (procmon) {
                    dirty.add(seg);
                    process();
                }
                for (MCache.Grid g : missing) {
                    Grid sg = Grid.from(map, g);
                    Coord sc = g.gc.add(moff);
                    sg.save(MapFile.this);
                    seg.include(sg, sc);
                    gridinfo.put(g.id, new GridInfo(g.id, seg.id, sc));
                }
            }
            if (merge != null) {
                for (Pair<Long, Coord> mel : merge) {
                    Segment a = segments.get(mseg);
                    Segment b = segments.get(mel.a);
                    Coord ab = mel.b;
                    Segment src, dst;
                    Coord soff;
                    if (a.map.size() > b.map.size()) {
                        src = b;
                        dst = a;
                        soff = ab;
                    } else {
                        src = a;
                        dst = b;
                        soff = ab.inv();
                    }
                    if (debug)
                        Debug.log.printf("mapfile: merging segment %x (%d) into %x (%d) at %s\n", src.id, src.map.size(), dst.id, dst.map.size(), soff);
                    merge(dst, src, soff);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        if (debug) Debug.log.printf("mapfile: update completed\n");
    }

    // You need multiple grids around one otherwise it can merge!
    public void updategrids(MCache map, Collection<MCache.Grid> grids) {
        if (!grids.isEmpty()) {
            synchronized (procmon) {
                updqueue.add(new Pair<>(map, grids));
                process();
            }
        }
    }

    public static interface ExportFilter {
        public boolean includeseg(long id);

        public boolean includegrid(Segment seg, Coord sc, long id);

        public boolean includemark(Marker mark);

        public static final ExportFilter all = new ExportFilter() {
            public boolean includeseg(long id) {
                return (true);
            }

            public boolean includegrid(Segment seg, Coord sc, long id) {
                return (true);
            }

            public boolean includemark(Marker mark) {
                return (true);
            }
        };

        public static ExportFilter segment(long sid) {
            return (new ExportFilter() {
                public boolean includeseg(long id) {
                    return (id == sid);
                }

                public boolean includegrid(Segment seg, Coord sc, long id) {
                    return (seg.id == sid);
                }

                public boolean includemark(Marker mark) {
                    return (mark.seg == sid);
                }
            });
        }

        public static ExportFilter around(Marker mark, double rad) {
            return (new ExportFilter() {
                public boolean includeseg(long id) {
                    return (id == mark.seg);
                }

                public boolean includegrid(Segment seg, Coord sc, long id) {
                    return ((seg.id == mark.seg) && (sc.mul(cmaps).add(cmaps.div(2)).dist(mark.tc) <= rad));
                }

                public boolean includemark(Marker cmark) {
                    return (cmark == mark);
                }
            });
        }
    }

    public static interface ExportStatus {
        public default void grid(int cs, int ns, int cg, int ng) {
        }

        public default void mark(int cm, int nm) {
        }
    }

    private static final byte[] EXPORT_SIG = "Haven Mapfile 1".getBytes(Utils.ascii);

    public void export(Message out, ExportFilter filter, ExportStatus prog) throws InterruptedException {
        if (prog == null) prog = new ExportStatus() {
        };
        out.addbytes(EXPORT_SIG);
        ZMessage zout = new ZMessage(out);
        Collection<Long> segbuf = locked((Collection<Long> c) -> new ArrayList<>(c), lock.readLock()).apply(knownsegs);
        int nseg = 0;
        for (Long sid : segbuf) {
            if (!filter.includeseg(sid))
                continue;
            Segment seg;
            Collection<Pair<Coord, Long>> gridbuf = new ArrayList<>();
            lock.readLock().lock();
            try {
                seg = segments.get(sid);
                for (Map.Entry<Coord, Long> gd : seg.map.entrySet()) {
                    if (filter.includegrid(seg, gd.getKey(), gd.getValue()))
                        gridbuf.add(new Pair<>(gd.getKey(), gd.getValue()));
                }
            } finally {
                lock.readLock().unlock();
            }
            int ngrid = 0;
            for (Pair<Coord, Long> gd : gridbuf) {
                prog.grid(nseg, segbuf.size(), ngrid++, gridbuf.size());
                Grid grid = Grid.load(this, gd.b);
                MessageBuf buf = new MessageBuf();
                buf.adduint8(1);
                buf.addint64(gd.b);
                buf.addint64(seg.id);
                buf.addint64(grid.mtime);
                buf.addcoord(gd.a);
                buf.adduint8(grid.tilesets.length);
                for (TileInfo tinf : grid.tilesets) {
                    buf.addstring(tinf.res.name);
                    buf.adduint16(tinf.res.ver);
                    buf.adduint8(tinf.prio);
                }
                buf.addbytes(grid.tiles);
                byte[] od = buf.fin();
                zout.addstring("grid");
                zout.addint32(od.length);
                zout.addbytes(od);
                Utils.checkirq();
            }
            nseg++;
        }
        Collection<Marker> markbuf = locked((Collection<Marker> c) -> new ArrayList<>(c), lock.readLock()).apply(markers);
        int nmark = 0;
        for (Marker mark : markbuf) {
            prog.mark(nmark++, markbuf.size());
            if (!filter.includemark(mark))
                continue;
            MessageBuf buf = new MessageBuf();
            savemarker(buf, mark);
            byte[] od = buf.fin();
            zout.addstring("mark");
            zout.addint32(od.length);
            zout.addbytes(od);
            Utils.checkirq();
        }
        zout.finish();
    }

    public void export(OutputStream out, ExportFilter filter, ExportStatus prog) throws InterruptedException {
        StreamMessage msg = new StreamMessage(null, out);
        export(msg, filter, prog);
        msg.flush();
    }

    public static class ImportedGrid {
        public long gid, segid, mtime;
        public Coord sc;
        public TileInfo[] tilesets;
        public byte[] tiles;
        public int[] zmap;

        ImportedGrid(Message data) {
            int ver = data.uint8();
            if (ver != 1)
                throw (new Message.FormatError("Unknown grid data version: " + ver));
            gid = data.int64();
            segid = data.int64();
            mtime = data.int64();
            sc = data.coord();
            tilesets = new TileInfo[data.uint8()];
            for (int i = 0; i < tilesets.length; i++)
                tilesets[i] = new TileInfo(new Resource.Spec(Resource.remote(), data.string(), data.uint16()), data.uint8());
            tiles = data.bytes();
            zmap = new int[]{};
            if (tiles.length != (cmaps.x * cmaps.y))
                throw (new Message.FormatError("Bad grid data dimensions: " + tiles.length));
            for (byte td : tiles) {
                if ((td & 0xff) >= tiles.length)
                    throw (new Message.FormatError(String.format("Bad grid data contents: Tileset ID %d does not exist among 0-%d", (td & 0xff), tiles.length - 1)));
            }
        }

        Grid togrid() {
            return (new Grid(gid, tilesets, tiles, zmap, mtime));
        }
    }

    public static interface ImportFilter {
        public boolean includegrid(ImportedGrid grid, boolean hasprev);

        public boolean includemark(Marker mark, Marker prev);

        public default void handleerror(RuntimeException exc, String ctx) {
            throw (exc);
        }

        public static ImportFilter all = new ImportFilter() {
            public boolean includegrid(ImportedGrid grid, boolean hasprev) {
                return (true);
            }

            public boolean includemark(Marker mark, Marker prev) {
                return (prev == null);
            }
        };

        public static ImportFilter readonly = new ImportFilter() {
            public boolean includegrid(ImportedGrid grid, boolean hasprev) {
                return (false);
            }

            public boolean includemark(Marker mark, Marker prev) {
                return (false);
            }
        };
    }

    private class Importer {
        final Map<Long, ImportedSegment> segs = new HashMap<>();
        final ImportFilter filter;
        Segment curseg;

        class ImportedSegment {
            final Map<Long, Coord> offs = new HashMap<>();
            long nseg;
            Coord noff = null;
        }

        Importer(ImportFilter filter) {
            this.filter = filter;
        }

        void flush() {
            chseg(null);
        }

        Segment chseg(Segment nseg) {
            if ((curseg != null) && (curseg != nseg)) {
                locked(() -> segments.put(curseg.id, curseg), lock.writeLock()).run();
            }
            return (curseg = nseg);
        }

        Segment chseg(long id) {
            if ((curseg != null) && (curseg.id == id))
                return (curseg);
            Segment ret;
            lock.readLock().lock();
            try {
                ret = segments.get(id);
            } finally {
                lock.readLock().unlock();
            }
            return (chseg(ret));
        }

        void importgrid(Message data) {
            ImportedGrid grid = new ImportedGrid(data);
            ImportedSegment seg = segs.get(grid.segid);
            if (seg == null) {
                segs.put(grid.segid, seg = new ImportedSegment());
            }
            GridInfo info;
            lock.readLock().lock();
            try {
                info = gridinfo.get(grid.gid);
            } finally {
                lock.readLock().unlock();
            }
            if (info != null) {
                Coord off = seg.offs.get(info.seg);
                if (off == null) {
                    seg.offs.put(info.seg, info.sc.sub(grid.sc));
                } else {
                    if (!off.equals(info.sc.sub(grid.sc)))
                        throw (new RuntimeException("Inconsistent grid locations detected"));
                }
            }
            Segment rseg;
            if (filter.includegrid(grid, info != null)) {
                lock.writeLock().lock();
                try {
                    Grid rgrid = grid.togrid();
                    rgrid.save(MapFile.this);
                    if (seg.noff == null) {
                        if (info == null) {
                            rseg = chseg(new Segment(seg.nseg = grid.gid));
                            seg.noff = Coord.z;
                            seg.offs.put(seg.nseg, Coord.z);
                        } else {
                            rseg = chseg(seg.nseg = info.seg);
                            if (rseg == null)
                                throw (new NullPointerException());
                            seg.noff = seg.offs.get(info.seg);
                        }
                    } else {
                        if ((info == null) || (info.seg == seg.nseg)) {
                            rseg = chseg(seg.nseg);
                            if (rseg == null)
                                throw (new NullPointerException());
                        } else {
                            if (curseg.id != seg.nseg)
                                throw (new AssertionError());
                            Segment nseg = segments.get(info.seg);
                            Coord noff = seg.offs.get(info.seg);
                            Coord soff = seg.noff.sub(noff);
                            merge(nseg, curseg, soff);
                            seg.nseg = nseg.id;
                            seg.noff = noff;
                            rseg = curseg = nseg;
                        }
                    }
                    Coord nc = grid.sc.add(seg.noff);
                    if (info == null) {
                        rseg.include(rgrid, nc);
                        gridinfo.put(rgrid.id, new GridInfo(rgrid.id, rseg.id, nc));
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }
        }

        Marker prevmark(Marker mark) {
            for (Marker pm : MapFile.this.markers) {
                if ((pm.getClass() != mark.getClass()) || !pm.nm.equals(mark.nm) || !pm.tc.equals(mark.tc))
                    continue;
                if (pm instanceof SMarker) {
                    if (((SMarker) pm).oid != ((SMarker) mark).oid)
                        continue;
                }
                return (pm);
            }
            return (null);
        }

        void importmark(Message data) {
            Marker mark = loadmarker(data);
            ImportedSegment seg = segs.get(mark.seg);
            if ((seg == null) || (seg.noff == null))
                return;
            Coord soff = seg.offs.get(seg.nseg);
            if (soff == null)
                return;
            mark.tc = mark.tc.add(soff.mul(cmaps));
            mark.seg = seg.nseg;
            if (filter.includemark(mark, prevmark(mark))) {
                add(mark);
            }
        }

        void reimport(Message data) throws InterruptedException {
            if (!Arrays.equals(EXPORT_SIG, data.bytes(EXPORT_SIG.length)))
                throw (new Message.FormatError("Invalid map file format"));
            data = new ZMessage(data);
            try {
                while (!data.eom()) {
                    String type = data.string();
                    int len = data.int32();
                    Message lay = new LimitMessage(data, len);
                    if (type.equals("grid")) {
                        try {
                            importgrid(lay);
                        } catch (RuntimeException exc) {
                            filter.handleerror(exc, "grid");
                        }
                    } else if (type.equals("mark")) {
                        try {
                            importmark(lay);
                        } catch (RuntimeException exc) {
                            filter.handleerror(exc, "mark");
                        }
                    }
                    lay.skip();
                    Utils.checkirq();
                }
            } catch (InterruptedException e) {
                flush();
                throw (e);
            }
            flush();
        }
    }

    public void reimport(Message data, ImportFilter filter) throws InterruptedException {
        new Importer(filter).reimport(data);
    }

    public void reimport(InputStream fp, ImportFilter filter) throws InterruptedException {
        reimport(new StreamMessage(fp, null), filter);
    }

    private static final Coord[] inout = new Coord[]{
            new Coord(0, 0),
            new Coord(0, -1), new Coord(1, 0), new Coord(0, 1), new Coord(-1, 0),
            new Coord(1, -1), new Coord(1, 1), new Coord(-1, 1), new Coord(-1, -1),
    };

    public void update(MCache map, Coord cgc) {
        Collection<MCache.Grid> grids = new ArrayList<>();
        for (Coord off : inout) {
            Coord gc = cgc.add(off);
            try {
                grids.add(map.getgrid(gc));
            } catch (Loading l) {
                continue;
            }
        }
        if (!grids.isEmpty()) {
            synchronized (procmon) {
                updqueue.add(new Pair<>(map, grids));
                process();
            }
        }
    }
}

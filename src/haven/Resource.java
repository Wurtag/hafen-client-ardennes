/*
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

import modification.configuration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

public class Resource implements Serializable {
    public static Resource fake = new Resource(null, "fake", -1);
    private static ResCache prscache;
    public static ThreadGroup loadergroup = null;
    private static Map<String, LayerFactory<?>> ltypes = new TreeMap<String, LayerFactory<?>>();
    public static Class<Image> imgc = Image.class;
    public static Class<Neg> negc = Neg.class;
    public static Class<Anim> animc = Anim.class;
    public static Class<Pagina> pagina = Pagina.class;
    public static Class<AButton> action = AButton.class;
    public static Class<Audio> audio = Audio.class;
    public static Class<Tooltip> tooltip = Tooltip.class;

    public static final String language = Utils.getpref("language", "en");
    public static final String BUNDLE_TOOLTIP = "tooltip";
    public static final String BUNDLE_PAGINA = "pagina";
    public static final String BUNDLE_WINDOW = "window";
    public static final String BUNDLE_BUTTON = "button";
    public static final String BUNDLE_FLOWER = "flower";
    public static final String BUNDLE_MSG = "msg";
    public static final String BUNDLE_LABEL = "label";
    public static final String BUNDLE_ACTION = "action";
    public static final String BUNDLE_INGREDIENT = "ingredient";
    private final static Map<String, Map<String, String>> l10nBundleMap;
    public static final boolean L10N_DEBUG = System.getProperty("dumpstr") != null;


    private Collection<Layer> layers = new LinkedList<Layer>();
    public final String name;
    public int ver;
    public ResSource source;
    public final transient Pool pool;
    private boolean used = false;

    public abstract static class Named implements Indir<Resource> {
        public final String name;
        public final int ver;

        public Named(String name, int ver) {
            this.name = name;
            this.ver = ver;
        }

        public boolean equals(Object other) {
            if (!(other instanceof Named))
                return (false);
            Named o = (Named) other;
            return (o.name.equals(this.name) && (o.ver == this.ver));
        }

        public int hashCode() {
            int ret = name.hashCode();
            ret = (ret * 31) + ver;
            return (ret);
        }

        public String name() {
            return name;
        }
    }

    public String toString2() {
        return (String.format("#<res-name %s v%d>", name, ver));
    }


    public static class Spec extends Named implements Serializable {
        public final transient Pool pool;

        public Spec(Pool pool, String name, int ver) {
            super(name, ver);
            this.pool = pool;
        }

        public Spec(Pool pool, String name) {
            this(pool, name, -1);
        }

        public Resource get(int prio) {
            return (pool.load(name, ver, prio).get());
        }

        public Resource get() {
            return (get(0));
        }
    }

    public static interface Resolver {
        public Indir<Resource> getres(int id);

        public class ResourceMap implements Resource.Resolver {
            public final Resource.Resolver bk;
            public final Map<Integer, Integer> map;

            public ResourceMap(Resource.Resolver bk, Map<Integer, Integer> map) {
                this.bk = bk;
                this.map = map;
            }

            public ResourceMap(Resource.Resolver bk, Message data) {
                this(bk, decode(data));
            }

            public ResourceMap(Resource.Resolver bk, Object[] args) {
                this(bk, decode(args));
            }

            public static Map<Integer, Integer> decode(Message sdt) {
                if (sdt.eom())
                    return (Collections.emptyMap());
                int n = sdt.uint8();
                Map<Integer, Integer> ret = new HashMap<>();
                for (int i = 0; i < n; i++)
                    ret.put(sdt.uint16(), sdt.uint16());
                return (ret);
            }

            public static Map<Integer, Integer> decode(Object[] args) {
                if (args.length == 0)
                    return (Collections.emptyMap());
                Map<Integer, Integer> ret = new HashMap<>();
                for (int a = 0; a < args.length; a += 2)
                    ret.put((Integer) args[a], (Integer) args[a + 1]);
                return (ret);
            }

            public Indir<Resource> getres(int id) {
                return (bk.getres(map.get(id)));
            }

            public String toString() {
                return (map.toString());
            }
        }
    }

    private Resource(Pool pool, String name, int ver) {
        this.pool = pool;
        this.name = name;
        this.ver = ver;
    }

    public static void setcache(ResCache cache) {
        prscache = cache;
    }

    public String basename() {
        int p = name.lastIndexOf('/');
        if (p < 0)
            return (name);
        return (name.substring(p + 1));
    }

    public static interface ResSource {
        public InputStream get(String name) throws IOException;
    }

    public static abstract class TeeSource implements ResSource, Serializable {
        public ResSource back;

        public TeeSource(ResSource back) {
            this.back = back;
        }

        public InputStream get(String name) throws IOException {
            StreamTee tee = new StreamTee(back.get(name));
            tee.setncwe();
            tee.attach(fork(name));
            return (tee);
        }

        public abstract OutputStream fork(String name) throws IOException;

        public String toString() {
            return ("forking source backed by " + back);
        }
    }

    public static class CacheSource implements ResSource, Serializable {
        public transient ResCache cache;

        public CacheSource(ResCache cache) {
            this.cache = cache;
        }

        public InputStream get(String name) throws IOException {
            return (cache.fetch("res/" + name));
        }

        public String toString() {
            return ("cache source backed by " + cache);
        }
    }

    public static class FileSource implements ResSource, Serializable {
        File base;

        public FileSource(File base) {
            this.base = base;
        }

        public InputStream get(String name) throws FileNotFoundException {
            File cur = base;
            String[] parts = name.split("/");
            for (int i = 0; i < parts.length - 1; i++)
                cur = new File(cur, parts[i]);
            cur = new File(cur, parts[parts.length - 1] + ".res");
            return (new FileInputStream(cur));
        }

        public String toString() {
            return ("filesystem res source (" + base + ")");
        }
    }

    public static class JarSource implements ResSource, Serializable {
        public InputStream get(String name) throws FileNotFoundException {
            InputStream s = Resource.class.getResourceAsStream("/res/" + name + ".res");
            if (name.contains("cupboard") && !Config.flatcupboards)
                return null;
            if (s == null)
                throw (new FileNotFoundException("Could not find resource locally: " + name));
            return (s);
        }

        public String toString() {
            return ("local res source");
        }
    }

    public static class HttpSource implements ResSource, Serializable {
        private final transient SslHelper ssl;
        public URL baseurl;

        {
            ssl = new SslHelper();
            try {
                ssl.trust(ssl.loadX509(Resource.class.getResourceAsStream("ressrv.crt")));
            } catch (java.security.cert.CertificateException e) {
                throw (new Error("Invalid built-in certificate", e));
            } catch (IOException e) {
                throw (new Error(e));
            }
            ssl.ignoreName();
        }

        public HttpSource(URL baseurl) {
            System.out.println("baseurl " + baseurl);
            this.baseurl = baseurl;
        }

        private URL encodeurl(URL raw) throws IOException {
            /* This is "kinda" ugly. It is, actually, how the Java
             * documentation recommend that it be done, though... */
            try {
                return (new URL(new URI(raw.getProtocol(), raw.getHost(), raw.getPath(), raw.getRef()).toASCIIString()));
            } catch (URISyntaxException e) {
                throw (new IOException(e));
            }
        }

        public InputStream get(String name) throws IOException {
            URL resurl = encodeurl(new URL(baseurl, name + ".res"));
            URLConnection c;
            int tries = 0;
            while (true) {
                try {
                    if (resurl.getProtocol().equals("https"))
                        c = ssl.connect(resurl);
                    else
                        c = resurl.openConnection();
                    /* Apparently, some versions of Java Web Start has
                     * a bug in its internal cache where it refuses to
                     * reload a URL even when it has changed. */
                    c.setUseCaches(false);
                    c.addRequestProperty("User-Agent", "Haven/1.0");
                    return (c.getInputStream());
                } catch (ConnectException e) {
                    if (++tries >= 5)
                        throw (new IOException("Connection failed five times", e));
                }
            }
        }

        public String toString() {
            return ("HTTP res source (" + baseurl + ")");
        }
    }

    public static class Loading extends haven.Loading {
        private final Pool.Queued res;

        private Loading(Pool.Queued res) {
            super("Waiting for resource " + res.name + "...");
            this.res = res;
        }

        public String toString() {
            return ("#<Resource " + res.name + ">");
        }

        public boolean canwait() {
            return (true);
        }

        public void waitfor() throws InterruptedException {
            synchronized (res) {
                while (!res.done) {
                    res.wait();
                }
            }
        }

        public void boostprio(int prio) {
            res.boostprio(prio);
        }
    }

    public static class Pool {
        public int nloaders = 2;
        private final Collection<Loader> loaders = new LinkedList<Loader>();
        private final List<ResSource> sources = new LinkedList<ResSource>();
        private final Map<String, Resource> cache = new CacheMap<String, Resource>();
        private final PrioQueue<Queued> queue = new PrioQueue<Queued>();
        private final Map<String, Queued> queued = new HashMap<String, Queued>();
        private final Pool parent;

        public Pool(Pool parent, ResSource... sources) {
            this.parent = parent;
            for (ResSource source : sources)
                this.sources.add(source);
        }

        public Pool(ResSource... sources) {
            this(null, sources);
        }

        public void add(ResSource src) {
            sources.add(src);
        }

        private class Queued extends Named implements Prioritized, Serializable {
            volatile int prio;
            transient final Collection<Queued> rdep = new LinkedList<Queued>();
            Queued awaiting;
            volatile boolean done = false;
            Resource res;
            LoadException error;

            Queued(String name, int ver, int prio) {
                super(name, ver);
                this.prio = prio;
            }

            public int priority() {
                return (prio);
            }

            public void boostprio(int prio) {
                if (this.prio < prio)
                    this.prio = prio;
                Queued p = awaiting;
                if (p != null)
                    p.boostprio(prio);
            }

            public Resource get() {
                if (!done) {
                    boostprio(1);
                    throw (new Loading(this));
                }
                if (error != null) {
					//throw (new RuntimeException("Delayed error in resource " + name + " (v" + ver + "), from " + error.src, error));
                }
                return (res);
            }

            private void done() {
                synchronized (this) {
                    done = true;
                    for (Iterator<Queued> i = rdep.iterator(); i.hasNext(); ) {
                        Queued dq = i.next();
                        i.remove();
                        dq.prior(this);
                    }
                    this.notifyAll();
                }
                if (res != null) {
                    synchronized (cache) {
                        cache.put(name, res);
                    }
                    synchronized (queue) {
                        queued.remove(name);
                    }
                }
            }

            private void prior(Queued prior) {
                if ((res = prior.res) == null) {
                    error = prior.error;
                    synchronized (queue) {
                        queue.add(this);
                        queue.notify();
                    }
                    ckld();
                } else {
                    done();
                }
            }

            public String toString() {
                return (String.format("<q:%s(v%d)>", name, ver));
            }
        }

        private void handle(Queued res) {
            for (ResSource src : sources) {
                try {
                    InputStream in = src.get(res.name);
                    try {
                        Resource ret = new Resource(this, res.name, res.ver);
                        ret.source = src;
                        ret.load(in);
                        res.res = ret;
                        res.error = null;
                        break;
                    } finally {
                        in.close();
                    }
                } catch (Throwable t) {
                    LoadException error;
                    if (t instanceof LoadException)
                        error = (LoadException) t;
                    else
                        error = new LoadException(String.format("Load error in resource %s(v%d), from %s", res.name, res.ver, src), t, null);
                    error.src = src;
                    if (res.error != null) {
                        error.prev = res.error;
                        error.addSuppressed(res.error);
                    }
                    res.error = error;
                }
            }
            res.done();
        }

        public Named load(String name, int ver, int prio) {
            Queued ret;
            synchronized (cache) {
                Resource cur = cache.get(name);
                if (cur != null) {
                    if ((ver == -1) || (cur.ver == ver)) {
                        return (cur.indir());
                    } else if (ver < cur.ver) {
                        /* Throw LoadException rather than
                         * RuntimeException here, to make sure
                         * obsolete resources doing nested loading get
                         * properly handled. This could be the wrong
                         * way of going about it, however; I'm not
                         * sure. */
                        throw (new LoadException(String.format("Weird version number on %s (%d > %d), loaded from %s", cur.name, cur.ver, ver, cur.source), cur));
                    }
                }
                synchronized (queue) {
                    Queued cq = queued.get(name);
                    if (cq != null) {
                        if (ver != -1) {
                            if (ver < cq.ver) { //who cares, don't kill the client over this...
                                //throw(new LoadException(String.format("Weird version number on %s (%d > %d)", cq.name, cq.ver, ver), null));
                                cq.boostprio(prio);
                                return (cq);
                            } else if (ver == cq.ver) {
                                cq.boostprio(prio);
                                return (cq);
                            }
                        } else {
                            if (cq.done && (cq.error != null)) {
                                /* XXX: This is probably not the right way to handle this. */
                            } else {
                                cq.boostprio(prio);
                                return (cq);
                            }
                        }
                        queued.remove(name);
                        queue.removeid(cq);
                    }
                    Queued nq = new Queued(name, ver, prio);
                    queued.put(name, nq);
                    if (parent == null) {
                        queue.add(nq);
                        queue.notify();
                    } else {
                        Indir<Resource> pr = parent.load(name, ver, prio);
                        if (pr instanceof Queued) {
                            Queued pq = (Queued) pr;
                            synchronized (pq) {
                                if (pq.done) {
                                    nq.prior(pq);
                                } else {
                                    nq.awaiting = pq;
                                    pq.rdep.add(nq);
                                }
                            }
                        } else {
                            queued.remove(name);
                            nq.res = pr.get();
                            nq.done = true;
                        }
                    }
                    ret = nq;
                }
            }
            ckld();
            return (ret);
        }

        public Named load(String name, int ver) {
            return (load(name, ver, -5));
        }

        public Named load(String name) {
            return (load(name, -1));
        }

        public Indir<Resource> dynres(long id) {
            return (load(String.format("dyn/%x", id), 1));
        }

        private void ckld() {
            int qsz;
            synchronized (queue) {
                qsz = queue.size();
            }
            synchronized (loaders) {
                while (loaders.size() < Math.min(nloaders, qsz)) {
                    final Loader n = new Loader();
                    Thread th = AccessController.doPrivileged(new PrivilegedAction<Thread>() {
                        public Thread run() {
                            return (new HackThread(loadergroup, n, "Haven resource loader"));
                        }
                    });
                    th.setDaemon(true);
                    th.start();
                    while (!n.added) {
                        try {
                            loaders.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        }

        public class Loader implements Runnable {
            private boolean added = false;

            public void run() {
                synchronized (loaders) {
                    loaders.add(this);
                    added = true;
                    loaders.notifyAll();
                }
                boolean intd = false;
                try {
                    while (true) {
                        Queued cur;
                        synchronized (queue) {
                            long start = System.currentTimeMillis(), now = start;
                            while ((cur = queue.poll()) == null) {
                                queue.wait(10000 - (now - start));
                                now = System.currentTimeMillis();
                                if (now - start >= 10000)
                                    return;
                            }
                        }
                        handle(cur);
                        cur = null;
                    }
                } catch (InterruptedException e) {
                    intd = true;
                } finally {
                    synchronized (loaders) {
                        loaders.remove(this);
                    }
                    if (!intd)
                        ckld();
                }
            }
        }

        public int qdepth() {
            int ret = (parent == null) ? 0 : parent.qdepth();
            synchronized (queue) {
                ret += queue.size();
            }
            return (ret);
        }

        public int numloaded() {
            int ret = (parent == null) ? 0 : parent.numloaded();
            synchronized (cache) {
                ret += cache.size();
            }
            return (ret);
        }

        public Collection<Resource> cached() {
            Set<Resource> ret = new HashSet<Resource>();
            if (parent != null)
                ret.addAll(parent.cached());
            synchronized (cache) {
                ret.addAll(cache.values());
            }
            return (ret);
        }

        public Collection<Resource> used() {
            Collection<Resource> ret = cached();
            for (Iterator<Resource> i = ret.iterator(); i.hasNext(); ) {
                Resource r = i.next();
                if (!r.used)
                    i.remove();
            }
            return (ret);
        }

        private final Set<Resource> loadwaited = new HashSet<Resource>();

        public Collection<Resource> loadwaited() {
            Set<Resource> ret = new HashSet<Resource>();
            if (parent != null)
                ret.addAll(parent.loadwaited());
            synchronized (loadwaited) {
                ret.addAll(loadwaited);
            }
            return (ret);
        }

        private Resource loadwaited(Resource res) {
            synchronized (loadwaited) {
                loadwaited.add(res);
            }
            return (res);
        }

        public Resource loadwaitint(String name, int ver) throws InterruptedException {
            return (loadwaited(Loading.waitforint(load(name, ver, 10))));
        }

        public Resource loadwaitint(String name) throws InterruptedException {
            return (loadwaitint(name, -1));
        }

        public Resource loadwait(String name, int ver) {
            return (loadwaited(Loading.waitfor(load(name, ver, 10))));
        }

        public Resource loadwait(String name) {
            return (loadwait(name, -1));
        }
    }

    private static Pool _local = null;

    public static Pool local() {
        if (_local == null) {
            synchronized (Resource.class) {
                if (_local == null) {
                    Pool local = new Pool(new JarSource());
                    try {
                        if (Config.resdir != null)
                            local.add(new FileSource(new File(Config.resdir)));
                    } catch (Exception e) {
                        /* Ignore these. We don't want to be crashing the client
                         * for users just because of errors in development
                         * aids. */
                    }
                    _local = local;
                }
            }
        }
        return (_local);
    }

    private static Pool _remote = null;

    public static Pool remote() {
        if (_remote == null) {
            synchronized (Resource.class) {
                if (_remote == null) {
                    Pool remote = new Pool(local());
                    if (prscache != null)
                        remote.add(new CacheSource(prscache));
                    _remote = remote;
                    ;
                }
            }
        }
        return (_remote);
    }

    public static void addurl(URL url) {
        ResSource src = new HttpSource(url);
        if (prscache != null) {
            class Caching extends TeeSource {
                private final transient ResCache cache;

                Caching(ResSource bk, ResCache cache) {
                    super(bk);
                    this.cache = cache;
                }

                public OutputStream fork(String name) throws IOException {
                    return (cache.store("res/" + name));
                }
            }
            src = new Caching(src, prscache);
        }
        remote().add(src);
    }

    @Deprecated
    public static Resource load(String name, int ver) {
        return (remote().loadwait(name, ver));
    }

    @Deprecated
    public Resource loadwait() {
        return (this);
    }

    public static class LoadException extends RuntimeException {
        public Resource res;
        public ResSource src;
        public LoadException prev;

        public LoadException(String msg, Resource res) {
            super(msg);
            this.res = res;
        }

        public LoadException(String msg, Throwable cause, Resource res) {
            super(msg, cause);
            this.res = res;
        }

        public LoadException(Throwable cause, Resource res) {
            super("Load error in resource " + res.toString() + ", from " + res.source, cause);
            this.res = res;
        }
    }

    public static Coord cdec(Message buf) {
        return (new Coord(buf.int16(), buf.int16()));
    }

    public abstract class Layer implements Serializable {
        public abstract void init();

        public Resource getres() {
            return (Resource.this);
        }
    }

    public interface LayerFactory<T extends Layer> {
        public T cons(Resource res, Message buf);
    }

    public static class LayerConstructor<T extends Layer> implements LayerFactory<T> {
        public final Class<T> cl;
        private final Constructor<T> cons;

        public LayerConstructor(Class<T> cl) {
            this.cl = cl;
            try {
                this.cons = cl.getConstructor(Resource.class, Message.class);
            } catch (NoSuchMethodException e) {
                throw (new RuntimeException("No proper constructor found for layer type " + cl.getName(), e));
            }
        }

        public T cons(Resource res, Message buf) {
            try {
                return (cons.newInstance(res, buf));
            } catch (InstantiationException e) {
                throw (new LoadException(e, res));
            } catch (IllegalAccessException e) {
                throw (new LoadException(e, res));
            } catch (InvocationTargetException e) {
                Throwable c = e.getCause();
                if (c instanceof RuntimeException)
                    throw ((RuntimeException) c);
                else
                    throw (new LoadException(e, res));
            }
        }
    }

    public static void addltype(String name, LayerFactory<?> cons) {
        ltypes.put(name, cons);
    }

    public static <T extends Layer> void addltype(String name, Class<T> cl) {
        addltype(name, new LayerConstructor<T>(cl));
    }

    @dolda.jglob.Discoverable
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface LayerName {
        public String value();
    }

    static {
        l10nBundleMap = new HashMap<String, Map<String, String>>(9) {{
            if (!language.equals("en") || Resource.L10N_DEBUG) {
                put(BUNDLE_TOOLTIP, l10n(BUNDLE_TOOLTIP, language));
                put(BUNDLE_PAGINA, l10n(BUNDLE_PAGINA, language));
                put(BUNDLE_WINDOW, l10n(BUNDLE_WINDOW, language));
                put(BUNDLE_BUTTON, l10n(BUNDLE_BUTTON, language));
                put(BUNDLE_FLOWER, l10n(BUNDLE_FLOWER, language));
                put(BUNDLE_MSG, l10n(BUNDLE_MSG, language));
                put(BUNDLE_LABEL, l10n(BUNDLE_LABEL, language));
                put(BUNDLE_ACTION, l10n(BUNDLE_ACTION, language));
                put(BUNDLE_INGREDIENT, l10n(BUNDLE_INGREDIENT, language));
            }
        }};

        for (Class<?> cl : dolda.jglob.Loader.get(LayerName.class).classes()) {
            String nm = cl.getAnnotation(LayerName.class).value();
            if (LayerFactory.class.isAssignableFrom(cl)) {
                try {
                    addltype(nm, cl.asSubclass(LayerFactory.class).newInstance());
                } catch (InstantiationException e) {
                    throw (new Error(e));
                } catch (IllegalAccessException e) {
                    throw (new Error(e));
                }
            } else if (Layer.class.isAssignableFrom(cl)) {
                addltype(nm, cl.asSubclass(Layer.class));
            } else {
                throw (new Error("Illegal resource layer class: " + cl));
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, String> l10n(String bundle, String langcode) {
        Properties props = new Properties();

        InputStream is = Config.class.getClassLoader().getResourceAsStream("l10n/" + bundle + "_" + langcode + ".properties");
        if (is == null)
            return null;

        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader(is, "UTF-8");
            props.load(isr);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (isr != null) {
                try {
                    isr.close();
                } catch (IOException e) { // ignored
                }
            }
        }

        return props.size() > 0 ? new HashMap<>((Map) props) : null;
    }

    public interface IDLayer<T> {
        public T layerid();
    }

    @LayerName("image")
    public class Image extends Layer implements Comparable<Image>, IDLayer<Integer> {
        public transient BufferedImage img;
        transient private TexI tex;
        public final int z, subz;
        public final boolean nooff;
        public final int id;
        private int gay = -1;
        public Coord sz;
        public Coord o;

        public Image(Message buf) {
            z = buf.int16();
            subz = buf.int16();
            int fl = buf.uint8();
            /* Obsolete flag 1: Layered */
            nooff = (fl & 2) != 0;
            id = buf.int16();
            o = cdec(buf);
            try {
                img = ImageIO.read(new MessageInputStream(buf));
            } catch (IOException e) {
                throw (new LoadException(e, Resource.this));
            }
            if (img == null)
                throw (new LoadException("Invalid image data in " + name, Resource.this));
            sz = Utils.imgsz(img);
        }

        public synchronized Tex tex() {
            if (tex != null)
                return (tex);
            tex = new TexI(img) {
                public String toString() {
                    return ("TexI(" + Resource.this.name + ", " + id + ")");
                }
            };
            return (tex);
        }

        public synchronized TexI texi() {
            if (tex != null)
                return (tex);
            tex = new TexI(img) {
                public String toString() {
                    return ("TexI(" + Resource.this.name + ", " + id + ")");
                }
            };
            return (tex);
        }

        private boolean detectgay() {
            for (int y = 0; y < sz.y; y++) {
                for (int x = 0; x < sz.x; x++) {
                    if ((img.getRGB(x, y) & 0x00ffffff) == 0x00ff0080)
                        return (true);
                }
            }
            return (false);
        }

        public boolean gayp() {
            if (gay == -1)
                gay = detectgay() ? 1 : 0;
            return (gay == 1);
        }

        public int compareTo(Image other) {
            return (z - other.z);
        }

        public Integer layerid() {
            return (id);
        }

        public void init() {
        }
    }

    @LayerName("tooltip")
    public class Tooltip extends Layer {
        public String t = "null";

        public Tooltip(Message buf) {
            String text = new String(buf.bytes(), Utils.utf8);
            Resource res = super.getres();
            try {
                String locText = getLocString(BUNDLE_TOOLTIP, res, text);

                if (!language.equals("en")) {
                    if (locText.equals(text) || !res.name.startsWith("gfx/invobjs") ||
                            // exclude meat "conditions" since the tooltip is dynamically generated and it won't be in right order
                            text.contains("Raw ") || text.contains("Filet of ") || text.contains("Sizzling") ||
                            text.contains("Roast") || text.contains("Meat") || text.contains("Spitroast") ||
                            // exclude food conditions
                            res.name.startsWith("gfx/invobjs/food/")) {
                        this.t = locText;
                    } else {
                        this.t = locText + " (" + text + ")";
                    }
                    return;
                }
                this.t = locText;
            } catch (Exception e) {
                this.t = text;
                System.out.println("Tooltip res " + e);
            }
        }

        public void init() {
        }
    }

    @LayerName("neg")
    public class Neg extends Layer {
        public Coord cc;
        public Coord bc, bs, sz;
        public Coord[][] ep;

        public Neg(Message buf) {
            cc = cdec(buf);
            bc = cdec(buf);
            bs = cdec(buf);
            sz = cdec(buf);
            //buf.skip(12);
            ep = new Coord[8][0];
            int en = buf.uint8();
            for (int i = 0; i < en; i++) {
                int epid = buf.uint8();
                int cn = buf.uint16();
                ep[epid] = new Coord[cn];
                for (int o = 0; o < cn; o++)
                    ep[epid][o] = cdec(buf);
            }
        }

        public void init() {
        }
    }

    @LayerName("anim")
    public class Anim extends Layer {
        private int[] ids;
        public int id, d;
        public Image[][] f;

        public Anim(Message buf) {
            id = buf.int16();
            d = buf.uint16();
            ids = new int[buf.uint16()];
            for (int i = 0; i < ids.length; i++)
                ids[i] = buf.int16();
            configuration.resourceLog("Anim " + id + " " + d + " " + ids);
        }

        public void init() {
            f = new Image[ids.length][];
            Image[] typeinfo = new Image[0];
            for (int i = 0; i < ids.length; i++) {
                LinkedList<Image> buf = new LinkedList<Image>();
                for (Image img : layers(Image.class)) {
                    if (img.id == ids[i])
                        buf.add(img);
                }
                f[i] = buf.toArray(typeinfo);
            }
            configuration.resourceLog("Anim init " + f);
        }
    }

    @LayerName("pagina")
    public class Pagina extends Layer {
        public final String text;

        public Pagina(Message buf) {
            String text = new String(buf.bytes(), Utils.utf8);
            this.text = Resource.getLocString(Resource.BUNDLE_PAGINA, super.getres(), text);
            configuration.resourceLog("Pagina " + text);
        }

        public void init() {
        }
    }

    @LayerName("action")
    public class AButton extends Layer {
        public final String name;
        public final Named parent;
        public final char hk;
        public final String[] ad;

        public AButton(Named parent, String name) {
            this.name = name;
            this.parent = parent;
            ad = null;
            hk = '\0';
        }

        public AButton(Named parent, String name, char h) {
            this.name = name;
            this.parent = parent;
            ad = null;
            hk = h;
        }

        public AButton(Message buf) {
            String pr = buf.string();
            int pver = buf.uint16();
            if (pr.length() == 0) {
                parent = null;
            } else {
                try {
                    parent = pool.load(pr, pver);
                } catch (RuntimeException e) {
                    throw (new LoadException("Illegal resource dependency", e, Resource.this));
                }
            }

            name = buf.string();

            buf.string(); /* Prerequisite skill */
            hk = (char) buf.uint16();
            ad = new String[buf.uint16()];
            for (int i = 0; i < ad.length; i++)
                ad[i] = buf.string();
        }

        public void init() {
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface PublishedCode {
        String name();

        Class<? extends Instancer> instancer() default Instancer.class;

        public interface Instancer {
            public Object make(Class<?> cl) throws InstantiationException, IllegalAccessException;
        }
    }

    @LayerName("code")
    public class Code extends Layer {
        public final String name;
        transient public final byte[] data;

        public Code(Message buf) {
            name = buf.string();
            data = buf.bytes();
            configuration.resourceLog("Code " + name + " " + data);
            if (configuration.decodeCode)
                try {
                    decode();
                } catch (Exception ex) {
                    System.out.println("Error Code: " + ex.getMessage());
                }
            //Ardenneses pro fucking code do not touch ever
        }

        public void init() {
        }

        public void decode() throws Exception {
            String resname = name;
            if (name.equals("Fac")) {
                resname = java.util.UUID.randomUUID().toString();
            }
            File f = new File("code/" + resname + "/" + resname + ".data");
            new File("code/" + resname + "/").mkdirs();
            f.createNewFile();
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8"));
            bw.write("#CODE LAYER FOR RES " + resname + "\r\n");
            bw.write("#String class_name" + "\r\n");
            bw.write("#Note: the .class file will have the same namea s this file" + "\r\n");
            bw.write(resname.replace("\n", "\\n") + "\r\n");
            bw.flush();
            bw.close();
            f = new File("code/" + resname + "/" + resname + ".class");
            FileOutputStream fout = new FileOutputStream(f);
            fout.write(data);
            fout.flush();
            fout.close();
        }
    }

    public class ResClassLoader extends ClassLoader {
        public ResClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Resource getres() {
            return (Resource.this);
        }

        public String toString() {
            return ("cl:" + Resource.this.toString());
        }
    }

    public static Resource classres(final Class<?> cl) {
        return (AccessController.doPrivileged(new PrivilegedAction<Resource>() {
            public Resource run() {
                ClassLoader l = cl.getClassLoader();
                if (l instanceof ResClassLoader)
                    return (((ResClassLoader) l).getres());
                throw (new RuntimeException("Cannot fetch resource of non-resloaded class " + cl));
            }
        }));
    }

    public <T> T getcode(Class<T> cl, boolean fail) {
        CodeEntry e = layer(CodeEntry.class);
        if (e == null) {
            if (fail)
                throw (new RuntimeException("Tried to fetch non-present res-loaded class " + cl.getName() + " from " + Resource.this.name));
            return (null);
        }
        return (e.get(cl, fail));
    }

    public static class LibClassLoader extends ClassLoader {
        private final ClassLoader[] classpath;

        public LibClassLoader(ClassLoader parent, Collection<ClassLoader> classpath) {
            super(parent);
            this.classpath = classpath.toArray(new ClassLoader[0]);
        }

        public Class<?> findClass(String name) throws ClassNotFoundException {
            for (ClassLoader lib : classpath) {
                try {
                    return (lib.loadClass(name));
                } catch (ClassNotFoundException e) {
                }
            }
            throw (new ClassNotFoundException("Could not find " + name + " in any of " + Arrays.asList(classpath).toString()));
        }
    }

    @LayerName("codeentry")
    public class CodeEntry extends Layer {
        private String clnm;
        private Map<String, Code> clmap = new TreeMap<String, Code>();
        private Map<String, String> pe = new TreeMap<String, String>();
        private Collection<Indir<Resource>> classpath = new LinkedList<Indir<Resource>>();
        transient private ClassLoader loader;
        transient private Map<String, Class<?>> lpe = null;
        transient private Map<Class<?>, Object> ipe = new HashMap<Class<?>, Object>();

        public CodeEntry(Message buf) {
            while (!buf.eom()) {
                int t = buf.uint8();
                if (t == 1) {
                    while (true) {
                        String en = buf.string();
                        String cn = buf.string();
                        if (en.length() == 0)
                            break;
                        pe.put(en, cn);
                    }
                } else if (t == 2) {
                    while (true) {
                        String ln = buf.string();
                        if (ln.length() == 0)
                            break;
                        int ver = buf.uint16();
                        classpath.add(pool.load(ln, ver));
                    }
                } else {
                    throw (new LoadException("Unknown codeentry data type: " + t, Resource.this));
                }
            }
        }

        public void init() {
            for (Code c : layers(Code.class))
                clmap.put(c.name, c);
        }

        public ClassLoader loader(final boolean wait) {
            synchronized (CodeEntry.this) {
                if (this.loader == null) {
                    this.loader = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                        public ClassLoader run() {
                            ClassLoader ret = Resource.class.getClassLoader();
                            if (classpath.size() > 0) {
                                Collection<ClassLoader> loaders = new LinkedList<ClassLoader>();
                                for (Indir<Resource> res : classpath) {
                                    loaders.add((wait ? Loading.waitfor(res) : res.get()).layer(CodeEntry.class).loader(wait));
                                }
                                ret = new LibClassLoader(ret, loaders);
                            }
                            if (clmap.size() > 0) {
                                ret = new ResClassLoader(ret) {
                                    public Class<?> findClass(String name) throws ClassNotFoundException {
                                        Code c = clmap.get(name);
                                        if (c == null)
                                            throw (new ClassNotFoundException("Could not find class " + name + " in resource (" + Resource.this + ")"));
                                        return (defineClass(name, c.data, 0, c.data.length));
                                    }
                                };
                            }
                            return (ret);
                        }
                    });
                }
            }
            return (this.loader);
        }

        private void load() {
            synchronized (CodeEntry.class) {
                if (lpe != null)
                    return;
                ClassLoader loader = loader(false);
                lpe = new TreeMap<String, Class<?>>();
                try {
                    for (Map.Entry<String, String> e : pe.entrySet()) {
                        String name = e.getKey();
                        String clnm = e.getValue();
                        Class<?> cl = loader.loadClass(clnm);
                        lpe.put(name, cl);
                    }
                } catch (ClassNotFoundException e) {
                    throw (new LoadException(e, Resource.this));
                }
            }
        }

        public <T> Class<? extends T> getcl(Class<T> cl, boolean fail) {
            load();
            PublishedCode entry = cl.getAnnotation(PublishedCode.class);
            if (entry == null)
                throw (new RuntimeException("Tried to fetch non-published res-loaded class " + cl.getName() + " from " + Resource.this.name));
            Class<?> acl;
            synchronized (lpe) {
                if ((acl = lpe.get(entry.name())) == null) {
                    if (fail)
                        throw (new RuntimeException("Tried to fetch non-present res-loaded class " + cl.getName() + " from " + Resource.this.name));
                    return (null);
                }
            }
            return (acl.asSubclass(cl));
        }

        public <T> Class<? extends T> getcl(Class<T> cl) {
            return (getcl(cl, true));
        }

        public <T> T get(Class<T> cl, boolean fail) {
            load();
            PublishedCode entry = cl.getAnnotation(PublishedCode.class);
            if (entry == null)
                throw (new RuntimeException("Tried to fetch non-published res-loaded class " + cl.getName() + " from " + Resource.this.name));
            Class<?> acl;
            synchronized (lpe) {
                if ((acl = lpe.get(entry.name())) == null) {
                    if (fail)
                        throw (new RuntimeException("Tried to fetch non-present res-loaded class " + cl.getName() + " from " + Resource.this.name));
                    return (null);
                }
            }
            synchronized (ipe) {
                Object pinst;
                if ((pinst = ipe.get(acl)) != null) {
                    return (cl.cast(pinst));
                } else {
                    T inst;
                    Object rinst = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                        try {
                            if (entry.instancer() != PublishedCode.Instancer.class)
                                return (entry.instancer().newInstance().make(acl));
                            else
                                return (acl.newInstance());
                        } catch (IllegalAccessException e) {
                            throw (new RuntimeException(e));
                        } catch (InstantiationException e) {
                            throw (new RuntimeException(e));
                        }
                    });
                    try {
                        inst = cl.cast(rinst);
                    } catch (ClassCastException e) {
                        throw (new ClassCastException("Published class in " + Resource.this.name + " is not of type " + cl));
                    }
                    ipe.put(acl, inst);
                    return (inst);
                }
            }
        }

        public <T> T get(Class<T> cl) {
            return (get(cl, true));
        }

        public Class get(String tag) {
            load();
            Class<?> acl;
            synchronized (lpe) {
                acl = lpe.get(tag);
            }
            return (acl);
        }
    }

    @LayerName("audio")
    public class Audio extends Layer implements IDLayer<String> {
        transient public byte[] coded;
        public final String id;
        public double bvol = 1.0;

        public Audio(byte[] coded, String id) {
            this.coded = coded;
            this.id = id.intern();
        }

        public Audio(Message buf) {
            this(buf.bytes(), "cl");
        }

        public void init() {
        }

        public haven.Audio.CS stream() {
            try {
                return (new haven.Audio.VorbisClip(new dolda.xiphutil.VorbisStream(new ByteArrayInputStream(coded))));
            } catch (IOException e) {
                throw (new RuntimeException(e));
            }
        }

        public String layerid() {
            return (id);
        }
    }

    @LayerName("audio2")
    public static class Audio2 implements LayerFactory<Audio> {
        public Audio cons(Resource res, Message buf) {
            int ver = buf.uint8();
            if ((ver == 1) || (ver == 2)) {
                String id = buf.string();
                double bvol = 1.0;
                if (ver == 2)
                    bvol = buf.uint16() / 1000.0;
                Audio ret = res.new Audio(buf.bytes(), id);
                ret.bvol = bvol;
                return (ret);
            } else {
                throw (new LoadException("Unknown audio layer version: " + ver, res));
            }
        }
    }

    @LayerName("midi")
    public class Music extends Resource.Layer {
        transient javax.sound.midi.Sequence seq;

        public Music(Message buf) {
            try {
                seq = javax.sound.midi.MidiSystem.getSequence(new MessageInputStream(buf));
            } catch (javax.sound.midi.InvalidMidiDataException e) {
                throw (new LoadException("Invalid MIDI data", Resource.this));
            } catch (IOException e) {
                throw (new LoadException(e, Resource.this));
            }
        }

        public void init() {
        }
    }

    @LayerName("font")
    public class Font extends Layer {
        public transient final java.awt.Font font;

        public Font(Message buf) {
            int ver = buf.uint8();
            if (ver == 1) {
                int type = buf.uint8();
                if (type == 0) {
                    try {
                        this.font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, new MessageInputStream(buf));
                    } catch (Exception e) {
                        throw (new RuntimeException(e));
                    }
                } else {
                    throw (new LoadException("Unknown font type: " + type, Resource.this));
                }
            } else {
                throw (new LoadException("Unknown font layer version: " + ver, Resource.this));
            }
        }

        public void init() {
        }
    }

    private void readall(InputStream in, byte[] buf) throws IOException {
        int ret, off = 0;
        while (off < buf.length) {
            ret = in.read(buf, off, buf.length - off);
            if (ret < 0)
                throw (new LoadException("Incomplete resource at " + name, this));
            off += ret;
        }
    }

    public <L extends Layer> Collection<L> layers(final Class<L> cl) {
        used = true;
        return (new DefaultCollection<L>() {
            public Iterator<L> iterator() {
                return (Utils.filter(layers.iterator(), cl));
            }
        });
    }

    public <L extends Layer> L layer(Class<L> cl) {
        used = true;
        for (Layer l : layers) {
            if (cl.isInstance(l))
                return (cl.cast(l));
        }
        return (null);
    }

    public <I, L extends IDLayer<I>> L layer(Class<L> cl, I id) {
        used = true;
        for (Layer l : layers) {
            if (cl.isInstance(l)) {
                L ll = cl.cast(l);
                if (ll.layerid().equals(id))
                    return (ll);
            }
        }
        return (null);
    }

    public boolean equals(Object other) {
        if (!(other instanceof Resource))
            return (false);
        Resource o = (Resource) other;
        return (o.name.equals(this.name) && (o.ver == this.ver));
    }

    private void load(InputStream st) throws IOException {
        Message in = new StreamMessage(st);
        byte[] sig = "Haven Resource 1".getBytes(Utils.ascii);
        if (!Arrays.equals(sig, in.bytes(sig.length)))
            throw (new LoadException("Invalid res signature", this));
        int ver = in.uint16();
        List<Layer> layers = new LinkedList<Layer>();
        if (this.ver == -1)
            this.ver = ver;
        else if (ver != this.ver) {
            throw (new LoadException("Wrong res version (" + ver + " != " + this.ver + ")", this));
        }
        while (!in.eom()) {
            LayerFactory<?> lc = ltypes.get(in.string());
            int len = in.int32();
            if (lc == null) {
                in.skip(len);
                continue;
            }
            Message buf = new LimitMessage(in, len);
            layers.add(lc.cons(this, buf));
            buf.skip();
        }
        this.layers = layers;
        for (Layer l : layers)
            l.init();
        used = false;
    }

    private transient Named indir = null;

    public Named indir() {
        if (indir != null)
            return (indir);
        class Ret extends Named implements Serializable {
            Ret(String name, int ver) {
                super(name, ver);
            }

            public Resource get() {
                return (Resource.this);
            }

            public String toString() {
                return String.format("<indir:%s(v%d)>", name, ver);
            }
        }
        indir = new Ret(name, ver);
        return (indir);
    }

    public static BufferedImage loadimg(String name) {
        return (local().loadwait(name).layer(imgc).img);
    }

    public static BufferedImage loadimg(final String name, final int id) {
        final Resource res = local().loadwait(name);
        final Collection<Image> imgs = res.layers(imgc);
        for (Image img : imgs) {
            if (img.id == id) {
                return img.img;
            }
        }
        throw new RuntimeException("Failed to find img for " + name + " - id: " + id);
    }

    public static Tex loadtex(final String name, final int id) {
        final Resource res = local().loadwait(name);
        final Collection<Image> imgs = res.layers(imgc);
        for (Image img : imgs) {
            if (img.id == id) {
                return img.tex();
            }
        }
        throw new RuntimeException("Failed to find tex for " + name + " - id: " + id);
    }

    public static Tex loadtex(String name) {
        return (local().loadwait(name).layer(imgc).tex());
    }

    public String toString() {
        return (name + "(v" + ver + ")");
    }

    public static void loadlist(Pool pool, InputStream list, int prio) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(list, "us-ascii"));
        String ln;
        while ((ln = in.readLine()) != null) {
            int pos = ln.indexOf(':');
            if (pos < 0)
                continue;
            String nm = ln.substring(0, pos);
            int ver;
            try {
                ver = Integer.parseInt(ln.substring(pos + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            try {
                pool.load(nm, ver, prio);
            } catch (RuntimeException e) {
            }
        }
        in.close();
    }

    public static void dumplist(Collection<Resource> list, Writer dest) {
        PrintWriter out = new PrintWriter(dest);
        List<Resource> sorted = new ArrayList<Resource>(list);
        Collections.sort(sorted, new Comparator<Resource>() {
            public int compare(Resource a, Resource b) {
                return (a.name.compareTo(b.name));
            }
        });
        for (Resource res : sorted)
            out.println(res.name + ":" + res.ver);
    }

    public static void updateloadlist(File file, File resdir) throws Exception {
        BufferedReader r = new BufferedReader(new FileReader(file));
        Map<String, Integer> orig = new HashMap<String, Integer>();
        String ln;
        while ((ln = r.readLine()) != null) {
            int pos = ln.indexOf(':');
            if (pos < 0) {
                System.err.println("Weird line: " + ln);
                continue;
            }
            String nm = ln.substring(0, pos);
            int ver = Integer.parseInt(ln.substring(pos + 1));
            orig.put(nm, ver);
        }
        r.close();
        Pool pool = new Pool(new FileSource(resdir));
        for (String nm : orig.keySet())
            pool.load(nm);
        while (true) {
            int d = pool.qdepth();
            if (d == 0)
                break;
            System.out.print("\033[1GLoading... " + d + "\033[K");
            Thread.sleep(500);
        }
        System.out.println();
        Collection<Resource> cur = new LinkedList<Resource>();
        for (Map.Entry<String, Integer> e : orig.entrySet()) {
            String nm = e.getKey();
            int ver = e.getValue();
            Resource res = Loading.waitfor(pool.load(nm));
            if (res.ver != ver)
                System.out.println(nm + ": " + ver + " -> " + res.ver);
            cur.add(res);
        }
        Writer w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            dumplist(cur, w);
        } finally {
            w.close();
        }
    }

    private static final String[] fmtLocStringsLabel = new String[]{
            "Health: %s",
            "Stamina: %s",
            "Energy: %s",
            "Pony Power: %s",
            "Hunger modifier: %s",
            "Food event bonus: %s",
            "Tell %s of your exploits",
            "Go laugh at %s",
            "Go rage at %s",
            "Go wave to %s",
            "Greet %s",
            "Visit %s",
            "Meeting %s",
            "%s's Biddings",
            "%s's Labors",
            "%s's Laundry List",
            "Believing in %s",
            "%s's Wild Hunt",
            "%s's Quest",
            "By %s's Command",
            "%s's Quarry",
            "Hunting for %s",
            "Grass, Stone, and %s",
            "%s's Tasks",
            "%s's Business",
            "%s Giving the Chase",
            "A Favor for %s",
            "%s's Laughter",
            "Blood for %s",
            "%s's Follies",
            "Under %s's Star",
            "%s's Story",
            "Errands for %s",
            "Affair with %s",
            "%s's Catch",
            "As %s Wishes",
            "Poaching for %s",
            "Crazy Old %s",
            "Flowers for %s",
            "Silly %s",
            "%s's Gathering",
            "In Name of %s",
            "%s's Dirty Laundry",
            "What %s Asked",
            "Tasked by %s",
            "What %s Asked",
            "One for %s",
            "Fair Game for %s",
            "Meditations on %s",
            "%s's Wild Harvest",
            "%s has invited you to join his party. Do you wish to do so?",
            "%s has requested to spar with you. Do you accept?",
            "Experience points gained: %s",
            "Here lies %s",
            "Create a level %d artifact"
    };

    private static final String[] fmtLocStringsFlower = new String[]{
            "Gild (%s%% chance)",
            //"Follow %s",
            //"Travel along %s",
            //"Connect %s",
            //	"Extend %s"
    };

    private static final String[] fmtLocStringsMsg = new String[]{
            "That land is owned by %s.",
            "The name of this charterstone is \"%s\".",
            "Will refill in %s days",
            "Will refill in %s hours",
            "Will refill in %s minutes",
            "Will refill in %s seconds",
            "Will refill in %s second",
            "Quality: %s",
            "%s%% grown",
            "The battering ram cannot be used until the glue has dried, in %s hours."
    };

    public static String getLocString(String bundle, String key) {
        Map<String, String> map = l10nBundleMap.get(bundle);
        if (map == null || key == null)
            return key;
        if (Resource.L10N_DEBUG)
            Resource.saveStrings(bundle, key, key);
        String ll = map.get(key);
        // strings which need to be formatted
        if (ll == null && bundle == BUNDLE_LABEL) {
            for (String s : fmtLocStringsLabel) {
                String llfmt = fmtLocString(map, key, s);
                if (llfmt != null)
                    return llfmt;
            }
        } else if (ll == null && bundle == BUNDLE_FLOWER) {
            for (String s : fmtLocStringsFlower) {
                String llfmt = fmtLocString(map, key, s);
                if (llfmt != null)
                    return llfmt;
            }
        } else if (ll == null && bundle == BUNDLE_MSG) {
            for (String s : fmtLocStringsMsg) {
                String llfmt = fmtLocString(map, key, s);
                if (llfmt != null)
                    return llfmt;
            }
        }
        return ll != null ? ll : key;
    }

    private static String fmtLocString(Map<String, String> map, String key, String s) {
        String ll = map.get(s);
        if (ll != null) {
            int vi = s.indexOf("%s");

            String sufix = s.substring(vi + 2);
            if (sufix.startsWith("%%"))                // fix for strings with escaped percentage sign
                sufix = sufix.substring(1);

            if (key.startsWith(s.substring(0, vi)) && key.endsWith(sufix))
                return String.format(ll, key.substring(vi, key.length() - sufix.length()));
        }
        return null;
    }

    public static String getLocString(String bundle, Resource key, String def) {
        Map<String, String> map = l10nBundleMap.get(bundle);
        if (map == null || key == null)
            return def;
        if (Resource.L10N_DEBUG)
            Resource.saveStrings(bundle, key.name, def);
        String ll = map.get(key.name);
        return ll != null ? ll : def;
    }

    public static String getLocContent(String str) {
        String loc;
        if ((loc = Resource.locContent(str, " l of ")) != null)
            return loc;
        if ((loc = Resource.locContent(str, " kg of ")) != null)
            return loc;
        if ((loc = Resource.locContent(str, " seeds of ")) != null)
            return loc;
        return str;
    }

    private static String locContent(String str, String type) {
        int i = str.indexOf(type);
        if (i > 0) {
            Map<String, String> map = l10nBundleMap.get(Resource.BUNDLE_LABEL);
            if (map == null)
                return str;
            String contName = str.substring(i);
            if (Resource.L10N_DEBUG)
                Resource.saveStrings(Resource.BUNDLE_LABEL, contName, contName);
            String locContName = map.get(contName);
            if (locContName != null)
                return str.substring(0, i) + locContName + " (" + str.substring(i + type.length()) + ")";
            return str;
        }
        return null;
    }

    private static void saveStrings(String bundle, String key, String val) {
        synchronized (Resource.class) {
            if (bundle.equals(BUNDLE_FLOWER)) {
                if (key.startsWith("Follow ") || key.startsWith("Travel along") ||
                        key.startsWith("Extend ") && key.startsWith("Connect "))
                    return;
            }

            Map<String, String> map = l10nBundleMap.get(bundle);

            if (bundle.equals(BUNDLE_TOOLTIP) &&
                    (key.startsWith("paginae/act") || key.startsWith("paginae/bld")
                            || key.startsWith("paginae/craft") || key.startsWith("paginae/gov")
                            || key.startsWith("paginae/pose") || key.startsWith("paginae/amber")
                            || key.startsWith("paginae/atk/ashoot") || key.startsWith("paginae/seid")))
                return;

            if (key == null || key.equals("") || val.equals(""))
                return;

            if (val.charAt(0) >= '0' && val.charAt(0) <= '9')
                return;

            if (key.startsWith("Village shield:") ||
                    key.endsWith("is ONLINE") || key.endsWith("is offline") ||
                    key.startsWith("Born to ") ||
                    key.equals("ui/r-enact"))
                return;

            if (bundle == BUNDLE_LABEL) {
                for (String s : fmtLocStringsLabel) {
                    if (fmtLocString(map, key, s) != null)
                        return;
                }
            } else if (bundle == BUNDLE_FLOWER) {
                for (String s : fmtLocStringsFlower) {
                    if (fmtLocString(map, key, s) != null)
                        return;
                }
            } else if (bundle == BUNDLE_MSG) {
                for (String s : fmtLocStringsMsg) {
                    if (fmtLocString(map, key, s) != null)
                        return;
                }
            }

            val = sanitizeVal(val);

            String valOld = map.get(key);
            if (valOld != null && sanitizeVal(valOld).equals(val))
                return;

            new File("l10n").mkdirs();

            CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
            encoder.onMalformedInput(CodingErrorAction.REPORT);
            encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            BufferedWriter out = null;
            try {
                map.put(key, val);
                key = key.replace(" ", "\\ ").replace(":", "\\:").replace("=", "\\=");
                if (key.startsWith("\\ "))
                    key = "\\u0020" + key.substring(2);
                if (key.endsWith("\\ "))
                    key = key.substring(0, key.length() - 2) + "\\u0020";
                out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("l10n/" + bundle + "_new.properties", true), encoder));
                out.write(key + " = " + val);
                out.newLine();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            return;
        }
    }

    private static String sanitizeVal(String val) {
        val = val.replace("\\", "\\\\").replace("\n", "\\n").replace("\u0000", "");
        if (val.startsWith(" "))
            val = "\\u0020" + val.substring(1);
        if (val.endsWith(" "))
            val = val.substring(0, val.length() - 1) + "\\u0020";

        while (val.endsWith("\\n"))
            val = val.substring(0, val.length() - 2);

        return val;
    }

    public static void main(String[] args) throws Exception {
        String cmd = args[0].intern();
        if (cmd == "update") {
            updateloadlist(new File(args[1]), new File(args[2]));
        }
    }
}

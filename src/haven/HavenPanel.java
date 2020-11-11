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

import com.jogamp.opengl.util.awt.Screenshot;
import haven.sloth.util.ObservableCollection;
import integrations.mapv4.MappingClient;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLContext;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

public class HavenPanel extends GLCanvas implements Runnable, Console.Directory, UI.Context {
    //All of our UIs
    public final ObservableCollection<UI> sessions = new ObservableCollection<>(new ArrayList<>());
    //The UI for the next frame, or null of no change
    private UI nextUI;
    //The current active UI
    public UI ui;

    boolean inited = false;
    public static int w, h;
    public boolean bgmode = false;
    long fps = 0;
    public static long bgfd = 1000 / Config.fpsBackgroundLimit, fd = 1000 / Config.fpsLimit;
    boolean iswap = true, aswap;
    long last_sess_upd = System.currentTimeMillis();
    long ssent = 0, srecv = 0, sretran = 0;
    long sent = 0, recv = 0, retran = 0;
    double uidle = 0.0, ridle = 0.0;
    Queue<InputEvent> events = new LinkedList<InputEvent>();
    private String cursmode = "tex";
    private Resource lastcursor = null;
    public Coord mousepos = new Coord(0, 0);
    private MouseEvent mousemv = null;
    public CPUProfile uprof = new CPUProfile(300), rprof = new CPUProfile(300);
    public GPUProfile gprof = new GPUProfile(300);
    public static final GLState.Slot<GLState> global = new GLState.Slot<GLState>(GLState.Slot.Type.SYS, GLState.class);
    public static final GLState.Slot<GLState> proj2d = new GLState.Slot<GLState>(GLState.Slot.Type.SYS, GLState.class, global);
    private GLState gstate, rtstate, ostate;
    private Throwable uncaught = null;
    private GLState.Applier state = null;
    private GLConfig glconf = null;
    public static boolean needtotakescreenshot;
    public static boolean isATI;
    private final boolean gldebug = false;
    private static final Cursor emptycurs = Toolkit.getDefaultToolkit().createCustomCursor(TexI.mkbuf(new Coord(1, 1)), new java.awt.Point(), "");

    private static GLCapabilities stdcaps() {
        GLProfile prof = GLProfile.getDefault();
        GLCapabilities cap = new GLCapabilities(prof);
        cap.setDoubleBuffered(true);
        cap.setAlphaBits(8);
        cap.setRedBits(8);
        cap.setGreenBits(8);
        cap.setBlueBits(8);
        cap.setStencilBits(8);
        cap.setSampleBuffers(true);
        cap.setNumSamples(4);
        return (cap);
    }

    public HavenPanel(int w, int h) {
        super(stdcaps());
        if (gldebug)
            setContextCreationFlags(getContextCreationFlags() | GLContext.CTX_OPTION_DEBUG);
        setSize(this.w = w, this.h = h);
        MappingClient.getInstance();
        initgl();
        if (Toolkit.getDefaultToolkit().getMaximumCursorColors() >= 256 || Config.hwcursor)
            cursmode = "awt";
    }

    private void initgl() {
        final Thread caller = Thread.currentThread();
        final haven.error.ErrorHandler h = haven.error.ErrorHandler.find();
        addGLEventListener(new GLEventListener() {
            Debug.DumpGL dump = null;

            public void takescreenshot(int width, int height) {
                try {
                    String curtimestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss.SSS").format(new Date());
                    File outputfile = new File(String.format("screenshots/%s.png", curtimestamp));
                    outputfile.getParentFile().mkdirs();
                    Screenshot.writeToFile(outputfile, width, height);
                    ui.root.findchild(GameUI.class).msg(String.format("Screenshot has been saved as \"%s\"", outputfile.getName()), Color.WHITE);
                } catch (Exception ex) {
                    System.out.println("Unable to take screenshot: " + ex.getMessage());
                }
            }

            public void display(GLAutoDrawable d) {
                if (HavenPanel.needtotakescreenshot) {
                    takescreenshot(d.getWidth(), d.getHeight());
                    HavenPanel.needtotakescreenshot = false;
                }

                GL2 gl = d.getGL().getGL2();
            /*
            if((dump == null) || (dump.getDownstreamGL() != gl))
			dump = new Debug.DumpGL((GL4bc)gl);
		    if(Debug.kf2 && !Debug.pk2)
			dump.dump("/tmp/gldump");
		    dump.reset();
		    gl = dump;
		    */
                if (inited)
                    redraw(gl);
            }

            public void init(GLAutoDrawable d) {
                try {
                    GL gl = d.getGL();
                    if (h != null) {
                        String vendor = gl.glGetString(gl.GL_VENDOR);
                        isATI = vendor.contains("AMD") || vendor.contains("ATI");
                        h.lsetprop("gpu", vendor + " (" + gl.glGetString(gl.GL_RENDERER) + ") - " + gl.glGetString(gl.GL_VERSION));
                        // h.lsetprop("gl.vendor", vendor);
                        // h.lsetprop("gl.version", gl.glGetString(gl.GL_VERSION));
                        // h.lsetprop("gl.renderer", gl.glGetString(gl.GL_RENDERER));
                        // h.lsetprop("gl.exts", Arrays.asList(gl.glGetString(gl.GL_EXTENSIONS).split(" ")));
                        // h.lsetprop("gl.caps", d.getChosenGLCapabilities().toString());
                        // h.lsetprop("gl.conf", glconf);
                    }
                    glconf = GLConfig.fromgl(gl, d.getContext(), getChosenGLCapabilities());
                    if (gldebug) {
                        if (!d.getContext().isGLDebugMessageEnabled())
                            System.err.println("GL debugging not actually enabled");
                        ((GL2) gl).glDebugMessageControl(GL.GL_DONT_CARE, GL.GL_DONT_CARE, GL.GL_DONT_CARE, 0, null, true);
                    }
                    glconf.pref = GLSettings.load(glconf, true);
                    if (ui != null) {
                        ui.cons.add(glconf);
                    }
                    gstate = new GLState() {
                        public void apply(GOut g) {
                            BGL gl = g.gl;
                            gl.glColor3f(1, 1, 1);
                            gl.glPointSize(4);
                            gl.joglSetSwapInterval((aswap = iswap) ? 1 : 0);
                            gl.glEnable(GL.GL_BLEND);
                            //gl.glEnable(GL.GL_LINE_SMOOTH);
                            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                            if (g.gc.glmajver >= 2)
                                gl.glBlendEquationSeparate(GL.GL_FUNC_ADD, GL2.GL_MAX);
                            if (g.gc.havefsaa()) {
                                /* Apparently, having sample
                                 * buffers in the config enables
                                 * multisampling by default on
                                 * some systems. */
                                g.gl.glDisable(GL.GL_MULTISAMPLE);
                            }
                            GOut.checkerr(gl);
                        }

                        public void unapply(GOut g) {
                        }

                        public void prep(Buffer buf) {
                            buf.put(global, this);
                        }
                    };
                } catch (RuntimeException e) {
                    uncaught = e;
                    throw (e);
                }
            }

            public void reshape(GLAutoDrawable d, final int x, final int y, final int w, final int h) {
                ostate = OrthoState.fixed(new Coord(w, h));
                rtstate = new GLState() {
                    public void apply(GOut g) {
                        g.st.proj = Projection.makeortho(new Matrix4f(), 0, w, 0, h, -1, 1);
                    }

                    public void unapply(GOut g) {
                    }

                    public void prep(Buffer buf) {
                        buf.put(proj2d, this);
                    }
                };
                HavenPanel.this.w = w;
                HavenPanel.this.h = h;
            }

            public void displayChanged(GLAutoDrawable d, boolean cp1, boolean cp2) {
            }

            public void dispose(GLAutoDrawable d) {
            }
        });
    }

    public static abstract class OrthoState extends GLState {
        protected abstract Coord sz();

        public void apply(GOut g) {
            Coord sz = sz();
            g.st.proj = Projection.makeortho(new Matrix4f(), 0, sz.x, sz.y, 0, -1, 1);
        }

        public void unapply(GOut g) {
        }

        public void prep(Buffer buf) {
            buf.put(proj2d, this);
        }

        public static OrthoState fixed(final Coord sz) {
            return (new OrthoState() {
                protected Coord sz() {
                    return (sz);
                }
            });
        }
    }

    public void init() {
        setFocusTraversalKeysEnabled(false);
        addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                synchronized (events) {
                    events.add(e);
                    events.notifyAll();
                }
            }

            public void keyPressed(KeyEvent e) {
                synchronized (events) {
                    events.add(e);
                    events.notifyAll();
                }
            }

            public void keyReleased(KeyEvent e) {
                synchronized (events) {
                    events.add(e);
                    events.notifyAll();
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                synchronized (events) {
                    events.add(e);
                    events.notifyAll();
                }
            }

            public void mouseReleased(MouseEvent e) {
                synchronized (events) {
                    events.add(e);
                    events.notifyAll();
                }
            }
        });
        addMouseMotionListener(new MouseMotionListener() {
            public void mouseDragged(MouseEvent e) {
                synchronized (events) {
                    mousemv = e;
                }
            }

            public void mouseMoved(MouseEvent e) {
                synchronized (events) {
                    mousemv = e;
                }
            }
        });
        addMouseWheelListener(new MouseWheelListener() {
            public void mouseWheelMoved(MouseWheelEvent e) {
                synchronized (events) {
                    events.add(e);
                    events.notifyAll();
                }
            }
        });
        inited = true;
    }

    public UI newui(Session sess) {
        final UI lui = new UI(this, new Coord(w, h), sess);
        lui.root.guprof = uprof;
        lui.root.grprof = rprof;
        lui.root.ggprof = gprof;
        if (getParent() instanceof Console.Directory)
            lui.cons.add((Console.Directory) getParent());
        lui.cons.add(this);
        if (glconf != null)
            lui.cons.add(glconf);
        synchronized (sessions) {
            sessions.add(lui);
        }
        if (this.ui == null) {
            //set default UI if first one
            this.ui = lui;
        }
        return (lui);
    }

    public boolean isActiveUI(final UI lui) {
        return this.ui == lui;
    }

    //Set the UI for the next frame
    public void setActiveUI(final UI lui) {
        if (this.ui != lui)
            nextUI = lui;
    }

    public int sessionCount() {
        synchronized (sessions) {
            return sessions.size();
        }
    }

    public boolean isMasterUIActive() {
        synchronized (sessions) {
            Iterator<UI> itr = sessions.iterator();
            if (itr.hasNext())
                return itr.next() == this.ui;
            else
                return false;
        }
    }

    public void closeCurrentSession() {
        if (this.ui.gui != null) {
            this.ui.gui.act("lo");
        } else {
            if (this.ui.sess != null) {
                this.ui.sess.close();
            } //TODO: should be a way to close a session that's not logged in
        }
    }

    public void closeSession(UI ui) {
        if (ui.gui != null) {
            ui.gui.act("lo");
        } else {
            if (ui.sess != null) {
                ui.sess.close();
            } //TODO: should be a way to close a session that's not logged in
        }
    }

    //Remove a UI
    public void removeUI(final UI lui) {
        lui.destroy();
        synchronized (sessions) {
            sessions.remove(lui);
        }

        //Update the next active UI if we have any others currently.
        if (this.ui == lui) {
            synchronized (sessions) {
                Iterator<UI> itr = sessions.iterator();
                if (itr.hasNext())
                    setActiveUI(itr.next());
            }
        }

        synchronized (sessions) {
            if (sessions.size() == 0) {
                MainFrame.instance.makeNewSession();
                setActiveUI(ui);
            }
        }
    }

    private static Cursor makeawtcurs(BufferedImage img, Coord hs) {
        java.awt.Dimension cd = Toolkit.getDefaultToolkit().getBestCursorSize(img.getWidth(), img.getHeight());
        BufferedImage buf = TexI.mkbuf(new Coord((int) cd.getWidth(), (int) cd.getHeight()));
        java.awt.Graphics g = buf.getGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return (Toolkit.getDefaultToolkit().createCustomCursor(buf, new java.awt.Point(hs.x, hs.y), ""));
    }

    void rootdraw(GLState.Applier state, UI ui, BGL gl) {
        GLState.Buffer ibuf = new GLState.Buffer(state.cfg);
        gstate.prep(ibuf);
        ostate.prep(ibuf);
        GOut g = new GOut(gl, state.cgl, state.cfg, state, ibuf, new Coord(w, h));
        state.set(ibuf);

        g.state(ostate);
        g.apply();
        gl.glClearColor(0, 0, 0, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        synchronized (ui) {
            ui.draw(g);
        }


        if (Config.dbtext) {
            int y = h - 190;
            FastText.aprintf(g, new Coord(10, y -= 15), 0, 1, "FPS: %d (%d%%, %d%% idle)", fps, (int) (uidle * 100.0), (int) (ridle * 100.0));
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory(), total = rt.totalMemory();
            FastText.aprintf(g, new Coord(10, y -= 15), 0, 1, "Mem: %,011d/%,011d/%,011d/%,011d", free, total - free, total, rt.maxMemory());
            FastText.aprintf(g, new Coord(10, y -= 15), 0, 1, "Tex-current: %d", TexGL.num());
            FastText.aprintf(g, new Coord(10, y -= 15), 0, 1, "GL progs: %d", g.st.numprogs());
            FastText.aprintf(g, new Coord(10, y -= 15), 0, 1, "Stats slots: %d", GLState.Slot.num());
            GameUI gi = ui.root.findchild(GameUI.class);
            if ((gi != null) && (gi.map != null)) {
                try {
                    FastText.aprintf(g, new Coord(10, y -= 15), 0, 1, "Mapview: %s", gi.map);
                } catch (Loading e) {
                }
                if (gi.map.rls != null)
                    FastText.aprintf(g, new Coord(10, y -= 15), 0, 1, "Rendered: %,d+%,d(%,d), cached %,d/%,d+%,d(%,d)", gi.map.rls.drawn, gi.map.rls.instanced, gi.map.rls.instancified, gi.map.rls.cacheroots, gi.map.rls.cached, gi.map.rls.cacheinst, gi.map.rls.cacheinstn);
            }
            if (Resource.remote().qdepth() > 0)
                FastText.aprintf(g, new Coord(10, y -= 15), 0, 1, "RQ depth: %d (%d)", Resource.remote().qdepth(), Resource.remote().numloaded());
        }

        //Update session stats
        if (ui.sess != null) {
            if ((System.currentTimeMillis() - last_sess_upd) >= 1000) {
                sent = ui.sess.sent - ssent;
                recv = ui.sess.recv - srecv;
                retran = ui.sess.retran - sretran;
                ssent = ui.sess.sent;
                srecv = ui.sess.recv;
                sretran = ui.sess.retran;
                last_sess_upd = System.currentTimeMillis();
            }
            if (Config.showfps) {
                FastText.aprintf(g, new Coord(w, 0), 1, 0, "FPS: %d (%d%%, %d%% idle)", fps, (int) (uidle * 100.0), (int) (ridle * 100.0));
                FastText.aprintf(g, new Coord(w, 15), 1, 0, "S: %d | R: %d | P: %d | RT: %d", sent, recv, ui.sess.pend, retran);
                if (ui.gui != null && ui.gui.map != null) {
                    FastText.aprintf(g, new Coord(w, 30), 1, 0, "%.2f units/s", ui.gui.map.speed());
                }
            }
        }

        Object tooltip;
        try {
            synchronized (ui) {
                tooltip = ui.root.tooltip(mousepos, ui.root);
            }
        } catch (Loading e) {
            tooltip = "...";
        }
        Tex tt = null;
        if (tooltip != null && !TexGL.disableall) {
            if (tooltip instanceof Text) {
                tt = ((Text) tooltip).tex();
            } else if (tooltip instanceof Tex) {
                tt = (Tex) tooltip;
            } else if (tooltip instanceof Indir<?>) {
                Indir<?> t = (Indir<?>) tooltip;
                Object o = t.get();
                if (o instanceof Tex)
                    tt = (Tex) o;
            } else if (tooltip instanceof String) {
                if (((String) tooltip).length() > 0)
                    tt = (Text.render((String) tooltip)).tex();
            } else if (tooltip instanceof BufferedImage) {
                tt = new TexI((BufferedImage) tooltip);
            }
        }
        if (tt != null) {
            Coord sz = tt.sz();
            Coord pos = mousepos.add(sz.inv());
            if (pos.x < 0)
                pos.x = 0;
            if (pos.y < 0)
                pos.y = 0;
            g.chcolor(244, 247, 21, 192);
            g.rect(pos.add(-3, -3), sz.add(6, 6));
            g.chcolor(35, 35, 35, 192);
            g.frect(pos.add(-2, -2), sz.add(4, 4));
            g.chcolor();
            g.image(tt, pos);
        }
        ui.lasttip = tooltip;
        Resource curs;
        synchronized (ui) {
            curs = ui.getcurs(mousepos);
        }
        if (cursmode == "awt") {
            if (curs != lastcursor) {
                try {
                    if (curs == null)
                        setCursor(null);
                    else
                        setCursor(makeawtcurs(curs.layer(Resource.imgc).img, curs.layer(Resource.negc).cc));
                } catch (Exception e) {
                    cursmode = "tex";
                }
            }
        } else if (cursmode == "tex") {
            if (curs == null) {
                if (lastcursor != null)
                    setCursor(null);
            } else {
                if (lastcursor == null)
                    setCursor(emptycurs);
                Coord dc = mousepos.add(curs.layer(Resource.negc).cc.inv());
                g.image(curs.layer(Resource.imgc), dc);
            }
        }
        lastcursor = curs;
        state.clean();
        GLObject.disposeall(state.cgl, gl);
        if (gldebug)
            gl.bglGetDebugMessageLog(msg -> System.err.println(msg));
    }

    private static class Frame {
        BufferBGL buf;
        CurrentGL on;
        CPUProfile.Frame pf;
        long doneat;

        Frame(BufferBGL buf, CurrentGL on) {
            this.buf = buf;
            this.on = on;
        }
    }

    private Frame[] curdraw = {null};

    void redraw(GL2 gl) {
        if (uncaught != null)
            throw (new RuntimeException("Exception occurred during init but was somehow discarded", uncaught));
        if ((state == null) || (state.cgl.gl != gl))
            state = new GLState.Applier(new CurrentGL(gl, glconf));

        Frame f;
        synchronized (curdraw) {
            f = curdraw[0];
            curdraw[0] = null;
        }
        if ((f != null) && (f.on.gl == gl)) {
            GPUProfile.Frame curgf = null;
            if (Config.profilegpu)
                curgf = gprof.new Frame((GL3) gl);
            if (f.pf != null)
                f.pf.tick("awt");
            f.buf.run(gl);
            GOut.checkerr(gl);
            if (f.pf != null)
                f.pf.tick("gl");
            if (curgf != null) {
                curgf.tick("draw");
                curgf.fin();
            }

            if (glconf.pref.dirty) {
                glconf.pref.save();
                glconf.pref.dirty = false;
            }
            DefSettings.checkForDirty();
            f.doneat = System.currentTimeMillis();
        }

        if (iswap != aswap)
            gl.setSwapInterval((aswap = iswap) ? 1 : 0);
    }

    void dispatch() {
        synchronized (events) {
            if (mousemv != null) {
                mousepos = new Coord(mousemv.getX(), mousemv.getY());
                ui.mousemove(mousemv, mousepos);
                mousemv = null;
            }
            InputEvent e = null;
            while ((e = events.poll()) != null) {
                if (e instanceof MouseEvent) {
                    MouseEvent me = (MouseEvent) e;
                    if (me.getID() == MouseEvent.MOUSE_PRESSED) {
                        ui.mousedown(me, new Coord(me.getX(), me.getY()), me.getButton());
                    } else if (me.getID() == MouseEvent.MOUSE_RELEASED) {
                        ui.mouseup(me, new Coord(me.getX(), me.getY()), me.getButton());
                    } else if (me instanceof MouseWheelEvent) {
                        ui.mousewheel(me, new Coord(me.getX(), me.getY()), ((MouseWheelEvent) me).getWheelRotation());
                    }
                } else if (e instanceof KeyEvent) {
                    KeyEvent ke = (KeyEvent) e;
                    if (ke.getID() == KeyEvent.KEY_PRESSED) {
                        ui.keydown(ke);
                    } else if (ke.getID() == KeyEvent.KEY_RELEASED) {
                        ui.keyup(ke);
                    } else if (ke.getID() == KeyEvent.KEY_TYPED) {
                        ui.type(ke);
                    }
                }
                ui.lastevent = Utils.rtime();
            }
        }
    }

    private Frame bufdraw = null;
    private final Runnable drawfun = new Runnable() {
        private void uglyjoglhack() throws InterruptedException {
            try {
                display();
            } catch (RuntimeException e) {
                InterruptedException ie = Utils.hascause(e, InterruptedException.class);
                if (ie != null)
                    throw (ie);
                else
                    throw (e);
            }
        }

        public void run() {
            try {
                uglyjoglhack();
                if (state == null)
                    throw (new RuntimeException("State applier is still null after redraw"));
                synchronized (drawfun) {
                    drawfun.notifyAll();
                }
                while (true) {
                    long then = System.currentTimeMillis();
                    int waited = 0;
                    Frame current;
                    synchronized (drawfun) {
                        while ((current = bufdraw) == null)
                            drawfun.wait();
                        bufdraw = null;
                        drawfun.notifyAll();
                        waited += System.currentTimeMillis() - then;
                    }
                    CPUProfile.Frame curf = null;
                    if (Config.profile)
                        current.pf = curf = rprof.new Frame();
                    synchronized (curdraw) {
                        curdraw[0] = current;
                    }
                    uglyjoglhack();
                    if (curf != null) {
                        curf.tick("aux");
                        curf.fin();
                    }
                    long now = System.currentTimeMillis();
                    waited += now - current.doneat;
                    ridle = (ridle * 0.95) + (((double) waited / ((double) (now - then))) * 0.05);
                    current = null; /* Just for the GC. */
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    };

    public void run() {
        try {
            Thread drawthread = new HackThread(drawfun, "Render thread");
            drawthread.start();
            synchronized (drawfun) {
                while (state == null)
                    drawfun.wait();
            }
            try {
                long now, then;
                long frames[] = new long[128];
                int framep = 0, waited[] = new int[128];
                while (true) {
                    if (nextUI != null)
                        ui = nextUI;
                    if (ui != null) {
                        if (!DefSettings.PAUSED.get()) {
                            int fwaited = 0;
                            Debug.cycle();
                            UI ui = this.ui;
                            then = System.currentTimeMillis();
                            CPUProfile.Frame curf = null;
                            if (Config.profile)
                                curf = uprof.new Frame();
                            synchronized (ui) {
                                if (ui.sess != null)
                                    ui.sess.glob.ctick();
                                dispatch();
                                ui.tick();
                                if ((ui.root.sz.x != w) || (ui.root.sz.y != h))
                                    ui.root.resize(new Coord(w, h));
                            }
                            if (curf != null)
                                curf.tick("dsp");

                            BufferBGL buf = new BufferBGL();
                            GLState.Applier state = this.state;
                            rootdraw(state, ui, buf);
                            if (curf != null)
                                curf.tick("draw");
                            synchronized (drawfun) {
                                now = System.currentTimeMillis();
                                while (bufdraw != null)
                                    drawfun.wait();
                                bufdraw = new Frame(buf, state.cgl);
                                drawfun.notifyAll();
                                fwaited += System.currentTimeMillis() - now;
                            }

                            ui.audio.cycle();
                            if (curf != null)
                                curf.tick("aux");

                            now = System.currentTimeMillis();
                            long fd = bgmode ? this.bgfd : this.fd;
                            if (now - then < fd) {
                                synchronized (events) {
                                    events.wait(fd - (now - then));
                                }
                                fwaited += System.currentTimeMillis() - now;
                            }

                            frames[framep] = now;
                            waited[framep] = fwaited;
                            {
                                int i = 0, ckf = framep, twait = 0;
                                for (; i < frames.length - 1; i++) {
                                    ckf = (ckf - 1 + frames.length) % frames.length;
                                    twait += waited[ckf];
                                    if (now - frames[ckf] > 1000)
                                        break;
                                }
                                fps = (i * 1000) / (now - frames[ckf]);
                                uidle = ((double) twait) / ((double) (now - frames[ckf]));
                            }
                            framep = (framep + 1) % frames.length;

                            if (curf != null)
                                curf.tick("wait");
                            if (curf != null)
                                curf.fin();
                            if (Thread.interrupted())
                                throw (new InterruptedException());
                        } else {
                            //Things that must run each frame, even when paused
                            Debug.cycle();
                            synchronized (ui) {
                                if (ui.sess != null)
                                    ui.sess.glob.ctick();
                                dispatch();
                                ui.tick();
                                if ((ui.root.sz.x != w) || (ui.root.sz.y != h))
                                    ui.root.resize(new Coord(w, h));
                            }
                            ui.audio.cycle();
                            //This is for scripts doing queued movements
                            //TODO: Fix this once scripting is added back in
                            // if(haven.Context.map != null)
                            //    haven.Context.map.try_move();
                            Thread.sleep(100);
                        }
                    }

                    //Update all other UIs as well, just don't render
                    synchronized (sessions) {
                        for (final UI lui : sessions) {
                            if (lui != ui) {
                                if (lui.sess != null)
                                    lui.sess.glob.ctick();
                                lui.tick();
                                if ((lui.root.sz.x != w) || (lui.root.sz.y != h))
                                    lui.root.resize(new Coord(w, h));
                                lui.audio.cycle();
                            }
                        }
                    }
                }
            } finally {
                drawthread.interrupt();
                drawthread.join();
            }
        } catch (InterruptedException e) {
        } finally {
            if (ui != null)
                ui.destroy();
        }
    }

    public GraphicsConfiguration getconf() {
        return (getGraphicsConfiguration());
    }

    private java.awt.Robot awtrobot;

    public void setmousepos(Coord c) {
        java.awt.EventQueue.invokeLater(() -> {
            if (awtrobot == null) {
                try {
                    awtrobot = new Robot(getGraphicsConfiguration().getDevice());
                } catch (java.awt.AWTException e) {
                    return;
                }
            }
            Point rp = getLocationOnScreen();
            awtrobot.mouseMove(rp.x + c.x, rp.y + c.y);
        });
    }

    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();

    {
        cmdmap.put("hz", new Console.Command() {
            public void run(Console cons, String[] args) {
                fd = 1000 / Integer.parseInt(args[1]);
                Utils.setprefi("hz", (int) fd);
            }
        });
        cmdmap.put("bghz", new Console.Command() {
            public void run(Console cons, String[] args) {
                bgfd = 1000 / Integer.parseInt(args[1]);
                Utils.setprefi("bghz", (int) bgfd);
            }
        });
        cmdmap.put("vsync", (cons, args) -> {
            iswap = Utils.parsebool(args[1]);
        });
    }

    public Map<String, Console.Command> findcmds() {
        return (cmdmap);
    }
}
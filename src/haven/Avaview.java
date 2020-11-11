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

import haven.Composited.Desc;

import java.awt.Color;
import java.util.List;
import java.util.Random;

public class Avaview extends PView {
    private static final String plkey = "PlayerAvaview";
    public static final Tex missing = Resource.loadtex("gfx/hud/equip/missing");
    public static final Coord dasz = missing.sz();
    public Color color = Color.WHITE;
    public long avagob;
    public Desc avadesc;
    public Resource.Resolver resmap = null;
    private Composited comp;
    private List<Composited.MD> cmod = null;
    private List<Composited.ED> cequ = null;
    private UI.Grab dm;
    private Coord doff;
    private final String camnm;
    private final boolean player, party, fight;


    @RName("av")
    public static class $_ implements Factory {
        public Widget create(UI ui, Object[] args) {
            long avagob = -1;
            Coord sz = dasz;
            String camnm = "avacam";
            if (args[0] != null)
                avagob = Utils.uint32((Integer) args[0]);
            if ((args.length > 1) && (args[1] != null))
                sz = (Coord) args[1];
            if ((args.length > 2) && (args[2] != null))
                camnm = (String) args[2];
            return (new Avaview(sz, avagob, camnm));
        }
    }

    public Avaview(Coord sz, long avagob, String camnm) {
        super(sz);
        switch (camnm) {
            case "plavacam":
                player = true;
                party = false;
                fight = false;
                this.camnm = "avacam";
                break;
            case "ptavacam":
                player = false;
                party = true;
                fight = false;
                this.camnm = "avacam";
                break;
            case "fightcam":
                player = false;
                party = false;
                fight = true;
                this.camnm = "avacam";
                break;
            case "bdavacam":
                player = false;
                party = false;
                fight = true;
                this.camnm = "equcam";
                break;
            default:
                player = false;
                fight = false;
                party = false;
                this.camnm = camnm;
        }
        this.avagob = avagob;
    }

    public void uimsg(String msg, Object... args) {
        if (msg == "upd") {
            if (args[0] == null)
                this.avagob = -1;
            else
                this.avagob = Utils.uint32((Integer) args[0]);
            this.avadesc = null;
        } else if (msg == "col") {
            this.color = (Color) args[0];
        } else if (msg == "pop") {
            pop(Desc.decode(ui.sess, args));
        } else {
            super.uimsg(msg, args);
        }
    }

    public void pop(Desc ava, Resource.Resolver resmap) {
        this.avadesc = ava;
        this.resmap = resmap;
        this.avagob = -1;
    }

    public void pop(Desc ava) {
        pop(ava, null);
    }

    private static final OwnerContext.ClassResolver<Avaview> ctxr = new OwnerContext.ClassResolver<Avaview>()
            .add(Glob.class, v -> v.ui.sess.glob)
            .add(Session.class, v -> v.ui.sess)
            .add(Resource.Resolver.class, v -> (v.resmap == null ? v.ui.sess : v.resmap));

    public class AvaOwner implements Sprite.Owner {
        public Random mkrandoom() {
            return (new Random());
        }

        public Resource getres() {
            return (null);
        }

        public <T> T context(Class<T> cl) {
            return (ctxr.context(cl, Avaview.this));
        }
    }

    private void initcomp(Composite gc) {
        if ((comp == null) || (comp.skel != gc.comp.skel)) {
            comp = new Composited(gc.comp.skel);
            comp.eqowner = new AvaOwner();
        }
    }

    private static Camera makecam(Resource base, Composited comp, String camnm) {
        Skeleton.BoneOffset bo = base.layer(Skeleton.BoneOffset.class, camnm);
        if (bo == null)
            throw (new Loading());
        GLState.Buffer buf = new GLState.Buffer(null);
        bo.forpose(comp.pose).prep(buf);
        return (new LocationCam(buf.get(PView.loc)));
    }

    private Camera cam = null;

    protected Camera camera() {
        if (cam == null)
            throw (new Loading());
        return (cam);
    }

    protected void setup(RenderList rl) {
        if (comp == null)
            throw (new Loading());
        rl.add(comp, null);
        rl.add(new DirLight(Color.WHITE, Color.WHITE, Color.WHITE, new Coord3f(1, 1, 1).norm()), null);
    }

    private Composite getgcomp() {
        Gob gob = ui.sess.glob.oc.getgob(avagob);
        if (gob == null)
            return (null);
        Drawable d = gob.getattr(Drawable.class);
        if (!(d instanceof Composite))
            return (null);
        Composite gc = (Composite) d;
        if (gc.comp == null)
            return (null);
        return (gc);
    }

    private Indir<Resource> lbase = null;

    public void updcomp() {
        if (avagob != -1) {
            Composite gc = getgcomp();
            if (gc == null)
                throw (new Loading());
            initcomp(gc);
            if ((cam == null) || (gc.base != lbase))
                cam = makecam((lbase = gc.base).get(), comp, camnm);
            if (gc.comp.cmod != this.cmod)
                comp.chmod(this.cmod = gc.comp.cmod);
            if (gc.comp.cequ != this.cequ)
                comp.chequ(this.cequ = gc.comp.cequ);
        } else if (avadesc != null) {
            Desc d = avadesc;
            if ((d.base != lbase) || (cam == null) || (comp == null)) {
                lbase = d.base;
                comp = new Composited(d.base.get().layer(Skeleton.Res.class).s);
                comp.eqowner = new AvaOwner();
                cam = makecam(d.base.get(), comp, camnm);
            }
            if (d.mod != this.cmod)
                comp.chmod(this.cmod = d.mod);
            if (d.equ != this.cequ)
                comp.chequ(this.cequ = d.equ);
        }
    }

    public void tick(double dt) {
        if (comp != null)
            comp.tick((int) (dt * 1000));
    }

    private String rnm(Indir<Resource> r) {
        try {
            if (r != null && r.get() != null)
                return r.get().name;
        } catch (Exception e) {
            return "";
        }
        return "";
    }

    public Object tooltip(Coord cc, Widget prev) {
        if ((!camnm.equals("equcam") || fight) && Config.avatooltips) {
            StringBuilder base = new StringBuilder();

            if (cequ != null)
                for (Composited.ED eq : cequ) {
                    base.append("\nEqu: ");
                    base.append(rnm(eq.res.res));
                }

            if (cmod != null)
                for (Composited.MD md : cmod) {
                    base.append("\nMod: ");
                    base.append(rnm(md.mod));
                }

            return RichText.render(base.toString(), 300);
        }
        return null;
    }

    public void draw(GOut g) {
        if (TexGL.disableall)
            return;
        boolean drawn = false;
        try {
            if (avagob != -1) {
                Gob gob = ui.sess.glob.oc.getgob(avagob);
                if (gob != null) {
                    Avatar ava = gob.getattr(Avatar.class);
                    if (ava != null) {
                        List<Resource.Image> imgs = ava.images();
                        if (imgs != null) {
                            for (Resource.Image img : imgs) {
                                g.image(img.tex(), Coord.z, this.sz);
                            }
                            drawn = true;
                        }
                    }
                }
            }
        } catch (Loading e) {
        }
        if (!drawn) {
            try {
                updcomp();
                super.draw(g);
            } catch (Loading e) {
                g.image(missing, Coord.z, sz);
            }
        }
        if (color != null) {
            g.chcolor(color);
            Window.wbox.draw(g, Coord.z, sz);
        }
    }

    public boolean mousedown(Coord c, int button) {
        if (canactivate) {
            wdgmsg("click", button);
            return (true);
        }
        return (super.mousedown(c, button));
    }
}

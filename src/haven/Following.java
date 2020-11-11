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

import haven.sloth.gob.HeldBy;
import haven.sloth.gob.Holding;

import java.util.Optional;

public class Following extends Moving {
    long tgt;
    double lastv = 0.0;
    Indir<Resource> xfres;
    String xfname;
    GLState xf = null, lpxf = null;
    Gob lxfb = null;
    Skeleton.Pose lpose = null;

    public Following(Gob gob, long tgt, Indir<Resource> xfres, String xfname) {
        super(gob);
        this.tgt = tgt;
        this.xfres = xfres;
        this.xfname = xfname;
    }

    public Coord3f getc() {
        Gob tgt = gob.glob.oc.getgob(this.tgt);
        if (tgt == null)
            return (gob.getrc());
        return (tgt.getc());
    }

    public double getv() {
        Gob tgt = gob.glob.oc.getgob(this.tgt);
        if (tgt != null) {
            Moving mv = tgt.getattr(Moving.class);
            if (mv == null)
                lastv = 0.0;
            else
                lastv = mv.getv();
        }
        return (lastv);
    }

    public Optional<Coord2d> getDest() {
        Gob tgt = gob.glob.oc.getgob(this.tgt);
        if (tgt != null) {
            return Optional.of(new Coord2d(tgt.getc()));
        } else {
            return Optional.empty();
        }
    }

    public Gob tgt() {
        return (gob.glob.oc.getgob(this.tgt));
    }

    @Override
    public void tick() {
        super.tick();
        final Gob tgt = tgt();
        if (tgt != null) {
            final Holding holding = tgt.getattr(Holding.class);
            if (holding == null || holding.held != gob) {
                tgt.setattr(new Holding(tgt, gob));
                gob.delayedsetattr(new HeldBy(gob, tgt), Gob::updateHitmap);
            }
        }
    }

    private Skeleton.Pose getpose(Gob tgt) {
        if (tgt == null)
            return (null);
        return (Skeleton.getpose(tgt.getattr(Drawable.class)));
    }

    public GLState xf() {
        synchronized (this) {
            Gob tgt = tgt();
            Skeleton.Pose cpose = getpose(tgt);
            GLState pxf = xf(tgt);
            if ((xf == null) || (cpose != lpose) || (lpxf != pxf)) {
                if (tgt == null) {
                    xf = null;
                    lpose = null;
                    lxfb = null;
                    lpxf = null;
                    return (null);
                }
                Skeleton.BoneOffset bo = xfres.get().layer(Skeleton.BoneOffset.class, xfname);
                if (bo == null)
                    throw (new RuntimeException("No such boneoffset in " + xfres.get() + ": " + xfname));
                if (pxf != null)
                    xf = GLState.compose(pxf, bo.forpose(cpose));
                else
                    xf = GLState.compose(tgt.loc, bo.forpose(cpose));
                lpxf = pxf;
                lxfb = tgt;
                lpose = cpose;
            }
        }
        return (xf);
    }

    public static GLState xf(Gob gob) {
        if (gob == null)
            return (null);
        Following flw = gob.getattr(Following.class);
        if (flw == null)
            return (null);
        return (flw.xf());
    }
}

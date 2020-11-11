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

import static java.lang.Math.PI;

public class Cal extends Widget {
    public static final double hbr = 20;
    static final Tex bg = Resource.loadtex("gfx/hud/calendar/glass");
    static final Tex[] dlnd = new Tex[4];
    static final Tex[] nlnd = new Tex[4];
    static final Tex dsky = Resource.loadtex("gfx/hud/calendar/daysky");
    static final Tex nsky = Resource.loadtex("gfx/hud/calendar/nightsky");
    static final Resource.Anim sun = Resource.local().loadwait("gfx/hud/calendar/sun").layer(Resource.animc);
    static final Resource.Anim moon = Resource.local().loadwait("gfx/hud/calendar/moon").layer(Resource.animc);

    static {
        for (int i = 0; i < dlnd.length; i++) {
            dlnd[i] = Resource.loadtex(String.format("gfx/hud/calendar/dayscape-%d", i));
            nlnd[i] = Resource.loadtex(String.format("gfx/hud/calendar/nightscape-%d", i));
        }
    }

    public Cal() {
        super(bg.sz());
    }

    public void draw(GOut g) {
        Astronomy a = ui.sess.glob.ast;
        long now = System.currentTimeMillis();
        g.image(a.night ? nsky : dsky, Coord.z);
        int mp = (int) Math.round(a.mp * (double) moon.f.length) % moon.f.length;
        Resource.Image moon = Cal.moon.f[mp][0];
        Resource.Image sun = Cal.sun.f[(int) ((now / Cal.sun.d) % Cal.sun.f.length)][0];
        Coord mc = Coord.sc((a.dt + 0.25) * 2 * PI, hbr).add(sz.div(2)).sub(moon.sz.div(2));
        Coord sc = Coord.sc((a.dt + 0.75) * 2 * PI, hbr).add(sz.div(2)).sub(sun.sz.div(2));
        this.parent.ui.sess.glob.moonid = moon.id;
        this.parent.ui.sess.glob.night = a.night;
        g.chcolor(a.mc);
        g.image(moon, mc);
        g.chcolor();
        g.image(sun, sc);
        g.image((a.night ? nlnd : dlnd)[a.is], Coord.z);
        g.image(bg, Coord.z);
    }
}

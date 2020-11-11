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

import haven.sloth.gui.MovableWidget;

import java.awt.Color;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IMeter extends MovableWidget {
    private static final Resource ponysfx = Resource.local().loadwait("sfx/alarmpony");
    private static final Pattern hppat = Pattern.compile("Health: ([0-9]+)/([0-9]+)/([0-9]+)");
    private static final Pattern stampat = Pattern.compile("Stamina: ([0-9]+)");
    private static final Pattern energypat = Pattern.compile("Energy: ([0-9]+)");
    static Coord off = new Coord(22, 7);
    static Coord fsz = new Coord(101, 24);
    static Coord msz = new Coord(75, 10);
    Indir<Resource> bg;
    List<Meter> meters;
    private boolean ponyalarm = true;
    String meterinfo = "";

    @RName("im")
    public static class $_ implements Factory {
        public Widget create(UI ui, Object[] args) {
            Indir<Resource> bg = ui.sess.getres((Integer) args[0]);
            List<Meter> meters = new LinkedList<>();
            for (int i = 1; i < args.length; i += 2) {
                meters.add(new Meter((Color) args[i], (Integer) args[i + 1]));
            }
            return (new IMeter(bg, meters, "meter-" + args[0]));
        }
    }

    private IMeter(Indir<Resource> bg, List<Meter> meters, final String name) {
        super(fsz, name);
        this.bg = bg;
        this.meters = meters;
    }

    public static class Meter {
        Color c;
        public int a;

        public Meter(Color c, int a) {
            this.c = c;
            this.a = a;
        }
    }

    @Override
    protected boolean moveHit(Coord c, int btn) {
        return c.isect(Coord.z, sz);
    }

    public void draw(GOut g) {
        try {
            Tex bg = this.bg.get().layer(Resource.imgc).tex();
            g.chcolor(0, 0, 0, 255);
            g.frect(off, msz);
            g.chcolor();
            for (Meter m : meters) {
                int w = msz.x;
                w = (w * m.a) / 100;
                g.chcolor(m.c);
                g.frect(off, new Coord(w, msz.y));
                if (Config.showmetertext) {
                    g.chcolor();
                    g.atextstroked(meterinfo, new Coord(msz.x / 2 + 10, msz.y / 2 - 1), 0, 0, Color.WHITE, Color.BLACK, Text.num10Fnd);
                }
            }
            g.chcolor();
            g.image(bg, Coord.z);
        } catch (Loading l) {
            //Ignore
        }
    }

    public void uimsg(String msg, Object... args) {
        if (msg == "set") {
            List<Meter> meters = new LinkedList<>();
            for (int i = 0; i < args.length; i += 2)
                meters.add(new Meter((Color) args[i], (Integer) args[i + 1]));
            this.meters = meters;

            if (ponyalarm) {
                try {
                    Resource res = bg.get();
                    if (res != null && res.name.equals("gfx/hud/meter/häst")) {
                        if (meters.get(0).a <= 10) {
                            Audio.play(ponysfx, 1.0);
                            ponyalarm = false;
                        }
                    }
                } catch (Loading e) {
                }
            }
        } else {
            super.uimsg(msg, args);
            if (msg.equals("tip")) {
                final String tt = (String) args[0];
                Matcher matcher = hppat.matcher(tt);
                if (matcher.find()) {
                    ui.sess.details.shp = Integer.parseInt(matcher.group(1));
                    ui.sess.details.hhp = Integer.parseInt(matcher.group(2));
                    ui.sess.details.mhp = Integer.parseInt(matcher.group(3));
                } else {
                    matcher = stampat.matcher(tt);
                    if (matcher.find()) {
                        ui.sess.details.stam = Integer.parseInt(matcher.group(1));
                    } else {
                        matcher = energypat.matcher(tt);
                        if (matcher.find()) {
                            ui.sess.details.energy = Integer.parseInt(matcher.group(1));
                        }
                    }
                }
                if (Config.showmetertext) {
                    meterinfo = tt;
                    if (meterinfo.contains("ow")) {
                        meterinfo = args[0].toString().split(" ")[2];
                    } else {
                        meterinfo = args[0].toString().split(" ")[1];
                    }
                    if (meterinfo.contains("/")) {
                        meterinfo = Integer.toString(ui.sess.details.shp);
                    }
                }
            }
        }
    }
}

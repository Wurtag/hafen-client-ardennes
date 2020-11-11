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

import haven.sloth.gui.SessionDisplay;

import java.awt.event.KeyEvent;

public class RootWidget extends ConsoleHost {
    public static final Resource defcurs = Resource.local().loadwait("gfx/hud/curs/arw");
    Profile guprof, grprof, ggprof;
    boolean afk = false;
    private char last_gk;
    private long last_gk_time;
    public SessionDisplay sessionDisplay = new SessionDisplay();

    public RootWidget(UI ui, Coord sz) {
        super(ui, new Coord(0, 0), sz);
        setfocusctl(true);
        hasfocus = true;
        cursor = defcurs.indir();
        if (Config.sessiondisplay) {
            add(sessionDisplay = new SessionDisplay());
        }
    }

    public boolean globtype(char key, KeyEvent ev) {
        if (!super.globtype(key, ev)) {
            if (key == '`') {
                GameUI gi = findchild(GameUI.class);
                if (Config.profile) {
                    add(new Profwnd(guprof, "UI profile"), new Coord(100, 100));
                    add(new Profwnd(grprof, "GL profile"), new Coord(450, 100));
                    if ((gi != null) && (gi.map != null))
                        add(new Profwnd(gi.map.prof, "Map profile"), new Coord(100, 250));
                }
                if (Config.profilegpu) {
                    add(new Profwnd(ggprof, "GPU profile"), new Coord(450, 250));
                }
            } else if (key == ':') {
                entercmd();
            } else if (key != 0 && (last_gk != key || (System.currentTimeMillis() - last_gk_time) >= 500)) {
                wdgmsg("gk", (int) key);
                last_gk = key;
                last_gk_time = System.currentTimeMillis();
            }
        }
        return (true);
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        return super.mousedown(c, button);
    }

    public void draw(GOut g) {
        super.draw(g);
        drawcmd(g, new Coord(20, sz.y - 20));
    }

    public void error(String msg) {
    }
}

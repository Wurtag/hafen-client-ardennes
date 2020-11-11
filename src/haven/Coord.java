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

import java.util.Iterator;
import java.util.Optional;

import static java.lang.Math.PI;

public class Coord implements Comparable<Coord>, java.io.Serializable {
    public int x, y;
    public static Coord z = new Coord(0, 0);
    public static Coord[] uecw = {new Coord(0, -1), new Coord(1, 0), new Coord(0, 1), new Coord(-1, 0)};
    public static Coord[] uccw = {new Coord(0, 0), new Coord(1, 0), new Coord(1, 1), new Coord(0, 1)};
    public static Coord[] upcw = {new Coord(0, -1), new Coord(1, -1), new Coord(1, 0), new Coord(1, 1),
            new Coord(0, 1), new Coord(-1, 1), new Coord(-1, 0), new Coord(-1, -1)};
    public static Coord[] usqc = {new Coord(-1, -1), new Coord(0, -1), new Coord(1, -1),
            new Coord(-1, 0), new Coord(0, 0), new Coord(1, 0),
            new Coord(-1, 1), new Coord(0, 1), new Coord(1, 1)};

    public Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Coord(float x, float y) {
        this((int) x, (int) y);
    }

    public Coord(Coord c) {
        this(c.x, c.y);
    }

    public Coord(Coord3f c) {
        this((int) c.x, (int) c.y);
    }

    public Coord() {
        this(0, 0);
    }

    public Coord(java.awt.Dimension d) {
        this(d.width, d.height);
    }

    public static Coord sc(double a, double r) {
        return (new Coord((int) Math.round(Math.cos(a) * r), -(int) Math.round(Math.sin(a) * r)));
    }

    public boolean equals(Object o) {
        if (!(o instanceof Coord))
            return (false);
        Coord c = (Coord) o;
        return ((c.x == x) && (c.y == y));
    }

    public boolean equals(int X, int Y) {
        return ((x == X) && (y == Y));
    }

    public int compareTo(Coord c) {
        if (c.y != y)
            return (c.y - y);
        if (c.x != x)
            return (c.x - x);
        return (0);
    }

    public int hashCode() {
        return (((y & 0xffff) * 31) + (x & 0xffff));
    }

    public Coord add(int ax, int ay) {
        return (new Coord(x + ax, y + ay));
    }

    public Coord add(Coord b) {
        return (add(b.x, b.y));
    }

    public Coord sub(int ax, int ay) {
        return (new Coord(x - ax, y - ay));
    }

    public Coord sub(Coord b) {
        return (sub(b.x, b.y));
    }

    public Coord mul(int f) {
        return (new Coord(x * f, y * f));
    }

    public Coord mul(int fx, int fy) {
        return (new Coord(x * fx, y * fy));
    }

    public Coord mul(double f) {
        return (new Coord((int) Math.round(x * f), (int) Math.round(y * f)));
    }

    public Coord inv() {
        return (new Coord(-x, -y));
    }

    public Coord mul(Coord f) {
        return (new Coord(x * f.x, y * f.y));
    }

    public Coord2d mul(Coord2d f) {
        return (new Coord2d(x * f.x, y * f.y));
    }

    public Coord div(Coord d) {
        return (new Coord(Utils.floordiv(x, d.x), Utils.floordiv(y, d.y)));
    }

    public Coord div(int d) {
        return (div(new Coord(d, d)));
    }

    public Coord div(double d) {
        return new Coord((int) (x / d), (int) (y / d));
    }

    public Coord mod(Coord d) {
        return (new Coord(Utils.floormod(x, d.x), Utils.floormod(y, d.y)));
    }

    public Coord copy() {
        return new Coord(x, y);
    }

    public boolean isect(Coord c, Coord s) {
        return ((x >= c.x) && (y >= c.y) && (x < c.x + s.x) && (y < c.y + s.y));
    }

    public boolean eisect(Coord c, Coord s) {
        return ((x >= c.x) && (y >= c.y) && (x <= c.x + s.x) && (y <= c.y + s.y));
    }

    //m2 is 0, b2 is e2.y
    public Optional<Coord> hlsect(Coord e1, int b2) {
        double m1 = (e1.y - y) / (double) (e1.x - x);
        double b1 = e1.y - m1 * e1.x;

        if (m1 != 0) {
            return Optional.of(new Coord((int) (Double.isFinite(m1) ? (b2 - b1) / m1 : this.x), b2));
        } else {
            return Optional.empty();
        }
    }

    //m2 is Inf, x is e2.x
    public Optional<Coord> vlsect(Coord e1, int x) {
        double m = (e1.y - y) / (double) (e1.x - this.x);
        double b = this.y - m * this.x;

        if (Double.isFinite(m)) {
            return Optional.of(new Coord(x, (int) (m * x + b)));
        } else {
            return Optional.empty();
        }
    }

    public boolean between(Coord s, Coord e) {
        Coord sz = new Coord(e.x - s.x, e.y - s.y);
        Coord tl = new Coord(s);
        if (sz.x < 0) {
            sz.x = -sz.x;
            tl.x = e.x;
        }
        if (sz.y < 0) {
            sz.y = -sz.y;
            tl.y = e.y;
        }
        return eisect(tl, sz);
    }

    public String toString() {
        return ("(" + x + ", " + y + ")");
    }

    public double angle(Coord o) {
        Coord c = o.add(this.inv());
        if (c.x == 0) {
            if (c.y < 0)
                return (-PI / 2);
            else
                return (PI / 2);
        } else {
            if (c.x < 0) {
                if (c.y < 0)
                    return (-PI + Math.atan((double) c.y / (double) c.x));
                else
                    return (PI + Math.atan((double) c.y / (double) c.x));
            } else {
                return (Math.atan((double) c.y / (double) c.x));
            }
        }
    }

    public Coord rot(float angl) {
        final float
                cos = (float) Math.cos(angl),
                sin = (float) Math.sin(angl);
        return new Coord(x * cos - y * sin,
                x * sin + y * cos);
    }

    public double abs() {
        double x = this.x, y = this.y;
        return (Math.sqrt((x * x) + (y * y)));
    }

    public Coord norm(double n) {
        return (mul(n / abs()));
    }

    public double dist(Coord o) {
        long dx = o.x - x;
        long dy = o.y - y;
        return (Math.sqrt((dx * dx) + (dy * dy)));
    }

    public Coord clip(Coord ul, Coord sz) {
        Coord ret = this;
        if (ret.x < ul.x)
            ret = new Coord(ul.x, ret.y);
        if (ret.y < ul.y)
            ret = new Coord(ret.x, ul.y);
        if (ret.x > ul.x + sz.x)
            ret = new Coord(ul.x + sz.x, ret.y);
        if (ret.y > ul.y + sz.y)
            ret = new Coord(ret.x, ul.y + sz.y);
        return (ret);
    }

    public int manhattan2(Coord c) {
        return Math.max(Math.abs(c.x - x), Math.abs(c.y - y));
    }

    public Coord rotate(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Coord((int) Math.round(x * cos - y * sin),
                (int) Math.round(y * cos + x * sin));
    }

    public Iterable<Coord> offsets(Coord... list) {
        return (new Iterable<Coord>() {
            public Iterator<Coord> iterator() {
                return (new Iterator<Coord>() {
                    int i = 0;

                    public boolean hasNext() {
                        return (i < list.length);
                    }

                    public Coord next() {
                        return (add(list[i++]));
                    }
                });
            }
        });
    }
}

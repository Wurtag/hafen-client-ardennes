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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

public class Utils {
    public static final java.nio.charset.Charset utf8 = java.nio.charset.Charset.forName("UTF-8");
    public static final java.nio.charset.Charset ascii = java.nio.charset.Charset.forName("US-ASCII");
    public static final java.awt.image.ColorModel rgbm = java.awt.image.ColorModel.getRGBdefault();
    private static Preferences prefs = null;

    static Coord imgsz(BufferedImage img) {
        return (new Coord(img.getWidth(), img.getHeight()));
    }

    public static void defer(final Runnable r) {
        Defer.later(new Defer.Callable<Object>() {
            public Object call() {
                r.run();
                return (null);
            }
        });
    }

    static void drawgay(BufferedImage t, BufferedImage img, Coord c) {
        Coord sz = imgsz(img);
        for (int y = 0; y < sz.y; y++) {
            for (int x = 0; x < sz.x; x++) {
                int p = img.getRGB(x, y);
                if (Utils.rgbm.getAlpha(p) > 128) {
                    if ((p & 0x00ffffff) == 0x00ff0080)
                        t.setRGB(x + c.x, y + c.y, 0);
                    else
                        t.setRGB(x + c.x, y + c.y, p);
                }
            }
        }
    }

    public static int drawtext(Graphics g, String text, Coord c) {
        java.awt.FontMetrics m = g.getFontMetrics();
        g.drawString(text, c.x, c.y + m.getAscent());
        return (m.getHeight());
    }

    public static String stream2str(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    static Coord textsz(Graphics g, String text) {
        java.awt.FontMetrics m = g.getFontMetrics();
        java.awt.geom.Rectangle2D ts = m.getStringBounds(text, g);
        return (new Coord((int) ts.getWidth(), (int) ts.getHeight()));
    }

    static void aligntext(Graphics g, String text, Coord c, double ax, double ay) {
        java.awt.FontMetrics m = g.getFontMetrics();
        java.awt.geom.Rectangle2D ts = m.getStringBounds(text, g);
        g.drawString(text, (int) (c.x - ts.getWidth() * ax), (int) (c.y + m.getAscent() - ts.getHeight() * ay));
    }

    public static String fpformat(int num, int div, int dec) {
        StringBuilder buf = new StringBuilder();
        boolean s = false;
        if (num < 0) {
            num = -num;
            s = true;
        }
        for (int i = 0; i < div - dec; i++)
            num /= 10;
        for (int i = 0; i < dec; i++) {
            buf.append((char) ('0' + (num % 10)));
            num /= 10;
        }
        buf.append('.');
        if (num == 0) {
            buf.append('0');
        } else {
            while (num > 0) {
                buf.append((char) ('0' + (num % 10)));
                num /= 10;
            }
        }
        if (s)
            buf.append('-');
        return (buf.reverse().toString());
    }

    public static String thformat(long num) {
        return (String.format("%,d", num));
    }

    /* These are horribly imprecise and ugly technically speaking, but
     * they should do for these simple purposes. */
    public static String odformat(double num, int md) {
        if (num < 0)
            return ("-" + odformat(-num, md));
        long dm = 1;
        for (int i = 0; i < md; i++) dm *= 10;
        long raw = (long) Math.round(num * dm);
        long ip = raw / dm;
        long dp = raw % dm;
        if (dp == 0)
            return (Long.toString(ip));
        StringBuilder buf = new StringBuilder();
        buf.append(ip);
        buf.append('.');
        for (dm /= 10; dm > dp; dm /= 10) buf.append('0');
        while ((dp % 10) == 0) dp /= 10;
        buf.append(dp);
        return (buf.toString());
    }

    public static String odformat2(double num, int md) {
        if (num < 0)
            return ("-" + odformat2(-num, md));
        if (num == 0)
            return ("0");
        long tm = 1;
        for (int i = 0; i < md; i++) tm *= 10;
        long dm;
        for (dm = tm; ((long) Math.round(num * dm)) < tm; dm *= 10) ;
        if (dm > tm)
            dm /= 10;
        long raw = (long) Math.round(num * dm);
        long ip = raw / dm;
        long dp = raw % dm;
        if (dp == 0)
            return (Long.toString(ip));
        StringBuilder buf = new StringBuilder();
        buf.append(ip);
        buf.append('.');
        for (dm /= 10; dm > dp; dm /= 10) buf.append('0');
        while ((dp % 10) == 0) dp /= 10;
        buf.append(dp);
        return (buf.toString());
    }

    static void line(Graphics g, Coord c1, Coord c2) {
        g.drawLine(c1.x, c1.y, c2.x, c2.y);
    }

    static void AA(Graphics g) {
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    public static Random mkrandoom(long seed) {
        long rev = 0;
        for (int i = 0; i < 32; i++) {
            rev |= (seed & (1l << i)) << (63 - (i * 2));
            rev |= (seed & (1l << (63 - i))) >>> (63 - (i * 2));
        }
        seed = seed ^ rev;
        seed = (seed & 0x0000ffffffffffffl) ^ ((rev & 0xffffffffffff0000l) >>> 16);
        return (new Random(seed));
    }

    static synchronized Preferences prefs() {
        if (prefs == null) {
            Preferences node = Preferences.userNodeForPackage(Utils.class);
            if (Config.prefspec != null)
                node = node.node(Config.prefspec);
            prefs = node;
        }
        return (prefs);
    }

    static void delpref(String prefname) {
        try {
            prefs().remove(prefname);
        } catch (SecurityException e) {
        }
    }

    public static String getpref(String prefname, String def) {
        try {
            return (prefs().get(prefname, def));
        } catch (SecurityException e) {
            return (def);
        }
    }

    public static void setpref(String prefname, String val) {
        try {
            prefs().put(prefname, val);
        } catch (SecurityException e) {
        }
    }

    static String[] getprefsa(String prefname, String[] def) {
        try {
            String jsonstr = Utils.getpref(prefname, null);
            if (jsonstr == null)
                return def;
            JSONArray ja = new JSONArray(jsonstr);
            String[] ra = new String[ja.length()];
            for (int i = 0; i < ja.length(); i++)
                ra[i] = ja.getString(i);
            return ra;
        } catch (SecurityException e) {
            return def;
        } catch (Exception ex) {
            ex.printStackTrace();
            return def;
        }
    }

    static void setprefsa(String prefname, String[] val) {
        try {
            String jsonarr = "";
            for (String s : val)
                jsonarr += "\"" + s + "\",";
            if (jsonarr.length() > 0)
                jsonarr = jsonarr.substring(0, jsonarr.length() - 1);
            Utils.setpref(prefname, "[" + jsonarr + "]");
        } catch (SecurityException e) {
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void loadprefchklist(String prefname, Map<String, CheckListboxItem> data) {
        try {
            String jsonstr = Utils.getpref(prefname, null);
            if (jsonstr == null)
                return;
            JSONArray ja = new JSONArray(jsonstr);
            for (CheckListboxItem itm : data.values())
                itm.selected = false;
            for (int i = 0; i < ja.length(); i++) {
                CheckListboxItem itm = data.get(ja.getString(i));
                if (itm != null)
                    itm.selected = true;
            }
        } catch (SecurityException e) {
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void setprefchklst(String prefname, Map<String, CheckListboxItem> val) {
        try {
            String jsonarr = "";
            Iterator it = val.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                CheckListboxItem itm = (CheckListboxItem) entry.getValue();
                if (itm.selected)
                    jsonarr += "\"" + entry.getKey() + "\",";
            }
            if (jsonarr.length() > 0)
                jsonarr = jsonarr.substring(0, jsonarr.length() - 1);
            Utils.setpref(prefname, "[" + jsonarr + "]");
        } catch (SecurityException e) {
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    public static void saveCurioList() {
        synchronized (Config.curioslist) {
            Gson gson = (new GsonBuilder()).create();
            Config.saveFile("curiolist.json", gson.toJson(Config.curioslist));
        }
    }

    public static void loadCurioList() {
        String json = Config.loadFile("curiolist.json");
        if (json != null) {
            try {
                Gson gson = (new GsonBuilder()).create();
                Type collectionType = new TypeToken<HashMap<String, Boolean>>() {
                }.getType();
                Config.curioslist = gson.fromJson(json, collectionType);
            } catch (Exception ignored) {
            }
        }
        if (Config.curioslist == null) {
            Config.curioslist = new HashMap<>();
            Config.curioslist.put("Chiming Bluebell", false);
        }
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    public static void saveAutodropList() {
        synchronized (Config.autodroplist) {
            Gson gson = (new GsonBuilder()).create();
            Config.saveFile("autodroplist.json", gson.toJson(Config.autodroplist));
        }
    }

    public static void loadAutodropList() {
        String json = Config.loadFile("autodroplist.json");
        if (json != null) {
            try {
                Gson gson = (new GsonBuilder()).create();
                Type collectionType = new TypeToken<HashMap<String, Boolean>>() {
                }.getType();
                Config.autodroplist = gson.fromJson(json, collectionType);
            } catch (Exception ignored) {
            }
        }
        if (Config.autodroplist == null) {
            Config.autodroplist = new HashMap<>();
            Config.autodroplist.put("Intestines", false);
            Config.autodroplist.put("Bone Material", false);
            Config.autodroplist.put("Cat Gold", false);
            Config.autodroplist.put("Strange Crystal", false);
            Config.autodroplist.put("Petrified Seashell", false);
            Config.autodroplist.put("Coal", false);
            Config.autodroplist.put("Soil", false);
            Config.autodroplist.put("Entrails", false);
            Config.autodroplist.put("Cattail Head", false);
            Config.autodroplist.put("Cattail Roots", false);
        }
    }

    public static JSONObject[] getprefjsona(String prefname, JSONObject[] def) {
        try {
            String jsonstr = Utils.getpref(prefname, null);
            if (jsonstr == null)
                return null;
            JSONArray ja = new JSONArray(jsonstr);
            JSONObject[] ra = new JSONObject[ja.length()];
            for (int i = 0; i < ja.length(); i++)
                ra[i] = ja.getJSONObject(i);
            return ra;
        } catch (SecurityException e) {
            return def;
        } catch (Exception ex) {
            ex.printStackTrace();
            return def;
        }
    }

    public static void setprefjsona(String prefname, JSONObject[] val) {
        try {
            String jsonarr = "";
            for (JSONObject o : val)
                jsonarr += o.toString() + ",";
            if (jsonarr.length() > 0)
                jsonarr = jsonarr.substring(0, jsonarr.length() - 1);
            Utils.setpref(prefname, "[" + jsonarr + "]");
        } catch (SecurityException e) {
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static int getprefi(String prefname, int def) {
        try {
            return (prefs().getInt(prefname, def));
        } catch (SecurityException e) {
            return (def);
        }
    }

    public static void setprefi(String prefname, int val) {
        try {
            prefs().putInt(prefname, val);
        } catch (SecurityException e) {
        }
    }

    public static double getprefd(String prefname, double def) {
        try {
            return (prefs().getDouble(prefname, def));
        } catch (SecurityException e) {
            return (def);
        }
    }

    public static void setprefd(String prefname, double val) {
        try {
            prefs().putDouble(prefname, val);
        } catch (SecurityException e) {
        }
    }

    public static float getpreff(String prefname, float def) {
        try {
            return (prefs().getFloat(prefname, def));
        } catch (SecurityException e) {
            return (def);
        }
    }

    public static void setpreff(String prefname, float val) {
        try {
            prefs().putFloat(prefname, val);
        } catch (SecurityException e) {
        }
    }

    public static boolean getprefb(String prefname, boolean def) {
        try {
            return (prefs().getBoolean(prefname, def));
        } catch (SecurityException e) {
            return (def);
        }
    }

    public static void setprefb(String prefname, boolean val) {
        try {
            prefs().putBoolean(prefname, val);
        } catch (SecurityException e) {
        }
    }

    public static Coord getprefc(String prefname, Coord def) {
        try {
            String val = prefs().get(prefname, null);
            if (val == null)
                return (def);
            int x = val.indexOf('x');
            if (x < 0)
                return (def);
            return (new Coord(Integer.parseInt(val.substring(0, x)), Integer.parseInt(val.substring(x + 1))));
        } catch (SecurityException e) {
            return (def);
        }
    }

    public static void setprefc(String prefname, Coord val) {
        try {
            prefs().put(prefname, val.x + "x" + val.y);
        } catch (SecurityException e) {
        }
    }

    static byte[] getprefb(String prefname, byte[] def) {
        try {
            return (prefs().getByteArray(prefname, def));
        } catch (SecurityException e) {
            return (def);
        }
    }

    static void setprefb(String prefname, byte[] val) {
        try {
            prefs().putByteArray(prefname, val);
        } catch (SecurityException e) {
        }
    }

    public static String getprop(String propname, String def) {
        try {
            String ret;
            if ((ret = System.getProperty(propname)) != null)
                return (ret);
            return (def);
        } catch (SecurityException e) {
            return (def);
        }
    }

    public static int ub(byte b) {
        return (((int) b) & 0xff);
    }

    public static byte sb(int b) {
        return ((byte) b);
    }

    public static byte f2s8(float v) {
        return ((byte) Math.max(Math.min(Math.round(v * 127f), 127), -127));
    }

    public static byte f2u8(float v) {
        return ((byte) Math.max(Math.min(Math.round(v * 255f), 255), 0));
    }

    public static long uint32(int n) {
        return (n & 0xffffffffl);
    }

    public static int uint16d(byte[] buf, int off) {
        return (ub(buf[off]) | (ub(buf[off + 1]) << 8));
    }

    public static int int16d(byte[] buf, int off) {
        return ((int) (short) uint16d(buf, off));
    }

    public static long uint32d(byte[] buf, int off) {
        return ((long) ub(buf[off]) | ((long) ub(buf[off + 1]) << 8) | ((long) ub(buf[off + 2]) << 16) | ((long) ub(buf[off + 3]) << 24));
    }

    public static void uint32e(long num, byte[] buf, int off) {
        buf[off] = (byte) (num & 0xff);
        buf[off + 1] = (byte) ((num & 0x0000ff00) >> 8);
        buf[off + 2] = (byte) ((num & 0x00ff0000) >> 16);
        buf[off + 3] = (byte) ((num & 0xff000000) >> 24);
    }

    public static int int32d(byte[] buf, int off) {
        return ((int) uint32d(buf, off));
    }

    public static long int64d(byte[] buf, int off) {
        long b = 0;
        for (int i = 0; i < 8; i++)
            b |= ((long) ub(buf[off + i])) << (i * 8);
        return (b);
    }

    public static void int64e(long num, byte[] buf, int off) {
        for (int i = 0; i < 8; i++) {
            buf[off++] = (byte) (num & 0xff);
            num >>>= 8;
        }
    }

    public static void int32e(int num, byte[] buf, int off) {
        uint32e(((long) num) & 0xffffffff, buf, off);
    }

    public static void uint16e(int num, byte[] buf, int off) {
        buf[off] = sb(num & 0xff);
        buf[off + 1] = sb((num & 0xff00) >> 8);
    }

    public static String strd(byte[] buf, int[] off) {
        int i;
        for (i = off[0]; buf[i] != 0; i++) ;
        String ret;
        try {
            ret = new String(buf, off[0], i - off[0], "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw (new IllegalArgumentException(e));
        }
        off[0] = i + 1;
        return (ret);
    }

    public static double floatd(byte[] buf, int off) {
        int e = buf[off];
        long t = uint32d(buf, off + 1);
        int m = (int) (t & 0x7fffffffL);
        boolean s = (t & 0x80000000L) != 0;
        if (e == -128) {
            if (m == 0)
                return (0.0);
            throw (new RuntimeException("Invalid special float encoded (" + m + ")"));
        }
        double v = (((double) m) / 2147483648.0) + 1.0;
        if (s)
            v = -v;
        return (Math.pow(2.0, e) * v);
    }

    public static float float32d(byte[] buf, int off) {
        return (Float.intBitsToFloat(int32d(buf, off)));
    }

    public static double float64d(byte[] buf, int off) {
        return (Double.longBitsToDouble(int64d(buf, off)));
    }

    public static void float32e(float num, byte[] buf, int off) {
        int32e(Float.floatToIntBits(num), buf, off);
    }

    public static void float64e(double num, byte[] buf, int off) {
        int64e(Double.doubleToLongBits(num), buf, off);
    }

    public static void float9995d(int word, float[] ret) {
        int xb = (word & 0x7f800000) >> 23, xs = ((word & 0x80000000) >> 31) & 1,
                yb = (word & 0x003fc000) >> 14, ys = ((word & 0x00400000) >> 22) & 1,
                zb = (word & 0x00001fd0) >> 5, zs = ((word & 0x00002000) >> 13) & 1;
        int me = (word & 0x1f) - 15;
        int xe = Integer.numberOfLeadingZeros(xb) - 24,
                ye = Integer.numberOfLeadingZeros(yb) - 24,
                ze = Integer.numberOfLeadingZeros(zb) - 24;
        if (xe == 32) ret[0] = 0;
        else ret[0] = Float.intBitsToFloat((xs << 31) | ((me - xe + 127) << 23) | ((xb << (xe + 16)) & 0x007fffff));
        if (ye == 32) ret[1] = 0;
        else ret[1] = Float.intBitsToFloat((ys << 31) | ((me - ye + 127) << 23) | ((yb << (ye + 16)) & 0x007fffff));
        if (ze == 32) ret[2] = 0;
        else ret[2] = Float.intBitsToFloat((zs << 31) | ((me - ze + 127) << 23) | ((zb << (ze + 16)) & 0x007fffff));
    }

    public static float hfdec(short bits) {
        int b = ((int) bits) & 0xffff;
        int e = (b & 0x7c00) >> 10;
        int m = b & 0x03ff;
        int ee;
        if (e == 0) {
            if (m == 0) {
                ee = 0;
            } else {
                int n = Integer.numberOfLeadingZeros(m) - 22;
                ee = (-15 - n) + 127;
                m = (m << (n + 1)) & 0x03ff;
            }
        } else if (e == 0x1f) {
            ee = 0xff;
        } else {
            ee = e - 15 + 127;
        }
        int f32 = ((b & 0x8000) << 16) |
                (ee << 23) |
                (m << 13);
        return (Float.intBitsToFloat(f32));
    }

    public static short hfenc(float f) {
        int b = Float.floatToIntBits(f);
        int e = (b & 0x7f800000) >> 23;
        int m = b & 0x007fffff;
        int ee;
        if (e == 0) {
            ee = 0;
            m = 0;
        } else if (e == 0xff) {
            ee = 0x1f;
        } else if (e < 127 - 14) {
            ee = 0;
            m = (m | 0x00800000) >> ((127 - 14) - e);
        } else if (e > 127 + 15) {
            return (((b & 0x80000000) == 0) ? ((short) 0x7c00) : ((short) 0xfc00));
        } else {
            ee = e - 127 + 15;
        }
        int f16 = ((b >> 16) & 0x8000) |
                (ee << 10) |
                (m >> 13);
        return ((short) f16);
    }

    public static float mfdec(byte bits) {
        int b = ((int) bits) & 0xff;
        int e = (b & 0x78) >> 3;
        int m = b & 0x07;
        int ee;
        if (e == 0) {
            if (m == 0) {
                ee = 0;
            } else {
                int n = Integer.numberOfLeadingZeros(m) - 29;
                ee = (-7 - n) + 127;
                m = (m << (n + 1)) & 0x07;
            }
        } else if (e == 0x0f) {
            ee = 0xff;
        } else {
            ee = e - 7 + 127;
        }
        int f32 = ((b & 0x80) << 24) |
                (ee << 23) |
                (m << 20);
        return (Float.intBitsToFloat(f32));
    }

    public static byte mfenc(float f) {
        int b = Float.floatToIntBits(f);
        int e = (b & 0x7f800000) >> 23;
        int m = b & 0x007fffff;
        int ee;
        if (e == 0) {
            ee = 0;
            m = 0;
        } else if (e == 0xff) {
            ee = 0x0f;
        } else if (e < 127 - 6) {
            ee = 0;
            m = (m | 0x00800000) >> ((127 - 6) - e);
        } else if (e > 127 + 7) {
            return (((b & 0x80000000) == 0) ? ((byte) 0x78) : ((byte) 0xf8));
        } else {
            ee = e - 127 + 7;
        }
        int f8 = ((b >> 24) & 0x80) |
                (ee << 3) |
                (m >> 20);
        return ((byte) f8);
    }

    public static void uvec2oct(float[] buf, float x, float y, float z) {
        float m = 1.0f / (Math.abs(x) + Math.abs(y) + Math.abs(z));
        float hx = x * m, hy = y * m;
        if (z >= 0) {
            buf[0] = hx;
            buf[1] = hy;
        } else {
            buf[0] = (1 - Math.abs(hy)) * Math.copySign(1, hx);
            buf[1] = (1 - Math.abs(hx)) * Math.copySign(1, hy);
        }
    }

    public static void oct2uvec(float[] buf, float x, float y) {
        float z = 1 - (Math.abs(x) + Math.abs(y));
        if (z < 0) {
            float xc = x, yc = y;
            x = (1 - Math.abs(yc)) * Math.copySign(1, xc);
            y = (1 - Math.abs(xc)) * Math.copySign(1, yc);
        }
        float f = 1 / (float) Math.sqrt((x * x) + (y * y) + (z * z));
        buf[0] = x * f;
        buf[1] = y * f;
        buf[2] = z * f;
    }

    static char num2hex(int num) {
        if (num < 10)
            return ((char) ('0' + num));
        else
            return ((char) ('A' + num - 10));
    }

    static int hex2num(char hex) {
        if ((hex >= '0') && (hex <= '9'))
            return (hex - '0');
        else if ((hex >= 'a') && (hex <= 'f'))
            return (hex - 'a' + 10);
        else if ((hex >= 'A') && (hex <= 'F'))
            return (hex - 'A' + 10);
        else
            throw (new IllegalArgumentException());
    }

    public static String byte2hex(byte[] in) {
        StringBuilder buf = new StringBuilder();
        for (byte b : in) {
            buf.append(num2hex((b & 0xf0) >> 4));
            buf.append(num2hex(b & 0x0f));
        }
        return (buf.toString());
    }

    public static byte[] hex2byte(String hex) {
        if (hex.length() % 2 != 0)
            throw (new IllegalArgumentException("Invalid hex-encoded string"));
        byte[] ret = new byte[hex.length() / 2];
        for (int i = 0, o = 0; i < hex.length(); i += 2, o++)
            ret[o] = (byte) ((hex2num(hex.charAt(i)) << 4) | hex2num(hex.charAt(i + 1)));
        return (ret);
    }

    private final static String base64set = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private final static int[] base64rev;

    static {
        int[] rev = new int[128];
        for (int i = 0; i < 128; rev[i++] = -1) ;
        for (int i = 0; i < base64set.length(); i++)
            rev[base64set.charAt(i)] = i;
        base64rev = rev;
    }

    public static String base64enc(byte[] in) {
        StringBuilder buf = new StringBuilder();
        int p = 0;
        while (in.length - p >= 3) {
            buf.append(base64set.charAt((in[p + 0] & 0xfc) >> 2));
            buf.append(base64set.charAt(((in[p + 0] & 0x03) << 4) | ((in[p + 1] & 0xf0) >> 4)));
            buf.append(base64set.charAt(((in[p + 1] & 0x0f) << 2) | ((in[p + 2] & 0xc0) >> 6)));
            buf.append(base64set.charAt(in[p + 2] & 0x3f));
            p += 3;
        }
        if (in.length == p + 1) {
            buf.append(base64set.charAt((in[p + 0] & 0xfc) >> 2));
            buf.append(base64set.charAt((in[p + 0] & 0x03) << 4));
            buf.append("==");
        } else if (in.length == p + 2) {
            buf.append(base64set.charAt((in[p + 0] & 0xfc) >> 2));
            buf.append(base64set.charAt(((in[p + 0] & 0x03) << 4) | ((in[p + 1] & 0xf0) >> 4)));
            buf.append(base64set.charAt((in[p + 1] & 0x0f) << 2));
            buf.append("=");
        }
        return (buf.toString());
    }

    public static byte[] base64dec(String in) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int cur = 0, b = 8;
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c >= 128)
                throw (new IllegalArgumentException());
            if (c == '=')
                break;
            int d = base64rev[c];
            if (d == -1)
                throw (new IllegalArgumentException());
            b -= 6;
            if (b <= 0) {
                cur |= d >> -b;
                buf.write(cur);
                b += 8;
                cur = 0;
            }
            cur |= d << b;
        }
        return (buf.toByteArray());
    }

    public static String[] splitwords(String text) {
        ArrayList<String> words = new ArrayList<String>();
        StringBuilder buf = new StringBuilder();
        String st = "ws";
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (st == "ws") {
                if (!Character.isWhitespace(c))
                    st = "word";
                else
                    i++;
            } else if (st == "word") {
                if (c == '"') {
                    st = "quote";
                    i++;
                } else if (c == '\\') {
                    st = "squote";
                    i++;
                } else if (Character.isWhitespace(c)) {
                    words.add(buf.toString());
                    buf = new StringBuilder();
                    st = "ws";
                } else {
                    buf.append(c);
                    i++;
                }
            } else if (st == "quote") {
                if (c == '"') {
                    st = "word";
                    i++;
                } else if (c == '\\') {
                    st = "sqquote";
                    i++;
                } else {
                    buf.append(c);
                    i++;
                }
            } else if (st == "squote") {
                buf.append(c);
                i++;
                st = "word";
            } else if (st == "sqquote") {
                buf.append(c);
                i++;
                st = "quote";
            }
        }
        if (st == "word")
            words.add(buf.toString());
        if ((st != "ws") && (st != "word"))
            return (null);
        return (words.toArray(new String[0]));
    }

    public static String[] splitlines(String text) {
        ArrayList<String> ret = new ArrayList<String>();
        int p = 0;
        while (true) {
            int p2 = text.indexOf('\n', p);
            if (p2 < 0) {
                ret.add(text.substring(p));
                break;
            }
            ret.add(text.substring(p, p2));
            p = p2 + 1;
        }
        return (ret.toArray(new String[0]));
    }

    static int atoi(String a) {
        try {
            return (Integer.parseInt(a));
        } catch (NumberFormatException e) {
            return (0);
        }
    }

    static void readtileof(InputStream in) throws IOException {
        byte[] buf = new byte[4096];
        while (true) {
            if (in.read(buf, 0, buf.length) < 0)
                return;
        }
    }

    public static byte[] readall(InputStream in) throws IOException {
        byte[] buf = new byte[4096];
        int off = 0;
        while (true) {
            if (off == buf.length) {
                byte[] n = new byte[buf.length * 2];
                System.arraycopy(buf, 0, n, 0, buf.length);
                buf = n;
            }
            int ret = in.read(buf, off, buf.length - off);
            if (ret < 0) {
                byte[] n = new byte[off];
                System.arraycopy(buf, 0, n, 0, off);
                return (n);
            }
            off += ret;
        }
    }

    private static void dumptg(ThreadGroup tg, PrintWriter out, int indent) {
        for (int o = 0; o < indent; o++)
            out.print("    ");
        out.println("G: \"" + tg.getName() + "\"");
        Thread[] ths = new Thread[tg.activeCount() * 2];
        ThreadGroup[] tgs = new ThreadGroup[tg.activeGroupCount() * 2];
        int nt = tg.enumerate(ths, false);
        int ng = tg.enumerate(tgs, false);
        for (int i = 0; i < nt; i++) {
            Thread ct = ths[i];
            for (int o = 0; o < indent + 1; o++)
                out.print("    ");
            out.println("T: \"" + ct.getName() + "\"");
        }
        for (int i = 0; i < ng; i++) {
            ThreadGroup cg = tgs[i];
            dumptg(cg, out, indent + 1);
        }
    }

    public static void dumptg(ThreadGroup tg, PrintWriter out) {
        if (tg == null) {
            tg = Thread.currentThread().getThreadGroup();
            while (tg.getParent() != null)
                tg = tg.getParent();
        }
        dumptg(tg, out, 0);
        out.flush();
    }

    public static void dumparr(Object[] arr, PrintStream out, boolean term) {
        out.print('[');
        boolean f = true;
        for (Object o : arr) {
            if (!f) out.print(", ");
            f = false;
            if (o instanceof Object[])
                dumparr((Object[]) o, out, false);
            else
                out.print(o);
        }
        out.print(']');
        if (term) out.println();
    }

    public static void dumparr(int[] arr, PrintStream out, boolean term) {
        if (arr == null) {
            out.print("null");
        } else {
            out.print('[');
            boolean f = true;
            for (int i : arr) {
                if (!f) out.print(", ");
                f = false;
                out.print(i);
            }
            out.print(']');
        }
        if (term) out.println();
    }

    public static void dumparr(short[] arr, PrintStream out, boolean term) {
        if (arr == null) {
            out.print("null");
        } else {
            out.print('[');
            boolean f = true;
            for (int i : arr) {
                if (!f) out.print(", ");
                f = false;
                out.print(i);
            }
            out.print(']');
        }
        if (term) out.println();
    }

    public static void hexdump(byte[] arr, PrintStream out, int width) {
        if (arr == null) {
            out.println("null");
            return;
        }
        if (width <= 0)
            width = 16;
        for (int i = 0; i < arr.length; i += width) {
            out.printf("%08x:\t", i);
            for (int o = 0; (o < width) && (i + o < arr.length); o++) {
                if (o > 0) out.print(' ');
                out.printf("%02x", arr[i + o]);
            }
            out.print('\n');
        }
    }

    public static String titlecase(String str) {
        return (Character.toTitleCase(str.charAt(0)) + str.substring(1));
    }

    public static Color contrast(Color col) {
        int max = Math.max(col.getRed(), Math.max(col.getGreen(), col.getBlue()));
        if (max > 128) {
            return (new Color(col.getRed() / 2, col.getGreen() / 2, col.getBlue() / 2, col.getAlpha()));
        } else if (max == 0) {
            return (Color.WHITE);
        } else {
            int f = 128 / max;
            return (new Color(col.getRed() * f, col.getGreen() * f, col.getBlue() * f, col.getAlpha()));
        }
    }

    public static Color clipcol(int r, int g, int b, int a) {
        if (r < 0) r = 0;
        if (r > 255) r = 255;
        if (g < 0) g = 0;
        if (g > 255) g = 255;
        if (b < 0) b = 0;
        if (b > 255) b = 255;
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (new Color(r, g, b, a));
    }

    public static Color col16(int col) {
        return (new Color(((col & 0xf000) >> 12) * 17,
                ((col & 0x0f00) >> 8) * 17,
                ((col & 0x00f0) >> 4) * 17,
                ((col & 0x000f) >> 0) * 17));
    }

    public static BufferedImage hconcat(final BufferedImage... imgs) {
        int width = 0;
        int height = 0;
        for (final BufferedImage img : imgs) {
            width += img.getWidth();
            height = Math.max(height, img.getHeight());
        }

        final BufferedImage img = TexI.mkbuf(new Coord(width, height));
        final Graphics g = img.createGraphics();
        int x = 0;
        for (final BufferedImage i : imgs) {
            g.drawImage(i, x, 0, null);
            x += i.getWidth();
        }
        g.dispose();
        return img;
    }

    public static BufferedImage outline(BufferedImage img, Color col) {
        Coord sz = imgsz(img).add(2, 2);
        BufferedImage ol = TexI.mkbuf(sz);
        Object fcol = ol.getColorModel().getDataElements(col.getRGB(), null);
        Raster src = img.getRaster();
        WritableRaster dst = ol.getRaster();
        for (int y = 0; y < sz.y; y++) {
            for (int x = 0; x < sz.x; x++) {
                boolean t;
                if ((y == 0) || (x == 0) || (y == sz.y - 1) || (x == sz.x - 1)) {
                    t = true;
                } else {
                    t = src.getSample(x - 1, y - 1, 3) < 250;
                }
                if (!t)
                    continue;
                if (((x > 1) && (y > 0) && (y < sz.y - 1) && (src.getSample(x - 2, y - 1, 3) >= 250)) ||
                        ((x > 0) && (y > 1) && (x < sz.x - 1) && (src.getSample(x - 1, y - 2, 3) >= 250)) ||
                        ((x < sz.x - 2) && (y > 0) && (y < sz.y - 1) && (src.getSample(x, y - 1, 3) >= 250)) ||
                        ((x > 0) && (y < sz.y - 2) && (x < sz.x - 1) && (src.getSample(x - 1, y, 3) >= 250)))
                    dst.setDataElements(x, y, fcol);
            }
        }
        return (ol);
    }

    public static BufferedImage outline2(BufferedImage img, Color col) {
        return outline2(img, col, false);
    }

    public static BufferedImage outline2(BufferedImage img, Color col, boolean thick) {
        BufferedImage ol = outline(img, col);
        Graphics g = ol.getGraphics();
        g.drawImage(img, 1, 1, null);
        g.dispose();
        return (ol);
    }

    public static int floordiv(int a, int b) {
        if (a < 0)
            return (((a + 1) / b) - 1);
        else
            return (a / b);
    }

    public static int floormod(int a, int b) {
        int r = a % b;
        if (r < 0)
            r += b;
        return (r);
    }

    /* XXX: These are not actually correct, since an exact integer
     * will round downwards, but I don't actually expect that to be a
     * problem given how I use these, and it turns out that
     * java.lang.Math.floor is actually surprisingly slow (it
     * delegates to StrictMath.floor for some reason). */
    public static int floordiv(float a, float b) {
        float q = a / b;
        return ((q < 0) ? (((int) q) - 1) : ((int) q));
    }

    public static int floordiv(double a, double b) {
        double q = a / b;
        return ((q < 0) ? (((int) q) - 1) : ((int) q));
    }

    public static float floormod(float a, float b) {
        float r = a % b;
        return ((a < 0) ? (r + b) : r);
    }

    public static double floormod(double a, double b) {
        double r = a % b;
        return ((a < 0) ? (r + b) : r);
    }

    public static double cangle(double a) {
        while (a > Math.PI)
            a -= Math.PI * 2;
        while (a < -Math.PI)
            a += Math.PI * 2;
        return (a);
    }

    public static double cangle2(double a) {
        while (a > Math.PI * 2)
            a -= Math.PI * 2;
        while (a < 0)
            a += Math.PI * 2;
        return (a);
    }

    public static double clip(double d, double min, double max) {
        if (d < min)
            return (min);
        if (d > max)
            return (max);
        return (d);
    }

    public static float clip(float d, float min, float max) {
        if (d < min)
            return (min);
        if (d > max)
            return (max);
        return (d);
    }

    public static int clip(int i, int min, int max) {
        if (i < min)
            return (min);
        if (i > max)
            return (max);
        return (i);
    }

    public static double clipnorm(double d, double min, double max) {
        if (d < min)
            return (0.0);
        if (d > max)
            return (1.0);
        return ((d - min) / (max - min));
    }

    public static double smoothstep(double d) {
        return (d * d * (3 - (2 * d)));
    }

    public static Color blendcol(Color in, Color bl) {
        int f1 = bl.getAlpha();
        int f2 = 255 - bl.getAlpha();
        return (new Color(((in.getRed() * f2) + (bl.getRed() * f1)) / 255,
                ((in.getGreen() * f2) + (bl.getGreen() * f1)) / 255,
                ((in.getBlue() * f2) + (bl.getBlue() * f1)) / 255,
                in.getAlpha()));
    }

    public static Color blendcol(Color x, Color y, double a) {
        int f1 = (int) (a * 255), f2 = 255 - f1;
        return (new Color(((x.getRed() * f2) + (y.getRed() * f1)) / 255,
                ((x.getGreen() * f2) + (y.getGreen() * f1)) / 255,
                ((x.getBlue() * f2) + (y.getBlue() * f1)) / 255,
                ((x.getAlpha() * f2) + (y.getAlpha() * f1)) / 255));
    }

    public static Color preblend(Color c1, Color c2) {
        double a1 = c1.getAlpha() / 255.0;
        double a2 = c2.getAlpha() / 255.0;
        /* I can't help but feel that this should be possible to
         * express in some simpler form, but I can't see how. */
        double ac = a1 + a2 - (a1 * a2);
        return (new Color((int) Math.round((((c2.getRed() * a2) - (c1.getRed() * a2)) / ac) + c1.getRed()),
                (int) Math.round((((c2.getGreen() * a2) - (c1.getGreen() * a2)) / ac) + c1.getGreen()),
                (int) Math.round((((c2.getBlue() * a2) - (c1.getBlue() * a2)) / ac) + c1.getBlue()),
                (int) Math.round(ac * 255)));
    }

    public static void serialize(Object obj, OutputStream out) throws IOException {
        ObjectOutputStream oout = new ObjectOutputStream(out);
        oout.writeObject(obj);
        oout.flush();
    }

    public static byte[] serialize(Object obj) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            serialize(obj, out);
        } catch (IOException e) {
            throw (new RuntimeException(e));
        }
        return (out.toByteArray());
    }

    public static Object deserialize(InputStream in) throws IOException {
        ObjectInputStream oin = new ObjectInputStream(in);
        try {
            return (oin.readObject());
        } catch (ClassNotFoundException e) {
            return (null);
        }
    }

    public static Object deserialize(byte[] buf) {
        if (buf == null)
            return (null);
        InputStream in = new ByteArrayInputStream(buf);
        try {
            return (deserialize(in));
        } catch (IOException e) {
            return (null);
        }
    }

    public static boolean parsebool(String s) {
        if (s == null)
            throw (new IllegalArgumentException(s));
        else if (s.equalsIgnoreCase("1") || s.equalsIgnoreCase("on") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes"))
            return (true);
        else if (s.equalsIgnoreCase("0") || s.equalsIgnoreCase("off") || s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no"))
            return (false);
        throw (new IllegalArgumentException(s));
    }

    public static boolean eq(Object a, Object b) {
        return ((a == b) || ((a != null) && a.equals(b)));
    }

    public static boolean parsebool(String s, boolean def) {
        try {
            return (parsebool(s));
        } catch (IllegalArgumentException e) {
            return (def);
        }
    }

    /* Just in case anyone doubted that Java is stupid. :-/ */
    public static FloatBuffer bufcp(float[] a) {
        FloatBuffer b = mkfbuf(a.length);
        b.put(a);
        b.rewind();
        return (b);
    }

    public static ShortBuffer bufcp(short[] a) {
        ShortBuffer b = mksbuf(a.length);
        b.put(a);
        b.rewind();
        return (b);
    }

    public static FloatBuffer bufcp(FloatBuffer a) {
        a.rewind();
        FloatBuffer ret = mkfbuf(a.remaining());
        ret.put(a).rewind();
        return (ret);
    }

    public static IntBuffer bufcp(IntBuffer a) {
        a.rewind();
        IntBuffer ret = mkibuf(a.remaining());
        ret.put(a).rewind();
        return (ret);
    }

    public static ByteBuffer mkbbuf(int n) {
        try {
            return (ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder()));
        } catch (OutOfMemoryError e) {
            /* At least Sun's class library doesn't try to collect
             * garbage if it's out of direct memory, which is pretty
             * stupid. So do it for it, then. */
            System.gc();
            return (ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder()));
        }
    }

    public static FloatBuffer mkfbuf(int n) {
        return (mkbbuf(n * 4).asFloatBuffer());
    }

    public static ShortBuffer mksbuf(int n) {
        return (mkbbuf(n * 2).asShortBuffer());
    }

    public static IntBuffer mkibuf(int n) {
        return (mkbbuf(n * 4).asIntBuffer());
    }

    /*
    public static ByteBuffer wbbuf(int n) {
	return(mkbbuf(n));
    }
    public static IntBuffer wibuf(int n) {
	return(mkibuf(n));
    }
    public static FloatBuffer wfbuf(int n) {
	return(mkfbuf(n));
    }
    public static ShortBuffer wsbuf(int n) {
	return(mksbuf(n));
    }
    */
    public static ByteBuffer wbbuf(int n) {
        return (ByteBuffer.wrap(new byte[n]));
    }

    public static IntBuffer wibuf(int n) {
        return (IntBuffer.wrap(new int[n]));
    }

    public static FloatBuffer wfbuf(int n) {
        return (FloatBuffer.wrap(new float[n]));
    }

    public static ShortBuffer wsbuf(int n) {
        return (ShortBuffer.wrap(new short[n]));
    }

    public static FloatBuffer wbufcp(FloatBuffer a) {
        a.rewind();
        FloatBuffer ret = wfbuf(a.remaining());
        ret.put(a).rewind();
        return (ret);
    }

    public static IntBuffer wbufcp(IntBuffer a) {
        a.rewind();
        IntBuffer ret = wibuf(a.remaining());
        ret.put(a).rewind();
        return (ret);
    }

    public static ByteBuffer growbuf(ByteBuffer buf, int req) {
        if (buf.remaining() >= req)
            return (buf);
        int sz = buf.capacity();
        while (sz - buf.position() < req)
            sz <<= 1;
        return (ByteBuffer.allocate(sz).order(buf.order()).put((ByteBuffer) buf.flip()));
    }

    public static float[] c2fa(Color c) {
        return (new float[]{
                ((float) c.getRed() / 255.0f),
                ((float) c.getGreen() / 255.0f),
                ((float) c.getBlue() / 255.0f),
                ((float) c.getAlpha() / 255.0f)
        });
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] mkarray(Class<T> cl, int len) {
        return ((T[]) Array.newInstance(cl, len));
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] splice(T[] src, int off, int len) {
        T[] dst = (T[]) Array.newInstance(src.getClass().getComponentType(), len);
        System.arraycopy(src, off, dst, 0, len);
        return (dst);
    }

    public static <T> T[] splice(T[] src, int off) {
        return (splice(src, off, src.length - off));
    }

    public static byte[] splice(byte[] src, int off, int len) {
        byte[] dst = new byte[len];
        System.arraycopy(src, off, dst, 0, len);
        return (dst);
    }

    public static byte[] splice(byte[] src, int off) {
        return (splice(src, off, src.length - off));
    }

    public static float[] splice(float[] src, int off, int len) {
        float[] dst = new float[len];
        System.arraycopy(src, off, dst, 0, len);
        return (dst);
    }

    public static float[] splice(float[] src, int off) {
        return (splice(src, off, src.length - off));
    }

    public static double[] splice(double[] src, int off, int len) {
        double[] dst = new double[len];
        System.arraycopy(src, off, dst, 0, len);
        return (dst);
    }

    public static double[] splice(double[] src, int off) {
        return (splice(src, off, src.length - off));
    }

    public static int[] splice(int[] src, int off, int len) {
        int[] dst = new int[len];
        System.arraycopy(src, off, dst, 0, len);
        return (dst);
    }

    public static int[] splice(int[] src, int off) {
        return (splice(src, off, src.length - off));
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] extend(T[] src, int off, int nl) {
        T[] dst = (T[]) Array.newInstance(src.getClass().getComponentType(), nl);
        System.arraycopy(src, off, dst, 0, Math.min(src.length - off, dst.length));
        return (dst);
    }

    public static <T> T[] extend(T[] src, int nl) {
        return (extend(src, 0, nl));
    }

    public static <T, E extends T> T[] extend(T[] src, E ne) {
        T[] ret = extend(src, 0, src.length + 1);
        ret[src.length] = ne;
        return (ret);
    }

    public static <T, E extends T> T[] extend(T[] src, E[] ne) {
        T[] ret = extend(src, 0, src.length + ne.length);
        System.arraycopy(ne, 0, ret, src.length, ne.length);
        return (ret);
    }

    public static int[] extend(int[] src, int nl) {
        int[] dst = new int[nl];
        System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));
        return (dst);
    }

    public static double[] extend(double[] src, int nl) {
        double[] dst = new double[nl];
        System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));
        return (dst);
    }

    public static float[] extend(float[] src, int nl) {
        float[] dst = new float[nl];
        System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));
        return (dst);
    }

    public static short[] extend(short[] src, int nl) {
        short[] dst = new short[nl];
        System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));
        return (dst);
    }

    public static <T> T el(Iterable<T> c) {
        Iterator<T> i = c.iterator();
        if (!i.hasNext()) return (null);
        return (i.next());
    }

    public static <T> T take(Iterable<T> c) {
        Iterator<T> i = c.iterator();
        if (!i.hasNext()) return (null);
        T ret = i.next();
        i.remove();
        return (ret);
    }

    public static <T> int index(T[] arr, T el) {
        for (int i = 0; i < arr.length; i++) {
            if (Objects.equals(arr[i], el))
                return (i);
        }
        return (-1);
    }

    public static boolean strcheck(String str, IntPredicate p) {
        for (int i = 0; i < str.length(); i++) {
            if (!p.test(str.charAt(i)))
                return (false);
        }
        return (true);
    }

    public static <T> T find(Iterable<? extends T> in, Predicate<? super T> p) {
        for (T obj : in) {
            if (p.test(obj))
                return (obj);
        }
        return (null);
    }

    @SafeVarargs
    public static <T> T or(Supplier<T>... vals) {
        for (Supplier<T> val : vals) {
            T ret = val.get();
            if (ret != null)
                return (ret);
        }
        return (null);
    }

    public static <T> void clean(Collection<T> c, Consumer<? super T> clean) {
        for (T item : c)
            clean.accept(item);
        c.clear();
    }

    public static <T> T construct(Constructor<T> cons, Object... args) {
        try {
            return (cons.newInstance(args));
        } catch (InstantiationException e) {
            throw (new RuntimeException(e));
        } catch (IllegalAccessException e) {
            throw (new RuntimeException(e));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException)
                throw ((RuntimeException) e.getCause());
            throw (new RuntimeException(e.getCause()));
        }
    }

    public static Object invoke(Method mth, Object ob, Object... args) {
        try {
            return (mth.invoke(ob, args));
        } catch (IllegalAccessException e) {
            throw (new RuntimeException(e));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException)
                throw ((RuntimeException) e.getCause());
            throw (new RuntimeException(e.getCause()));
        }
    }

    public static <R> Function<Object[], R> smthfun(Class<?> cl, String name, Class<R> rtype, Class<?>... args) throws NoSuchMethodException {
        Method mth = cl.getDeclaredMethod(name, args);
        if (!rtype.isAssignableFrom(mth.getReturnType()))
            throw (new NoSuchMethodException("unexpected return type: " + mth.getReturnType()));
        int mod = mth.getModifiers();
        if (((mod & Modifier.STATIC) == 0) || ((mod & Modifier.PUBLIC) == 0))
            throw (new NoSuchMethodException("expected public static method"));
        return (iargs -> rtype.cast(invoke(mth, null, iargs)));
    }

    public static String urlencode(String in) {
        StringBuilder buf = new StringBuilder();
        byte[] enc;
        try {
            enc = in.getBytes("utf-8");
        } catch (java.io.UnsupportedEncodingException e) {
            /* ¦] */
            throw (new Error(e));
        }
        for (byte c : enc) {
            if (((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z')) ||
                    ((c >= '0') && (c <= '9')) || (c == '.')) {
                buf.append((char) c);
            } else {
                buf.append("%" + Utils.num2hex((c & 0xf0) >> 4) + Utils.num2hex(c & 0x0f));
            }
        }
        return (buf.toString());
    }

    public static URL urlparam(URL base, String... pars) {
        /* Why is Java so horribly bad? */
        String file = base.getFile();
        int p = file.indexOf('?');
        StringBuilder buf = new StringBuilder();
        if (p >= 0) {
            /* For now, only add; don't augment. Since Java sucks. */
            buf.append('&');
        } else {
            buf.append('?');
        }
        for (int i = 0; i < pars.length; i += 2) {
            if (i > 0)
                buf.append('&');
            buf.append(urlencode(pars[i]));
            buf.append('=');
            buf.append(urlencode(pars[i + 1]));
        }
        try {
            return (new URL(base.getProtocol(), base.getHost(), base.getPort(), file + buf.toString()));
        } catch (java.net.MalformedURLException e) {
            throw (new RuntimeException(e));
        }
    }

    public static <C> C hascause(Throwable t, Class<C> c) {
        while (t != null) {
            if (c.isInstance(t))
                return (c.cast(t));
            t = t.getCause();
        }
        return (null);
    }

    private final static Map<Character, Character> az2qwmap = new HashMap<Character, Character>(12) {{
        put('&', '1');
        put('é', '2');
        put('"', '3');
        put('\'', '4');
        put('(', '5');
        put('-', '6');
        put('è', '7');
        put('_', '8');
        put('ç', '9');
        put('à', '0');
        put('a', 'q');
        put('z', 'w');
    }};

    public static char azerty2qwerty(char az) {
        return az2qwmap.containsKey(az) ? az2qwmap.get(az) : az;
    }

    // Windows only
    public static Long getScancode(final KeyEvent keyEvent) {
        try {
            Field field = KeyEvent.class.getDeclaredField("scancode");
            field.setAccessible(true);
            return field.getLong(keyEvent);
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
            return null;
        }
    }

    public static double ntime() {
        return (System.currentTimeMillis() / 1e3);
    }

    private static final long rtimeoff = System.nanoTime();

    public static double rtime() {
        return ((System.nanoTime() - rtimeoff) / 1e9);
    }

    public static class MapBuilder<K, V> {
        private final Map<K, V> bk;

        public MapBuilder(Map<K, V> bk) {
            this.bk = bk;
        }

        public MapBuilder<K, V> put(K k, V v) {
            bk.put(k, v);
            return (this);
        }

        public Map<K, V> map() {
            return (Collections.unmodifiableMap(bk));
        }
    }

    public static <T> Indir<T> cache(Indir<T> src) {
        return (new Indir<T>() {
            private T val;
            private boolean has = false;

            public T get() {
                if (!has) {
                    val = src.get();
                    has = true;
                }
                return (val);
            }
        });
    }

    public static <K, V> MapBuilder<K, V> map() {
        return (new MapBuilder<K, V>(new HashMap<K, V>()));
    }

    public static <F, T> Iterator<T> map(Iterator<F> from, Function<F, T> fn) {
        return (new Iterator<T>() {
            boolean h = false;
            T n;

            public boolean hasNext() {
                if (h)
                    return (true);
                if (!from.hasNext())
                    return (false);
                n = fn.apply(from.next());
                h = true;
                return (true);
            }

            public T next() {
                if (!hasNext())
                    throw (new NoSuchElementException());
                T ret = n;
                h = false;
                n = null;
                return (ret);
            }

            public void remove() {
                from.remove();
            }
        });
    }

    public static <E> Iterator<E> filter(Iterator<E> from, Predicate<E> filter) {
        return (new Iterator<E>() {
            boolean h = false;
            E n;

            public boolean hasNext() {
                while (!h) {
                    if (!from.hasNext())
                        return (false);
                    E g = from.next();
                    if (filter.test(g)) {
                        n = g;
                        h = true;
                        break;
                    }
                }
                return (true);
            }

            public E next() {
                if (!hasNext())
                    throw (new NoSuchElementException());
                E ret = n;
                h = false;
                n = null;
                return (ret);
            }

            public void remove() {
                from.remove();
            }
        });
    }

    public static void checkirq() throws InterruptedException {
        if (Thread.interrupted())
            throw (new InterruptedException());
    }

    public static <T, F> Iterator<T> filter(Iterator<F> from, Class<T> filter) {
        return (map(filter(from, filter::isInstance), filter::cast));
    }

    public static <E, T extends Collection<E>> T merge(T dst, Iterable<? extends E> a, Iterable<? extends E> b, Comparator<? super E> cmp) {
        Iterator<? extends E> i = a.iterator(), o = b.iterator();
        if (i.hasNext() && o.hasNext()) {
            E e = i.next(), f = o.next();
            while (true) {
                if (cmp.compare(e, f) <= 0) {
                    dst.add(e);
                    if (i.hasNext()) {
                        e = i.next();
                    } else {
                        dst.add(f);
                        break;
                    }
                } else {
                    dst.add(f);
                    if (o.hasNext()) {
                        f = o.next();
                    } else {
                        dst.add(e);
                        break;
                    }
                }
            }
        }
        while (i.hasNext())
            dst.add(i.next());
        while (o.hasNext())
            dst.add(o.next());
        return (dst);
    }

    public static final Comparator<Object> idcmp = new Comparator<Object>() {
        int eid = 0;
        final Map<Ref, Long> emerg = new HashMap<Ref, Long>();
        final ReferenceQueue<Object> cleanq = new ReferenceQueue<Object>();

        class Ref extends WeakReference<Object> {
            final int h;

            Ref(Object o, ReferenceQueue<Object> queue) {
                super(o, queue);
                this.h = System.identityHashCode(o);
            }

            public int hashCode() {
                return (h);
            }

            public boolean equals(Object o) {
                if (o == this)
                    return (true);
                if (!(o instanceof Ref))
                    return (false);
                Object or = ((Ref) o).get();
                Object sr = get();
                return ((or != null) && (sr != null) && (or == sr));
            }
        }

        private void clean() {
            Reference<? extends Object> ref;
            while ((ref = cleanq.poll()) != null)
                emerg.remove(ref);
        }

        public int compare(Object a, Object b) {
            if (a == b)
                return (0);
            if (a == null)
                return (1);
            if (b == null)
                return (-1);
            int ah = System.identityHashCode(a);
            int bh = System.identityHashCode(b);
            if (ah < bh)
                return (-1);
            else if (ah > bh)
                return (1);

            synchronized (emerg) {
                if (eid == 0)
                    System.err.println("could not impose ordering in idcmd, using slow-path");
                clean();
                Ref ar = new Ref(a, cleanq), br = new Ref(b, cleanq);
                Long ai, bi;
                if ((ai = emerg.get(ar)) == null)
                    emerg.put(ar, ai = ((long) ah << 32) | (((long) eid++) & 0xffffffffl));
                if ((bi = emerg.get(br)) == null)
                    emerg.put(br, bi = ((long) ah << 32) | (((long) eid++) & 0xffffffffl));
                if (ai < bi)
                    return (-1);
                else if (ai > bi)
                    return (1);
                throw (new RuntimeException("Comparison identity crisis"));
            }
        }
    };

    static {
        Console.setscmd("threads", (cons, args) -> Utils.dumptg(null, cons.out));
        Console.setscmd("gc", (cons, args) -> System.gc());
    }

    // NOTE: will not work with values having large integer part
    public static String fmt1DecPlace(double value) {
        double rvalue = (double) Math.round(value * 10) / 10;
        return (rvalue % 1 == 0) ? Integer.toString((int) rvalue) : Double.toString(rvalue);
    }

    public static Color hex2rgb(String clrhex) {
        try {
            return new Color(
                    Integer.valueOf(clrhex.substring(0, 2), 16),
                    Integer.valueOf(clrhex.substring(2, 4), 16),
                    Integer.valueOf(clrhex.substring(4, 6), 16),
                    255);
        } catch (NumberFormatException e) {
        }
        return null;
    }

    public static double round(double a, int order) {
        double o = Math.pow(10, order);
        return Math.round(o * a) / o;
    }

    public static String timeLeft(long at) {
        long t = at - System.currentTimeMillis();
        if (t < 0) return "Finishing...";
        long hours = t / 3600000;
        long mins = t / 60000 % 60;
        long seconds = t / 1000 % 60;
        return String.format("%02d:%02d:%02d", hours, mins, seconds);
    }
}

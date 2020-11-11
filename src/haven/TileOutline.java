package haven;

import javax.media.opengl.GL2;
import java.nio.BufferOverflowException;
import java.nio.FloatBuffer;

import static haven.MCache.tilesz;

public class TileOutline implements Rendered {
    private final MapView mapView;
    private final MCache map;
    private final FloatBuffer[] vertexBuffers;
    private int area;
    public static States.ColState color = new States.ColState(
            DefSettings.GUIDESCOLOR.get().getRed(),
            DefSettings.GUIDESCOLOR.get().getGreen(),
            DefSettings.GUIDESCOLOR.get().getBlue(),
            (int) (DefSettings.GUIDESCOLOR.get().getAlpha() * 0.7)
    );
    private Location location;
    private Coord ul;
    private int curIndex;


    public TileOutline(MapView mapView) {
        this.mapView = mapView;
        this.map = mapView.glob.map;
        this.area = (MCache.cutsz.x * 5) * (MCache.cutsz.y * 5) * (2 * mapView.view);
        this.color = new States.ColState(255, 255, 255, 64);
        // double-buffer to prevent flickering
        vertexBuffers = new FloatBuffer[2];
        vertexBuffers[0] = Utils.mkfbuf(this.area * 3 * 4);
        vertexBuffers[1] = Utils.mkfbuf(this.area * 3 * 4);
        curIndex = 0;
    }

    @Override
    public void draw(GOut g) {
        g.apply();
        BGL gl = g.gl;
        FloatBuffer vbuf = getCurrentBuffer();
        vbuf.rewind();
        gl.glLineWidth(1.0F);
        gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
        gl.glVertexPointer(3, GL2.GL_FLOAT, 0, vbuf);
        gl.glDrawArrays(GL2.GL_LINES, 0, area * 4);
        gl.glDisableClientState(GL2.GL_VERTEX_ARRAY);
    }

    @Override
    public boolean setup(RenderList rl) {
        rl.prepo(location);
        rl.prepo(States.ndepthtest);
        rl.prepo(last);
        rl.prepo(color);
        return true;
    }

    public void update(Coord ul) {
        try {
            this.ul = ul;
            this.location = Location.xlate(new Coord3f((float) (ul.x * tilesz.x), (float) (-ul.y * tilesz.y), 0.0F));
            this.area = (MCache.cutsz.x * (mapView.view * 2 + 1)) * (MCache.cutsz.y * (mapView.view * 2 + 1));
            vertexBuffers[0] = Utils.mkfbuf(this.area * 3 * 4);
            vertexBuffers[1] = Utils.mkfbuf(this.area * 3 * 4);
            curIndex = (curIndex + 1) % 2; // swap buffers
            Coord c = new Coord();
            Coord size = ul.add(MCache.cutsz.mul(mapView.view * 2 + 1)); //75(1)(25*3) 125(2)(25*5) 175(3)(25*7) 225(4)(25*9) 275(5)(25*11)
            for (c.y = ul.y; c.y < size.y; c.y++)
                for (c.x = ul.x; c.x < size.x; c.x++)
                    addLineStrip(mapToScreen(c), mapToScreen(c.add(1, 0)), mapToScreen(c.add(1, 1)));
        } catch (Loading e) {
        }
    }

    private Coord3f mapToScreen(Coord c) {
        return new Coord3f((float) ((c.x - ul.x) * tilesz.x), (float) (-(c.y - ul.y) * tilesz.y), Config.disableelev ? 0 : map.getz_safe(c));
    }

    private void addLineStrip(Coord3f... vertices) {
        try {
            FloatBuffer vbuf = getCurrentBuffer();
            for (int i = 0; i < vertices.length - 1; i++) {
                Coord3f a = vertices[i];
                Coord3f b = vertices[i + 1];
                vbuf.put(a.x).put(a.y).put(a.z);
                vbuf.put(b.x).put(b.y).put(b.z);
            }
        } catch (BufferOverflowException boe) {
        }
    }

    private FloatBuffer getCurrentBuffer() {
        return vertexBuffers[curIndex];
    }
}

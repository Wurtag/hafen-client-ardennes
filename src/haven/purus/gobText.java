package haven.purus;

import haven.Camera;
import haven.Coord;
import haven.GLState;
import haven.GOut;
import haven.Location;
import haven.Matrix4f;
import haven.PView;
import haven.Projection;
import haven.RenderList;
import haven.Sprite;
import haven.Tex;
import haven.Text;

import java.awt.Color;

public class gobText extends Sprite {
    // Text custom text above gob

    private Tex tex;
    private static Matrix4f mv = new Matrix4f();
    private Projection proj;
    private Coord wndsz;
    private Location.Chain loc;
    private Camera camp;
    private int height;

    public gobText(String text, int height) {
        super(null, null);
        update(text);
        this.height = height;
    }

    public void draw(GOut g) {
        float[] c = mv.load(camp.fin(Matrix4f.id)).mul1(loc.fin(Matrix4f.id)).homoc();
        Coord sc = proj.get2dCoord(c, wndsz);
        sc.x -= tex.sz().x / 2;
        sc.y -= height; // 10 default?
        g.image(tex, sc);
    }

    public boolean setup(RenderList rl) {
        rl.prepo(last);
        GLState.Buffer buf = rl.state();
        proj = buf.get(PView.proj);
        wndsz = buf.get(PView.wnd).sz();
        loc = buf.get(PView.loc);
        camp = buf.get(PView.cam);
        return true;
    }

    public void update(String text) {
        String str = text;
        tex = Text.render(text, Color.white).tex();
    }

    public Object staticp() {
        return CONSTANS;
    }

}

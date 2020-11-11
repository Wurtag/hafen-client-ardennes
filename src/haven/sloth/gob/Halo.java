package haven.sloth.gob;

import haven.Composite;
import haven.DefSettings;
import haven.Drawable;
import haven.GAttrib;
import haven.Gob;
import haven.RenderList;
import haven.ResData;
import haven.sloth.gfx.GobDirMesh;

public class Halo extends GAttrib implements Rendered {
    private final GobDirMesh mesh;
    private boolean show;

    public Halo(final Gob g) {
        super(g);
        mesh = GobDirMesh.getmesh();
        show = false;
    }

    public void setup(RenderList rl) {
        if (show) {
            rl.add(mesh, null);
        }
    }

    private boolean isHearthingOrKnocked() {
        Drawable d = gob.getattr(Drawable.class);
        if (d instanceof Composite) {
            Composite comp = (Composite) d;

            if (comp.oldposes != null) {
                for (ResData res : comp.oldposes) {
                    final String nm = gob.rnm(res.res);
                    if (nm.equals("gfx/borka/knock") || nm.equals("gfx/borka/pointhome")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (DefSettings.SHOWHALO.get()) {
            show = true;
        } else if (DefSettings.SHOWHALOONHEARTH.get()) {
            show = isHearthingOrKnocked();
        } else {
            show = false;
        }
    }
}

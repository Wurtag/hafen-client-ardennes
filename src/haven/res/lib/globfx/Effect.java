package haven.res.lib.globfx;

import haven.Rendered;

public interface Effect extends Rendered {
    public boolean tick(float dt);
}
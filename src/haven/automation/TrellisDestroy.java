package haven.automation;


import haven.Coord;
import haven.Coord2d;
import haven.GameUI;
import haven.Gob;
import haven.Loading;
import haven.Resource;

import java.util.HashSet;
import java.util.Set;

import static haven.OCache.posres;

public class TrellisDestroy implements Runnable {
    private GameUI gui;
    private Set<String> plants = new HashSet<>(5);

    public TrellisDestroy(GameUI gui) {
        this.gui = gui;
        plants.add("gfx/terobjs/plants/wine");
        plants.add("gfx/terobjs/plants/pepper");
        plants.add("gfx/terobjs/plants/hops");
        plants.add("gfx/terobjs/plants/peas");
        plants.add("gfx/terobjs/plants/cucumber");
    }

    @Override
    public void run() {
        Gob plant = null;
        synchronized (gui.map.glob.oc) {
            for (Gob gob : gui.map.glob.oc) {
                try {
                    Resource res = gob.getres();
                    if (res != null && plants.contains(res.name)) {
                        Coord2d plc = gui.map.player().rc;
                        if ((plant == null || gob.rc.dist(plc) < plant.rc.dist(plc)))
                            plant = gob;
                    }
                } catch (Loading l) {
                }
            }
        }

        if (plant == null)
            return;

        gui.act("destroy");
        gui.map.wdgmsg("click", plant.sc, plant.rc.floor(posres), 1, 0, 0, (int) plant.id, plant.rc.floor(posres), 0, -1);
        gui.map.wdgmsg("click", Coord.z, Coord.z, 3, 0);
    }
}

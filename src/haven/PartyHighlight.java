package haven;


import java.awt.Color;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class PartyHighlight {
    public static final Color PLAYER_OL_COLOR = new Color(255, 255, 255, 255);
    public static final Color MEMBER_OL_COLOR = new Color(0, 255, 0, 255);

    private final Party party;
    private final long playerId;
    private final Map<Gob, Gob.Overlay> overlays;

    public PartyHighlight(Party party, long playerId) {
        this.party = party;
        this.playerId = playerId;
        this.overlays = new HashMap<Gob, Gob.Overlay>();
    }

    public void update() {
        Collection<Gob> old = new HashSet<Gob>(overlays.keySet());
        if (party.memb.size() > 0) {
            for (Party.Member m : party.memb.values()) {
                Gob gob = m.getgob();
                if (gob == null)
                    continue;
                if (Config.partycircles && m.gobid != playerId)
                    highlight(gob, MEMBER_OL_COLOR);
                else if (Config.playercircle && m.gobid == playerId)
                    highlight(gob, PLAYER_OL_COLOR);
                else
                    unhighlight(gob);
                old.remove(gob);
            }
        }
        for (Gob gob : old)
            unhighlight(gob);
    }

    private void highlight(Gob gob, Color color) {
        if (overlays.containsKey(gob))
            return;
        Gob.Overlay overlay = new Gob.Overlay(new PartyMemberOutline(gob, color));
        gob.ols.add(overlay);
        overlays.put(gob, overlay);
    }

    private void unhighlight(Gob gob) {
        Gob.Overlay overlay = overlays.remove(gob);
        if (overlay != null)
            gob.ols.remove(overlay);
    }
}
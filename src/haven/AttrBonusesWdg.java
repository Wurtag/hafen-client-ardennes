package haven;


import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

public class AttrBonusesWdg extends Widget implements ItemInfo.Owner {
    private static final Coord bonusc = new Coord(0, 20);
    private static final OwnerContext.ClassResolver<AttrBonusesWdg> ctxr = new ClassResolver<AttrBonusesWdg>()
            .add(Glob.class, wdg -> wdg.ui.sess.glob)
            .add(Session.class, wdg -> wdg.ui.sess);
    private final Scrollbar bar;

    private boolean needUpdate = false;
    private boolean needBuild = false;
    private boolean needRedraw = false;

    private WItem[] items;
    private Map<Resource, Integer> bonuses;
    private List<ItemInfo> info = null;
    private BufferedImage tip = null;

    private CharWnd charWnd = null;

    public AttrBonusesWdg(int y) {
        super(new Coord(175, y));
        add(new Label("Equipment bonuses:"));
        bar = adda(new Scrollbar(y - bonusc.y, 0, 0), sz.x, bonusc.y, 1, 0);
    }

    @Override
    public boolean mousewheel(Coord c, int amount) {
        bar.ch(15 * amount);
        return true;
    }

    public void update(WItem[] items) {
        this.items = items;
        needUpdate = true;
    }

    @Override
    public void draw(GOut g) {
        if (needRedraw) {
            render();
        }

        if (tip != null) {
            Coord c = Coord.z;
            if (bar.visible) {
                c = c.sub(0, bar.val);
            }
            g.reclip(bonusc, sz).image(tip, c);
        }
        super.draw(g);
    }

    private void render() {
        try {
            if (info != null && !info.isEmpty()) {
                tip = ItemInfo.longtip(info);
            } else {
                tip = null;
            }

            int delta = tip != null ? tip.getHeight() : 0;
            bar.visible = delta > bar.sz.y;
            bar.max = delta - bar.sz.y;
            bar.ch(0);

            needRedraw = false;
        } catch (Loading ignored) {
        }
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (needUpdate) {
            doUpdate();
        }
        if (charWnd == null) {
            charWnd = ui.gui.chrwdg;
            if (charWnd != null) {
                needBuild = true;
            }
        }
        if (needBuild) {
            build();
        }
    }

    private void doUpdate() {
        try {
            bonuses = Arrays.stream(items)
                    .filter(Objects::nonNull)
                    .map(wItem -> wItem.item)
                    .distinct()
                    .map(GItem::info)
                    .map(ItemInfo::getBonuses)
                    .map(Map::entrySet)
                    .flatMap(Collection::stream)
                    .collect(
                            Collectors.toMap(
                                    Entry::getKey,
                                    Entry::getValue,
                                    Integer::sum
                            )
                    );
            needUpdate = false;
            needBuild = true;
        } catch (Loading ignored) {
        }
    }

    private void build() {
        try {
            if (bonuses != null) {
                ItemInfo compiled = make(bonuses.entrySet()
                        .stream()
                        .sorted(this::BY_PRIORITY)
                        .collect(Collectors.toList())
                );
                info = compiled != null ? Collections.singletonList(compiled) : null;
            }

            needBuild = false;
            needRedraw = true;
        } catch (Loading ignored) {
        }
    }

    private ItemInfo make(Collection<Entry<Resource, Integer>> mods) {
        if (mods.isEmpty()) {
            return null;
        }
        Resource res = Resource.remote().load("ui/tt/attrmod").get();
        ItemInfo.InfoFactory f = res.layer(Resource.CodeEntry.class).get(ItemInfo.InfoFactory.class);
        Object[] args = new Object[mods.size() * 2 + 1];
        int i = 1;
        for (Entry<Resource, Integer> entry : mods) {
            args[i] = ui.sess.getresid(entry.getKey());
            args[i + 1] = entry.getValue();
            i += 2;
        }
        return f.build(this, args);
    }

    private int BY_PRIORITY(Entry<Resource, Integer> o1, Entry<Resource, Integer> o2) {
        Resource r1 = o1.getKey();
        Resource r2 = o2.getKey();

        if (charWnd != null) {
            return charWnd.BY_PRIORITY(r1, r2);
        }
        return r1.name.compareTo(r2.name);
    }

    @Override
    public List<ItemInfo> info() {
        return info;
    }

    @Override
    public <T> T context(Class<T> cl) {
        return (ctxr.context(cl, this));
    }
}

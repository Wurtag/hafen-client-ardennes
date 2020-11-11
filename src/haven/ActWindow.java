package haven;

import haven.MenuGrid.Pagina;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class ActWindow extends GameUI.Hidewnd {
    private static final int WIDTH = 200;

    private final ActList filtered;
    private final TextEntry filter;
    private final List<Pagina> all = new LinkedList<>();
    private final Pattern category;
    private int pagseq = 0;
    private boolean needfilter = false;

    public ActWindow(String cap, String category) {
        super(Coord.z, cap);
        this.category = Pattern.compile(category);
        filter = add(new TextEntry(WIDTH, "") {
            @Override
            public void activate(String text) {
                act(filtered.sel.pagina);
            }

            @Override
            protected void changed() {
                super.changed();
                needfilter();
            }

            @Override
            public boolean keydown(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    filtered.change(Math.max(filtered.selindex - 1, 0));
                    filtered.showsel();
                    return true;
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    filtered.change((Math.min(filtered.selindex + 1, filtered.listitems() - 1)));
                    filtered.showsel();
                    return true;
                } else {
                    return super.keydown(e);
                }
            }
        });
        setfocus(filter);
        filtered = add(new ActList(WIDTH, 10) {
            @Override
            protected void itemactivate(ActItem item) {
                act(item.pagina);
            }
        }, 0, filter.sz.y + 5);
        filtered.bgcolor = new Color(0, 0, 0, 128);
        pack();
    }

    private void act(Pagina pagina) {
        ui.gui.menu.use(pagina.button(), false);
        ActWindow.this.hide();
    }

    @Override
    public void show() {
        super.show();
        filter.settext("");
        filtered.change(0);
        filtered.showsel();
        parent.setfocus(this);
    }

    @Override
    public void lostfocus() {
        super.lostfocus();
        hide();
    }

    @Override
    public boolean type(char key, KeyEvent ev) {
        return !ignoredKey(ev) && super.type(key, ev);
    }

    @Override
    public boolean keydown(KeyEvent ev) {
        return !ignoredKey(ev) && super.keydown(ev);
    }

    private static boolean ignoredKey(KeyEvent ev) {
        int code = ev.getKeyCode();
        int mods = ev.getModifiersEx();
        //any modifier except SHIFT pressed alone is ignored, TAB is also ignored
        return (mods != 0 && mods != KeyEvent.SHIFT_DOWN_MASK)
                || code == KeyEvent.VK_CONTROL
                || code == KeyEvent.VK_ALT
                || code == KeyEvent.VK_META
                || code == KeyEvent.VK_TAB;
    }

    private void needfilter() {
        needfilter = true;
    }

  /*  private void filter() {
	needfilter = false;
	String filter = this.filter.text.toLowerCase();
	synchronized (all) {
	    filtered.clear();
	   // ItemFilter itemFilter = ItemFilter.create(filter);
	    for (Pagina p : all) {
		try {
		    Resource res = p.res.get();
		    String name = res.layer(Resource.action).name.toLowerCase();
		    if(name.contains(filter) || itemFilter.matches(p, ui.sess)) {
			filtered.add(p);
		    }
		} catch (Loading e) {
		    needfilter = true;
		}
	    }
	}
	filtered.sort(new ItemComparator(filter));
	if(filtered.listitems() > 0) {
	    filtered.change(filtered.sel);
	    if(filtered.selindex == -1) {
		filtered.change(0);
	    }
	    filtered.sb.val = 0;
	    filtered.showsel();
	}
    }*/

    @Override
    public void tick(double dt) {
        super.tick(dt);

        MenuGrid menu = ui.gui.menu;
        synchronized (menu.paginae) {
            if (pagseq != menu.pagseq) {
                synchronized (all) {
                    all.clear();
                    //   all.addAll(
                    //menu.paginae.stream()
                    // .filter(p -> category.matcher(Pagina.name(p)).matches())
                    //  .collect(Collectors.toList())
                    //   );

                    pagseq = menu.pagseq;
                    needfilter();
                }
            }
        }
        //if(needfilter) {
        //    filter();
//	}
    }

    public static class ItemComparator implements Comparator<ActList.ActItem> {
        private final String filter;

        public ItemComparator(String filter) {
            this.filter = filter;
        }

        @Override
        public int compare(ActList.ActItem a, ActList.ActItem b) {
            if (!filter.isEmpty()) {
                boolean ai = a.name.text.toLowerCase().startsWith(filter);
                boolean bi = b.name.text.toLowerCase().startsWith(filter);
                if (ai && !bi) {
                    return -1;
                }
                if (!ai && bi) {
                    return 1;
                }
            }
            return a.name.text.compareTo(b.name.text);
        }
    }
}

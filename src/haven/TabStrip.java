package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TabStrip<T> extends Widget {
    public static final IBox frame = new IBox("gfx/hud/tab", "tl", "tr", "bl", "br", "extvl", "extvr", "extht", "exthb");
    private final List<Button<T>> buttons = new ArrayList<Button<T>>();
    private Button<T> selected;
    private Orientation orientation = Orientation.Horizontal;
    private int minWidth;
    private Color selectedColor = null;

    protected void selected(Button<T> button) {
    }

    public int getSelectedButtonIndex() {
        return buttons.indexOf(selected);
    }

    public int getButtonCount() {
        return buttons.size();
    }

    public Button<T> insert(int index, Tex image, String text) {
        final Button<T> button = add(new Button<T>(image, text) {
            public void click() {
                select(this);
            }
        });
        if (selectedColor != null) {
            button.bg = selectedColor;
        }
        buttons.add(index, button);
        updateLayout();
        return button;
    }

    public Button<T> insert(int index, Tex image, String text, MenuGrid.Pagina pagina) {
        final Button<T> button = add(new Button<T>(image, text, pagina) {
            public void click() {
                select(this);
            }
        });
        if (selectedColor != null) {
            button.bg = selectedColor;
        }
        buttons.add(index, button);
        updateLayout();
        return button;
    }

    void setSelectedColor(Color c) {
        selectedColor = c;
        for (Button b : buttons) {
            b.bg = selectedColor;
        }
    }

    public void select(T tag) {
        select(tag, false);
    }

    public void select(T tag, boolean skipSelected) {
        for (Button<T> btn : buttons) {
            if (Objects.equals(btn.tag, tag)) {
                select(btn, skipSelected);
                return;
            }
        }
    }

    public void select(int buttonIndex) {
        select(buttons.get(buttonIndex));
    }

    public void select(int buttonIndex, boolean skipSelected) {
        select(buttons.get(buttonIndex), skipSelected);
    }

    public void select(Button<T> button) {
        select(button, false);
    }

    public void select(Button<T> button, boolean skipSelected) {
        if (selected != button) {
            for (Button<T> b : buttons) {
                b.setActive(b == button);
            }
            selected = button;
            if (!skipSelected) {
                selected(button);
            }
        }
    }

    public Button<T> remove(int buttonIndex) {
        Button<T> button = buttons.remove(buttonIndex);
        button.destroy();
        updateLayout();
        return button;
    }

    public void remove(Button<T> button) {
        if (buttons.remove(button)) {
            button.destroy();
            updateLayout();
        }
    }

    public void setOrientation(Orientation value) {
        if (value != orientation) {
            orientation = value;
            updateLayout();
        }
    }

    public void setMinWidth(int value) {
        minWidth = value;
    }

    private void updateLayout() {
        switch (orientation) {
            case Horizontal:
                int x = 0;
                for (Button<T> button : buttons) {
                    button.c = new Coord(x, 0);
                    x += button.sz.x - 1;
                }
                break;
            case Vertical:
                int y = 0;
                int width = minWidth;
                for (Button<T> button : buttons) {
                    button.c = new Coord(0, y);
                    y += button.sz.y - 1;
                    width = Math.max(width, button.sz.x);
                }
                // set same size for all buttons
                for (Button<T> button : buttons)
                    button.resize(width, button.sz.y);
                break;
        }
        pack();
    }

    public abstract static class Button<T> extends Widget {
        public static final Coord padding = new Coord(5, 2);
        public static final Text.Foundry font = new Text.Foundry(Text.dfont, 14).aa(true);
        private Color bg = new Color(0, 0, 0, 128);
        private Tex image;
        private Text text;
        private MenuGrid.Pagina pagina = null;
        private boolean active;
        public T tag;

        Button(Tex image, String text) {
            this.image = image;
            this.text = font.render(text);
            int w = this.text.sz().x + this.image.sz().x + padding.x * 2;
            if (text != null && !text.isEmpty()) {
                w += 10; // space between image and text
            }
            int h = Math.max(this.text.sz().y, this.image.sz().y) + padding.y * 2;
            resize(w, h);
        }

        Button(Tex image, String text, MenuGrid.Pagina pagina) {
            this(image, text);
            this.pagina = pagina;
        }

        public abstract void click();

        @Override
        public void draw(GOut g) {
            if (active) {
                g.chcolor(bg);
                g.frect(Coord.z, sz);
                g.chcolor();
            }
            frame.draw(g, Coord.z, sz);
            g.image(image, padding);
            g.image(text.tex(), new Coord(image.sz().x + 10, padding.y));
        }

        @Override
        public boolean mousedown(Coord c, int button) {
            if (button == 1) {
                click();
                return true;
            }
            return false;
        }

        void setActive(boolean value) {
            this.active = value;
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            Object tt = super.tooltip(c, prev);
            if (tt != null) {
                return tt;
            } else {
                if (pagina != null) {
                    try {
                        BufferedImage bi = pagina.button().rendertt(true);
                        if (bi != null) return (bi);
                        else return ("...");
                    } catch (Loading e) {
                        return ("...");
                    }
                } else {
                    return text;
                }
            }
        }
    }

    public enum Orientation {
        Horizontal,
        Vertical;

        public Orientation invert() {
            switch (this) {
                case Horizontal:
                    return Vertical;
                case Vertical:
                    return Horizontal;
                default:
                    return null;
            }
        }
    }
}
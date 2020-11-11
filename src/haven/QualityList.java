package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class QualityList extends ItemInfo {
    public static final String classname = "Quality";
    public static final Comparator<Quality> QSORTER = Comparator.comparing(o -> o.type.name());

    private final List<Quality> qualities;
    private TexI tex;
    private final Map<SingleType, Quality> singles = new HashMap<>();
    private final boolean isEmpty;
    public static final Quality DEFAULT = new Quality(QualityType.Quality, 10);

    public QualityList(List<ItemInfo> list) {
        super(null);
        qualities = new LinkedList<Quality>();
        for (ItemInfo inf : list) {
            if (inf.getClass().getName().equals(classname)) {
                String name = Reflect.getFieldValueString(inf, "name");
                double q = Reflect.getFieldValueDouble(inf, "q");
                try {
                    qualities.add(new Quality(QualityType.valueOf(name), q));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        qualities.sort(QSORTER);
        isEmpty = qualities.isEmpty();
        if (!isEmpty) {
            SingleType[] types = SingleType.values();
            for (SingleType type : types) {
                singles.put(type, type.get(qualities));
            }
        }
    }

    public boolean isEmpty() {
        return isEmpty;
    }


    public Quality single(SingleType type) {
        return singles.get(type);
    }

    public TexI tex() {
        if (tex == null) {
            tex = render(qualities);
        }
        return tex;
    }

    private static TexI render(List<Quality> qualities) {
        BufferedImage[] imgs = new BufferedImage[qualities.size()];
        for (int i = 0; i < qualities.size(); i++) {
            imgs[i] = qualities.get(i).tex().back;
        }
        return new TexI(ItemInfo.catimgs(-6, true, imgs));
    }

    public static class Quality implements Comparable<Quality> {
        public static final DecimalFormat format = new DecimalFormat("#.#");
        private final QualityType type;
        public final double value;
        public final double multiplier;
        protected TexI tex;

        public Quality(QualityType type, double value) {
            this.type = type;
            this.value = value;
            this.multiplier = Math.sqrt(value / 10);
        }

        public TexI tex() {
            if (tex == null) {
                String text = String.format("%s", format.format(value));
                BufferedImage img = Text.render(text, type.color).img;
                tex = new TexI(Utils.outline2(img, type.outline, true));
            }
            return tex;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public int compareTo(Quality o) {
            return Double.compare(value, o != null ? o.value : 0);
        }
    }

    enum QualityType {
        Quality(new Color(235, 255, 255));
        public final Color color, outline;

        QualityType(Color color) {
            this.color = color;
            this.outline = Color.BLACK;
        }
    }

    public enum SingleType {
        All {
            public Quality get(List<Quality> qualities) {
                return new AllQualities(QualityType.Quality, qualities);
            }
        },
        Average {
            @Override
            public Quality get(List<Quality> qualities) {
                if (qualities.isEmpty()) {
                    return null;
                }
                float sum = 0;
                for (Quality q : qualities) {
                    sum += q.value * q.value;
                }
                return new Quality(Max.get(qualities).type, (float) Math.sqrt(sum / qualities.size()));
            }
        },
        Min {
            @Override
            public Quality get(List<Quality> qualities) {
                Quality min = Collections.min(qualities);
                if (equal(qualities)) {
                    min = new Quality(QualityType.Quality, min.value);
                }
                return min;
            }
        },
        Max {
            @Override
            public Quality get(List<Quality> qualities) {
                Quality max = Collections.max(qualities);
                if (equal(qualities)) {
                    max = new Quality(QualityType.Quality, max.value);
                }
                return max;
            }
        },
        Quality {
            @Override
            public Quality get(List<Quality> qualities) {
                for (Quality q : qualities) {
                    if (q.type == QualityType.Quality) {
                        return q;
                    }
                }
                return null;
            }
        };

        private Tex tex;

        public abstract Quality get(List<Quality> qualities);

        public Tex tex() {
            if (tex == null) {
                tex = Text.render(name()).tex();
            }
            return tex;
        }

        private static boolean equal(List<Quality> qualities) {
            if (qualities.isEmpty()) {
                return true;
            }
            Quality q0 = qualities.get(0);
            for (Quality q : qualities) {
                if (q0.compareTo(q) != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class AllQualities extends Quality {
        private final List<Quality> qualities;

        @Override
        public TexI tex() {
            if (tex == null) {
                tex = QualityList.render(qualities);
            }
            return tex;
        }

        public AllQualities(QualityType type, List<Quality> qualities) {
            super(type, SingleType.Average.get(qualities).value);
            this.qualities = qualities;
        }
    }
}

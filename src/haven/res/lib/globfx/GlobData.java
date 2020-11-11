package haven.res.lib.globfx;

public abstract class GlobData implements Datum {
    public int hashCode() {
        return (this.getClass().hashCode());
    }

    public boolean equals(Object o) {
        return (this.getClass() == o.getClass());
    }
}
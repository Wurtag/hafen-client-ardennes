package haven.res.ui.tt.q.qbuff;

import haven.ItemInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class QList extends ItemInfo.Tip {
    final List<QBuff> ql = new ArrayList<>();

    QList() {
        super(null);
    }

    void sort() {
        Collections.sort(this.ql, (a, b) -> a.origName.compareTo(b.origName));
    }
}
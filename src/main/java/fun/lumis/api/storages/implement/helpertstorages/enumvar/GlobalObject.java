package fun.lumis.api.storages.implement.helpertstorages.enumvar;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;

@Getter
public class GlobalObject<T> {
    private final ObjectArrayList<T> object = new ObjectArrayList<>();
}

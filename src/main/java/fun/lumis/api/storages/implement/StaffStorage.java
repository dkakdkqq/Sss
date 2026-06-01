package fun.lumis.api.storages.implement;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class StaffStorage {

    @Getter private final List<String> staffs = new ArrayList<>();

    public void add(String friend) {
        if (!friend.isEmpty()) staffs.add(friend);
    }

    public void remove(String friend) {
        staffs.remove(friend);
    }

    public void clear() {
        staffs.clear();
    }

    public boolean isStaff(String friend) {
        return staffs.contains(friend);
    }

    public boolean isEmpty() {
        return staffs.isEmpty();
    }
}
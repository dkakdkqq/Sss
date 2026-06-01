package fun.lumis.api.utils.client;

public class UserInfo {
    private final String username;
    private final int uid;
    private final String role;
    private final String hwid;
    private final String expireDate;

    public UserInfo(String username, int uid, String role, String hwid, String expireDate) {
        this.username = username;
        this.uid = uid;
        this.role = role;
        this.hwid = hwid;
        this.expireDate = expireDate;
    }

    public static UserInfo empty() {
        return new UserInfo("Unknown", 0, "Unknown", "", "");
    }

    public String getUsername() {
        return username;
    }

    public int getUid() {
        return uid;
    }

    public String getRole() {
        return role;
    }

    public String getHwid() {
        return hwid;
    }

    public String getExpireDate() {
        return expireDate;
    }
}

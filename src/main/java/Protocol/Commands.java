package Protocol;

public enum Commands {
    LOGIN("login"),
    REGISTER("register"),
    LIST("list"),
    UPLOAD("upload"),
    DOWNLOAD("download"),
    DELETE("delete"),
    SHARE("share"),
    UNSHARE("unshare"),
    LOGOUT("logout"),
    EXIT("exit");

    private final String value;

    Commands(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Commands fromString(String value) {
        for (Commands command : values()) {
            if (command.value.equalsIgnoreCase(value)) {
                return command;
            }
        }
        return null;
    }
}
package Client;

public enum UserCommands {
    LOGIN("login"),
    REGISTER("register"),
    LOGOUT("logout"),
    EXIT("exit"),
    LIST("list"),
    UPLOAD("upload"),
    DOWNLOAD("download"),
    SHARE("share");

    private final String command;

    UserCommands(String command) { this.command = command; }

    public String getCommand() { return command; }
}

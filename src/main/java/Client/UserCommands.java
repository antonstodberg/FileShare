package Client;

public enum UserCommands {
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

    private final String command;

    UserCommands(String command) { this.command = command; }

    public String getCommand() { return command; }

    public static UserCommands fromCommand(String command) {
        for (UserCommands userCommand : values()) {
            if (userCommand.getCommand().equalsIgnoreCase(command)) {
                return userCommand;
            }
        }
        return null;
    }
}

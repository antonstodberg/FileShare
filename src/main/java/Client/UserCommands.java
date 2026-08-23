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

    public static UserCommands fromCommand(String command) {
        for (UserCommands userCommand : values()) {
            if (userCommand.getCommand().equalsIgnoreCase(command)) {
                return userCommand;
            }
        }
        return null;
    }
}

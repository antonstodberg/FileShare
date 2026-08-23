package Server;

public enum ServerResponse {
    // Login & Registration
    LOGIN_SUCCESS("Log in was successful."),
    LOGIN_FAILED("Incorrect username or password."),
    REGISTER_SUCCESS("Registration successful."),
    REGISTER_FAILED("Registration failed."),
    LOGOUT("User logged out."),

    // Command & Argument mismatch
    USER_NOT_LOGGED_IN("You must log in first."),
    ALREADY_LOGGED_IN("You are already logged in."),
    ARGUMENT_MISMATCH("Incorrect number of arguments."),

    // General
    UNRECOGNIZED_COMMAND("Not a valid command."),
    EXIT("exit");

    private final String response;

    ServerResponse(String response) {
        this.response = response;
    }

    public String getResponse() {
        return response;
    }
}

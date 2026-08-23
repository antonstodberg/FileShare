package Server;

public enum ServerResponse {
    // Login & Registration
    LOGIN_SUCCESS("Log in was successful."),
    LOGIN_FAILED("Incorrect username or password."),
    REGISTER_SUCCESS("Registration successful."),
    REGISTER_FAILED("Registration failed."),

    // Command & Argument mismatch
    USER_NOT_LOGGED_IN("You must log in first"),
    ALREADY_LOGGED_IN("You are already logged in"),
    ARGUMENT_MISMATCH("Incorrect number of arguments"),

    // General
    UNRECOGNIZED_COMMAND("Not a valid command");

    private final String response;

    ServerResponse(String Response) {
        this.response = response;
    }

    public String getResponse() {
        return response;
    }
}

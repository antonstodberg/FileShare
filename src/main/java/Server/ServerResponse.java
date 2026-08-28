package Server;

public final class ServerResponse {
    // FOR USER
    // Login & Registration
    public static final String LOGIN_SUCCESS = "Log in was successful.";
    public static final String LOGIN_FAILED = "Incorrect username or password.";
    public static final String REGISTER_SUCCESS = "Registration successful.";
    public static final String REGISTER_FAILED = "Registration failed.";
    public static final String LOGOUT = "User logged out.";

    // Command & Argument mismatch
    public static final String USER_NOT_LOGGED_IN = "You must log in first.";
    public static final String ALREADY_LOGGED_IN = "You are already logged in.";
    public static final String ARGUMENT_MISMATCH = "Incorrect number of arguments.";

    // General
    public static final String UNRECOGNIZED_COMMAND = "Not a valid command.";
    public static final String END = "<end>";
    public static final String EXIT = "exit";
    public static final String GENERAL_INTERNAL_ERROR = "Process failed due to internal error";
    public static final String UPLOAD_FAILED = "Upload failed due to internal error";
    public static final String UPLOAD_SUCCESS = "File was uploaded to your FileShare account";

    private ServerResponse() {  }
}

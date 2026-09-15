package Protocol;

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
    public static final String INCORRECT_FORMAT_ARGUMENT = "Argument given is not correct number format";

    // General
    public static final String UNRECOGNIZED_COMMAND = "Not a valid command.";
    public static final String EXIT = "exit";
    public static final String GENERAL_INTERNAL_ERROR = "Process failed due to internal error";
    public static final String UPLOAD_FAILED_INTERNAL_ERROR = "Upload failed due to internal error";
    public static final String UPLOAD_SUCCESS = "File was uploaded to your FileShare account";
    public static final String UPLOAD_FAILED = "File could not upload";
    public static final String DOWNLOAD_SUCCESS = "File successfully downloaded";
    public static final String DOWNLOAD_FAILED = "File could not download";
    public static final String ACCESS_DENIED = "User does not have rights to perform this action";
    public static final String SHARE_SUCCESS = "File shared to other user";
    public static final String USER_NOT_ACCESS_TO_FILE = "User does not have access to this file";
    public static final String USER_NOT_OWN_FILE = "You are not the owner of this file";
    public static final String FILE_DOES_NOT_EXIST = "File does not exist";
    public static final String USER_ALREADY_ACCESS_FILE = "User already has access to this file";
    public static final String DELETE_SUCCESS = "File was deleted";
    public static final String UNSHARE_SUCCESS = "File no longer shared to other user";

    private ServerResponse() {  }
}

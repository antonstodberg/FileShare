package Protocol;

public enum Response {

    // Authentication
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    REGISTER_SUCCESS,
    REGISTER_FAILED,
    LOGOUT,

    // Command / argument errors
    UNRECOGNIZED_COMMAND,
    ARGUMENT_MISMATCH,
    INCORRECT_FORMAT_ARGUMENT,

    // User state
    USER_NOT_LOGGED_IN,
    ALREADY_LOGGED_IN,

    // General
    GENERAL_INTERNAL_ERROR,
    ACCESS_DENIED,
    END,
    EXIT,

    // Upload
    UPLOAD_SUCCESS,
    UPLOAD_FAILED,
    UPLOAD_FAILED_INTERNAL_ERROR,

    // Download
    DOWNLOAD_SUCCESS,
    DOWNLOAD_FAILED,

    // File access / sharing
    FILE_DOES_NOT_EXIST,
    USER_NOT_OWN_FILE,
    USER_NOT_ACCESS_TO_FILE,
    USER_ALREADY_ACCESS_FILE,
    SHARE_SUCCESS,
    UNSHARE_SUCCESS,
    USER_NOT_FOUND,

    // Delete
    DELETE_SUCCESS
}
package Protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UserCommandsTest {

    @Test
    void fromCommandIsCaseInsensitive() {
        assertEquals(UserCommands.LOGIN,
                UserCommands.fromCommand("LOGIN"));

        assertEquals(UserCommands.REGISTER,
                UserCommands.fromCommand("ReGiStEr"));

        assertEquals(UserCommands.DOWNLOAD,
                UserCommands.fromCommand("DoWnLoAd"));
    }

    @Test
    void fromCommandReturnsNullForUnknownCommand() {
        assertNull(UserCommands.fromCommand("hello"));
        assertNull(UserCommands.fromCommand(""));
        assertNull(UserCommands.fromCommand("uploadFile"));
    }

    @Test
    void fromCommandReturnsNullForNull() {
        assertNull(UserCommands.fromCommand(null));
    }
}

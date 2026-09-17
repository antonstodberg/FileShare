package Server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void constructorAndGettersReturnCorrectValues() {
        User user = new User(
                42,
                "anton",
                "anton@example.com",
                "hashedPassword"
        );

        assertEquals(42, user.getId());
        assertEquals("anton", user.getUsername());
        assertEquals("anton@example.com", user.getEmail());
        assertEquals("hashedPassword", user.getPasswordHash());
    }
}
package com.example.bossprogression;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for MockBukkit tests: provides a fresh mocked server, a simple world
 * and an enabled {@link BossProgressionPlugin} per test method.
 */
public abstract class PluginTestBase {

    protected ServerMock server;
    protected WorldMock world;
    protected BossProgressionPlugin plugin;

    @BeforeEach
    void setUpMock() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(BossProgressionPlugin.class);
    }

    @AfterEach
    void tearDownMock() {
        MockBukkit.unmock();
    }
}

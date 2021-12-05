package me.philcali.device.pool.connection;

import me.philcali.device.pool.exceptions.ConnectionException;
import me.philcali.device.pool.model.Host;

public interface ConnectionFactory {
    Connection connect(Host host) throws ConnectionException;
}

/*
 * Copyright (c) 2022 Philip Cali
 * Released under Apache-2.0 License
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 */

package me.philcali.device.pool.service.api.exception;

public class InvalidInputException extends ClientException {
    private static final long serialVersionUID = -589194868205978540L;

    public InvalidInputException(Throwable ex) {
        super(ex);
    }

    public InvalidInputException(String message) {
        super(message);
    }
}

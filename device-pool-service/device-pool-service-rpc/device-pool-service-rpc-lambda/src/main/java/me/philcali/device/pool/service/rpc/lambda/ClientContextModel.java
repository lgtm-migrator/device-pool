/*
 * Copyright (c) 2022 Philip Cali
 * Released under Apache-2.0 License
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 */

package me.philcali.device.pool.service.rpc.lambda;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import me.philcali.device.pool.model.ApiModel;
import me.philcali.device.pool.service.api.model.CompositeKey;
import org.immutables.value.Value;

@ApiModel
@Value.Immutable
@JsonDeserialize(as = ClientContext.class)
interface ClientContextModel {
    /**
     * <p>accountKey.</p>
     *
     * @return a {@link me.philcali.device.pool.service.api.model.CompositeKey} object
     */
    CompositeKey accountKey();

    /**
     * <p>operationName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    String operationName();
}

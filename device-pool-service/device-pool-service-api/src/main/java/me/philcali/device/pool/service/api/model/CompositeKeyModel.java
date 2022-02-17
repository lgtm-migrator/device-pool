/*
 * Copyright (c) 2022 Philip Cali
 * Released under Apache-2.0 License
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 */

package me.philcali.device.pool.service.api.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import me.philcali.device.pool.model.ApiModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

@ApiModel
@Value.Immutable
@JsonDeserialize(as = CompositeKey.class)
abstract class CompositeKeyModel {
    private static final String DELIMITER = ":";

    abstract String account();

    /**
     * <p>parentKey.</p>
     *
     * @return a {@link me.philcali.device.pool.service.api.model.CompositeKey} object
     */
    public CompositeKey parentKey() {
        if (Objects.isNull(resources()) || resources().isEmpty()) {
            return null;
        }
        return CompositeKey.builder()
                .from(this)
                .resources(resources().subList(0, resources().size() - 1))
                .build();
    }

    @Nullable
    abstract List<String> resources();

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringJoiner joiner = new StringJoiner(DELIMITER)
                .add(account());
        Optional.ofNullable(resources()).ifPresent(res -> res.forEach(joiner::add));
        return joiner.toString();
    }

    /**
     * <p>fromString.</p>
     *
     * @param key a {@link java.lang.String} object
     * @return a {@link me.philcali.device.pool.service.api.model.CompositeKey} object
     */
    public static CompositeKey fromString(final String key) {
        String[] parts = key.split(DELIMITER);
        if (parts.length < 1) {
            throw new IllegalArgumentException("Encoded key must contain an account, received: " + key);
        }
        CompositeKey.Builder builder = CompositeKey.builder()
                .account(parts[0]);
        Arrays.stream(parts).skip(1).forEach(builder::addResources);
        return builder.build();
    }
}

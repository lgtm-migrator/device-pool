package me.philcali.device.pool.service.api.model;

import me.philcali.device.pool.model.ApiModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

@ApiModel
@Value.Immutable
abstract class CompositeKeyModel {
    private static final String DELIMITER = ":";

    abstract String account();

    @Nullable
    abstract List<String> resources();

    @Override
    public String toString() {
        final StringJoiner joiner = new StringJoiner(DELIMITER)
                .add(account());
        Optional.ofNullable(resources()).ifPresent(res -> res.forEach(joiner::add));
        return joiner.toString();
    }

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
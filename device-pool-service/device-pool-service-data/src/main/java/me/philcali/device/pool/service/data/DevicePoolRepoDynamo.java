/*
 * Copyright (c) 2022 Philip Cali
 * Released under Apache-2.0 License
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 */

package me.philcali.device.pool.service.data;

import me.philcali.device.pool.service.api.DevicePoolRepo;
import me.philcali.device.pool.service.api.exception.InvalidInputException;
import me.philcali.device.pool.service.api.model.CompositeKey;
import me.philcali.device.pool.service.api.model.CreateDevicePoolObject;
import me.philcali.device.pool.service.api.model.DevicePoolObject;
import me.philcali.device.pool.service.api.model.DevicePoolType;
import me.philcali.device.pool.service.api.model.UpdateDevicePoolObject;
import me.philcali.device.pool.service.data.token.TokenMarshaller;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * <p>DevicePoolRepoDynamo class.</p>
 *
 * @author philcali
 * @version $Id: $Id
 */
@Singleton
public class DevicePoolRepoDynamo
        extends AbstractObjectRepo<DevicePoolObject, CreateDevicePoolObject, UpdateDevicePoolObject>
        implements DevicePoolRepo {
    /** Constant <code>RESOURCE="pool"</code> */
    public static final String RESOURCE = "pool";

    @Inject
    /**
     * <p>Constructor for DevicePoolRepoDynamo.</p>
     *
     * @param table a {@link software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable} object
     * @param marshaller a {@link me.philcali.device.pool.service.data.token.TokenMarshaller} object
     */
    public DevicePoolRepoDynamo(
            DynamoDbTable<DevicePoolObject> table,
            TokenMarshaller marshaller) {
        super(RESOURCE, table, marshaller);
    }

    /** {@inheritDoc} */
    @Override
    protected PutItemEnhancedRequest<DevicePoolObject> putItemRequest(
            CompositeKey account, CreateDevicePoolObject create) {
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        DevicePoolObject newObject = DevicePoolObject.builder()
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .name(create.name())
                .description(create.description())
                .endpoint(create.endpoint())
                .type(create.type())
                .key(toPartitionKey(account))
                .lockOptions(create.lockOptions())
                .build();
        if (Objects.isNull(create.endpoint()) && create.type() == DevicePoolType.UNMANAGED) {
            throw new InvalidInputException("Cannot have an empty endpoint for an " + create.type() + " pool");
        }
        return PutItemEnhancedRequest.builder(DevicePoolObject.class)
                .item(newObject)
                .conditionExpression(Expression.builder()
                        .expression("attribute_not_exists(#id) and #name <> :id")
                        .putExpressionName("#id", PK)
                        .putExpressionName("#name", SK)
                        .putExpressionValue(":id", AttributeValue.builder().s(create.name()).build())
                        .build())
                .build();
    }

    /** {@inheritDoc} */
    @Override
    protected UpdateItemEnhancedRequest<DevicePoolObject> updateItemRequest(
            CompositeKey account, UpdateDevicePoolObject update) {
        final Expression.Builder builder = Expression.builder()
                .putExpressionName("#id", PK)
                .putExpressionName("#name", SK)
                .putExpressionValue(":id", AttributeValue.builder()
                        .s(update.name())
                        .build());
        final StringBuilder expression = new StringBuilder()
                .append("attribute_exists(#id) and #name = :id");
        Optional.ofNullable(update.type()).ifPresent(type -> {
            expression.append(" and #type = :type");
            builder.putExpressionName("#type", "type")
                    .putExpressionValue(":type", AttributeValue.builder()
                            .s(type.name())
                            .build());
        });
        builder.expression(expression.toString());
        return UpdateItemEnhancedRequest.builder(DevicePoolObject.class)
                .ignoreNulls(true)
                .conditionExpression(builder.build())
                .item(DevicePoolObject.builder()
                        .name(update.name())
                        .description(update.description())
                        .endpoint(update.endpoint())
                        .type(update.type())
                        .key(toPartitionKey(account))
                        .lockOptions(update.lockOptions())
                        .updatedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS))
                        .build())
                .build();
    }
}

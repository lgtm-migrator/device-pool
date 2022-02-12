/*
 * Copyright (c) 2022 Philip Cali
 * Released under Apache-2.0 License
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 */

package me.philcali.device.pool.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import me.philcali.device.pool.service.api.model.CreateDeviceObject;
import me.philcali.device.pool.service.api.model.CreateDevicePoolObject;
import me.philcali.device.pool.service.api.model.CreateProvisionObject;
import me.philcali.device.pool.service.api.model.DevicePoolEndpoint;
import me.philcali.device.pool.service.api.model.DevicePoolEndpointType;
import me.philcali.device.pool.service.api.model.DevicePoolLockOptions;
import me.philcali.device.pool.service.api.model.DevicePoolType;
import me.philcali.device.pool.service.api.model.UpdateDeviceObject;
import me.philcali.device.pool.service.api.model.UpdateDevicePoolObject;
import me.philcali.device.pool.service.client.AwsV4SigningInterceptor;
import me.philcali.device.pool.service.client.DeviceLabService;
import picocli.CommandLine;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@CommandLine.Command(
        name = "device-lab",
        description = "Device Lab CLI for the control plane",
        subcommands = {CommandLine.HelpCommand.class}
)
public class DeviceLabCLI {
    @CommandLine.Option(
            names = "--endpoint",
            description = "Endpoint override",
            required = true
    )
    String endpoint;

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            description = "Verbose flag to output wire details"
    )
    boolean verbose;

    final ObjectMapper mapper;

    private DeviceLabCLI() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new DeviceLabCLI()).execute(args));
    }

    private DeviceLabService createService() {
        return DeviceLabService.create((client, builder) -> {
           client.addInterceptor(AwsV4SigningInterceptor.create());
           builder.baseUrl(endpoint);
        });
    }

    private void executeAndPrint(Call<?> call) {
        try {
            if (verbose) {
                System.out.println("Request URL: " + call.request().url().uri());
                System.out.println("Request Method: " + call.request().method());
            }
            Response<?> response = call.execute();
            if (verbose) {
                System.out.println("Response Status: " + response.code());
                System.out.println("Response Headers: ");
                System.out.println(response.headers());
            }
            if (Objects.nonNull(response.errorBody())) {
                System.err.println(response.errorBody().string());
            } else {
                mapper.writeValue(System.out, response.body());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @CommandLine.Command(
            name = "list-device-pools",
            description = "List device pools"
    )
    public void listDevicePools(
            @CommandLine.Option(names = "--next-token") String nextToken,
            @CommandLine.Option(names = "--limit") Integer limit
    ) {
       executeAndPrint(createService().listDevicePools(nextToken, limit));
    }

    @CommandLine.Command(
            name = "list-devices",
            description = "List devices to device pools"
    )
    public void listDevices(
            @CommandLine.Option(names = "--pool-id", required = true) String poolId,
            @CommandLine.Option(names = "--next-token") String nextToken,
            @CommandLine.Option(names = "--limit") Integer limit
    ) {
        executeAndPrint(createService().listDevices(poolId, nextToken, limit));
    }

    @CommandLine.Command(
            name = "list-provisions",
            description = "List provision requests to device pools"
    )
    public void listProvisions(
            @CommandLine.Option(names = "--pool-id", required = true) String poolId,
            @CommandLine.Option(names = "--next-token") String nextToken,
            @CommandLine.Option(names = "--limit") Integer limit
    ) {
        executeAndPrint(createService().listProvisions(poolId, nextToken, limit));
    }

    @CommandLine.Command(
            name = "list-reservations",
            description = "List device reservation requests to provisions"
    )
    public void listReservations(
            @CommandLine.Option(names = "--pool-id", required = true) String poolId,
            @CommandLine.Option(names = "--provision-id", required = true) String provisionId,
            @CommandLine.Option(names = "--next-token") String nextToken,
            @CommandLine.Option(names = "--limit") Integer limit
    ) {
        executeAndPrint(createService().listReservations(poolId, provisionId, nextToken, limit));
    }

    @CommandLine.Command(
            name = "get-device-pool",
            description = "Obtains a single device pool metadata"
    )
    public void getDevicePool(
            @CommandLine.Option(names = "--pool-id") String poolId
    ) {
        executeAndPrint(createService().getDevicePool(poolId));
    }

    @CommandLine.Command(
            name = "get-device",
            description = "Obtains a single device metadata"
    )
    public void getDevice(
            @CommandLine.Option(names = "--pool-id") String poolId,
            @CommandLine.Option(names = "--device-id") String deviceId
    ) {
        executeAndPrint(createService().getDevice(poolId, deviceId));
    }

    @CommandLine.Command(
            name = "get-provision",
            description = "Obtains a single provision request metadata"
    )
    public void getProvision(
            String poolId,
            String reservationId
    ) {
        executeAndPrint(createService().getProvision(poolId, reservationId));
    }

    @CommandLine.Command(
            name = "get-reservation",
            description = "Obtains a single device reservation"
    )
    public void getReservation(
            @CommandLine.Option(names = "--pool-id", required = true) String poolId,
            @CommandLine.Option(names = "--provision-id", required = true) String provisionId,
            @CommandLine.Option(names = "--reservation-id", required = true) String reservationId
    ) {
        executeAndPrint(createService().getReservation(poolId, provisionId, reservationId));
    }

    @CommandLine.Command(
            name = "create-device-pool",
            description = "Creates a single device pool"
    )
    public void createDevicePool(
            @CommandLine.Option(names = "--pool-id", required = true) String name,
            @CommandLine.Option(names = "--description") String description,
            @CommandLine.Option(names = "--type", defaultValue = "MANAGED") String type,
            @CommandLine.Option(names = "--endpoint-type") String endpointType,
            @CommandLine.Option(names = "--endpoint-uri") String endpointUri,
            @CommandLine.Option(names = "--lock-options-duration") Long duration,
            @CommandLine.Option(names = "--lock-options-enabled") boolean enabled
    ) {
        DevicePoolEndpoint endpoint = null;
        if (Objects.nonNull(endpointUri) || Objects.nonNull(endpointType)) {
            endpoint = DevicePoolEndpoint.builder()
                    .uri(endpointUri)
                    .type(DevicePoolEndpointType.valueOf(endpointUri))
                    .build();
        }
        DevicePoolLockOptions lockOptions = DevicePoolLockOptions.builder()
                .enabled(enabled)
                .duration(duration)
                .build();
        executeAndPrint(createService().createDevicePool(CreateDevicePoolObject.builder()
                .type(DevicePoolType.valueOf(type.toUpperCase()))
                .description(description)
                .endpoint(endpoint)
                .lockOptions(lockOptions)
                .name(name)
                .build()));
    }

    @CommandLine.Command(
            name = "create-device",
            description = "Creates a single device for a device pool"
    )
    public void createDevice(
            @CommandLine.Option(names = "--pool-id", required = true) String poolId,
            @CommandLine.Option(names = "--device-id", required = true) String deviceId,
            @CommandLine.Option(names = "--public-address", required = true) String publicAddress,
            @CommandLine.Option(names = "--private-address") String privateAddress,
            @CommandLine.Option(names = "--expires-in") Instant expiresIn
    ) {
        executeAndPrint(createService().createDevice(poolId, CreateDeviceObject.builder()
                .id(deviceId)
                .publicAddress(publicAddress)
                .privateAddress(privateAddress)
                .expiresIn(expiresIn)
                .build()));
    }

    @CommandLine.Command(
            name = "create-provision",
            description = "Creates a single provision request"
    )
    public void createProvision(
            @CommandLine.Option(names = "--pool-id", required = true) String poolId,
            @CommandLine.Option(names = "--id", required = true) String id,
            @CommandLine.Option(names = "--amount", description = "1") int amount,
            @CommandLine.Option(names = "--expires-in") Instant expiresIn
    ) {
        executeAndPrint(createService().createProvision(poolId, CreateProvisionObject.builder()
                .id(id)
                .amount(amount)
                .expiresIn(expiresIn)
                .build()));
    }

    @CommandLine.Command(
            name = "update-device-pool",
            description = "Updates a single device pool record"
    )
    public void updateDevicePool(
            @CommandLine.Option(names = "--pool-id", required = true) String poolId,
            @CommandLine.Option(names = "--description") String description,
            @CommandLine.Option(names = "--type") String type,
            @CommandLine.Option(names = "--endpoint-type") String endpointType,
            @CommandLine.Option(names = "--endpoint-uri") String endpointUri,
            @CommandLine.Option(names = "--lock-options-duration") Long duration,
            @CommandLine.Option(names = "--lock-options-enabled") Boolean enabled
    ) {
        DevicePoolEndpoint endpoint = null;
        if (Objects.nonNull(endpointUri) || Objects.nonNull(endpointType)) {
            endpoint = DevicePoolEndpoint.builder()
                    .uri(endpointUri)
                    .type(DevicePoolEndpointType.valueOf(endpointUri))
                    .build();
        }
        DevicePoolLockOptions lockOptions = null;
        if (Objects.nonNull(enabled)) {
            lockOptions = DevicePoolLockOptions.builder()
                    .enabled(enabled)
                    .duration(duration)
                    .build();
        }
        executeAndPrint(createService().updateDevicePool(poolId, UpdateDevicePoolObject.builder()
                .description(description)
                .type(Optional.ofNullable(type).map(DevicePoolType::valueOf).orElse(null))
                .lockOptions(lockOptions)
                .endpoint(endpoint)
                .build()));
    }

    @CommandLine.Command(
            name = "update-device",
            description = "Updates a single device metadata record"
    )
    public void updateDevice(
            @CommandLine.Option(names = "--pool-id", required = true) String poolId,
            @CommandLine.Option(names = "--device-id", required = true) String deviceId,
            @CommandLine.Option(names = "--public-address") String publicAddress,
            @CommandLine.Option(names = "--private-address") String privateAddress,
            @CommandLine.Option(names = "--expires-in") Instant expiresIn
    ) {
        executeAndPrint(createService().updateDevice(poolId, deviceId, UpdateDeviceObject.builder()
                .publicAddress(publicAddress)
                .privateAddress(privateAddress)
                .expiresIn(expiresIn)
                .build()));
    }

    @CommandLine.Command(
            name = "cancel-provision",
            description = "Cancel a single non-terminal provision request"
    )
    public void cancelProvision(
            @CommandLine.Option(names = "--pool-id") String poolId,
            @CommandLine.Option(names = "--provision-id") String provisionId
    ) {
        executeAndPrint(createService().cancelProvision(poolId, provisionId));
    }

    @CommandLine.Command(
            name = "delete-provision",
            description = "Deletes a single terminal provision request"
    )
    public void deleteProvision(
            @CommandLine.Option(names = "--pool-id") String poolId,
            @CommandLine.Option(names = "--provision-id") String provisionId
    ) {
        executeAndPrint(createService().deleteProvision(poolId, provisionId));
    }

    @CommandLine.Command(
            name = "cancel-reservation",
            description = "Cancels a single non-terminal device reservation"
    )
    public void cancelReservation(
            @CommandLine.Option(names = "--pool-id", required = true) String poolId,
            @CommandLine.Option(names = "--provision-id", required = true) String provisionId,
            @CommandLine.Option(names = "--reservation-id", required = true) String reservationId
    ) {
        executeAndPrint(createService().cancelReservation(poolId, provisionId, reservationId));
    }
}
/*
 * Copyright (c) 2022 Philip Cali
 * Released under Apache-2.0 License
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 */

package me.philcali.device.pool.provision;

import me.philcali.device.pool.Device;
import me.philcali.device.pool.exceptions.ProvisioningException;
import me.philcali.device.pool.exceptions.ReservationException;
import me.philcali.device.pool.model.ApiModel;
import me.philcali.device.pool.model.Host;
import me.philcali.device.pool.model.ProvisionInput;
import me.philcali.device.pool.model.ProvisionOutput;
import me.philcali.device.pool.model.Reservation;
import me.philcali.device.pool.model.Status;
import me.philcali.device.pool.reservation.ReservationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.immutables.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ApiModel
@Value.Immutable
abstract class LocalProvisionServiceModel implements ProvisionService, ReservationService {
    private static final Logger LOGGER = LogManager.getLogger(LocalProvisionService.class);
    private final Map<String, CachedEntry<ProvisionOutput>> reservations = new ConcurrentHashMap<>();
    private final BlockingQueue<LocalProvisionEntry> activeProvisions = new LinkedBlockingQueue<>();
    private final BlockingQueue<Host> availableHosts = new LinkedBlockingQueue<>();
    private final LocalProvisionRunnable currentRunnable = new LocalProvisionRunnable();
    private final LocalProvisionReaper reapRunnable = new LocalProvisionReaper();
    private final Lock lock = new ReentrantLock();

    abstract Set<Host> hosts();

    @Value.Default
    boolean expireProvisions() {
        return true;
    }

    @Value.Default
    long provisionTimeout() {
        return TimeUnit.HOURS.toMillis(1);
    }

    @Value.Default
    ExecutorService executorService() {
        int threads = 1;
        if (expireProvisions()) {
            threads += 1;
        }
        return Executors.newFixedThreadPool(threads, runnable -> {
            final Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("Local-Provisioning");
            return thread;
        });
    }

    @Value.Check
    LocalProvisionServiceModel validate() {
        if (hosts().isEmpty()) {
            throw new IllegalArgumentException("hosts must contain at least one entry");
        }
        // Initialize the available hosts from the set of hosts
        if (!hosts().stream().allMatch(availableHosts::offer)) {
            throw new IllegalStateException("could not queue pending hosts");
        }
        // Start the background queue drain, to supply provisions
        executorService().execute(currentRunnable);
        // Start provision expiry
        if (expireProvisions()) {
            executorService().execute(reapRunnable);
        }
        return this;
    }

    private static class CachedEntry<T> {
        T value;
        long expiresIn;

        CachedEntry(T value, long expiresIn) {
            this.value = value;
            this.expiresIn = expiresIn;
        }
    }

    private static class LocalProvisionEntry {
        ProvisionInput input;
        ProvisionOutput output;

        LocalProvisionEntry(ProvisionInput input, ProvisionOutput output) {
            this.input = input;
            this.output = output;
        }
    }

    private class LocalProvisionReaper implements Runnable {
        volatile boolean running = true;

        @Override
        public void run() {
            while (running) {
                try {
                    int amount = LocalProvisionServiceModel.this.releaseAvailable(System.currentTimeMillis());
                    LOGGER.info("Reaped {} devices", amount);
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    LOGGER.info("Reaper is shutting down.");
                    running = false;
                }
            }
        }
    }

    private class LocalProvisionRunnable implements Runnable {
        volatile boolean running = true;

        @Override
        public void run() {
            try {
                while (running) {
                    // Only acquire reaping lock if unlocked on active entry
                    final LocalProvisionEntry entry = activeProvisions.take();
                    lock.lock();
                    reservations.computeIfPresent(entry.input.id(), (id, cache) -> new CachedEntry<>(
                            ProvisionOutput.builder()
                                .from(cache.value)
                                .status(Status.PROVISIONING)
                                .build(), cache.expiresIn));
                    final List<Reservation> pendingReservations = new ArrayList<>();
                    for (int time = 0; time < entry.input.amount(); time++) {
                        final Host host = availableHosts.take();
                        pendingReservations.add(Reservation.builder()
                                .deviceId(host.deviceId())
                                .status(Status.SUCCEEDED)
                                .build());
                        LOGGER.info("Adding host {} to provision {}", host.deviceId(), entry.input.id());
                    }
                    reservations.computeIfPresent(entry.input.id(), (id, cache) -> new CachedEntry<>(
                            ProvisionOutput.builder()
                                .from(cache.value)
                                .status(Status.SUCCEEDED)
                                .addAllReservations(pendingReservations)
                                .build(), cache.expiresIn));
                    lock.unlock();
                }
            } catch (InterruptedException ie) {
                LOGGER.info("Queue poll interrupted, stopping");
                running = false;
            }
        }
    }

    @Override
    public ProvisionOutput provision(ProvisionInput input) throws ProvisioningException {
        final CachedEntry<ProvisionOutput> handle = reservations.computeIfAbsent(input.id(), id -> new CachedEntry<>(
                ProvisionOutput.builder()
                    .id(id)
                    .status(Status.REQUESTED)
                    .build(), System.currentTimeMillis() + provisionTimeout()));
        if (!activeProvisions.offer(new LocalProvisionEntry(input, handle.value))) {
            throw new ProvisioningException("Could not create a provision with id: " + input.id());
        }
        return handle.value;
    }

    @Override
    public ProvisionOutput describe(ProvisionOutput output) throws ProvisioningException {
        CachedEntry<ProvisionOutput> handle = reservations.get(output.id());
        if (Objects.isNull(handle)) {
            throw new ProvisioningException("Could not find a provision with id: " + output.id());
        }
        return handle.value;
    }

    @Override
    public Host exchange(Reservation reservation) throws ReservationException {
        return hosts().stream()
                .filter(h -> h.deviceId().equals(reservation.deviceId()))
                .findFirst()
                .orElseThrow(() -> new ReservationException("Could not a host with id: " + reservation.deviceId()));
    }

    private boolean releaseHost(String deviceId) {
        return hosts().stream()
                .filter(host -> host.deviceId().equals(deviceId))
                .filter(host -> !availableHosts.contains(host))
                .reduce(false,
                        (left, right) -> availableHosts.offer(right) || left,
                        (left, right) -> left || right);
    }

    public boolean release(Device device) {
        return releaseHost(device.id());
    }

    public int release(ProvisionOutput output) {
        AtomicInteger released = new AtomicInteger();
        CachedEntry<ProvisionOutput> handle = reservations.remove(output.id());
        if (Objects.nonNull(handle)) {
            handle.value.reservations().stream()
                    .map(Reservation::deviceId)
                    .filter(this::releaseHost)
                    .forEach(hostId -> {
                        LOGGER.info("Release host {}", hostId);
                        released.incrementAndGet();
                    });
            LOGGER.info("Released provision with id: {}", output.id());
        }
        return released.get();
    }

    protected int releaseAvailable(long when) {
        lock.lock();
        try {
            int released = 0;
            for (CachedEntry<ProvisionOutput> cachedEntry : reservations.values()) {
                if (cachedEntry.expiresIn < when) {
                    released += release(cachedEntry.value);
                }
            }
            return released;
        } finally {
            lock.unlock();
        }
    }

    public void extend(ProvisionOutput output) {
        lock.lock();
        reservations.computeIfPresent(output.id(), (id, cache) -> {
            cache.expiresIn += provisionTimeout();
            return cache;
        });
        lock.unlock();
    }

    @Override
    public void close() throws Exception {
        currentRunnable.running = false;
        reapRunnable.running = false;
    }
}
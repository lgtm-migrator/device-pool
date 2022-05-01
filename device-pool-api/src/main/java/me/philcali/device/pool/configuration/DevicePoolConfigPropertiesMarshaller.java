/*
 * Copyright (c) 2022 Philip Cali
 * Released under Apache-2.0 License
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 */

package me.philcali.device.pool.configuration;

import me.philcali.device.pool.exceptions.DevicePoolConfigMarshallException;
import me.philcali.device.pool.local.LocalDevicePool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class DevicePoolConfigPropertiesMarshaller implements DevicePoolConfigMarshaller {
    private static final Logger LOGGER = LogManager.getLogger(DevicePoolConfigPropertiesMarshaller.class);

    private static final String DEVICE_NAMESPACE = "device.pool";
    private static final String DEVICE_CLASS_NAME =  DEVICE_NAMESPACE + ".class";
    static final String DEFAULT_POOL_CLASS = LocalDevicePool.class.getName();

    @Override
    public Set<String> contentTypes() {
        return Set.of("properties", "props");
    }

    DevicePoolConfigProperties internalLoad(Properties properties) {
        DevicePoolConfigProperties.Builder builder = DevicePoolConfigProperties.builder();
        builder.poolClassName(properties.getProperty(DEVICE_CLASS_NAME, DEFAULT_POOL_CLASS));
        PriorityQueue<String> propNames = new PriorityQueue<>(properties.stringPropertyNames());
        SortedMap<String, DevicePoolConfigPropertiesModel.DevicePoolConfigEntryProperties> children = new TreeMap<>();
        while (!propNames.isEmpty()) {
            String propName = propNames.poll();
            if (propName.equals(DEVICE_CLASS_NAME)) {
                continue;
            }
            String[] parts = propName.split("\\.");
            if (parts.length >= 3) {
                Map<String, DevicePoolConfigPropertiesModel.DevicePoolConfigEntryProperties> current = children;
                for (int i = 2; i < parts.length; i++) {
                    current = current.computeIfAbsent(parts[i],
                            key -> new DevicePoolConfigPropertiesModel.DevicePoolConfigEntryProperties(key, propName, properties)).children();
                }
            } else {
                LOGGER.info("Found prop {}, but skipping", propName);
            }
        }
        builder.properties(Collections.unmodifiableSortedMap(children));
        return builder.build();
    }

    @Override
    public DevicePoolConfigProperties unmarshall(InputStream stream) throws IOException {
        Objects.requireNonNull(stream, "Failed to load properties; stream provided is null");
        try (InputStreamReader inputStreamReader = new InputStreamReader(stream, StandardCharsets.UTF_8);
             Reader bufferedReader = new BufferedReader(inputStreamReader)) {

            Properties properties = new Properties();
            properties.load(bufferedReader);
            return internalLoad(properties);
        }
    }

    private void fillTree(
            Properties properties, String namespace, Map<String, DevicePoolConfig.DevicePoolConfigEntry> entries) {
        Map<String, DevicePoolConfig.DevicePoolConfigEntry> sorted = new TreeMap<>(entries);
        for (Map.Entry<String, DevicePoolConfig.DevicePoolConfigEntry> entry : sorted.entrySet()) {
            final String field = namespace + "." + entry.getKey();
            entry.getValue().value().ifPresent(value -> properties.put(field, value));
            fillTree(properties, field, entry.getValue().properties());
        }
    }

    @Override
    public void marshall(OutputStream output, DevicePoolConfig config) throws IOException {
        Properties properties = new Properties();
        fillTree(properties, DEVICE_NAMESPACE, config.properties());
        properties.put(DEVICE_CLASS_NAME, config.poolClassName());
        properties.store(output, "Generated by " + getClass().getName());
    }
}

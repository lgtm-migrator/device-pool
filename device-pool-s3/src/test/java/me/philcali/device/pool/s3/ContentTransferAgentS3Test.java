/*
 * Copyright (c) 2022 Philip Cali
 * Released under Apache-2.0 License
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 */

package me.philcali.device.pool.s3;

import me.philcali.device.pool.connection.Connection;
import me.philcali.device.pool.content.ContentTransferAgent;
import me.philcali.device.pool.model.CommandInput;
import me.philcali.device.pool.model.CommandOutput;
import me.philcali.device.pool.model.CopyInput;
import me.philcali.device.pool.model.CopyOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class})
class ContentTransferAgentS3Test {
    @Mock
    private Connection connection;
    @Mock
    private S3Client s3;
    @Mock
    private AgentCommand command;
    private final String bucketName = "test-bucket";
    private final String prefix = "abc-123";
    private ContentTransferAgent agent;
    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        agent = ContentTransferAgentS3.builder()
                .connection(connection)
                .s3(s3)
                .command(command)
                .bucketName(bucketName)
                .prefix(prefix)
                .build();

        tempDir = Files.createTempDirectory("test-s3");
        Files.createDirectories(tempDir.resolve("a").resolve("b"));
        Files.createDirectories(tempDir.resolve("c"));
        writeTestFile(tempDir);
        writeTestFile(tempDir.resolve("a"));
        writeTestFile(tempDir.resolve("a").resolve("b"));
        writeTestFile(tempDir.resolve("c"));
    }

    @AfterEach
    void teardown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private void writeTestFile(Path location) throws IOException {
        Files.write(location.resolve("test.txt"), "Hello World!".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void GIVEN_transfer_is_created_WHEN_transfer_sends_directory_THEN_s3_is_recursive() {
        CopyInput input = CopyInput.builder()
                .source(tempDir.resolve("a").toString())
                .destination("a")
                .addOptions(CopyOption.RECURSIVE)
                .build();
        final Set<String> uuids = new HashSet<>();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).then(answer -> {
            PutObjectRequest request = answer.getArgument(0);
            String[] parts = request.key().split("/");
            uuids.add(parts[1]);
            assertEquals(prefix, parts[0]);
            assertEquals("test.txt", parts[2]);
            assertEquals(bucketName, request.bucket());
            return PutObjectResponse.builder()
                    .build();
        });
        when(command.copy(any(CopyInput.class))).then(answer -> {
            CopyInput commandInput = answer.getArgument(0);
            assertEquals(input.destination(), commandInput.destination());
            String[] parts = commandInput.source().split("/");
            assertTrue(uuids.contains(parts[4]), "uuids does not contain " + parts[4]);
            return CommandInput.of("echo Hello World");
        });
        when(connection.execute(eq(CommandInput.of("echo Hello World")))).thenReturn(CommandOutput.builder()
                .exitCode(0)
                .originalInput(CommandInput.of("echo Hello World"))
                .stdout("Hello World".getBytes(StandardCharsets.UTF_8))
                .build());
        agent.send(input);
        assertEquals(2, uuids.size());
    }

    @Test
    void GIVEN_transfer_is_created_WHEN_transfer_sends_THEN_s3_is_invoked() {
        CopyInput input = CopyInput.builder()
                .source(tempDir.resolve("c").resolve("test.txt").toString())
                .destination("test.txt")
                .build();
        AtomicReference<String> uuid = new AtomicReference<>();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).then(answer -> {
            PutObjectRequest request = answer.getArgument(0);
            RequestBody body = answer.getArgument(1);
            String[] parts = request.key().split("/");
            uuid.set(parts[1]);
            assertEquals(prefix, parts[0]);
            assertEquals("test.txt", parts[2]);
            assertEquals(bucketName, request.bucket());
            return PutObjectResponse.builder()
                    .build();
        });
        when(command.copy(any(CopyInput.class))).then(answer -> {
            CopyInput commandInput = answer.getArgument(0);
            assertEquals(input.destination(), commandInput.destination());
            assertEquals("s3://" + bucketName + "/" + prefix + "/" + uuid.get() + "/test.txt", commandInput.source());
            return CommandInput.of("echo Hello World");
        });
        when(connection.execute(eq(CommandInput.of("echo Hello World")))).thenReturn(CommandOutput.builder()
                .exitCode(0)
                .originalInput(CommandInput.of("echo Hello World"))
                .stdout("Hello World".getBytes(StandardCharsets.UTF_8))
                .build());
        agent.send(input);
    }

    @Test
    void GIVEN_transfer_is_created_WHEN_transfer_receives_THEN_s3_is_invoked() throws IOException {
        CopyInput input = CopyInput.builder()
                .destination(tempDir.resolve("d").resolve("test.txt").toString())
                .source("test.txt")
                .build();
        AtomicReference<String> uuid = new AtomicReference<>();
        when(command.copy(any(CopyInput.class))).then(answer -> {
            CopyInput commandInput = answer.getArgument(0);
            String[] parts = commandInput.destination().split("/");
            // s3:[0] ""[1] bucketName[2] prefix[3] uuid[4]
            uuid.set(parts[4]);
            assertEquals(input.source(), commandInput.source());
            assertEquals("s3://" + bucketName + "/" + prefix + "/" + uuid.get() + "/test.txt", commandInput.destination());
            return CommandInput.of("echo Hello World");
        });
        when(connection.execute(eq(CommandInput.of("echo Hello World")))).thenReturn(CommandOutput.builder()
                .stdout("Hello World".getBytes(StandardCharsets.UTF_8))
                .exitCode(0)
                .build());
        InputStream inputStream = Files.newInputStream(tempDir.resolve("test.txt"));
        when(s3.getObject(any(GetObjectRequest.class))).then(answer -> {
            GetObjectRequest request = answer.getArgument(0);
            assertEquals(prefix + "/" + uuid.get() + "/test.txt", request.key());
            assertEquals(bucketName, request.bucket());
            return new ResponseInputStream<>(
                    GetObjectResponse.builder().build(),
                    AbortableInputStream.create(inputStream));
        });
        agent.receive(input);
        assertTrue(Files.exists(tempDir.resolve("d").resolve("test.txt")));
        assertEquals("Hello World!", Files.readString(tempDir.resolve("d").resolve("test.txt")));
    }
}

package com.rallit.support;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;

public abstract class DeploymentTestSupport {
  protected static final String LINUX_IMAGE =
      "postgres@sha256:3540c0362c809df084ffac0ae69fe794515f9de92692f02178bcdce0ef0302ac";
  protected final Path repository = Path.of("").toAbsolutePath();
  @TempDir protected Path temporary;

  protected record Result(int exitCode, String output) {}

  protected Result run(Map<String, String> environment, String... command) throws Exception {
    Path output = temporary.resolve("command-" + UUID.randomUUID() + ".log");
    var builder =
        new ProcessBuilder(command)
            .directory(repository.toFile())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile());
    // Never allow a developer's exported deployment values to enter the test fixture.
    for (String line : Files.readAllLines(repository.resolve(".env.example"))) {
      if (!line.isBlank() && !line.startsWith("#")) {
        builder.environment().remove(line.split("=", 2)[0]);
      }
    }
    builder.environment().putAll(environment);
    var process = builder.start();
    if (!process.waitFor(100, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError("Deployment test command timed out");
    }
    return new Result(process.exitValue(), Files.readString(output));
  }
}

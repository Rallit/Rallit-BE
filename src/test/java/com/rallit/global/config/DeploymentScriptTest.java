package com.rallit.global.config;

import static org.assertj.core.api.Assertions.*;

import com.rallit.support.DeploymentTestSupport;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
class DeploymentScriptTest extends DeploymentTestSupport {
  private static final String OLD = "example/rallit:old";
  private static final String NEW = "example/rallit:new";
  private static final String HISTORY = "2026-01-01T00:00:00Z " + OLD + "\n";

  @BeforeEach
  void fixture() throws Exception {
    Files.copy(repository.resolve("deploy/deploy.sh"), temporary.resolve("deploy.sh"));
    Files.writeString(temporary.resolve(".env"), "APP_IMAGE=" + OLD + "\n");
    temporary.resolve(".env").toFile().setReadable(false, false);
    temporary.resolve(".env").toFile().setWritable(false, false);
    temporary.resolve(".env").toFile().setReadable(true, true);
    temporary.resolve(".env").toFile().setWritable(true, true);
    Files.writeString(temporary.resolve("deploy-history.log"), HISTORY);
    Files.createDirectory(temporary.resolve("bin"));
    Files.writeString(
        temporary.resolve("bin/docker"),
        """
#!/bin/sh
printf '%s|%s\n' "$APP_IMAGE" "$*" >> /work/docker-calls
case "$*" in
  'compose config --services') printf 'app\nredis\ndb\n' ;;
  *'up '*redis)
    [ "$RALLIT_TEST_SCENARIO" != redis-failure ] || exit 1
    printf 'ready' > /work/redis-ready ;;
  *'up '*app)
    if [ "$RALLIT_TEST_SCENARIO" = app-failure ] && [ "$APP_IMAGE" = example/rallit:new ]; then exit 1; fi ;;
esac
exit 0
""");
    temporary.resolve("bin/docker").toFile().setExecutable(true, false);
  }

  private Result deploy(String scenario) throws Exception {
    return run(
        Map.of(),
        "docker",
        "run",
        "--rm",
        "--network",
        "none",
        "--user",
        Files.getAttribute(temporary, "unix:uid") + ":" + Files.getAttribute(temporary, "unix:gid"),
        "--mount",
        "type=bind,source=" + temporary.toRealPath() + ",target=/work",
        "--workdir",
        "/work",
        "--env",
        "PATH=/work/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "--env",
        "RALLIT_TEST_SCENARIO=" + scenario,
        "--entrypoint",
        "bash",
        LINUX_IMAGE,
        "/work/deploy.sh",
        NEW);
  }

  private List<String> calls() throws Exception {
    return Files.readAllLines(temporary.resolve("docker-calls"));
  }

  @Test
  void redisFailureDoesNotReplaceAppOrUpdateSuccessfulState() throws Exception {
    var result = deploy("redis-failure");
    assertThat(result.exitCode()).as(result.output()).isNotZero();
    assertThat(calls()).noneMatch(call -> call.contains("up ") && call.endsWith(" app"));
    assertThat(Files.readString(temporary.resolve(".env"))).isEqualTo("APP_IMAGE=" + OLD + "\n");
    assertThat(Files.readString(temporary.resolve("deploy-history.log"))).isEqualTo(HISTORY);
    assertThat(calls()).noneMatch(call -> call.contains("image rm"));
  }

  @Test
  void successfulDeploymentWaitsForRedisBeforeAppAndRecordsNewImage() throws Exception {
    var result = deploy("success");
    assertThat(result.exitCode()).as(result.output()).isZero();
    List<String> calls = calls();
    assertThat(calls).anyMatch(call -> call.contains("up ") && call.endsWith(" redis"));
    int redis =
        java.util.stream.IntStream.range(0, calls.size())
            .filter(i -> calls.get(i).contains("up ") && calls.get(i).endsWith(" redis"))
            .findFirst()
            .orElseThrow();
    int app =
        java.util.stream.IntStream.range(0, calls.size())
            .filter(i -> calls.get(i).contains("up ") && calls.get(i).endsWith(" app"))
            .findFirst()
            .orElseThrow();
    assertThat(redis).isLessThan(app);
    assertThat(calls.get(redis)).contains("--wait");
    assertThat(Files.readString(temporary.resolve(".env"))).isEqualTo("APP_IMAGE=" + NEW + "\n");
    assertThat(Files.readAllLines(temporary.resolve("deploy-history.log")))
        .hasSize(2)
        .last()
        .asString()
        .endsWith(" " + NEW);
  }

  @Test
  void appFailureRollsBackWithoutRestartingRedisOrRecordingSuccess() throws Exception {
    var result = deploy("app-failure");
    assertThat(result.exitCode()).as(result.output()).isNotZero();
    assertThat(calls().stream().filter(c -> c.contains("up ") && c.endsWith(" redis"))).hasSize(1);
    assertThat(calls().stream().filter(c -> c.contains("up ") && c.endsWith(" app"))).hasSize(2);
    assertThat(calls()).anyMatch(c -> c.startsWith(OLD + "|") && c.contains("--no-deps app"));
    assertThat(Files.readString(temporary.resolve("deploy-history.log"))).isEqualTo(HISTORY);
    assertThat(Files.readString(temporary.resolve(".env"))).isEqualTo("APP_IMAGE=" + OLD + "\n");
    assertThat(calls())
        .noneMatch(c -> c.contains("down") || c.contains("volume rm") || c.contains("image rm"));
  }
}

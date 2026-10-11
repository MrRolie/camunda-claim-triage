package demo.claimtriage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Drives real claims through the real engine: needs Camunda 8 Run on localhost, and the app itself must not
 * be running (its workers would race this test's). Off by default; run with {@code ./mvnw test -De2e=true}.
 * The person in the loop is played by this test, completing the user tasks through the API.
 */
@EnabledIfSystemProperty(named = "e2e", matches = "true")
@SpringBootTest(properties = {"claims.extractor=rules", "claims.output-dir=target/e2e-out"})
class ClaimTriageEndToEndTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired ClaimController front;
  @Autowired CamundaClient camunda;

  @Test
  void approvedClaimEndsWithAQueuedPayout() throws IOException {
    long instance = submit("claim-01-auto.txt");

    UserTask review = awaitUserTask(instance);
    assertThat(review.getElementId()).isEqualTo("ReviewClaim");
    camunda.newCompleteUserTaskCommand(review.getUserTaskKey())
        .variables(Map.of("decision", "approve", "reviewerNote", "e2e"))
        .send().join();

    awaitCompleted(instance);
    String claimId = processVariable(instance, "claimId").replace("\"", "");
    assertThat(Files.readString(Path.of("target/e2e-out/payout-queue.jsonl"))).contains(claimId);
  }

  @Test
  void rejectedClaimEndsWithARejectionNotice() throws IOException {
    long instance = submit("claim-04-injection.txt");

    UserTask review = awaitUserTask(instance);
    camunda.newCompleteUserTaskCommand(review.getUserTaskKey())
        .variables(Map.of("decision", "reject", "reviewerNote", "e2e"))
        .send().join();

    awaitCompleted(instance);
    String claimId = processVariable(instance, "claimId").replace("\"", "");
    assertThat(Files.readString(Path.of("target/e2e-out/rejection-notices.jsonl"))).contains(claimId);
  }

  @Test
  void unusableClaimGoesToManualTriageWithTheProblemsAttached() throws IOException {
    long instance = submit("claim-03-vague.txt");

    UserTask triage = awaitUserTask(instance);
    assertThat(triage.getElementId()).isEqualTo("ManualTriage");
    // The triage form reads these, so they must sit on the process, not on the error event.
    assertThat(processVariable(instance, "extractionProblems")).contains("No valid policy number.");

    camunda.newCompleteUserTaskCommand(triage.getUserTaskKey())
        .variables(Map.of("triageNote", "e2e"))
        .send().join();
    awaitCompleted(instance);
  }

  private long submit(String sample) throws IOException {
    Map<String, Object> response = front.submit(Files.readString(Path.of("samples", sample)));
    return (Long) response.get("processInstanceKey");
  }

  private UserTask awaitUserTask(long instance) {
    return await(() -> camunda.newUserTaskSearchRequest()
        .filter(f -> f.processInstanceKey(instance))
        .send().join().items().stream().findFirst());
  }

  private void awaitCompleted(long instance) {
    await(() -> Optional.of(camunda.newProcessInstanceGetRequest(instance).send().join())
        .filter(p -> p.getState() == ProcessInstanceState.COMPLETED));
  }

  private String processVariable(long instance, String name) {
    return await(() -> {
      List<Variable> vars = camunda.newVariableSearchRequest()
          .filter(f -> f.processInstanceKey(instance).scopeKey(instance).name(name))
          .send().join().items();
      return vars.stream().findFirst().map(Variable::getValue);
    });
  }

  /** The search API is eventually consistent, so poll until the answer shows up. */
  private static <T> T await(Supplier<Optional<T>> probe) {
    Instant deadline = Instant.now().plus(TIMEOUT);
    while (Instant.now().isBefore(deadline)) {
      try {
        Optional<T> result = probe.get();
        if (result.isPresent()) return result.get();
      } catch (RuntimeException notYetVisible) {
        // e.g. 404 before the instance is exported; try again
      }
      sleep();
    }
    throw new AssertionError("Timed out after " + TIMEOUT);
  }

  private static void sleep() {
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}

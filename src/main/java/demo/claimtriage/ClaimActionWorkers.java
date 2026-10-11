package demo.claimtriage;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The automated actions that run only after a person has decided. Each one appends a JSON line to a file,
 * standing in for a call to a payments API or a mail service.
 *
 * <p>Jobs are delivered at least once, so a retry can repeat an action. Each record carries the claim id as
 * an idempotency key; a real downstream system would use it to drop duplicates.
 */
@Component
public class ClaimActionWorkers {

  private final Path outputDir;
  private final JsonMapper json = JsonMapper.builder().build();

  public ClaimActionWorkers(@Value("${claims.output-dir}") Path outputDir) {
    this.outputDir = outputDir;
  }

  @JobWorker(type = "queue-payout")
  public Map<String, Object> queuePayout(
      @Variable String claimId,
      @Variable ClaimDetails claim,
      @Variable(optional = true) String reviewerNote) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("idempotencyKey", claimId);
    record.put("policyNumber", claim.policyNumber());
    record.put("amount", claim.amountClaimed());
    record.put("reviewerNote", reviewerNote == null ? "" : reviewerNote);
    record.put("queuedAt", Instant.now().toString());
    append("payout-queue.jsonl", record);
    return Map.of("payoutQueuedAt", record.get("queuedAt"));
  }

  @JobWorker(type = "send-rejection")
  public Map<String, Object> sendRejection(
      @Variable String claimId,
      @Variable(optional = true) String reviewerNote) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("idempotencyKey", claimId);
    record.put("message", "Your claim " + claimId + " was not approved."
        + (reviewerNote == null || reviewerNote.isBlank() ? "" : " Reviewer note: " + reviewerNote));
    record.put("sentAt", Instant.now().toString());
    append("rejection-notices.jsonl", record);
    return Map.of("rejectionSentAt", record.get("sentAt"));
  }

  private void append(String fileName, Map<String, Object> record) {
    try {
      Files.createDirectories(outputDir);
      Files.writeString(
          outputDir.resolve(fileName),
          json.writeValueAsString(record) + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException(e); // technical failure: the engine retries the job
    }
  }
}

package demo.claimtriage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.exception.BpmnError;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The worker's three outcomes, without an engine: complete, BPMN error, or technical failure. */
class ExtractClaimWorkerTest {

  private final ClaimValidator validator = new ClaimValidator(LocalDate.of(2026, 10, 10));

  private static ClaimExtractor returning(ClaimDetails claim) {
    return new ClaimExtractor() {
      public ClaimDetails extract(String text) { return claim; }
      public String name() { return "stub"; }
    };
  }

  private static ClaimExtractor throwing(RuntimeException e) {
    return new ClaimExtractor() {
      public ClaimDetails extract(String text) { throw e; }
      public String name() { return "stub"; }
    };
  }

  @Test
  void returnsTheClaimWhenTheExtractionIsUsable() {
    ClaimDetails claim = new ClaimDetails("AUTO", 3420.50, "2026-09-28", "POL-482913", "Rear-ended.", List.of());
    Map<String, Object> vars = new ExtractClaimWorker(returning(claim), validator).extract("text");
    assertThat(vars).containsEntry("claim", claim).containsEntry("extractedBy", "stub");
  }

  @Test
  void raisesABpmnErrorWhenValidationFails() {
    ClaimDetails claim = new ClaimDetails("OTHER", 0, "", "", "Something happened.", List.of());
    ExtractClaimWorker worker = new ExtractClaimWorker(returning(claim), validator);

    assertThatThrownBy(() -> worker.extract("text"))
        .isInstanceOfSatisfying(BpmnError.class, e -> {
          assertThat(e.getErrorCode()).isEqualTo(ExtractClaimWorker.EXTRACTION_FAILED);
          assertThat(e.getVariables()).isInstanceOfSatisfying(Map.class, v ->
              assertThat(v.get("extractionProblems")).asList().contains("No valid policy number."));
        });
  }

  @Test
  void raisesABpmnErrorWhenTheModelDeclines() {
    ExtractClaimWorker worker = new ExtractClaimWorker(
        throwing(new ExtractionException("The model declined to process this claim.")), validator);

    assertThatThrownBy(() -> worker.extract("text"))
        .isInstanceOfSatisfying(BpmnError.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ExtractClaimWorker.EXTRACTION_FAILED));
  }

  @Test
  void letsTechnicalFailuresEscapeSoTheEngineRetries() {
    ExtractClaimWorker worker = new ExtractClaimWorker(
        throwing(new IllegalStateException("connection reset")), validator);

    assertThatThrownBy(() -> worker.extract("text"))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(BpmnError.class);
  }
}

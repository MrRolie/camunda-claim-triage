package demo.claimtriage;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The LLM step of the process (service task "Extract claim details", job type "extract-claim").
 *
 * <p>Three outcomes, each handled by a different part of Camunda:
 * <ul>
 *   <li>Usable extraction: return the fields; the engine completes the job and moves on to the review.
 *   <li>Unusable extraction (model declined, or the fields fail validation): throw a BPMN error. The
 *       process model catches it and routes the claim to a person for manual triage.
 *   <li>Technical failure (network, rate limit, API down): let the exception escape. The engine fails the
 *       job, retries it (3 retries in the model), and raises an incident in Operate if it keeps failing.
 * </ul>
 */
@Component
public class ExtractClaimWorker {

  static final String EXTRACTION_FAILED = "EXTRACTION_FAILED";

  private final ClaimExtractor extractor;
  private final ClaimValidator validator;

  public ExtractClaimWorker(ClaimExtractor extractor, ClaimValidator validator) {
    this.extractor = extractor;
    this.validator = validator;
  }

  @JobWorker(type = "extract-claim")
  public Map<String, Object> extract(@Variable String claimText) {
    ClaimDetails claim;
    try {
      claim = extractor.extract(claimText);
    } catch (ExtractionException e) {
      throw unusable(List.of(e.getMessage()));
    }

    List<String> problems = validator.problems(claim);
    if (!problems.isEmpty()) {
      throw unusable(problems);
    }
    return Map.of("claim", claim, "extractedBy", extractor.name());
  }

  private RuntimeException unusable(List<String> problems) {
    return CamundaError.bpmnError(
        EXTRACTION_FAILED,
        String.join(" ", problems),
        Map.of("extractionProblems", problems, "extractedBy", extractor.name()));
  }
}

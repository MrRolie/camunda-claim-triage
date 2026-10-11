package demo.claimtriage;

import io.camunda.client.annotation.Deployment;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Starts the workers and deploys the process model and its two forms to Camunda on startup. */
@SpringBootApplication
@Deployment(resources = {
    "classpath:claim-triage.bpmn",
    "classpath:review-claim.form",
    "classpath:manual-triage.form"})
public class ClaimTriageApplication {

  private static final Logger log = LoggerFactory.getLogger(ClaimTriageApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(ClaimTriageApplication.class, args);
  }

  @Bean
  ClaimExtractor claimExtractor(
      @Value("${claims.extractor}") String mode,
      @Value("${claims.llm-model}") String model) {
    boolean hasKey = System.getenv("ANTHROPIC_API_KEY") != null && !System.getenv("ANTHROPIC_API_KEY").isBlank();
    boolean useClaude = switch (mode) {
      case "claude" -> true;
      case "rules" -> false;
      default -> hasKey;
    };
    ClaimExtractor extractor = useClaude ? new ClaudeClaimExtractor(model) : new RuleBasedClaimExtractor();
    log.info("Claim extraction runs on: {}", extractor.name());
    return extractor;
  }

  @Bean
  ClaimValidator claimValidator() {
    return new ClaimValidator(LocalDate.now());
  }
}

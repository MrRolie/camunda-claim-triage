package demo.claimtriage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimValidatorTest {

  private final ClaimValidator validator = new ClaimValidator(LocalDate.of(2026, 10, 10));

  private static ClaimDetails valid() {
    return new ClaimDetails("AUTO", 3420.50, "2026-09-28", "POL-482913", "Rear-ended at a red light.", List.of());
  }

  @Test
  void acceptsACompleteClaim() {
    assertThat(validator.problems(valid())).isEmpty();
  }

  @Test
  void flagsEveryMissingField() {
    ClaimDetails empty = new ClaimDetails("OTHER", 0, "", "", "Something happened.", List.of());
    assertThat(validator.problems(empty))
        .containsExactly("No amount claimed.", "No valid policy number.", "No incident date.");
  }

  @Test
  void rejectsAnUnknownTypeAFutureDateAndAnOversizedAmount() {
    ClaimDetails claim = new ClaimDetails("PET", 900_000, "2026-12-01", "POL-482913", "x", List.of());
    assertThat(validator.problems(claim)).hasSize(3)
        .anyMatch(p -> p.startsWith("Unknown claim type"))
        .anyMatch(p -> p.startsWith("Amount above the intake limit"))
        .anyMatch(p -> p.startsWith("Incident date is in the future"));
  }

  @Test
  void rejectsAMalformedDateAndPolicyNumber() {
    ClaimDetails claim = new ClaimDetails("AUTO", 100, "28/09/2026", "482913", "x", List.of());
    assertThat(validator.problems(claim))
        .containsExactly("No valid policy number.", "Incident date is not a valid date: 28/09/2026");
  }
}

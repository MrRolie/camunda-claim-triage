package demo.claimtriage;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deterministic checks on whatever the extractor returned. The model proposes; plain code decides whether
 * the proposal is fit to show a reviewer. An empty list means it is.
 */
public class ClaimValidator {

  private static final Set<String> CLAIM_TYPES = Set.of("AUTO", "PROPERTY", "TRAVEL", "OTHER");
  private static final double MAX_AMOUNT = 250_000;

  private final LocalDate today;

  public ClaimValidator(LocalDate today) {
    this.today = today;
  }

  public List<String> problems(ClaimDetails claim) {
    List<String> problems = new ArrayList<>();

    if (claim.claimType() == null || !CLAIM_TYPES.contains(claim.claimType())) {
      problems.add("Unknown claim type: " + claim.claimType());
    }
    if (claim.amountClaimed() <= 0) {
      problems.add("No amount claimed.");
    } else if (claim.amountClaimed() > MAX_AMOUNT) {
      problems.add("Amount above the intake limit of " + (long) MAX_AMOUNT + ": " + claim.amountClaimed());
    }
    if (claim.policyNumber() == null || !claim.policyNumber().matches("POL-\\d{6}")) {
      problems.add("No valid policy number.");
    }
    problems.addAll(dateProblems(claim.incidentDate()));
    if (claim.summary() == null || claim.summary().isBlank()) {
      problems.add("No summary.");
    }
    return problems;
  }

  private List<String> dateProblems(String incidentDate) {
    if (incidentDate == null || incidentDate.isBlank()) {
      return List.of("No incident date.");
    }
    try {
      LocalDate date = LocalDate.parse(incidentDate);
      return date.isAfter(today) ? List.of("Incident date is in the future: " + incidentDate) : List.of();
    } catch (DateTimeParseException e) {
      return List.of("Incident date is not a valid date: " + incidentDate);
    }
  }
}

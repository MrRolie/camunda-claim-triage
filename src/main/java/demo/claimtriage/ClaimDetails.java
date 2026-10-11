package demo.claimtriage;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * The fields the extraction step pulls out of a free-text claim.
 * The same record is the JSON schema Claude must answer in, and the process variable "claim".
 */
public record ClaimDetails(
    @JsonPropertyDescription("One of AUTO, PROPERTY, TRAVEL, OTHER")
    String claimType,

    @JsonPropertyDescription("Amount the claimant asks for, in dollars. 0 if no amount is stated")
    double amountClaimed,

    @JsonPropertyDescription("Date of the incident as YYYY-MM-DD. Empty string if not stated or ambiguous")
    String incidentDate,

    @JsonPropertyDescription("Policy number exactly as written, e.g. POL-123456. Empty string if absent")
    String policyNumber,

    @JsonPropertyDescription("One neutral sentence describing the claim, for the reviewer")
    String summary,

    @JsonPropertyDescription("Facts a claims reviewer would need that the claim does not state")
    List<String> missingInformation) {
}

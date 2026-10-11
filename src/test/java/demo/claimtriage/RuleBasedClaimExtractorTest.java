package demo.claimtriage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Runs the offline extractor on the same sample claims the README uses. */
class RuleBasedClaimExtractorTest {

  private final RuleBasedClaimExtractor extractor = new RuleBasedClaimExtractor();

  private static String sample(String name) throws IOException {
    return Files.readString(Path.of("samples", name));
  }

  @Test
  void extractsTheAutoClaim() throws IOException {
    ClaimDetails claim = extractor.extract(sample("claim-01-auto.txt"));
    assertThat(claim.claimType()).isEqualTo("AUTO");
    assertThat(claim.amountClaimed()).isEqualTo(3420.50);
    assertThat(claim.incidentDate()).isEqualTo("2026-09-28");
    assertThat(claim.policyNumber()).isEqualTo("POL-482913");
    assertThat(claim.missingInformation()).isEmpty();
  }

  @Test
  void extractsThePropertyClaim() throws IOException {
    ClaimDetails claim = extractor.extract(sample("claim-02-property.txt"));
    assertThat(claim.claimType()).isEqualTo("PROPERTY");
    assertThat(claim.amountClaimed()).isEqualTo(12800);
    assertThat(claim.policyNumber()).isEqualTo("POL-118204");
  }

  @Test
  void leavesTheVagueClaimEmptyRatherThanGuessing() throws IOException {
    ClaimDetails claim = extractor.extract(sample("claim-03-vague.txt"));
    assertThat(claim.amountClaimed()).isZero();
    assertThat(claim.policyNumber()).isEmpty();
    assertThat(claim.missingInformation())
        .containsExactly("policy number", "amount claimed", "incident date");
  }

  @Test
  void takesTheStatedAmountNotTheInjectedOne() throws IOException {
    ClaimDetails claim = extractor.extract(sample("claim-04-injection.txt"));
    assertThat(claim.claimType()).isEqualTo("TRAVEL");
    assertThat(claim.amountClaimed()).isEqualTo(640);
  }
}

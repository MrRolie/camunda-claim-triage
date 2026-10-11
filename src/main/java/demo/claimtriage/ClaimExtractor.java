package demo.claimtriage;

/** Turns free claim text into {@link ClaimDetails}. Two implementations: Claude, and an offline rule-based one. */
public interface ClaimExtractor {

  /**
   * @throws ExtractionException when no usable result can be produced (e.g. the model declined).
   *     Any other exception is treated as a technical failure and retried by the engine.
   */
  ClaimDetails extract(String claimText);

  /** Shown to the reviewer, so they know what produced the fields they are checking. */
  String name();
}

package demo.claimtriage;

/** The extractor ran but has nothing usable to hand on. A business outcome, not a crash. */
public class ExtractionException extends RuntimeException {

  public ExtractionException(String message) {
    super(message);
  }
}

package demo.claimtriage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline stand-in for the LLM: regular expressions and keywords. Lets anyone run the demo without an
 * API key, and gives the tests something deterministic. It is deliberately simple, not a rival to the model.
 */
public class RuleBasedClaimExtractor implements ClaimExtractor {

  private static final Pattern POLICY = Pattern.compile("\\bPOL-\\d{6}\\b");
  private static final Pattern AMOUNT = Pattern.compile("\\$\\s?(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?|\\d+(?:\\.\\d{2})?)");
  private static final Pattern DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");

  @Override
  public ClaimDetails extract(String claimText) {
    String policy = firstMatch(POLICY, claimText, 0);
    String amount = firstMatch(AMOUNT, claimText, 1);
    String date = firstMatch(DATE, claimText, 1);

    List<String> missing = new ArrayList<>();
    if (policy.isEmpty()) missing.add("policy number");
    if (amount.isEmpty()) missing.add("amount claimed");
    if (date.isEmpty()) missing.add("incident date");

    return new ClaimDetails(
        classify(claimText),
        amount.isEmpty() ? 0 : Double.parseDouble(amount.replace(",", "")),
        date,
        policy,
        firstSentence(claimText),
        missing);
  }

  @Override
  public String name() {
    return "offline rule-based extractor";
  }

  private static String classify(String text) {
    String t = text.toLowerCase(Locale.ROOT);
    if (containsAny(t, "car", "vehicle", "collision", "rear-ended", "bumper")) return "AUTO";
    if (containsAny(t, "pipe", "water damage", "roof", "basement", "kitchen", "fire")) return "PROPERTY";
    if (containsAny(t, "flight", "luggage", "baggage", "trip", "hotel")) return "TRAVEL";
    return "OTHER";
  }

  private static boolean containsAny(String text, String... words) {
    for (String w : words) {
      if (text.contains(w)) return true;
    }
    return false;
  }

  private static String firstMatch(Pattern pattern, String text, int group) {
    Matcher m = pattern.matcher(text);
    return m.find() ? m.group(group) : "";
  }

  private static String firstSentence(String text) {
    String flat = text.strip().replaceAll("\\s+", " ");
    int end = flat.indexOf(". ");
    String sentence = end > 0 ? flat.substring(0, end + 1) : flat;
    return sentence.length() > 200 ? sentence.substring(0, 197) + "..." : sentence;
  }
}

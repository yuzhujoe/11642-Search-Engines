import java.util.Comparator;

public class ValueComparator implements Comparator<String> {

	public int compare(String a, String b) {
		String[] word_a = a.trim().split(":");
		String[] word_b = b.trim().split(":");
		double score_a = Double.parseDouble(word_a[1]);
		double score_b = Double.parseDouble(word_b[1]);
		if (score_a > score_b) {
			return -1;
		} else if (score_a == score_b && word_a[0].compareTo(word_b[0]) < 0) {
			return -1;
		} else {
			// returning 0 would merge keys
			return 1;
		}
	}
}
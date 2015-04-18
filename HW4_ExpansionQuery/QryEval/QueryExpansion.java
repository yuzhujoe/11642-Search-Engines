import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;

public class QueryExpansion {
	private int fbDocs, fbTerms, fbMu;

	public QueryExpansion(int fbDocs, int fbTerms, int fbMu) {
		this.fbDocs = fbDocs;
		this.fbTerms = fbTerms;
		this.fbMu = fbMu;
	}

	/**
	 * 
	 * @param path
	 *            path to the initial ranking file
	 * @return A map with query id as key and lines from initial ranking file as
	 *         value
	 * @throws IOException
	 */
	public Map<Integer, List<String>> retrieveInitialRanking(String path)
			throws IOException {
		Map<Integer, List<String>> qrMap = new HashMap<Integer, List<String>>();
		FileInputStream fin = new FileInputStream(path);
		BufferedReader br = new BufferedReader(new InputStreamReader(fin));
		String line = null;
		// string format external_id:score:docid
		List<String> cur = new ArrayList<String>();
		int prev = -1;

		while ((line = br.readLine()) != null) {
			String[] words = line.trim().split("[ \t]");
			int qid = Integer.parseInt(words[0]);
			String extid = words[2];
			String score = words[4];
			int intid = Integer.MAX_VALUE;

			try {
				intid = getInternalDocid(extid);
			} catch (Exception e) {
				System.err.println("Docid Not Exist: " + extid);
			}

			if (prev == -1) {
				// first line
				prev = qid;
			} else if (qid != prev) {
				// result of another query
				qrMap.put(prev, cur);
				cur = new ArrayList<String>();
				prev = qid;
			}
			cur.add(String.format("%s:%s:%d", extid, score, intid));
		}
		qrMap.put(prev, cur);
		return qrMap;
	}

	/**
	 * Construct an expanded query from ranked result
	 * 
	 * @param sResult
	 *            sorted result with each line in format external_id:score:docid
	 * @return expanded query
	 * @throws IOException
	 */
	public String constructExpandQuery(List<String> sResult) throws IOException {
		Map<String, Double> termScore = new HashMap<String, Double>();
		final String field = "body";
		int len = Math.min(sResult.size(), this.fbDocs);
		double[] docConst = new double[len];
		DocLengthStore doclengthStore = QryEval.docLenStore;
		long clength = QryEval.READER.getSumTotalTermFreq(field);

		// iterate docs to retrieve p(I|d), length(d)
		for (int i = 0; i < len; i++) {
			String[] words = sResult.get(i).trim().split(":");
			int intid = Integer.parseInt(words[2]);
			double docScore = Double.parseDouble(words[1]); // p(I|d)
			long doclen = doclengthStore.getDocLength(field, intid); // length(d)

			docConst[i] = docScore / (double) (doclen + this.fbMu);
		}

		// iterate all doc to calcalate term score
		for (int i = 0; i < len; i++) {
			String[] words = sResult.get(i).trim().split(":");
			int intid = Integer.parseInt(words[2]);
			TermVector tv = new TermVector(intid, field);

			// iterate all terms in doc
			for (int j = 1; j < tv.stemsLength(); j++) {
				String term = tv.stemString(j);

				if (term.contains(".") || term.contains(",")) {
					continue;
				}

				long termFreq = tv.stemFreq(j);
				long ctf = tv.totalStemFreq(j);
				double probMle = ctf / (double) clength;

				if (!termScore.containsKey(term)) {
					// first time the term occurs
					double tempScore = termFreq * docConst[i];
					// add MLE of p(t|C) for all the docs
					for (int doc = 0; doc < len; doc++) {
						tempScore += this.fbMu * probMle * docConst[doc];
					}
					tempScore *= Math.log(1 / probMle);
					termScore.put(term, tempScore);
				} else {
					// update term frequency
					double tempScore = termScore.get(term);
					tempScore += (termFreq * docConst[i] * Math
							.log(1 / probMle));
					termScore.put(term, tempScore);
				}
			}
		}

		// sort termScore by value
		Comparator<Map.Entry<String, Double>> compare = new Comparator<Map.Entry<String, Double>>() {
			@Override
			public int compare(Map.Entry<String, Double> e1,
					Map.Entry<String, Double> e2) {
				// descending order
				return e2.getValue().compareTo(e1.getValue());
			}
		};

		List<Map.Entry<String, Double>> sortedScore = new ArrayList<Map.Entry<String, Double>>(
				termScore.entrySet());
		Collections.sort(sortedScore, compare);

		// retrieve the top terms and construct expanded query
		int tsize = Math.min(sortedScore.size(), this.fbTerms);
		String expandQuery = "#WAND (";
		
		for (int i = tsize-1; i >= 0; i--) {
			if (!expandQuery.equalsIgnoreCase("#wand (")) {
				expandQuery += " ";
			}
			expandQuery += String.format("%.4f", sortedScore.get(i).getValue()) + " " + sortedScore.get(i).getKey();
		}
		expandQuery += ")"; 

		return expandQuery;
	}

	/**
	 * Finds the internal document id for a document specified by its external
	 * id, e.g. clueweb09-enwp00-88-09710. If no such document exists, it throws
	 * an exception.
	 * 
	 * @param externalId
	 *            The external document id of a document.s
	 * @return An internal doc id suitable for finding document vectors etc.
	 * @throws Exception
	 */
	public int getInternalDocid(String externalId) throws Exception {
		Query q = new TermQuery(new Term("externalId", externalId));

		IndexSearcher searcher = new IndexSearcher(QryEval.READER);
		TopScoreDocCollector collector = TopScoreDocCollector.create(1, false);
		searcher.search(q, collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs;

		if (hits.length < 1) {
			throw new Exception("External id not found.");
		} else {
			return hits[0].doc;
		}
	}
}

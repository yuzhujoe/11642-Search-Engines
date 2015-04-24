import java.io.IOException;
import java.util.*;

import org.apache.lucene.index.Term;

public class LetorScoreFromTermVector {
	private List<String> qstems;
	private int docId;
	private RetrievalModelLetor model;
	private String field;
	private TermVector tv;

	public LetorScoreFromTermVector(RetrievalModelLetor model, String field,
			List<String> qstems, int docId) throws IOException {
		this.model = model;
		this.field = field;
		this.qstems = qstems;
		this.docId = docId;
		if (QryEval.READER.getTermVector(docId, field) == null) {
			this.tv = null;
		} else {
			this.tv = new TermVector(docId, field);
		}
	}

	public double getBM25Score() throws IOException {
		double totalScore = 0;
		double k_1, b, k_3;
		k_1 = model.getParameter("k_1");
		b = model.getParameter("b");
		k_3 = model.getParameter("k_3");

		// constants (N, avg_doclen) stored in index
		int N = QryEval.READER.numDocs();
		// System.out.println(field);
		double avg_doclen = QryEval.READER.getSumTotalTermFreq(field)
				/ (double) QryEval.READER.getDocCount(field);
		DocLengthStore doclengthStore = QryEval.docLenStore;
		long doclen = doclengthStore.getDocLength(field, docId);

		for (String stem : qstems) {
			int index = tv.getIndex(stem);
			if (index == -1)
				continue;

			int tf = tv.stemFreq(index);
			int df = tv.stemDf(index);
			int qtf = 1;
			double idf, tf_weight, user_weight;
			idf = Math.max(0, Math.log((N - df + 0.5) / (df + 0.5)));
			tf_weight = tf
					/ (double) (tf + k_1 * ((1 - b) + b * doclen / avg_doclen));
			user_weight = (k_3 + 1) * qtf / (k_3 + qtf);
			double docScore = idf * tf_weight * user_weight;
			totalScore += docScore;
		}
		return totalScore;
	}

	public double getIndriScore() throws IOException {
		double totalScore = 1.0;
		double mu, lambda;
		mu = model.getParameter("mu");
		lambda = model.getParameter("lambda");
		boolean isMatch = false;

		DocLengthStore doclengthStore = QryEval.docLenStore;
		long doclen = doclengthStore.getDocLength(field, docId);

		for (String stem : qstems) {
			int index = tv.getIndex(stem);
			int tf;
			long ctf;

			if (index == -1) {
				// if the term is not in the document
				tf = 0;
				ctf = QryEval.READER.totalTermFreq(new Term(field, stem));
			} else {
				// the term is in the document
				isMatch = true;
				tf = tv.stemFreq(index);
				ctf = tv.totalStemFreq(index);
			}
			double mleProb = ctf
					/ (double) QryEval.READER.getSumTotalTermFreq(field);
			double docScore = (1 - lambda) * (tf + mu * mleProb)
					/ (doclen + mu) + lambda * mleProb;
			totalScore *= Math.pow(docScore, 1.0 / qstems.size());
		}

		return (isMatch ? totalScore : 0);
	}
	
	public double getTfIdfScore() throws IOException {
		double totalScore = 0;
		int N = QryEval.READER.numDocs();
		
		for (String stem : qstems) {
			int index = tv.getIndex(stem);
			if (index == -1) continue;
			
			int tf = tv.stemFreq(index);
			int df = tv.stemDf(index);
			double idf = Math.max(0, Math.log((N - df + 0.5) / (df + 0.5)));
			totalScore += tf * idf;
		}
		
		return totalScore;
	}
	
	public double getBooleanScore() {
		for (String stem : qstems) {
			int index = tv.getIndex(stem);
			if (index == -1) {
				return 0;
			}
		}
		return 1.0;
	}

	public double getTermoverlapScore() throws IOException {
		double totalScore = 0;
		int totalMatch = 0;
		for (String stem : qstems) {
			int index = tv.getIndex(stem);
			if (index != -1) {
				totalMatch++;
			}
		}
		if (qstems.size() > 0) {
			totalScore = totalMatch / (double) qstems.size();
		}

		return totalScore;
	}

	public TermVector getTv() {
		return this.tv;
	}
}

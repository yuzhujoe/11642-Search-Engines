import java.io.*;
import java.util.*;

public class QryopSlWeight extends QryopSl {
	protected List<Double> weight;
	private final boolean WAND;
	private boolean isWeight;

	/**
	 * It is convenient for the constructor to accept a variable number of
	 * arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
	 * 
	 * @param q
	 *            A query argument (a query operator).
	 */
	public QryopSlWeight(boolean type, Qryop... q) {
		for (int i = 0; i < q.length; i++)
			this.args.add(q[i]);
		this.weight = new ArrayList<Double>();
		this.isWeight = true;
		WAND = type;
	}

	/**
	 * Appends an argument to the list of query operator arguments. This
	 * simplifies the design of some query parsing architectures.
	 * 
	 * @param {q} q The query argument (query operator) to append.
	 * @return void
	 * @throws IOException
	 */
	public void add(Qryop a) {
		this.args.add(a);
		this.isWeight = true;
	}

	public void add(double weight) {
		this.weight.add(weight);
		this.isWeight = false;
	}

	public boolean getIsWeight() {
		return this.isWeight;
	}

	public void setIsWeight(boolean isWeight) {
		this.isWeight = isWeight;
	}

	/**
	 * Evaluates the query operator, including any child operators and returns
	 * the result.
	 * 
	 * @param r
	 *            A retrieval model that controls how the operator behaves.
	 * @return The result of evaluating the query.
	 * @throws IOException
	 */
	public QryResult evaluate(RetrievalModel r) throws IOException {
		allocArgPtrs(r);
		QryResult result = new QryResult();
		
		int argSize = this.argPtrs.size();
		ArgPtr[] ptrArray = new ArgPtr[argSize];

		for (int j = 0; j < argSize; j++) {
			ptrArray[j] = this.argPtrs.get(j);
		}

		int curSize = argSize;

		// loop until all lists reach the end
		while (curSize > 0) {
			int minDocid = Integer.MAX_VALUE;

			// loop list of args to find min docid
			for (ArgPtr curPtr : ptrArray) {
				if (curPtr.nextDoc >= curPtr.scoreList.scores.size()) {
					continue;
				}
				int curDocid = curPtr.scoreList.getDocid(curPtr.nextDoc);
				if (curDocid < minDocid) {
					// set new minDocid
					minDocid = curDocid;
				}
			}

			if (minDocid == Integer.MAX_VALUE)
				break;

			// found the min docid in this round
			// calculate docScore
			double docScore;

			if (WAND)
				docScore = 1;
			else
				docScore = 0;

			for (int i = 0; i < argSize; i++) {
				ArgPtr curPtr = ptrArray[i];
				int curDocid = Integer.MAX_VALUE;
				if (curPtr.nextDoc < curPtr.scoreList.scores.size()) {
					curDocid = curPtr.scoreList.getDocid(curPtr.nextDoc);
				}

				if (curDocid == minDocid) {
					// get docScore
					if (WAND) {
						docScore *= Math.pow(curPtr.scoreList
								.getDocidScore(curPtr.nextDoc++), this.weight
								.get(i));
					} else {
						docScore += curPtr.scoreList
								.getDocidScore(curPtr.nextDoc++)
								* this.weight.get(i);
					}
					ptrArray[i] = curPtr;
					if (curPtr.nextDoc >= curPtr.scoreList.scores.size()) {
						curSize--;
					}
				} else {
					// get default score
					if (WAND) {
						docScore *= Math.pow(((QryopSl) this.args.get(i))
								.getDefaultScore(r, minDocid), this.weight
								.get(i));
					} else {
						docScore += ((QryopSl) this.args.get(i))
								.getDefaultScore(r, minDocid)
								* this.weight.get(i);
					}
				}
			}

			double totalWeight = 0;
			for (double w : this.weight) {
				totalWeight += w;
			}

			if (totalWeight > 0) {
				// add min docid to result
				if (WAND) {
					result.docScores.add(minDocid,
							Math.pow(docScore, 1 / totalWeight));
				} else {
					result.docScores.add(minDocid, docScore / totalWeight);
				}
			}
		}

		freeArgPtrs();

		return result;
	}

	/*
	 * Calculate the default score for the specified document if it does not
	 * match the query operator. This score is 0 for many retrieval models, but
	 * not all retrieval models.
	 * 
	 * @param r A retrieval model that controls how the operator behaves.
	 * 
	 * @param docid The internal id of the document that needs a default score.
	 * 
	 * @return The default score.
	 */
	public double getDefaultScore(RetrievalModel r, long docid)
			throws IOException {
		if (r instanceof RetrievalModelIndri) {
			double score;

			if (WAND) {
				score = 1;
			} else {
				score = 0;
			}

			int qSize = this.args.size();
			double totalWeight = 0;

			for (int i = 0; i < qSize; i++) {
				double w = this.weight.get(i);
				if (WAND) {
					score *= Math.pow(((QryopSl) this.args.get(i))
							.getDefaultScore(r, docid), w);
				} else {
					score += ((QryopSl) this.args.get(i)).getDefaultScore(r,
							docid) * w;
				}
				totalWeight += w;
			}

			if (totalWeight > 0) {
				if (WAND) {
					score = Math.pow(score, 1 / totalWeight);
				} else {
					score /= totalWeight;
				}
				return score;
			} else {
				return 1.0;
			}
		}

		System.err.println("WSUM Error: Model not Indri");
		return 0.0;
	}

	/*
	 * Return a string version of this query operator.
	 * 
	 * @return The string version of this query operator.
	 */
	public String toString() {

		String result = new String();

		for (int i = 0; i < this.args.size(); i++)
			result += this.args.get(i).toString() + " ";

		return ("#WAND( " + result + ")");
	}
}

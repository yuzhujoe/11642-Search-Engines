public class RetrievalModelLetor extends RetrievalModel {
	private double svmC;
	private double k_1, b, k_3;
	private double mu, lambda;
	public String trainingQueryFile, trainingQrelsFile,
			trainingFeatureVectorFile;
	public String pageRankFile;
	public String featureDisable;
	public String svmRankLearnPath, svmRankClassifyPath, svmRankModelFile;
	public String testingFeatureVectorsFile, testingDocumentScores;

	public RetrievalModelLetor() {

	}

	public RetrievalModelLetor(double svmC, double k_1, double b, double k_3,
			double mu, double lambda) {
		this.svmC = svmC;
		this.k_1 = k_1;
		this.b = b;
		this.k_3 = k_3;
		this.mu = mu;
		this.lambda = lambda;
	}

	/**
	 * Set a retrieval model parameter.
	 * 
	 * @param parameterName
	 * @param parametervalue
	 * @return Always false because this retrieval model has no parameters.
	 */
	public boolean setParameter(String parameterName, double value) {
		if (parameterName.equals("svmC")) {
			svmC = value;
		} else if (parameterName.equals("k_1")) {
			k_1 = value;
		} else if (parameterName.equals("b")) {
			b = value;
		} else if (parameterName.equals("k_3")) {
			k_3 = value;
		} else if (parameterName.equals("mu")) {
			mu = value;
		} else if (parameterName.equals("lambda")) {
			lambda = value;
		} else {
			System.err
					.println("Error: Unknown parameter name for retrieval model "
							+ "BM25: " + parameterName);
			return false;
		}
		return true;
	}

	/**
	 * Set a retrieval model parameter.
	 * 
	 * @param parameterName
	 * @param parametervalue
	 * @return Always false because this retrieval model has no parameters.
	 */
	public boolean setParameter(String parameterName, String value) {
		if (parameterName.equals("trainingQueryFile")) {
			trainingQueryFile = value;
		} else if (parameterName.equals("trainingQrelsFile")) {
			trainingQrelsFile = value;
		} else if (parameterName.equals("trainingFeatureVectorFile")) {
			trainingFeatureVectorFile = value;
		} else if (parameterName.equals("pageRankFile")) {
			pageRankFile = value;
		} else if (parameterName.equals("featureDisable")) {
			featureDisable = value;
		} else if (parameterName.equals("svmRankLearnPath")) {
			svmRankLearnPath = value;
		} else if (parameterName.equals("svmRankClassifyPath")) {
			svmRankClassifyPath = value;
		} else if (parameterName.equals("svmRankModelFile")) {
			svmRankModelFile = value;
		} else if (parameterName.equals("testingFeatureVectorsFile")) {
			testingFeatureVectorsFile = value;
		} else if (parameterName.equals("testingDocumentScores")) {
			testingDocumentScores = value;
		} else {
			System.err
					.println("Error: Unknown parameter name for retrieval model "
							+ "BM25: " + parameterName);
			return false;
		}
		return true;
	}

	public double getParameter(String parameterName) {
		if (parameterName.equals("svmC")) {
			return svmC;
		} else if (parameterName.equals("k_1")) {
			return k_1;
		} else if (parameterName.equals("b")) {
			return b;
		} else if (parameterName.equals("k_3")) {
			return k_3;
		} else if (parameterName.equals("mu")) {
			return mu;
		} else if (parameterName.equals("lambda")) {
			return lambda;
		} else {
			return -1;
		}
	}
}
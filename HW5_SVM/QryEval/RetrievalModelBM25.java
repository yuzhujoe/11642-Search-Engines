public class RetrievalModelBM25 extends RetrievalModel {
	private double k_1, b, k_3;
	
	public RetrievalModelBM25() {
		
	}
	
	public RetrievalModelBM25(double k_1, double b, double k_3) {
		this.k_1 = k_1;
		this.b = b;
		this.k_3 = k_3;
	}
  /**
   * Set a retrieval model parameter.
   * @param parameterName
   * @param parametervalue
   * @return Always false because this retrieval model has no parameters.
   */
  public boolean setParameter (String parameterName, double value) {
	  if (parameterName.equals("k_1")) {
		  k_1 = value;
	  } else if (parameterName.equals("b")) {
		  b = value;
	  } else if (parameterName.equals("k_3")) {
		  k_3 = value;
	  } else {
		  System.err.println ("Error: Unknown parameter name for retrieval model " +
				  "BM25: " +
				  parameterName);
		  return false;
	  }
	  return true;
  }

  /**
   * Set a retrieval model parameter.
   * @param parameterName
   * @param parametervalue
   * @return Always false because this retrieval model has no parameters.
   */
  public boolean setParameter (String parameterName, String value) {
	  if (parameterName.equals("k_1")) {
		  k_1 = Double.parseDouble(value);
	  } else if (parameterName.equals("b")) {
		  b = Double.parseDouble(value);
	  } else if (parameterName.equals("k_3")) {
		  k_3 = Double.parseDouble(value);
	  } else {
		  System.err.println ("Error: Unknown parameter name for retrieval model " +
				  "BM25: " +
				  parameterName);
		  return false;
	  }
	  return true;
  }

  public double getParameter (String parameterName) {
	  if (parameterName.equals("k_1")) {
		  return k_1;
	  } else if (parameterName.equals("b")) {
		  return b;
	  } else if (parameterName.equals("k_3")) {
		  return k_3;
	  } else {
		  return -1;
	  }
  }

}

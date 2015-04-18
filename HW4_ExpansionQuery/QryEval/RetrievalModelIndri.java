public class RetrievalModelIndri extends RetrievalModel {
	private double mu, lambda;
	
	public RetrievalModelIndri() {
		
	}
	
	public RetrievalModelIndri(double mu, double lambda) {
		this.mu = mu;
		this.lambda = lambda;
	}
  /**
   * Set a retrieval model parameter.
   * @param parameterName
   * @param parametervalue
   * @return Always false because this retrieval model has no parameters.
   */
  public boolean setParameter (String parameterName, double value) {
	  if (parameterName.equals("mu")) {
		  mu = value;
	  } else if (parameterName.equals("lambda")) {
		  lambda = value;
	  } else {
		  System.err.println ("Error: Unknown parameter name for retrieval model " +
				  "Indri: " +
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
	  if (parameterName.equals("mu")) {
		  mu = Double.parseDouble(value);
	  } else if (parameterName.equals("lambda")) {
		  lambda = Double.parseDouble(value);
	  } else {
		  System.err.println ("Error: Unknown parameter name for retrieval model " +
				  "Indri: " +
				  parameterName);
		  return false;
	  }
	  return true;
  }

  public double getParameter (String parameterName) {
	  if (parameterName.equals("mu")) {
		  return mu;
	  } else if (parameterName.equals("lambda")) {
		  return lambda;
	  } else {
		  return -1;
	  }
  }

}

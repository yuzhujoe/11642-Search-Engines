/**
 *  This class implements the SCORE operator for all retrieval models.
 *  The single argument to a score operator is a query operator that
 *  produces an inverted list.  The SCORE operator uses this
 *  information to produce a score list that contains document ids and
 *  scores.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

public class QryopSlScore extends QryopSl {
  private String field = "body";
  private int ctf = 0;

  /**
   *  Construct a new SCORE operator.  The SCORE operator accepts just
   *  one argument.
   *  @param q The query operator argument.
   *  @return @link{QryopSlScore}
   */
  public QryopSlScore(Qryop q) {
    this.args.add(q);
  }

  /**
   *  Construct a new SCORE operator.  Allow a SCORE operator to be
   *  created with no arguments.  This simplifies the design of some
   *  query parsing architectures.
   *  @return @link{QryopSlScore}
   */
  public QryopSlScore() {
  }

  /**
   *  Appends an argument to the list of query operator arguments.  This
   *  simplifies the design of some query parsing architectures.
   *  @param q The query argument to append.
   */
  public void add (Qryop a) {
    this.args.add(a);
  }

  /**
   *  Evaluate the query operator.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean)
    	return (evaluateBoolean (r));
    else if (r instanceof RetrievalModelRankedBoolean)
    	return (evaluateRankedBoolean(r));
    else if (r instanceof RetrievalModelBM25 || r instanceof RetrievalModelLetor)
    	return (evaluateBM25(r));
    else if (r instanceof RetrievalModelIndri)
    	return (evaluateIndri(r));

    return null;
  }

 /**
   *  Evaluate the query operator for boolean retrieval models.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

    // Evaluate the query argument.

    QryResult result = args.get(0).evaluate(r);

    // Each pass of the loop computes a score for one document. Note:
    // If the evaluate operation above returned a score list (which is
    // very possible), this loop gets skipped.

    for (int i = 0; i < result.invertedList.df; i++) {

      // DIFFERENT RETRIEVAL MODELS IMPLEMENT THIS DIFFERENTLY. 
      // Unranked Boolean. All matching documents get a score of 1.0.

      result.docScores.add(result.invertedList.postings.get(i).docid,
			   (float) 1.0);
    }

    // The SCORE operator should not return a populated inverted list.
    // If there is one, replace it with an empty inverted list.

    if (result.invertedList.df > 0)
	result.invertedList = new InvList();

    return result;
  }
  
  public QryResult evaluateRankedBoolean(RetrievalModel r) throws IOException {
	    // Evaluate the query argument.
	    QryResult result = args.get(0).evaluate(r);

	    for (int i = 0; i < result.invertedList.df; i++) {

	      result.docScores.add(result.invertedList.postings.get(i).docid,
				   (float) (result.invertedList.getTf(i)));
	    }

	    if (result.invertedList.df > 0)
		result.invertedList = new InvList();

	    return result;
	  }
  
  	  public QryResult evaluateBM25(RetrievalModel r) throws IOException {
	    // Evaluate the query argument.
	    QryResult result = args.get(0).evaluate(r);
	    
	    // parameters stored in retrieval model
	    double k_1, b, k_3;
	    k_1 = r.getParameter("k_1");
	    b = r.getParameter("b");
	    k_3 = r.getParameter("k_3");
	    
	    // constants (N, avg_doclen) stored in index
	    int N = QryEval.READER.numDocs();
	    String field = result.invertedList.field;
	    //System.out.println(field);
	    double avg_doclen = QryEval.READER.getSumTotalTermFreq(field) /
	            (double) QryEval.READER.getDocCount(field);
	    DocLengthStore doclengthStore = QryEval.docLenStore;

	    // Each pass of the loop computes a score for one document. Note:
	    // If the evaluate operation above returned a score list (which is
	    // very possible), this loop gets skipped.
	    int df = result.invertedList.df;
	    
	    for (int i = 0; i < df; i++) {
	    	int tf = result.invertedList.getTf(i);
	    	int qtf = 1;
	    	int docid = result.invertedList.postings.get(i).docid;
	    	long doclen = doclengthStore.getDocLength(field, docid);
	    	double idf, tf_weight, user_weight;
	    	idf = Math.max(0, Math.log((N - df + 0.5) / (df + 0.5)));
	    	tf_weight = tf / (double)(tf + k_1 * ((1-b) + b * doclen / avg_doclen));
	    	user_weight = (k_3 + 1) * qtf / (k_3 + qtf);
	    	
	    	double docScore = idf * tf_weight * user_weight;
	    	
	    	result.docScores.add(docid, docScore);
	    }

	    if (df > 0)
	    	result.invertedList = new InvList();

	    return result;
	  }
  	  
  	public QryResult evaluateIndri(RetrievalModel r) throws IOException {
	    // Evaluate the query argument.
	    QryResult result = args.get(0).evaluate(r);
	    
	    double mu, lambda;
	    mu = r.getParameter("mu");
	    lambda = r.getParameter("lambda");
	    
	    // constants (N, avg_doclen) stored in index
	    this.field = result.invertedList.field;
	    this.ctf = result.invertedList.ctf;
	    DocLengthStore doclengthStore = QryEval.docLenStore;
	    double mleProb = this.ctf / (double)QryEval.READER.getSumTotalTermFreq(field);
	    
	    int df = result.invertedList.df;
	    
	    for (int i = 0; i < df; i++) {
	    	int tf = result.invertedList.getTf(i);
	    	int docid = result.invertedList.postings.get(i).docid;
	    	long doclen = doclengthStore.getDocLength(field, docid);
	    	double docScore = (1 - lambda) * (tf + mu * mleProb) / (doclen + mu)
	    			+ lambda * mleProb;
	    	result.docScores.add(docid, docScore);
	    }
	    
	    if (df > 0)
	    	result.invertedList = new InvList();

	    return result;
  	}

  /*
   *  Calculate the default score for a document that does not match
   *  the query argument.  This score is 0 for many retrieval models,
   *  but not all retrieval models.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @param docid The internal id of the document that needs a default score.
   *  @return The default score.
   */
  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {

    if (r instanceof RetrievalModelIndri) {
    	double mu, lambda;
	    mu = r.getParameter("mu");
	    lambda = r.getParameter("lambda");
	    
	    DocLengthStore doclengthStore = QryEval.docLenStore;
	    double mleProb = ctf / (double)QryEval.READER.getSumTotalTermFreq(field);
	    
    	long doclen = doclengthStore.getDocLength(field, (int)docid);
    	double docScore = (1 - lambda) * (mu * mleProb) / (doclen + mu) 
    			+ lambda * mleProb;

	    return docScore;
    }

    return 0.0;
  }

  /**
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
    
    String result = new String ();

    for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
      result += (i.next().toString() + " ");

    return ("#SCORE( " + result + ")");
  }
}

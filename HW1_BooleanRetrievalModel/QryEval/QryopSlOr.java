/**
 *  This class implements the OR operator for all retrieval models.
 *
 */

import java.io.*;
import java.util.*;

public class QryopSlOr extends QryopSl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *  @param q A query argument (a query operator).
   */
  public QryopSlOr(Qryop... q) {
    for (int i = 0; i < q.length; i++)
      this.args.add(q[i]);
  }

  /**
   *  Appends an argument to the list of query operator arguments.  This
   *  simplifies the design of some query parsing architectures.
   *  @param {q} q The query argument (query operator) to append.
   *  @return void
   *  @throws IOException
   */
  public void add (Qryop a) {
    this.args.add(a);
  }

  /**
   *  Evaluates the query operator, including any child operators and
   *  returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
    	return (evaluateBoolean(r));
    } else if (r instanceof RetrievalModelRankedBoolean) {
    	return (evaluateRankedBoolean(r));
    }

    return null;
  }

  /**
   *  Evaluates the query operator for boolean retrieval models,
   *  including any child operators and returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateBoolean (RetrievalModel r) throws IOException {

	  //  Initialization
	  allocArgPtrs (r);
	  QryResult result = new QryResult ();
	
	  Set<Integer> idSet = new HashSet<Integer>();
	  
	  for (int i = 0; i < this.argPtrs.size(); i++) {
		  ArgPtr ptr = this.argPtrs.get(i);
		  for ( ; ptr.nextDoc < ptr.scoreList.scores.size(); ptr.nextDoc ++) {
			  int ptrDocid = ptr.scoreList.getDocid(ptr.nextDoc);
			  double docScore = 1.0;
			  if (!idSet.contains(ptrDocid)) {
				  result.docScores.add (ptrDocid, docScore);
				  idSet.add(ptrDocid);
			  }
		  }
	  }
	
	  idSet.clear();
	  freeArgPtrs ();
	
	  return result;
  }
  
  public QryResult evaluateRankedBoolean (RetrievalModel r) throws IOException {

	  //  Initialization
	
	  allocArgPtrs (r);
	  QryResult result = new QryResult ();
	
	  Map<Integer, Double> idMap = new HashMap<Integer, Double>();
	  
	  for (int i = 0; i < this.argPtrs.size(); i++) {
		  ArgPtr ptr = this.argPtrs.get(i);
		  for ( ; ptr.nextDoc < ptr.scoreList.scores.size(); ptr.nextDoc ++) {
			  int ptrDocid = ptr.scoreList.getDocid(ptr.nextDoc);
			  double docScore = ptr.scoreList.getDocidScore(ptr.nextDoc);
			  
			  if (!idMap.containsKey(ptrDocid) || idMap.get(ptrDocid) < docScore) {
				  idMap.put(ptrDocid, docScore);
			  }
		  }
	  }
	  
	  Iterator itr = idMap.entrySet().iterator();
	  while (itr.hasNext()) {
	      Map.Entry<Integer, Double> pairs = (Map.Entry)itr.next();
	      result.docScores.add(pairs.getKey(), pairs.getValue());
	      itr.remove(); // avoids a ConcurrentModificationException
	  }
	
	  idMap.clear();
	  freeArgPtrs ();
	
	  return result;
  }

  /*
   *  Calculate the default score for the specified document if it
   *  does not match the query operator.  This score is 0 for many
   *  retrieval models, but not all retrieval models.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @param docid The internal id of the document that needs a default score.
   *  @return The default score.
   */
  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean)
      return (0.0);

    return 0.0;
  }

  /*
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
    
    String result = new String ();

    for (int i=0; i<this.args.size(); i++)
      result += this.args.get(i).toString() + " ";

    return ("#AND( " + result + ")");
  }
}

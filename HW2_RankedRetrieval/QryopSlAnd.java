/**
 *  This class implements the AND operator for all retrieval models.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

public class QryopSlAnd extends QryopSl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *  @param q A query argument (a query operator).
   */
  public QryopSlAnd(Qryop... q) {
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
    } else if (r instanceof RetrievalModelIndri) {
    	return (evaluateIndri(r));
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
	
	  //  Sort the arguments so that the shortest lists are first.  This
	  //  improves the efficiency of exact-match AND without changing
	  //  the result.
	
	  for (int i=0; i<(this.argPtrs.size()-1); i++) {
		  for (int j=i+1; j<this.argPtrs.size(); j++) {
			  if (this.argPtrs.get(i).scoreList.scores.size() >
				  this.argPtrs.get(j).scoreList.scores.size()) {
				  ScoreList tmpScoreList = this.argPtrs.get(i).scoreList;
				  this.argPtrs.get(i).scoreList = this.argPtrs.get(j).scoreList;
				  this.argPtrs.get(j).scoreList = tmpScoreList;
			  }
		  }
	  }
	
	  //  Exact-match AND requires that ALL scoreLists contain a
	  //  document id.  Use the first (shortest) list to control the
	  //  search for matches.
	
	  //  Named loops are a little ugly.  However, they make it easy
	  //  to terminate an outer loop from within an inner loop.
	  //  Otherwise it is necessary to use flags, which is also ugly.
	
	  ArgPtr ptr0 = this.argPtrs.get(0);
	
	  EVALUATEDOCUMENTS:
      for ( ; ptr0.nextDoc < ptr0.scoreList.scores.size(); ptr0.nextDoc ++) {
	
    	  int ptr0Docid = ptr0.scoreList.getDocid (ptr0.nextDoc);
    	  double docScore = 1.0;
	
    	  //  Do the other query arguments have the ptr0Docid?
	
    	  for (int j=1; j<this.argPtrs.size(); j++) {
	
    		  ArgPtr ptrj = this.argPtrs.get(j);
	
    		  while (true) {
    			  if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
    				  break EVALUATEDOCUMENTS;		// No more docs can match
    			  else if (ptrj.scoreList.getDocid (ptrj.nextDoc) > ptr0Docid)
    				  continue EVALUATEDOCUMENTS;	// The ptr0docid can't match.
    			  else if (ptrj.scoreList.getDocid (ptrj.nextDoc) < ptr0Docid)
    				  ptrj.nextDoc ++;			// Not yet at the right doc.
    			  else
    				  break;				// ptrj matches ptr0Docid
    		  }
    	  }
	
    	  //  The ptr0Docid matched all query arguments, so save it.
	
    	  result.docScores.add (ptr0Docid, docScore);
	  }
	
	  freeArgPtrs ();
	
	  return result;
  }
  
  public QryResult evaluateRankedBoolean (RetrievalModel r) throws IOException {

	  //  Initialization
	
	  allocArgPtrs (r);
	  QryResult result = new QryResult ();
	
	  //  Sort the arguments so that the shortest lists are first.  This
	  //  improves the efficiency of exact-match AND without changing
	  //  the result.
	
	  for (int i=0; i<(this.argPtrs.size()-1); i++) {
		  for (int j=i+1; j<this.argPtrs.size(); j++) {
			  if (this.argPtrs.get(i).scoreList.scores.size() >
				  this.argPtrs.get(j).scoreList.scores.size()) {
				  ScoreList tmpScoreList = this.argPtrs.get(i).scoreList;
				  this.argPtrs.get(i).scoreList = this.argPtrs.get(j).scoreList;
				  this.argPtrs.get(j).scoreList = tmpScoreList;
			  }
		  }
	  }
	
	  //  Exact-match AND requires that ALL scoreLists contain a
	  //  document id.  Use the first (shortest) list to control the
	  //  search for matches.
	
	  //  Named loops are a little ugly.  However, they make it easy
	  //  to terminate an outer loop from within an inner loop.
	  //  Otherwise it is necessary to use flags, which is also ugly.
	
	  ArgPtr ptr0 = this.argPtrs.get(0);
	
	  EVALUATEDOCUMENTS:
      for ( ; ptr0.nextDoc < ptr0.scoreList.scores.size(); ptr0.nextDoc ++) {
	
    	  int ptr0Docid = ptr0.scoreList.getDocid (ptr0.nextDoc);
    	  double docScore = ptr0.scoreList.getDocidScore(ptr0.nextDoc);
	
    	  //  Do the other query arguments have the ptr0Docid?
	
    	  for (int j=1; j<this.argPtrs.size(); j++) {
	
    		  ArgPtr ptrj = this.argPtrs.get(j);
	
    		  while (true) {
    			  if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
    				  break EVALUATEDOCUMENTS;		// No more docs can match
    			  else if (ptrj.scoreList.getDocid (ptrj.nextDoc) > ptr0Docid)
    				  continue EVALUATEDOCUMENTS;	// The ptr0docid can't match.
    			  else if (ptrj.scoreList.getDocid (ptrj.nextDoc) < ptr0Docid)
    				  ptrj.nextDoc ++;			// Not yet at the right doc.
    			  else
    				  break;				// ptrj matches ptr0Docid
    		  }
    		  double ptrjScore = ptrj.scoreList.getDocidScore(ptrj.nextDoc);
    		  if (ptrjScore < docScore) {
    			  docScore = ptrjScore;
    		  }
    	  }
	
    	  //  The ptr0Docid matched all query arguments, so save it.
	
    	  result.docScores.add (ptr0Docid, docScore);
	  }
	
	  freeArgPtrs ();
	
	  return result;
  }
  
  public QryResult evaluateIndri(RetrievalModel r) throws IOException {
	System.out.println("evaluate Indri");
	allocArgPtrs (r);
	QryResult result = new QryResult ();
  
    List<ArgPtr> ptrList = new ArrayList<ArgPtr>(this.argPtrs.size());
  
    for (int j = 0; j < this.argPtrs.size(); j++) {
		ptrList.add(j, this.argPtrs.get(j));
    }
  
    int curSize = ptrList.size();
    
    while (curSize > 0) {
		int minDocid = Integer.MAX_VALUE;
		
		// loop list of args to find min docid
		int j = 0;
		for (ArgPtr curPtr : ptrList) {
			if (curPtr.nextDoc >= curPtr.scoreList.scores.size()) {
				continue;
			}
			int curDocid = curPtr.scoreList.getDocid(curPtr.nextDoc); 
			if (curDocid < minDocid) {
				// set new minDocid
				minDocid = curDocid;
			}
			j++;
		}
		
		// found the min docid in this round
		// calculate docScore
		double docScore = 0;
		
		for (int i = 0; i < ptrList.size(); i++) {
			ArgPtr curPtr = ptrList.get(i);
			if (curPtr.nextDoc >= curPtr.scoreList.scores.size()) {
				continue;
			}
			int curDocid = curPtr.scoreList.getDocid(curPtr.nextDoc);
			if (curDocid == minDocid) {
				// get docScore
				docScore += curPtr.scoreList.getDocidScore(curPtr.nextDoc ++);
				ptrList.set(i, curPtr);
				if (curPtr.nextDoc >= curPtr.scoreList.scores.size()) {
					curSize --;
				}
			} else {
				// get default score
				docScore += ((QryopSl)this.args.get(i)).getDefaultScore(r, curDocid);
			}
		}
		
		// add min docid to result
		if (minDocid != Integer.MAX_VALUE) {
			result.docScores.add (minDocid, docScore);
		}
	}
    
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

    if (r instanceof RetrievalModelIndri) {
    	int qSize = this.args.size();
    	double score = 1;
    	for (int i = 0; i < qSize; i++) {
    		score *= ((QryopSl)this.args.get(i)).getDefaultScore(r, docid);
    	}
    	return Math.pow(score, 1/qSize);
    }

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

import java.io.*;
import java.util.*;



public class QryopSlSum extends QryopSl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *  @param q A query argument (a query operator).
   */
  public QryopSlSum(Qryop... q) {
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
	//  Initialization
	allocArgPtrs (r);
	QryResult result = new QryResult ();
	
	// store the current ptr to list of each argument
	List<ArgPtr> ptrList = new ArrayList<ArgPtr>(this.argPtrs.size());
	
	for (int j = 0; j < this.argPtrs.size(); j++) {
		ptrList.add(j, this.argPtrs.get(j));
	}
	
	while (ptrList.size() > 0) {
		int minDocid = Integer.MAX_VALUE;
		Map<Integer, ArgPtr> minPtrMap = new HashMap<Integer, ArgPtr>(this.argPtrs.size());
		
		// loop list of args to find min docid
		int j = 0;
		for (ArgPtr curPtr : ptrList) {
			int curDocid = curPtr.scoreList.getDocid(curPtr.nextDoc); 
			if (curDocid < minDocid) {
				// set new minDocid
				minPtrMap.clear();
				minDocid = curDocid;
				minPtrMap.put(j, curPtr);
			} else if (curDocid == minDocid) {
				// add cur docid to minPtrList
				minPtrMap.put(j, curPtr);
			}
			j++;
		}
		
		// found the min docid in this round
		// calculate docScore
		double docScore = 0;
		PriorityQueue<Integer> rmQueue = 
				new PriorityQueue<Integer>(minPtrMap.size(), Collections.reverseOrder());
		
		for (int index : minPtrMap.keySet()) {
			ArgPtr ptr = minPtrMap.get(index);
			docScore += ptr.scoreList.getDocidScore(ptr.nextDoc);
			// incre ptr in minPtrList
			// remove list if no records left, otherwise update ptrList
			ptr.nextDoc ++;
			if (ptr.nextDoc >= ptr.scoreList.scores.size()) {
				//ptrList.remove(index);
				rmQueue.add(index);
			} else {
				ptrList.set(index, ptr);
			}
		}
		
		// remove lists that have reached the end
		while (!rmQueue.isEmpty()) {
			int index = rmQueue.poll();
			ptrList.remove(index);
		}
		
		// add min docid to result
		if (minDocid != Integer.MAX_VALUE) {
			result.docScores.add (minDocid, docScore);
		}
	}
	freeArgPtrs();
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

    return ("#SUM( " + result + ")");
  }
}

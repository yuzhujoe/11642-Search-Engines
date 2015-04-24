import java.io.*;
import java.util.*;

public class QryopIlWindow extends QryopIl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new QryopIlSyn (arg1, arg2, arg3, ...).
   */
  private final int DIST;
	
  public QryopIlWindow(int distance, Qryop... q) {
	  DIST = distance;
	  for (int i = 0; i < q.length; i++) {
		  this.args.add(q[i]);
	  }
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
    syntaxCheckArgResults (this.argPtrs);

    QryResult result = new QryResult ();
    
    ArgPtr[] ptr = new ArgPtr[this.argPtrs.size()];
    ptr[0] = this.argPtrs.get(0);
    result.invertedList.field = new String (ptr[0].invList.field);

	ITERATE_DOC_IN_PTR0:
	for ( ; ptr[0].nextDoc < ptr[0].invList.postings.size(); ptr[0].nextDoc ++) {
		
		int ptr0Docid = ptr[0].invList.getDocid (ptr[0].nextDoc);
		// ptr[j] point to doc in jth argument invList with id = ptr0Docid
		
	
  	  	//  Do the other query arguments have the ptr0Docid?
  	  	for (int j=1; j < this.argPtrs.size(); j++) {
  	  		ptr[j] = this.argPtrs.get(j);
	  	  	while (true) {
				if (ptr[j].nextDoc >= ptr[j].invList.postings.size())
					break ITERATE_DOC_IN_PTR0;		// No more docs can match
				else if (ptr[j].invList.getDocid (ptr[j].nextDoc) > ptr0Docid)
					continue ITERATE_DOC_IN_PTR0;	// The ptr0docid can't match.
				else if (ptr[j].invList.getDocid (ptr[j].nextDoc) < ptr0Docid)
					ptr[j].nextDoc ++;			// Not yet at the right doc.
				else
					break;				// ptrj matches ptr0Docid
			}
  	  	}
  	  	
  	  	// ptr0Docid exists in all argPtrs
  	  	// now check the distance in positions
  	  	List<Integer> positions = new ArrayList<Integer>();
  	  	int[] ptrjCurIndex = new int[this.argPtrs.size()];
  	  	// init ptr position
  	  	
  	  	ITERATE_POS_IN_PTR0DOCID:
  	    while (true) {
  	    	int pos[] = new int[this.argPtrs.size()];
  	    	for (int i = 0; i < this.argPtrs.size(); i++) {
  	    		pos[i] = ptr[i].invList.postings.get(ptr[i].nextDoc).
  	  	    			positions.get(ptrjCurIndex[i]);
  	    	}
  	    	int min = getMinValue(pos);
  	    	if (min == -1) break;
  	    	int minPos = pos[min];
  	    	for (int j = 0; j < this.argPtrs.size(); j++) {
  	    		if (pos[j] - minPos >= DIST) {
  	    			ptrjCurIndex[min]++;
  	    			if (ptrjCurIndex[min] >= ptr[min].invList.postings.
  	    					get(ptr[min].nextDoc).positions.size()) {
  	    				break ITERATE_POS_IN_PTR0DOCID;
  	    			}
  	    			continue ITERATE_POS_IN_PTR0DOCID;
  	    		}
  	    	}
  	    	// all positions are in window
  	    	positions.add(minPos);
  	    	for (int j = 0; j < this.argPtrs.size(); j++) {
  	    		ptrjCurIndex[j]++;
  	    		if (ptrjCurIndex[j] >= ptr[j].invList.postings.
	    				get(ptr[j].nextDoc).positions.size()) {
	    			break ITERATE_POS_IN_PTR0DOCID;
  	    		}
  	    	}
  	    }
  	  	
  	  	// add the match doc with positions
  	  	if (!positions.isEmpty()) {
  	  		result.invertedList.appendPosting(ptr0Docid, positions);
  	  	}
	}
	
    freeArgPtrs();
    return result;
  }
  
  public int getMinValue(int[] posArray) {
	  int min = Integer.MAX_VALUE;
	  int minIndex = -1;
	  for (int i = 0; i < posArray.length; i++) {
		  if (posArray[i] < min) {
			  min = posArray[i];
			  minIndex = i;
		  }
	  }
	  return minIndex;
  }
  

  /**
   *  syntaxCheckArgResults does syntax checking that can only be done
   *  after query arguments are evaluated.
   *  @param ptrs A list of ArgPtrs for this query operator.
   *  @return True if the syntax is valid, false otherwise.
   */
  public Boolean syntaxCheckArgResults (List<ArgPtr> ptrs) {

    for (int i=0; i<this.args.size(); i++) {

      if (! (this.args.get(i) instanceof QryopIl)) {
    	  QryEval.fatalError ("Error:  Invalid argument in " +
			    this.toString());
      } else if ((i>0) &&
	    (! ptrs.get(i).invList.field.equals (ptrs.get(0).invList.field))){
    	  QryEval.fatalError ("Error: Arguments must be in the same field:  " +
			    this.toString());
      }
    }

    return true;
  }

  /*
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
    
    String result = new String ();

    for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
	      result += (i.next().toString() + " ");

    return ("#WINDOW/" + DIST + "( " + result + ")");
  }
}

import java.io.*;
import java.util.*;

public class QryopIlNear extends QryopIl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new QryopIlSyn (arg1, arg2, arg3, ...).
   */
  private final int DIST;
	
  public QryopIlNear(int distance, Qryop... q) {
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
    ArgPtr ptr0 = this.argPtrs.get(0);
    result.invertedList.field = new String (ptr0.invList.field);

	ITERATE_DOC_IN_PTR0:
	for ( ; ptr0.nextDoc < ptr0.invList.postings.size(); ptr0.nextDoc ++) {
		
		int ptr0Docid = ptr0.invList.getDocid (ptr0.nextDoc);
		// ptr[j] point to doc in jth argument invList with id = ptr0Docid
		ArgPtr[] ptr = new ArgPtr[this.argPtrs.size()-1];
	
  	  	//  Do the other query arguments have the ptr0Docid?
  	  	for (int j=0; (j + 1) < this.argPtrs.size(); j++) {
  	  		ptr[j] = this.argPtrs.get(j + 1);
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
  	  	// now check the distance for every arg
  	  	List<Integer> positions = new ArrayList<Integer>();
  	  	int[] ptrjCurIndex = new int[this.argPtrs.size()-1];
  	  	
  	  	ITERATE_POS_IN_PTR0DOCID:
  	  	for (int ptr0Pos : ptr0.invList.postings.get(ptr0.nextDoc).positions) {
  	  		int prevArgPos = ptr0Pos;
  	  		
  	  		ITERATE_POS_IN_PTRJ:
	  	  	for (int j=0; (j + 1) < this.argPtrs.size(); j++) {
	  	  		Vector<Integer> ptrjVector = ptr[j].invList.postings.get(ptr[j].nextDoc).positions;

		  	  	for (int i = ptrjCurIndex[j]; i < ptrjVector.size(); i++) {
		  	  		int ptrjPos = ptrjVector.get(i);
		  	  		ptrjCurIndex[j] = i;
		  	  		
		  	  		if (ptrjPos <= prevArgPos) {
		  	  			// not yet the right position
		  	  			// increment ptrjPos
		  	  			continue;
		  	  		} else if (ptrjPos - prevArgPos <= DIST) {
		  	  			// match
		  	  			// check next arg
		  	  			prevArgPos = ptrjPos;
		  	  			continue ITERATE_POS_IN_PTRJ;
		  	  		} else {
		  	  			// no longer can match for this ptr0Pos
		  	  			// increment ptr0Pos
		  	  			continue ITERATE_POS_IN_PTR0DOCID;
		  	  		}
		  	  	}
		  	  	break ITERATE_POS_IN_PTR0DOCID;
	  	  	}
	  	  	
	  	  	// add the match ptr0Pos
	  	  	positions.add(ptr0Pos);
	  	  	// increment ptrjPos
	  	  	for (int j=0; (j + 1) < this.argPtrs.size(); j++) {
	  	  		ptrjCurIndex[j]++;
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

    return ("#NERR/" + DIST + "( " + result + ")");
  }
}

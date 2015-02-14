/**
 *  QryEval illustrates the architecture for the portion of a search
 *  engine that evaluates queries.  It is a template for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class QryEval {

  static String usage = "Usage:  java " + System.getProperty("sun.java.command")
      + " paramFile\n\n";

  //  The index file reader is accessible via a global variable. This
  //  isn't great programming style, but the alternative is for every
  //  query operator to store or pass this value, which creates its
  //  own headaches.

  public static IndexReader READER;
  public static DocLengthStore docLenStore;

  //  Create and configure an English analyzer that will be used for
  //  query parsing.

  public static EnglishAnalyzerConfigurable analyzer =
      new EnglishAnalyzerConfigurable (Version.LUCENE_43);
  static {
	  analyzer.setLowercase(true);
	  analyzer.setStopwordRemoval(true);
	  analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
  }

  /**
   *  @param args The only argument is the path to the parameter file.
   *  @throws Exception
   */
  public static void main(String[] args) throws Exception {
    
    // must supply parameter file
    if (args.length < 1) {
    	System.err.println(usage);
    	System.exit(1);
    }

    // read in the parameter file; one parameter per line in format of key=value
    Map<String, String> params = new HashMap<String, String>();
    Scanner scan = new Scanner(new File(args[0]));
    String line = null;
    do {
    	line = scan.nextLine();
    	String[] pair = line.split("=");
    	params.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());
    scan.close();
    
    // parameters required for this example to run
    if (!params.containsKey("indexPath")) {
    	System.err.println("Error: indexPath were missing.");
    	System.exit(1);
    }
    if (!params.containsKey("retrievalAlgorithm")) {
    	System.err.println("Error: retrievalAlgorithm were missing.");
    	params.put("retrievalAlgorithm", "UnrankedBoolean");
    }
    if (!params.containsKey("queryFilePath")) {
    	System.err.println("Error: queryFilePath were missing");
    	System.exit(1);
    }

    // open the index
    READER = DirectoryReader.open(FSDirectory.open(new File(params.get("indexPath"))));

    if (READER == null) {
      System.err.println(usage);
      System.exit(1);
    }

    docLenStore = new DocLengthStore(READER);

    RetrievalModel model;
    String inputModel = params.get("retrievalAlgorithm");
    System.out.println(inputModel);
    if (inputModel.equals("UnrankedBoolean")) {
    	model = new RetrievalModelUnrankedBoolean();
    } else if (inputModel.equals("RankedBoolean")) {
    	model = new RetrievalModelRankedBoolean();
    } else if (inputModel.equals("BM25")) {
    	double k_1, b, k_3;
    	k_1 = Double.parseDouble(params.get("BM25:k_1"));
    	b = Double.parseDouble(params.get("BM25:b"));
    	k_3 = Double.parseDouble(params.get("BM25:k_3"));
    	
    	if (k_1 < 0 || b < 0 || b > 1 || k_3 < 0) {
    		System.err.println("Wrong Value for k_1, b, k_3");
    		System.exit(1);
    	}
    	
    	model = new RetrievalModelBM25(k_1, b, k_3);
    } else if (inputModel.equals("Indri")) {
    	double mu, lambda;
    	mu = Double.parseDouble(params.get("Indri:mu"));
    	lambda = Double.parseDouble(params.get("Indri:lambda"));
    	
    	if (mu < 0 || lambda < 0 || lambda > 1) {
    		System.err.println("Wrong Value for mu, lambda");
    		System.exit(1);
    	}
    	
    	model = new RetrievalModelIndri(mu, lambda);
    } else {
    	System.err.println("RetrievalModel does not exists: " + inputModel);
    	model = new RetrievalModelUnrankedBoolean();
    	System.exit(1);
    }

    String queryReadPath = params.get("queryFilePath");
    File queryFile = new File(queryReadPath);
    /*
     *  The code below is an unorganized set of examples that show
     *  you different ways of accessing the index.  Some of these
     *  are only useful in HW2 or HW3.
     */
    /*
    // Lookup the document length of the body field of doc 0.
    System.out.println(s.getDocLength("body", 0));

    // How to use the term vector.
    TermVector tv = new TermVector(1, "body");
    System.out.println("TermVector Demo");
    System.out.println(tv.stemString(10)); // get the string for the 10th stem
    System.out.println(tv.stemDf(10)); // get its df
    System.out.println(tv.totalStemFreq(10)); // get its ctf
	*/
    /**
     *  The index is open. Start evaluating queries. The examples
     *  below show query trees for two simple queries.  These are
     *  meant to illustrate how query nodes are created and connected.
     *  However your software will not create queries like this.  Your
     *  software will use a query parser.  See parseQuery.
     *
     *  The general pattern is to tokenize the  query term (so that it
     *  gets converted to lowercase, stopped, stemmed, etc), create a
     *  Term node to fetch the inverted list, create a Score node to
     *  convert an inverted list to a score list, evaluate the query,
     *  and print results.
     * 
     *  Modify the software so that you read a query from a file,
     *  parse it, and form the query tree automatically.
     */
    
    FileInputStream fin = new FileInputStream(queryFile);
    BufferedReader br = new BufferedReader(new InputStreamReader(fin));
    String query_line = null;
    Qryop qTree;
    String[] query;
    File output = new File(params.get("trecEvalOutputPath"));
    
    BufferedWriter writer = null;
    
    try{
    	writer = new BufferedWriter(new FileWriter(output));

	    while ((query_line = br.readLine()) != null) {
	    	query = new String(query_line).trim().split(":");
	    	//System.out.println(query);
	        qTree = parseQuery (query[1], model);
	        
	        if (model instanceof RetrievalModelBM25 ||
	        		model instanceof RetrievalModelIndri) {
	        	printResults (query, qTree.evaluate (model), output, writer);
	        } else {
	        	QryopSlScore scoreOp = new QryopSlScore(qTree);
	        	printResults (query, scoreOp.evaluate (model), output, writer);
	        }
	    }
	    
	    br.close();
	    writer.close();
    } catch (Exception e) {
    	e.printStackTrace();
    }
    /*
    //  A one-word query.
    printResults("pea",
        (new QryopSlScore(
    	     new QryopIlTerm(tokenizeQuery("pea")[0]))).evaluate(model));

    //  A more complex query.
    printResults("#AND (aparagus broccoli cauliflower #SYN(peapods peas))",
        (new QryopSlAnd(
            new QryopIlTerm(tokenizeQuery("asparagus")[0]),
            new QryopIlTerm(tokenizeQuery("broccoli")[0]),
            new QryopIlTerm(tokenizeQuery("cauliflower")[0]),
            new QryopIlSyn(
                new QryopIlTerm(tokenizeQuery("peapods")[0]), 
                new QryopIlTerm(tokenizeQuery("peas")[0])))).evaluate(model));

    //  A different way to create the previous query.  This doesn't use
    //  a stack, but it may make it easier to see how you would parse a
    //  query with a stack-based architecture.
    Qryop op1 = new QryopSlAnd();
    op1.add (new QryopIlTerm(tokenizeQuery("asparagus")[0]));
    op1.add (new QryopIlTerm(tokenizeQuery("broccoli")[0]));
    op1.add (new QryopIlTerm(tokenizeQuery("cauliflower")[0]));
    Qryop op2 = new QryopIlSyn();
    op2.add (new QryopIlTerm(tokenizeQuery("peapods")[0]));
    op2.add (new QryopIlTerm(tokenizeQuery("peas")[0]));
    op1.add (op2);
    printResults("#AND (aparagus broccoli cauliflower #SYN(peapods peas))",
		 op1.evaluate(model));


    //  Using the example query parser.  Notice that this does no
    //  lexical processing of query terms.  Add that to the query
    //  parser.
    Qryop qTree;
    String query = new String ("#AND(apple pie)");
    qTree = parseQuery (query);
    printResults (query, qTree.evaluate (model));
    */
    
    /*
     *  Create the trec_eval output.  Your code should write to the
     *  file specified in the parameter file, and it should write the
     *  results that you retrieved above.  This code just allows the
     *  testing infrastructure to work on QryEval.
     */
    /*
    BufferedWriter writer = null;

    try {
      writer = new BufferedWriter(new FileWriter(new File(params.get("trecEvalOutputPath"))));

      writer.write("10 Q0 clueweb09-en0000-00-03431 1 1.000000000000 fubar\n");
      writer.write("10 Q0 clueweb09-en0000-00-03433 2 1.000000000000 fubar\n");
      writer.write("10 Q0 clueweb09-en0000-00-03434 3 1.000000000000 fubar\n");
      writer.write("10 Q0 clueweb09-en0000-00-03435 4 1.000000000000 fubar\n");
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        writer.close();
      } catch (Exception e) {
      }
    }
	*/
    // Later HW assignments will use more RAM, so you want to be aware
    // of how much memory your program uses.

    printMemoryUsage(false);

  }

  /**
   *  Write an error message and exit.  This can be done in other
   *  ways, but I wanted something that takes just one statement so
   *  that it is easy to insert checks without cluttering the code.
   *  @param message The error message to write before exiting.
   *  @return void
   */
  static void fatalError (String message) {
    System.err.println (message);
    System.exit(1);
  }

  /**
   *  Get the external document id for a document specified by an
   *  internal document id. If the internal id doesn't exists, returns null.
   *  
   * @param iid The internal document id of the document.
   * @throws IOException 
   */
  static String getExternalDocid (int iid) throws IOException {
    Document d = QryEval.READER.document (iid);
    String eid = d.get ("externalId");
    return eid;
  }

  /**
   *  Finds the internal document id for a document specified by its
   *  external id, e.g. clueweb09-enwp00-88-09710.  If no such
   *  document exists, it throws an exception. 
   * 
   * @param externalId The external document id of a document.s
   * @return An internal doc id suitable for finding document vectors etc.
   * @throws Exception
   */
  static int getInternalDocid (String externalId) throws Exception {
    Query q = new TermQuery(new Term("externalId", externalId));
    
    IndexSearcher searcher = new IndexSearcher(QryEval.READER);
    TopScoreDocCollector collector = TopScoreDocCollector.create(1,false);
    searcher.search(q, collector);
    ScoreDoc[] hits = collector.topDocs().scoreDocs;
    
    if (hits.length < 1) {
      throw new Exception("External id not found.");
    } else {
      return hits[0].doc;
    }
  }

  /**
   * parseQuery converts a query string into a query tree.
   * 
   * @param qString
   *          A string containing a query.
   * @param qTree
   *          A query tree
   * @throws IOException
   */
  static Qryop parseQuery(String qString, RetrievalModel model) throws IOException {
    Qryop currentOp = null ;
    Stack<Qryop> stack = new Stack<Qryop>();

    // Add a default query operator to an unstructured query. This
    // is a tiny bit easier if unnecessary whitespace is removed.

    qString = qString.trim();

    // set default op
    if (model instanceof RetrievalModelUnrankedBoolean ||
    		model instanceof RetrievalModelRankedBoolean) {
    	qString = "#or(" + qString + ")";
    } else if (model instanceof RetrievalModelBM25) {
    	qString = "#sum(" + qString + ")";
    } else if (model instanceof RetrievalModelIndri) {
    	qString = "#and(" + qString + ")";
    }

    // Tokenize the query.

    StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
    String token = null;

    // Each pass of the loop processes one token. To improve
    // efficiency and clarity, the query operator on the top of the
    // stack is also stored in currentOp.

    while (tokens.hasMoreTokens()) {

      token = tokens.nextToken();

      if (token.matches("[ ,(\t\n\r]")) {
    	  // Ignore most delimiters.
      } else if (token.equalsIgnoreCase("#and")) {
    	  currentOp = new QryopSlAnd();
    	  stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#syn")) {
    	  currentOp = new QryopIlSyn();
    	  stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#or")) {
    	  currentOp = new QryopSlOr();
    	  stack.push(currentOp);
      } else if (token.toLowerCase().startsWith("#near")) {
    	  int distance = 1;
    	  try {
    		  distance = Integer.parseInt(token.trim().split("/")[1]);
    	  } catch (Exception e) {
    		  System.err.println(e.getStackTrace());
    	  }
    	  currentOp = new QryopIlNear(distance);
    	  stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#sum")) {
    	  System.out.println("sum op");
    	  currentOp = new QryopSlSum();
    	  stack.push(currentOp);
      } else if (token.startsWith(")")) { 
    	// Finish current query operator.
      
        // If the current query operator is not an argument to
        // another query operator (i.e., the stack is empty when it
        // is removed), we're done (assuming correct syntax - see
        // below). Otherwise, add the current operator as an
        // argument to the higher-level operator, and shift
        // processing back to the higher-level operator.

        stack.pop();

        if (stack.empty())
          break;

        Qryop arg = currentOp;
        currentOp = stack.peek();
        currentOp.add(arg);
      } else {

        // NOTE: You should do lexical processing of the token before
        // creating the query term, and you should check to see whether
        // the token specifies a particular field (e.g., apple.title).
    	String field = new String("body");
    	if (token.contains(".")) {
    		String[] tokenAndField = token.trim().split("\\.");
    		token = tokenAndField[0];
    		field = tokenAndField[1];
    	}
    	if (tokenizeQuery(token).length > 0) {
    		currentOp.add(new QryopIlTerm(tokenizeQuery(token)[0], field));
    	}
      }
    }
    
    // A broken structured query can leave unprocessed tokens on the
    // stack, so check for that.

    if (tokens.hasMoreTokens()) {
      System.err.println("Error:  Query syntax is incorrect.  " + qString);
      return null;
    }

    System.out.println("return value: " + currentOp);
    return currentOp;
  }

  /**
   *  Print a message indicating the amount of memory used.  The
   *  caller can indicate whether garbage collection should be
   *  performed, which slows the program but reduces memory usage.
   *  @param gc If true, run the garbage collector before reporting.
   *  @return void
   */
  public static void printMemoryUsage (boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc) {
      runtime.gc();
    }

    System.out.println ("Memory used:  " +
			((runtime.totalMemory() - runtime.freeMemory()) /
			 (1024L * 1024L)) + " MB");
  }
  
  /**
   * Print the query results. 
   * 
   * THIS IS NOT THE CORRECT OUTPUT FORMAT.  YOU MUST CHANGE THIS
   * METHOD SO THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK
   * PAGE, WHICH IS:
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName Original query.
   * @param result Result object generated by {@link Qryop#evaluate()}.
   * @throws IOException 
   */
  static void printResults(String[] query, QryResult result, 
		  File output, BufferedWriter writer) throws IOException {
	String queryID = query[0];
	String queryName = query[1];
    System.out.println(queryName + ":  ");
    if (result.docScores.scores.size() < 1) {
    	System.out.println("\tNo results.");
    } else {
    	List<String> list = new ArrayList<String>();
        ValueComparator compare =  new ValueComparator();
        
    	for (int i = 0; i < result.docScores.scores.size(); i++) {
    		list.add(String.format("%s:%s", getExternalDocid(result.docScores.getDocid(i)), 
    				result.docScores.getDocidScore(i)));
    	}
    	
    	Collections.sort(list, compare);
    	String temp;

        
    	for (int i = 0; i < Math.min(result.docScores.scores.size(), 100); i++) {
    		temp = String.format("%s\t%s\t%s\t%d\t%s\t%s\n", 
    				queryID, "Q0", list.get(i).trim().split(":")[0], 
    				(i + 1), list.get(i).trim().split(":")[1], "run-1");
    		System.out.print(temp);
    		writer.write(temp);
    	}
    }
  }

  /**
   *  Given a query string, returns the terms one at a time with stopwords
   *  removed and the terms stemmed using the Krovetz stemmer. 
   * 
   *  Use this method to process raw query terms. 
   * 
   *  @param query String containing query
   *  @return Array of query tokens
   *  @throws IOException
   */
  static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp = analyzer.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();
    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }
    return tokens.toArray(new String[tokens.size()]);
  }
}

class ValueComparator implements Comparator<String> {

    // Note: this comparator imposes orderings that are inconsistent with equals.    
    public int compare(String a, String b) {
    	String[] word_a = a.trim().split(":");
    	String[] word_b = b.trim().split(":");
    	double score_a = Double.parseDouble(word_a[1]);
    	double score_b = Double.parseDouble(word_b[1]);
    	if (score_a > score_b) {
    		return -1;
    	} else if (score_a == score_b && word_a[0].compareTo(word_b[0]) < 0) {
            return -1; 
        } else {
        	return 1;
        	// returning 0 would merge keys
        }
    }
}

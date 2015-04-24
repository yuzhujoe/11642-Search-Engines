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

import javax.sound.midi.SysexMessage;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class QryEval {

	static String usage = "Usage:  java "
			+ System.getProperty("sun.java.command") + " paramFile\n\n";

	// The index file reader is accessible via a global variable. This
	// isn't great programming style, but the alternative is for every
	// query operator to store or pass this value, which creates its
	// own headaches.

	public static IndexReader READER;
	public static DocLengthStore docLenStore;

	// Create and configure an English analyzer that will be used for
	// query parsing.

	public static EnglishAnalyzerConfigurable analyzer = new EnglishAnalyzerConfigurable(
			Version.LUCENE_43);
	static {
		analyzer.setLowercase(true);
		analyzer.setStopwordRemoval(true);
		analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
	}

	/**
	 * @param args
	 *            The only argument is the path to the parameter file.
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		// must supply parameter file
		if (args.length < 1) {
			System.err.println(usage);
			System.exit(1);
		}

		final long startTime = System.currentTimeMillis();

		// read in the parameter file; one parameter per line in format of
		// key=value
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
		READER = DirectoryReader.open(FSDirectory.open(new File(params
				.get("indexPath"))));

		if (READER == null) {
			System.err.println(usage);
			System.exit(1);
		}

		docLenStore = new DocLengthStore(READER);

		RetrievalModel model = null;
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
		} else if (inputModel.equals("letor")) {
			double k_1, b, k_3;
			k_1 = Double.parseDouble(params.get("BM25:k_1"));
			b = Double.parseDouble(params.get("BM25:b"));
			k_3 = Double.parseDouble(params.get("BM25:k_3"));

			if (k_1 < 0 || b < 0 || b > 1 || k_3 < 0) {
				System.err.println("Wrong Value for k_1, b, k_3");
				System.exit(1);
			}

			double mu, lambda;
			mu = Double.parseDouble(params.get("Indri:mu"));
			lambda = Double.parseDouble(params.get("Indri:lambda"));

			if (mu < 0 || lambda < 0 || lambda > 1) {
				System.err.println("Wrong Value for mu, lambda");
				System.exit(1);
			}

			model = new RetrievalModelLetor(Double.parseDouble(params
					.get("letor:svmRankParamC")), k_1, b, k_3, mu, lambda);

			model.setParameter("trainingQueryFile",
					params.get("letor:trainingQueryFile"));
			model.setParameter("trainingQrelsFile",
					params.get("letor:trainingQrelsFile"));
			model.setParameter("trainingFeatureVectorFile",
					params.get("letor:trainingFeatureVectorsFile"));
			model.setParameter("pageRankFile", params.get("letor:pageRankFile"));
			model.setParameter("featureDisable",
					params.get("letor:featureDisable"));
			model.setParameter("svmRankLearnPath",
					params.get("letor:svmRankLearnPath"));
			model.setParameter("svmRankClassifyPath",
					params.get("letor:svmRankClassifyPath"));
			model.setParameter("svmRankModelFile",
					params.get("letor:svmRankModelFile"));
			model.setParameter("testingFeatureVectorsFile",
					params.get("letor:testingFeatureVectorsFile"));
			model.setParameter("testingDocumentScores",
					params.get("letor:testingDocumentScores"));
		} else {
			System.err.println("RetrievalModel does not exists: " + inputModel);
			System.exit(1);
		}

		FeatureVector fv = null;
		String queryReadPath = params.get("queryFilePath");

		if (model instanceof RetrievalModelLetor) {
			// read in training query and document
			fv = new FeatureVector();
			fv.readToQueryMap(((RetrievalModelLetor) model).trainingQueryFile);
			fv.readToReleMapFromFile(((RetrievalModelLetor) model).trainingQrelsFile);
			// construct training feature vector
			fv.constructFeatureVectors((RetrievalModelLetor) model,
					((RetrievalModelLetor) model).trainingFeatureVectorFile);
			// run svm_train to get training model
			svmTrain((RetrievalModelLetor) model);
			// read in testing query
			fv = new FeatureVector();
			fv.readToQueryMap(queryReadPath);
		}

		// read query from queryFilePath
		File queryFile = new File(queryReadPath);
		boolean needExpansion = model instanceof RetrievalModelIndri
				&& params.containsKey("fb")
				&& params.get("fb").equalsIgnoreCase("true");
		Map<Integer, List<String>> initialRanking = null;
		BufferedWriter expandQueryWriter = null;
		QueryExpansion qExp = null;

		if (needExpansion) {
			// get params need fro expanding query
			int fbDocs, fbTerms, fbMu;
			fbDocs = Integer.parseInt(params.get("fbDocs"));
			fbTerms = Integer.parseInt(params.get("fbTerms"));
			fbMu = Integer.parseInt(params.get("fbMu"));

			qExp = new QueryExpansion(fbDocs, fbTerms, fbMu);

			// read document ranking in trec_eval format from
			// fbInitialRankingFile
			if (params.containsKey("fbInitialRankingFile")) {
				// initialRanking format same as sortedResult
				// external_id:score:docid
				initialRanking = qExp.retrieveInitialRanking(params
						.get("fbInitialRankingFile"));
			}

			// initiate expand query writer
			if (params.containsKey("fbExpansionQueryFile")) {
				File expandQueryOutput = new File(
						params.get("fbExpansionQueryFile"));
				expandQueryWriter = new BufferedWriter(new FileWriter(
						expandQueryOutput));
			}
		}

		// use the read in query to retrieve documents
		FileInputStream fin = new FileInputStream(queryFile);
		BufferedReader br = new BufferedReader(new InputStreamReader(fin));
		String query_line = null;
		Qryop qTree;
		String[] query;

		File output = new File(params.get("trecEvalOutputPath"));
		BufferedWriter resultWriter = new BufferedWriter(new FileWriter(output));

		// read in queries line by line
		while ((query_line = br.readLine()) != null) {
			query = new String(query_line).trim().split(":");

			qTree = parseQuery(query[1], model);
			QryResult result = null;
			List<String> sResult = null; // sorted result
											// external_id:score:docid

			if (initialRanking != null) {
				// read a document ranking from the fbInitialRankingFile
				sResult = initialRanking.get(Integer.parseInt(query[0]));
			} else {
				// use evaluate from HW3 to retrieve documents
				if (model instanceof RetrievalModelBM25
						|| model instanceof RetrievalModelIndri
						|| model instanceof RetrievalModelLetor) {
					result = qTree.evaluate(model);
				} else {
					QryopSlScore scoreOp = new QryopSlScore(qTree);
					result = scoreOp.evaluate(model);
				}
				sResult = sortedResult(result);
			}

			// if expand query is enabled, re-evaluate the qeury
			if (needExpansion) {
				// construct expanded query
				String expandQuery = qExp.constructExpandQuery(sResult);

				expandQueryWriter.write(query[0] + ": " + expandQuery + "\n");
				System.out.println(query[0] + ": " + expandQuery);

				Qryop expTree = parseQuery(expandQuery, model);

				// create combined query
				double origWeight = Double.parseDouble(params
						.get("fbOrigWeight"));
				QryopSlWeight combineOp = new QryopSlWeight(true);
				combineOp.add(origWeight);
				combineOp.add(qTree);
				combineOp.add(1 - origWeight);
				combineOp.add(expTree);
				result = combineOp.evaluate(model);
				sResult = sortedResult(result);
			}

			if (model instanceof RetrievalModelLetor) {
				// construct relevance judgement map
				fv.readToReleMapFromResult(Integer.parseInt(query[0]), sResult);
			} else {
				printResults(Integer.parseInt(query[0]), sResult, result.docScores.scores.size(),
						output, resultWriter);
			}
		} // end query iteration
		br.close();

		if (model instanceof RetrievalModelLetor) {
			// construct testing feature vector
			String testingFeatureVectorsFile = ((RetrievalModelLetor) model)
					.testingFeatureVectorsFile;
			String testingDocumentScores = ((RetrievalModelLetor) model).
					testingDocumentScores;
			System.out.println("Start constructing testing feature vector");
			fv.constructFeatureVectors((RetrievalModelLetor) model,
					testingFeatureVectorsFile);
			System.out.println("Finish constructing testing feature vector");
			svmTest((RetrievalModelLetor) model);

			// read in final scores
			// Map<qid, List<external_id:score>>
			Map<Integer, List<String>> rankMap = new HashMap<Integer, List<String>>();
			BufferedReader fvbr = new BufferedReader(new InputStreamReader(
					new FileInputStream(testingFeatureVectorsFile)));
			BufferedReader dsbr = new BufferedReader(new InputStreamReader(
					new FileInputStream(testingDocumentScores)));
			String fline, dline;
			while((fline = fvbr.readLine()) != null) {
				dline = dsbr.readLine();
				String[] words = fline.trim().split("\t");
				int qid = Integer.parseInt(words[1].trim().split(":")[1]);
				String extid = words[words.length-1];
				String docScore = dline.trim();
				if (rankMap.containsKey(qid)) {
					rankMap.get(qid).add(String.format("%s:%s", extid, docScore));
				} else {
					List<String> temp = new ArrayList<String>();
					temp.add(String.format("%s:%s", extid, docScore));
					rankMap.put(qid, temp);
				}
			}
			fvbr.close();
			dsbr.close();
			
			// re-rank the result
			// sort query ids
			List<Integer> sortedQids = new ArrayList<Integer>(rankMap.size());
			sortedQids.addAll(rankMap.keySet());
			Collections.sort(sortedQids);
			for (int qid : sortedQids) {
				List<String> resultList = rankMap.get(qid);
				ValueComparator compare = new ValueComparator();
				Collections.sort(resultList, compare);
				// write result and print out
				printResults(qid, resultList, resultList.size(),
						output, resultWriter);
			}
		}

		resultWriter.close();
		if (expandQueryWriter != null) {
			expandQueryWriter.close();
		}

		final long endTime = System.currentTimeMillis();
		System.out.println("Total Time: " + (endTime - startTime) / 1000.0);
		printMemoryUsage(false);
	}

	/**
	 * Write an error message and exit. This can be done in other ways, but I
	 * wanted something that takes just one statement so that it is easy to
	 * insert checks without cluttering the code.
	 * 
	 * @param message
	 *            The error message to write before exiting.
	 * @return void
	 */
	static void fatalError(String message) {
		System.err.println(message);
		System.exit(1);
	}

	/**
	 * Get the external document id for a document specified by an internal
	 * document id. If the internal id doesn't exists, returns null.
	 * 
	 * @param iid
	 *            The internal document id of the document.
	 * @throws IOException
	 */
	static String getExternalDocid(int iid) throws IOException {
		Document d = QryEval.READER.document(iid);
		String eid = d.get("externalId");
		return eid;
	}

	/**
	 * Finds the internal document id for a document specified by its external
	 * id, e.g. clueweb09-enwp00-88-09710. If no such document exists, it throws
	 * an exception.
	 * 
	 * @param externalId
	 *            The external document id of a document.s
	 * @return An internal doc id suitable for finding document vectors etc.
	 * @throws Exception
	 */
	static int getInternalDocid(String externalId) throws Exception {
		Query q = new TermQuery(new Term("externalId", externalId));

		IndexSearcher searcher = new IndexSearcher(QryEval.READER);
		TopScoreDocCollector collector = TopScoreDocCollector.create(1, false);
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
	 *            A string containing a query.
	 * @param qTree
	 *            A query tree
	 * @throws IOException
	 */
	static Qryop parseQuery(String qString, RetrievalModel model)
			throws IOException {
		Qryop currentOp = null;
		Stack<Qryop> stack = new Stack<Qryop>();

		// Add a default query operator to an unstructured query. This
		// is a tiny bit easier if unnecessary whitespace is removed.

		qString = qString.trim();

		// set default op
		if (model instanceof RetrievalModelUnrankedBoolean
				|| model instanceof RetrievalModelRankedBoolean) {
			qString = "#or(" + qString + ")";
		} else if (model instanceof RetrievalModelBM25
				|| model instanceof RetrievalModelLetor) {
			qString = "#sum(" + qString + ")";
		} else if (model instanceof RetrievalModelIndri) {
			qString = "#and(" + qString + ")";
		}

		// Tokenize the query.

		StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()",
				true);
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
				System.out.println("near op");
				int distance = 1;
				try {
					distance = Integer.parseInt(token.trim().split("/")[1]);
				} catch (Exception e) {
					System.err.println(e.getStackTrace());
				}
				currentOp = new QryopIlNear(distance);
				stack.push(currentOp);
			} else if (token.toLowerCase().startsWith("#window")) {
				int distance = 1;
				try {
					distance = Integer.parseInt(token.trim().split("/")[1]);
				} catch (Exception e) {
					System.err.println(e.getStackTrace());
				}
				currentOp = new QryopIlWindow(distance);
				stack.push(currentOp);
			} else if (token.equalsIgnoreCase("#wand")) {
				currentOp = new QryopSlWeight(true);
				stack.push(currentOp);
			} else if (token.equalsIgnoreCase("#wsum")) {
				currentOp = new QryopSlWeight(false);
				stack.push(currentOp);
			} else if (token.equalsIgnoreCase("#sum")) {
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
				if (currentOp instanceof QryopSlWeight
						&& model instanceof RetrievalModelIndri
						&& ((QryopSlWeight) currentOp).getIsWeight()) {
					((QryopSlWeight) currentOp).add(Double.parseDouble(token));
					continue;
				}

				String field = new String("body");
				if (token.contains(".")) {
					String[] tokenAndField = token.trim().split("\\.");
					token = tokenAndField[0];
					field = tokenAndField[1];
				}
				if (tokenizeQuery(token).length > 0) {
					currentOp.add(new QryopIlTerm(tokenizeQuery(token)[0],
							field));
				} else if (currentOp instanceof QryopSlWeight
						&& model instanceof RetrievalModelIndri) {
					((QryopSlWeight) currentOp).setIsWeight(true);
					((QryopSlWeight) currentOp).weight
							.remove(((QryopSlWeight) currentOp).weight.size() - 1);
				}
			}
		}

		// A broken structured query can leave unprocessed tokens on the
		// stack, so check for that.

		if (tokens.hasMoreTokens()) {
			System.err
					.println("Error:  Query syntax is incorrect.  " + qString);
			return null;
		}

		System.out.println("return value: " + currentOp);
		return currentOp;
	}

	/**
	 * Print a message indicating the amount of memory used. The caller can
	 * indicate whether garbage collection should be performed, which slows the
	 * program but reduces memory usage.
	 * 
	 * @param gc
	 *            If true, run the garbage collector before reporting.
	 * @return void
	 */
	public static void printMemoryUsage(boolean gc) {

		Runtime runtime = Runtime.getRuntime();

		if (gc) {
			runtime.gc();
		}

		System.out
				.println("Memory used:  "
						+ ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L))
						+ " MB");
	}

	/**
	 * Print the query results.
	 * 
	 * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
	 * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
	 * 
	 * QueryID Q0 DocID Rank Score RunID
	 * 
	 * @param queryName
	 *            Original query.
	 * @param result
	 *            Result object generated by {@link Qryop#evaluate()}.
	 * @throws IOException
	 */
	static void printResults(int qid, List<String> list, int count,
			File output, BufferedWriter writer) throws IOException {
		if (list == null) {
			System.out.println("\tNo results.");
		} else {
			String temp;

			for (int i = 0; i < Math.min(count, 100); i++) {
				temp = String.format("%d\t%s\t%s\t%d\t%s\t%s\n", qid, "Q0",
						list.get(i).trim().split(":")[0], (i + 1), list.get(i)
								.trim().split(":")[1], "run-1");
				System.out.print(temp);
				writer.write(temp);
			}
		}
	}

	static List<String> sortedResult(QryResult result) throws IOException {
		List<String> list = new ArrayList<String>();
		ValueComparator compare = new ValueComparator();

		if (result.docScores.scores.size() < 1) {
			return null;
		} else {
			for (int i = 0; i < result.docScores.scores.size(); i++) {
				list.add(String.format("%s:%s:%d",
						getExternalDocid(result.docScores.getDocid(i)),
						result.docScores.getDocidScore(i),
						result.docScores.getDocid(i)));
			}

			Collections.sort(list, compare);
			return list;
		}
	}

	/**
	 * Given a query string, returns the terms one at a time with stopwords
	 * removed and the terms stemmed using the Krovetz stemmer.
	 * 
	 * Use this method to process raw query terms.
	 * 
	 * @param query
	 *            String containing query
	 * @return Array of query tokens
	 * @throws IOException
	 */
	static String[] tokenizeQuery(String query) throws IOException {

		TokenStreamComponents comp = analyzer.createComponents("dummy",
				new StringReader(query));
		TokenStream tokenStream = comp.getTokenStream();

		CharTermAttribute charTermAttribute = tokenStream
				.addAttribute(CharTermAttribute.class);
		tokenStream.reset();

		List<String> tokens = new ArrayList<String>();
		while (tokenStream.incrementToken()) {
			String term = charTermAttribute.toString();
			tokens.add(term);
		}
		return tokens.toArray(new String[tokens.size()]);
	}

	static void svmTrain(RetrievalModelLetor model) throws Exception {
		Process cmdProc = Runtime
				.getRuntime()
				.exec(new String[] { model.svmRankLearnPath, "-c",
						String.valueOf(model.getParameter("svmC")),
						model.trainingFeatureVectorFile, model.svmRankModelFile });
		consumeOutput(cmdProc);
	}

	static void svmTest(RetrievalModelLetor model) throws Exception {
		Process cmdProc = Runtime.getRuntime().exec(
				new String[] { model.svmRankClassifyPath,
						model.testingFeatureVectorsFile,
						model.svmRankModelFile, model.testingDocumentScores });
		consumeOutput(cmdProc);
	}

	static void consumeOutput(Process cmdProc) throws Exception {
		// The stdout/stderr consuming code MUST be included.
		// It prevents the OS from running out of output buffer space and
		// stalling.

		// consume stdout and print it out for debugging purposes
		BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(
				cmdProc.getInputStream()));
		String line;
		while ((line = stdoutReader.readLine()) != null) {
			System.out.println(line);
		}
		// consume stderr and print it for debugging purposes
		BufferedReader stderrReader = new BufferedReader(new InputStreamReader(
				cmdProc.getErrorStream()));
		while ((line = stderrReader.readLine()) != null) {
			System.out.println(line);
		}

		// get the return value from the executable. 0 means success, non-zero
		// indicates a problem
		int retValue = cmdProc.waitFor();
		if (retValue != 0) {
			throw new Exception("SVM Rank crashed.");
		}
	}
}

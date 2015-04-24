import java.io.*;
import java.util.*;

import org.apache.lucene.document.Document;

public class FeatureVector {
	// Map<qid, qstems>
	private Map<Integer, List<String>> qMap;
	// Map<qid, Map<external_id, relevance_score>>
	private Map<Integer, Map<String, Integer>> rjMap;
	private final int featureNum;

	public FeatureVector() {
		featureNum = 18;
		rjMap = new HashMap<Integer, Map<String, Integer>>();
		qMap = new HashMap<Integer, List<String>>();
	}

	public void constructFeatureVectors(RetrievalModelLetor model, String featureVectorFile)
			throws Exception {
		// read pagerank scores
		// Map<external_id, pagerank_score>
		Map<String, Double> prMap = new HashMap<String, Double>();
		FileInputStream prin = new FileInputStream(model.pageRankFile);
		BufferedReader prr = new BufferedReader(new InputStreamReader(prin));
		String prline;
		while ((prline = prr.readLine()) != null) {
			String[] words = prline.trim().split("\t");
			if (!prMap.containsKey(words[0])) {
				prMap.put(words[0], Double.parseDouble(words[1]));
			}
		}
		prr.close();
		prin.close();
		
		// iterate query
		BufferedWriter resultWriter = new BufferedWriter(new FileWriter(
				featureVectorFile));

		// sort query id in qMap
		List<Integer> sortedQids = new ArrayList<Integer>(qMap.size());
		sortedQids.addAll(qMap.keySet());
		Collections.sort(sortedQids);
		
		// create feature disable array
		Set<Integer> featureDisable = new HashSet<Integer>();
		if (model.featureDisable != null) {
			for (String fstr : model.featureDisable.trim().split(",")) {
				featureDisable.add(Integer.parseInt(fstr));
			}
		}
		
		for (int qid : sortedQids) {
			// Map<extid, Map<fv_index, fv_value>>
			Map<String, Map<Integer, Double>> fvMap = new HashMap<String, Map<Integer, Double>>();
			// Map<fv_index, [min, max]>
			Map<Integer, List<Double>> edgeMap = new HashMap<Integer, List<Double>>();
			Iterator<Map.Entry<String, Integer>> itr = rjMap.get(qid)
					.entrySet().iterator();
			// iterate docs for current query
			while (itr.hasNext()) {
				Map.Entry<String, Integer> entry = itr.next();
				Map<Integer, Double> featureVector = new HashMap<Integer, Double>();
				String extid = entry.getKey();
				int intid = QryEval.getInternalDocid(extid);

				Document d = QryEval.READER.document(intid);
				// f1 spam score for d
				if (!featureDisable.contains(1)) {
					double spamScore = Double.parseDouble(d.get("score"));
					featureVector.put(1, spamScore);
					updateEdgeValue(edgeMap, spamScore, 1);
				}

				// f2 url depth for d
				String rawUrl = d.get("rawUrl").trim();
				if (!featureDisable.contains(2)) {
					//double urlDepth = (double) (rawUrl.split("/").length - 1);
					double urlDepth = (double) (rawUrl.length() - rawUrl.replace("/", "").length());
					featureVector.put(2, urlDepth);
					updateEdgeValue(edgeMap, urlDepth, 2);
				}

				// f3 FromWikipedia score for d
				if (!featureDisable.contains(3)) {
					double wikiScore = (rawUrl.contains("wikipedia.org")) ? 1 : 0;
					featureVector.put(3, wikiScore);
					updateEdgeValue(edgeMap, wikiScore, 3);
				}

				// f4 pagerank score for d
				if (!featureDisable.contains(4)) {
					if (prMap.containsKey(extid)) {
						double prScore = prMap.get(extid);
						featureVector.put(4, prScore);
						updateEdgeValue(edgeMap, prScore, 4);
					}
				}

				// BM25, Indri, Termoverlap scores
				String[] fields = { "body", "title", "url", "inlink" };

				for (int i = 0; i < fields.length; i++) {
					LetorScoreFromTermVector lsftv = new LetorScoreFromTermVector(
							model, fields[i], qMap.get(qid), intid);
					// the document does not contain this field
					if (lsftv.getTv() == null)
						continue;
					
					if (!featureDisable.contains(5 + i * 3)) {
						double bmScore = lsftv.getBM25Score();
						featureVector.put(5 + i * 3, bmScore);
						updateEdgeValue(edgeMap, bmScore, 5 + i * 3);
					}

					if (!featureDisable.contains(6 + i * 3)) {
						double indriScore = lsftv.getIndriScore();
						featureVector.put(6 + i * 3, indriScore);
						updateEdgeValue(edgeMap, indriScore, 6 + i * 3);
					}

					if (!featureDisable.contains(7 + i * 3)) {
						double termolScore = lsftv.getTermoverlapScore();
						featureVector.put(7 + i * 3, termolScore);
						updateEdgeValue(edgeMap, termolScore, 7 + i * 3);
					}
				}
				
				// f17 tf * idf in body
				if (!featureDisable.contains(17)) {
					LetorScoreFromTermVector lsftv = new LetorScoreFromTermVector(
							model, "body", qMap.get(qid), intid);
					if (lsftv.getTv() != null) {
						double tfIdfScore = lsftv.getTfIdfScore();
						featureVector.put(17, tfIdfScore);
						updateEdgeValue(edgeMap, tfIdfScore, 17);
					}
				}
				
				// f18 unrankedboolean score in body
				if (!featureDisable.contains(18)) {
					LetorScoreFromTermVector lsftv = new LetorScoreFromTermVector(
							model, "body", qMap.get(qid), intid);
					if (lsftv.getTv() != null) {
						double booleanScore = lsftv.getBooleanScore();
						featureVector.put(18, booleanScore);
						updateEdgeValue(edgeMap, booleanScore, 18);
					}
				}

				fvMap.put(extid, featureVector);
			}

			// normalize the featureVector and output
			Iterator<Map.Entry<String, Map<Integer, Double>>> normalItr = fvMap
					.entrySet().iterator();
			// iterate each doc
			while (normalItr.hasNext()) {
				Map.Entry<String, Map<Integer, Double>> entry = normalItr
						.next();
				String extid = entry.getKey();
				Map<Integer, Double> featureVector = entry.getValue();
				StringBuilder out = new StringBuilder(String.format(
						"%d\tqid:%d", rjMap.get(qid).get(extid), qid));
				// iterate each feature
				for (int index = 1; index <= featureNum; index++) {
					if (!featureVector.containsKey(index)) continue;
					
					double min = edgeMap.get(index).get(0);
					double max = edgeMap.get(index).get(1);
					double normalVal = (max == min) ? 0 : (featureVector
							.get(index) - min) / (max - min);
					//double normalVal = featureVector.get(index);
					// featureVector.put(index, normalVal);
					out.append(String.format("\t%d:%.14f", index, normalVal));
				}

				out.append(String.format("\t#\t%s\n", extid));
				System.out.print(out.toString());
				resultWriter.write(out.toString());
			}
		}
		resultWriter.close();
	}

	public void updateEdgeValue(Map<Integer, List<Double>> map, double score,
			int index) {
		if (map.containsKey(index)) {
			// min and max value already exist
			if (score > map.get(index).get(1)) {
				// update max value
				map.get(index).set(1, score);
			}
			if (score < map.get(index).get(0)) {
				// update min value
				map.get(index).set(0, score);
			}
		} else {
			List<Double> temp = new ArrayList<Double>(2);
			temp.add(score);
			temp.add(score);
			map.put(index, temp);
		}
	}

	public void readToQueryMap(String trainingQueryFile) throws Exception {
		// read query
		FileInputStream qin = new FileInputStream(trainingQueryFile);
		BufferedReader qr = new BufferedReader(new InputStreamReader(qin));
		String qline;
		while ((qline = qr.readLine()) != null) {
			String[] words = qline.trim().split(":");
			int qid = Integer.parseInt(words[0]);
			if (!qMap.containsKey(qid)) {
				List<String> tokens = new ArrayList<String>();
				for (String token : words[1].trim().split("[ \t]")) {
					if (QryEval.tokenizeQuery(token).length > 0) {
						tokens.add(QryEval.tokenizeQuery(token)[0]);
					}
				}
				if (tokens.size() > 0) {
					qMap.put(qid, tokens);
				}
			}
		}
		qr.close();
		qin.close();
	}

	public void readToReleMapFromFile(String trainingQrelsFile) throws Exception {
		// read relevance judgements
		FileInputStream din = new FileInputStream(trainingQrelsFile);
		BufferedReader dr = new BufferedReader(new InputStreamReader(din));
		String dline;
		while ((dline = dr.readLine()) != null) {
			String[] words = dline.trim().split(" ");
			int qid = Integer.parseInt(words[0]);
			if (rjMap.containsKey(qid)) {
				rjMap.get(qid).put(words[2], Integer.parseInt(words[3]));
			} else {
				// Map<external_id, relevance_score>
				Map<String, Integer> temp = new HashMap<String, Integer>();
				temp.put(words[2], Integer.parseInt(words[3]));
				rjMap.put(qid, temp);
			}
		}
		dr.close();
		din.close();
	}
	
	public void readToReleMapFromResult(int qid, List<String> sResult) throws Exception {
		if (sResult == null || sResult.size() < 1) {
			throw new Exception("sResult equals null!");
		}
		// sResult format => external_id:score:docid
		for (int i = 0; i < Math.min(100, sResult.size()); i++) {
			String line = sResult.get(i);
			String[] words = line.trim().split(":");
			if (rjMap.containsKey(qid)) {
				rjMap.get(qid).put(words[0], 0);
			} else {
				// Map<external_id, relevance_score>
				Map<String, Integer> temp = new HashMap<String, Integer>();
				temp.put(words[0], 0);
				rjMap.put(qid, temp);
			}
		}
	}
}

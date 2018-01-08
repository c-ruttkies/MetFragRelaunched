package de.ipbhalle.metfrag.substructure2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.ipbhalle.metfraglib.database.LocalCSVDatabase;
import de.ipbhalle.metfraglib.database.LocalPSVDatabase;
import de.ipbhalle.metfraglib.exceptions.MultipleHeadersFoundInInputDatabaseException;
import de.ipbhalle.metfraglib.interfaces.ICandidate;
import de.ipbhalle.metfraglib.interfaces.IDatabase;
import de.ipbhalle.metfraglib.interfaces.IPeakListReader;
import de.ipbhalle.metfraglib.list.CandidateList;
import de.ipbhalle.metfraglib.match.MassFingerprintMatch;
import de.ipbhalle.metfraglib.parameter.Constants;
import de.ipbhalle.metfraglib.parameter.SettingsChecker;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.settings.MetFragGlobalSettings;
import de.ipbhalle.metfraglib.settings.Settings;
import de.ipbhalle.metfraglib.writer.CandidateListWriterZippedPSV;

public class PostCalculateScoreValuesFromResultFileLossThreadFP {

	public static double ALPHA_VALUE_LOSS = 0.0001;
	public static double BETA_VALUE_LOSS = 0.0001;

	public static int numberFinished = 0;
	public static java.util.Hashtable<String, String> argsHash;

	public static boolean getArgs(String[] args) {
		argsHash = new java.util.Hashtable<String, String>();
		for (String arg : args) {
			arg = arg.trim();
			String[] tmp = arg.split("=");
			if (!tmp[0].equals("parampath") && !tmp[0].equals("resultpath") && !tmp[0].equals("threads")
					&& !tmp[0].equals("output") 
					&& !tmp[0].equals("alpha") && !tmp[0].equals("beta")
					&& !tmp[0].equals("outputtype") && !tmp[0].equals("stdout") && !tmp[0].equals("negscore")
					&& !tmp[0].equals("scorenames") && !tmp[0].equals("transform") && !tmp[0].equals("outputfolder")) {
				System.err.println("property " + tmp[0] + " not known.");
				return false;
			}
			if (argsHash.containsKey(tmp[0])) {
				System.err.println("property " + tmp[0] + " already defined.");
				return false;
			}
			argsHash.put(tmp[0], tmp[1]);
		}

		if (!argsHash.containsKey("parampath")) {
			System.err.println("no csv defined");
			return false;
		}
		if (!argsHash.containsKey("resultpath")) {
			System.err.println("no csv defined");
			return false;
		}
		if (!argsHash.containsKey("threads")) {
			System.err.println("no csv defined");
			return false;
		}
		if (!argsHash.containsKey("output")) {
			System.err.println("no csv defined");
			return false;
		}
		if (!argsHash.containsKey("alpha")) {
			System.err.println("no csv defined");
			return false;
		}
		if (!argsHash.containsKey("beta")) {
			System.err.println("no csv defined");
			return false;
		}
		if (!argsHash.containsKey("type")) {
			argsHash.put("type", "csv");
		}
		if (!argsHash.containsKey("stdout")) {
			argsHash.put("stdout", "false");
		}
		if (!argsHash.containsKey("outputtype")) {
			argsHash.put("outputtype", "rank"); // or "list"
		}
		if (!argsHash.containsKey("outputfolder")) {
			argsHash.put("outputfolder", Constants.OS_TEMP_DIR); // or "list"
		}
		if (!argsHash.containsKey("negscore")) {
			argsHash.put("negscore", "false");
		}
		if (!argsHash.containsKey("scorenames")) {
			System.err.println("no scorenames defined");
			return false;
		}
		if (!argsHash.containsKey("transform")) {
			argsHash.put("transform", "false");
		}
		return true;
	}

	public static void main(String[] args) throws Exception {
		getArgs(args);

		String paramfolder = (String) argsHash.get("parampath");
		String resfolder = (String) argsHash.get("resultpath");
		String outputfile = (String) argsHash.get("output");
		String alpha = (String) argsHash.get("alpha");
		String beta = (String) argsHash.get("beta");
		int numberThreads = Integer.parseInt(argsHash.get("threads"));
		Boolean stdout = Boolean.parseBoolean(argsHash.get("stdout"));
		String outputtype = (String) argsHash.get("outputtype");
		Boolean negScores = Boolean.parseBoolean(argsHash.get("negscore"));
		String[] scoringPropertyNames = argsHash.get("scorenames").split(",");
		Boolean transformScores = Boolean.parseBoolean(argsHash.get("transform"));
		String outputfolder = (String) argsHash.get("outputfolder");
		
		ALPHA_VALUE_LOSS = Double.parseDouble(alpha);
		BETA_VALUE_LOSS = Double.parseDouble(beta);

		File _resfolder = new File(resfolder);
		File _paramfolder = new File(paramfolder);

		File[] resultFiles = _resfolder.listFiles();
		File[] paramFiles = _paramfolder.listFiles();

		File folder = new File(outputfolder + Constants.OS_SPECIFIC_FILE_SEPARATOR + "scores_loss");
		if(!folder.exists()) folder.mkdirs();
		String fileprefix = alpha + "_" + beta;
		fileprefix = fileprefix.replaceAll("\\.", "");
		
		String path = folder.getAbsolutePath() + Constants.OS_SPECIFIC_FILE_SEPARATOR + fileprefix + Constants.OS_SPECIFIC_FILE_SEPARATOR;
		File filepath = new File(path);
		if(!filepath.exists()) filepath.mkdirs();
		
		ArrayList<ProcessThread> threads = new ArrayList<ProcessThread>();

		for (int i = 0; i < paramFiles.length; i++) {
			String id = paramFiles[i].getName().split("\\.")[0];
			int resultFileID = -1;
			for (int j = 0; j < resultFiles.length; j++) {
				if (resultFiles[j].getName().startsWith(id + ".")) {
					resultFileID = j;
					break;
				}
			}
			if (resultFileID == -1) {
				System.err.println(id + " not found as result.");
				continue;
			}

			Settings settings = getSettings(paramFiles[i].getAbsolutePath());
			settings.set(VariableNames.LOCAL_DATABASE_PATH_NAME, resultFiles[resultFileID].getAbsolutePath());

			settings.set(VariableNames.LOSS_FINGERPRINT_ANNOTATION_ALPHA_VALUE_NAME, ALPHA_VALUE_LOSS);
			settings.set(VariableNames.LOSS_FINGERPRINT_ANNOTATION_BETA_VALUE_NAME, BETA_VALUE_LOSS);

			SettingsChecker sc = new SettingsChecker();
			if (!sc.check(settings)) {
				System.err.println("Error checking settings for " + id);
				continue;
			}
			IPeakListReader peakListReader = (IPeakListReader) Class
					.forName((String) settings.get(VariableNames.METFRAG_PEAK_LIST_READER_NAME))
					.getConstructor(Settings.class).newInstance(settings);

			settings.set(VariableNames.PEAK_LIST_NAME, peakListReader.read());

			ProcessThread thread = new PostCalculateScoreValuesFromResultFileLossThreadFP().new ProcessThread(
					settings, outputfile, resfolder, paramFiles[i].getAbsolutePath(), outputtype, negScores, 
					transformScores, scoringPropertyNames, stdout, path);
			threads.add(thread);
		}
		System.err.println("preparation finished");

		ExecutorService executer = Executors.newFixedThreadPool(numberThreads);
		for (ProcessThread thread : threads) {
			executer.execute(thread);
		}
		executer.shutdown();
		while (!executer.isTerminated()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}

	public static double[][] readWeights(String weightsfile) throws IOException {
		BufferedReader breader = new BufferedReader(new FileReader(new File(weightsfile)));
		ArrayList<double[]> weights = new ArrayList<double[]>();
		String line = "";
		while ((line = breader.readLine()) != null) {
			line = line.trim();
			String[] tmp = line.split("\\s+");
			double[] doubleweights = new double[tmp.length];
			for (int i = 0; i < tmp.length; i++)
				doubleweights[i] = Double.parseDouble(tmp[i]);
			weights.add(doubleweights);
		}
		breader.close();
		double[][] weightmatrix = new double[weights.size()][weights.get(0).length];
		for (int i = 0; i < weights.size(); i++) {
			for (int k = 0; k < weights.get(0).length; k++) {
				weightmatrix[i][k] = weights.get(i)[k];
			}
		}
		return weightmatrix;
	}

	public static synchronized void increaseNumberFinished(String name) {
		numberFinished++;
	}

	public static Settings getSettings(String parameterfile) {
		File parameterFile = new File(parameterfile);
		MetFragGlobalSettings settings = null;
		try {
			settings = MetFragGlobalSettings.readSettings(parameterFile, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return settings;
	}

	public static MassFingerprintMatch getMatchByMass(ArrayList<?> matches, Double peakMass) {
		return getMatchByMass(matches, peakMass, false);
	}

	public static MassFingerprintMatch getMatchByMass(ArrayList<?> matches, Double peakMass, boolean debug) {
		for (int i = 0; i < matches.size(); i++) {
			MassFingerprintMatch match = (MassFingerprintMatch) matches.get(i);
			if (debug)
				System.out.println(match.getMass());
			if (match.getMass().equals(peakMass))
				return match;
		}
		return null;
	}

	class ProcessThread extends Thread {
		protected Settings settings;
		protected String outputfile;
		protected String resultsfolder;
		protected String paramFile;
		protected String outputtype;
		protected boolean negScores;
		protected double[][] weights;
		protected String[] scorenames;
		protected boolean tranformscores;
		protected boolean stdout;
		protected int[] ranks_for_weight;
		protected boolean ispositivequery;
		protected CandidateList candidates;
		protected String outputfolder;

		/**
		 * 
		 * @param settings
		 * @param outputFolder
		 */
		public ProcessThread(Settings settings, String outputfile, String resultsfolder, String paramFile,
				String outputtype, boolean negScores, boolean tranformscores, String[] scorenames,
				boolean stdout, String outputfolder) {
			this.settings = settings;
			this.outputfile = outputfile;
			this.resultsfolder = resultsfolder;
			this.paramFile = paramFile;
			this.outputtype = outputtype;
			this.negScores = negScores;
			this.scorenames = scorenames;
			this.tranformscores = tranformscores;
			this.stdout = stdout;
			if (this.paramFile.contains("-01."))
				this.ispositivequery = true;
			else
				this.ispositivequery = false;
			this.outputfolder = outputfolder;
		}

		public String getParamFileName() {
			return this.paramFile;
		}

		public CandidateList getCandidateList() {
			return this.candidates;
		}
		
		/**
		 * 
		 */
		public void run() {
			IDatabase db = null;
			String dbFilename = (String) settings.get(VariableNames.LOCAL_DATABASE_PATH_NAME);
			if (dbFilename.endsWith("psv"))
				db = new LocalPSVDatabase(this.settings);
			else
				db = new LocalCSVDatabase(settings);
			ArrayList<String> ids = null;
			try {
				ids = db.getCandidateIdentifiers();
			} catch (MultipleHeadersFoundInInputDatabaseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			CandidateList candidates = null;
			try {
				candidates = db.getCandidateByIdentifier(ids);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			if (candidates.getNumberElements() == 0) {
				System.out.println(
						"No candidates found in " + (String) this.settings.get(VariableNames.LOCAL_DATABASE_PATH_NAME));
				return;
			}

			System.err.println(
					dbFilename.replaceAll(".*/", "") + ": Read " + candidates.getNumberElements() + " candidates");
			try {
				this.postProcessScoreParametersLoss(this.settings, candidates);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			
			for (int i = 0; i < candidates.getNumberElements(); i++) {
				this.settings.set(VariableNames.CANDIDATE_NAME, candidates.getElement(i));
				this.singlePostCalculateLoss(this.settings, candidates.getElement(i));
				candidates.getElement(i).removeProperty("LossMatchList");
				this.removeProperties(candidates.getElement(i));
			}

			this.candidates = candidates;
			
			CandidateListWriterZippedPSV psvWriter = new CandidateListWriterZippedPSV();
			String paramid = this.getParamFileName().replaceAll(".*\\/", "").replaceAll("\\.txt", "");
			String filename = Constants.OS_SPECIFIC_FILE_SEPARATOR + paramid + "_loss.psv";
			filename = this.outputfolder + filename;
			try {
				psvWriter.write(candidates, filename);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			increaseNumberFinished(this.paramFile);
		}

		public boolean isPositiveQuery() {
			return this.ispositivequery;
		}

		public int[] getRanksForWeight() {
			return this.ranks_for_weight;
		}

		public void removeProperties(ICandidate candidate) {
			candidate.removeProperty("PeakMatchList");
			candidate.removeProperty("NoExplPeaks");
			candidate.removeProperty("NumberPeaksUsed");
			candidate.removeProperty("LossFingerprintOfExplPeaks");
			candidate.removeProperty("FragmentFingerprintOfExplPeaks");
			candidate.removeProperty("AutomatedLossFingerprintAnnotationScore_Probtypes");
			candidate.removeProperty("PeakMatchList");
			
		}
		
		protected String getInChIKey() throws IOException {
			String inchikey = "";
			BufferedReader breader = new BufferedReader(new FileReader(this.paramFile));
			String line = "";
			while ((line = breader.readLine()) != null) {
				if (line.matches("# [A-Z]*")) {
					inchikey = line.split("\\s+")[1];
					break;
				} else if (line.matches("# InChIKey [A-Z]*-*[A-Z]*-*[A-Z]*")) {
					inchikey = line.split("\\s+")[2].split("-")[0];
					break;
				} else if (line.matches("# [A-Z]*-*[A-Z]*-*[A-Z]*")) {
					inchikey = line.split("\\s+")[1].split("-")[0];
					break;
				}

			}
			breader.close();
			return inchikey;
		}

		public void postProcessScoreParametersLoss(Settings settings, CandidateList candidates) throws IOException {
			String samplename = (String) settings.get(VariableNames.SAMPLE_NAME);
			String filename = this.resultsfolder + Constants.OS_SPECIFIC_FILE_SEPARATOR + samplename + "_data_loss.txt";
			BufferedReader breader = new BufferedReader(new FileReader(new File(filename)));
			String line = "";
			Double sumFingerprintFrequencies = null;
			Double f_seen = null;
			Double f_unseen = null;
			java.util.HashMap<Double, java.util.LinkedList<Double>> massToObservations = new java.util.HashMap<Double, java.util.LinkedList<Double>>();
			java.util.HashMap<Double, Double> massToBackgroundSize = new java.util.HashMap<Double, Double>();
			boolean valuesStarted = false;
			while ((line = breader.readLine()) != null) {
				line = line.trim();
				if (valuesStarted) {
					String[] tmp = line.split("\\s+");
					Double mass = Double.parseDouble(tmp[0]);
					java.util.LinkedList<Double> observations = new java.util.LinkedList<Double>();
					for (int i = 1; i < (tmp.length - 1); i++) {
						observations.add(Double.parseDouble(tmp[i]));
					}
					massToObservations.put(mass, observations);
					massToBackgroundSize.put(mass, Double.parseDouble(tmp[tmp.length - 1].split(":")[1]));
				} else if (line.startsWith("sumFingerprintFrequencies"))
					sumFingerprintFrequencies = Double.parseDouble(line.split("\\s+")[1]);
				else if (line.startsWith("f_seen"))
					f_seen = Double.parseDouble(line.split("\\s+")[1]);
				else if (line.startsWith("f_unseen"))
					f_unseen = Double.parseDouble(line.split("\\s+")[1]);
				else if (line.startsWith("LossToFingerprints"))
					valuesStarted = true;
			}
			breader.close();

			double alpha = (double) settings.get(VariableNames.LOSS_FINGERPRINT_ANNOTATION_ALPHA_VALUE_NAME); // alpha
			double beta = (double) settings.get(VariableNames.LOSS_FINGERPRINT_ANNOTATION_BETA_VALUE_NAME); // beta

			// set value for denominator of P(f,m)
			double denominatorValue = sumFingerprintFrequencies + alpha * f_seen + alpha * f_unseen + beta;

			settings.set(VariableNames.LOSS_FINGERPRINT_DENOMINATOR_VALUE_NAME, denominatorValue);

			double alphaProbability = alpha / denominatorValue; // P(f,m) F_u
			double betaProbability = beta / denominatorValue; // p(f,m) not
																// annotated
			java.util.Iterator<Double> it = massToObservations.keySet().iterator();

			java.util.HashMap<Double, Double> massToSumF = new java.util.HashMap<Double, Double>();
			java.util.HashMap<Double, Double> massToAlphaProb = new java.util.HashMap<Double, Double>();
			java.util.HashMap<Double, Double> massToBetaProb = new java.util.HashMap<Double, Double>();

			while (it.hasNext()) {
				Double mass = it.next();
				java.util.LinkedList<Double> observations = massToObservations.get(mass);
				Double bgsize = massToBackgroundSize.get(mass);
				// sum_f P(f,m)
				// calculate sum of MF_s (including the alpha count) and the
				// joint probabilities
				// at this stage getProbability() returns the absolute counts
				// from the annotation files
				double sum_f = 0.0;
				double sumFsProbabilities = 0.0;
				for (int ii = 0; ii < observations.size(); ii++) {
					// sum_f P(f,m) -> for F_s
					sumFsProbabilities += (observations.get(ii) + alpha) / denominatorValue;
				}

				// calculate the sum of probabilities for un-observed
				// fingerprints for the current mass
				double sumFuProbabilities = alphaProbability * bgsize;

				sum_f += sumFsProbabilities;
				sum_f += sumFuProbabilities;
				sum_f += betaProbability;

				massToSumF.put(mass, sum_f);
				massToAlphaProb.put(mass, alphaProbability / sum_f);
				massToBetaProb.put(mass, betaProbability / sum_f);
			}
			settings.set("LossMassToSumF", massToSumF);
			settings.set("LossMassToAlphaProb", massToAlphaProb);
			settings.set("LossMassToBetaProb", massToBetaProb);
			settings.set("LossDenominatorValue", denominatorValue);
			return;
		}

		public String getProbTypeString(ArrayList<Double> matchProb, ArrayList<Integer> matchType,
				ArrayList<Double> matchMasses) {
			String string = "NA";
			if (matchProb.size() >= 1) {
				string = matchType.get(0) + ":" + matchProb.get(0) + ":" + matchMasses.get(0);
			}
			for (int i = 1; i < matchProb.size(); i++) {
				string += ";" + matchType.get(i) + ":" + matchProb.get(i) + ":" + matchMasses.get(i);
			}
			return string;
		}

		protected void readMassToProbType(String line, java.util.Vector<Double> masses,
				java.util.Vector<String> probtypes) {
			if (line.equals("NA"))
				return;
			String[] tmp1 = line.split(";");
			for (int i = 0; i < tmp1.length; i++) {
				String[] tmp2 = tmp1[i].split(":");
				String probType = tmp2[1];
				if (tmp2.length == 3)
					probType += ":" + tmp2[2];
				masses.add(Double.parseDouble(tmp2[0]));
				probtypes.add(probType);
			}
			return;
		}
		
		/**
		 * 
		 * @param settings
		 * @param candidate
		 */
		public void singlePostCalculateLoss(Settings settings, ICandidate candidate) {
			double value = 0.0;
			int matches = 0;
			java.util.Vector<Double> lossMasses = new java.util.Vector<Double>();
			java.util.Vector<String> lossProbTypes = new java.util.Vector<String>();
			this.readMassToProbType((String) candidate.getProperty("AutomatedLossFingerprintAnnotationScore_Probtypes"),
					lossMasses, lossProbTypes);
			ArrayList<Double> matchMasses = new ArrayList<Double>();
			ArrayList<Double> matchProb = new ArrayList<Double>();
			ArrayList<Integer> matchType = new ArrayList<Integer>(); // found -
																		// 1;
																		// alpha
																		// - 2;
																		// beta
																		// - 3
			// get foreground fingerprint observations (m_f_observed)

			double alpha = (double) settings.get(VariableNames.LOSS_FINGERPRINT_ANNOTATION_ALPHA_VALUE_NAME); // alpha
			java.util.HashMap<?, ?> massToSumF = (java.util.HashMap<?, ?>) settings.get("LossMassToSumF");
			java.util.HashMap<?, ?> massToAlphaProb = (java.util.HashMap<?, ?>) settings.get("LossMassToAlphaProb");
			java.util.HashMap<?, ?> massToBetaProb = (java.util.HashMap<?, ?>) settings.get("LossMassToBetaProb");
			Double denominatorValue = (Double) settings.get("LossDenominatorValue");
			Object[] matchingMasses = massToSumF.keySet().toArray();
			java.util.Arrays.sort(matchingMasses);
			for (int k = 0; k < lossMasses.size(); k++) {
				Double curmass = lossMasses.get(k);
				String probType = lossProbTypes.get(k);
				Double matchingMass = this.findMatchingLossMass(matchingMasses, curmass);
				String[] tmp = probType.split(":");
				// (fingerprintToMasses.getSize(currentFingerprint));
				if (tmp.length == 1) {
					double prob = 0.0;
					if (tmp[0].equals("2"))
						prob = (Double) massToAlphaProb.get(matchingMass);
					else if (tmp[0].equals("3"))
						prob = (Double) massToBetaProb.get(matchingMass);
					matchProb.add(prob);
					matchType.add(Integer.parseInt(tmp[0]));
					matchMasses.add(curmass);
					value += Math.log(prob);

				} else {
					matches++;
					// (p(m,f) + alpha) / sum_F(p(m,f)) + |F| * alpha
					double matching_prob = ((Double.parseDouble(tmp[0]) + alpha) / denominatorValue)
							/ (Double) massToSumF.get(matchingMass);
					// |F|
					if (matching_prob != 0.0) {
						value += Math.log(matching_prob);
						matchProb.add(matching_prob);
						matchType.add(1);
						matchMasses.add(curmass);
					}
				}
			}

			candidate.setProperty("AutomatedLossFingerprintAnnotationScore_Matches", matches);
			candidate.setProperty("AutomatedLossFingerprintAnnotationScore", value);
			candidate.setProperty("AutomatedLossFingerprintAnnotationScore_Probtypes",
					getProbTypeString(matchProb, matchType, matchMasses));
		}

		protected Double findMatchingLossMass(Object[] masses, Double mass) {
			Double lastMassFound = null;
			Double lastDevFound = null;
			for (int i = 0; i < masses.length; i++) {
				Double currentMass = (Double) masses[i];
				Double currentDev = Math.abs(mass - currentMass);
				if (lastDevFound == null || lastDevFound > currentDev) {
					lastMassFound = currentMass;
					lastDevFound = currentDev;
				} else
					break;
			}
			return lastMassFound;
		}
	}
}

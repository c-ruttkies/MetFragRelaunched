package de.ipbhalle.metfraglib.score;

import de.ipbhalle.metfraglib.FastBitArray;
import de.ipbhalle.metfraglib.interfaces.ICandidate;
import de.ipbhalle.metfraglib.interfaces.IMatch;
import de.ipbhalle.metfraglib.match.MassFingerprintMatch;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.settings.Settings;
import de.ipbhalle.metfraglib.substructure.MassToFingerprintGroupList;
import de.ipbhalle.metfraglib.substructure.MassToFingerprintGroupListCollection;

public class AutomatedLossFingerprintAnnotationScore extends AbstractScore {

	protected ICandidate candidate;
	
	public AutomatedLossFingerprintAnnotationScore(Settings settings) {
		super(settings);
		this.optimalValues = new double[1];
		this.optimalValues[0] = 0.0;
		this.candidate = (ICandidate)settings.get(VariableNames.CANDIDATE_NAME);
		this.hasInterimResults = false;
	}
	
	public void calculate() {
		this.value = 0.0;
		this.calculationFinished = true;
	}

	public void setOptimalValues(double[] values) {
		this.optimalValues[0] = values[0];
	}
	
	/**
	 * collects the background fingerprints
	 */
	public Double[] calculateSingleMatch(IMatch match) {
		return new Double[] {0.0, null};
	}
	
	@Override
	public void singlePostCalculate() {
		this.value = 0.0;
		MassToFingerprintGroupListCollection lossToFingerprintGroupListCollection = (MassToFingerprintGroupListCollection)settings.get(VariableNames.LOSS_TO_FINGERPRINT_GROUP_LIST_COLLECTION_NAME);
	
		int matches = 0;
		java.util.ArrayList<?> matchlist = (java.util.ArrayList<?>)candidate.getProperty("LossMatchList");
		java.util.ArrayList<Double> matchMasses = new java.util.ArrayList<Double>();
		java.util.ArrayList<Double> matchProb = new java.util.ArrayList<Double>();
		java.util.ArrayList<Integer> matchType = new java.util.ArrayList<Integer>(); // found - 1; alpha - 2; beta - 3
		// get foreground fingerprint observations (m_f_observed)
		for(int i = 0; i < lossToFingerprintGroupListCollection.getNumberElements(); i++) {
			// get f_m_observed
			MassToFingerprintGroupList lossToFingerprintGroupList = lossToFingerprintGroupListCollection.getElement(i);
			Double currentMass = lossToFingerprintGroupList.getPeakmz();
			MassFingerprintMatch currentMatch = this.getMatchByMass(matchlist, currentMass);
			if(currentMatch == null) {
				matchProb.add(lossToFingerprintGroupList.getBetaProb());
				matchType.add(3);
				matchMasses.add(currentMass);
				this.value += Math.log(lossToFingerprintGroupList.getBetaProb());
			} else {
				FastBitArray currentFingerprint = new FastBitArray(currentMatch.getFingerprint());
				// ToDo: at this stage try to check all fragments not only the best one
				matches++;
				// (p(m,f) + alpha) / sum_F(p(m,f)) + |F| * alpha
				double matching_prob = lossToFingerprintGroupList.getMatchingProbability(currentFingerprint);
				// |F|
				if(matching_prob != 0.0) {
					this.value += Math.log(matching_prob);
					matchProb.add(matching_prob);
					matchType.add(1);
					matchMasses.add(currentMass);
				}
				else {
					this.value += Math.log(lossToFingerprintGroupList.getAlphaProb());
					matchProb.add(lossToFingerprintGroupList.getAlphaProb());
					matchType.add(2);
					matchMasses.add(currentMass);
				}
			}
		}
		
		if(lossToFingerprintGroupListCollection.getNumberElements() == 0) this.value = 0.0;
		
		candidate.setProperty("AutomatedLossFingerprintAnnotationScore_Matches", matches);
		candidate.setProperty("AutomatedLossFingerprintAnnotationScore", this.value);
		candidate.setProperty("AutomatedLossFingerprintAnnotationScore_Probtypes", this.getProbTypeString(matchProb, matchType, matchMasses));
 	}

	public String getProbTypeString(java.util.ArrayList<Double> matchProb, java.util.ArrayList<Integer> matchType, java.util.ArrayList<Double> matchMasses) {
		if(matchProb.size() == 0) return "NA";
		StringBuilder string = new StringBuilder();
		if(matchProb.size() >= 1) {
			string.append(matchType.get(0));
			string.append(":");
			string.append(matchProb.get(0));
			string.append(":");
			string.append(matchMasses.get(0));
		}
		for(int i = 1; i < matchProb.size(); i++) {
			string.append(";");
			string.append(matchType.get(i));
			string.append(":");
			string.append(matchProb.get(i));
			string.append(":");
			string.append(matchMasses.get(i));
		}
		return string.toString();
	}
	
	public MassFingerprintMatch getMatchByMass(java.util.ArrayList<?> matches, Double peakMass) {
		for(int i = 0; i < matches.size(); i++) {
			MassFingerprintMatch match = (MassFingerprintMatch)matches.get(i);
			if(match.getMass().equals(peakMass)) 
				return match;
		}
		return null;
	}
	
	@Override
	public void nullify() {
		super.nullify();
	}

	public boolean isBetterValue(double value) {
		return value > this.value ? true : false;
	}
}

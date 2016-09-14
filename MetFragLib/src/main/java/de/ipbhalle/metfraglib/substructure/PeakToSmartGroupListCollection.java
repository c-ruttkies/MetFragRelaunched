package de.ipbhalle.metfraglib.substructure;

import org.openscience.cdk.interfaces.IAtomContainer;

import de.ipbhalle.metfraglib.additionals.MathTools;
import de.ipbhalle.metfraglib.additionals.MoleculeFunctions;
import de.ipbhalle.metfraglib.list.DefaultList;
import de.ipbhalle.metfraglib.similarity.TanimotoSimilarity;

public class PeakToSmartGroupListCollection extends DefaultList {
	
	// P ( p )
	private double[] peakProbabilities;
	private Integer maximumAnnotatedID = null;
	
	public PeakToSmartGroupListCollection() {
		super();
	}

	public void addElement(PeakToSmartGroupList obj) {
		this.list.add(obj);
	}

	public void addElementSorted(PeakToSmartGroupList obj) {
		int index = 0;
		while(index < this.list.size()) {
			double peakMz = ((PeakToSmartGroupList)this.list.get(index)).getPeakmz();
			if(peakMz < obj.getPeakmz()) index++;
			else break;
		}
		this.list.add(index, obj);
	}

	public void addElement(int index, PeakToSmartGroupList obj) {
		this.list.add(index, obj);
	}
	
	public PeakToSmartGroupList getElement(int index) {
		return (PeakToSmartGroupList)this.list.get(index);
	}
	
	public PeakToSmartGroupList getElementByPeak(Double mzValue, Double mzppm, Double mzabs) {
		double dev = MathTools.calculateAbsoluteDeviation(mzValue, mzppm) + mzabs;
		double minDev = Integer.MAX_VALUE;
		PeakToSmartGroupList bestMatch = null;
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList peakToSmartGroupList = (PeakToSmartGroupList)this.list.get(i);
			double currentDev = Math.abs(peakToSmartGroupList.getPeakmz() - mzValue);
			if(currentDev <= dev) {
				if(currentDev < minDev) {
					minDev = currentDev;
					bestMatch = peakToSmartGroupList;
				}
			}
		}
		return bestMatch;
	}
	
	public void print() {
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList peakToSmartGroupList = this.getElement(i);
			System.out.print(peakToSmartGroupList.getPeakmz());
			for(int j = 0; j < peakToSmartGroupList.getNumberElements(); j++) {
				System.out.print(" ");
				peakToSmartGroupList.getElement(j).print();
			}
		}
	}
	
	public String toString() {
		String string = "";
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList peakToSmartGroupList = this.getElement(i);
			string += peakToSmartGroupList.getPeakmz() + " " + peakToSmartGroupList.toString();
		}
		return string;
	}

	public String toStringSmiles() {
		String string = "";
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList peakToSmartGroupList = this.getElement(i);
			string += peakToSmartGroupList.getPeakmz() + " " + peakToSmartGroupList.toStringSmiles(); 
		}
		return string;
	}
	
	/**
	 * calculate P ( p ) for every peak p
	 * 
	 */
	public void calculatePeakProbabilities() {
		this.peakProbabilities = new double[this.list.size()];
		int totalNumber = 0;
		for(int i = 0; i < this.list.size(); i++) {
			totalNumber += this.getElement(i).getAbsolutePeakFrequency();
			this.peakProbabilities[i] = (double)this.getElement(i).getAbsolutePeakFrequency();
		}
		for(int i = 0; i < this.peakProbabilities.length; i++) {
			this.peakProbabilities[i] /= (double)totalNumber;
		}
	}
	
	public void setProbabilityToJointProbability() {
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList peakToSmartGroupList = this.getElement(i);
			peakToSmartGroupList.setProbabilityToJointProbability();
		}
	}

	public void setProbabilityToConditionalProbability_sp() {
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList peakToSmartGroupList = this.getElement(i);
			peakToSmartGroupList.setProbabilityToConditionalProbability_sp();
		}
	}
	
	public void setProbabilityToConditionalProbability_ps() {
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList peakToSmartGroupList = this.getElement(i);
			peakToSmartGroupList.setProbabilityToConditionalProbability_ps();
		}
	}
	
	/*
	 * calculates P( p | s ) = ( P( s | p ) * P ( p ) ) / ( P ( s ) )
	 * 
	 */
	public void calculatePosteriorProbabilitesVariant1() {
		// P ( s )
		double sumJointProbabilities = 0.0;
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList currentPeakToSmartGroupList = this.getElement(i);
			for(int j = 0; j < currentPeakToSmartGroupList.getNumberElements(); j++) {
				SmartsGroup currentSmartsGroup = currentPeakToSmartGroupList.getElement(j);
				// P ( s, p ) 
				double currentLikelihood = currentSmartsGroup.getProbability();
				// P ( s | p ) * P ( p )
				double currentJointProbability = currentLikelihood * this.peakProbabilities[i];
				currentSmartsGroup.setProbability(currentJointProbability);
				sumJointProbabilities += currentJointProbability;
			}
		}
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList currentPeakToSmartGroupList = this.getElement(i);
			for(int j = 0; j < currentPeakToSmartGroupList.getNumberElements(); j++) {
				SmartsGroup currentSmartsGroup = currentPeakToSmartGroupList.getElement(j);
				// now P ( s , p ) 
				double currentJointProbability = currentSmartsGroup.getProbability();
				// P ( s , p ) / sum_p P ( s , p ) = P ( s , p ) / P ( s )
				double currentPosteriorProbability = currentJointProbability / sumJointProbabilities;
				currentSmartsGroup.setProbability(currentPosteriorProbability);
			}
		}
	}
	
	public void removeDuplicates() {
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList peakToSmartGroupList = (PeakToSmartGroupList)this.list.get(i);
			peakToSmartGroupList.removeDuplicates();
		}	
	}

	public void updateConditionalProbabilities() {
		for(int i = 0; i < this.getNumberElements(); i++) {
			this.getElement(i).updateConditionalProbabilities();
		}
	}

	public void updateConditionalProbabilities(int[] substructureAbsoluteProbabilities) {
		for(int i = 0; i < this.getNumberElements(); i++) {
			this.getElement(i).updateConditionalProbabilities(substructureAbsoluteProbabilities);
		}
	}
	
	public void updateJointProbabilities() {
		int numberN = 0;
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList peakToSmartGroupList = (PeakToSmartGroupList)this.list.get(i);
			for(int j = 0; j < peakToSmartGroupList.getNumberElements(); j++) {
				numberN += peakToSmartGroupList.getElement(j).getNumberElements();
			}
		}
		for(int i = 0; i < this.list.size(); i++) {
			this.getElement(i).updateJointProbabilities(numberN);
		}
	}
	
	public void annotateIds() {
		java.util.Vector<SmartsGroup> smartsGroups = new java.util.Vector<SmartsGroup>();
		int maxAnnotatedId = -1;
		for(int i = 0; i < this.list.size(); i++) {
			PeakToSmartGroupList peakToSmartGroupList = (PeakToSmartGroupList)this.getElement(i);
			for(int j = 0; j < peakToSmartGroupList.getNumberElements(); j++) {
				SmartsGroup smartsGroup = (SmartsGroup)peakToSmartGroupList.getElement(j);
				smartsGroups.add(smartsGroup);
			}
		}
		
		int number = smartsGroups.size();
		int nextPercent = 1;
		System.out.println(number + " substructures");
		
		IAtomContainer[] cons = new  IAtomContainer[number];
		cons[0] = MoleculeFunctions.parseSmiles(smartsGroups.get(0).getSmiles().get(0));
		for(int i = 1; i < cons.length; i++) 
			cons[i] = MoleculeFunctions.parseSmiles(smartsGroups.get(i).getSmiles().get(0));
		
		TanimotoSimilarity sims = new TanimotoSimilarity(cons);
		System.out.println("calculated similarities");
		
		for(int i = 0; i < smartsGroups.size(); i++) {
			SmartsGroup smartsGroupI = smartsGroups.get(i);
			for(int j = 0; j < smartsGroups.size(); j++) {
				SmartsGroup smartsGroupJ = smartsGroups.get(j);
				if(smartsGroupJ.getId() == null) {
					smartsGroupI.setId(++maxAnnotatedId);
					break;
				}
				double sim = TanimotoSimilarity.calculateSimilarity(sims.getFingerPrint(i), sims.getFingerPrint(j));
				if(sim == 1.0) {
					smartsGroupI.setId(smartsGroupJ.getId());
					break;
				}
			}
			double relation = ((double)i / (double)number) * 100.0;
			if(nextPercent < relation) {
				System.out.print(nextPercent + "% ");
				nextPercent = (int)Math.ceil(relation);
			}
		}
		System.out.println();
		this.maximumAnnotatedID = new Integer(maxAnnotatedId); 
	}
	
	public int[] calculateSubstructureAbsoluteProbabilities() {
		System.out.println(this.maximumAnnotatedID + " different substructures");
		int[] absoluteProbabilities = new int[this.maximumAnnotatedID + 1];
		for(int i = 0; i < this.getNumberElements(); i++) {
			PeakToSmartGroupList peakToSmartGroupList = this.getElement(i);
			for(int j = 0; j < peakToSmartGroupList.getNumberElements(); j++) {
				SmartsGroup smartGroup = peakToSmartGroupList.getElement(j);
				absoluteProbabilities[smartGroup.getId()] += smartGroup.getNumberElements();
			}
		}
		return absoluteProbabilities;
	}

	public void updateProbabilities(int[] substructureAbsoluteProbabilities) {
		for(int i = 0; i < this.list.size(); i++) {
			this.getElement(i).updateConditionalProbabilities(substructureAbsoluteProbabilities);
		}
	}

	public Integer getMaximumAnnotatedID() {
		return maximumAnnotatedID;
	}

	public void setMaximumAnnotatedID(Integer maximumAnnotatedID) {
		this.maximumAnnotatedID = maximumAnnotatedID;
	}
	
}

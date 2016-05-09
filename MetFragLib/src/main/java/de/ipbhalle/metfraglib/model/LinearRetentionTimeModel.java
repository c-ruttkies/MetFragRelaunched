package de.ipbhalle.metfraglib.model;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.qsar.DescriptorValue;
import org.openscience.cdk.qsar.result.IDescriptorResult;

import de.ipbhalle.metfraglib.additionals.MoleculeFunctions;
import de.ipbhalle.metfraglib.database.LocalPropertyFileDatabase;
import de.ipbhalle.metfraglib.interfaces.ICandidate;
import de.ipbhalle.metfraglib.list.CandidateList;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.settings.Settings;

public class LinearRetentionTimeModel extends AbstractModel {

	protected double slope;
	protected double coefficient;
	protected org.openscience.cdk.qsar.descriptors.molecular.XLogPDescriptor xlogp; 
	protected boolean enableUserLogP;
	
	public LinearRetentionTimeModel(Settings settings) {
		super(settings);
		this.enableUserLogP = false;
		this.initialise();
	}

	public Double predict(Double value) {
		if(value == null) return null;
		return this.slope * value + this.coefficient;
	}
	
	public void nullify() {
		this.xlogp = null;
	}
	
	public boolean isEnabledUserLogP() {
		return this.enableUserLogP;
	}
	
	/**
	 * initialise slope and coefficient
	 * 
	 */
	private void initialise() {
		String modelFileName = (String)this.settings.get(VariableNames.RETENTION_TIME_TRAINING_FILE_NAME);
		
		Settings settings = new Settings();
		settings.set(VariableNames.LOCAL_DATABASE_PATH_NAME, modelFileName);
		if(this.settings.containsKey(VariableNames.USER_LOG_P_VALUE_NAME) && this.settings.get(VariableNames.USER_LOG_P_VALUE_NAME) != null)
			settings.set(VariableNames.USER_LOG_P_VALUE_NAME, (String)this.settings.get(VariableNames.USER_LOG_P_VALUE_NAME));

		LocalPropertyFileDatabase database = new LocalPropertyFileDatabase(settings);
		java.util.Vector<String> identifiers;
		CandidateList candidateList = null;
		try {
			identifiers = database.getCandidateIdentifiers();
		} catch (Exception e1) {
			e1.printStackTrace();
			return;
		}
		candidateList = database.getCandidateByIdentifier(identifiers);

		if(candidateList.getNumberElements() == 0) return;
		this.xlogp = new org.openscience.cdk.qsar.descriptors.molecular.XLogPDescriptor();
		try {
			this.xlogp.setParameters(new Boolean[] {true, true});
		} catch (CDKException e1) {
			e1.printStackTrace();
		}
		java.util.Vector<String> inchis = new java.util.Vector<String>();
		java.util.Vector<Double> rt_values = new java.util.Vector<Double>();
		java.util.Vector<Double> userLogPs = new java.util.Vector<Double>();
		/*
		 * reading the retention time information for training process
		 */
		ICandidate candidate = candidateList.getElement(0);
		if(!candidate.getProperties().containsKey(VariableNames.RETENTION_TIME_NAME) || 
				(!candidate.getProperties().containsKey(VariableNames.INCHI_NAME) 
						&& !candidate.getProperties().containsKey((String)settings.get(VariableNames.USER_LOG_P_VALUE_NAME)))) {
			return;
		}
		boolean enableInChI = false;
		if(settings.containsKey(VariableNames.USER_LOG_P_VALUE_NAME) 
				&& settings.get(VariableNames.USER_LOG_P_VALUE_NAME) != null
				&& ((String)settings.get(VariableNames.USER_LOG_P_VALUE_NAME)).trim().length() != 0 
				&& candidate.getProperties().containsKey((String)settings.get(VariableNames.USER_LOG_P_VALUE_NAME))) 
		{
			this.enableUserLogP = true;
		}
		else if(candidate.getProperties().containsKey(VariableNames.INCHI_NAME)) {
			enableInChI = true;
		}
		for(int i = 0; i < candidateList.getNumberElements(); i++) {
			candidate = candidateList.getElement(i);
			try {
				if(this.enableUserLogP) {
					userLogPs.add(Double.parseDouble((String)candidate.getProperties().get(this.settings.get(VariableNames.USER_LOG_P_VALUE_NAME))));
				}
				else if(enableInChI) {
					inchis.add((String)candidate.getProperties().get(VariableNames.INCHI_NAME));
				}
				rt_values.add(Double.parseDouble((String)candidate.getProperties().get(VariableNames.RETENTION_TIME_NAME)));
			}
			catch(Exception e) {
				System.err.println("no valid value in " + VariableNames.RETENTION_TIME_TRAINING_FILE_NAME + " in line " + (i + 1) + ": " + (String)candidate.getProperties().get(VariableNames.RETENTION_TIME_NAME));
				continue;
			}
		}
		Double[] logp_values = null;
		/*
		 * if no user logPs are given calculate them with cdk
		 */
		if(userLogPs.size() == 0) {
			logp_values = this.calculateXLogpValues(inchis);
		}
		/*
		 * in case user logPs are given use them 
		 */
		else {
			logp_values = new Double[userLogPs.size()];
			for(int i = 0; i < userLogPs.size(); i++)
				logp_values[i] = userLogPs.get(i);
		}
		Double[][] values = new Double[logp_values.length][2];
		for(int i = 0; i < values.length; i++) {
			values[i][0] = rt_values.get(i);
			values[i][1] = logp_values[i];
		}
		
		this.predictByLeastSquares(values);
	}
	
	/**
	 * 
	 * @param candidate
	 * @return
	 */
	public Double calculateLogPValue(ICandidate candidate) {
		int trials = 0;
		while(true) { 
			try {
				trials++;
				return Double.parseDouble(this.xlogp.calculate(candidate.getPrecursorMolecule().getStructureAsIAtomContainer()).getValue().toString());
			}
			catch(Exception e) {
				if(trials == 10) return null;
				continue;
			}
		}
	}
	
	/**
	 * 
	 * @param inchis
	 * @return
	 */
	public Double[] calculateXLogpValues(java.util.Vector<String> inchis) {
		Double[] values = new Double[inchis.size()];
		if(this.xlogp == null) return values;
		
		try {
			for(int i = 0; i < inchis.size(); i++) {
				DescriptorValue value = null;
				boolean calculated = false;
				int trials = 0;
				while(!calculated) { 
					try {
						trials++;
						value = this.xlogp.calculate(MoleculeFunctions.getAtomContainerFromInChI(inchis.get(i)));
					}
					catch(Exception e) {
						if(trials == 10) {
							break;
						}
						continue;
					}
					calculated = true;
				}
				if(value != null) {
					IDescriptorResult result = value.getValue();
					values[i] = Double.parseDouble(result.toString());
				}
				else values[i] = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		return values;
	}
	
	/**
	 * calculate slope and coefficient by least squares method
	 * 
	 * @param xy_values
	 */
	private void predictByLeastSquares(Double[][] xy_values) {
		double sum_products_xy = 0.0;
		double sum_squares_x = 0.0;
		double mean_x = 0.0;
		double mean_y = 0.0;
		double n = (double)xy_values.length;
		
		
		for(int i = 0; i < xy_values.length; i++) {
			if(xy_values[i][0] == null || xy_values[i][1] == null) {
				n--;
				continue;
			}
			sum_products_xy += xy_values[i][0] * xy_values[i][1];
			sum_squares_x += xy_values[i][0] * xy_values[i][0];
			mean_x += xy_values[i][0];
			mean_y += xy_values[i][1];
		}
		mean_x /= n;
		mean_y /= n;
		
		this.slope = (sum_products_xy - n * mean_x * mean_y) / (sum_squares_x - n * mean_x * mean_x);
		this.coefficient = mean_y - this.slope * mean_x;
	}
	
	public double getSlope() {
		return this.slope;
	}
	
	public double getCoefficient() {
		return this.coefficient;
	}
	
}

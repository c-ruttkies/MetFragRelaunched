package de.ipbhalle.metfraglib.database;

import java.io.FileNotFoundException;
import java.util.ArrayList;

import org.openscience.cdk.ChemObject;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

import de.ipbhalle.metfraglib.additionals.MathTools;
import de.ipbhalle.metfraglib.additionals.MoleculeFunctions;
import de.ipbhalle.metfraglib.candidate.TopDownPrecursorCandidate;
import de.ipbhalle.metfraglib.exceptions.AtomTypeNotKnownFromInputListException;
import de.ipbhalle.metfraglib.exceptions.DatabaseIdentifierNotFoundException;
import de.ipbhalle.metfraglib.interfaces.ICandidate;
import de.ipbhalle.metfraglib.list.CandidateList;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.settings.Settings;

public class LocalDeuteratedSDFDatabase extends AbstractDatabase {

	private java.util.ArrayList<ICandidate> candidates;
	
	public LocalDeuteratedSDFDatabase(Settings settings) {
		super(settings);
	}
	
	public java.util.ArrayList<String> getCandidateIdentifiers() throws Exception {
		if(this.candidates == null) this.readCandidatesFromFile();
		if(this.settings.get(VariableNames.PRECURSOR_DATABASE_IDS_NAME) != null)
			return this.getCandidateIdentifiers((String[])settings.get(VariableNames.PRECURSOR_DATABASE_IDS_NAME));
		if(this.settings.get(VariableNames.PRECURSOR_MOLECULAR_FORMULA_NAME) != null)
			return this.getCandidateIdentifiers((String)settings.get(VariableNames.PRECURSOR_MOLECULAR_FORMULA_NAME));
		if(this.settings.get(VariableNames.DATABASE_RELATIVE_MASS_DEVIATION_NAME) != null)
			return this.getCandidateIdentifiers((Double)settings.get(VariableNames.PRECURSOR_NEUTRAL_MASS_NAME), (Double)settings.get(VariableNames.DATABASE_RELATIVE_MASS_DEVIATION_NAME));
		ArrayList<String> identifiers = new ArrayList<String>();
		for(ICandidate candidate : candidates)
			identifiers.add(candidate.getIdentifier());
		return identifiers;
	}
	
	public ArrayList<String> getCandidateIdentifiers(double monoisotopicMass, double relativeMassDeviation) {
		if(this.candidates == null) this.readCandidatesFromFile();
		ArrayList<String> identifiers = new ArrayList<String>();
		double mzabs = MathTools.calculateAbsoluteDeviation(monoisotopicMass, relativeMassDeviation);
		double lowerLimit = monoisotopicMass - mzabs;
		double upperLimit = monoisotopicMass + mzabs;
		for(int i = 0; i < this.candidates.size(); i++) {
			double currentMonoisotopicMass = (Double)this.candidates.get(i).getProperty(VariableNames.MONOISOTOPIC_MASS_NAME);
			if(lowerLimit <= currentMonoisotopicMass && currentMonoisotopicMass <= upperLimit)
				identifiers.add(this.candidates.get(i).getIdentifier());
		}
		return identifiers;
	}

	public ArrayList<String> getCandidateIdentifiers(String molecularFormula) {
		if(this.candidates == null) this.readCandidatesFromFile();
		ArrayList<String> identifiers = new ArrayList<String>();
		org.openscience.cdk.interfaces.IMolecularFormula queryFormula = MolecularFormulaManipulator.getMolecularFormula(molecularFormula, new ChemObject().getBuilder());
		for(int i = 0; i < this.candidates.size(); i++) {
			org.openscience.cdk.interfaces.IMolecularFormula currentFormula = null;
			try {
				currentFormula = MolecularFormulaManipulator.getMolecularFormula(MoleculeFunctions.convertExplicitToImplicitHydrogens(this.candidates.get(i).getAtomContainer()));
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(queryFormula.equals(currentFormula)) identifiers.add(this.candidates.get(i).getIdentifier());
		}
		return identifiers;
	}

	public ArrayList<String> getCandidateIdentifiers(ArrayList<String> identifiers) {
		if(this.candidates == null) this.readCandidatesFromFile();
		ArrayList<String> verifiedIdentifiers = new ArrayList<String>();
		for(int i = 0; i < identifiers.size(); i++) {
			try {
				this.getCandidateByIdentifier(identifiers.get(i));
			} catch (DatabaseIdentifierNotFoundException e) {
				logger.warn("Candidate identifier " + identifiers.get(i) + " not found.");
				continue;
			}
			verifiedIdentifiers.add(identifiers.get(i));
		}
		return verifiedIdentifiers;
	
	}

	public ICandidate getCandidateByIdentifier(String identifier) throws DatabaseIdentifierNotFoundException {
		int index = this.indexOfIdentifier(identifier);
		if(index == -1) 
			throw new DatabaseIdentifierNotFoundException(identifier);
		return this.candidates.get(index);
	}

	public CandidateList getCandidateByIdentifier(ArrayList<String> identifiers) {
		CandidateList candidateList = new CandidateList();
		for(int i = 0; i < identifiers.size(); i++) {
			ICandidate candidate = null;
			try {
				candidate = this.getCandidateByIdentifier(identifiers.get(i));
			} catch (DatabaseIdentifierNotFoundException e) {
				logger.warn("Candidate identifier " + identifiers.get(i) + " not found.");
			}
			if(candidate != null) candidateList.addElement(candidate);
		}
		return candidateList;
	}

	public void nullify() {
	}

	/**
	 * 
	 */
	private void readCandidatesFromFile() {
		this.candidates = new java.util.ArrayList<ICandidate>();
		java.io.File f = new java.io.File((String)this.settings.get(VariableNames.LOCAL_DATABASE_PATH_NAME));
		if(f.isFile())
		{
			IteratingSDFReader reader;
			try {
				reader = new IteratingSDFReader(new java.io.FileReader(f), DefaultChemObjectBuilder.getInstance());
				int index = 1;
				while(reader.hasNext()) {
					IAtomContainer molecule = reader.next();
					String identifier = molecule.getID();
					if(molecule.getProperty("Identifier") != null) 
						identifier = (String)molecule.getProperty("Identifier");
					molecule = MoleculeFunctions.convertImplicitToExplicitHydrogens(molecule);
					AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molecule);
					
					if(identifier == null || identifier.length() == 0) identifier = String.valueOf(index);
				
					String[] inchiInfo = MoleculeFunctions.getInChIInfoFromAtomContainer(molecule);
					ICandidate precursorCandidate = new TopDownPrecursorCandidate(inchiInfo[0], identifier);
					
					java.util.Iterator<Object> properties = molecule.getProperties().keySet().iterator();
					while(properties.hasNext()) {
						String key = (String)properties.next();
						if(key != null && molecule.getProperty(key) != null)
							precursorCandidate.setProperty(key, molecule.getProperty(key));
					}
					precursorCandidate.setProperty(VariableNames.INCHI_KEY_1_NAME, inchiInfo[1].split("-")[0]);
					precursorCandidate.setProperty(VariableNames.INCHI_KEY_2_NAME, inchiInfo[1].split("-")[1]);
					precursorCandidate.setProperty(VariableNames.MOLECULAR_FORMULA_NAME, inchiInfo[0].split("/")[1]);
					try {
						precursorCandidate.setProperty(VariableNames.MONOISOTOPIC_MASS_NAME, precursorCandidate.getMolecularFormula().getMonoisotopicMass());
					} catch (AtomTypeNotKnownFromInputListException e) {
						continue;
					}
					this.candidates.add(precursorCandidate);
					index++;
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (CDKException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * 
	 * @param identifier
	 * @return
	 */
	private int indexOfIdentifier(String identifier) {
		for(int i = 0; i < this.candidates.size(); i++)
			if(this.candidates.get(i).getIdentifier().equals(identifier)) return i;
		return -1;
	}
}

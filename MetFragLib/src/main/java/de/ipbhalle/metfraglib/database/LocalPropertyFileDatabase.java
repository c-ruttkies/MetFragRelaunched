package de.ipbhalle.metfraglib.database;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Vector;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import de.ipbhalle.metfraglib.additionals.MathTools;
import de.ipbhalle.metfraglib.candidate.TopDownPrecursorCandidate;
import de.ipbhalle.metfraglib.exceptions.DatabaseIdentifierNotFoundException;
import de.ipbhalle.metfraglib.exceptions.MultipleHeadersFoundInInputDatabaseException;
import de.ipbhalle.metfraglib.interfaces.ICandidate;
import de.ipbhalle.metfraglib.list.CandidateList;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.settings.Settings;

/**
 * 
 * InChI database file with one candidate entry per line semicolon separated
 * like: Identifier|InChI|MolecularFormula|MonoisotopicMass|InChIKey1|InChIKey2
 * EA021313
 * |InChI=1S/C12H17NO/c1-4-13(5-2)12(14)11-8-6-7-10(3)9-11/h6-9H,4-5H2,1-
 * 3H3|C12H17NO|191.131014|MMOXZBCLCQITDF|UHFFFAOYSA
 * 
 * @author chrisr
 * 
 */
public class LocalPropertyFileDatabase extends AbstractDatabase {

	private java.util.Vector<ICandidate> candidates;

	public LocalPropertyFileDatabase(Settings settings) {
		super(settings);
	}

	public java.util.Vector<String> getCandidateIdentifiers()
			throws MultipleHeadersFoundInInputDatabaseException, Exception {
		if (this.candidates == null)
			this.readCandidatesFromFile();
		if (this.settings.get(VariableNames.PRECURSOR_DATABASE_IDS_NAME) != null)
			return this.getCandidateIdentifiers((String[]) settings
					.get(VariableNames.PRECURSOR_DATABASE_IDS_NAME));
		if (this.settings.get(VariableNames.PRECURSOR_MOLECULAR_FORMULA_NAME) != null)
			return this.getCandidateIdentifiers((String) settings
					.get(VariableNames.PRECURSOR_MOLECULAR_FORMULA_NAME));
		if (this.settings
				.get(VariableNames.DATABASE_RELATIVE_MASS_DEVIATION_NAME) != null)
			return this
					.getCandidateIdentifiers(
							(Double) settings
									.get(VariableNames.PRECURSOR_NEUTRAL_MASS_NAME),
							(Double) settings
									.get(VariableNames.DATABASE_RELATIVE_MASS_DEVIATION_NAME));
		Vector<String> identifiers = new Vector<String>();
		for (ICandidate candidate : candidates) {
			identifiers.add(candidate.getIdentifier());
		}
		return identifiers;
	}

	public Vector<String> getCandidateIdentifiers(double monoisotopicMass,
			double relativeMassDeviation)
			throws MultipleHeadersFoundInInputDatabaseException, Exception {
		if (this.candidates == null)
			this.readCandidatesFromFile();
		Vector<String> identifiers = new Vector<String>();
		double mzabs = MathTools.calculateAbsoluteDeviation(monoisotopicMass,
				relativeMassDeviation);
		double lowerLimit = monoisotopicMass - mzabs;
		double upperLimit = monoisotopicMass + mzabs;
		for (int i = 0; i < this.candidates.size(); i++) {
			double currentMonoisotopicMass = (Double) this.candidates.get(i)
					.getProperty(VariableNames.MONOISOTOPIC_MASS_NAME);
			if (lowerLimit <= currentMonoisotopicMass
					&& currentMonoisotopicMass <= upperLimit)
				identifiers.add(this.candidates.get(i).getIdentifier());
		}
		return identifiers;
	}

	public Vector<String> getCandidateIdentifiers(String molecularFormula)
			throws Exception {
		if (this.candidates == null)
			try {
				this.readCandidatesFromFile();
			} catch (MultipleHeadersFoundInInputDatabaseException e) {
				e.printStackTrace();
			}
		Vector<String> identifiers = new Vector<String>();
		for (int i = 0; i < this.candidates.size(); i++) {
			if (molecularFormula.equals(this.candidates.get(i).getProperty(
					VariableNames.MOLECULAR_FORMULA_NAME)))
				identifiers.add(this.candidates.get(i).getIdentifier());
		}
		return identifiers;
	}

	public Vector<String> getCandidateIdentifiers(Vector<String> identifiers)
			throws MultipleHeadersFoundInInputDatabaseException, Exception {
		if (this.candidates == null)
			this.readCandidatesFromFile();
		Vector<String> verifiedIdentifiers = new Vector<String>();
		for (int i = 0; i < identifiers.size(); i++) {
			try {
				this.getCandidateByIdentifier(identifiers.get(i));
			} catch (DatabaseIdentifierNotFoundException e) {
				logger.warn("Candidate identifier " + identifiers.get(i)
						+ " not found.");
				continue;
			}
			verifiedIdentifiers.add(identifiers.get(i));
		}
		return verifiedIdentifiers;

	}

	public ICandidate getCandidateByIdentifier(String identifier)
			throws DatabaseIdentifierNotFoundException {
		int index = this.indexOfIdentifier(identifier);
		if (index == -1)
			throw new DatabaseIdentifierNotFoundException(identifier);
		return this.candidates.get(index);
	}

	public CandidateList getCandidateByIdentifier(Vector<String> identifiers) {
		CandidateList candidateList = new CandidateList();
		for (int i = 0; i < identifiers.size(); i++) {
			ICandidate candidate = null;
			try {
				candidate = this.getCandidateByIdentifier(identifiers.get(i));
			} catch (DatabaseIdentifierNotFoundException e) {
				logger.warn("Candidate identifier " + identifiers.get(i)
						+ " not found.");
			}
			if (candidate != null)
				candidateList.addElement(candidate);
		}
		return candidateList;
	}

	public void nullify() {
	}
	
	private void readCandidatesFromFile() throws MultipleHeadersFoundInInputDatabaseException, Exception {
		this.candidates = new java.util.Vector<ICandidate>();
		java.io.File f = new java.io.File((String) this.settings.get(VariableNames.LOCAL_DATABASE_PATH_NAME));
		java.util.List<String> propertyNames = new java.util.ArrayList<String>();
		
		BufferedReader reader = null;
		if (f.isFile()) {
			reader = new BufferedReader(new FileReader(f));
			CSVParser parser = new CSVParser(reader, CSVFormat.EXCEL.withHeader());
			java.util.Iterator<?> it = parser.getHeaderMap().keySet().iterator();
			boolean identifierColDefined = false;
			boolean inchiColDefined = false;
			while(it.hasNext()) {
				String colname = (String)it.next();
				propertyNames.add(colname);
				if(colname.equals(VariableNames.IDENTIFIER_NAME)) identifierColDefined = true;
				if(colname.equals(VariableNames.INCHI_NAME)) inchiColDefined = true;
			}
			int recordNumber = 0;
			for(CSVRecord record : parser) {
				recordNumber++;
				String inchi = inchiColDefined ? record.get(VariableNames.INCHI_NAME) : "";
				String identifier = identifierColDefined ? record.get(VariableNames.IDENTIFIER_NAME) : 
					String.valueOf(recordNumber);
				ICandidate precursorCandidate = new TopDownPrecursorCandidate(
						inchi, identifier);
				if(!inchiColDefined)
					precursorCandidate.getProperties().remove(VariableNames.INCHI_NAME);
				
				for(int ii = 0; ii < propertyNames.size(); ii++) {
					String colname = propertyNames.get(ii);
					if(!colname.equals(VariableNames.INCHI_NAME) && !colname.equals(VariableNames.IDENTIFIER_NAME)) {
						if(colname.equals(VariableNames.MONOISOTOPIC_MASS_NAME))
							precursorCandidate.setProperty(VariableNames.MONOISOTOPIC_MASS_NAME, Double.parseDouble(record.get(VariableNames.MONOISOTOPIC_MASS_NAME)));
						else if (colname.equals(VariableNames.VARIABLE_DEUTERIUM_COUNT_NAME))
							precursorCandidate.setProperty(colname, Byte.parseByte(record.get(colname)));
						else {
							precursorCandidate.setProperty(colname, record.get(colname));
						}
					}	
				}
				this.candidates.add(precursorCandidate);
			
			}
			
			parser.close();
			reader.close();
			
			return;
		}
		throw new Exception();
	}
	
	/**
	 * 
	 * @param identifier
	 * @return
	 */
	private int indexOfIdentifier(String identifier) {
		for (int i = 0; i < this.candidates.size(); i++)
			if (this.candidates.get(i).getIdentifier().equals(identifier))
				return i;
		return -1;
	}
}

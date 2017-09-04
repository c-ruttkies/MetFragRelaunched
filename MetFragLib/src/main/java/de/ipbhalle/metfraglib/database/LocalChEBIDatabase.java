package de.ipbhalle.metfraglib.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import de.ipbhalle.metfraglib.candidate.TopDownPrecursorCandidate;
import de.ipbhalle.metfraglib.exceptions.DatabaseIdentifierNotFoundException;
import de.ipbhalle.metfraglib.interfaces.ICandidate;
import de.ipbhalle.metfraglib.list.CandidateList;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.settings.Settings;

public class LocalChEBIDatabase extends LocalPostgresDatabase {
	
	public LocalChEBIDatabase(Settings settings) {
		super(settings);
		
		this.DATABASE_NAME 			= 	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_NAME					); 
		this.TABLE_NAME				=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_COMPOUND_TABLE_NAME	);
		this.PORT					=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_PORT_NUMBER_NAME		);
		this.SERVER					=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_SERVER_IP_NAME			);
		this.MASS_COLUMN_NAME		=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_MASS_COLUMN_NAME		);
		this.FORMULA_COLUMN_NAME	=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_FORMULA_COLUMN_NAME	);
		this.INCHI_COLUMN_NAME		=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_INCHI_COLUMN_NAME		);
		this.INCHIKEY1_COLUMN_NAME	=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_INCHIKEY1_COLUMN_NAME	);
		this.INCHIKEY2_COLUMN_NAME	=	(String) settings.get(	VariableNames.LOCAL_CHEBI_DATABASE_INCHIKEY2_COLUMN_NAME	);
		this.CID_COLUMN_NAME		=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_CID_COLUMN_NAME		);
		this.SMILES_COLUMN_NAME		=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_SMILES_COLUMN_NAME		);
		this.COMPOUND_NAME_COLUMN_NAME		=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_COMPOUND_NAME_COLUMN_NAME		);
		
		this.db_user				=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_USER_NAME				);
		this.db_password			=	(String) settings.get( 	VariableNames.LOCAL_CHEBI_DATABASE_PASSWORD_NAME			);
		
	}

	public ICandidate getCandidateByIdentifier(String identifier)
			throws DatabaseIdentifierNotFoundException {
		String fields = this.CID_COLUMN_NAME + "," + this.INCHI_COLUMN_NAME + "," + this.INCHIKEY1_COLUMN_NAME 
				+ "," + this.INCHIKEY2_COLUMN_NAME + "," + this.SMILES_COLUMN_NAME + "," + this.MASS_COLUMN_NAME ;
		if(this.COMPOUND_NAME_COLUMN_NAME != null && this.COMPOUND_NAME_COLUMN_NAME.length() != 0) fields += "," +this.COMPOUND_NAME_COLUMN_NAME;
		String query = "SELECT " + fields + " from " + this.TABLE_NAME + " where " 
				+ this.CID_COLUMN_NAME + " =\'" + identifier + "\';";;
		ResultSet rs = this.submitQuery(query);
		if(rs == null) return null;
		ArrayList<String> inchis = new ArrayList<String>();
		ArrayList<String> inChIKeys1 = new ArrayList<String>();
		ArrayList<String> inChIKeys2 = new ArrayList<String>();
		ArrayList<String> formulas = new ArrayList<String>();
		ArrayList<String> names = new ArrayList<String>();
		ArrayList<String> smiles = new ArrayList<String>();
		ArrayList<Double> masses = new ArrayList<Double>();
		try {
			while(rs.next()) {
				inchis.add(rs.getString(this.INCHI_COLUMN_NAME));
				inChIKeys1.add(rs.getString(this.INCHIKEY1_COLUMN_NAME));
				inChIKeys2.add(rs.getString(this.INCHIKEY2_COLUMN_NAME));
				smiles.add(rs.getString(this.SMILES_COLUMN_NAME));
				masses.add(rs.getDouble(this.MASS_COLUMN_NAME));
				formulas.add(rs.getString(this.INCHI_COLUMN_NAME).split("/")[1]);
				if(rs.getString(this.COMPOUND_NAME_COLUMN_NAME) != null) names.add(rs.getString(this.COMPOUND_NAME_COLUMN_NAME));
				else names.add("NA");
			}
			rs.close();
			this.statement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		ICandidate candidate = new TopDownPrecursorCandidate(inchis.get(0), identifier);
		candidate.setProperty(VariableNames.INCHI_KEY_1_NAME, inChIKeys1.get(0));
		candidate.setProperty(VariableNames.INCHI_KEY_2_NAME, inChIKeys2.get(0));
		candidate.setProperty(VariableNames.MOLECULAR_FORMULA_NAME, formulas.get(0));
		candidate.setProperty(VariableNames.MONOISOTOPIC_MASS_NAME, masses.get(0));
		candidate.setProperty(VariableNames.SMILES_NAME, smiles.get(0));
		candidate.setProperty(VariableNames.COMPOUND_NAME_NAME, names.get(0));
		return candidate;
	}

	/**
	 * 
	 */
	public CandidateList getCandidateByIdentifier(ArrayList<String> identifiers) {
		if(identifiers.size() == 0) return new CandidateList();
	/*	String query = "SELECT " + this.CID_COLUMN_NAME + ", " 
				+ this.INCHI_COLUMN_NAME + "," + this.INCHIKEY1_COLUMN_NAME + "," 
				+ this.INCHIKEY2_COLUMN_NAME + " from " + this.TABLE_NAME + " where " 
				+ this.CID_COLUMN_NAME + " =\'" + identifiers.get(0) + "\'"; */
		String fields = this.CID_COLUMN_NAME + "," + this.INCHI_COLUMN_NAME + "," + this.INCHIKEY1_COLUMN_NAME 
				+ "," + this.INCHIKEY2_COLUMN_NAME + "," + this.SMILES_COLUMN_NAME + "," + this.MASS_COLUMN_NAME;
		if(this.COMPOUND_NAME_COLUMN_NAME != null && this.COMPOUND_NAME_COLUMN_NAME.length() != 0) fields += "," + this.COMPOUND_NAME_COLUMN_NAME;
		String query = "SELECT " + fields + " from " + this.TABLE_NAME + " where " 
				+ this.CID_COLUMN_NAME + " in (\'" + identifiers.get(0) + "\'";
		
		for(String cid : identifiers)
			query += ",\'" + cid + "\'";
		query += ");";

		ResultSet rs = this.submitQuery(query);
		if(rs == null) return new CandidateList();
		CandidateList candidates = new CandidateList();
		try {
			while(rs.next()) {
				ICandidate candidate = new TopDownPrecursorCandidate(rs.getString(this.INCHI_COLUMN_NAME), rs.getString(this.CID_COLUMN_NAME));
				candidate.setProperty(VariableNames.INCHI_KEY_1_NAME, rs.getString(this.INCHIKEY1_COLUMN_NAME));
				candidate.setProperty(VariableNames.INCHI_KEY_2_NAME, rs.getString(this.INCHIKEY2_COLUMN_NAME));
				candidate.setProperty(VariableNames.MOLECULAR_FORMULA_NAME, rs.getString(this.INCHI_COLUMN_NAME).split("/")[1]);
				candidate.setProperty(VariableNames.MONOISOTOPIC_MASS_NAME, rs.getDouble(this.MASS_COLUMN_NAME));
				candidate.setProperty(VariableNames.SMILES_NAME, rs.getString(this.SMILES_COLUMN_NAME));

				if(rs.getString(this.COMPOUND_NAME_COLUMN_NAME) != null) 
					candidate.setProperty(VariableNames.COMPOUND_NAME_NAME, rs.getString(this.COMPOUND_NAME_COLUMN_NAME));
				else
					candidate.setProperty(VariableNames.COMPOUND_NAME_NAME, "NA");
				candidates.addElement(candidate);
			}
			rs.close();
			this.statement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return candidates;
	}

}

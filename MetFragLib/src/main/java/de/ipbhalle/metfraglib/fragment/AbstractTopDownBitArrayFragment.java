package de.ipbhalle.metfraglib.fragment;

import de.ipbhalle.metfraglib.FastBitArray;
import de.ipbhalle.metfraglib.precursor.AbstractTopDownBitArrayPrecursor;

public abstract class AbstractTopDownBitArrayFragment extends DefaultBitArrayFragment {

	protected AbstractTopDownBitArrayFragment precursorFragment;
	protected java.util.Vector<AbstractTopDownBitArrayFragment> children;
	/*
	 * value needed during fragment generation
	 * stores whether the fragment was generated by a ring bond cleavage without losing an additional atom
	 * which won't result in a new valid fragment
	 * this variable can then be used to set the proper precursor fragment
	 */
	protected boolean wasRingCleavedFragment;
	protected byte addedToQueueCounts;
	protected short lastSkippedBond;
	protected boolean hasMatchedChild;
	
	public AbstractTopDownBitArrayFragment(AbstractTopDownBitArrayPrecursor precursor) {
		super(precursor);
		this.wasRingCleavedFragment = false;
		this.addedToQueueCounts = 0;
		this.lastSkippedBond = -1;
		this.hasMatchedChild = false;
	}
	
	public AbstractTopDownBitArrayFragment(AbstractTopDownBitArrayPrecursor precursorMolecule,
			FastBitArray atomsFastBitArray, FastBitArray bondsFastBitArray,
			FastBitArray brokenBondsFastBitArray) {
		super(precursorMolecule, atomsFastBitArray, bondsFastBitArray, brokenBondsFastBitArray);
		this.lastSkippedBond = -1;
	}

	public abstract AbstractTopDownBitArrayFragment[] traverseMolecule(short bondIndexToRemove, short[] indecesOfBondConnectedAtoms);

	public int getMaximalIndexOfRemovedBond() {
		return this.brokenBondsFastBitArray.getLastSetBit();
	}

	public boolean hasMatchedChild() {
		return this.hasMatchedChild;
	}

	public void setHasMatchedChild(boolean hasMatchedChild) {
		this.hasMatchedChild = hasMatchedChild;
	}

	public short getLastSkippedBond() {
		return lastSkippedBond;
	}

	public void setLastSkippedBond(short lastSkippedBond) {
		this.lastSkippedBond = lastSkippedBond;
	}

	public void shallowNullify() {
		super.shallowNullify();
		this.children = null;
		this.precursorFragment = null;
	}
	
	public void nullify() {
		super.nullify();
		this.children = null;
		this.precursorFragment = null;
	}
	
	public void setPrecursorFragment(AbstractTopDownBitArrayFragment precursorFragment) {
		this.precursorFragment = precursorFragment;
	}
	
	public void addChild(AbstractTopDownBitArrayFragment child) {
		if(this.children == null)
			this.children = new java.util.Vector<AbstractTopDownBitArrayFragment>();
		this.children.add(child);
	}
	
	public AbstractTopDownBitArrayFragment getPrecursorFragment() {
		return this.precursorFragment;
	}
	
	public void setPrecursorFragments(boolean value) {
		AbstractTopDownBitArrayFragment precursorFragment = this.precursorFragment;
		while(precursorFragment != null) {
			precursorFragment.setHasMatchedChild(value);
			precursorFragment = precursorFragment.getPrecursorFragment();
		}
	}
	
	public java.util.Vector<AbstractTopDownBitArrayFragment> getChildren() {
		return this.children;
	}
	
	public int getNumberOfChildren() {
		if(this.children == null) return 0;
		return this.children.size();
	}
	
	public boolean hasChildren() {
		if(this.children == null || this.children.size() == 0)
			return false;
		return true;
	}
	
	public byte getAddedToQueueCounts() {
		return addedToQueueCounts;
	}

	public void setAddedToQueueCounts(byte addedToQueueCounts) {
		this.addedToQueueCounts = addedToQueueCounts;
	}

	public boolean isWasRingCleavedFragment() {
		return wasRingCleavedFragment;
	}

	public void setWasRingCleavedFragment(boolean wasRingCleavedFragment) {
		this.wasRingCleavedFragment = wasRingCleavedFragment;
	}
	
	public boolean hasPrecursorFragment() {
		return this.precursorFragment == null ? false : true;
	}
}

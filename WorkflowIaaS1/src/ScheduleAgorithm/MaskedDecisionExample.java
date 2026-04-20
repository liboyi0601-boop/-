package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.List;

public final class MaskedDecisionExample
{
	private final List<double[]> candidateFeatures;
	private final boolean[] validMask;
	private final int chosenIndex;

	public MaskedDecisionExample(List<double[]> candidateFeatures, List<Boolean> validSelections, int chosenIndex)
	{
		this.candidateFeatures = copyFeatures(candidateFeatures);
		this.validMask = copyMask(validSelections);
		this.chosenIndex = chosenIndex;
	}

	public int size()
	{
		return candidateFeatures.size();
	}

	public int getChosenIndex()
	{
		return chosenIndex;
	}

	public boolean isValid(int index)
	{
		return index >= 0 && index < validMask.length && validMask[index];
	}

	public double[] getCandidateFeatures(int index)
	{
		return candidateFeatures.get(index);
	}

	public int getFeatureSize()
	{
		if(candidateFeatures.isEmpty())
		{
			return 0;
		}
		return candidateFeatures.get(0).length;
	}

	private List<double[]> copyFeatures(List<double[]> sourceFeatures)
	{
		List<double[]> copiedFeatures = new ArrayList<double[]>(sourceFeatures.size());
		for(double[] featureVector: sourceFeatures)
		{
			double[] copied = new double[featureVector.length];
			System.arraycopy(featureVector, 0, copied, 0, featureVector.length);
			copiedFeatures.add(copied);
		}
		return copiedFeatures;
	}

	private boolean[] copyMask(List<Boolean> validSelections)
	{
		boolean[] copiedMask = new boolean[validSelections.size()];
		for(int index = 0; index < validSelections.size(); index++)
		{
			copiedMask[index] = validSelections.get(index).booleanValue();
		}
		return copiedMask;
	}
}

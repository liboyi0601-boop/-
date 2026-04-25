package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VmActionMask
{
	private final List<Boolean> validSelections;

	public VmActionMask(List<Boolean> validSelections)
	{
		this.validSelections = Collections.unmodifiableList(new ArrayList<Boolean>(validSelections));
	}

	public List<Boolean> getValidSelections()
	{
		return validSelections;
	}

	public boolean isValid(int index)
	{
		return index >= 0 && index < validSelections.size() && validSelections.get(index).booleanValue();
	}
}

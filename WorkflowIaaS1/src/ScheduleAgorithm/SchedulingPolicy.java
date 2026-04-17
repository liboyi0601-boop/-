package ScheduleAgorithm;

import java.util.List;

import vmInfo.SaaSVm;
import workflow.WTask;

public interface SchedulingPolicy
{
	SchedulingAction selectAction(WTask task, List<SaaSVm> vmList);
}

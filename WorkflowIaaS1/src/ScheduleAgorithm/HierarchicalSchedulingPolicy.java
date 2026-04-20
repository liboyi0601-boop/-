package ScheduleAgorithm;

public interface HierarchicalSchedulingPolicy
{
	TaskSelection selectTask(TaskCandidateSet taskSet, SchedulingState state);

	ResourceSelection selectResource(TaskCandidateView selectedTask, VmCandidateSet vmSet, SchedulingState state);
}

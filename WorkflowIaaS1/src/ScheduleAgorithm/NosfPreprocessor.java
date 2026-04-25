package ScheduleAgorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import share.StaticfinalTags;
import workflow.ConstraintWTask;
import workflow.WTask;
import workflow.Workflow;

public class NosfPreprocessor
{
	public void initializeExecutionTimeEstimate(List<Workflow> workflows)
	{
		for(Workflow tempWorkflow: workflows)
		{
			for(WTask tempTask: tempWorkflow.getTaskList())
			{
				double standardDeviation = tempTask.getBaseExecutionTime()*StaticfinalTags.standardDeviation;
				int executionTimeWithConfidency = (int)(tempTask.getBaseExecutionTime()
						+ StaticfinalTags.VarDeviation * standardDeviation);
				tempTask.setExecutionTimeWithConfidency(executionTimeWithConfidency);
			}
		}
	}

	public void preprocessWorkflow(Workflow workflow)
	{
		calculateWorkflowTaskLeastTime(workflow);
		calculateWorkflowTaskEarliestTime(workflow);
		searchPCPsForWorkflow(workflow);
		assignSubDeadlineBasedOnPcp(workflow);
	}

	public void updateSuccessorReadyTasks(List<WTask> finishedTasks)
	{
		for(WTask finishedTask: finishedTasks)
		{
			List<ConstraintWTask> successorList = finishedTask.getSuccessorTaskList();
			for(ConstraintWTask successorConn: successorList)
			{
				WTask task = successorConn.getWTask();

				int maxDataArrival = Integer.MIN_VALUE;
				int executeTime = task.getExecutionTimeWithConfidency();
				boolean ready = true;

				for(ConstraintWTask parentConWTask: task.getParentTaskList())
				{
					if(!parentConWTask.getWTask().getFinishFlag())
					{
						ready = false;
						break;
					}
					else
					{
						int tempDataArrival = parentConWTask.getWTask().getRealFinishTime()
								+ parentConWTask.getDataSize()/StaticfinalTags.bandwidth;
						if(tempDataArrival > maxDataArrival)
						{
							maxDataArrival = tempDataArrival;
						}
					}
				}

				if(ready)
				{
					if(!task.getAllocatedFlag())
					{
						int newPEST = maxDataArrival;
						int newPECT = maxDataArrival + executeTime;

						task.setEarliestStartTime(newPEST);
						task.setEarliestFinishTime(newPECT);

						int newSubDeadline = task.getEarliestStartTime() + task.getSubSpan();
						if(newSubDeadline >= task.getLeastFinishTime())
						{
							newSubDeadline = task.getLeastFinishTime();
						}

						task.setSubDeadLine(newSubDeadline);
					}
					else
					{
						System.out.println("Error : the target task that will be updated has been scheduled!");
					}
				}
			}
		}
	}

	public List<WTask> collectReadyTasksForArrivals(List<Workflow> arrivingWorkflows)
	{
		List<WTask> readyTaskList = new ArrayList<WTask>();

		for(Workflow roundWorkflow: arrivingWorkflows)
		{
			for(WTask tempWTask: roundWorkflow.getTaskList())
			{
				if(!tempWTask.getAllocatedFlag())
				{
					boolean ready = true;
					for(ConstraintWTask parentConstraint: tempWTask.getParentTaskList())
					{
						if(!parentConstraint.getWTask().getFinishFlag())
						{
							ready = false;
							break;
						}
					}
					if(ready)
					{
						readyTaskList.add(tempWTask);
					}
				}
			}
		}

		return readyTaskList;
	}

	public List<WTask> collectReadyTasksFromGlobalPool(List<WTask> globalTaskPool)
	{
		List<WTask> readyWTaskList = new ArrayList<WTask>();
		for(WTask task: globalTaskPool)
		{
			if(task.getParentTaskList().size() == 0)
			{
				readyWTaskList.add(task);
			}
			else
			{
				boolean ready = true;
				for(ConstraintWTask parentConWTask: task.getParentTaskList())
				{
					if(!parentConWTask.getWTask().getFinishFlag())
					{
						ready = false;
						break;
					}
				}

				if(ready)
				{
					if(!task.getAllocatedFlag())
					{
						readyWTaskList.add(task);
					}
					else
					{
						System.out.println("Error in getReadyWTaskInGlobalPool(List<WTask> WTaskList): the task has been scheduled!");
					}
				}
			}
		}

		return readyWTaskList;
	}

	private void calculateWorkflowTaskLeastTime(Workflow workflow)
	{
		List<WTask> calculatedTaskList = new ArrayList<WTask>();
		while(true)
		{
			for(WTask task: workflow.getTaskList())
			{
				if(task.getLeastFinishTime() != -1)
				{
					continue;
				}

				int executionTime = task.getExecutionTimeWithConfidency();
				if(task.getSuccessorTaskList().size() == 0)
				{
					task.setLeastFinishTime(workflow.getDeadline());
					task.setLeastStartTime(task.getLeastFinishTime() - executionTime);
					calculatedTaskList.add(task);
				}
				else
				{
					int leastFinishTime = Integer.MAX_VALUE;
					boolean unCalculatedSucessor = false;
					for(ConstraintWTask sucessorConn: task.getSuccessorTaskList())
					{
						if(sucessorConn.getWTask().getLeastFinishTime() != -1)
						{
							int tempLeastFT = sucessorConn.getWTask().getLeastStartTime()
									- sucessorConn.getDataSize()/StaticfinalTags.bandwidth;
							if(tempLeastFT < leastFinishTime)
							{
								leastFinishTime = tempLeastFT;
							}
						}
						else
						{
							unCalculatedSucessor = true;
							break;
						}
					}
					if(unCalculatedSucessor == false)
					{
						task.setLeastFinishTime(leastFinishTime);
						task.setLeastStartTime(leastFinishTime - executionTime);
						calculatedTaskList.add(task);

						if(task.getLeastStartTime() < workflow.getArrivalTime())
						{
							throw new IllegalArgumentException(
									"The least start time of task is less than its workflow's arrival time!");
						}
					}
				}
			}

			if(calculatedTaskList.size() == workflow.getTaskList().size())
			{
				break;
			}
		}
	}

	private void calculateWorkflowTaskEarliestTime(Workflow workflow)
	{
		List<WTask> calculatedTaskList = new ArrayList<WTask>();
		while(true)
		{
			for(WTask task: workflow.getTaskList())
			{
				if(task.getEarliestStartTime() != -1)
				{
					continue;
				}

				int executionTime = task.getExecutionTimeWithConfidency();
				if(task.getParentTaskList().size() == 0)
				{
					task.setEarliestStartTime(workflow.getArrivalTime());
					task.setEarliestFinishTime(task.getEarliestStartTime() + executionTime);
					calculatedTaskList.add(task);
				}
				else
				{
					int earliestStartTime = -1;
					boolean unCalculatedParent = false;
					for(ConstraintWTask parentConn: task.getParentTaskList())
					{
						if(parentConn.getWTask().getEarliestStartTime() != -1)
						{
							int tempEarliestST = parentConn.getWTask().getEarliestFinishTime()
									+ parentConn.getDataSize()/StaticfinalTags.bandwidth;
							if(tempEarliestST > earliestStartTime)
							{
								earliestStartTime = tempEarliestST;
							}
						}
						else
						{
							unCalculatedParent = true;
							break;
						}
					}
					if(unCalculatedParent == false)
					{
						task.setEarliestStartTime(earliestStartTime);
						task.setEarliestFinishTime(earliestStartTime + executionTime);
						calculatedTaskList.add(task);
						if(task.getEarliestFinishTime() > workflow.getDeadline())
						{
							throw new IllegalArgumentException(
									"The earliest finish time of task is more than its workflow's deadline!");
						}
					}
				}
			}

			if(calculatedTaskList.size() == workflow.getTaskList().size())
			{
				break;
			}
		}
	}

	private void searchPCPsForWorkflow(Workflow workflow)
	{
		List<WTask> assignedWTaskList = new ArrayList<WTask>();
		int pcpNum = 1;

		while(assignedWTaskList.size() != workflow.getTaskList().size())
		{
			WTask startWTask = null;
			int maxEarlistFinishTime = -1;

			for(WTask task: workflow.getTaskList())
			{
				if(task.getPCPNum() == -1 && task.getSuccessorTaskList().size() == 0)
				{
					if(task.getEarliestFinishTime() > maxEarlistFinishTime)
					{
						maxEarlistFinishTime = task.getEarliestFinishTime();
						startWTask = task;
					}
				}
			}

			if(startWTask == null)
			{
				for(WTask tempWTask: assignedWTaskList)
				{
					int maxCPdataArrivalTime = -1;
					for(ConstraintWTask conWTask: tempWTask.getParentTaskList())
					{
						if(conWTask.getWTask().getPCPNum() == -1)
						{
							int dataArrivalTime = conWTask.getWTask().getEarliestFinishTime()
									+ conWTask.getDataSize()/StaticfinalTags.bandwidth;
							if(dataArrivalTime > maxCPdataArrivalTime)
							{
								maxCPdataArrivalTime = dataArrivalTime;
								startWTask = conWTask.getWTask();
							}
						}
					}
					if(startWTask != null)
					{
						break;
					}
				}
			}

			while(startWTask != null)
			{
				startWTask.setPCPNum(pcpNum);
				assignedWTaskList.add(startWTask);

				WTask tempCPTask = null;
				int maxCPdataArrivalTime = -1;
				for(ConstraintWTask conWTask: startWTask.getParentTaskList())
				{
					if(conWTask.getWTask().getPCPNum() == -1)
					{
						int dataArivalTime = conWTask.getWTask().getEarliestFinishTime()
								+ conWTask.getDataSize()/StaticfinalTags.bandwidth;
						if(dataArivalTime > maxCPdataArrivalTime)
						{
							tempCPTask = conWTask.getWTask();
						}
					}
				}
				startWTask = tempCPTask;
			}

			pcpNum++;
		}
	}

	private void assignSubDeadlineBasedOnPcp(Workflow workflow)
	{
		int pcpId = 1;
		int selectTaskNum = 0;

		while(true)
		{
			List<WTask> pcpPath = new ArrayList<WTask>();
			for(WTask task: workflow.getTaskList())
			{
				if(task.getPCPNum() == pcpId)
				{
					pcpPath.add(task);
					selectTaskNum++;
				}
			}

			Collections.sort(pcpPath, new Comparator<WTask>()
			{
				public int compare(WTask task1, WTask task2)
				{
					return task1.getEarliestFinishTime() - task2.getEarliestFinishTime();
				}
			});
			int F_PEST = pcpPath.get(0).getEarliestStartTime();
			int L_PLCT = pcpPath.get(pcpPath.size()-1).getLeastFinishTime();
			int L_PECT = pcpPath.get(pcpPath.size()-1).getEarliestFinishTime();

			int lWholeTime = L_PLCT - F_PEST;
			int sWholeTime = L_PECT - F_PEST;

			for(WTask task: pcpPath)
			{
				int executeTime = task.getEarliestFinishTime() - F_PEST;
				double aValue = (double)executeTime/sWholeTime;
				int subLine = (int)(aValue * lWholeTime);
				int subdeadline = F_PEST + subLine;
				int subSpan = subdeadline - task.getEarliestStartTime();

				task.setSubSpan(subSpan);
				task.setSubDeadLine(subdeadline);
				task.setWorkflowArrival(workflow.getArrivalTime());
				task.setWorkFlowDeadline(workflow.getDeadline());
			}

			pcpId++;
			if(selectTaskNum == workflow.getTaskList().size())
			{
				break;
			}
		}
	}
}

package workflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ScheduleAgorithm.RegressionSuite;
import share.StaticfinalTags;

public final class BenchmarkWorkflowGenerator
{
	private BenchmarkWorkflowGenerator()
	{
	}

	public static void main(String[] args) throws Exception
	{
		RunnerOptions options = RunnerOptions.parse(args);
		RegressionSuite suite = RegressionSuite.fromName(options.suiteName);
		if(!suite.isBenchmark())
		{
			throw new IllegalArgumentException("Benchmark workflow generator only supports benchmark suites: "
					+ suite.getSuiteName());
		}

		Path outputPath = suite.getWorkloadPath();
		if(Files.exists(outputPath) && !options.overwrite)
		{
			throw new IllegalStateException("Refusing to overwrite existing benchmark workload: "
					+ outputPath.toString() + ". Pass --overwrite to replace it.");
		}

		Path templatePath = suite.getTemplatePath();
		if(templatePath == null || !Files.exists(templatePath))
		{
			throw new IllegalStateException("Benchmark template set is missing: "
					+ (templatePath == null ? "null" : templatePath.toString())
					+ ". Generate it with ./scripts/generate_benchmark_templates.sh "
					+ resolveTemplateScriptArgument(suite));
		}

		List<TempWorkflow> tempWorkflows = TempWorkflowDatasetIO.readWorkflows(templatePath);
		if(tempWorkflows.isEmpty())
		{
			throw new IllegalStateException("Benchmark template set is empty: " + templatePath.toString());
		}

		List<Workflow> workflows = produceWorkflow(tempWorkflows, suite.getExpectedWorkflowCount());
		WorkflowProducer.calculateRealTaskBaseExecutionTime(workflows);
		WorkflowDatasetIO.writeWorkflows(outputPath, workflows);
		System.out.println("Benchmark workload generated: " + outputPath.toString());
	}

	private static List<Workflow> produceWorkflow(List<TempWorkflow> templateWorkflows, int workflowNum)
	{
		List<Workflow> templateInstances = materializeTemplateWorkflows(templateWorkflows);
		List<Workflow> workflows = new ArrayList<Workflow>();

		int workflowId = 0;
		int arrivalTime = 0;
		int rotationId = 0;

		while(workflows.size() < workflowNum)
		{
			int arrivingWorkflowCount = WorkflowProducer.PoissValue(StaticfinalTags.arrivalLamda);
			if(arrivingWorkflowCount == 0)
			{
				arrivalTime++;
				continue;
			}

			for(int index = 0; index < arrivingWorkflowCount && workflows.size() < workflowNum; index++)
			{
				int templateIndex = selectTemplateIndex(templateInstances.size(), rotationId);
				Workflow templateWorkflow = templateInstances.get(templateIndex);
				rotationId++;

				List<WTask> copiedTaskList = cloneWorkflowTasks(templateWorkflow, workflowId);
				bindTaskDependencies(copiedTaskList);

				int deadline = (int)(arrivalTime + templateWorkflow.getMakespan() * StaticfinalTags.deadlineBase);
				Workflow workflow = new Workflow(workflowId,
						templateWorkflow.getWorkflowName(),
						arrivalTime,
						templateWorkflow.getMakespan(),
						deadline);
				workflow.setTaskList(copiedTaskList);
				workflows.add(workflow);
				workflowId++;
			}

			arrivalTime++;
		}

		return workflows;
	}

	private static List<Workflow> materializeTemplateWorkflows(List<TempWorkflow> tempWorkflows)
	{
		List<Workflow> templateInstances = new ArrayList<Workflow>();
		for(TempWorkflow tempWorkflow: tempWorkflows)
		{
			List<WTask> taskList = new ArrayList<WTask>();
			for(TempWTask tempTask: tempWorkflow.getTaskList())
			{
				int baseExecutionTime = (int)tempTask.getTaskRunTime();
				if(baseExecutionTime < 1)
				{
					baseExecutionTime = 1;
				}

				WTask task = new WTask(tempTask.getTaskId(), -1, baseExecutionTime);
				task.getParentIDList().addAll(copyConstraintList(tempTask.getParentTaskList()));
				task.getSuccessorIDList().addAll(copyConstraintList(tempTask.getSuccessorTaskList()));
				taskList.add(task);
			}

			Workflow workflow = new Workflow(-1, tempWorkflow.getWorkflowName(), -1, -1, -1);
			workflow.setTaskList(taskList);
			workflow.setMakespan(WorkflowProducer.CalculateMakespan(workflow));
			templateInstances.add(workflow);
		}
		return templateInstances;
	}

	private static List<Constraint> copyConstraintList(List<Constraint> constraints)
	{
		List<Constraint> copiedConstraints = new ArrayList<Constraint>(constraints.size());
		for(Constraint constraint: constraints)
		{
			copiedConstraints.add(new Constraint(constraint.getTaskId(), constraint.getDataSize()));
		}
		return copiedConstraints;
	}

	private static List<WTask> cloneWorkflowTasks(Workflow templateWorkflow, int workflowId)
	{
		List<WTask> copiedTasks = new ArrayList<WTask>();
		for(WTask task: templateWorkflow.getTaskList())
		{
			WTask copiedTask = new WTask(task.getTaskId(), workflowId, task.getBaseExecutionTime());
			copiedTask.setBaseStartTime(task.getBaseStartTime());
			copiedTask.setBaseFinishTime(task.getBaseFinishTime());
			copiedTask.getParentIDList().addAll(copyConstraintList(task.getParentIDList()));
			copiedTask.getSuccessorIDList().addAll(copyConstraintList(task.getSuccessorIDList()));
			copiedTasks.add(copiedTask);
		}
		return copiedTasks;
	}

	private static void bindTaskDependencies(List<WTask> tasks)
	{
		for(WTask connectedTask: tasks)
		{
			for(Constraint parentConstraint: connectedTask.getParentIDList())
			{
				for(WTask parentTask: tasks)
				{
					if(parentConstraint.getTaskId().equals(parentTask.getTaskId()))
					{
						connectedTask.getParentTaskList().add(
								new ConstraintWTask(parentTask, parentConstraint.getDataSize()));
						break;
					}
				}
			}

			for(Constraint successorConstraint: connectedTask.getSuccessorIDList())
			{
				for(WTask successorTask: tasks)
				{
					if(successorConstraint.getTaskId().equals(successorTask.getTaskId()))
					{
						connectedTask.getSuccessorTaskList().add(
								new ConstraintWTask(successorTask, successorConstraint.getDataSize()));
						break;
					}
				}
			}
		}
	}

	private static int selectTemplateIndex(int templateCount, int rotationId)
	{
		StaticfinalTags.workflowSelectOption selectionStyle = StaticfinalTags.OperationStyle;
		switch(selectionStyle)
		{
			case Special:
				if(StaticfinalTags.selectedNum < 0 || StaticfinalTags.selectedNum >= templateCount)
				{
					throw new IllegalArgumentException("selectedNum is out of range for benchmark template set: "
							+ StaticfinalTags.selectedNum + " / " + templateCount);
				}
				return StaticfinalTags.selectedNum;
			case Random:
				return (int)(Math.random() * templateCount);
			default:
				return rotationId % templateCount;
		}
	}

	private static String resolveTemplateScriptArgument(RegressionSuite suite)
	{
		return suite.getBenchmarkFamilies().size() == 1
				? suite.getBenchmarkFamilies().get(0).getFamilyName()
				: "mixed";
	}

	private static final class RunnerOptions
	{
		private final String suiteName;
		private final boolean overwrite;

		private RunnerOptions(String suiteName, boolean overwrite)
		{
			this.suiteName = suiteName;
			this.overwrite = overwrite;
		}

		private static RunnerOptions parse(String[] args)
		{
			String suiteName = null;
			boolean overwrite = false;

			for(int index = 0; index < args.length; index++)
			{
				String arg = args[index];
				if("--suite".equals(arg))
				{
					index++;
					suiteName = args[index];
				}
				else if("--overwrite".equals(arg))
				{
					overwrite = true;
				}
				else
				{
					throw new IllegalArgumentException("Unknown argument: " + arg);
				}
			}

			if(suiteName == null)
			{
				throw new IllegalArgumentException("Missing required argument: --suite");
			}

			return new RunnerOptions(suiteName, overwrite);
		}
	}
}

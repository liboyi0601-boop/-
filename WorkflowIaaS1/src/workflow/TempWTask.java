package workflow;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ljg 修改   2017.2.10
 * 
 * TempWTask类存放从工作流模板文件中读出来的原始任务信息
 * */

public class TempWTask implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	private final String taskId;  //任务的ID
	private int taskWorkFlowId;   //任务所在工作流的ID
	private final double runTime; //任务的执行时间 
	private final double utilization; //任务的资源利用率
	
	private List<Constraint> parentTaskList;   //父节点列表
	private List<Constraint> sucessorTaskList; //子节点列表
	
	/**
	 * @param taskId: 任务的id
	 * @param workflowId: 所在工作流的id
	 * @param runTime: 任务的执行时间
	 * @param uti: 任务的资源利用率
	 */
	public TempWTask(String taskId, int workflowId, double runTime, double uti)
	{	
		this.taskId = taskId;
		this.taskWorkFlowId = workflowId;
		this.runTime = runTime;	
		this.utilization = uti;
		parentTaskList = new ArrayList<Constraint>();   //初始化父节点列表
		sucessorTaskList = new ArrayList<Constraint>(); //初始化子节点列表
	}
	
	/**任务的id*/
	public String getTaskId(){return taskId;}
	
	/**任务所在工作流的Id*/
	public int getTaskWorkFlowId(){return taskWorkFlowId;}
	public void setTaskWorkFlowId(int workFlowId)
	{
		this.taskWorkFlowId = workFlowId;
	}
	
	/**获取任务的执行时间*/
	public double getTaskRunTime(){return runTime;}
	/**获取任务的资源利用率*/
	public double getTaskUtilization(){return utilization;}
	
	/**获取该任务的父任务列表*/
	public List<Constraint> getParentTaskList(){return parentTaskList;}
	
	/**获取该任务的子任务列表*/
	public List<Constraint> getSuccessorTaskList(){return sucessorTaskList;}
}
package workflow;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ljg修改补充	2017.2.12
 * Workflow类描述实验算法要处理工作流
 * */
public class Workflow implements Serializable, Cloneable
{
	private static final long serialVersionUID = 1L;
	
	private int workflowId;
	private final String workflowName;
	private int arrivalTime;
	private int makespan; //柳佳刚——记录工作流最快的完成时间，根据最初的基本执行时间计算
	private int deadline; //柳佳刚——deadline=arrivalTime+makespan*deadlineBase
	
	private List<WTask> taskList; //节点列表
	private boolean startedFlag;  //工作流是否已经开始放置
	private int finishTime;       //工作流的完成时间
	private boolean successfulOrNot; //工作流是否被成功执行
	
	/**
	 * @param wordFlowId: 工作流ID
	 * @param name: 工作流名称
	 * @param arrivalTime: 到达时间
	 * @param makespan: 工作流的最快makespan
	 * @param deadline: deadline
	 * */
	public Workflow(int workFlowId, String name, int arrivalTime, int makespan, int deadline)
	{
		this.workflowId = workFlowId;
		this.workflowName = name;
		this.arrivalTime = arrivalTime;
		this.makespan = makespan;
		this.deadline = deadline;
		
		this.taskList = new ArrayList<WTask>(); //初始化任务列表
		this.startedFlag = false;
		this.finishTime = -1;
		this.successfulOrNot = false;
	}
		
	/**获取工作流的ID*/
	public int getWorkflowId(){return workflowId;}
	/**设置工作流的ID*/
	public void setWorkflowId(int workflowId)
	{
		this.workflowId = workflowId;
	}
	
	/**获取工作流的名字*/
	public String getWorkflowName(){return workflowName;}
	
	/**获取工作流的到达时间*/
	public int getArrivalTime(){return arrivalTime;}
	/**设置工作流的到达时间*/
	public void setArrivalTime(int arrivalTime)
	{
		this.arrivalTime = arrivalTime;
	}
	
	/**获取工作流的跨期*/
	public int getMakespan(){return makespan;}
	/**设置工作流的跨期*/
	public void setMakespan(int makespan)
	{
		this.makespan = makespan;
	}
	
	/**获取工作流的截止期*/
	public int getDeadline(){return deadline;}
	/**设置工作流的截止期*/
	public void setDeadline(int deadline)
	{
		this.deadline = deadline;
	}
	
	/**获取该工作流的任务集合*/
	public List<WTask> getTaskList(){return taskList;}
	/**设置该工作流的任务集合*/
	public void setTaskList(List<WTask> list)
	{
		this.taskList = list;
	}
	
	/**获取工作流已经开始放置的标志*/
	public boolean getStartedFlag(){return startedFlag;}
	/**设置工作流已经开始放置的标志*/
	public void setStartedFlag(boolean startedFlag)
	{
		this.startedFlag = startedFlag;
	}
	
	/**获取工作流的完成时间*/
	public int getFinishTime(){return finishTime;}
	/**设置工作流的完成时间*/
	public void setFinishTime(int finishTime)
	{
		this.finishTime = finishTime;
	}
	
	/**获取工作流是否在截止期内完成*/
	public boolean getSuccessfulOrNot(){return successfulOrNot;}
	/**设置工作流是否在截止期内完成*/
	public void setSuccessfulOrNot(boolean successfulOrNot)
	{
		this.successfulOrNot = successfulOrNot;
	}
}
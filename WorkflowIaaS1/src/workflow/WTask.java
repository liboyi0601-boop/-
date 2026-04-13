package workflow;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import vmInfo.SaaSVm;

/** 
 * @author ljg修改补充	2017.2.12
 * WTask类描述实验算法中要处理的工作流任务
 * */

public class WTask implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	private final String taskId;
	private int taskWorkFlowId;  /*所属工作流的ID*/
	private int WorkFlowArrival; /*所属工作流的到达时间*/
	private int WorkFlowDeadline;
	
	private final int baseExecutionTime;   /*基准执行时间，等于从公开数据集中读出的任务执行时间（时间段）*/
	private int baseStartTime;   /*用于计算任务的makespan,无父任务时为0，有父任务时为最大父任务基准完成时间*/
	private int baseFinishTime;   /*任务结束的基准时间,用于计算makespan，等于baseStartTime+baseExecutionTime的值*/
	
	private int realBaseExecutionTime; /*考虑随机因素后的基准执行时间,由正态分布产生的执行时长基本执行时长（时间段）
										*使用基本执行时长和标准差来生成正态分布的真实基准执行时长，此时和VM因子无关*/
	
	private int realExecutionTime; /*任务真正的执行时间,在真实基准执行时间上考虑了VM因子后的执行时间(时间段)*/
	
	private int executionTimeWithConfidency; /*一定可信度下任务的基准执行时间（时间段）
											   OSDS算法中=任务的平均执行时间+标准差
											   PRS算法中=任务带分位点的近似执行时长，未分VM时=基准执行时间+标准差*分位点，已分VM后=带上VM因子*(基准执行时间+标准差*分位点)*/
		
	private int realStartTime;   /*任务真正的开始时间,即系统记录的执行过程中每步的当前时间*/
	private int startTimeWithConfidency;   /*一定可信度下任务的开始时间，在特定的VM上任务的近似估计开始时间*/
	private int earliestStartTime;
	private int leastStartTime;   /*预计最迟开始时间，由leastFinishTime和executionTimeWithConfidency计算得到*/
	
	private int realFinishTime;   /*任务真正的结束时间,在真实基准执行时间上考虑了VM因子后的任务实际结束时间*/
	private int finishTimeWithConfidency;  /*一定可信度下任务的结束时间，在特定的VM上任务的近似估计完成时间*/
	private int earliestFinishTime;
	private int leastFinishTime;  /*预计最迟完成时间，由deadline和executionTimeWithConfidency计算得到*/
	
	private int subDeadline; /*任务的subdeadline*/
	private int subSpan; /*subdeadline与自己PEST之间的间隔时间*/
	
	private boolean PathLastFlag; /*是否是PCP路径最后结点*/
	private boolean PathFirstFlag; /*是否是PCP路径首结点*/
	private WTask PathFirstTask; /*记录所在PCP的首结点*/
	private WTask PathLastTask;  /*记录所在PCP的末结点*/
				
	private boolean allocatedFlag; //该任务是否配置到虚拟机
	private SaaSVm allocateVm; //该任务放置的具体虚拟机
	private boolean finishFlag; //该任务是否已经完成
	private boolean newStartVm; //该任务是否分到一个新启动的VM
			
	private int priority;//记录任务的排序权重
	
	private int PCPNum;//记录不同的PCP编号，每条PCP上的节点编号都相同
			
	private List<ConstraintWTask> parentTaskList;   //父节点列表
	private List<ConstraintWTask> sucessorTaskList; //子节点列表
	
	private List<Constraint> parentIDList;    //父节点ID列表
	private List<Constraint> successorIDList; //子节点ID列表
	
	/**
	 * @param taskId: 任务ID
	 * @param workflowID: 工作流ID
	 * @param executionTime: 任务的执行时间
	 * */	
	public WTask(String taskId, int workflowId, int executionTime)
	{	
		this.taskId = taskId;
		this.taskWorkFlowId = workflowId;
		
		this.baseExecutionTime = executionTime; //用于计算makespan
		this.baseStartTime = -1;
		this.baseFinishTime = -1;
		
		this.realBaseExecutionTime = 0;
		
		this.realExecutionTime = 0;
		this.executionTimeWithConfidency = 0;
		
		this.realStartTime = -1;
		this.startTimeWithConfidency = -1;
		this.earliestStartTime = -1;		
		this.leastStartTime = -1;
		
		this.realFinishTime = -1;
		this.finishTimeWithConfidency = -1;
		this.earliestFinishTime = -1;
		this.leastFinishTime = -1;
								
		this.allocatedFlag = false;
		this.allocateVm = null;
		this.finishFlag = false;
		this.newStartVm = false;
										
		this.priority = -1;			
		this.PCPNum = -1; //用来标记节点是否已经进入关键路径list，用序号来表示顺序
		this.subDeadline = -1;
		this.subSpan = 0;

		this.PathFirstFlag = false;
		this.PathLastFlag = false;
		
		this.PathFirstTask = null;
		this.PathLastTask = null;
								
		parentTaskList = new ArrayList<ConstraintWTask>(); //初始化父节点列表
		sucessorTaskList = new ArrayList<ConstraintWTask>(); //初始化子节点列表
		
		parentIDList = new ArrayList<Constraint>();    //初始化父节点ID列表
		successorIDList = new ArrayList<Constraint>(); //初始化子节点ID列表	
	}
	
	public WTask()
	{
		this.taskId = "initial";
		this.baseExecutionTime = -1;
		this.baseFinishTime = -1;
		this.realFinishTime = -1;
	}
	
	/**获取任务的ID*/
	public String getTaskId(){return taskId;}
	
	/**获取任务所在工作流的ID*/
	public int getTaskWorkFlowId(){return taskWorkFlowId;}
	/**设置任务所在工作流的ID*/
	public void setTaskWorkFlowId(int workFlowId)
	{
		this.taskWorkFlowId = workFlowId;
	}
	
	/**获取任务的基准执行时间*/
	public int getBaseExecutionTime(){return baseExecutionTime;}
	
	/**获取任务开始的基准时间，用于计算makespan*/
	public int getBaseStartTime(){return baseStartTime;}
	/**设置任务开始的基准时间，用于计算makespan*/
	public void setBaseStartTime(int startTime)
	{
		this.baseStartTime = startTime;
	}
	
	/**获取任务结束的基准时间，用于计算makespan*/
	public int getBaseFinishTime(){return baseFinishTime;}
	/**设置任务结束的基准时间，用于计算makespan*/
	public void setBaseFinishTime(int bFinishTime)
	{
		this.baseFinishTime = bFinishTime;
	}
	
	/**获取任务真正的基准执行时间*/
	public int getRealBaseExecutionTime(){return realBaseExecutionTime;}
	/**设置任务真正的基准执行时间*/
	public void setRealBaseExecutionTime(int realBaseTime)
	{
		this.realBaseExecutionTime = realBaseTime;
	}
	
	/**获取任务的真正执行时间*/
	public int getRealExecutionTime(){return realExecutionTime;}
	/**设置任务的真正执行时间*/
	public void setRealExecutionTime(int realTime)
	{
		this.realExecutionTime = realTime;
	}
	
	/**获取在一定可信度下任务的执行时间*/
	public int getExecutionTimeWithConfidency(){return executionTimeWithConfidency;}
	/**设置在一定可信度下任务的执行时间*/
	public void setExecutionTimeWithConfidency(int eTimeWithConfidency)
	{   //一定置信度下的执行时间=基准执行时间+标准差*分位点
		this.executionTimeWithConfidency = eTimeWithConfidency; 
	}
	
	/**获取任务真正的开始时间*/
	public int getRealStartTime(){return realStartTime;}
	/**设置任务真正的开始时间*/
	public void setRealStartTime(int realStartTime)
	{
		this.realStartTime = realStartTime;
	}
	
	/**获取在一定可信度下，任务的开始时间*/
	public int getStartTimeWithConfidency(){return startTimeWithConfidency;}
	/**设置在一定可信度下，任务的开始时间*/
	public void setStartTimeWithConfidency(int startTime)
	{
		this.startTimeWithConfidency = startTime;
	}
	
	/**获取任务的最早开始时间*/
	public int getEarliestStartTime(){return earliestStartTime;}
	/**设置任务的最早开始时间*/
	public void setEarliestStartTime(int earliestStartTime)
	{
		this.earliestStartTime = earliestStartTime;
	}
	
	/**获取任务的最晚开始时间*/
	public int getLeastStartTime(){return leastStartTime;}
	/**设置任务的最晚开始时间*/
	public void setLeastStartTime(int leastStartTime)
	{
		this.leastStartTime = leastStartTime;
	}
	
	/**获取任务的真正完成时间*/
	public int getRealFinishTime(){return realFinishTime;}
	/**设置任务的真正完成时间*/
	public void setRealFinishTime(int realFinishTime)
	{
		this.realFinishTime = realFinishTime;
	}
		
	/**获取在一定可信度下，任务的结束时间*/
	public int getFinishTimeWithConfidency(){return finishTimeWithConfidency;}
	/**设置在一定可信度下，任务的结束时间*/
	public void setFinishTimeWithConfidency(int finishTime)
	{
		this.finishTimeWithConfidency = finishTime;
	}
	
	/**获取任务的最早完成时间*/
	public int getEarliestFinishTime(){return earliestFinishTime;}
	/**设置任务的最早完成时间*/
	public void setEarliestFinishTime(int earliestFinishTime)
	{
		this.earliestFinishTime = earliestFinishTime;
	}
	
	/**获取任务的最晚完成时间*/
	public int getLeastFinishTime(){return leastFinishTime;}
	/**设置任务的最晚完成时间*/
	public void setLeastFinishTime(int leastFinishTime)
	{
		this.leastFinishTime = leastFinishTime;
	}
			
	/**获取任务是否已经被放置的标志*/
	public boolean getAllocatedFlag(){return allocatedFlag;}
	/**设置任务是否已经被放置的标志*/
	public void setAllocatedFlag(boolean allocatedFlag)
	{
		this.allocatedFlag = allocatedFlag;
	}
	
	/**获取任务放置的虚拟机*/
	public SaaSVm getAllocateVm(){return allocateVm;}
	/**设置任务放置的虚拟机*/
	public void setAllocateVm(SaaSVm vm)
	{
		this.allocateVm = vm;
	}
	
	/**获取任务是否已经被完成的标志*/
	public boolean getFinishFlag(){return finishFlag;}
	/**设置任务是否已经被完成的标志*/
	public void setFinishFlag(boolean flag)
	{
		this.finishFlag = flag;
	}
	
	/**获取任务是否被分配到新启动的VM的标志*/
	public boolean getNewStartVm(){return newStartVm;}
	/**设置任务被分配到新启动的VM的标志*/
	public void setNewStartVm(boolean newVmFlag)
	{
		this.newStartVm = newVmFlag;
	}
			
	/**获取任务的权重*/
	public int getPriority(){return priority;}
	/**设置任务的权重*/
	public void setPriority(int priority)
	{
		this.priority = priority;
	}
	
	/**获取任务的PCPNum*/
	public int getPCPNum(){return PCPNum;}
	/**设置任务的权重*/
	public void setPCPNum(int num)
	{
		this.PCPNum = num;
	}
	
	/**获取任务的subDeadLIne*/
	public int getSubDeadLine() {return subDeadline;}
	/**设置任务的subDeadLIne*/
	public void setSubDeadLine(int subDeadline)
	{
		this.subDeadline = subDeadline;
	}
	
	/**设置PCP末节点标志*/
	public boolean getPathLastFlag(){return PathLastFlag;}
	/**获得PCP末节点标志*/
	public void setPathLastFlag(boolean LastFlag)
	{
		this.PathLastFlag = LastFlag;
	}
	
	/**设置PCP首节点标志*/
	public boolean getPathFirstFlag(){return PathFirstFlag;}
	/**获得PCP首节点标志*/
	public void setPathFirstFlag(boolean FirstFlag)
	{
		this.PathFirstFlag = FirstFlag; 
	}
	
	/**获得PCP首节点*/
	public WTask getPathFirstTask(){return PathFirstTask;}
	/**设置PCP首节点*/
	public void setPathFirstTask(WTask firstTask)
	{
		this.PathFirstTask = firstTask;
	}
	
	/**获得PCP末节点*/
	public WTask getPathLastTask(){return PathLastTask;}
	/**设置PCP末节点*/
	public void setPathLastTask(WTask lastTask)
	{
		this.PathLastTask = lastTask;
	}
	
	/**获得节点到subdeadline的间隔*/
	public int getSubSpan(){return subSpan;}
	/**设置节点到subdeadline的间隔*/
	public void setSubSpan(int Span)
	{
		this.subSpan = Span;
	}
	
	/**设置任务对应工作流的ArrivalTime*/
	public void setWorkflowArrival(int Arrival)
	{
		this.WorkFlowArrival = Arrival;
	}
	/**获取任务对应工作流的ArrivalTime*/
	public int getWorkFlowArrival() {return WorkFlowArrival;}
	
	/**设置任务对应工作流的deadline*/
	public int getWorkFlowDeadline(){return WorkFlowDeadline;}
	/**获得任务对应工作流的deadline*/
	public void setWorkFlowDeadline(int deadline)
	{
		this.WorkFlowDeadline = deadline;
	}
	
	/**获取该任务的父任务列表*/
	public List<ConstraintWTask> getParentTaskList(){return parentTaskList;}
	
	/**获取该任务的子任务列表*/
	public List<ConstraintWTask> getSuccessorTaskList(){return sucessorTaskList;}
	
	/**获取该任务的父任务ID的列表*/
	public List<Constraint> getParentIDList(){return parentIDList; }
	
	/**获取该任务的子任务ID的列表*/
	public List<Constraint> getSuccessorIDList(){return successorIDList; }	
}
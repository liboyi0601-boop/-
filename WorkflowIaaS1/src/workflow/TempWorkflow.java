package workflow;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ljg修改补充	2017.2.12 
 * TempWorkflow类用来描述从workflow模板文件中读出来的工作流对象 
 * */

public class TempWorkflow implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	private final String workflowName; //工作流的名称	
	private List<TempWTask> taskList; //节点列表
	
	/**
	 * @param name: 工作流的名称
	 */
	public TempWorkflow(String name)
	{
		this.workflowName = name;		
		this.taskList = new ArrayList<TempWTask>(); //初始化任务列表
	}
	
	/**获取工作流的名字*/
	public String getWorkflowName(){return workflowName;}
	
	/**获取该工作流的任务集合*/
	public List<TempWTask> getTaskList(){return taskList;}
	public void setTaskList(List<TempWTask> list)
	{
		this.taskList = list;
	}
}
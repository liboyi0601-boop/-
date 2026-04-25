package workflow;

import java.io.Serializable;
/**
 * @author ljg 修改补充	2017.2.12
 * ConstraintWtask类表示：直接使用任务关联的约束类，描述约束相关的任务类和传输的数据量
 * */
public class ConstraintWTask implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	private final WTask wTask; //连接任务
	private final int dataSize; //数据传输量
	
	/**直接使用任务实例的前后约束关系
	 * @param task: 任务实例
	 * @param dataSize: 数据传输量
	 * */
	public ConstraintWTask(WTask task, int dataSize)
	{
		this.wTask = task;
		this.dataSize = dataSize;
	}
	
	public WTask getWTask(){return wTask;}	
	public int getDataSize(){return dataSize;}
}
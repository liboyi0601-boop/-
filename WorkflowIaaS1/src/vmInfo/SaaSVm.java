package vmInfo;

import java.util.ArrayList;
import java.util.List;
import workflow.WTask;
import share.StaticfinalTags;

public class SaaSVm 
{
	private final int vmID; //虚拟机的ID
	private final String vmType; //虚拟机的类型
	
	private final double vmPrice; //虚拟机的价格
	private double totalCost; //虚拟机生命周期内的费用
	
	private final int startWorkTime; //虚拟机开始工作的时间，VM开始启动初始化后开始工作的时间（时间点）
	private int endWorkTime; //虚拟机结束工作的时间，VM完成一个整的slot后关机的时间（时间点）
	private int realWorkTime; //虚拟机实际工作的时间，VM上实际有任务执行的时间（时间段）
	private int idleTime; //虚拟机空闲的时间，VM启动后没有任务执行的闲置时间（时间段）
		
	private final double executionTimeFactor; //任务执行时间的影响因子
	private int finishTime; //VM完成其上任务的近似估计时间
	                        //该任务可以是正在执行的，也可以是排在上面等待的
	private int realFinishTime; //VM完成其上任务真正的完成时间，由真实开始时间+正态分布的真实基准执行时长*VM因子得到
								//该任务可以是正在执行的，也可以是排在上面等待的
	private int readyTime; //虚拟机就绪时间，即可以用来分配任务的时间
	                       //由该VM上任务近似完成时间来表示准备时间，即准备下次开始计算的时间
	
	private boolean status; //虚拟机状态
		
	private List<WTask> WTaskList; //虚拟机完成的所有任务	
	private List<WTask> waitWTaskList; //等待任务列表	
	private WTask waitingWTask; //正在等待的任务     
	private WTask executingWTask; //正在执行任务
	
	public SaaSVm(int id, String type, int startTime, double price, double factor)
	{
		this.vmID = id;
		this.vmType = type;
		this.vmPrice = price;
		this.executionTimeFactor = factor;
		
		this.totalCost = 0;
		this.startWorkTime = startTime + StaticfinalTags.createVmTime; //刚启动虚拟机时，虚拟机的开始工作时间是开始时间+初始化时间
		this.endWorkTime = startTime + StaticfinalTags.VmSlot; //时间以秒为单位
		this.realWorkTime = 0;
		this.idleTime = 0;
				
		this.finishTime = -1;
		this.realFinishTime = -1;
		this.readyTime = startTime + StaticfinalTags.createVmTime; //刚启动虚拟机时，虚拟机的就绪时间是开始工作时间+初始化时间
		
		this.status = true;
		this.WTaskList = new ArrayList<WTask>(); //实例化一个空列表
		this.waitWTaskList = new ArrayList<WTask>(); //实例化一个空列表
		this.waitingWTask = new WTask(); //实例化一个空任务
		this.executingWTask = new WTask();	//实例化一个空任务			
	}
	
	/**获取虚拟机的ID*/
	public int getVmID(){return vmID;}
	
	/**获取虚拟机的类型*/
	public String getVmType(){return vmType;}
	
	/**获取虚拟机的价格*/
	public double getVmPrice(){return vmPrice;}		
	
	/**获取虚拟机对任务执行时间的影响因素*/
	public double getVmFactor(){return executionTimeFactor;}
	
	/**获取虚拟机开始工作的时间*/
	public int getVmStartWorkTime(){return startWorkTime;}
	
	/**获取虚拟机的总费用*/
	public double getTotalCost(){return totalCost;}
	/**更新虚拟机的总费用*/
	public void setTotalCost(double add)
	{
		this.totalCost = add;
	}
		
	/**获取虚拟机的结束时间*/
	public int getEndWorkTime(){return endWorkTime;}
	/**设置虚拟机的结束时间*/
	public void setEndWorkTime(int endTime)
	{
		this.endWorkTime = endTime;
	}
	
	/**获取虚拟机真正的工作时间*/
	public int getRealWorkTime(){return realWorkTime;}
	
	/**设置虚拟机真正的工作时间*/
	public void updateRealWorkTime(int workTime)
	{
		this.realWorkTime += workTime;
	}
	
	/**获取虚拟机的空闲时间*/
	public int getIdleTime(){return idleTime;}
	
	/**更新虚拟机的空闲时间*/
	public void updateIdleTime(int idleTime)
	{
		this.idleTime += idleTime;
	}
	
	/**获取虚拟机完成任务的期望时间*/
	public int getFinishTime(){return finishTime;}
	/**设置虚拟机完成任务的期望时间*/
	
	public void setFinishTime(int finishTime)
	{
		this.finishTime = finishTime;
	}
	
	/**获取虚拟机完成任务的真正时间*/
	public int getRealFinishTime(){return realFinishTime;}
	
	/**设置虚拟机完成任务的真正时间*/
	public void setRealFinishTime(int rFinishTime)
	{
		this.realFinishTime = rFinishTime;
	}
	
	/**获取虚拟机的就绪时间*/
	public int getReadyTime(){return readyTime;}
	
	/**设置虚拟机的就绪时间*/
	public void setReadyTime(int readyTime)
	{
		this.readyTime = readyTime;
	}
	
	/**获取虚拟机的状态*/
	public boolean getVmStatus(){return status;}
	
	/**设置虚拟机的状态*/
	public void setVmStatus(boolean status)
	{
		this.status = status;
	}
	
	/**获取虚拟机所有完成任务的列表*/
	public List<WTask> getWTaskList(){return WTaskList;}
	
	/**获取虚拟机上正在等待的任务列表*/
	public List<WTask> getWaitWTaskList(){return waitWTaskList;}
	
	/**获取虚拟机上正在等待的任务*/
	public WTask getWaitingWTask(){return waitingWTask;}
	
	/**设置虚拟机上正在等待的任务*/
	public void setWaitingWTask(WTask task)
	{
		this.waitingWTask = task;
	}
	
	/**获取虚拟机上正在执行的任务*/
	public WTask getExecutingWTask(){return executingWTask;}
	
	/**设置虚拟机上正在执行的任务*/
	public void setExecutingWTask(WTask task)
	{
		this.executingWTask = task;
	}
		
}

package share;

/**
 * 统一实验参数值
 * @author ljg 	2017.4
 */
public final class StaticfinalTags 
{
	/**算法的选择*/
	public static int choose = 0;
	
	/**工作流的数量*/	
	public static int workflowNum = 100;
	
	/**工作流模板数*/
	public static int workflowTemplateNum = 15;
	
	/**工作流的到达率*/
	public static double arrivalLamda = 0.01;
	
	/**工作流截止期的基准*/
	public static double deadlineBase = 2.0;
	
	/**系统的网络带宽100MBps 13107200*/
	public static int bandwidth = 13107200;
	
	/**随机执行时间偏差系数*/
	public static double standardDeviation = 0.2;
	
	/**随机执行时间标准差变化因子*/
	public static double VarDeviation = 1.0;
	
	/**当前时间, 如果为负数，则无效*/
	public static int currentTime = -1;
	
	/**创建虚拟机的时间开销*/
	public static int createVmTime = 97;
	
	/**虚拟机的时间槽分割比*/
	public static double PartVmSlot = 12.0/12;
	
	/**虚拟机计费时间槽的长度*/
	public static int VmSlot = 3600;
	
	/**指定工作流的模板编号: 
	 * 当使用20个模板时：			|	当使用15个模板时：	    |   当使用5个模板时
	 * CyberShake: 0,1,2,3		|	CyberShake: 0,1,2	|	CyberShake: 0
	 * Epigenomics: 4,5,6,7		|	Epigenomics: 3,4,5	|	Epigenomics: 1
	 * Inspiral: 8,9,10,11		|	Inspiral: 6,7,8		|	Inspiral: 2
	 * Montage: 12,13,14,15		|	Montage: 9,10,11	|	Montage: 3
	 * Sipht: 16,17,18,19    	|	Sipht: 12,13,14		|	Sipht: 4
	 * 只有当OperationStyle为Special时编号才有效*/
	public static int selectedNum = 10; //选择工作流编号
	
	/**指定制作工作流的方式：Special, Random, Rotation*/
	public static workflowSelectOption OperationStyle = workflowSelectOption.Random;
	
	/**ROSA中任务执行时间的可信度*/
	public static double ROSAConfidency = 0.85;
	
	public enum workflowSelectOption
	{
		Special, /**指定一个特定的工作流类型，需要与selectedNum参数对应*/ 
		Random,  /**从模板中随机选择一个工作流类型*/
		Rotation; /**从模板中循环选择一个工作流类型*/
	}
}

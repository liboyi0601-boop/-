package share;

public class PerformanceValue 
{
	/**完成工作流的成本*/
	public static double TotalCost;
	/**虚拟机的资源利用率*/
	public static double ResourceUtilization;
	/**任务的波动性*/
	public static double taskDeviation;
	/**工作流的波动性*/
	public static double workflowDeviation;
	/**超出deadline数量的比率*/
	public static double ViolationCount;
	/**超出deadline的时间比率*/
	public static double ViolationTime;
	/**算法执行的时间*/
	public static long ScheduleTime;
	/**租用的VM数*/
	public static int totalVmCount;
}
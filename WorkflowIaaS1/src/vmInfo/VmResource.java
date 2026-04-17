package vmInfo;

import java.util.ArrayList;
import java.util.List;
import java.lang.*;
import share.StaticfinalTags;

import workflow.WTask;

/**
 * @author ljg
 * VmResource类用来描述数据中心的虚拟机池
 * */

public  class VmResource {
	
	private List<SaaSVm> VmList; //数据中心虚拟机列表
	private List<SaaSVm> ActiveVmList; //活动的虚拟机列表
	private List<SaaSVm> OffVmList; //关闭的虚拟机列表
	
	/**
	 * @param num: 初始化虚拟机的数目
	 * */
	public VmResource(int num)
	{
		//System.out.println("初始化虚拟机的类型数!："+num);
		this.VmList = new ArrayList<SaaSVm>();
		this.ActiveVmList = new ArrayList<SaaSVm>();
		this.OffVmList = new ArrayList<SaaSVm>();
		
		for(int i=0; i<num; i++)
		{
			int level = i% num;
			SaaSVm tempVm;
			//System.out.println("VM类型的ID:"+i);
			//System.out.println("VM的level:"+level);
			tempVm = scaleUpVm(i, 0, level);
			this.VmList.add(tempVm);
			this.ActiveVmList.add(tempVm);
			
		}
	}
	
	public VmResource( )
	{
		this.VmList = new ArrayList<SaaSVm>();
		this.ActiveVmList = new ArrayList<SaaSVm>();
		this.OffVmList = new ArrayList<SaaSVm>();
	}
	
	
	/**
	 * 根据虚拟机的类型产生虚拟机
	 * @param vmId: VM的ID
	 * @param startTime: VM的开始时间
	 * @param level：对应不同类型的虚拟机，如0: Extra-Large; 1: High-CPU; 2: High-Memory; 3: Standard; 4: Micro;
	 * @return 新增的虚拟机
	 */ 
	public static SaaSVm scaleUpVm(int vmId, int startTime, int level)
	{
		SaaSVm tempVm = null;
		if(VmParameter.valueOf(level) != null)
		{
			tempVm = new SaaSVm(vmId, VmParameter.valueOf(level).getVmType(), startTime, 
					VmParameter.valueOf(level).getPrice(), VmParameter.valueOf(level).getFactor());
			
		}
		return tempVm;
	}
	
	public enum VmParameter 
    {	
		//(虚拟机类型，虚拟机价格，虚拟机性能因子)
		Type_1("m2.4xlarge", 0.98*StaticfinalTags.PartVmSlot, 1.0), // vCPU 8  Memory 68.4
		Type_2("m2.2xlarge", 0.49*StaticfinalTags.PartVmSlot, 1.2), // vCPU 4  Memory 34.2
		Type_3("m1.xlarge", 0.35*StaticfinalTags.PartVmSlot, 1.3), // vCPU 2  Memory 17.1
		Type_4("m2.xlarge", 0.245*StaticfinalTags.PartVmSlot, 1.4),  // vCPU 4  Memory 15
		Type_5("m1.large", 0.175*StaticfinalTags.PartVmSlot,  1.6), // vCPU 2  Memory 7.5
		Type_6("m1.medium", 0.087*StaticfinalTags.PartVmSlot, 1.8), // vCPU 1  Memory 3.75
		Type_7("m1.small", 0.044*StaticfinalTags.PartVmSlot, 2.0);  // vCPU 1  Memory 1.7

		
     	private String VmType;
    	private double Price;
    	private double Factor;
    	
    	/**
    	 * @param type: 虚拟机类型
    	 * @param price: 虚拟机价格
    	 * @param factor: 虚拟机性能因子
    	 * */
    	private VmParameter(String type, double price, double factor)
    	{
    		this.VmType = type;
    		this.Price = price;
    		this.Factor = factor;
    	}
    	
    	/**@Override
    	 * @param level: 0--Type_1, 1--Type_2, 2--Type_3, 3--Type_4, 4--Type_5*/
    	public static VmParameter valueOf(int level)
    	{
    		switch(level)
    		{
    			case 0: 
    				return Type_1;
    			case 1:
    				return Type_2;
    			case 2:
    				return Type_3;
    			case 3:
    				return Type_4;
    			case 4:
    				return Type_5;
    			case 5:
    				return Type_6;
    			case 6:
    				return Type_7;
    			default:
    				return null;
    		}
    	}
    	/**获得虚拟机类型名*/
    	public String getVmType(){return VmType;}
    	/**获得虚拟机价格*/
    	public double getPrice(){return Price;}
    	/**获得虚拟机因子*/
    	public double getFactor(){return Factor;}
    	
    }/*end: public enum VmParameter*/
	
	/** 获得数据中心的虚拟机列表*/
	public List<SaaSVm> getVmList()	{	return VmList;	}
	/** 获得活动的虚拟机列表*/
	public List<SaaSVm> getActiveVmList() { return ActiveVmList;}
	/** 获得关闭的虚拟机列表*/
	public List<SaaSVm> getOffVmList() { return OffVmList;}
}

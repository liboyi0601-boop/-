package share;

public class StochasticDistribution 
{
		
	public double normalRandom1(double average, double variance)
	{
		double temp = 12;
		double x = 0;
		for (int i = 0; i < temp; i++)
			x = x + (Math.random());
		x = (x - temp / 2) / (Math.sqrt(temp / 12));
		x = average + x * Math.sqrt(variance);
		return x;
	 }

	public double normalRandom2(double average, double variance) 
	 {
		 double pi = 3.1415926535;
		 double r1 = Math.random();
		 Math.random();
		 Math.random();
		 Math.random();
		 Math.random();
		 Math.random();
		 Math.random();
		 Math.random();
		 double r2 = Math.random();
		 double u = Math.sqrt((-2) * Math.log(r1)) * Math.cos(2 * pi * r2);
		 double z = average + u * Math.sqrt(variance);
		 return (z);
	 }

	public double normalRandom3(double average, double variance)
	 {
		 double f = 0;
		 double c0 = 2.515517, c1 = 0.802853, c2 = 0.010328;
		 double d1 = 1.432788, d2 = 0.189269, d3 = 0.001308;
		 double w;
		 double r = Math.random();
		 if (r <= 0.5) w = r;
		 else w = 1 - r;
		 if ((r - 0.5) > 0) f = 1;
		 else if ((r - 0.5) < 0) f = -1;
		 double y = Math.sqrt((-2) * Math.log(w));
		 double x = f * (y - (c0 + c1 * y + c2 * y * y) / (1 + d1 * y + d2 * y * y + d3 * y * y * y));
		 double z = average + x * Math.sqrt(variance);
		 return (z);
	 }

	
	/**
	 * 正态分布数值生成器
	 * @param average 均值
	 * @param variance 方差
	 * @return 
	 */
	public double NormalDistribution(double average, double variance)
	{
		double r = Math.random() * 9;
		switch ((int) r / 3) 
		{
		case 0:
			return normalRandom1(average, variance);
		case 1:
			return normalRandom2(average, variance);
		case 2:
			return normalRandom3(average, variance);
		}
		return 0.0;
	}
	
	/**
	 * 泊松分布数值的产生
	 * @param Lamda: 泊松分布函数lamda参数(单位时间到达的工作流数量)
	 * @return 指定单位时间内到达的工作流数量
	 * */
	public static int PoissValue(double Lamda)
	{//产生的数值value是符合泊松分布的，均值和方差都是Lamda
		 int value=0;
		 double b=1;
		 double c=0;
		 c=Math.exp(-Lamda); 
		 double u=0;
		 do 
		 {
			 u=Math.random();
			 b*=u;
			 if(b>=c)
				 value++;
		  }while(b>=c);
		 return value;
	}
	
}

import java.util.*;

/*
 * JOBTABLE - created to hold all job objects 
 * arrived into the system. Used to retrieve and 
 * update job information in OS class
 * 
 */
public class jobTable 
{

	public static Map<Integer, PCB> JobTable;
	public static int maxSize = 50;
	public static int JobTableSize;
    
	/*
	 * initialization of JobTable
	 * creates Map data structure, 
	 * save Job Number in key
	 * and Job Object itself in value 
	 */
	public jobTable() 
	{
		JobTable = new HashMap<Integer,PCB>();
		JobTableSize = 0;
	}
	
	/*
	 * Adding new Job in Jobtable by placing
	 * JobNumber in key, JobObject in value
	 */
	public static void addNewJob(PCB job) 
	{
		if(JobTable.size()<maxSize) {
			JobTable.put(job.getJobNum(), job);
			JobTableSize++;
		} else System.out.println("No More Space to Place Job in Table, Improve Swapping");
	}
	
	/*
	 * Remove Job from JobTable when Job finished 
	 * done by OS
	 */
	public static void removeJob(PCB job) 
	{
		JobTable.remove(job.getJobNum());
		JobTableSize--;
	}
	
	/*
	 * methods 'put', 'get', 'size' needed
	 * to use Map commands in other class
	 * Since JobTable created in OS class
	 * we have to set own getters and setters to use Map
	 */
	public static void put(int JobNum, PCB job) 
	{
		JobTable.put(JobNum, job);
	}

	public static PCB get(int JobNum) 
	{
		return JobTable.get(JobNum); 
	}
	
	public static int size() 
	{
		return JobTable.size();
	}
}

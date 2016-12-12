//import java.util.LinkedList;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Set;
import java.util.*;
// has all the jobs created in system
//array list for holding all jobs in memory
//
public class jobTable {

	public static Map<Integer, PCB> JobTable;
	public static int maxSize = 50;
	public static int JobTableSize;

	public jobTable(){
		JobTable = new HashMap<Integer,PCB>();
    JobTableSize = 0;
	}

	public static void addNewJob(PCB job){
		if(JobTable.size()<50) {
		JobTable.put(job.getJobNum(), job);
		JobTableSize++;
		} else System.out.println("No More Space to Place Job in Table, Improve Swapping Maybe");
	}

	public static void removeJob(PCB job){
		JobTable.remove(job.getJobNum());
		JobTableSize--;
	}

	public static void put(int JobNum, PCB job){
		JobTable.put(JobNum, job);
	}

	public static PCB get(int JobNum){
		return JobTable.get(JobNum);
}
}

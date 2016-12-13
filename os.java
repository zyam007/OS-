/*
import java.util.Queue;
import java.util.LinkedList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
*/
import java.util.Map;
import java.util.HashMap;
import java.util.*;

public class os {

	//variables
	/*
	 * readyqueue, ioqueue,
	 *Now MemoryTable is places in os itself - working on that
	 */
	public static LinkedList <Integer> readyQueue; //not queue -change it
	public static LinkedList <Integer> ioQueue;
	public static LinkedList <Integer> drumToCoreQueue;
	public static LinkedList <Integer> coreToDrumQueue;
	//public static LinkedList <Integer> blockedQueue;
	//public static freeSpaceTable FreeSpaceTable;
	public static jobTable JobTable;

	public static Integer lastJobRunning;
	public static Integer jobToRun;
	public static PCB temp;
	public static Integer swapInMemory;
	public static Integer swapOutMemory;
	public static Integer jobIO;



	public static boolean doingIONow;
	public static boolean doingSwap;
	public static int timeSlice;

	public static boolean cpuGood;


	public static int [] MemoryTable;
	public static int memorySize;



	/*
	 * Allows initialization of static system variables declared above.
     *Called once at start of the simulation.
	 */
	public static void startup() {
		sos.ontrace();
		readyQueue = new LinkedList<Integer>();
		ioQueue = new LinkedList<Integer>();
		drumToCoreQueue = new LinkedList<Integer>();
		coreToDrumQueue = new LinkedList<Integer>();
		//blockedQueue = new LinkedList<Integer>();

		JobTable = new jobTable();
		//FreeSpaceTable = new freeSpaceTable();
		doingIONow = false;
		doingSwap = false;
		timeSlice = 10;
		lastJobRunning = 0;
		jobToRun = 0;

		cpuGood = false;
		swapInMemory = 0;
		swapOutMemory = 0;

		memorySize = 100;
		MemoryTable = new int [memorySize];
		for(int i=0; i<100; i++) {
			MemoryTable[i] = 0;
		}

	}
//new job arrived - interrupt
	public static void Crint(int []a, int[]p) {
		Bookkeeping(p[5]); //last job to save if any
		PCB job = new PCB(p[1],p[2],p[3],p[4],p[5]);
		JobTable.addNewJob(job);  //saving in Map by JobNUm and PCB object itself
		drumToCoreQueue.add(job.getJobNum()); //adding int JobNum to the queues, not PCB object itself, all updates about job will be written to Jobtable list of PCB
		MemoryManager();
		CpuScheduler();
		Dispatcher(a, p);

	}


//The disk has finished an I/O operation. I/O has been finished
	public static void Dskint(int []a, int[]p){

		Bookkeeping(p[5]);
		doingIONow = false; //finished IO
		JobTable.get(jobIO).unLatched(); //job is not doing IO now - it can be terminated then
		ioQueue.removeFirst(); //remove from IO queue
		JobTable.get(jobIO).decIOPending();  //dec IO pending as just finished it
		if(JobTable.get(jobIO).getIOPending()==0) { //if IO pending =0 => no more IO need to be finished
			JobTable.get(jobIO).unBlocked();
			//readyQueue.add(ioQueue.getFirst()); //it was on Ready Queue still
		}
		if(JobTable.get(jobIO).getTerminated()) { // kill the job as it finisged IO and wants to be terminated
				cleanUp(jobIO);
		}
		//pick job to do IO if you have jobs on IOqueue need to do it
		if(ioQueue.size()>0) { //still have jobs to do IO
			doingIONow = true;
			jobIO = ioQueue.getFirst();
			JobTable.get(jobIO).setLatched();
			sos.siodisk(jobIO); //schedule job to do IO - if job doing io set latched bit
		}
		MemoryManager();
		CpuScheduler(); //schedule job to run on cpu
		Dispatcher(a, p); //send parameters to sos to run
}

//Job swapping In/Out has finished
	public static void Drmint(int[]a, int[]p){ //need to doooooooo
		Bookkeeping(p[5]);
		doingSwap = false; //finished swapping
		readyQueue.add(swapInMemory); //swapInMemory has job number
		swapInMemory = 0;
		MemoryManager(); //to see if we can swap in another job
		//need to schedule job next to run!!!
		CpuScheduler();
		Dispatcher(a, p);


	}
//job finished running its TimeSlice, put it on readyQueue and schedule another Job
	public static void Tro(int[]a, int[]p){ //start to do!!!!!!!!
		Bookkeeping(p[5]);
		if(JobTable.get(lastJobRunning).getCpuTimeNeeded()==0) {
			if(!JobTable.get(lastJobRunning).getLatched()){
			cleanUp(lastJobRunning);
		} else { JobTable.get(lastJobRunning).setTerminated();}
		}
		System.out.println("Ready Queue is right now :   ");
		for(int i=0; i<readyQueue.size(); i++) {
		System.out.println(readyQueue.get(i) + "  ");
	}
		MemoryManager();
		CpuScheduler();
		Dispatcher(a, p);

	}

	/*
	 * Supervisor call from user program.
	 * At call: p [5] = current time
	 * a = 5 => job has terminated
	 * a = 6 => job requests disk i/o
	 * a = 7 => job wants to be blocked until all its pending
	 * I/O requests are completed
	 */
	//Start to do SVC - blocked and IO request to do first
	public static void Svc(int []a, int []p){
		Bookkeeping(p[5]); //save jobtToRun records by using Bookkeper


		if(a[0]==5) { //job wants to terminate, check if doing io right now, if not - kill it

			JobTable.get(lastJobRunning).setTerminated();
			if(!JobTable.get(lastJobRunning).getLatched()) {
					cleanUp(lastJobRunning);
					//addMemorySpace(lastJobRunning);
					//JobTable.removeJob(JobTable.get(lastJobRunning));
					//ioQueue.remove(lastJobRunning);
					//readyQueue.remove(lastJobRunning);
			}
		} else if(a[0]==6) { //job wants IO, add it to IO queue.
				JobTable.get(lastJobRunning).setIOPending(); //update JobTable Job IO Pending (Increment)
				ioQueue.add(lastJobRunning); //aded JobNum to IOqueue
				if(ioQueue.size()>=1 && !doingIONow) { //check if not doing IO rigth now -since only one job can do IO at time
					doingIONow = true; //setting marker to doing IO now, schedulling job to do IO
					jobIO = ioQueue.getFirst(); //FCFS IO job schedulled
					JobTable.get(jobIO).setLatched();
					sos.siodisk(jobIO); //send IO job to run on Disk
				//readyQueue.add(jobIO); //bookKeeper already added it to ready Queue again => no need to ad it
				}
			} else if(a[0]==7) {
				if(JobTable.get(lastJobRunning).getIOPending()!=0) {
				JobTable.get(lastJobRunning).setBlocked(); //if job wants to be Blocked => set it to be blocked
				//readyQueue.add(lastJobOnCpu);
			    //blockedQueue.add(lastJobRunning);
			    //jobToRun=null;
				}
			}

		MemoryManager(); //maybe can fir job into memory - try to place it
		CpuScheduler(); //schedule job to run on cpu
		Dispatcher(a, p);



}

	public static void CpuScheduler(){
		int counter=0;
		//temp = null;

		if(readyQueue.size()>=1) { //if there are jobs ready
			for(; counter<readyQueue.size(); counter++) {
				if(!JobTable.get(readyQueue.get(counter)).getBlocked() && !JobTable.get(readyQueue.get(counter)).getTerminated()) {

					cpuGood = true;
					jobToRun = readyQueue.get(counter);//pick job to run:first or next as soon as find it
					//temp = jobToRun;
					readyQueue.remove(counter);

					break;
				} else {
					cpuGood = false; //no jobs are ready to run
				}
			}
		} else { //no jobs to run, nothing on ready
			cpuGood = false;
		}
	}

	public static void Swapper(){ //swaps job in
			 //if swap is false => there is no job doing swap right now
			doingSwap = true;  //starting swap now
			drumToCoreQueue.remove(swapInMemory);
			sos.siodrum(JobTable.get(swapInMemory).getJobNum(), JobTable.get(swapInMemory).getJobSize(), JobTable.get(swapInMemory).getMemoryAddress(),0); //0 => direction of swap
			 //remove job from queue
		//sos.siodrum(job.getJobNum(), job.getJobSize(), job.getMemoryAddress(),0);
	}

	public static void Dispatcher(int a[], int p[]) {
		if(!cpuGood) {
			a[0] = 1; //no jobs => idle, no need in p[] values
			jobToRun = 0;
		} else if (JobTable.get(jobToRun).getCpuTimeNeeded()<=0) {
				a[0] = 1; //no jobs => idle, no need in p[] values
					cleanUp(jobToRun);//
					//addMemorySpace(jobToRun);
					//JobTable.removeJob(JobTable.get(jobToRun));
					//ioQueue.remove(jobToRun);
					//readyQueue.remove(jobToRun);
					jobToRun = 0;

		} else {
		JobTable.get(jobToRun).setStartTime(p[5]); //set start time
		a[0]=2; //put job to run
		p[2]=JobTable.get(jobToRun).getMemoryAddress();
		p[3]=JobTable.get(jobToRun).getJobSize();

		TimeManager(jobToRun, p); //sets time slice p[4]


		}
	}

	public static void MemoryManager() {
			 //try to place as much jobs as fit in memory for all jobs on queue
		if(!doingSwap) { //check if not doing swap - if yes, wait till finish it
			if(drumToCoreQueue.size()!=0) //if jobs wait for memory
				for(int i=0; i<drumToCoreQueue.size(); i++){
					//found space in memory and job to be placed by swapper
					if(findMemorySpace(JobTable.get(drumToCoreQueue.get(i)).getJobNum())) {
						//if(MemoryTable.findMemorySpace(JobTable.get(drumToCoreQueue.get(i)).getJobNum())) {
						swapInMemory = drumToCoreQueue.get(i);																//using job number in jobtable
						Swapper();  //if found space in our table, then we need to start swap into sos memory
						break;			//swapInMemory holds Job num
				} else {

			//swapper();//no memory for job to be placed - maybe to swap out???
					}
				}
		} //else for first if
	}

	public static void Bookkeeping(int currentTime) { //current time is p[5] value
//if previously job was running on cpu - then need to save it, if cpu was idling - then jobToRun would be null-nothing to update then
		if(jobToRun!=0) {

			lastJobRunning = jobToRun;
			//update in jobtable by searchin key-JobNum
			JobTable.get(lastJobRunning).setCpuTimeNeeded(currentTime - JobTable.get(lastJobRunning).getStartTime());
			//lastJobRunning.setCpuTimeUsed((currentTime - startTime)); //accumulates cpu time used, adds to previous stored value
			//update Jobtable with latest information
      readyQueue.add(lastJobRunning); //put jobNum back on readyqueue
			//readyQueue.add(lastJobOnCpu); won't place it on ready queue - do it individually
		 //to know the start time when next job will start on CPU
		}
	}

	public static void TimeManager(int jobNum, int[]p){
		if((JobTable.get(jobNum).getCpuTimeNeeded())>=timeSlice){
			p[4] = timeSlice;
		} else {
			p[4] = (JobTable.get(jobNum).getCpuTimeNeeded()); //set to run for how long it has left to run
		}

	}

	public static void UpdateJobTable(PCB job) {
		JobTable.put(job.getJobNum(), job);
	}


//What if I put FreeSpaceTable in os file???? it worked
public static boolean findMemorySpace(int jobNum) {//pass value job size
		int freeSpace;
		int MemoryIndex;
		boolean memoryAvailable = false;
		for(int i=0; i<100; i++) {
			freeSpace=0;
			MemoryIndex=i;
			if(i>99) {return memoryAvailable;}
			while(MemoryTable[i]==0) {
				freeSpace++;
				i++;
				if(i==99) { break; } //no memory to place job
				if(freeSpace==JobTable.get(jobNum).getJobSize()){
					JobTable.get(jobNum).setMemoryAddress(MemoryIndex);
					JobTable.get(jobNum).setInCore();
				  fillMemory(jobNum);
				  memoryAvailable = true;
				  return memoryAvailable;
				}
			}
		}
		return memoryAvailable;
	}

	/*
	 * fill memory table with  Job number to indicate that it is occupied
	 */
	public static void fillMemory(int jobNum) {
		for(int i=JobTable.get(jobNum).getMemoryAddress(); i<(JobTable.get(jobNum).getJobSize()+JobTable.get(jobNum).getMemoryAddress()); i++) {
			MemoryTable[i]=JobTable.get(jobNum).getJobNum();
		}
	}
/*
 * Job gets out of memory, fill in it's space with 0 - indicating free memory
 */
	public static void addMemorySpace(int jobNum) {
		for(int i=JobTable.get(jobNum).getMemoryAddress(); i<(JobTable.get(jobNum).getJobSize()+JobTable.get(jobNum).getMemoryAddress()); i++) {
			MemoryTable[i]=0;
		}
	}

	public static void cleanUp(int jobNum) { //take terminated job out of everything, indicate which job to kill
					addMemorySpace(jobNum);
					JobTable.removeJob(JobTable.get(jobNum));
					if(ioQueue.contains(Integer.valueOf(jobNum)))
					{
						ioQueue.remove(Integer.valueOf(jobNum));
					}
					if(readyQueue.contains(Integer.valueOf(jobNum))){
					readyQueue.remove(Integer.valueOf(jobNum));
				}
	}

}

import java.util.*;

/*
 * OS class, responsible for initializing all variables
 * needed for work, contains fubctions to process 5 Interrupts
 * Also has and manages MemoryTable
 * Includes CPUscheduller, Swapper, Dispatcher, MemoryManager,
 * Bookkeeping, TimeManager, findMemorySpace, fillMemory,
 * addMemorySpace, and CleanUp
 * Each Job information updates via JobTable
 */
public class os 
{	//Containers for job process to hold jobs ready to run
	//Jobs ready to do IO, ready to swapIn/swapOut
	//All queues holds Job Number, and not Job object itself
	//Job Object is stored in JobTable only
	public static LinkedList <Integer> readyQueue; 
	public static LinkedList <Integer> ioQueue;
	public static LinkedList <Integer> drumToCoreQueue;
	//JobTable and MemoryTable to use for storing jobs in system and core
	public static jobTable JobTable;
	public static int [] MemoryTable;
	//variables to hold temp value of Job Number in current process 
	public static Integer lastJobRunning;
	public static Integer jobToRun;
	public static Integer swapInMemory;
	public static Integer swapOutMemory;
	public static Integer jobIO;
	public static Integer coreToDrumJob;
	//used to check if some process are currently executing
	public static boolean doingIONow;
	public static boolean doingSwapIn;
	public static boolean doingSwapOut;
	public static boolean cpuGood;
	//timeslice of 2 gives better results than other values
	public static final int TIMESLICE = 2;
	public static final int MEMORYSIZE = 100;
	public static int direction;
	public static int jobMemoryBorder;
	
	public static int [][] BestFitTable;
	
	/*
	 * Allowing to initialize necessary variables to start process
     * Called once at start of the simulation by sos
	 */
	public static void startup() 
	{  
		sos.ontrace();
		//creates all queues 
		readyQueue = new LinkedList<Integer>();
		ioQueue = new LinkedList<Integer>();
		drumToCoreQueue = new LinkedList<Integer>();
		//JobTable to store jobs on drum
		JobTable = new jobTable();
		//Set temp variables to false since none process has been started
		//CpuGood indicated you can use cpu
		doingIONow = false;
		doingSwapIn = false;
		doingSwapOut = false;
		cpuGood = false;
		//direction -1 => so no swapping 
		direction = -1;
		//Holds JobsNumbers, 0 when no jobs were assigned
		lastJobRunning = 0;
		jobToRun = 0;
		coreToDrumJob = 0;
		//swapping is not in process => 0, otherwise has Job Number
		swapInMemory = 0;
		swapOutMemory = 0;
		//we have 100K memory(0-99)
		jobMemoryBorder = 0;
		//Memorytable is array of 100 size
		//we set free memory with 0, otherwise Job Number is put in every 
		//memory cell to indicate that it is occupied
		MemoryTable = new int [MEMORYSIZE];
		for(int i=0; i<MEMORYSIZE; i++) 
		{
			MemoryTable[i] = 0;
		}
		BestFitTable = new int [50][2]; // [][0] = Size of Memory [][1] = Address in Memory Table
		BestFitTable [0][0] = MEMORYSIZE;
		BestFitTable [0][1] = 0;
	}
	
	/*
	 * Crint is called when new job arrives in, 
	 * the information we need to store in Job object: 
	 * a - don't need to use
	 * p[1] - Job Number, p[2] - Priority
	 * p[3] - Job Size, p[4] - Max Cpu
	 * p[5] - current time when interrupt happened
	 * we need to use it to update how much cpu time
	 * was used by the last job running on Cpu
	 */
	public static void Crint(int []a, int[]p) 
	{   //saving information about job which run on cpu before interrupt
		Bookkeeping(p[5]); 
		//create new Job object
		PCB job = new PCB(p[1],p[2],p[3],p[4],p[5]);
		JobTable.addNewJob(job);  
		//job wants to get into memory, we have queue to hold such jobs
		drumToCoreQueue.add(job.getJobNum());
		//we calling these three methods in each interrupt handler
		//to find job to place in/out memory if available
		//pick job to run On Cpu
		//and set a and p variables
		MemoryManager();
		CpuScheduler();
		Dispatcher(a, p);
	}
	
	/*
	 * Disint is called when job has finished doing IO and
	 * we need to set that job unlatched, and decrement IO operation
	 * as it just finished one.
	 * we need to get it out of IOqueue, and put it on ReadyQueue
	 * The job might be blocked, so we need to unblock it as IO is finished
	 * If job requested to be terminated before, as IO is finished
	 * we need to remove that job from all queues and free memory space
	 * After, we need to pick next job to do IO if any on IOqueue
	 * 
	 */
	public static void Dskint(int []a, int[]p)
	{
		Bookkeeping(p[5]);
		//IO is done
		doingIONow = false; 
		JobTable.get(jobIO).unLatched();
		ioQueue.removeFirst();
		JobTable.get(jobIO).decIOPending(); 
		//As IO finished => we can unblock the job
		JobTable.get(jobIO).unBlocked();
			//If job requested to be terminated, as soon as IO finished we remove job out
			if(JobTable.get(jobIO).getTerminated()) { 
				cleanUp(jobIO);
			}
			//pick job to do IO if you have jobs on IOqueue need to do it
			//still have jobs to do IO on IO queue =>schedule first from io queue
			if(ioQueue.size()>0) { 
				doingIONow = true;
				jobIO = ioQueue.getFirst();
				//latched is set to indicated job which is doing IO Right now
				JobTable.get(jobIO).setLatched();
				//send to sos command siodisk with Job Number parameter of Job to do IO
				sos.siodisk(jobIO); 
			}
			
		MemoryManager();
		CpuScheduler(); 
		Dispatcher(a, p);
	}

	/*
	 * sos calls Drumint when job has finished swapping IN or OUT
	 * using only p[5](current time) in Bookkeeping
	 * need to check which swap it was - IN or OUT
	 * if swap in memory => job need to be added to ready queue
	 * if swap in job previously swapped out, need to checkn if it had IO requested but not processed
	 * if yes => need to add also to IO queue
	 * if swap out of memory => add job to the drum to memory queue
	 * free memory that job occupied 
	 */
	public static void Drmint(int[]a, int[]p)
	{ 
		Bookkeeping(p[5]);
			if(doingSwapIn) {
				doingSwapIn = false; 
				readyQueue.add(swapInMemory);
					if(JobTable.get(swapInMemory).getIOPending()==1) {
						ioQueue.add(swapInMemory);
					}
				swapInMemory = 0;
			} 	
			else {
				doingSwapOut = false; 
				drumToCoreQueue.add(swapOutMemory);
				addMemorySpace(swapOutMemory);
				swapOutMemory = 0;
			}
		direction = -1;
		MemoryManager(); 
		CpuScheduler();
		Dispatcher(a, p);
	}
	
	/*
	 * Tro is called when job used all time allocated on CPU - 
	 * either whole timeslice, or cpu time it has left to run
	 * whichever is less
	 * If job finished its cpu time => check if it has pending IO
	 * If yes => set it to terminate and wait until it finishes it and 
	 */
	public static void Tro(int[]a, int[]p)
	{ 
		Bookkeeping(p[5]);
			if(JobTable.get(lastJobRunning).getCpuTimeNeeded()==0) {
				if(JobTable.get(lastJobRunning).getIOPending()==0) {    
					cleanUp(lastJobRunning);
				} 
				else { 
					JobTable.get(lastJobRunning).setTerminated();
				}
			}
		MemoryManager();
		CpuScheduler();
		Dispatcher(a, p);
	}

	/*
	 * SVC is called if running jon on cpu wants to be blocked, or IO request, or terminate
	 * At call: p [5] = current time
	 * a = 5 => job has terminated - kill job if no pending IO left
	 * a = 6 => job requests disk i/o - put it on readyQueue and schedule IO to process
	 * a = 7 => job wants to be blocked until all its pending I/O requests are completed
	 *
	 */
	public static void Svc(int []a, int []p)
	{
		Bookkeeping(p[5]); 
			//job wants to be terminated
			if(a[0]==5) { 
				JobTable.get(lastJobRunning).setTerminated();
				if(JobTable.get(lastJobRunning).getIOPending()==0) { 
					cleanUp(lastJobRunning);
				}
			//job request IO
			} 
			//job request IO
			else if(a[0]==6) { 	//increment IO pending in Job Object
				JobTable.get(lastJobRunning).setIOPending();
				ioQueue.add(lastJobRunning);
					//check if not doing IO right now =>can schedule if no IO in process
				if(!doingIONow) {	
					//now we starting IO to run
					doingIONow = true;
					//IO schedulled FCFS => get the first in queue
					jobIO = ioQueue.getFirst();
					//set parameter indicating Job is in IO processing
					JobTable.get(jobIO).setLatched();
					sos.siodisk(jobIO);
				}
			
			} 
			//Block request
			else if(a[0]==7) {
				//Some Jobs request to be blocked but don't have IO pending - don't need to block that job
				if(JobTable.get(lastJobRunning).getIOPending()!=0) {
					JobTable.get(lastJobRunning).setBlocked();
				}
			}
		MemoryManager(); //maybe can fir job into memory - try to place it
		CpuScheduler(); //schedule job to run on cpu
		Dispatcher(a, p);
	}
	/*
	 * Method to choose which Job to pick from ready queue to run on cpu
	 * Using RoundRobin schedulling algorithm to pick first Job on queue
	 * and give it timeslice to run. 
	 * Need to check if job is blocked or terminated
	 * If not => schedule it to run on cpu
	 * Remove form Ready queue
	 * 
	 */
	public static void CpuScheduler()
	{	//check if any job on ready queue, if not - nothing to schedule
		if(readyQueue.size()>=1) {	
			//go through whole loop if necessary as first jobs might be blocked or terminated
			for(int i=0; i<readyQueue.size(); i++) {
				if(!JobTable.get(readyQueue.get(i)).getBlocked() && !JobTable.get(readyQueue.get(i)).getTerminated()) { 
					//found job to run
					cpuGood = true;
					//save picked job
					jobToRun = readyQueue.get(i);
					readyQueue.remove(i);
					//break as soon as found the job
					break;
				} 
				else {	//no job found => can't use cpu
					cpuGood = false; 
				}
			}
		} 
		else { 	
			//no jobs to run, ready queue empty or everything blocked
			cpuGood = false;
		}
	}
	/*
	 * Swapper is called by  MemoryManager if swap IN or swap Out needed
	 * Direction holds either 0 (swapin) or 1 (swapout)
	 * If swapin => job needs to be removed from waiting line to get into memory
	 * sos.siodrum is used to send all job information which we select to swapin
	 * send parameters - Job Number, Job Size, startting Memory address and direction
	 * If swapout => need to remove that job from ready and io queue to prevent from running it
	 * And set Out of Core flag in Job Object
	 * all progress of a job is saved before swap out, including io pending
	 */
	public static void Swapper(int direction)
	{
		//checking direction
		if(direction == 0) {
			//starting swapIN
			doingSwapIn = true;  
			drumToCoreQueue.remove(swapInMemory);
			//call to sos to indicate Job to start swapIN
			sos.siodrum(JobTable.get(swapInMemory).getJobNum(), JobTable.get(swapInMemory).getJobSize(), 
					JobTable.get(swapInMemory).getMemoryAddress(),0);
	
		} 
		else { 
			//starting swapOut
			doingSwapOut = true;
			swapOutMemory = coreToDrumJob;
			readyQueue.remove(Integer.valueOf(swapOutMemory));
			ioQueue.remove(Integer.valueOf(swapOutMemory));
			coreToDrumJob = 0;
			JobTable.get(swapOutMemory).setOutOfCore();
			sos.siodrum(JobTable.get(swapOutMemory).getJobNum(), JobTable.get(swapOutMemory).getJobSize(), 
					JobTable.get(swapOutMemory).getMemoryAddress(),1);
		}
	}

	/*
	 * Dispatcher is responsible to set a and p parameters of Job to run on cpu
	 * If previously job was found to run => parameters are folllowing:
	 * a[0]=2 (Run Job on cpu), p[2] - memory start address
	 * p[3]=Job size, p[4]=time slice
	 * time slice is set by Time manager
	 * If none job to run => set a[0]=1 (cpu idle)
	 */
	public static void Dispatcher(int a[], int p[]) {
		if(!cpuGood) {
			a[0] = 1;
			jobToRun = 0;
		} 
		else if (JobTable.get(jobToRun).getCpuTimeNeeded()<=0) {
			//no jobs => idle, no need in p[] values
			a[0] = 1; 
			cleanUp(jobToRun);
			jobToRun = 0;
		} 
		else {
			//record start time contained in p[5] to use it in calculation 
			//how much time job spend on cpu running whe it finishes or interrupted
			JobTable.get(jobToRun).setStartTime(p[5]);
			a[0]=2; 
			p[2]=JobTable.get(jobToRun).getMemoryAddress();
			p[3]=JobTable.get(jobToRun).getJobSize();
			//manages p[4] parameter
			TimeManager(jobToRun, p);
		}
	}
	/*
	 * MemoryManager places jobs in memory if it is possible
	 * while doing swap it cannot start looking which jobs to pick in or out
	 * Using First Fit strategy, first job to fit in free memory space is picked
	 * If job is find, no more looking for other job, callin swapper to take care of it 
	 * If none jobs on drumToCore queue can be placed in memory => starting swap out
	 * Swap out starts when JobTable is filled with 15 or more jobs
	 * It loops through whole readyQueue and pick the biggest in size
	 * and which isn't latched, or wants to be terminated
	 * Call swapper if found job to swapout
	 * 
	 */
	public static void MemoryManager() {
		
		//check is swapIN/Out in not in process
		if(!doingSwapIn && !doingSwapOut) { 
			//if any jobs available on queue to get into memory
			if(drumToCoreQueue.size()!=0) {
				for(int i=0; i<drumToCoreQueue.size(); i++) {
					//findMemorySpace returns true if job can fit into memory
					//and place it in our memoryTable
					if(findMemorySpace(JobTable.get(drumToCoreQueue.get(i)).getJobNum())) {
						swapInMemory = drumToCoreQueue.get(i);
						direction = 0;
						Swapper(direction);  
						break;	
					} 
				}
			}
		} 
		//in no jobs to swap in was found, try to swap out
		int biggestJob =0;
		if(!doingSwapIn && !doingSwapOut) { 
			if(JobTable.size()>=15) { 
				for(int j=0; j<readyQueue.size(); j++) {
					//check if job is not doign IO right now(can't be swaped out, 
					//and job is not waiting to be terminated
					if(JobTable.get(readyQueue.get(j)).getJobSize()>biggestJob 
							&& !JobTable.get(readyQueue.get(j)).getLatched() 
								&& !JobTable.get(readyQueue.get(j)).getTerminated()) {
						biggestJob = readyQueue.get(j); 
					}
				}
				coreToDrumJob = biggestJob;
				direction = 1;
				Swapper(direction);
			}
		}
	}
 
	/*
	 * Bookkeping is used ad the beginning or each Interrupt handler
	 * It checks if jobwas runnnig on cpu before interrupt
	 * If yes => updates information regarding how much time to run on cpu that job has left
	 * And add that job to the end of readyQueue
	 */
	public static void Bookkeeping(int currentTime) { 
		//if job was on cpu
		if(jobToRun!=0) {
			lastJobRunning = jobToRun;
			//update job cpu time Needed left to run(subtracts time it run on cpu)
			JobTable.get(lastJobRunning).setCpuTimeNeeded(currentTime - JobTable.get(lastJobRunning).getStartTime());
			readyQueue.add(lastJobRunning); //put jobNum back on readyqueue	
		}
	}

	/*
	 * TimeManager sets time slice for job to run on cpu
	 * if time job needs to have on cpu is more than timeslice
	 * give that job timeslice to run
	 * if job's cpu needs is less that timeslice, give the amoun it needs 
	 */
	public static void TimeManager(int jobNum, int[]p) {
		if((JobTable.get(jobNum).getCpuTimeNeeded())>=TIMESLICE) {
			p[4] = TIMESLICE;
		} 
		else {
			p[4] = (JobTable.get(jobNum).getCpuTimeNeeded()); 
		}
	}

	/*
	 * Finds free chunk of memory for job if any is available
	 * Going through whole MemoryTable and checks if space = 0 (free space)
	 * keep track how many free space continous and compare each time with job size
	 * once free space = job size => place it in MemoryTable
	 * record job starting MemoryIndex, set Job in Core
	 * calls function to fill assigned space in MemoryTable with Job Number
	 * returns true is space is found and false if not
	 */
	/*
	public static boolean findMemorySpace(int jobNum) {
		//temp variables to use in search for free space
		int freeSpace;
		int MemoryIndex;
		boolean memoryAvailable = false;
		//going through whole memoryTable
		for(int i=0; i<MEMORYSIZE; i++) {
			freeSpace=0;
			//index to record where freespace starts
			MemoryIndex=i;
			//check so don't try to access memory out of bounds
			if(i>99) {return memoryAvailable;}
				//while free space is after Memory Index, increment free space and go again
			while(MemoryTable[i]==0) {
				freeSpace++;
				i++;
				if(i==99) { break; } 
				//if job size can fit in free space =>stop looking, place it
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
	*/
	public static boolean findMemorySpace(int jobNum){
		if (BestFitTable[0][0] == MEMORYSIZE){
			updateBestFit(0);
		}
		for (int i = 0; i< 100; i++){
			if (BestFitTable[i][0] >= JobTable.get(jobNum).getJobSize()){
				JobTable.get(jobNum).setMemoryAddress(BestFitTable[i][1]);
				JobTable.get(jobNum).setInCore();
				fillMemory(jobNum);
				updateBestFit(i);
				//memoryAvailable = true;
				return true;
			}
		}
		return false;
		
		
	}

	public static void updateBestFit(int index){
		int size = 0;
		int tmpSize, tmpAddress;
		//int index = 0;
		for (int i=0; i<100; i++){
			if (MemoryTable[i] == 0){
				size++;
			}
			if (MemoryTable[i] > 0 && size > 0) {
				BestFitTable[index][0] = size;
				BestFitTable[index][1] = i;
				index++;
			}
		}
		//index = combineBFT(index+1);
		for (int i=0; i<index+1; i++){
			for (int j = 0; j<i; j++){
				if (BestFitTable[j][0] > BestFitTable[i][0]){}
				tmpSize = BestFitTable[j][0];
				tmpAddress = BestFitTable[j][1];
				BestFitTable[j][0] = BestFitTable[i][0];
				BestFitTable[j][1] = BestFitTable[i][1];
				
				BestFitTable[i][0] = tmpSize;
				BestFitTable[i][1] = tmpAddress;
			}
		}
	} 
	/*
	Checks to see if any adresses are next to each other and can be combined
	*/
	public static int combineBFT(int index){
		int totalAddress;
		for (int i=0; i< index; i++){
			totalAddress = BestFitTable[i][0] + BestFitTable[i][1];
			for (int j=index-1; j>i; j--){
				if (BestFitTable[j][1] == totalAddress){
				BestFitTable[i][0] = BestFitTable[i][0] + BestFitTable[j][0];
				BestFitTable[j][0] = BestFitTable [index-1][0];
				BestFitTable[j][1] = BestFitTable [index-1][1];
				BestFitTable [index-1][1] = 0;
				BestFitTable [index-1][1] = 0;
				index--;
				}
			}
		}
		return index;
	}
	

	/*
	 * method to fill found free space with Job Number
	 */
	public static void fillMemory(int jobNum) {
		jobMemoryBorder = JobTable.get(jobNum).getJobSize()+JobTable.get(jobNum).getMemoryAddress();
		for(int i=JobTable.get(jobNum).getMemoryAddress(); i<jobMemoryBorder; i++) {
			MemoryTable[i]=JobTable.get(jobNum).getJobNum();
		}
	}
	
	/*
	 * Adding free space once job is removed from memory
	 */
	public static void addMemorySpace(int jobNum) {
		jobMemoryBorder = JobTable.get(jobNum).getJobSize()+JobTable.get(jobNum).getMemoryAddress();
		for(int i=JobTable.get(jobNum).getMemoryAddress(); i<jobMemoryBorder; i++) {
			MemoryTable[i]=0;
		}
	}

	/*
	 * Once job is finished and terminated completety
	 * JobTable needs to erase Job records
	 * free memory occupied by this job
	 * remove it from ready queue
	 */
	public static void cleanUp(int jobNum) {
		addMemorySpace(jobNum);
		JobTable.removeJob(JobTable.get(jobNum));
		if(readyQueue.contains(Integer.valueOf(jobNum))) {
			readyQueue.remove(Integer.valueOf(jobNum));
		}
	}

}

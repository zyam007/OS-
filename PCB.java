

public class PCB {
	int JobNum;
	int Size;
	int StartTime;
	int CpuTimeNeeded;
	int CpuTimeUsed;
	int MaxCpuTime;
	int IOPending;
	int IOComp;
	int Priority;
	int MemoryAddress;
	boolean Blocked;
	boolean Latched; //Job currently doing IO, cannot be swapped out
	boolean InCore;
	boolean Terminated;
	boolean Overwrite;
	boolean KillBit;


	//everything about the job saved in this constructor
	public PCB(int JobNum, int Priority, int Size, int MaxCpuTime, int StartTime ){
		this.JobNum = JobNum;
		this.Priority = Priority; //we don't use priority
		this.Size = Size;
		this.MaxCpuTime = MaxCpuTime;
		this.StartTime = 0;

		Blocked = false;
		Latched = false;
		InCore = false;
		Terminated = false;
		IOPending = 0;
		IOComp = 0;
		KillBit = false;
		MemoryAddress = -1;
		this.CpuTimeNeeded = MaxCpuTime;

		//getters
	}

	public int getJobNum(){
		return JobNum;
	}

	public int getJobSize(){
		return Size;
	}

	public int getCpuTimeNeeded(){
		return CpuTimeNeeded;
	}

	public int getCpuTimeUsed(){
		return CpuTimeUsed;
	}

	public int getMaxCpuTime(){
		return MaxCpuTime;
	}

	public int getIOPending(){
		return IOPending;
	}

	public int getIOComp(){
		return IOComp;
	}

	public boolean getBlocked(){
		return Blocked;
	}
	public boolean getLatched(){
		return Latched;
	}
	public boolean getInCore(){
		return InCore;
	}
	public boolean getTerminated(){
		return Terminated;
	}

	public int getMemoryAddress(){
		return MemoryAddress;
	}

	public int getStartTime(){
		return StartTime;
	}


//setters

	public void setCpuTimeUsed(int time){
		this.CpuTimeUsed += time;

	}

	public void setCpuTimeNeeded(int time){
		this.CpuTimeNeeded -= time;

	}

	public void setIOPending(){
		this.IOPending++;
	}

	public void decIOPending() {
		this.IOPending--;
	}

	public void setIOComp(){
		this.IOComp++;
	}

	public void setBlocked(){
		this.Blocked = true;
	}

	public void unBlocked(){
		this.Blocked = false;
	}
	public void setLatched(){
		this.Latched = true;
	}
	public void unLatched(){
		this.Latched = false;
	}

	public void setInCore(){
		this.InCore = true;
	}

	public void setOutOfCore(){
		this.InCore = false;
	}
	public void setTerminated(){
		this.Terminated = true;
	}
	//memory
	public void setMemoryAddress(int MemoryIndex){
		this.MemoryAddress = MemoryIndex;
	}

	public void setStartTime( int time){
		this.StartTime = time;
	}



}

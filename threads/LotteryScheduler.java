package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }
    
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */

    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new LotteryQueue(transferPriority);
    }
    
    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());
        
        return getLotteryThreadState(thread).getPriority();
    }
    
    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());
        
        return getLotteryThreadState(thread).getEffectivePriority();
    }
    
    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());
        
        Lib.assertTrue(priority >= priorityMinimum &&
                       priority <= priorityMaximum);
        
        getLotteryThreadState(thread).setPriority(priority);
    }
    
    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();
        
        KThread thread = KThread.currentThread();
        
        int priority = getPriority(thread);
        if (priority == priorityMaximum)
            return false;
        
        setPriority(thread, priority+1);
        
        Machine.interrupt().restore(intStatus);
        return true;
    }
    
    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();
        
        KThread thread = KThread.currentThread();
        
        int priority = getPriority(thread);
        if (priority == priorityMinimum)
            return false;
        
        setPriority(thread, priority-1);
        
        Machine.interrupt().restore(intStatus);
        return true;
    }
    
    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = Integer.MAX_VALUE;
    
    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected LotteryThreadState getLotteryThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new LotteryThreadState(thread);
        
        return (LotteryThreadState) thread.schedulingState;
    }
    
    
    
    
    
    
    
    
    
    
    protected class LotteryQueue extends ThreadQueue {
        LotteryQueue(boolean transferPriority) {//ook
            this.transferPriority = transferPriority;
            priority_Wrong=false;
            Effective_priority=0;
        }
        
        public void waitForAccess(KThread thread) {//ook
            Lib.assertTrue(Machine.interrupt().disabled());
            getLotteryThreadState(thread).waitForAccess(this);
            setWrong();
        }
        
        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            getLotteryThreadState(thread).acquire(this);
            
        }
        
        public KThread nextThread() {//ook
            //       System.out.println("next:");
            Lib.assertTrue(Machine.interrupt().disabled());
            
            if (this.lockHolder != null && this.transferPriority)
                this.lockHolder.Lockset.remove(this);
            if (waitQueue.isEmpty())
                return null;
            KThread optimal_Thread = pickNextThread();
            if (optimal_Thread!=null){
                waitQueue.remove(optimal_Thread);
                getLotteryThreadState(optimal_Thread).acquire(this);
            }
            //       System.out.println("HAHAHA");
            
            //   System.out.println( getLotteryThreadState(optimal_Thread).getEffectivePriority()+"HAHAHA");
            return optimal_Thread;
        }
        
        
        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return	the next thread that <tt>nextThread()</tt> would
         *		return.
         */
        protected KThread pickNextThread() {//ook
            // implement me!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            KThread res=null;

            int Total_Ticket=0;
            
            Iterator<KThread>  it=waitQueue.iterator();
            while (it.hasNext()){
                //      System.out.println("aa:");
                
                KThread temp=it.next();
                int temp_priority = getLotteryThreadState(temp).getEffectivePriority();
                    Total_Ticket=temp_priority;
                
              }
            //从1到Total_Ticket 之间随机取出一个数
            Total_Ticket=(int)((Math.random())*Total_Ticket)+1;
            
            it=waitQueue.iterator();
            while (it.hasNext()){
                //      System.out.println("aa:");
                
                KThread temp=it.next();
                int temp_priority = getLotteryThreadState(temp).getEffectivePriority();
                Total_Ticket-=temp_priority;
                if (Total_Ticket<=0)
                    return temp;
                

            }
            
            return res;
        }
        
        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me (if you want)
        }
        
        public int getEffectivePriority(){//ook
            //    System.out.println("          WAAAAAA"+(transferPriority)+waitQueue.size());
            if (transferPriority == false)
                return priorityMinimum;
            //     System.out.println("          In queue:"+Effective_priority);
            if ( priority_Wrong==true){
                Iterator<KThread> it=waitQueue.iterator();
                //   System.out.println("          more in:");
                Effective_priority=0;
                while (it.hasNext()){
                    KThread temp=it.next();
                    int temp_priority=getLotteryThreadState(temp).getEffectivePriority();
                    Effective_priority+=temp_priority;
                }
                priority_Wrong=false;
            }
            return Effective_priority;
        }
        
        public void setWrong() {//ook
            if (transferPriority==true && priority_Wrong==false){
                priority_Wrong = true;
                if (lockHolder!=null)
                    lockHolder.setWrong();
            }
        }
        
        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;
        private boolean priority_Wrong;
        private int Effective_priority=0;
        private LotteryThreadState lockHolder = null;
        private LinkedList<KThread> waitQueue = new LinkedList<KThread>();
    }
    
    
    
    
    
    
    
    
    
    
    
    
    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class LotteryThreadState{
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param	thread	the thread this state belongs to.
         */
       
        public LotteryThreadState(KThread thread) {
            this.thread = thread;
            setPriority(priorityDefault);
        }
        
        /**
         * Return the priority of the associated thread.
         *
         * @return	the priority of the associated thread.
         */
        public int getPriority() {//ook
            return priority;
        }
        
        /**
         * Return the effective priority of the associated thread.
         *
         * @return	the effective priority of the associated thread.
         */
        public int getEffectivePriority() {//ook
            // implement me!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            //   System.out.println("    In"+priority);
            if (priority_Wrong==true){
                //   System.out.println("    ReallIn?");
                Effective_priority=priority;
                Iterator it=Lockset.iterator();
                while (it.hasNext()){
                    LotteryQueue temp=(LotteryQueue)(it.next());
                    int temp_priority=temp.getEffectivePriority();
                    Effective_priority+=temp_priority;
                    //       System.out.println("    nextStep?"+Effective_priority);
                }
                priority_Wrong=false;
            }
            return Effective_priority;
        }
        
        public void setWrong(){//ook
            // implement me!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            if (priority_Wrong==true)
                return;
            priority_Wrong=true;
            if (father_Thread!=null)
                father_Thread.setWrong();
        }
        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param	priority	the new priority.
         */
        public void setPriority(int priority) {//ook
            if (this.priority == priority)
                return;
            this.priority = priority;
            setWrong();
            // implement me!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            //ok
        }
        
        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param	waitQueue	the queue that the associated thread is
         *				now waiting on.
         *
         * @see	nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(LotteryQueue waitQueue) {//ook
            // implement me!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            waitQueue.waitQueue.add(thread);
            father_Thread=waitQueue;
            if (Lockset.indexOf(waitQueue)>=0){
                Lockset.remove(waitQueue);
                // System.out.println("  BIIIG error");
                waitQueue.lockHolder=null;
            }
            waitQueue.setWrong();
        }
        
        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see	nachos.threads.ThreadQueue#acquire
         * @see	nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(LotteryQueue waitQueue) {//ook
            // implement me!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            
            if (waitQueue.lockHolder!=null && waitQueue.transferPriority)
                waitQueue.lockHolder.Lockset.remove(waitQueue);
            waitQueue.lockHolder = this;
            Lockset.add(waitQueue);
            if (waitQueue==father_Thread)
                father_Thread=null;
            setWrong();
        }
        
        /** The thread with which this object is associated. */	   
        protected KThread thread;
        /** The priority of the associated thread. */
        protected int priority=0;
        protected int Effective_priority=0;
        protected boolean priority_Wrong;
        protected LotteryQueue father_Thread;
        private LinkedList<ThreadQueue> Lockset = new LinkedList<ThreadQueue>();
        
    }
    
    
    
    
    
}

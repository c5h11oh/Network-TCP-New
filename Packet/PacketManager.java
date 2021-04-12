package Packet;

// import java.util.PriorityQueue;
import java.util.concurrent.PriorityBlockingQueue;

import Statistics.*;

public class PacketManager {
    private final int windowsSize; //in number of segments
    private PriorityBlockingQueue<PacketWithInfo> packetsWithInfo;
    private Statistics statistics;
    private int localSequenceNumber;
    private int remoteSequenceNumber; // Fill in `remoteSequenceNumber + 1` in my outgoing packet's ACK field
    private boolean allPacketsEnqueued;

    public PacketManager(int windowSize){
        this.windowsSize = windowSize;
        this.remoteSequenceNumber = this.localSequenceNumber = 0;
        packetsWithInfo = new PriorityBlockingQueue<PacketWithInfo>(11, new PacketWithInfoComparator());
        statistics = new Statistics();
        this.allPacketsEnqueued = false;
    }

    public PriorityBlockingQueue<PacketWithInfo> getQueue(){
        return this.packetsWithInfo;
    }

    public synchronized void setRemoteSequenceNumber(int remoteSeq){
        this.remoteSequenceNumber = remoteSeq;
    }

    public synchronized int getRemoteSequenceNumber(){
        return this.remoteSequenceNumber;
    }

    public synchronized int getLocalSequenceNumber(){
        return this.localSequenceNumber;
    }

    public synchronized void setLocalSequenceNumber( int localSeq){
        this.localSequenceNumber = localSeq;
        return;
    }

    public synchronized void setAllPacketsEnqueued(){
        this.allPacketsEnqueued = true;
    }

    public synchronized boolean isAllPacketsEnqueued(){
        return this.allPacketsEnqueued;
    }

    public Statistics getStatistics(){
        return this.statistics;
    }

    /*
    This function scan through the queue and checking unexpired packets all time 
    retransmit and set new timeout during the process
    */
    public synchronized Packet returnExpired(){
        //while ! all packet enqueued
            //if the queue not empty: cheking timout util find unexpired packet 
            //if queue empty, sleep until sender buffer put() notify 
        //end while, another while loop to check until queue empty 
        return null; 

    }
}

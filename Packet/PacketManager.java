package Packet;

// import java.util.PriorityQueue;
import java.util.concurrent.PriorityBlockingQueue;

import Statistics.*;

public class PacketManager {
    private final int windowsSize; //in number of segments
    private PriorityBlockingQueue<PacketWithInfo> packetsWithInfo;
    private Statistics statistics;
    private int localSequenceNumberCounter;
    private int remoteSequenceNumberCounter;
    private boolean allPacketsEnqueued;

    public PacketManager(int windowSize){
        this.windowsSize = windowSize;
        this.remoteSequenceNumberCounter = this.localSequenceNumberCounter = 0;
        packetsWithInfo = new PriorityBlockingQueue<PacketWithInfo>(11, new PacketWithInfoComparator());
        statistics = new Statistics();
        this.allPacketsEnqueued = false;
    }

    public PriorityBlockingQueue<PacketWithInfo> getQueue(){
        return this.packetsWithInfo;
    }

    public synchronized void setRemoteSequenceNumberCounter(int remoteSeq){
        this.remoteSequenceNumberCounter = remoteSeq;
    }

    public synchronized int getRemoteSequenceNumberCounter(){
        return this.remoteSequenceNumberCounter;
    }

    public synchronized int getLocalSequenceNumberCounter(){
        return this.localSequenceNumberCounter;
    }

    public synchronized void setLocalSequenceNumberCounter( int localSeq){
        this.localSequenceNumberCounter = localSeq;
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
}

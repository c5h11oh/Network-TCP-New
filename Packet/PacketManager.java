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

    public PacketManager(int windowSize){
        this.windowsSize = windowSize;
        this.remoteSequenceNumberCounter = this.localSequenceNumberCounter = 0;
        packetsWithInfo = new PriorityBlockingQueue<PacketWithInfo>(11, new PacketWithInfoComparator());
        statistics = new Statistics();
    }

    public PriorityBlockingQueue<PacketWithInfo> getQueue(){
        return this.packetsWithInfo;
    }

    public void add(PacketWithInfo infoPkt){
        this.packetsWithInfo.add(infoPkt);
    }

    public void setRemoteSequenceNumberCounter(int remoteSeq){
        this.remoteSequenceNumberCounter = remoteSeq;
    }

    public int getRemoteSequenceNumberCounter(){
        return this.remoteSequenceNumberCounter;
    }

    public int getLocalSequenceNumberCounter(){
        return this.localSequenceNumberCounter;
    }

    public void setLocalSequenceNumberCounter( int localSeq){
        this.localSequenceNumberCounter = localSeq;
        return;
    }

    public Statistics getStatistics(){
        return this.statistics;
    }
}

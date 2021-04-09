package Packet;

// import java.util.PriorityQueue;
import java.util.concurrent.PriorityBlockingQueue;

import Statistics.*;

public class PacketManager {
    private final int windowsSize;
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

    public Statistics getStatistics(){
        return this.statistics;
    }
}

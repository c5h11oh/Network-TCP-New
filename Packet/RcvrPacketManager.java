package Packet;
import java.util.concurrent.PriorityBlockingQueue;

public class RcvrPacketManager{

    private PriorityBlockingQueue<Packet> sequentPkts;
    private int localSequenceNumber;
    private int remoteSequenceNumber;

    public RcvrPacketManager(){
        this.sequentPkts = new PriorityBlockingQueue<Packet>( 11, new RcvPacketComparator());
    }

    

}

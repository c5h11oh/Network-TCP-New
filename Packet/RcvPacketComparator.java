package Packet;
import java.util.Comparator;

public class RcvPacketComparator implements Comparator<Packet> {
    //min priority queue: head of the queue should be the lowest sequence pkt 
    public int compare(Packet p1, Packet p2){
        if(p1.getByteSeqNum() < p2.getByteSeqNum() ){ return 1; }
        else if(p1.getByteSeqNum() > p2.getByteSeqNum() ){
            return -1;
        }else{
            return 0;
        }
    }
    
}

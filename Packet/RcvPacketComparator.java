package Packet;
import java.util.Comparator;

public class RcvPacketComparator implements Comparator<PacketWithInfo> {
    //min priority queue: head of the queue should be the lowest sequence pkt 
    public int compare(PacketWithInfo p1, PacketWithInfo p2){
        if(p1.packet.getByteSeqNum() < p2.packet.getByteSeqNum() ){ return 1; }
        else if(p1.packet.getByteSeqNum() > p2.packet.getByteSeqNum() ){
            return -1;
        }else{
            return 0;
        }
    }
    
}

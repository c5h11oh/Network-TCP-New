package Packet;

import java.util.Comparator;

public class PacketWithInfoComparator implements Comparator<PacketWithInfo> {
    public int compare(PacketWithInfo pk1, PacketWithInfo pk2){
        if ( (pk1.timeOut+pk1.timeStamp) < (pk2.timeOut + pk2.timeStamp)){
            return -1;
        }
        else if (( pk1.timeOut+pk1.timeStamp ) == (pk2.timeOut + pk2.timeStamp)){
            return 0;
        }
        else{
            return 1;
        }
    }
}

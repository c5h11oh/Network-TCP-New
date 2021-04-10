package Packet;

public class PacketWithInfo {
    public Packet packet;
    public long timeStamp; // In nanosecond. Absolute time.
    public long timeOut; // In nanosecond. Time interval.
    public int ACKcount = 0; // Worker (Sender/Receiver) should keep track of the last 1-ACKed Packet. If the next ACK is not DupACK, such `PacketWithInfo` should be withdraw from the `PacketManager.packetsWithInfo`. Fast retransmit is only used once, i.e. we will not increase ACKcount after the first triple-DupACK (ACKcount == 4) occurs.
    public int retransmissionCount = 0;

    public PacketWithInfo(Packet pkt, long timeStamp){
        this.packet = pkt;
        this.timeStamp = timeStamp;
        
    }

    public void setTimeOut(long timeOut){
        this.timeOut = timeOut; 
        return;
    }

    public void setACKcount(int ACKcount){
        this.ACKcount = ACKcount;
        return;
    }

    public void setRetransmissionCount(int retransmissionCount){
        this.retransmissionCount = retransmissionCount;
        return; 
    }
}

package Packet;

public class PacketWithInfo {
    public Packet packet;
    public long timeStamp; // In nanosecond. Absolute time.
    public long timeOut; // In nanosecond. Time interval.
    public int ACKcount = 0; // Worker (Sender/Receiver) should keep track of the last 1-ACKed Packet. If the next ACK is not DupACK, such `PacketWithInfo` should be withdraw from the `PacketManager.packetsWithInfo`. Fast retransmit is only used once, i.e. we will not increase ACKcount after the first triple-DupACK (ACKcount == 4) occurs.
    public int retransmissionCount = 0;
}

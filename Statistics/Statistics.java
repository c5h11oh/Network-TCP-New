package Statistics;

public class Statistics {
    // Both Sender and Receiver should maintain
    public int amountOfValidData = 0; // in byte
    public int amountOfPacket = 0; // in packet

    // Receiver should maintain
    public int amountOfOutOfSeqDiscard = 0; // in packet
    public int amountOfIncChecksumDiscard = 0; // in packet
    
    // Sender should maintain
    public int globalRetransmissionCount = 0;
    public int globalDupAckCount = 0;
}

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

    public String receiverString = "Amount of Data transferred/received: %d byte\nNumber of out-of-sequence packets discarded: %d packets\nNumber of packets discarded due to incorrect checksum: %d packets\n";
    public String senderString = "Number of packets sent/received: %d packets\nNumber of retransmissions: %d packets\nNumber of duplicate acknowledgements: %d counts\n";

   
   /*********************************************************************/
    /**********************          setter         **********************/
    /*********************************************************************/
    public void setIncChecksum(int n){
        this.amountOfIncChecksumDiscard = n;
    }

    public void incrementIncChecksum(int n){
        this.amountOfIncChecksumDiscard+= n ;

    }

    public void incrementValidDataByte(int n){
        this.amountOfValidData += n; 
    }

    public void incrementPacketCount(int n){
        this.amountOfPacket += n;
    }

    public void incrementRetransCount(int n){
        this.globalRetransmissionCount += n;
    }

    public void incrementRetransCount(){
        this.incrementRetransCount(1);
    }

    public void incrementDupACKCount(){
        this.globalDupAckCount +=1;
    }

    public void incrementOutSeqDiscardCount(){
        this.amountOfOutOfSeqDiscard+=1;
    }

    /*********************************************************************/
    /**********************          getter         **********************/
    /*********************************************************************/

    public String senderStat(){
        String s = String.format(senderString, amountOfPacket,globalRetransmissionCount,globalDupAckCount );
        return s;
    }

    public String receiverStat(){
        String s = String.format(receiverString, amountOfValidData, amountOfOutOfSeqDiscard, amountOfIncChecksumDiscard );
        return s; 
    }


}

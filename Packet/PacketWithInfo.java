package Packet;

public class PacketWithInfo {
    public Packet packet;
    // public long timeStamp; // In nanosecond. Absolute time. // To avoid duplication, timeStamp is this.packet.getTimeStamp()
    public long timeOut; // In nanosecond. Time interval.
    public int ACKcount = 0; // Worker (Sender/Receiver) should keep track of the last 1-ACKed Packet. If the next ACK is not DupACK, such `PacketWithInfo` should be withdraw from the `PacketManager.packetsWithInfo`. Fast retransmit is only used once, i.e. we will not increase ACKcount after the first triple-DupACK (ACKcount == 4) occurs.
    public int resendCount = 0;

    public PacketWithInfo(Packet pkt){
        this.packet = pkt;
        // this.timeStamp = pkt.getTimeStamp();
    }

    public void setPacket(Packet packet){
        this.packet = packet;
    }

    public void setTimeOut(long timeOut){
        this.timeOut = timeOut; 
        return;
    }

    public void setACKcount(int ACKcount){
        this.ACKcount = ACKcount;
        return;
    }

    public void setresendCount(int resendCount){
        this.resendCount = resendCount;
        return; 
    }

    /**
     * Make a new PacketWithInfo intended for resending. The new PacketWithInfo's resend count is (1 + old resend count), timeout is (2 * old timeout), and ACKcount remains the same. The new PacketWithInfo's packet has the same data but updated timestamp, ACK, and checksum. 
     * @param remoteSequenceNumber
     * @return
     */
    public PacketWithInfo getResendPacketWithInfo(int remoteSequenceNumber){
        Packet resendPacket = new Packet(this.packet);
        resendPacket.setTimeStampToCurrent();
        resendPacket.setACK(remoteSequenceNumber + 1);
        Packet.calculateAndSetChecksum(resendPacket);

        PacketWithInfo resendPacketWithInfo = new PacketWithInfo(resendPacket);
        resendPacketWithInfo.resendCount = this.resendCount + 1;
        resendPacketWithInfo.timeOut = this.timeOut * 2;
        resendPacketWithInfo.ACKcount = this.ACKcount;

        return resendPacketWithInfo;
    }
}

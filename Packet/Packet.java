package Packet;

import java.nio.*;
import java.util.Arrays;

public class Packet {
    public int byteSeqNum;
    public int ACK;
    public long timeStamp;
    private int lengthAndFlag;
    private int paddedChecksum;
    private byte[] data;

    public Packet(){
        setTimeStampToCurrent();
    }
    public Packet( int byteSeqNum){
        this.byteSeqNum = byteSeqNum;
        setTimeStampToCurrent();

        // The following values should be set. Just to be safe.
        this.ACK = 0;
        this.lengthAndFlag = 0;
        this.paddedChecksum = 0;
        this.data = null;

    }
    public Packet(Packet src){
        this.ACK = src.ACK;
        this.byteSeqNum = src.byteSeqNum;
        this.data = Arrays.copyOf(src.data, src.data.length);
        this.lengthAndFlag = src.lengthAndFlag;
        this.paddedChecksum = src.paddedChecksum;
        this.timeStamp = src.timeStamp;
    }

    public void setTimeStampToCurrent(){
        this.timeStamp = System.nanoTime();
    }
    
    public static byte[] serialize(Packet packet){
        int size = 6 * 4 + (packet.data == null ? 0 : packet.data.length);
        byte[] resultByteArray = new byte[size];
        ByteBuffer bb = ByteBuffer.wrap(resultByteArray);
        bb.putInt(packet.byteSeqNum);
        bb.putInt(packet.ACK);
        bb.putLong(packet.timeStamp);
        bb.putInt(packet.lengthAndFlag);
        bb.putInt(packet.paddedChecksum);
        if (packet.data != null){
            bb.put(packet.data);
        }
        return resultByteArray;
    }

    public static Packet deserialize(byte[] raw) {
        Packet resultPacket = new Packet();
        ByteBuffer bb = ByteBuffer.wrap(raw);
        resultPacket.byteSeqNum = bb.getInt();
        resultPacket.ACK = bb.getInt();
        resultPacket.timeStamp = bb.getLong();
        resultPacket.lengthAndFlag = bb.getInt();
        resultPacket.paddedChecksum = bb.getInt();
        if (bb.hasRemaining()){
            resultPacket.data = new byte[bb.remaining()];
            bb.get(resultPacket.data);
            if (bb.remaining() != 0) {
                throw new RuntimeException();
            }
        }
        return resultPacket;
    }

    public static void setDataAndLength(Packet packet, byte[] data){
        packet.data = data;
        packet.lengthAndFlag &= 7; // Clear data, keep flag
        packet.lengthAndFlag |= (data.length << 3); // Set data
    }

  
    /**
     * Set (by passing in true/false) SYN, FIN, ACK flag in the Packet packet.
     * @param packet
     * @param SYN
     * @param FIN
     * @param ACK
     */
    public static void setFlag(Packet packet, boolean SYN, boolean FIN, boolean ACK){
        if (SYN) {
            packet.lengthAndFlag |= (1 << 2);
        }
        if (FIN) {
            packet.lengthAndFlag |= (1 << 1);
        }
        if (ACK) {
            packet.lengthAndFlag |= (1);
        }
    }

    public static boolean checkSYN(Packet pkt){
        int SYN = pkt.lengthAndFlag & 4;
        return (SYN == 0)? false:true; 
    }

    public static boolean checkFIN(Packet pkt){
        int FIN = pkt.lengthAndFlag & 2;
        return (FIN == 0)? false:true; 
    }

    public static boolean checkACK(Packet pkt){
        int ACK = pkt.lengthAndFlag & 1;
        return (ACK == 0)? false:true; 
    }


    public static void clearFlag(Packet packet){
        packet.lengthAndFlag &= (Integer.MAX_VALUE - 7);
    }
    
    // The return value is Padded (32-bit) checksum
    public static int calculateChecksum(Packet packet){
        // Save the original Checksum
        int originalChecksum = packet.paddedChecksum;
        packet.paddedChecksum = 0;
        int calculateChecksum = 0;
        byte[] serialized = Packet.serialize(packet);
        ByteBuffer bb = ByteBuffer.wrap(serialized);
        ShortBuffer sb = bb.asShortBuffer();
        while(sb.hasRemaining()){
            short a = sb.get();
            calculateChecksum += ((int)a & ((1 << 16) - 1));
            if (calculateChecksum >= (1 << 16)) { // overflow
                calculateChecksum++;
                calculateChecksum &= ((1 << 16) - 1);
            }
        }
        packet.paddedChecksum = originalChecksum;
        return calculateChecksum;
    }
    
    /**
     *  
     * Should only be called when byteSeqNum, ACK, timeStamp are set and 
     * setDataAndLength() and setFlag() are called.
     */
    public static void calculateAndSetChecksum(Packet packet){
        int calculateChecksum = calculateChecksum(packet);
        packet.paddedChecksum = calculateChecksum;
    }

    public long getTimeStamp(){
        return this.timeStamp;
    }

    public void setACK( int ACK){
        this.ACK = ACK; 
    }

    public int getByteSeqNum(){
        return this.byteSeqNum;
    }

    public int getACK(){
        return this.ACK;
    }

    public boolean verifyChecksum(){
        int computed = calculateChecksum(this);
        if( this.paddedChecksum != computed){
            return false;
        }else{
            return true;
        }
    }

    public int getDataLength(){
        if(this.data == null){
            return 0;
        }else{
            return this.data.length;
        }
    }

    public byte[] getData() {
        return this.data;
    }

    
}

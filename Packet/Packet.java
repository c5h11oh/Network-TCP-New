package Packet;

public class Packet {
    public int byteSeqNum;
    public int ACK;
    public long timeStamp;
    private int lengthAndFlag;
    private int paddedChecksum;
    private byte[] data;

    public static byte[] serialize(Packet packet){
        return null;
    }

    public static Packet deserialize(byte[] raw){
        return null;
    }

    public static void setDataAndLength(Packet packet, byte[] data){
        packet.data = data;
        packet.lengthAndFlag &= 7; // Clear data, keep flag
        packet.lengthAndFlag |= (data.length << 3); // Set data
    }

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

    public static void clearFlag(Packet packet){
        packet.lengthAndFlag &= (Integer.MAX_VALUE - 7);
    }
    
    /* 
     * Should only be called when byteSeqNum, ACK, timeStamp are set and 
     * setDataAndLength() and setFlag() are called.
     */
    public static void setChecksum(Packet packet){
    }
}

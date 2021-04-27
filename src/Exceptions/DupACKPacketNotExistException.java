package Exceptions;

public class DupACKPacketNotExistException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = -6697370167403774867L;

    public DupACKPacketNotExistException(){
        super("Dup ACK occured. However, the Packet with ACK# = ByteSeqNum is not found in BufferManager.");
    }
    public DupACKPacketNotExistException(String message){
        super(message);
    }
}

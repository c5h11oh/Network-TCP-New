import Exceptions.*;

public class SenderBuffer extends Buffer {
    /* "Pointers" that are indices of the buffer. Check validility by AssertValidPointers() */
    private int lastByteACK;
    private int lastByteSent;
    private int lastByteWritten;

    /* Indicators of thread `SendFile` finishing putting data into buffer */
    private boolean sendFileFinish = false;     // If set, `SendFile` has put all data into buffer.
    
    public SenderBuffer(int bufferSize, int windowSize) throws BufferNegativeSizeException {
        super(bufferSize, windowSize);
        lastByteACK = lastByteSent = -1;
        lastByteWritten = -1;
    }

    public int getLastByteACK(){
        return this.lastByteACK;
    }

    public int getLastByteSent(){
        return this.lastByteSent;
    }

    public int getLastByteWritten(){
        return this.lastByteWritten;
    }

    public int checkFreeSpace(){
        if(lastByteWritten >= lastByteACK) { // not wrapped
            return bufferSize - (lastByteWritten - lastByteACK);
        }
        else{   // wrapped
            return (bufferSize - lastByteACK) + lastByteWritten;
        }
    }

    public void put(byte[] data, int length) throws BufferInsufficientSpaceException, InvalidPointerException {
        if (checkFreeSpace() < length){
            throw new BufferInsufficientSpaceException();
        }
        
        // wrap check
        if (length < bufferSize - lastByteWritten) { // not wrapped
            System.arraycopy(data, 0, this.buf, lastByteWritten + 1, length);
            lastByteWritten += length;
        }
        else{ // wrapped
            int endByteCount = bufferSize - lastByteWritten - 1;
            System.arraycopy(data, 0, this.buf, lastByteWritten + 1, endByteCount);
            System.arraycopy(data, endByteCount, this.buf, 0, length - endByteCount);   
            lastByteWritten = length - endByteCount - 1;
        }

        AssertValidPointers();
    }

    public void put(byte[] data, int length, boolean finish) throws BufferInsufficientSpaceException, InvalidPointerException {
        put(data, length);
        if(finish){
            this.sendFileFinish = true;
            
        }
    }

    /*
     * Get [lastByteSent + 1, lastByteSent + 1 + length) data from buffer.
     * Advance `lastByteSent` by length.
     * Note this get function DOES advance the lastByteSent pointer, which is different 
     * from the handwritten draft. The thread that resend packet should NEVER get data 
     * by calling this function, since now retransmission only has to do with "PacketManager".
     */
    public byte[] getDataToSend(int length) throws InvalidPointerException {
        if (sendFileFinish && lastByteWritten == lastByteSent) { 
            // No more data to send
            return null;
        }
        
        int byteToBeSent;
        boolean wrapped = length >= bufferSize - lastByteSent;

        if (!wrapped) { // Not wrapped
            byteToBeSent = Math.min(length, lastByteWritten - lastByteSent);
        }
        else { // wrapped
            byteToBeSent = Math.min(length, lastByteWritten + bufferSize - lastByteSent);
        }
        
        byte[] returnData = new byte[byteToBeSent];
        if(!wrapped) {
            System.arraycopy(this.buf, lastByteSent + 1, returnData, 0, byteToBeSent);
            lastByteSent += byteToBeSent;
        }
        else {
            int endByteCount = bufferSize - lastByteSent - 1;
            System.arraycopy(this.buf, lastByteSent + 1, returnData, 0, endByteCount);
            System.arraycopy(this.buf, 0, returnData, endByteCount, byteToBeSent - endByteCount);
            lastByteSent = byteToBeSent - endByteCount - 1;
        }

        AssertValidPointers();
        return returnData; // Caller needs to confirm the return length. May not be `length`.
    }

    private void AssertValidPointers() throws InvalidPointerException{
        if (lastByteACK > lastByteSent || lastByteSent > lastByteWritten){
            throw new InvalidPointerException();
        }
    }
}

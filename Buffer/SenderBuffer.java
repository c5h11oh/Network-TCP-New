package Buffer;

import Exceptions.*;

public class SenderBuffer extends Buffer {
    /* "Pointers" that are indices of the buffer. Check validility by AssertValidPointers() */
    private int lastByteACK;
    private int lastByteSent;
    private int lastByteWritten;

    /* Indicators of thread `SendFile` finishing putting data into buffer */
    private boolean sendFileFinish = false;     // If set, `SendFile` has put all data into buffer.
    
    public SenderBuffer(int bufferSize, int windowSize) throws BufferSizeException {
        super(++bufferSize, windowSize);  // We add one additional byte to avoid ambiguity when wrapping.
        // The `lastByteACK` byte cannot be written even if `lastByteACK == nextByteExpected`, where `nextByteExpected = (lastByteWritten + 1) % bufferSize`. 
        // If `lastByteRead == nextByteExpected`, the free space is 0 and getDataToSend() must be called.
        lastByteACK = lastByteSent = bufferSize - 1;
        lastByteWritten = bufferSize - 1;
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

    public boolean isSendFileFinished() {
        return sendFileFinish;
    }

    public int checkFreeSpace(){ // `lastByteACK` is NOT free
        int nextByteExpected = (lastByteWritten + 1) % bufferSize;
        if (nextByteExpected == lastByteACK) {
            return 0;
        }
        else if (nextByteExpected > lastByteACK) { // not wrapped
            return bufferSize - (nextByteExpected - lastByteACK);
        }
        else{   // wrapped
            return (lastByteACK - nextByteExpected);
        }
    }

    public void put(byte[] data, int length) throws BufferInsufficientSpaceException, InvalidPointerException {
        if (checkFreeSpace() < length){
            throw new BufferInsufficientSpaceException();
        }
        
        int endByteCount = bufferSize - lastByteWritten - 1;
        boolean wrapped = length > endByteCount;

        // wrap check
        if (!wrapped) { // not wrapped
            System.arraycopy(data, 0, this.buf, lastByteWritten + 1, length);
            lastByteWritten += length;
        }
        else{ // wrapped
            System.arraycopy(data, 0, this.buf, lastByteWritten + 1, endByteCount);
            System.arraycopy(data, endByteCount, this.buf, 0, length - endByteCount);   
            lastByteWritten = length - endByteCount - 1;
        }
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
     * by calling this function. It should retrieve data from "PacketManager".
     */
    public byte[] getDataToSend(int length) throws InvalidPointerException {
        if (lastByteWritten == lastByteSent) { 
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
        
        AssertValidSentPointer();
        return returnData; // Caller needs to confirm the return length. May not be `length`.
    }

    private void AssertValidSentPointer() throws InvalidPointerException{
        if (lastByteACK < lastByteWritten){
            if (lastByteSent < lastByteACK || lastByteSent > lastByteWritten) {
                throw new InvalidPointerException();
            }
        }
        else if (lastByteACK > lastByteWritten){
            if (lastByteSent < lastByteACK && lastByteSent > lastByteWritten) {
                throw new InvalidPointerException();
            }
        }
        else {
            if (lastByteSent != lastByteACK){
                throw new InvalidPointerException();
            }
        }
    }
}

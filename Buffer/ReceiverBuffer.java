package Buffer;

import Exceptions.*;

public class ReceiverBuffer extends Buffer {
    /* "Pointers" that are indices of the buffer. Check validility by AssertValidPointers() */
    private int lastByteRead;
    private int nextByteExpected;
    // private int lastByteRcvd;

    /* Indicators of thread `DataReceiver` has retrieved all data and put in buffer */
    private boolean allDataReceived = false;     // If set, `DataReceiver` has put all data into buffer.
    
    public ReceiverBuffer(int bufferSize, int windowSize) throws BufferSizeException {
        super(bufferSize, windowSize);
        lastByteRead = bufferSize - 1;
        nextByteExpected = 0;
    }

    public int getLastByteRead(){
        return this.lastByteRead;
    }

    public int getNextByteExpected(){
        return this.nextByteExpected;
    }

    // public int getLastByteRcvd(){
    //     return this.lastByteRcvd;
    // }

    public boolean isAllDataReceived(){
        return allDataReceived;
    }

    public int checkFreeSpace(){
        if(nextByteExpected > lastByteRead) { // not wrapped
            return bufferSize - (nextByteExpected - 1 - lastByteRead);
        }
        else{   // wrapped
            return (bufferSize - lastByteRead) + nextByteExpected - 1;
        }
    }

    /* Design: You can only put contiguous data in the buffer. All non-contiguous data are 
     * stored in and managed by PacketManager as packets. Therefore, ReceiverBuffer does not 
     * need to maintain lastByteRcvd.
     */
    public void put(byte[] data) throws BufferInsufficientSpaceException, InvalidPointerException {
        int endByteCount = bufferSize - nextByteExpected;
        boolean wrapped = data.length > endByteCount;
        if(!wrapped){ // not wrapped
            if ((lastByteRead >= nextByteExpected) && (lastByteRead - nextByteExpected + 1 < data.length)){
                throw new BufferInsufficientSpaceException();
            }
            System.arraycopy(data, 0, this.buf, nextByteExpected, data.length);
            nextByteExpected += data.length;
        }
        else{ // wrapped
            if (
                ( (lastByteRead < nextByteExpected) && (lastByteRead + 1 + endByteCount < data.length) ) ||
                ( (lastByteRead >= nextByteExpected) )
            ){
                throw new BufferInsufficientSpaceException();
            }
            System.arraycopy(data, 0, this.buf, nextByteExpected, endByteCount);
            System.arraycopy(data, endByteCount, this.buf, 0, data.length - endByteCount);
            nextByteExpected = data.length - endByteCount;
        }

        AssertValidPointers();
    }

    public void put(byte[] data, boolean finish) 
                 throws BufferInsufficientSpaceException, InvalidPointerException {
        put(data);
        if(finish){
            this.allDataReceived = true;
        }
    }

    /*
     * Get as much data as possible.
     */
    public byte[] getData() throws InvalidPointerException {
        int lastContiguousByte = (nextByteExpected == 0) ? (bufferSize - 1) : (nextByteExpected - 1);
        if (lastByteRead == lastContiguousByte) { 
            // No more data
            return null;
        }
        
        int dataLength;
        boolean wrapped = lastByteRead > lastContiguousByte;

        if (!wrapped) { // Not wrapped
            dataLength = lastContiguousByte - lastByteRead;
        }
        else { // wrapped
            dataLength = lastContiguousByte + bufferSize - lastByteRead;
        }
        
        byte[] returnData = new byte[dataLength];
        if(!wrapped) {
            System.arraycopy(this.buf, lastByteRead + 1, returnData, 0, dataLength);
            lastByteRead += dataLength;
        }
        else {
            int endByteCount = bufferSize - lastContiguousByte - 1;
            System.arraycopy(this.buf, lastByteRead + 1, returnData, 0, endByteCount);
            System.arraycopy(this.buf, 0, returnData, endByteCount, dataLength - endByteCount);
            lastByteRead = dataLength - endByteCount - 1;
        }
        assert lastByteRead == lastContiguousByte;
        AssertValidPointers();
        return returnData; // Caller needs to confirm the return length. May not be `length`.
    }

    // TODO: May be wrong. Did not consider wrapping
    private void AssertValidPointers() throws InvalidPointerException{
        if (lastByteRead >= nextByteExpected /* || nextByteExpected > lastByteRcvd + 1 */ ) {
            throw new InvalidPointerException();
        }
    }
}

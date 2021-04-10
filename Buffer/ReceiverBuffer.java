package Buffer;

import Exceptions.*;

//receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>


public class ReceiverBuffer extends Buffer {
    /* "Pointers" that are indices of the buffer. Check validility by AssertValidPointers() */
    private int lastByteRead;
    private int nextByteExpected;
    // private int lastByteRcvd;

    /* Indicators of thread `DataReceiver` has retrieved all data and put in buffer */
    private boolean allDataReceived = false;     // If set, `DataReceiver` has put all data into buffer.
    
    public ReceiverBuffer(int bufferSize, int mtu, int windowSize) throws BufferSizeException {
        super(++bufferSize, mtu, windowSize); // We add one additional byte to avoid ambiguity when wrapping.
        // The `lastByteRead` byte cannot be written even if `lastByteRead == nextByteExpected`. 
        // If `lastByteRead == nextByteExpected`, the free space is 0 and getData() must be called.
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

    public int checkFreeSpace(){ // `lastByteRead` is NOT free
        if (nextByteExpected == lastByteRead){ 
            return 0;
        }
        else if (nextByteExpected > lastByteRead) { // not wrapped
            return bufferSize - (nextByteExpected - lastByteRead);
        }
        else {   // wrapped
            return (lastByteRead - nextByteExpected);
        }
    }

    /* Design: You can only put contiguous data in the buffer. All non-contiguous data are 
     * stored in and managed by PacketManager as packets. Therefore, ReceiverBuffer does not 
     * need to maintain lastByteRcvd.
     */
    public void put(byte[] data) throws BufferInsufficientSpaceException{
        if (data.length == 0) return;
        
        if (checkFreeSpace() < data.length) throw new BufferInsufficientSpaceException();
        
        int endByteCount = bufferSize - nextByteExpected;
        boolean wrapped = data.length > endByteCount;
        
        if (!wrapped){ // not wrapped
            System.arraycopy(data, 0, this.buf, nextByteExpected, data.length);
            nextByteExpected += data.length;
        }
        else{ // wrapped
            System.arraycopy(data, 0, this.buf, nextByteExpected, endByteCount);
            System.arraycopy(data, endByteCount, this.buf, 0, data.length - endByteCount);
            nextByteExpected = data.length - endByteCount;
        }

        this.notifyAll();
    }

    public void put(byte[] data, boolean finish) 
                 throws BufferInsufficientSpaceException{
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
        return returnData; // Caller needs to confirm the return length. May not be `length`.
    }

    // Seems I cannot check if the pointer is valid. Every position has its meaning.
    // private void AssertValidPointers() throws InvalidPointerException{
    //     if (lastByteRead >= nextByteExpected /* || nextByteExpected > lastByteRcvd + 1 */ ) {
    //         throw new InvalidPointerException();
    //     }
    // }
}

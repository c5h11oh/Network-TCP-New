package Buffer;

import Exceptions.*;

//receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>


public class ReceiverBuffer extends Buffer {
    /* "Pointers" that are indices of the buffer. Check validility by AssertValidPointers() */
    private int lastByteRead;
    private int nextByteExpected;
    private boolean noMoreNewByte;
    // private int lastByteRcvd;

    /* obsolete */
    // private boolean allDataReceived = false;     // If set, `DataReceiver` has put all data into buffer
    
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

    public boolean getNoMoreNewByte() {
        return this.noMoreNewByte;
    }

    public void setNoMoreNewByteToTrue() {
        this.noMoreNewByte = true;
        notifyAll();
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

    /* Design: You can only put continuous data in the buffer. All non-continuous data are 
     * stored in and managed by PacketManager as packets. Therefore, ReceiverBuffer does not 
     * need to maintain lastByteRcvd.
     */
    public synchronized void put(byte[] data) throws BufferInsufficientSpaceException{
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

    /*
     * Get as much data as possible.
     */
    public synchronized byte[] getData() {
        int lastContinuousByte = (nextByteExpected == 0) ? (bufferSize - 1) : (nextByteExpected - 1);
        if (lastByteRead == lastContinuousByte) { 
            // No more data
            return null;
        }
        
        int dataLength;
        boolean wrapped = lastByteRead > lastContinuousByte;

        if (!wrapped) { // Not wrapped
            dataLength = lastContinuousByte - lastByteRead;
        }
        else { // wrapped
            dataLength = lastContinuousByte + bufferSize - lastByteRead;
        }
        
        byte[] returnData = new byte[dataLength];
        if(!wrapped) {
            System.arraycopy(this.buf, lastByteRead + 1, returnData, 0, dataLength);
            lastByteRead += dataLength;
        }
        else {
            int endByteCount = bufferSize - lastContinuousByte - 1;
            System.arraycopy(this.buf, lastByteRead + 1, returnData, 0, endByteCount);
            System.arraycopy(this.buf, 0, returnData, endByteCount, dataLength - endByteCount);
            lastByteRead = dataLength - endByteCount - 1;
        }
        assert lastByteRead == lastContinuousByte;
        return returnData; // Caller needs to confirm the return length. May not be `length`.
    }

    public synchronized byte[] waitAndGetData() {
        byte[] b = this.getData();
                    
        // if there is no data, wait(). when wake up, go back to start of while loop.
        while (b == null && this.noMoreNewByte == false) {
            this.notifyAll();
            try{
                this.wait();
            } catch (InterruptedException e) {
                b = this.getData();
            }
        }

        return b;
    }
}

package Buffer;

import Exceptions.*;

public class SenderBuffer extends Buffer {
    /* "Pointers" that are indices of the buffer. Check validility by AssertValidPointers() */
    private int lastByteACK;
    private int lastByteSent;
    private int lastByteWritten;

    /* Indicators of thread `FileToBuffer` finishing putting data into buffer */
    private boolean fileToBufferFinish = false;     // If set, `FileToBuffer` has put all data into buffer.
    
    public SenderBuffer(int bufferSize, int mtu, int windowSize) throws BufferSizeException {
        super(++bufferSize, mtu, windowSize);  // We add one additional byte to avoid ambiguity when wrapping.
        // The `lastByteACK` byte cannot be written even if `lastByteACK == nextByteExpected`, where `nextByteExpected = (lastByteWritten + 1) % bufferSize`. 
        // If `lastByteRead == nextByteExpected`, the free space is 0 and getDataToSend() must be called.
        lastByteACK = lastByteSent = 0; // Apr 12: The first sending byte's sequence number is 1
        lastByteWritten = 0;
    }

    public synchronized int getLastByteACK(){
        return this.lastByteACK;
    }

    public synchronized int getLastByteSent(){
        return this.lastByteSent;
    }

    

    public synchronized int getLastByteWritten(){
        return this.lastByteWritten;
    }

    public synchronized void setFileToBufferFinished() {
        fileToBufferFinish = true;
    }
    
    public synchronized boolean isFileToBufferFinished() {
        return fileToBufferFinish;
    }

    private synchronized int checkFreeSpace(){ // `lastByteACK` is NOT free
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

    public synchronized int waitForFreeSpace(){
        int fs; 
        while((fs = this.checkFreeSpace()) == 0){
            full = true;
            notifyAll();
            try{
                wait();
            } catch (InterruptedException e) {}
        }
        assert fs > 0;
        
        return fs;
    }

    public synchronized void put(byte[] data) throws BufferInsufficientSpaceException {
        if (data.length == 0) return;
        
        if (checkFreeSpace() < data.length){
            throw new BufferInsufficientSpaceException();
        }
        
        int endByteCount = bufferSize - lastByteWritten - 1;
        boolean wrapped = data.length > endByteCount;

        // wrap check
        if (!wrapped) { // not wrapped
            System.arraycopy(data, 0, this.buf, lastByteWritten + 1, data.length);
            lastByteWritten += data.length;
        }
        else{ // wrapped
            System.arraycopy(data, 0, this.buf, lastByteWritten + 1, endByteCount);
            System.arraycopy(data, endByteCount, this.buf, 0, data.length - endByteCount);   
            lastByteWritten = data.length - endByteCount - 1;
        }

        empty = false;
        this.notifyAll();
    }

    public synchronized void put(byte[] data, boolean finish) throws BufferInsufficientSpaceException, InvalidPointerException {
        put(data);
        if(finish){
            this.fileToBufferFinish = true;
        }
    }

    /*
     * Get [lastByteSent + 1, lastByteSent + 1 + length) data from buffer.
     * Advance `lastByteSent` by length.
     * Note this get function DOES advance the lastByteSent pointer, which is different 
     * from the handwritten draft. The thread that resend packet should NEVER get data 
     * by calling this function. It should retrieve data from "PacketManager".
     */
    public synchronized byte[] getDataToSend(int length) throws InvalidPointerException {
        if (length <= 0) return null;
        
        while (lastByteWritten == lastByteSent) { 
            // Buffer empty. No more data to send. 
            empty = true;

            // Does app still want to send data? Maybe it's finished?
            if(this.fileToBufferFinish) { return null; }

            // The app still has data wishes to send. Notify it 
            notifyAll();
            try{
                wait();
            } catch (InterruptedException e) {}
        }
        
        int byteToBeSent;
        boolean wrapped = (lastByteWritten > lastByteSent);//??seem like this boolean is true if not wrapped 

       

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
        this.full = false;
        notifyAll();

        return returnData; // Caller needs to confirm the return length. May not be `length`.
    }

    /*
    This function checks how many bytes of data are ready to be sent to packet manager 
    */    
    public synchronized int getAvailableDataSize(){
        int availableByte; 
        boolean wrapped = (lastByteWritten < lastByteSent);

        if (!wrapped) { // Not wrapped
            availableByte = lastByteWritten - lastByteSent; 
        }
        else { // wrapped
            availableByte = lastByteWritten + bufferSize - lastByteSent; 
        }

        return availableByte; 
    }

    private synchronized void AssertValidSentPointer() throws InvalidPointerException{
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

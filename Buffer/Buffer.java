package Buffer;

import Exceptions.*;

public class Buffer {
    protected byte[] buf;
    protected final int bufferSize; // in byte
    protected final int mtu; // segment count
    protected final int windowSize; // in segment

    protected boolean empty, full;

    public Buffer(int bufferSize, int mtu, int windowSize) throws BufferSizeException {
        if (bufferSize - 1 <= 0 || windowSize <= 0 || mtu <= 0 ){
            throw new BufferSizeException("Buffer size, mtu, and window size should be positive integer");
        }
        if (bufferSize - 1 <= mtu * windowSize){
            throw new BufferSizeException("Buffer is too small for window to slide");
        }
        this.bufferSize = bufferSize;
        this.windowSize = windowSize;
        this.mtu = mtu;
        this.buf = new byte[bufferSize];
        this.empty = true;
        this.full = false;
    }
}
